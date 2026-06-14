## Context

`tools/preview_structure.py` is a zero-dependency, pure-stdlib renderer (it hand-writes PNG bytes; neither PIL nor numpy is installed). It loads voxels via `read_voxels()` into a `{(x,y,z): blockstate}` dict and resolves colors via `resolve_color()` against `block_colors.json`, then emits static PNGs: per-Y XZ slices + contact sheet, a fixed-angle `isometric.png` (painter's algorithm, interior-culled), and a legend.

Reviewers need to triage layout/massing on multi-story houses and large compounds before an in-game `/place` pass. Static images make that slow: no rotation, no floor isolation, no movable cut. The data is already in-process; this change reuses it to emit an interactive viewer while keeping the offline, no-toolchain ethos.

Representative sizes: `small_house_01` ≈ 328 non-air voxels; `cultivation_sect_001` ≈ 8.7k non-air voxels; largest town compounds estimated ~30–50k.

## Goals / Non-Goals

**Goals:**
- Emit one self-contained, offline `out/preview/<stem>/viewer.html` per structure.
- Support camera orbit/zoom/pan, Y-layer and block-base visibility toggles, and a draggable cross-section that shows a solid cut.
- Reuse `read_voxels()` / `resolve_color()` unchanged; keep all existing PNG outputs intact (additive only).
- No new Python dependencies.

**Non-Goals:**
- Textured / blockstate-accurate rendering (door facing, stair direction remain a `/place` concern).
- A combined multi-structure browser/index page (per-stem files only for now).
- Greedy meshing or base64-packed payload optimization (deferred until size demands it).
- Editing structures in the viewer; it is read-only inspection.

## Decisions

**1. Self-contained HTML + vendored three.js (over zero-lib WebGL and over .glb export).**
Three.js gives `OrbitControls`, instancing, and a clipping/visibility model for near-free, and ships as one offline `viewer.html`. A hand-rolled WebGL viewer would re-implement camera math and instancing for no benefit; a `.glb` export would drop the movable cross-section (generic glTF viewers can't do it), which is the feature motivating this change. Cost: vendor `tools/web/three.min.js` (~600KB), loaded by relative path — the only dent in the pure-stdlib purity, accepted because it stays fully offline.

**2. Keep all non-air voxels; do not interior-cull (over reusing `_is_exposed` culling).**
A movable cross-section must reveal a solid interior, which culling would hollow out. At ~8.7k voxels per sect and ~50k for the largest compounds, GPU instancing handles the full set trivially, so culling buys nothing. Bake every non-air voxel.

**3. `InstancedMesh` with per-instance color (over per-voxel meshes).**
One `BoxGeometry`, N instances, per-instance color from a deduplicated palette. This is what scales to 50k cubes at interactive frame rates. Per-voxel metadata (Y-layer, block-base index) is carried in parallel arrays for toggles.

**4. Cross-section by hiding instances past the plane (over GPU clipping planes).**
Filtering instances by coordinate shows the outer faces of the last remaining layer — the exact, continuous, rotatable evolution of today's `slice_yNN.png`, and it reads as a solid cut with no capping geometry. GPU `clippingPlanes` would give smooth mid-cube cuts but hollow faces unless we add capping; not worth the complexity for v1.

**5. Plain inline JSON payload (over base64-packed typed arrays).**
Simpler to generate and debug. A 50k-voxel `[x,y,z,idx]` payload is a few MB of HTML — acceptable. Packing is a later optimization if file size becomes a problem.

**6. CLI shape mirrors existing outputs.**
Viewer is written by default alongside PNGs; `--all` emits one per stem. Add flags to scope/skip (e.g. a `--no-viewer` / `--viewer-only` pair consistent with existing `--iso-only` / `--slices-only`).

## Risks / Trade-offs

- **Vendored JS purity cost** → Confine to `tools/web/`, commit once, load by relative path; document provenance/version. Still no Python deps, still offline.
- **Large-compound HTML size (multi-MB)** → Bake non-air voxels only; document `--no-viewer` to skip; keep base64 packing as a known follow-up if it bites.
- **Browser/WebGL availability for reviewers** → PNG outputs remain the fallback and are unchanged, so no reviewer loses capability.
- **Misreading the viewer as blockstate-accurate** → Keep the explicit voxel-color caveat in README and any in-viewer header text; cross-section/toggles don't change that limitation.
- **three.js version drift / API churn** → Pin a specific vendored version; viewer code targets that version only.

## Open Questions

- Exact CLI flag names for scoping the viewer (`--no-viewer` vs folding into existing `--*-only` flags).
- Whether the viewer should also surface the legend (block id ↔ color) in-page, or rely on the existing `legend.png`/`legend.txt`.
- Default cross-section axis and whether to remember the last-used axis/threshold across reloads.
