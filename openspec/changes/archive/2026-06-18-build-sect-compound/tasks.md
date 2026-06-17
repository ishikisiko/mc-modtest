## 1. Terraced plan (planner geometry)

- [x] 1.1 Add `tools/buildgen/sect.py` with a `_SectPlan` carrying `terraces` (ordered, each with elevation/bounds), `axis_cells`, `retaining_faces`, `axis_stairs`, `gallery_links`, `slots` (per-terrace, with importance tier), and `feature` (optional detached-spire variant).
- [x] 1.2 Derive the default 5-terrace skeleton (gate / disciple / assembly / scripture / summit), parametric on `terrace_count` (4–6), `terrace_rise`, `terrace_depth`, `terrace_width` with summit taper, and `axis_stair_w`; lay the single fall-line ritual axis from gate to summit.
- [x] 1.3 Assign slots per terrace with importance tier non-decreasing up the stack; pin principal hall + scripture pagoda to the top tiers; select sect `.nbt` pieces by terrace level (gate→sect_gate, disciple→disciple_quarters rows, summit→sect_main_hall, scripture→scripture_pavilion+pagoda).
- [x] 1.4 Place flanking volumes as mirrored pairs about the axis (disciple rows, paired pavilions, flanking bell/drum) and emit covered-gallery links with both endpoints between same/adjacent-terrace volumes.
- [x] 1.5 Emit retaining faces (height = inter-terrace rise) and on-axis stair flights between adjacent terraces; declare the summit `cliff_back` edge and place the principal hall against it.
- [x] 1.6 Define the detached-spire flying-bridge feature as 3 deterministic variants (differ on ≥2 of: detached volume, bridge span/shape, spire offset/bearing); select per-seed with an absence probability; emit the flying-bridge link with both endpoints.
- [x] 1.7 Export the terrace skeleton + geometry parameters (counts, per-terrace elevation/bounds, rise/depth/taper, axis-stair width, cliff-back height) as explicit plan outputs for `add-sect-worldgen`.

## 2. Plan validation (Python)

- [x] 2.1 Add `validate_sect_plan`: axis ordered + traversable gate→summit; importance non-decreasing up terraces with hall+pagoda at top tiers.
- [x] 2.2 Validate every covered-gallery and the flying-bridge feature has both endpoints on the volumes/terraces it joins.
- [x] 2.3 Validate the 3 feature variants are pairwise distinct on ≥2 axes; validate feature-absent plans are still complete.
- [x] 2.4 Assert same-seed reproducibility of terraces, axis, slots, links, and feature selection.

## 3. Realizer (Java)

- [x] 3.1 Add `src/main/java/com/example/myvillage/sect/SectGenerator.java` mirroring the `_SectPlan` geometry deterministically (no shared RNG).
- [x] 3.2 Add a `/myvillage sect [seed]` branch in `MyVillageMod`, reusing the chunk-ticket force-load + report-don't-skip path from `TownGenerator`.
- [x] 3.3 Carve + retain each terrace against the heightmap and build on-axis stair flights so terraces step the slope with no sub-footprint air gap; place the principal hall against the cliff-back edge.
- [x] 3.4 Place slotted volumes via the existing template path routed through the mod-fallback resolver; build covered galleries as block-placed covered walks with recorded endpoints.
- [x] 3.5 When the seed selects it, realize the detached-spire feature: place the detached volume on existing ground and span the flying bridge with endpoints on the compound and the detached volume.
- [x] 3.6 Add a Java realization validator mirroring `validate_sect_plan` (axis/importance/endpoints) and a same-seed Python/Java parity assertion; emit `reports/sect_generation_validation.json`.

## 4. Acceptance handoff

- [x] 4.1 Generate a sect-compound preview and refresh the preview aggregate `out/preview/index.html`; serve and report the review URL per `AGENTS.md`.
- [x] 4.2 Update `README.md` (`/myvillage sect` usage), `AGENTS.md`, and `CHANGELOG.md` in this change; build the mod jar when practical.
- [x] 4.3 Confirm the exported terrace skeleton + parameters are sufficient for `add-sect-worldgen` to derive terrain (review the contract with that change's design).
