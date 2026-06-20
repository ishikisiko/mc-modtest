## Why

The mod's worldgen has no macro layer. The one self-generating subject (the sect) spawns uniformly wherever its biome tag matches — there is no notion of 洲/域, no tier gradient, no rule for how regions relate. As more self-generating subjects arrive (sect today; town, 秘境, 遗迹 next), there is no **格局** to place them into: the world has no designed geography and no replayable-but-coherent structure. OTG's WorldConfig / FromImage layer is exactly this missing top of the stack. This change adds it in the form OTG §19 recommends — **offline-first**, driving generation and validation as data before any runtime chunk-gen — so the world gains a designed geography without prematurely rewriting terrain generation.

## What Changes

- **Introduce a region (洲) profile data model.** Each region declares a tier, a qi range, a danger range, a placement role (`anchor` / `peripheral` / `walled`), and which worldgen subjects it admits. Authored as JSON under the mod's worldgen data so a future runtime placement-director can read the same files.
- **Introduce a constrained-random region topology generator** that produces a 洲际图 (region graph) per world seed:
  - **5–7 regions** (fixed range), with the **中州 anchor at map center**.
  - Relations are **randomized under rules** (not authored pairwise): tier is assigned outward from the anchor with a **tier-step limit N = 5**; edges are typed **连 (passable)** or **隔 (separated)** where the separator palette is **{特殊山脉, 特殊海洋}**.
  - Built **constructively**: a 连 **spanning tree** guarantees the world is globally traversable, then geometric-neighbour edges are rule-typed (large Δtier or a walled region → 隔; otherwise 连). Because connectivity and the tier-step are *constructed*, generation is **seed-deterministic and never re-rolls**.
- **Walled-region rule (魔域-style).** A region may force all incident edges to 隔 with at most one 关隘 entry, so a high-danger region reads as sealed off rather than randomly disconnected.
- **Offline-first delivery (OTG §19).** The generator ships as a `tools/` script that emits the region graph + a visualization, checked by an offline validator. **No** runtime chunk-gen, **no** biome takeover, **no** terrain blocks, **no** in-game placement in this change — those are explicitly deferred.
- **Land the data.** Region-profile JSON (the authored region catalog + rules) and an example generated graph/report are written into `src/main/resources/data/myvillage/worldgen/` so the data exists in the tree and ships in the jar.

## Capabilities

### New Capabilities
- `region-profile`: a per-region (洲) data model — tier, qi/danger ranges, placement role, and admitted worldgen subjects — populated by the topology generator and consumable by future placement/biome layers.
- `region-topology`: a constrained-random region-graph generator that lays out 5–7 regions with a centered anchor and rule-governed 连/隔 relations, built constructively (spanning-tree connectivity + rule-typed neighbour edges), seed-deterministic and re-roll-free, emitted offline with a visualization.

### Modified Capabilities
- `validation`: add region-topology validation — region count within range, exactly one centered anchor, tier-step ≤ N on 连 edges, traversability of the 连-subgraph, the walled-region rule, separator-palette legality, and seed-determinism.

## Impact

- **Tooling**: new `tools/generate_region_topology.py` (graph generator + text/PNG visualization) and `tools/validate_region_topology.py`; the shared rules/algorithm in a new `tools/buildgen/region_topology.py` module so generation and validation use one source.
- **Data**: new `src/main/resources/data/myvillage/worldgen/region_profile/` (authored region catalog + topology rules) and an example generated graph + report. JSON (not a `groups.py`-style Python table) so a future runtime placement-director (Java) can read the same files.
- **Code (Java)**: none this change — offline-first. The future runtime placement-director will consume these profiles.
- **Specs**: new `region-profile`, `region-topology`; `validation` extended.
- **Reports**: new `reports/region_topology_validation.json` plus a multi-seed survey (count distribution, connectivity, tier spread, walled-region presence).
- **Docs**: new `docs/ai-kb/` note (region-topology model + OTG WorldConfig/FromImage mapping) listed in `docs/ai-kb/INDEX.md` with see-also to `07_neoforge_worldgen.md` and the new specs; `docs/otg_worldgen_philosophy.md` cited as the design reference; `README.md` documents the new tools; `AGENTS.md` notes the region-profile authoring convention (add regions/rules via the JSON catalog, not by branching in code).
- **Version**: feature bump per the `openspec/config.yaml` version rule (`gradle.properties`, `neoforge.mods.toml`, README jar-name examples, `CHANGELOG.md` together).
- **Depends on**: nothing hard — independent of the sect/town realizers. **Enables (future, deferred):** runtime placement of subjects into regions; terrain realization of 隔 edges (山脉/海洋 as actual relief — the natural next change, extending sect's 反推山形 from single peak to range); and the subject-framework generalization (the placement/terrain-verb "横轴").
