# DevBridge

A development-only NeoForge mod that opens a small, token-protected HTTP API into a
running Minecraft instance, plus an MCP server that lets an AI agent on **another
machine** use it.

It is deliberately **independent of any particular mod**: it only speaks Minecraft /
NeoForge concepts (logs, commands, registries, block states, NBT, entities, reflection,
screenshots). Anything specific to the mod you are developing should be exposed by that
mod as a normal `/command` and driven through DevBridge's command endpoint.

```
┌──────────────────────┐   MCP (stdio)   ┌──────────────────────┐   HTTP+token over Tailscale   ┌─────────────────────┐
│ AI agent (Linux box) │ ◄─────────────► │ mcp-server/server.py │ ◄───────────────────────────► │ Minecraft + DevBridge│
└──────────────────────┘                 └──────────────────────┘        100.x.y.z:8787         │   (Windows PC)       │
                                                                                                 └─────────────────────┘
```

Targets **Minecraft 1.21.1 / NeoForge 21.1.233** (any 21.1.x should work).

---

## 1. Build the mod (once)

```bash
cd devbridge
./gradlew build          # first run downloads NeoForge + decompiles; takes a while
# -> build/libs/devbridge-0.6.0.jar  (build also runs selfCheck: names, marker, file moves)
```

(If you don't have the wrapper jar yet: `gradle wrapper` with any Gradle ≥ 8.8 installed, then use `./gradlew`.)

## 2. Load it in *your* mod's dev environment

Nothing in your mod project references DevBridge. Just make the jar available at runtime:

**Option A – drop the jar in the run folder.** Your mod's `runs/client/mods/` (or `run/mods/`,
whatever `gameDirectory` is). NeoForge loads mods from that folder like a normal launcher would.

**Option B – add it as a `localRuntime` dependency** in your mod's `build.gradle`, so it's
present in every run config but never leaks into your published artifact:

```groovy
dependencies {
    localRuntime files("libs/devbridge-0.6.0.jar")
}
```

(The MDK already defines the `localRuntime` configuration.)

Then `./gradlew runClient` as usual. On first start DevBridge writes
`config/devbridge.json` with a random token:

```json
{
  "bind": "127.0.0.1",
  "port": 8787,
  "allowReflect": true,
  "timeoutMillis": 15000,
  "logBufferSize": 5000,
  "token": "…48 hex chars…"
}
```

Every setting can also be overridden with `-Ddevbridge.<key>=…` or `DEVBRIDGE_<KEY>=…`,
which is handy inside a run config:

```groovy
runs {
    client {
        client()
        systemProperty 'devbridge.bind', '100.101.102.103'   // your Tailscale IP
        systemProperty 'devbridge.token', 'dev-token-change-me'
    }
}
```

## 3. Reach it from the other machine (Tailscale)

1. Install Tailscale on the Windows PC and on the Linux server, log both into the same tailnet.
2. Note the Windows machine's Tailscale IP (`tailscale ip -4` → `100.x.y.z`) or its MagicDNS name.
3. Set `"bind": "100.x.y.z"` in `config/devbridge.json` (binding only to the Tailscale
   address means nothing on your LAN or the internet can even see the port).
4. Windows Defender Firewall → new inbound rule → TCP 8787, scope: remote address `100.64.0.0/10`.
5. From the Linux box: `curl http://100.x.y.z:8787/ping` should answer.

Optionally lock it down further with a Tailscale ACL so only the server can hit port 8787.

## 4. Run the MCP server next to your agent

```bash
cd mcp-server
pip install -r requirements.txt          # or: uv sync
export DEVBRIDGE_URL=http://100.x.y.z:8787
export DEVBRIDGE_TOKEN=<token from devbridge.json>
python server.py                          # stdio transport
```

Claude Code / Claude Desktop config:

```json
{
  "mcpServers": {
    "devbridge": {
      "command": "python",
      "args": ["/path/to/devbridge/mcp-server/server.py"],
      "env": {
        "DEVBRIDGE_URL": "http://100.x.y.z:8787",
        "DEVBRIDGE_TOKEN": "…"
      }
    }
  }
}
```

The agent then gets 58 tools (`mc_log`, `mc_command`, `mc_block`, `mc_registry`,
`mc_screenshot`, `mc_reflect_invoke`, …). Run `mc_routes` for the raw route list.

---

## 5. Optional: DevHost (restart the game without the user)

