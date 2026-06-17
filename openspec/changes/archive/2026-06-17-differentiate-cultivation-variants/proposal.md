## Why

The small and medium cultivation-town buildings barely differ between their `_v1/_v2/_v3` variants — perceived difference is around 10%, and several shipped variants are in fact **byte-identical** (`cultivation_house_001 == _002`, `cultivation_inn_001 == _002`, `cultivation_market_002 == _003`). The root cause is structural: the variant index is decorative (footprint is picked by `rng.choice` so adjacent variants collide; the v-number only flips one roof), and `wall_h`, `foundation_h`, `roof_axis`, and volume-count are locked constants — so `cultivation_house`/`shop`/`market` are all pinned at `silhouette_score` 55. This also defeats the in-progress `densify-cultivation-town` work, whose plan to "cycle across `_001/_002/_003` so a street row is not one repeated building" assumes the variants are actually distinct. We want at least a 30% read difference across the axes the variants should express: height (高低), footprint (长宽), proportion (胖瘦), and rear/side massing (后院/厢房).

## What Changes

- **Make each archetype's three variants deliberately distinct 形制 (forms), not three random rolls.** Each cultivation archetype defines `v1/v2/v3` as fixed massing templates that differ on **at least two** of: footprint, height, aspect ratio / roof-ridge axis, volume count, roof type / rear courtyard. Variant selection becomes deterministic on the variant index (RNG only jitters details within a template), preserving same-seed reproducibility.
- **Unlock the locked dimensions** for cultivation builders: `wall_h`, `foundation_h` (台基 height), `roof_axis`, and volume count are driven by the chosen template instead of hardcoded constants. The vocabulary already exists in `build_medium_house` (variable wall/foundation height, `side_wing`+`cross_gable_roof`, `rear_shed`+`courtyard_patch`) but is not wired into the cultivation builders.
- **Add a walled rear courtyard (后院/院墙) massing element** — a low enclosing wall with a gate opening around a courtyard ground patch — as a first-class cultivation form element. The ground patch (`courtyard_patch`) already exists; the enclosing 院墙 is the only genuinely new building block.
- **Add a measurable acceptance gate**: within each cultivation archetype the three variants' `silhouette_score` spread SHALL be ≥30 and no two shipped variant NBTs SHALL be byte-identical; same-seed regeneration stays reproducible.

Scope is the small/medium town archetypes (`cultivation_house`, `cultivation_shop`, `cultivation_market`, `cultivation_inn`). Pure vertical landmarks (`pagoda`, `pavilion`, `bell_drum_tower`, `town_shrine`) already vary strongly and are out of scope except where they reuse the new shared knobs.

## Capabilities

### New Capabilities
- `cultivation-variant-differentiation`: Each cultivation town archetype's variants SHALL be deliberately distinct forms (differing on ≥2 massing axes), selected deterministically per variant index, and SHALL meet a measurable distinctness gate (silhouette spread ≥30 within an archetype; no two variant NBTs byte-identical).

### Modified Capabilities
- `cultivation-massing-grammar`: Add a walled rear courtyard (后院/院墙) as a first-class cultivation massing element, and establish that wall height, platform (台基) height, roof-ridge axis, and the optional side wing (厢房) / rear annex (后罩房) volumes are parametric knobs of the grammar that variant templates select among (rather than fixed per archetype).

## Impact

- Code (generator only; no runtime `TownGenerator.java` change): `tools/buildgen/archetypes.py` (`build_cultivation_house/shop/market/inn`, `SCALE_TIERS`, `_cultivation_*` helpers, deterministic template tables keyed on `variant_index`), `tools/buildgen/ops.py` (new 院墙 enclosure renderer / motif), `tools/buildgen/quality.py` and the validation tooling (silhouette-spread + byte-identity acceptance check).
- Assets: the shipped `cultivation_house/shop/market/inn _001/_002/_003` NBTs and their `place/*.mcfunction` regenerate; `reports/cultivation_town_building_library_report.json` regenerates with the new footprints/heights/scores.
- Upstream of `densify-cultivation-town`: that change's frontage variant-cycling becomes meaningful once the variants are actually distinct; no requirement of that change is altered here.
- Validation/preview: cultivation-town library validation and the per-structure previews regenerate; expect intended diffs in parcel footprints, heights, and silhouette scores.
