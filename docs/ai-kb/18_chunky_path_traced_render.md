# Chunky Path-Traced Rendering (Headless)

> **Status:** pipeline verified end-to-end on 2026-07-01 with `tools/render_structure.py` producing upright four-view PNGs from the Stage 2 acceptance world. The renderer can now load world chunks, solve camera yaw/pitch/roll, render, snapshot, and emit a manifest without manual camera tuning.
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

`-Dchunky.home` **must be absolute** — CMD `%CD%` inside a `cd /d` chain can resolve to the repo root and scatter `lib/` + `resources/` + `chunky-launcher.json` there (we hit this; it pollutes the repo root). The `-scene-dir` flag is still needed for initial scene lookup, but Chunky 2.4.6 later resolves save/dump/snapshot paths through `PersistentSettings.getSceneDirectory()`, so `tools/render_structure.py` also writes `chunky-render/chunky.json` with `"sceneDirectory": "<out>"` before rendering.

## scene.json — correct field names (verified by decompiling Scene.class)

These are the **authoritative** field names from the 2.4.6 constant pool / `javap`. Getting them wrong gives the silent "Could not load chunks (no world found for scene)" failure with no other clue.

| Field | Type | Notes |
|---|---|---|
| `sdfVersion` | int | **Must be `9`** (Scene.SDF_VERSION). Missing → "Old scene version detected!" and the scene may not load. |
| `name` | string | Must equal the scene directory name. |
| `world` | object | **`world`, not `worldPath`.** Shape is `{ "path": "<absolute save root>", "dimension": 0 }`; the save root contains `level.dat`, not `region/`. Chunky appends `/region` itself. A bare string is ignored by `Scene.fromJson()` and causes `no world found for scene`. |
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
  "orientation": { "roll": 3.141592653589793, "pitch": 0.0, "yaw": 3.141592653589793 }
}
```

- `position` / `orientation` are **nested objects**, not the flat arrays/`pitch`,`yaw` siblings the older docs show.
- `yaw`/`pitch`/`roll` are **radians**, not degrees.
- `tools/render_structure.py` uses `roll=π`; this keeps the center ray fixed but flips Chunky's pinhole image Y so world-up reads as image-up in generated PNGs.
- If you hand-write a flat `camera` block, Chunky silently resets position to (0,0,0) on save → you render empty sky. This bit us repeatedly.

## Known traps (all hit during verification)

1. **`world` field is an object and is erased after first load.** Once Chunky successfully loads chunks it writes the scene back with `world` removed. If you then delete the `.octree2` and re-render, it fails with "no world found" because the field is gone. **Mitigation:** keep a pristine template scene.json with `world: {path, dimension}` and rewrite it before a fresh `-reload-chunks` render.
2. **`-scene-dir` is not enough for dumps/snapshots.** `SynchronousSceneManager.resolveSceneDirectory(sceneName)` uses `PersistentSettings.getSceneDirectory()`, not only the CLI `-scene-dir`. Set `chunky.home/chunky.json` key `sceneDirectory` to the output root, and use native layout `<out>/<scene_name>/<scene_name>.json`.
3. **`-reload-chunks` needs the `world` object** — so it only works on a freshly-written scene, never on one Chunky has already rewritten.
4. **Renderer ≠ block-pregen.** `run_chunky_acceptance.py` Stage 1-4 output is JSON only; it will never produce a PNG regardless of arguments.
5. **Two-pass/fallback:** `-render` may write an auto-snapshot under `<scene>/snapshots/`; otherwise `-snapshot` reads the dump and writes the PNG. `tools/render_structure.py` tries auto-snapshot first, then restores the pristine scene JSON and runs `-snapshot`.
6. **`-f` (force)** is required or headless render silently skips scenes it thinks are already at target SPP.
7. **Bbox-only `chunkList` clips the world.** Chunky renders unloaded chunks as empty background. `tools/render_structure.py` now builds a per-view chunk rectangle covering the structure bbox, camera position, line of sight, and `--chunk-pad` margin (default 4), so four-view shots include surrounding terrain.

## Verification record (2026-06-30)

- Source world: `run-acceptance/chunky_stage1_world` (the Stage 2 acceptance world, contains `small_house_001` placed at (0,79,192)).
- Block presence confirmed independently via `nbt` lib: chunk (0,12), section Y=4–5 holds `oak_planks` / `oak_log` / `oak_door` / `cobblestone` / `spruce_stairs` / `oak_trapdoor` etc. — the house **is** in the region file.
- A path-traced PNG **was produced** (`chunky-render/test_house.png`, ~12-17 KB, "Saved snapshot" confirmed in launcher log) — the renderer pipeline is functional.
- **The PNG was empty sky.** Root cause: camera framing. Building blocks are in the octree, but none of the yaw values tried (0, π/2, -2.55, π) put the house in frame. The exact `yaw`/`pitch` sign convention for `orientation` was not nailed down before stopping.

## Verification record (2026-07-01)

Final command:

```bash
python3 tools/render_structure.py \
  --world run-acceptance/chunky_stage1_world \
  --anchor 0 79 192 \
  --search-radius 24 \
  --launcher chunky-render/ChunkyLauncher.jar \
  --chunky-home chunky-render \
  --views front right back left \
  --spp 5 \
  --threads 6 \
  --out chunky-render/renders_four_upright
```

Result:

- Scanned bbox: `[-19,63,178]..[12,91,213]`, size `32x29x36`, `309` structure blocks.
- Chunky loaded per-view chunk rectangles (28 chunks per view for this fixture) after `world` was changed to `{path, dimension}` and `chunky.json.sceneDirectory` was pointed at `chunky-render/renders_four_upright`.
- PNGs produced: `chunky-render/renders_four_upright/view_{front,right,back,left}/view_*.png` (git-ignored).
- Manifest produced: `chunky-render/renders_four_upright/render_manifest.json`; `overall_framing_ok: true`.
- Local visual inspection confirmed all four rendered images contain the placed house, are upright (`roll=π`), and no longer show the earlier bbox-only empty-background clipping.
- PNG assessment added an edge metric (`edge_mean`, `strong_edge_ratio`) so a smooth sky gradient no longer passes just because luminance stdev is high.

## Open work (next session)

1. **Optional acceptance integration.** Add a separate renderer report link only after deciding how it should relate to `tools/write_visual_acceptance_report.py`. Keep it distinct from `tools/run_chunky_acceptance.py`, which remains the block-pregen/RCON acceptance flow.
2. **Higher quality review renders.** Low-SPP (`--spp 5`) is enough for automation smoke checks. Manual visual review should use a higher SPP once the camera target/world fixture is final.

## Cleanup note

The `chunky-render/` directory (launcher, `lib/`, `resources/minecraft.jar`, scenes, dumps) is large and **git-ignored as generator output** — do not commit it. The `chunky-launcher.json` and `lib/` that an early `--update` (with a mis-resolved `%CD%`) wrote to the **repo root** should be deleted if present; only the `chunky-render/` copy is canonical.
