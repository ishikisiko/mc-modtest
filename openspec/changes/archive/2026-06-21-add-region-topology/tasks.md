## 1. Region profile data model & ruleset (JSON)

- [x] 1.1 Define the `region-profile` JSON shape under `src/main/resources/data/myvillage/worldgen/region_profile/`: per-region `id`, `display_name`, integer `tier`, `qi` range, `danger` range, `role` (`anchor` | `peripheral` | `walled`), and `admitted_subjects` (referencing known worldgen subjects — today `sect`).
- [x] 1.2 Author the topology ruleset JSON (e.g. `worldgen/region_topology.json`): `region_count` range `[5,7]`, configured `tier_range`, `tier_step` `N=5`, separator palette `{特殊山脉, 特殊海洋}`, and role rules (single centered anchor; walled = all-隔 + ≤1 关隘). Keep all tunables in data, not code.
- [x] 1.3 Author an initial region catalog (中州 anchor + peripheral regions + one walled 魔域-style region) sufficient to exercise every role and both separator types.

## 2. Constructive topology generator

- [x] 2.1 Add `tools/buildgen/region_topology.py` as the single shared module (data model load + rules + constructive algorithm + a documented seeded RNG), so the generator and validator agree on one source.
- [x] 2.2 Implement constructive generation: place the anchor at center, choose count (5–7), embed remaining regions in radial sectors; assign tier outward (`parent − random(0..N)`, clamped to range) so tier-step holds by construction.
- [x] 2.3 Build the 连 spanning tree over non-walled regions (global traversability by construction), then type remaining geometric-neighbour edges by rule (tier gap > N or incident walled → 隔 with a palette separator; else may be 连).
- [x] 2.4 Apply the walled-region rule (all incident 隔 except ≤1 连 marked as 关隘 entry); fix a most-constrained-first placement order so a satisfiable ruleset never dead-ends.
- [x] 2.5 Add `tools/generate_region_topology.py [seed]` that emits the region graph as JSON (regions with tier/role/position + typed edge list with separator types). Assert determinism (same seed → identical graph) and no re-roll/global backtracking.

## 3. Visualization

- [x] 3.1 Produce a human-reviewable layout visualization per seed (text/ASCII at minimum, PNG/SVG preferred): regions by tier, 连 edges, 隔 edges with separator type, walled regions and their 关隘. Wire it into the preview path consistent with `AGENTS.md` acceptance prep.

## 4. Validation & multi-seed survey

- [x] 4.1 Add `tools/validate_region_topology.py`: count in `[5,7]`, exactly one centered anchor, anchor holds top tier, 连-subgraph connects every non-walled region, every 连 edge tier-step ≤ N, each 隔 edge carries a legal palette separator, each walled region ≤1 连 (关隘) with the rest 隔, and same-seed determinism. A deliberately broken graph SHALL fail with the offending invariant; an unsatisfiable ruleset SHALL be reported explicitly (not loop).
- [x] 4.2 Run a multi-seed survey and write `reports/region_topology_validation.json` (count distribution, connectivity, tier spread, walled-region presence) plus a determinism check across repeated seeds.

## 5. Land data into mod resources

- [x] 5.1 Land the authored region-profile JSON + topology ruleset and an example generated graph + report under `src/main/resources/data/myvillage/worldgen/` so the data ships in the jar; confirm the offline generator and validator read those same files (no second source of truth).

## 6. Docs, version, and acceptance

- [x] 6.1 Add a `docs/ai-kb/` note for the region-topology model + OTG WorldConfig/FromImage mapping; list it in `docs/ai-kb/INDEX.md` with see-also to `07_neoforge_worldgen.md` and the new `region-profile` / `region-topology` specs; cite `docs/otg_worldgen_philosophy.md` as the design reference.
- [x] 6.2 Update `README.md` with how to run `tools/generate_region_topology.py` and `tools/validate_region_topology.py` and how to read the visualization; note this layer is offline-only (no in-game command / no runtime worldgen yet, so no `/myvillage` manual entry is added this change).
- [x] 6.3 Note in `AGENTS.md` the region-profile authoring convention: add regions/rules via the JSON catalog + ruleset (and the shared `region_topology.py`), not by branching in code.
- [x] 6.4 Bump the mod version per the `openspec/config.yaml` rule — large feature `0.13.0 → 0.14.0` — updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.
- [x] 6.5 Run the offline validators (region topology + existing suite) and the preview/acceptance prep per `docs/ai-kb/09_validation_checklist.md`; serve the preview aggregate and report the review URL.
