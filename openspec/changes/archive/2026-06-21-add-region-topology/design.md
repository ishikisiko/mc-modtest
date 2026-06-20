## Context

The mod now has one self-generating worldgen subject (the sect) and a mature offline building/style/pool/metadata toolchain, but **no macro layer**: sects spawn uniformly wherever a biome tag matches, with no 洲/域, no tier gradient, and no rule for how regions relate. `docs/otg_worldgen_philosophy.md` maps OTG's layers onto this project and shows the gap is the top of the stack — OTG's WorldConfig (region rules) and FromImage (designed geography) — plus the generalization of the sect's own terrain engine. This change takes only the **格局 (region) layer**, and only its **offline** form (OTG §19: let the data drive offline generation and validation before touching runtime chunk generation).

The design target chosen during exploration is a **constrained-random region topology**: not a fixed hand-painted map (no replayability) and not pure noise (no designed geography), but a graph whose **topology is authored as rules** while its **geometry is randomized per seed** — "fixed range, randomness under constraint rules."

## Goals / Non-Goals

**Goals:**
- A seed-deterministic, **re-roll-free** region-graph generator: 5–7 regions, a single `anchor` (中州) at map center, relations **randomized under rules** (B-mode), tier-step limit **N = 5**, edges typed 连/隔 with separator palette **{特殊山脉, 特殊海洋}**.
- A `region-profile` JSON data model (tier, qi/danger ranges, role, admitted subjects) stored under the mod worldgen data tree — consumable offline now and by a runtime placement-director later.
- Offline visualization of a generated graph + an offline validator + a multi-seed survey.

**Non-Goals:**
- No runtime chunk generation, biome takeover, or block writing.
- No terrain realization of edges (turning 隔 into actual 山脉/海洋 relief) — that is the natural **next** change (extends sect's 反推山形 from single peak to range).
- No connector structures (官道/关隘/渡口 as buildables).
- No generalization of the placement / terrain-verb "横轴" framework.
- No FromImage PNG-driven layout; no change to the sect/town realizers.

## Decisions

### D1: Offline-first, data before runtime (OTG §19)
Ship the region layer as a `tools/` generator + validator + visualization that emits JSON, with no Minecraft worldgen wiring. Rationale: the terrain *engine* already exists (sect); the missing thing is the *格局 data layer*. Building the data first is low-risk, reviewable, and gives the future runtime something concrete to consume. The change is "done" when a seed deterministically yields a validated, visualizable region graph.

### D2: Topology authored as rules; geometry randomized (B-mode)
The authored content is a **ruleset**, not pairwise relations: region count range, tier range, tier-step N, the separator palette, and role rules. Each seed produces a different concrete graph satisfying the rules. Players form geography memory at the *rule* level (魔域 is always sealed and peripheral) rather than the *instance* level. This is the "limited-rule randomness" the exploration converged on.

### D3: Constructive generation → no re-roll, deterministic
Generate by construction so every intermediate state is legal; never generate-then-check-then-discard. Order:
1. Place the `anchor` at center; choose region count (5–7) and embed the rest in radial sectors.
2. Assign tier outward from the anchor (top tier), each step `parent − random(0..N)` clamped to the tier range — so every tree edge satisfies the tier-step **by construction**.
3. Build a **连 spanning tree** over non-walled regions — global traversability is **constructed**, not validated-and-retried. (Global connectivity is the *only* constraint that would otherwise force a re-roll; the spanning tree removes that need — the maze/dungeon trick.)
4. Type the remaining geometric-neighbour edges by rule: tier gap > N or an incident walled region → 隔 (with a palette separator); else may be 连.
5. Apply the walled-region rule (D6).

Randomness lives in sector assignment, the `random(0..N)` tier draws, and 连/隔 typing of non-tree edges — none of which can violate a rule. Determinism comes from seeding one RNG stream from the world seed.

### D4: Region data is JSON under `data/myvillage/worldgen/`, not a Python table
The sect worldgen layer is already JSON (structure / structure_set / biome tag). Region profiles + ruleset follow suit so a future runtime placement-director (Java) reads the **same** files — no second source of truth. `tools/buildgen/groups.py` stays the binding table for *building assembly*; it is a different axis (what to build) from region topology (where, at world scale). The shared generation/validation algorithm lives in one Python module (`tools/buildgen/region_topology.py`) so the generator and validator agree.

### D5: Edges model 连/隔; separators decided, connectors deferred
An edge is geometric adjacency given a type. 隔 carries a separator from {特殊山脉, 特殊海洋} (decided). 连 may later be realized as 官道/关隘/渡口, but those connector *buildables* and all terrain realization are deferred — this change only records the typed edge. The edge list is precisely the convergence point the next change consumes: a 隔=山脉 edge becomes a RIDGE terrain feature (sect 反推山形 extended to a range).

### D6: `walled` is an explicit role, not an emergent accident
A region marked `walled` (魔域-style) has all incident edges forced to 隔 except at most one 连 realized as a 关隘 entry, and need not join the 连 spanning tree. Making it a role keeps "sealed off" intentional and validatable, instead of hoping random typing happens to wall it.

### D7: Parameters live in the ruleset JSON
`region_count ∈ [5,7]`, `anchor` centered, `tier_step N = 5`, a configured tier range, and the separator palette are ruleset fields, not constants in code — so calibration does not require code edits, matching the project's data-driven convention.

## Risks / Trade-offs

- **Embedding quality** — random radial placement can put 连 neighbours far apart or cross edges visually. Mitigate with a simple sector + light relaxation embedding and rely on the visualization for review; at this stage the *data* (graph + types) is the contract and geometry is cosmetic, to be firmed when terrain realization lands.
- **Over-constrained rulesets cause dead-ends** — though generation is constructive, a pathological ruleset (too many walled regions, too-tight tier range vs N) could leave no legal choice at a step. Mitigate with a fixed most-constrained-first placement order and a validator that **flags an unsatisfiable ruleset** explicitly rather than looping or re-rolling.
- **Python↔Java RNG parity (future)** — the offline generator is Python; when a runtime later regenerates or reads graphs, the RNG/hash must match, exactly as `SectMountain` mirrors `sect_mountain.py` today. Keep the RNG simple and documented now so that parity is cheap later; this change owns only the Python side.
- **Scope creep toward terrain** — it is tempting to start drawing 山脉 blocks. Fence it: this change stops at the typed edge list and its visualization; turning edges into relief is the next change and must not leak in here.
