## Why

The offline preview tool (`tools/preview_structure.py`) only emits static images: per-Y XZ slices, a contact sheet, and a single fixed-angle `isometric.png`. Reviewers cannot rotate the model, isolate a floor, or move the cross-section, so triaging layout and massing on multi-story houses and large courtyard/sect compounds is slow and easy to misread. We already have the voxel data and color resolution in-process — we just throw it at PNGs. An interactive viewer turns the same data into something a reviewer can actually inspect, while staying fully offline.

## What Changes

- Add an interactive 3D viewer output to `tools/preview_structure.py`: a single self-contained `out/preview/<stem>/viewer.html` per structure that opens by double-click with no network access.
- Bake the voxel data (non-air only) plus a deduplicated color palette and per-voxel block-base metadata into the HTML; render it with `three.js` `InstancedMesh` (GPU instancing) sized to handle ~50k voxels (largest town compounds).
- Provide three interactions in the viewer:
  - **Orbit / zoom / pan** via `OrbitControls`.
  - **Layer / block toggles** — show/hide by Y-layer or by block base id.
  - **Draggable cross-section** — a slider hides instances past a movable X/Y/Z plane, the continuous, rotatable evolution of today's `slice_yNN.png`.
- Vendor `three.min.js` into `tools/web/` (committed once) so the viewer loads via a relative path and works with no internet.
- Keep all existing PNG outputs (slices, isometric, legend) unchanged; the viewer is additive. `--all` emits one `viewer.html` per structure, matching the current per-stem layout.
- Document the new output in `README.md`, keeping the existing caveat that this remains a voxel-color preview — blockstate detail (door facing, stair direction, trapdoor state) still needs an in-game `/place template` check.

## Capabilities

### New Capabilities
- `interactive-preview`: An offline, self-contained interactive 3D viewer for structures, with camera orbit, layer/block visibility toggles, and a movable cross-section plane.

### Modified Capabilities
<!-- None: the existing offline preview has no spec, and the static PNG outputs are unchanged. -->

## Impact

- **Code**: `tools/preview_structure.py` — new `render_interactive_html()` reusing existing `read_voxels()` / `resolve_color()`; new CLI flags to scope/skip the viewer output.
- **New assets**: `tools/web/three.min.js` (vendored, ~600KB) and an HTML/JS viewer template.
- **Docs**: `README.md` preview section; `AGENTS.md` acceptance-prep note if the viewer becomes part of the standard preview pass.
- **Dependencies**: No new Python dependencies (still pure stdlib). One vendored browser-side JS library, loaded offline via relative path.
- **Output size**: `viewer.html` for the largest compounds is a few MB of inline JSON; acceptable, with base64-packed typed arrays available later if needed.
