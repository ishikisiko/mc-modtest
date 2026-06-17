## Why

The cultivation **sect (宗门)** is fully described in specs (`cultivation-mountain-siting`, `settlement-group`) and has shipped building pieces (`sect_gate`, `sect_main_hall`, `scripture_pavilion`, `disciple_quarters`, `alchemy_room`, `pagoda`, `pavilion`, `bell_drum_tower`), but **there is no runtime that assembles a terraced sect compound.** `TownGenerator` is town-only; `MyVillageMod` exposes no sect command. So the `cultivation_sect` settlement group's "terraced/axial layout strategy" exists on paper with no realizer behind it. Before any worldgen can place a sect (the follow-on `add-sect-worldgen` change), the compound itself must be buildable and acceptable on its own — assembled at a chosen location, reviewable in-world, deterministic on a seed.

This change builds that assembler and exposes it as a command, mirroring the town's planner/realizer pattern, so "what a sect looks like" is settled before "where the world puts one."

## What Changes

- **Add a terraced axial sect-compound plan** (deterministic, seed-driven) above the building layer: an ordered stack of terraces ascending a fall-line, with a single ritual axis from the mountain gate (山门) at the foot to the principal hall (主殿) at the summit. The default skeleton is 5 terraces (gate / disciple / assembly / scripture / summit), parametric on terrace count, rise, depth, width taper, axis-stair width, and cliff-back height.
- **Grade slot importance by terrace level** (per `cultivation-mountain-siting`): higher terraces receive higher importance tiers, taller massing, and finer roof grade; the principal hall and scripture pagoda take the compound's top tiers. Building-piece selection is driven by terrace level, not random.
- **Place flanking volumes symmetrically about the axis** (厢房-style disciple rows, paired pavilions, flanking bell/drum), connected along terrace edges by **covered galleries (廊)** recorded as circulation links with endpoints.
- **Back the summit against a cliff and step the compound with retaining faces.** Each terrace meets the next through a retaining course and an on-axis stair flight; the principal hall sits against the cliff-back edge of the summit terrace.
- **Add a detached-spire flying-bridge feature as a first-class compound form with 3 variants.** A volume (e.g. a 悟道/丹室 pavilion) sits on a detached outcrop reachable only by a **flying bridge (飞桥)** spanning a gap; the feature ships as 3 deterministic form variants (differing on which volume is detached, bridge span/shape, and spire offset). It is selected per-seed and MAY be absent on a given seed. (Its terrain — the solitary peak — and worldgen appearance/command land in `add-sect-worldgen`.)
- **Expose `/myvillage sect [seed]`** to plan and build a complete sect compound at the player's location, carving its own terraces and retaining against terrain (acquiring chunk-load tickets across the footprint as `/myvillage town` does), reporting any extent it cannot build rather than silently skipping. Runtime placement routes palette ids through the existing mod-fallback resolver.

## Capabilities

### New Capabilities
- `sect-compound-layout`: A deterministic terraced axial sect-compound plan SHALL compose an ordered terrace stack with a single ground-to-summit ritual axis, importance graded by terrace level, symmetric flanking volumes joined by covered galleries, a cliff-backed summit, retaining/stair links between terraces, and an optional detached-spire flying-bridge feature shipped as 3 deterministic form variants.
- `sect-compound-realization`: The mod SHALL expose `/myvillage sect [seed]` that plans and builds a complete sect compound at the player's location against terrain — carving terraces with retaining and on-axis stairs, force-loading the planned footprint, routing palette ids through the mod-fallback resolver, and reporting any extent it cannot build — reproducibly per seed.

### Modified Capabilities
- `cultivation-mountain-siting`: Bind the spec's abstract terrace/axis/importance/link/siting-context requirements to a concrete realized compound — establishing the default terrace skeleton, the parametric geometry the compound exposes, and that covered galleries and the flying-bridge feature are realized circulation/structure with recorded endpoints (not decoration).
- `settlement-group`: Establish that the `cultivation_sect` group's terraced/axial layout strategy resolves to the `sect-compound-layout` plan and is realized by `/myvillage sect`, so selecting the group produces a terraced compound rather than a single exported block.

## Impact

- Code (planner): new `tools/buildgen/sect.py` — terraced axial plan (`_SectPlan` terraces, axis, retaining/stair links, gallery links, flanking slots, detached-spire variants), mirroring `town.py`'s deterministic style; a `validate_sect_plan` paralleling `validate_town_plan`.
- Code (realizer): new `src/main/java/com/example/myvillage/sect/SectGenerator.java` (analogous to `TownGenerator`) plus a `/myvillage sect` branch in `MyVillageMod`, reusing the chunk-ticket force-load path, the template-load mod-fallback path, and site-fit/retaining helpers.
- Assets: reuses shipped sect `.nbt` pieces; no new authored structures required (galleries, retaining, stairs, and the flying bridge are block-placed by the realizer). The detached-spire pavilion reuses an existing pavilion/pagoda piece.
- Specs: new `sect-compound-layout`, `sect-compound-realization`; `cultivation-mountain-siting` and `settlement-group` extended (not contradicted).
- Validation/reports: new `reports/sect_generation_validation.json` (axis ordering, importance-by-terrace, gallery/bridge endpoints, same-seed reproducibility); sect compound preview added to the preview aggregate.
- Docs: `README.md` (`/myvillage sect` usage) and `AGENTS.md`/`CHANGELOG.md` updated in this change.
- Downstream: `add-sect-worldgen` consumes this assembler — it sites the compound, derives the mountain from the terrace profile, and places the solitary peak under the flying-bridge feature.
