## Why

The runtime cultivation town (`/myvillage town`, [TownGenerator.java](../../../src/main/java/com/example/myvillage/town/TownGenerator.java)) reads in-game as "a few small courtyards stitched together," far short of a believable 修仙坊市. Root causes are structural, not cosmetic: the plan is nine equal-weight parcels each holding a reused ~15×14 single-courtyard template, buildings sit centered in oversized lots ringed by stone-brick plinth (so the gaps between them dominate), the tallest landmark is a 5-block paifang plus a 27×20 shrine (`silhouette_score≈55`), the whole town is hard-capped at 96×80 and requires every chunk preloaded, and street dressing is placeholder vanilla (barrels, oak fences, campfires, white wool, podzol). The project already proves the needed ambition exists — `cultivation_sect` is a 114 KB terraced mountain complex with real hierarchy — but that ambition was never brought to the town. This change rebuilds the runtime town to deliver the intended feel: 层次 (hierarchy), 人烟味 (human presence), 市井 (marketplace density).

## What Changes

- **Rebuild the town plan into a districted, tiered settlement.** Replace the nine equal-weight parcels with named districts — 坊门区 (gate approach), 市肆区 (market/shops), 民居坊 (residential), 礼制核心 (civic/ritual core), and 边缘区 (fringe: 灵田 spirit-fields, 码头 wharf, 演武场 drill-ground). Each district carries its own density, storey-height band, and material palette; the core is tall and dense, the fringe loose. **BREAKING**: the hardcoded nine-parcel plan and its single-shrine ritual-axis assumption are removed; existing `town-plan`/`town-realization` scenarios that assert nine parcels or a fixed 96×80 footprint are superseded.
- **Raise the scale ceiling to a mid-size fair (~160×160).** Lift `MAX_FOOTPRINT_AXIS` to 160 and replace the all-chunks-preloaded hard gate with a chunk-ticket / forced-load path so a 160×160 town can generate in one command without silently failing on unloaded chunks.
- **Add street frontage with party-wall row shops and alleys.** Buildings align to the street wall and share gable walls with neighbors (沿街连排 / 共墙铺面), eliminating the centered-in-lot plinth gaps; remaining interstitial space becomes intentional narrow alleys (窄巷) rather than dead lawn. This is the single change that flips the read from "courtyards" to "street market."
- **Introduce vertical landmark archetypes and a skyline rule.** Add 塔 (pagoda), 楼阁 (multi-storey pavilion), and 钟鼓楼 (bell/drum tower) archetypes built from existing terrace + `tiered_eave_roof` vocabulary, and a plan rule requiring the civic core to carry ≥N tall volumes, lifting `silhouette_score`.
- **Replace placeholder street life with cultivation-flavored dressing.** Swap barrels/fences/campfires/wool/podzol for 幌子 shop banners, 药圃/灵田 planting beds, 炼丹炉 alchemy furnaces, 法器摊 artifact stalls, 阵纹 formation floor patterns, and villager/spirit-beast occupants. Optional external decor (staged `fetzisdisplays`) is applied as skins through profile-gated slots only.
- **Demote the static `cultivation_town_NNN` compound library to a block-material source.** It stops being a parallel standalone "town" concept and instead supplies courtyard tissue the runtime planner draws district fill from, ending the two-systems conflict.

## Capabilities

### New Capabilities
- `town-districts`: zoning grammar that partitions a town footprint into named districts (gate / market / residential / civic-core / fringe), each binding a density target, storey-height band, and material register, with a hierarchy rule that concentrates scale at the core.
- `street-frontage`: party-wall, street-aligned building placement producing continuous row frontages and deliberate narrow alleys instead of centered-lot plinths.
- `vertical-landmark`: tall archetype family (pagoda / pavilion / bell-drum tower) plus a skyline requirement that guarantees vertical relief in the civic core.
- `cultivation-street-life`: cultivation-themed street dressing and inhabitant vocabulary (banners, spirit-fields, alchemy furnaces, formation floors, villagers/beasts) replacing placeholder vanilla furniture, profile-gated for optional decor mods.

### Modified Capabilities
- `town-plan`: the planner now emits a districted, tiered plan at up to ~160×160 instead of nine fixed parcels; parcel tiers derive from district assignment, and the ritual axis is expressed within the civic core rather than as the whole-town organizing spine.
- `town-realization`: footprint cap rises to 160 and the all-chunks-preloaded precondition is replaced with chunk forcing; placement honors street-frontage alignment and per-district material registers.
- `settlement-group`: the `cultivation_town` group gains a district brief (replacing the flat `soft_functional_brief`), and the static compound library is reclassified as district fill material rather than a standalone settlement output.

## Impact

- **Code**: [TownGenerator.java](../../../src/main/java/com/example/myvillage/town/TownGenerator.java) (plan + realization rewrite, scale constants, chunk loading), [tools/buildgen/town.py](../../../tools/buildgen/town.py) (district plan generation + validation), [tools/buildgen/groups.py](../../../tools/buildgen/groups.py) (`cultivation_town` district brief, new archetype roster entries), [tools/buildgen/archetypes.py](../../../tools/buildgen/archetypes.py) + [tools/buildgen/ops.py](../../../tools/buildgen/ops.py) (pagoda/pavilion/tower forms via the form registry, no string-prefix dispatch).
- **Resources**: regenerate `src/main/resources/data/myvillage/structure/` (new vertical-landmark templates, refreshed cultivation buildings); update gallery functions.
- **Validation**: extend `tools/validate_runtime_town_plan.py` and `tools/validate_town_generation.py` for districts/frontage/skyline; existing validators (`validate_compound_library.py`, `check_cultivation_forms.py`, `check_style_policy.py`) must still pass under both `--profile vanilla` and `full`.
- **Docs**: update `README.md` (`/myvillage town` behavior, footprint), `AGENTS.md` (runtime town composition + compound-library role), and the relevant `openspec/specs/` documents.
- **Compatibility**: **BREAKING** for anyone relying on the fixed 96×80 nine-parcel town; the static `cultivation_town_NNN` standalone-placement remains available but is no longer the canonical "town."