DevBridge dies with the game, so it cannot swap jars or start Minecraft. `devhost/DevHost.java`
runs next to the launcher, in the user's desktop session, and offers fixed verbs over the same
kind of token-protected HTTP API (default port 8790, bound to the Tailscale address):
`/status`, `/stop` (asks DevBridge to save and quit, kills after a timeout), `/mods/install`
(upload with sha256; jars with the same modId move to `devhost-backup/`), `/mods/install-url`
(the same, downloaded by DevHost), `/mods/remove`,
`/launch` (runs the launcher's exported launch script), `/log`, `/crash`. No arbitrary commands.

```bat
rem Windows, once: put DevHost.java, devhost.properties and start-devhost.bat in one folder,
rem export the instance's launch script from the launcher, then
start-devhost.bat            rem shortcut in shell:startup to start it at logon
```

From the agent's machine (`devhost/devhostctl.py`, standard library only, settings in
`~/.config/devbridge/devhost.env`):

```bash
devhostctl.py deploy build/libs/mymod-1.0.jar   # stop -> install -> launch -> wait in world
devhostctl.py status | stop | launch --world W | logs | crash | shot out.png
devhostctl.py world ensure t1                   # game started if needed, t1 created if missing, opened
devhostctl.py worlds                            # saves, bridge-owned or not, snapshots
devhostctl.py world create|open|leave|save|snapshot|restore|reset|delete|snapshots|snapshot-delete ...
```

The `world` commands wait until the operation has finished and exit 1 with its error if it
failed (`--timeout`, default 600 s, goes before the action: `world --timeout 120 save`). `ensure`
needs DevHost only when the game is not running: it launches into the world if it exists, else to
the title screen, then creates it (`--preset`, `--game-mode`, `--rule NAME=VALUE`, `--time`, ... as
for `create`). Everything new talks to DevBridge; DevHost itself is unchanged.

Slow uplink to the PC: `/mods/install` uploads the jar over the agent's link to the PC, which can be
far slower than the PC's own downloads (11 KB/s against 100 Mbit/s was measured). `/mods/install-url`
(`url`, `name`, `sha256`, optional `proxy=host:port`, `timeoutSec`) has DevHost download the jar
itself, through an HTTP proxy on the PC if one is named, check it against the sha256 and install it
as an upload would. The sha256 travels over the token-protected API, so the URL may be plain http;
only http(s) URLs, no redirects, at most 64 MB. `devhostctl.py install` and `deploy` use it when
`DEVHOST_PULL_DIR` (a local directory) and `DEVHOST_PULL_URL` (the public URL that serves it) are
set: the jar is staged under a random folder name, pulled through `DEVHOST_PULL_PROXY` first and
then directly, removed again, and uploaded the old way if pulling fails or the installed DevHost
predates the route (`/ping` lists `features`). DevHost cannot update itself: replace `DevHost.java`
on the PC and restart it to get a new version.

`/launch` reopens the world that was open at the last `/stop` (or `world=`): it writes the folder
name to `config/devbridge-open-world.txt`, which DevBridge reads at startup and opens once the
title screen is up, so the launch script stays untouched. DevHost finds the game by the game
directory in its command line, the pid DevBridge reports on `/ping`, or java children of the
launch script. It is remote code execution by design too (it installs jars): keep the token
secret and the bind on Tailscale.

---

## HTTP API

All routes return `{"ok": true, "result": …}` or `{"ok": false, "error": "…", "trace": "…"}`.
Auth: `Authorization: Bearer <token>` (or `X-DevBridge-Token`). `/ping` needs no auth.
GET routes take query parameters; POST routes take a JSON object body. Either method works
for any route.

