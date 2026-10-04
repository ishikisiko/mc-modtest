#!/usr/bin/env python3
"""
DevBridge MCP server.

Runs next to your AI agent and translates MCP tool calls into HTTP requests to
the DevBridge mod running inside Minecraft (possibly on another machine, e.g.
over Tailscale).

Environment:
  DEVBRIDGE_URL    e.g. http://100.101.102.103:8787   (default http://127.0.0.1:8787)
  DEVBRIDGE_TOKEN  the token from config/devbridge.json on the Minecraft side

Run:
  uv run server.py            # or: python server.py
Transport is stdio (the default for local MCP clients such as Claude Code).
"""

from __future__ import annotations

import base64
import json
import os
from typing import Any

import httpx
from mcp.server.fastmcp import FastMCP
from mcp.server.fastmcp.utilities.types import Image

BASE_URL = os.environ.get("DEVBRIDGE_URL", "http://127.0.0.1:8787").rstrip("/")
TOKEN = os.environ.get("DEVBRIDGE_TOKEN", "")
TIMEOUT = float(os.environ.get("DEVBRIDGE_TIMEOUT", "30"))

mcp = FastMCP(
    "devbridge",
    instructions=(
        "Tools for observing and driving a running Minecraft (NeoForge) instance during mod development. "
        "Typical loop: mc_log(level='WARN') to find problems -> mc_command / mc_block / mc_player to inspect "
        "or reproduce -> mc_screenshot to confirm visually. Anything mod-specific should be exposed by the mod "
        "as a command and driven through mc_command."
    ),
)

_client = httpx.Client(
    base_url=BASE_URL,
    headers={"Authorization": f"Bearer {TOKEN}"},
    timeout=TIMEOUT,
)


def _call(method: str, path: str, **params: Any) -> Any:
    params = {k: v for k, v in params.items() if v is not None}
    if method == "GET":
        r = _client.get(path, params=params)
    else:
        r = _client.post(path, json=params)
    try:
        data = r.json()
    except json.JSONDecodeError:
        raise RuntimeError(f"DevBridge returned non-JSON ({r.status_code}): {r.text[:500]}")
    if not data.get("ok"):
        msg = data.get("error", "unknown error")
        trace = data.get("trace")
        raise RuntimeError(f"DevBridge {r.status_code}: {msg}" + (f"\n{trace}" if trace else ""))
    return data["result"]


def _fmt(obj: Any) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=2)


# ---------------------------------------------------------------------------
# Instance / registries
# ---------------------------------------------------------------------------

@mcp.tool()
def mc_info() -> str:
    """Game + NeoForge versions, whether a server is running, online players, dimensions, tick time."""
    return _fmt(_call("GET", "/info"))


@mcp.tool()
def mc_mods() -> str:
    """List all loaded mods with id, display name, version and jar file."""
    return _fmt(_call("GET", "/mods"))


@mcp.tool()
def mc_registries() -> str:
    """List every registry id (blocks, items, entity types, plus datapack registries when in a world)."""
    return _fmt(_call("GET", "/registries"))


@mcp.tool()
def mc_registry(registry: str, namespace: str | None = None, contains: str | None = None, limit: int = 500) -> str:
    """Entries of one registry, e.g. registry='minecraft:block', namespace='mymod'.
    Use this to verify that your mod's blocks/items/entities actually registered under the ids you expect."""
    return _fmt(_call("GET", "/registry", id=registry, namespace=namespace, contains=contains, limit=limit))


@mcp.tool()
def mc_registry_entry(registry: str, key: str) -> str:
    """Describe one registry entry (class name, toString, raw id). e.g. registry='minecraft:item', key='mymod:widget'."""
    return _fmt(_call("GET", "/registry/entry", id=registry, key=key))


# ---------------------------------------------------------------------------
# Logs
# ---------------------------------------------------------------------------

@mcp.tool()
def mc_log(level: str = "INFO", logger: str | None = None, contains: str | None = None,
           since: int = 0, limit: int = 100) -> str:
    """Recent log lines captured in memory from ALL loggers (Minecraft, NeoForge, every mod).
    level: minimum level (TRACE/DEBUG/INFO/WARN/ERROR). logger: substring of logger name (e.g. your modid).
    contains: substring of message or stack trace. since: only entries with seq > since (for polling).
    Returns latestSeq so you can poll incrementally."""
    return _fmt(_call("GET", "/log", level=level, logger=logger, contains=contains, since=since, limit=limit))


