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
# -> build/libs/devbridge-0.1.0.jar
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
    localRuntime files("libs/devbridge-0.1.0.jar")
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

The agent then gets 33 tools (`mc_log`, `mc_command`, `mc_block`, `mc_registry`,
`mc_screenshot`, `mc_reflect_invoke`, …). Run `mc_routes` for the raw route list.

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

Everything that touches game state runs on the server (or client) thread via
`submit()`; HTTP threads never touch the world directly.

## Typical agent loop

1. `mc_log_clear` → reproduce (`mc_command "mymod debug spawn_thing"` or `mc_set_block`) →
   `mc_log level=WARN logger=mymod`.
2. `mc_block` / `mc_reflect_field target=blockentity:minecraft:overworld:x:y:z` to check internal state.
3. `mc_screenshot` to confirm rendering / GUI.
4. `mc_command "test runall"` if you use the GameTest framework.

## Gotchas

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