| Route | What it does |
|---|---|
| `GET /ping` | liveness |
| `GET /routes` | list all routes with descriptions |
| `GET /info` | versions, dist, server status, players, dimensions, tick time |
| `GET /mods` | loaded mods |
| `GET /registries` · `GET /registry?id=&namespace=&contains=` · `GET /registry/entry?id=&key=` | registry inspection |
| `GET /log?level=&logger=&contains=&since=&limit=` | in-memory ring buffer of **all** log output, with sequence numbers for polling |
| `POST /log/clear` · `GET /log/file?name=&lines=` | clear buffer / tail a file in `logs/` |
| `GET /crashes` · `GET /crashes/read?name=` | crash reports |
| `POST /command {command, as, dimension, x,y,z}` | run any command at op level 4, output captured, not broadcast |
| `GET /command/tree` · `GET /command/usage?name=` | discover commands |
| `GET /world/block?x=&y=&z=` | block state + block entity NBT |
| `GET /world/area?x1..z2&positions=` | block-state histogram of a box |
| `POST /world/block {x,y,z,state}` | set a block (command syntax incl. NBT) |
| `GET /world/entities` · `GET /world/entity?uuid=` | entities, optional NBT |
| `GET /player?name=` · `GET /players` | server-side player state incl. inventory |
| `GET /world/chunk` · `GET /world/biome` | chunk load status, biome |
| `GET /reflect/describe?class=` | fields/methods of any class |
| `POST /reflect/field {class, field, target}` | read a field |
| `POST /reflect/invoke {class, method, args, target}` | call a method |
| `GET /reflect/targets` | valid `target` strings |
| `GET /client/state` | fps, screen, local player, crosshair target *(client only)* |
| `GET /client/screenshot?maxWidth=` | PNG as base64 *(client only)* |
| `GET /client/screen` · `POST /client/screen/close` | inspect / close GUI *(client only)* |
| `POST /client/chat {text}` · `POST /client/look {yaw,pitch}` | act as the local player *(client only)* |
| `GET /client/keys` · `POST /client/key {names, action, ticks}` · `POST /client/key/release` | press, hold or release key mappings by name (move, jump, sprint, attack, use, hotbar, F5, ...) *(client only)* |
| `POST /client/hotbar {slot}` · `POST /client/perspective {mode}` · `POST /client/hud {hidden}` | hotbar slot, first/back/front camera, F1 *(client only)* |
| `POST /client/option {name, value, save}` | read or set an option such as `fov`, `renderDistance`, `gamma` *(client only)* |
| `POST /client/screen/click {widget \| x,y, button}` · `POST /client/screen/type {text, key}` | click and type in the open GUI *(client only)* |
| `GET /client/window` · `POST /client/window {width, height, x, y}` · `POST /client/mouse {grab}` | window size/position; watch mode (free mouse) or play *(client only)* |
| `GET /client/world` | open world (saves folder, name) and world operations: `busy`, `current`, `lastOperation`, `lastError` *(client only)* |
| `GET /client/worlds` | saves list: folder, name, gameMode, cheats, difficulty, open, `bridgeOwned`, snapshots *(client only)* |
| `GET /client/world/operation?id=` | one world operation: `running` (with phase) / `done` / `failed` (with error), result *(client only)* |
| `POST /client/world/leave` · `POST /client/world/open {folder}` | save and quit to title; open a save in watch mode (leaving the current one) *(client only, operation)* |
| `POST /client/world/create {folder, name, preset, gameMode, difficulty, cheats, seed, hardcore, structures, gameRules, time, open}` | new bridge-owned test world, opened in watch mode *(client only, operation)* |
| `POST /client/world/save` | save the open world now without leaving it *(client only, operation)* |
| `POST /client/world/snapshot {folder, name, overwrite, reopen}` · `GET /client/world/snapshots?folder=` | copy any save to `devbridge-snapshots/<folder>/<name>`; list *(client only, operation)* |
| `POST /client/world/restore {folder, name, reopen}` · `POST /client/world/reset {folder, reopen}` | bridge-owned only: put a snapshot back / recreate from the stored settings *(client only, operation)* |
| `POST /client/world/delete {folder}` · `POST /client/world/snapshot/delete {folder, name}` | bridge-owned, not open: move to `devbridge-trash/`; a snapshot to the trash *(client only)* |
| `POST /client/quit` | save and close the game *(client only)* |

Everything that touches game state runs on the server (or client) thread via
`submit()`; HTTP threads never touch the world directly.

## Test worlds

The agent makes and reuses its own disposable worlds instead of playing in the owner's.

- **Operations.** Open, leave, create, save, snapshot, restore, reset and delete run one at a time
  and outlast an HTTP call: the route returns `{"operation": {"id", "name", "folder"}}` at once
  (another operation while one runs gets 409). Wait with `GET /client/world/operation?id=` until
  `state` is `done` or `failed`; `GET /client/world` shows `busy`, `current.phase`,
  `lastOperation` and `lastError`. A world that fails to load (back at the title screen, or stuck
  on a prompt screen for 2 minutes) fails the operation with the last logged warnings.