@mcp.tool()
def mc_log_clear() -> str:
    """Clear the in-memory log buffer (useful right before reproducing an issue)."""
    return _fmt(_call("POST", "/log/clear"))


@mcp.tool()
def mc_log_file(name: str = "latest.log", lines: int = 200) -> str:
    """Tail of a log file on disk (logs/latest.log or logs/debug.log)."""
    return _call("GET", "/log/file", name=name, lines=lines)["text"]


@mcp.tool()
def mc_crashes() -> str:
    """List crash reports, newest first."""
    return _fmt(_call("GET", "/crashes"))


@mcp.tool()
def mc_crash_read(name: str | None = None, max_chars: int = 20000) -> str:
    """Read a crash report (newest if name omitted)."""
    return _call("GET", "/crashes/read", name=name, maxChars=max_chars)["text"]


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

@mcp.tool()
def mc_command(command: str, as_player: str | None = None, dimension: str | None = None,
               x: float | None = None, y: float | None = None, z: float | None = None) -> str:
    """Run a server command (no leading slash needed) with op level 4 and return its captured output.
    as_player: run with a player's position/entity context. Use this for your mod's own debug commands,
    /setblock, /give, /tp, /data get, /test runall, etc."""
    return _fmt(_call("POST", "/command", command=command, **{"as": as_player},
                      dimension=dimension, x=x, y=y, z=z))


@mcp.tool()
def mc_command_list(contains: str | None = None) -> str:
    """List registered top-level command names (filter with contains, e.g. your modid)."""
    return _fmt(_call("GET", "/command/tree", contains=contains))


@mcp.tool()
def mc_command_usage(name: str) -> str:
    """Usage strings (all argument forms) for a command."""
    return _fmt(_call("GET", "/command/usage", name=name))


# ---------------------------------------------------------------------------
# World
# ---------------------------------------------------------------------------

@mcp.tool()
def mc_block(x: int, y: int, z: int, dimension: str | None = None) -> str:
    """Block state (with properties) and block entity NBT at a position, from the server."""
    return _fmt(_call("GET", "/world/block", x=x, y=y, z=z, dimension=dimension))


@mcp.tool()
def mc_area(x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, dimension: str | None = None,
            include_air: bool = False, positions: bool = False) -> str:
    """Count of each block state in a box (max 32768 blocks). positions=True also lists coordinates per state."""
    return _fmt(_call("GET", "/world/area", x1=x1, y1=y1, z1=z1, x2=x2, y2=y2, z2=z2,
                      dimension=dimension, includeAir=include_air, positions=positions))


@mcp.tool()
def mc_set_block(x: int, y: int, z: int, state: str, dimension: str | None = None, flags: int = 3) -> str:
    """Place a block. state uses command syntax, e.g. 'mymod:my_machine[facing=north]{Items:[]}'."""
    return _fmt(_call("POST", "/world/block", x=x, y=y, z=z, state=state, dimension=dimension, flags=flags))


@mcp.tool()
def mc_entities(dimension: str | None = None, x: float | None = None, y: float | None = None,
                z: float | None = None, radius: float = 16, type_contains: str | None = None,
                nbt: bool = False, limit: int = 50) -> str:
    """Entities in a dimension, optionally within radius of x,y,z. type_contains filters by entity type id."""
    return _fmt(_call("GET", "/world/entities", dimension=dimension, x=x, y=y, z=z, radius=radius,
                      type=type_contains, nbt=nbt, limit=limit))


@mcp.tool()
def mc_entity(uuid: str | None = None, id: int | None = None, dimension: str | None = None) -> str:
    """One entity with full NBT, by uuid or numeric entity id."""
    return _fmt(_call("GET", "/world/entity", uuid=uuid, id=id, dimension=dimension))


@mcp.tool()
def mc_player(name: str | None = None) -> str:
    """Server-side player state: position, health, food, xp, gamemode, held items, full inventory with NBT."""
    return _fmt(_call("GET", "/player", name=name))


@mcp.tool()
def mc_players() -> str:
    """Names of online players."""
    return _fmt(_call("GET", "/players"))


