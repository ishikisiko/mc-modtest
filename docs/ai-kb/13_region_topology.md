# Region Topology (洲/域 Layer)

Implemented in 0.14.0 by change `add-region-topology`. The design reference is
[`docs/otg_worldgen_philosophy.md`](../otg_worldgen_philosophy.md); this note
records how that maps onto the implemented offline-first region layer. See-also
[`07_neoforge_worldgen.md`](07_neoforge_worldgen.md) and the specs
[`region-profile`](../../openspec/specs/region-profile/spec.md),
[`region-topology`](../../openspec/specs/region-topology/spec.md),
[`validation`](../../openspec/specs/validation/spec.md).

## What this layer is

The macro layer the mod was missing. Above the single self-generating subject
(the sect) there was no 洲/域, no tier gradient, no rule for how regions relate.
This adds it as a **region graph (洲际图)** per world seed, delivered
**offline-first** (OTG §19): data drives generation and validation before any
runtime chunk-gen. No blocks are written, no biome is taken over, and nothing
runs during chunk generation in this change.

## The model

Authored content is a **ruleset**, not pairwise relations. Two JSON bodies under
`src/main/resources/data/myvillage/worldgen/`:

- `region_profile/*.json` — the catalog. Each region (洲) declares `id`,
  `display_name`, a nominal `tier`, `qi` / `danger` ranges, a placement `role`
  (`anchor` | `peripheral` | `walled`), and `admitted_subjects` (today `sect`).
- `region_topology.json` — the rules: `region_count ∈ [5,7]`, `tier_range`,
  `tier_step N = 5`, the separator palette `{特殊山脉, 特殊海洋}`, embedding
  radii, and role rules (single centered anchor; walled = all 隔 + ≤1 关隘).

All tunables live in data. Adding regions or recalibrating is a JSON edit, not a
code branch (see `AGENTS.md`).

## Constructive generation (deterministic, no re-roll)

`tools/buildgen/region_topology.py` is the single shared module — the generator
and the validator import it, so they agree on one source. Per seed (one RNG
stream seeded from the world seed; splitmix64/FNV-1a tagged primitives mirroring
`town_hash.py`, parity-ready for a future Java runtime placement-director):

1. Place the `anchor` (中州) at center; choose count 5–7; embed the rest on
   radial sectors (walled on an outer ring).
2. Assign tier outward along a **连 (passable) spanning tree** over non-walled
   regions — `parent.tier − random(0..N)` clamped to range, anchor's direct
   children step down ≥1 — so every tree edge satisfies the tier-step **by
   construction** and the anchor is the sole highest tier.
3. Type remaining geometric-neighbour edges by rule: tier gap > N or incident
   `walled` → 隔 (separated) with a palette separator; otherwise may be 连.
4. Apply the walled rule: a `walled` region (魔域-style) is sealed except for at
   most one 连 edge marked 关隘; its tier is fixed from that neighbour so the
   pass also respects the tier-step.

Because connectivity and the tier-step are *constructed*, generation never
discards a candidate and regenerates. Same seed → identical graph. A
most-constrained-first placement order means a satisfiable ruleset never
dead-ends; an unsatisfiable ruleset is reported explicitly (`UnsatisfiableRuleset`
raises, it does not loop).

## Output

`tools/generate_region_topology.py [seed]` emits the graph as JSON (regions with
tier/role/position + the typed edge list with separator types). A canonical
example ships at `worldgen/region_topology_example.json` (seed `20260620`),
generated from the same ruleset + catalog the tooling reads — no second source
of truth. `tools/generate_region_topology_preview.py` renders per-seed
`layout.svg` + ASCII `layout.txt` + a `viewer.html` under `out/preview/`, wired
into the aggregate preview index. `tools/validate_region_topology.py` checks the
structural invariants, runs deliberate-break cases, confirms determinism, and
writes the multi-seed survey to `reports/region_topology_validation.json`.

## OTG mapping

This is the top of the OTG stack the mod was missing:

- **WorldConfig** (region rules) → `region_topology.json` — world-scale order:
  how many 洲, the tier gradient, how regions may relate, the separator palette.
- **FromImage** (designed geography) → the **constrained-random** generator: not
  a hand-painted PNG (no replayability) and not pure noise (no designed
  geography), but a graph whose *topology is authored as rules* while its
  *geometry is randomized per seed*. Players form geography memory at the rule
  level (魔域 is always sealed and peripheral) rather than the instance level.
- **Resource Queue / CustomObject / CustomStructure** stay where they are
  (biome/feature/structure layers below); this change owns only the region layer.

The typed **edge list** is the convergence point the next change consumes: a
隔=山脉 edge becomes a RIDGE terrain feature, extending the sect's 反推山形 from
a single peak to a range. Runtime placement of subjects into regions, and all
terrain realization, are explicitly deferred — this layer is offline-only today.
