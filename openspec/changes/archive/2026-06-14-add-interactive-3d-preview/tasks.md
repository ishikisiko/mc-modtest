## 1. Vendor the rendering library

- [x] 1.1 Add `tools/web/three.min.js` (pinned version) plus the OrbitControls helper for that version
- [x] 1.2 Record the three.js version and source in a `tools/web/README.md` or header comment for provenance
- [x] 1.3 Confirm the file loads from a `file://` path with no network (open a trivial test page offline)

## 2. Build the payload in preview_structure.py

- [x] 2.1 Add `build_viewer_payload(size, voxels)` that keeps all non-air voxels and produces: a deduplicated `palette` (`[[r,g,b],...]`) via `resolve_color()`, a packed `voxels` array (`[x,y,z,paletteIdx]`), and per-voxel `blockBase` index + Y for toggles
- [x] 2.2 Add the sorted list of unique block bases (for the block-toggle UI) to the payload
- [x] 2.3 Unit-check payload counts against `info.txt` non-air totals for `small_house_01` and `cultivation_sect_001`

## 3. Viewer template and rendering

- [x] 3.1 Create the HTML/JS viewer template (relative `<script src>` to vendored three.js, inline `<script>` payload placeholder)
- [x] 3.2 Render voxels as a single `InstancedMesh` (one BoxGeometry, per-instance color from palette) centered in the scene
- [x] 3.3 Wire `OrbitControls` for orbit/zoom/pan with a sensible default camera framing the bounding box

## 4. Interactions

- [x] 4.1 Cross-section: axis selector (X/Y/Z) + slider that hides instances past the plane in real time (solid cut)
- [x] 4.2 Y-layer toggle: range control that shows only voxels within the selected layer band
- [x] 4.3 Block-base toggle: per-block checkboxes that hide/show all voxels of a base id
- [x] 4.4 Ensure cross-section, layer, and block toggles compose (combined visibility set, no conflicts)

## 5. Wire into render_one and CLI

- [x] 5.1 Add `render_interactive_html(size, voxels, out_dir)` that emits `out/preview/<stem>/viewer.html` by injecting the payload into the template and copying/referencing the vendored library path
- [x] 5.2 Call it from `render_one` so the viewer is written alongside existing PNGs by default
- [x] 5.3 Add CLI flags to scope/skip the viewer (consistent with existing `--iso-only` / `--slices-only`); list `viewer.html` in `info.txt` outputs
- [x] 5.4 Verify `--all` emits one `viewer.html` per stem

## 6. Docs and acceptance

- [x] 6.1 Update `README.md` preview section to describe `viewer.html` and its interactions, keeping the voxel-color / blockstate `/place` caveat
- [x] 6.2 Update `AGENTS.md` acceptance-prep note if the viewer joins the standard preview pass
- [x] 6.3 Manually verify offline: open a generated `viewer.html` for a sect and a town compound, exercise orbit, cross-section, and both toggle types