@mcp.tool()
def mc_chunk(x: int, z: int, dimension: str | None = None) -> str:
    """Whether the chunk containing block x,z is loaded, its status and block entity count."""
    return _fmt(_call("GET", "/world/chunk", x=x, z=z, dimension=dimension))


@mcp.tool()
def mc_biome(x: int, y: int, z: int, dimension: str | None = None) -> str:
    """Biome id at a position."""
    return _fmt(_call("GET", "/world/biome", x=x, y=y, z=z, dimension=dimension))


# ---------------------------------------------------------------------------
# Reflection (dev-only escape hatch)
# ---------------------------------------------------------------------------

@mcp.tool()
def mc_reflect_describe(class_name: str, declared_only: bool = True) -> str:
    """List fields and methods of any loaded class (Minecraft, NeoForge or your mod), e.g. 'com.example.mymod.MyBlockEntity'."""
    return _fmt(_call("GET", "/reflect/describe", **{"class": class_name}, declaredOnly=declared_only))


@mcp.tool()
def mc_reflect_field(class_name: str, field: str, target: str | None = None, thread: str | None = None) -> str:
    """Read a field. target omitted = static field. target examples: 'server', 'player:Steve',
    'blockentity:minecraft:overworld:10:64:10', 'entity:<uuid>', 'mod:mymod', 'minecraft', 'static:<class>:<field>'.
    thread: 'server' | 'client' | 'none' (default: server thread if a world is open; 'client' for the minecraft target)."""
    return _fmt(_call("POST", "/reflect/field", **{"class": class_name}, field=field, target=target, thread=thread))


@mcp.tool()
def mc_reflect_invoke(class_name: str, method: str, args: list[Any] | None = None,
                      target: str | None = None, thread: str | None = None) -> str:
    """Invoke a method. args are JSON values converted to parameter types (primitives, strings, enums by name,
    ResourceLocation from 'ns:path', BlockPos from {x,y,z}, or a target string like 'player:Steve' for game objects).
    target omitted = static method. thread: 'server' | 'client' | 'none' (default: the appropriate game thread)."""
    return _fmt(_call("POST", "/reflect/invoke", **{"class": class_name}, method=method, args=args or [],
                      target=target, thread=thread))


# ---------------------------------------------------------------------------
# Client (only when DevBridge runs inside the game client)
# ---------------------------------------------------------------------------

@mcp.tool()
def mc_client_state() -> str:
    """Client status: fps, paused?, open screen, local player position/look, block or entity under the crosshair."""
    return _fmt(_call("GET", "/client/state"))


@mcp.tool()
def mc_screenshot(max_width: int = 1280, save: bool = False) -> Image:
    """Capture the game window and return it as an image. Use it to visually verify rendering, GUIs, models."""
    res = _call("GET", "/client/screenshot", maxWidth=max_width, save=save)
    return Image(data=base64.b64decode(res["base64"]), format="png")


@mcp.tool()
def mc_screen() -> str:
    """Describe the open GUI screen and its widgets (class, label, bounds, active/visible)."""
    return _fmt(_call("GET", "/client/screen"))


@mcp.tool()
def mc_screen_close() -> str:
    """Close the currently open GUI screen."""
    return _fmt(_call("POST", "/client/screen/close"))


@mcp.tool()
def mc_client_chat(text: str) -> str:
    """Send chat text or a '/command' as the local player (goes through the normal client -> server path)."""
    return _fmt(_call("POST", "/client/chat", text=text))


@mcp.tool()
def mc_client_look(yaw: float, pitch: float) -> str:
    """Turn the local player's camera (yaw: 0=south, 90=west, 180=north, -90=east; pitch: -90 up .. 90 down)."""
    return _fmt(_call("POST", "/client/look", yaw=yaw, pitch=pitch))


@mcp.tool()
def mc_client_keys(contains: str | None = None) -> str:
    """List the client's key mappings (name, category, bound key, currently down)."""
    return _fmt(_call("GET", "/client/keys", contains=contains))


@mcp.tool()
def mc_client_key(names: list[str], action: str = "click", ticks: int = 10) -> str:
    """Press key mappings as the player, by mapping name so the user's bindings don't matter.
    names: e.g. ["forward", "sprint"], ["jump"], ["attack"], ["use"], ["hotbar.3"], ["togglePerspective"], ["inventory"].
    action: click (one press) | hold (down for `ticks`, 20 ticks = 1 s) | down | up."""
    return _fmt(_call("POST", "/client/key", names=names, action=action, ticks=ticks))


