## Context

The runtime cultivation town is produced by two layers that must stay in lockstep:
the Python planner/validator in [tools/buildgen/town.py](../../../tools/buildgen/town.py)
(also exercised offline by `validate_runtime_town_plan.py` and `generate_town_plan_preview.py`)
and the Java realizer [TownGenerator.java](../../../src/main/java/com/example/myvillage/town/TownGenerator.java)
that re-derives an equivalent plan in-world. Today both encode the same flat
shape: a 96×80 footprint, a hardcoded nine-parcel list, a single south-gate→shrine
ritual axis, and per-cell street/furniture dressing. Buildings are small single-courtyard
templates centered in their lots, so the dominant visual is plinth-ringed gaps.

The project already contains the structural machinery this rebuild needs — terraced
massing, `tiered_eave_roof` flying eaves, the form registry in
[ops.py](../../../tools/buildgen/ops.py), profile-gated mod slots, and the
`cultivation_sect` proof that tall layered complexes are achievable. The work is to
bring that vocabulary to the town and to replace the flat plan with a districted one.

Constraints from `AGENTS.md`: register new families/roofs through
`groups.py`/`ops.py` (never dispatch by `style_id` prefix or string-match), gate
external-mod ids behind `--profile {vanilla,full}` resolved from `modset.py`, and
update README/AGENTS/specs plus regenerate resources in the same change. The Java plan
and the Python plan must remain structurally equivalent so the offline validators
stay meaningful.

## Goals / Non-Goals

**Goals:**
- Replace the flat nine-parcel plan with a districted, tiered plan: 坊门区 / 市肆区 / 民居坊 / 礼制核心 / 边缘区, each with its own density, storey band, and material register.
- Raise the runtime footprint to ~160×160 in a single command without silently failing on unloaded chunks.
- Make street frontage continuous: street-aligned, party-wall row buildings with deliberate alleys instead of centered-lot plinths.
- Add pagoda / pavilion / bell-drum-tower archetypes and a skyline rule so the civic core has vertical relief.
- Replace placeholder furniture with cultivation-flavored street life, profile-gated for optional decor.
- Keep Python planner and Java realizer structurally equivalent and all existing validators green under both profiles.

**Non-Goals:**
- City-scale (256+) or cross-tick/cross-chunk streaming generation — explicitly deferred; 160×160 is sized to complete in one command.
- Live NPC schedules/AI beyond placing villager/beast entities and decor.
- Reworking `cultivation_sect` (the mountain complex) or the static standalone-place command surface beyond reclassifying the compound library's role.
- Authoring brand-new external decor mod integration phases (this consumes the existing staged `fetzisdisplays` slots, it does not add new mods).

## Decisions

### D1: District-graph plan replaces the flat parcel list
The planner partitions the footprint into a small fixed set of **districts**, each a
rectangle (or L-region) tagged with `kind` (gate/market/residential/civic_core/fringe),
a `density` target, a `storey_band` (min/max floors), and a `material_register`. Parcels
are then subdivided *within* a district and inherit its brief; importance tier derives
from district kind (civic_core highest), so the existing "exactly one dominant landmark"
invariant becomes "civic_core carries the dominant landmark." The ritual axis (plaza /
paifang / lantern approach) is expressed *inside* the civic core rather than spanning the
whole town.
- *Alternatives considered*: (a) keep the flat list but add more parcels — rejected, it scales the "sparse lots" problem rather than fixing density/hierarchy; (b) fully organic growth (agent/L-system streets) — rejected as too non-deterministic and hard to keep Java/Python equivalent at this stage.

### D2: District briefs live in `groups.py`, not in branching code
The `cultivation_town` group's flat `soft_functional_brief` is replaced by a
`district_brief` (ordered districts with density/storey/material/archetype-roster per
kind). The planner reads the brief generically; no district behavior is keyed off
`style_id` or name prefixes, per AGENTS.md. New vertical archetypes are added to the
roster and to the form registry, not to a dispatch switch.
- *Alternative*: hardcode districts in `town.py` — rejected, it re-introduces the exact prefix/branch coupling AGENTS.md forbids and blocks future families reusing the grammar.

