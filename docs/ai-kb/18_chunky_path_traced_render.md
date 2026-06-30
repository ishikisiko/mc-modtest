# Chunky Path-Traced Rendering (Headless)

> **Status:** pipeline verified end-to-end on 2026-06-30 (PNG produced); camera framing not yet solved. This doc is the authoritative record of the correct headless render pipeline plus the open camera-framing issue, so the next session can resume without re-discovering it.
>
> See-also [17_chunky_acceptance.md](17_chunky_acceptance.md) (the *block-pregen* Chunky acceptance flow — a different tool that happens to share the name), and the `chunky-acceptance-automation` change spec.

## TL;DR — there are two "Chunky"s

| | Chunky (renderer) | Chunky (block-pregen mod) |
|---|---|---|
| Project | `llbit/chunky` (path tracer) | `pop4959/chunky` (server plugin) |
| Artifact | `ChunkyLauncher.jar` (standalone) | `Chunky-NeoForge-1.4.23.jar` (in-game mod) |
| Purpose | Off-line path-traced 3D rendering → PNG | Pre-generate world chunks (server lag reduction) |
| Headless | ✅ Yes, `java -jar ChunkyLauncher.jar -render Scene` | ✅ Yes, via RCON `chunky start` |
| Produces images | ✅ PNG via `-snapshot` | ❌ No |
| Used by this repo's acceptance? | ❌ Not yet | ✅ `tools/run_chunky_acceptance.py` (Stages 1-4) |

The existing `tools/run_chunky_acceptance.py` **uses the block-pregen mod**, so its Stages 1-4 only prove "chunks load without crashing" — they produce **no visual evidence**. To get a path-traced PNG of a placed structure, you must run the **renderer** (this doc), not the block-pregen acceptance flow.

## Verified pipeline (renderer, headless, Windows)

Setup (one-time; artifacts cache under `chunky-render/` so they survive reruns):

```bat
mkdir chunky-render
cd chunky-render
:: 1. Launcher (set HTTPS_PROXY=http://127.0.0.1:7897 if maven/modrinth is blocked)
powershell -Command "Invoke-WebRequest 'https://chunkyupdate.lemaik.de/ChunkyLauncher.jar' -OutFile 'ChunkyLauncher.jar' -Proxy 'http://127.0.0.1:7897'"
:: 2. Chunky core 2.4.6 (lands in lib/)
set "JAVA_TOOL_OPTIONS=-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"
java -Dchunky.home="%CD%" -jar ChunkyLauncher.jar --update
:: 3. MC 1.21.1 block textures (lands in resources/minecraft.jar)
java -Dchunky.home="%CD%" -jar ChunkyLauncher.jar -download-mc 1.21.1
```

Render + snapshot:

```bat
set "JAVA_TOOL_OPTIONS="
java -Dchunky.home="E:\Code\mc-modtest\chunky-render" -jar ChunkyLauncher.jar ^
    -scene-dir "E:\Code\mc-modtest\chunky-render\scenes" ^
    -render test_house -f -target 15 -threads 6
java -Dchunky.home="E:\Code\mc-modtest\chunky-render" -jar ChunkyLauncher.jar ^
    -scene-dir "E:\Code\mc-modtest\chunky-render\scenes" ^
    -snapshot test_house "E:\Code\mc-modtest\chunky-render\test_house.png"
```

`-Dchunky.home` **must be absolute** — CMD `%CD%` inside a `cd /d` chain can resolve to the repo root and scatter `lib/` + `resources/` + `chunky-launcher.json` there (we hit this; it pollutes the repo root). The `-scene-dir` flag is also mandatory because the launcher does not honor `chunky.home` for scene discovery the way the docs imply.

## scene.json — correct field names (verified by decompiling Scene.class)

These are the **authoritative** field names from the 2.4.6 constant pool / `javap`. Getting them wrong gives the silent "Could not load chunks (no world found for scene)" failure with no other clue.