- **Create.** `folder` is required and must not exist. Defaults: `preset` flat (`default` = normal
  terrain, `void` = vanilla's void superflat), creative, normal, cheats on, random seed,
  structures only with the `default` preset; `hardcore` means survival on hard. `gameRules` uses
  `/gamerule` names (`{"doDaylightCycle": false}`) and is checked first; `time` sets the day time
  once loaded. `open=false` goes back to the world that was open before (the game can only create
  a world by loading it). The folder gets `devbridge-world.json`: who created it and the settings,
  with the seed actually used.
- **Safety rule.** Restore, reset and delete only act on **bridge-owned** saves: a valid
  `devbridge-world.json` written for that very folder name (a copied or renamed save is not
  bridge-owned). Anything else is refused with 403 and left untouched; the owner's saves can be
  listed, opened, left, saved and snapshotted, never replaced or removed. Folder and snapshot names
  are one path segment by Windows rules (no `/ \ : * ? " < > |`, no `..` or leading dot, no trailing
  dot or space, no `CON`, `NUL`, `COM1`, ...).
- **Nothing is erased.** Snapshots are copies in `<gameDir>/devbridge-snapshots/<folder>/<name>`
  (an existing name needs `overwrite`, and the old one goes to the trash). Restore keeps the save
  it replaces, reset keeps the old world, delete keeps the world: all in
  `<gameDir>/devbridge-trash/<yyyyMMdd-HHmmss>-<what>-<folder>`. Empty the trash by hand.
- **Closed before copying.** A world that is open is left (saved, server stopped, `session.lock`
  released) before it is copied or replaced, and reopened afterwards (`reopen`, default: if it was
  open). A save whose `session.lock` is held by another game is refused. Copies go to a staging
  directory first and every move is a single rename inside the game directory, so a failure (a file
  held open by another program on Windows, say) leaves the old save in place and says so.

## Typical agent loop

1. `mc_log_clear` → reproduce (`mc_command "mymod debug spawn_thing"` or `mc_set_block`) →
   `mc_log level=WARN logger=mymod`.
2. `mc_block` / `mc_reflect_field target=blockentity:minecraft:overworld:x:y:z` to check internal state.
3. `mc_screenshot` to confirm rendering / GUI.
4. `mc_command "test runall"` if you use the GameTest framework.

## Gotchas

- **Client joined to a remote server**: `/command`, `/world/*` and `/player*` need a server
  in the same JVM (singleplayer, LAN host or dedicated server). A client connected to a
  remote server only has `/client/*`, logs, registries and client-side reflection. To drive
  the world *and* see it, install DevBridge on both the server and the client (different
  machines, or different ports) and register two MCP servers, one per URL.
- **Command output and WorldEdit**: `/command` captures what a command sends to its source.
  Mods that message the player directly (WorldEdit) skip that; read their replies with
  `/log?contains=CHAT` on the client instead. Pass `as` with the player name so WorldEdit
  uses that player's selection and undo history, and send `//set` as written.
- **Window and mouse on a shared desktop**: an automated launch (one with an open-world
  request) resizes the window to `automationWindow` (default `1600x900`, empty = leave it).
  With `keepMouseFree` (default true) a world DevBridge opens starts in *watch mode*: the game
  does not capture the user's cursor on its own (joining, closing a screen). A click into the
  game view is the user taking over: it captures the mouse for normal play, and that click does
  not attack. Leaving the window (Alt+Tab) returns to watch mode. The toggle key (default F8,
  in Controls under DevBridge) switches either way; `POST /client/mouse {grab}` does the same.
  A game started by hand behaves normally until F8 is pressed.
  `automationScreen` (default empty) can be set to `inventory` to open the player's inventory
  once such a world has been up for a second.
- **Held keys and focus**: keys are pressed through the game's key mappings, so the user's
  bindings don't matter. Continuous mining (holding attack) needs the window focused because
  vanilla requires a grabbed mouse; a click is enough in creative.
- **MCP SDK**: `server.py` uses `mcp.server.fastmcp`, which mcp 2.x renamed, so the
  dependency is pinned to `mcp<2`.
- **Pause**: in singleplayer the game pauses when the window loses focus, and the server
  thread stops ticking → game-thread calls time out. Press **F3+P** once in-game to disable
  pause-on-lost-focus (or run a dedicated server instead).
- **Dedicated server**: works, just without `/client/*`. Reflection target `minecraft` is unavailable.
- **`jdk.httpserver`**: DevBridge uses the JDK's built-in HTTP server. If you ever see
  `NoClassDefFoundError: com/sun/net/httpserver/HttpServer`, add `--add-modules jdk.httpserver`
  to the run's JVM args (not needed with standard launchers).
- **Security**: this is a remote code execution endpoint by design (reflection + op commands).
  Keep it bound to the Tailscale interface, keep the token secret, set `allowReflect=false`
  if you don't need it, and never ship the jar with your mod.

## Extending

New route = one line in an endpoint class:

```java
http.get("/my/thing", "description", req -> GameAccess.onServer(server -> { ... return json; }));
```

If a mod wants to contribute its own routes without DevBridge depending on it, the cleanest way
is for that mod to look up `dev.devbridge.DevBridge` reflectively — or, simpler, just register a
command and call it through `/command`.