### D3: Street-frontage placement supersedes centered-in-lot placement
Realization aligns a building's street-facing wall to the parcel's frontage edge and
butts neighbors against shared gable lines, so a run of parcels reads as one continuous
shopfront wall. The leftover depth behind frontage becomes courtyard/yard; leftover width
between district blocks becomes a typed **alley** (narrow, no plinth). The current
"center the template, ring it with plinth" path is removed for frontage parcels.
- *Alternative*: keep centering but shrink lots — rejected, shared party walls are what produce the 连排 read; mere shrinking still leaves seams and plinth rings.

### D4: Replace the all-chunks-preloaded gate with chunk forcing
`MAX_FOOTPRINT_AXIS` rises to 160. The `loaded()` hard refusal is replaced by acquiring
chunk-load tickets (forced loading) across the footprint before placement and releasing
them after, so a 160×160 town generates in one command. If forcing fails for a region,
the command reports the affected extent rather than silently skipping.
- *Alternative*: chunked/multi-tick streaming — deferred to a future city-scale change (Non-Goal); overkill for 160×160 and a large equivalence-maintenance burden.

### D5: Vertical landmarks built from existing terrace + flying-eave forms
Pagoda / pavilion / bell-drum-tower archetypes are composed from the existing terraced
massing and `tiered_eave_roof` ops rather than new bespoke geometry, registered as forms
in `ops.py` and listed in the style's `allowed_roof_types`/`allowed_motifs`. A plan-level
**skyline rule** requires the civic core to contain ≥N volumes above a height threshold;
the validator checks it and the building scorer should reflect improved silhouette.
- *Alternative*: import sect templates wholesale — rejected, sect massing is mountain-terraced and out of scale/idiom for an in-town tower.

### D6: Compound library demoted to district fill material
`cultivation_town_NNN` stops being a standalone settlement output and becomes a source of
courtyard tissue the residential/market districts draw fill from. `settlement_group`
reclassifies it accordingly; the `/myvillage place cultivation_town_001` command stays for
back-compat but is documented as a fragment, not "the town."
- *Alternative*: delete the compound library — rejected, its courtyard tissue is reusable fill and deleting it churns generated resources for no gain.

## Risks / Trade-offs

- **Java/Python plan divergence** → keep the district brief and plan invariants in one documented shape; extend `validate_runtime_town_plan.py` to assert districts/skyline/frontage so drift fails offline before in-game review.
- **Forced chunk loading at 160×160 stresses the server / may hit world border or protected regions** → cap at 160, force-load in a bounded window, release tickets in a `finally`, and report (not crash) on regions that can't be loaded or built.
- **Party-wall frontage on sloped terrain can stair-step ugly or clip** → keep the existing slope limit per parcel, let frontage runs break at slope thresholds into stepped segments, and skip (report) parcels over the limit rather than force-flatten.
- **Bigger, denser towns inflate block counts and generation time** → keep `estimate_block_budget` honest, add a budget assertion to the validator, and tune density targets per district so the fair stays within a reviewable budget.
- **Scope creep across four phases in one change** → phases are sequenced in tasks.md (districts+scale → frontage → landmarks → street life); each phase leaves the town buildable and validators green, so the change can be reviewed/landed incrementally even though it is proposed as one.
- **Visual regression on the established ritual axis** → preserve plaza/paifang/lantern semantics inside the civic core and keep the axis scenario in the spec so the recognizable approach survives the rebuild.

## Migration Plan

1. Land district plan + scale (Phase 1) behind the rebuilt `town()` path; regenerate plan previews and confirm `validate_runtime_town_plan.py` passes the new district invariants.
2. Layer frontage (Phase 2), then vertical landmarks (Phase 3), then street life (Phase 4); regenerate `src/main/resources/data/myvillage/structure/` and gallery functions after each phase.
3. Run the full acceptance suite from AGENTS.md (`generate_all_structures`, all validators, previews, `./gradlew build`) under both `--profile vanilla` and `full` before handoff.
4. Rollback: the change is additive to the command surface (`/myvillage place` fragments unchanged); reverting the `town()` rewrite restores the prior 96×80 plan. No persisted world data migration is required since towns are generated on demand.

## Open Questions

- Exact target footprint within the ~160 band (160×160 vs 160×128) and the per-district density numbers — to be tuned against block-budget and generation-time measurements during Phase 1.
- How many tall volumes the skyline rule should require in the civic core (≥2? ≥3?) and the height threshold that counts as "tall."
- Whether fringe 灵田/码头/演武场 are all in-scope for the first landing or whether wharf (needs water siting) is deferred to a follow-up.