@mcp.tool()
def mc_client_key_release() -> str:
    """Release every held key (stop moving)."""
    return _fmt(_call("POST", "/client/key/release"))


@mcp.tool()
def mc_client_window(width: int | None = None, height: int | None = None, x: int | None = None, y: int | None = None) -> str:
    """Read (no args) or resize/move the game window. Returns size, position, focused, mouseGrabbed."""
    if width is None and height is None and x is None and y is None:
        return _fmt(_call("GET", "/client/window"))
    return _fmt(_call("POST", "/client/window", width=width, height=height, x=x, y=y))


@mcp.tool()
def mc_client_mouse(grab: bool = False) -> str:
    """Release (default) or grab the mouse. Released, the user's cursor stays free even while the game has focus."""
    return _fmt(_call("POST", "/client/mouse", grab=grab))


@mcp.tool()
def mc_client_hotbar(slot: int) -> str:
    """Select hotbar slot 0-8; returns the item now in the main hand."""
    return _fmt(_call("POST", "/client/hotbar", slot=slot))


@mcp.tool()
def mc_client_perspective(mode: str) -> str:
    """Camera perspective: first | back | front (the F5 views)."""
    return _fmt(_call("POST", "/client/perspective", mode=mode))


@mcp.tool()
def mc_client_hud(hidden: bool = True) -> str:
    """Hide (F1) or show the HUD, e.g. for clean screenshots."""
    return _fmt(_call("POST", "/client/hud", hidden=hidden))


@mcp.tool()
def mc_client_option(name: str, value: Any = None, save: bool = False) -> str:
    """Read (value omitted) or set a client option by its Options accessor: fov, renderDistance, gamma, simulationDistance, ...
    save=False keeps the change only until the game restarts."""
    return _fmt(_call("POST", "/client/option", name=name, value=value, save=save))


@mcp.tool()
def mc_screen_click(widget: int | None = None, x: float | None = None, y: float | None = None, button: int = 0) -> str:
    """Click in the open GUI: a widget index from mc_screen, or GUI-scaled x,y. button 0 = left, 1 = right."""
    return _fmt(_call("POST", "/client/screen/click", widget=widget, x=x, y=y, button=button))


@mcp.tool()
def mc_screen_type(text: str = "", key: str | None = None) -> str:
    """Type text into the focused GUI field, then optionally press a key such as key.keyboard.enter or key.keyboard.escape."""
    return _fmt(_call("POST", "/client/screen/type", text=text, key=key))


@mcp.tool()
def mc_client_world() -> str:
    """The open world: inWorld, singleplayer, folder (the saves/ directory name), display name."""
    return _fmt(_call("GET", "/client/world"))


@mcp.tool()
def mc_client_worlds() -> str:
    """Singleplayer saves, newest first (folder names for mc_client_world_open)."""
    return _fmt(_call("GET", "/client/worlds"))


@mcp.tool()
def mc_client_world_leave() -> str:
    """Save and quit to the title screen. Returns at once; poll mc_client_state."""
    return _fmt(_call("POST", "/client/world/leave"))


@mcp.tool()
def mc_client_world_open(folder: str) -> str:
    """Open a singleplayer world by folder, leaving the current one first. Returns at once; poll mc_client_world."""
    return _fmt(_call("POST", "/client/world/open", folder=folder))


@mcp.tool()
def mc_client_quit() -> str:
    """Save the open world and close the game. DevBridge stops answering afterwards."""
    return _fmt(_call("POST", "/client/quit"))


@mcp.tool()
def mc_routes() -> str:
    """List every raw HTTP route the DevBridge mod exposes (for anything not wrapped as a tool)."""
    return _fmt(_call("GET", "/routes"))


@mcp.tool()
def mc_raw(method: str, path: str, params: dict[str, Any] | None = None) -> str:
    """Call any DevBridge route directly. method: GET or POST. params become query (GET) or JSON body (POST)."""
    return _fmt(_call(method.upper(), path, **(params or {})))


if __name__ == "__main__":
    mcp.run()