| Field | Type | Notes |
|---|---|---|
| `sdfVersion` | int | **Must be `9`** (Scene.SDF_VERSION). Missing → "Old scene version detected!" and the scene may not load. |
| `name` | string | Must equal the scene directory name. |
| `world` | string | **`world`, not `worldPath`.** Absolute path to the **save root** (the dir containing `level.dat`), NOT `region/`. Chunky appends `/region` itself. |
| `dimension` | int | 0 = overworld. (`dimension`, not `worldDimension`.) |
| `chunkList` | array of `[x, z]` | **Chunk** coords (block // 16), not block coords. |
| `camera` | object | See format below. |
| `sppTarget` / `width` / `height` | int | `sppTarget` (not `targetSpp`) is what Chunky writes back. |

`camera` **object** format (Chunky rewrites any other shape to position=(0,0,0)!):

```json
"camera": {
  "name": "camera 1",
  "projectionMode": "PINHOLE",
  "fov": 70.0,
  "position":    { "x": 6.0, "y": 88.0, "z": 170.0 },
  "orientation": { "roll": 0.0, "pitch": 0.0, "yaw": 3.141592653589793 }
}
```

- `position` / `orientation` are **nested objects**, not the flat arrays/`pitch`,`yaw` siblings the older docs show.
- `yaw`/`pitch`/`roll` are **radians**, not degrees.
- If you hand-write a flat `camera` block, Chunky silently resets position to (0,0,0) on save → you render empty sky. This bit us repeatedly.

## Known traps (all hit during verification)

1. **`world` field is erased after first load.** Once Chunky successfully loads chunks it writes the scene back with `world` removed. If you then delete the `.octree2` and re-render, it fails with "no world found" because the field is gone. **Mitigation:** keep `world` in a pristine template scene.json, and after the first successful load **do not delete the octree** — only change camera and re-render (camera changes do not require chunk reload).
2. **`-reload-chunks` needs the `world` field** — so it only works on a freshly-written scene, never on one Chunky has already rewritten.
3. **Renderer ≠ block-pregen.** `run_chunky_acceptance.py` Stage 1-4 output is JSON only; it will never produce a PNG regardless of arguments.
4. **Two-pass:** `-render` writes the `.dump`; `-snapshot` reads the dump and writes the PNG. Both are needed.
5. **`-f` (force)** is required or headless render silently skips scenes it thinks are already at target SPP.

## Verification record (2026-06-30)

- Source world: `run-acceptance/chunky_stage1_world` (the Stage 2 acceptance world, contains `small_house_001` placed at (0,79,192)).
- Block presence confirmed independently via `nbt` lib: chunk (0,12), section Y=4–5 holds `oak_planks` / `oak_log` / `oak_door` / `cobblestone` / `spruce_stairs` / `oak_trapdoor` etc. — the house **is** in the region file.
- A path-traced PNG **was produced** (`chunky-render/test_house.png`, ~12-17 KB, "Saved snapshot" confirmed in launcher log) — the renderer pipeline is functional.
- **The PNG was empty sky.** Root cause: camera framing. Building blocks are in the octree, but none of the yaw values tried (0, π/2, -2.55, π) put the house in frame. The exact `yaw`/`pitch` sign convention for `orientation` was not nailed down before stopping.

## Open work (next session)

1. **Solve camera framing.** Two reliable paths were identified but not executed:
   - **(a) Sweep yaw:** with octree intact, render snapshots at yaw ∈ {0, π/2, π, -π/2}, pitch ∈ {0, ±π/8}. One frame must contain the house; read the convention off the hit and compute framing from there.
   - **(b) Java API:** compile a small program against `chunky-core-2.4.6.jar` calling `Scene.loadChunks(world, chunks)` + `camera.setView(yaw, pitch, roll)` + `RenderManager`, bypassing JSON parsing entirely. Sketched in the chat history; unverified.
2. **Wrap as a tool.** Once framing is solved, encapsulate as `tools/render_structure.py` — input: structure id + placement → place in an isolated world → write scene.json from a pristine template → render → snapshot → emit PNG path. Then optionally a Stage 5 in `run_chunky_acceptance.py`.
3. **Decide naming.** The existing `run_chunky_acceptance.py` is really "block-pregen acceptance"; the path-tracer is a separate concern. Consider renaming or at least documenting the split prominently (this doc is that documentation).

## Cleanup note

The `chunky-render/` directory (launcher, `lib/`, `resources/minecraft.jar`, scenes, dumps) is large and **git-ignored as generator output** — do not commit it. The `chunky-launcher.json` and `lib/` that an early `--update` (with a mis-resolved `%CD%`) wrote to the **repo root** should be deleted if present; only the `chunky-render/` copy is canonical.
