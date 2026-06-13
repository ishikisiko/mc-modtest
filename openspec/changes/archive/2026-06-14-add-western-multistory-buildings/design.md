## Context

The buildgen pipeline (`tools/buildgen/`) generates a building through layered passes: `massing_pass` builds a `MassingGraph` of attached volumes, then `structure_pass`, `facade_detail_pass`, `roof_pass`, decoration, quality, and export. Every volume today is a single wall band (`foundation_h + wall_h`) topped by a gable roof; the only way a building grows is horizontally via `large_lite` attaching more volumes (see `archetypes.py` `SCALE_TIERS`). There is no story concept anywhere in `Node`, the passes, or the facade grammar.

The library generator (`tools/generate_building_library.py`) iterates `ARCHETYPES` × `count`, picking a tier from `TIER_PLAN`, retrying up to `MAX_ATTEMPTS` seeds until quality passes. Coordinates follow the documented convention: low z = front, +x = east, y=0 = foundation bottom.

This change adds vertical stacking (`multi-story-massing`) and two new archetype families (`shop`, `big_house`) that consume it. The Chinese courtyard 府邸 is explicitly deferred.

## Goals / Non-Goals

**Goals:**
- A reusable multi-story primitive: a volume can carry `stories > 1` with real floor slabs and a walkable stairwell whose openings align vertically.
- Two new families with strong, mostly-structural variant distinction: `shop` (`small_shop` 1-story, `medium_shop` 2-story) and `big_house` (2–3 stories), 5 variants each.
- Single-story behavior is byte-for-byte unchanged where possible (existing `small_house`/`medium_house`/`blacksmith` output should not regress).

**Non-Goals:**
- Industry-specific shop behavior (only a reserved `industry` meta field).
- Chinese 府邸 / compound, perimeter walls, water features, planting, new style profiles — separate proposal B.
- Per-floor interior room zoning beyond what's needed for a walkable stair; furnishing stays best-effort.
- Worldgen.

## Decisions

### D1: `stories` + `story_wall_h` live on the main volume node, not as separate stacked nodes
A multi-story building is one tall `main_volume` with `meta["stories"]` and `meta["story_wall_h"]`, total wall height `stories * story_wall_h`. Floor slabs and stairs are added by dedicated passes that read `stories`, rather than modeling each floor as its own `Node`.
- **Why:** keeps `structure_pass` hollow-box logic and roof placement working off one volume; minimizes blast radius. Stacked nodes would force every overlap/attachment helper (`_rects_overlap`, `_free_spot`) to become 3D and would complicate roofs.
- **Alternative considered:** one `Node` per story (rejected — large refactor, the graph's overlap math is 2D footprint-based by design).

### D2: New passes `floor_slab_pass` and `stair_pass`, inserted after `structure_pass`, before `facade_detail_pass`
`floor_slab_pass` lays an interior slab at each inter-story boundary (`foundation_h + k*story_wall_h`) leaving the stairwell opening. `stair_pass` places the stair blocks and tags the aligned openings `PROTECTED` so later passes don't fill them. Both are no-ops when `stories == 1`.
- **Why:** slabs/stairs need the shell from `structure_pass` to exist, and must precede facade so window bands and the stairwell can be deconflicted. Placing them before roof keeps roof logic untouched.
- **Alternative considered:** folding slabs into `structure_pass` (rejected — muddies the single-story path and the spec's stable, named pass order).

### D3: Stairwell column chosen at massing time; openings tagged PROTECTED
The stairwell footprint is selected during `massing_pass` (stored in graph meta), constrained to avoid the door bay and to leave room for window bands, reusing the existing "keep away from corners / door" logic style from `facade.py` / `_door`. Per-story openings share the same x/z column so the stair is continuous.
- **Why:** facade planning already needs to know occluded intervals; deciding the stair column up front lets facade treat it as another reserved interval, guaranteeing the spec's "stairwell avoids window bays" requirement.

### D4: Facade grammar plans one window band per story, vertically aligned by default
`plan_building_facades` gains a per-story loop: for `stories` bands at heights `foundation_h + k*story_wall_h`, reuse the existing single-band bay/post/corner planner, then by default copy story-1 along-wall positions to upper stories.
- **Why:** preserves all existing facade invariants per band; alignment is the visually expected default and is cheap (plan once, replicate positions).
- **Alternative considered:** independent per-story planning (rejected for default — risks misaligned, noisy facades; can be a future opt-in).

### D5: Variant distinction is driven structurally, encoded in per-seed plans, not random decoration
For `shop`: a small variant table over the form axis (story count is fixed per tier; roof style, signage, awning/eave, footprint, entrance vary). For `big_house`: variants differ by story count (2 vs 3), massing (single tall volume vs tall main + lower wing), and roof. The library generator assigns a distinct variant per index so the 5 outputs are visibly different, not 5 reskins.
- **Why:** the spec requires distinction "clearly stronger than `small_house`," whose variation today is mostly footprint + chimney + decoration. Driving off a variant table makes the difference deterministic and reviewable.

### D6: New families extend `ARCHETYPES` / `TIER_PLAN`; shop tiers are pseudo-archetypes
`shop` is generated as two named outputs `small_shop` and `medium_shop`; `big_house` as one family. The library generator and its validators learn the new names and the "5 variants" expectation. Existing `count 10` behavior for legacy archetypes is preserved.
- **Why:** matches how `TIER_PLAN` already maps index → tier; least surprise for the batch generators and reports.

## Risks / Trade-offs

- **Roof over tall volumes looks wrong** → reuse existing `roof_peak_y` math against the full wall top; verify peak height scales and add a quality check that the roof sits above the top story.
- **Stair geometry not actually walkable in-game (headroom / collision)** → keep stairs simple (straight or L run with a 2-high opening per story); validate openings are ≥2 cells tall and aligned; visually smoke-test one `medium_shop` and one 3-story `big_house`.
- **Facade per-story loop regresses single-story output** → guard the per-story path behind `stories > 1`; assert legacy archetype reports/structures are unchanged before/after.
- **Quality-check retry budget (`MAX_ATTEMPTS=8`) too tight for taller, more constrained buildings** → allow raising the budget for multi-story families if failure rate is high; surface failed-attempt counts in the report.
- **Variant table feels hand-authored rather than procedural** → acceptable trade-off this change; the table seeds structural choices, randomness still fills materials/details.

## Migration Plan

1. Land multi-story massing + passes with all existing archetypes still single-story (no output change).
2. Add `shop` and `big_house` families and library/validator wiring.
3. Regenerate assets via the canonical batch command; diff that legacy structures are unchanged and new ones appear.
4. Rollback: revert the change; legacy archetypes are untouched, so no regeneration of existing assets is required.

## Open Questions

- Stair style: straight run vs L-shaped per story — pick the simplest that passes headroom checks (lean straight).
- Whether `big_house` 3-story variants need a footprint bump to avoid looking like a tower; decide during massing tuning.
- Final per-tier variant tables (exact 5 form combinations for `small_shop` / `medium_shop` / `big_house`) to be fixed in tasks/implementation.
