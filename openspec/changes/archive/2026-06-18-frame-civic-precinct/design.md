## Context

The districted planner (`tools/buildgen/town.py`) and its Java mirror (`TownGenerator.java`) lay the `civic_core` at the spine terminus: a shrine (dominant landmark), two flanking vertical landmarks (pagoda + bell/drum), two civic halls, and a plaza/paifang/lantern ritual axis. Measured on seed 42 the core is 73×48 (3504 cells) but 1104 cells (31%) are unoccupied, and ~800 of those form one contiguous block in the south (gate-facing) half: an ~528-cell entry band at z111–118 spanning the full width except the spine, and a ~270-cell forecourt at z119–128 between the two civic halls. The remaining voids are 2–3-wide slivers along the lateral edges (x44–46 / x114–116) and 2-wide seams between the landmark parcels. The core's lateral edges abut `fringe` spirit-fields with no transition.

The `civic_core` branch of `_subdivide_district` ([town.py:703-733](tools/buildgen/town.py#L703-L733)) returns early after emitting the two halls and two landmarks, so nothing currently fills the approach band, forecourt, or edges. This change adds those elements as deterministic, planner-side structures mirrored by the realizer. It is independent of `densify-cultivation-town`, which only touches market/residential.

## Goals / Non-Goals

**Goals:**
- Convert the ~800-cell gate-facing void into a legible `门→神道→广场→牌坊→神庙` approach sequence.
- Enclose the plaza with flanking side halls and a lateral colonnade, consuming the forecourt and edge slivers.
- Wrap the core in a precinct wall + gates that doubles as the core↔fringe transition.
- Preserve every existing invariant: sole dominant landmark, ritual-axis terminus, skyline relief, determinism, block budget, spine traversability.
- Keep planner and realizer byte-for-byte in step (no shared RNG; deterministic positions).

**Non-Goals:**
- No change to market/residential/gate district subdivision (that is `densify-cultivation-town`).
- No new authored `.nbt` structures required; precinct gate / side halls reuse shipped small archetypes, and walls/spirit-way props are block-placed by the realizer.
- No rework of the spirit-field `fringe` interior beyond bounding it with the wall.
- No change to the south town gate, perimeter wall, or main spine routing outside the core.

## Decisions

### D1: Model the precinct as new layout regions, not a new district kind
Extend `_Layout` with explicit rectangles/cell-sets for the precinct gate, spirit-way band, side-hall parcels, colonnade edges, and wall, all derived deterministically from the existing `cx`, spine, plaza, shrine, and landmark bounds. Keep `civic_core` a single district so the `town-districts` partition, importance hierarchy, and skyline rule are untouched.
- *Why not a new district kind?* The partition invariants (every non-street cell in exactly one district, core-outranks-fringe) are stable; precinct elements are intra-core structure, like the existing plaza/paifang, not a new partition member.
- *Alternative considered:* a `precinct` sub-district — rejected as it would ripple through the district brief, importance map, and every partition validator for no behavioral gain.

### D2: Approach sequence is reserved structure + ritual-axis metadata, realized as block fill
The spirit way (statues/lanterns/stele) and precinct gate are recorded the way lanterns/paifang already are — as cell sets in `ritual_axis` — and the realizer dresses them with blocks, reusing the existing lantern/paifang placement path. The precinct gate on the spine is a paifang-style structure at the core's gate-facing edge.
- *Why?* Mirrors the established mechanism (`lantern_cells`, `paifang_gate_cells`), so the realizer change is incremental and determinism is preserved.
- *Trade-off:* spirit-way "statues" are block compositions, not authored NBTs; acceptable for v1 and avoids new assets.

### D3: Side halls and colonnade are parcels/edge-cells emitted from the civic_core branch
Side halls become low-tier `civic` parcels placed in the forecourt gaps (capped below the dominant landmark tier and within the storey band). The colonnade is a reserved edge structure (1–3-wide) along the lateral core edges, consuming the x44–46 / x114–116 slivers; the realizer builds it as a covered walk (posts + roof line).
- *Why parcels for halls?* They participate in existing parcel validators (inside-district, tier, storey-band) for free.
- *Alternative:* widen the existing two civic halls to fill the forecourt — rejected because it removes the plaza enclosure read and leaves the edge slivers.

### D4: Precinct wall on the core boundary is the fringe transition
Reserve wall cells along the core's gate-facing and lateral edges, with a spine gate (coincident with the precinct-gate opening) and one side gate per lateral edge. On edges shared with `fringe`, the wall lies exactly on the district boundary, so spirit-fields are provably "outside the wall." Wall cells are disjoint from parcels and from streets except at gates.
- *Why edges only (not the north/back edge mandatory)?* The back edge sits at the site interior behind the shrine; walling it is optional and can be a follow-up. Gate-facing + lateral edges are what produce the compound read and the fringe transition.
- *Spine traversability:* the spine gate is a passable opening; validator re-checks gate→precinct-gate→shrine connectivity.

### D5: Validation extends the existing town validators
Add a precinct check to `validate_town_plan` (Python) and the Java `validateRealizedTown`: assert wall presence on the required edges, a spine-axis precinct gate, a non-empty staged approach band, plaza still reachable through the gate, and an empty-core-cell count strictly below the unframed baseline for the same seed. Regenerate `reports/town_generation_validation.json` and the previews.
- *Why a baseline-relative emptiness check?* Encodes the actual goal (the void shrinks) without hardcoding cell counts that vary with site size.

## Risks / Trade-offs

- **Planner/realizer drift** → Both compute precinct geometry from the same deterministic inputs (cx, spine, plaza, shrine, landmark bounds); add a same-seed parity check to the Java/Python validation step before merge.
- **Overlap with spine/plaza/landmarks/lanterns** → All new elements are emitted after the axis and landmarks exist and are masked against `street_cells`, `plaza`, `lantern_cells`, and landmark parcel cells; existing disjointness validators (parcel/street/negative overlap) will catch regressions.
- **Block-budget inflation from walls + colonnade + props** → Walls and colonnade are thin (1–3 wide) and props are sparse; re-run `estimate_block_budget` and keep under `BLOCK_BUDGET_CEILING`; if tight, reduce spirit-way prop density first.
- **Spine accidentally blocked by the precinct gate** → Gate is modeled as a passable opening on the spine; the spine-connectivity validator (`spine_not_connected_to_civic_core`, gate→shrine reachability) guards this.
- **Edge slivers too narrow for a real colonnade** → If a lateral sliver is <2 wide for a given site, degrade the colonnade to a wall-only run on that edge (still consumes the sliver, still bounds the fringe).

## Migration Plan

Additive feature, no data migration. Land planner changes first (with validators + regenerated reports), then the Java realizer to match, gated on a same-seed Python/Java parity check. Rollback is reverting the change; existing towns are regenerated, not persisted, so there is no stored-state concern.

## Open Questions

- Should the precinct's back (north) edge also be walled, or left open to a rear garden behind the shrine? (Default: leave open for v1; D4.)
- Spirit-way guardian elements: block-composed statues now, or defer authored `灵兽`/statue NBTs to a later assets change? (Default: block-composed; D2.)
- Side-hall archetype: reuse `cultivation_shop` variants as the planner does for the existing civic halls, or introduce a dedicated 配殿 template? (Default: reuse shipped variants.)
