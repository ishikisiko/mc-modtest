## 1. Template scaffolding (deterministic variant forms)

- [x] 1.1 Add a `CultivationTemplate` parameter set (footprint, `fh`, `wall_h`, `ridge_axis`, `roof_type`, ordered ancillary volumes) in `tools/buildgen/archetypes.py`.
- [x] 1.2 Define the per-archetype template tables (3 each) for `cultivation_house`, `cultivation_shop`, `cultivation_market`, `cultivation_inn`, keyed on `variant_index`, matching the 1→2→3 volume ladder in design.md.
- [x] 1.3 Replace the four archetypes' `SCALE_TIERS` footprint usage so the variant footprint comes from the template (deterministic), not `rng.choice`; cap footprints to the existing parcel envelope (~≤17 wide).
- [x] 1.4 Verify each archetype's three variants resolve to different `(footprint, wall_h, fh, ridge_axis, roof_type)` tuples (quick script over the builders).

## 2. Unlock the locked knobs

- [x] 2.1 Thread `wall_h`, `fh`, `roof_axis`, `roof_type` from the template into `_cultivation_main` / `_main_volume` / `_set_roof` for all four builders (remove the hardcoded `wall_h=4, fh=2, roof_axis="x"`).
- [x] 2.2 Wire optional `side_wing` (厢房) ancillary volumes into the cultivation builders, reusing the `build_medium_house` `side_wing` construction; add interior `_zone`s for each wing. NOTE: the wing carries a transverse `sweeping_eave_roof` (双脊 read) instead of `cross_gable_roof`, because `cultivation_town`'s `allowed_roof_types` whitelist excludes gable/cross-gable forms.
- [x] 2.3 Wire optional `rear_shed` (后罩房/作坊) ancillary volumes, reusing the `rear_shed` attachment; add interior `_zone`s. NOTE: the shed carries a low `sweeping_eave_roof` instead of `lean_to_roof` for the same whitelist reason.
- [x] 2.4 Confirm `cultivation_inn` v2 height/重檐 treatment (reuses `tiered_eave_roof` for silhouette consistency, per the design open question) and that ridge-axis flips render correctly.

## 3. 院墙 walled rear courtyard element

- [x] 3.1 Add a `courtyard_enclosure` node type in `archetypes.py` (tags `DETAIL`/`STRUCTURE`, NOT added to `VOLUME_TYPES`) plus a `_cultivation_courtyard` helper that places it around a `courtyard_patch`.
- [x] 3.2 Implement the `courtyard_enclosure` renderer in `tools/buildgen/ops.py`: a low (1–2 cell) wall ring with a one-cell gate opening on the entry-adjacent side; wall blocks resolve through `BALUSTRADE`/`RIDGE_ORNAMENT`/`PLATFORM_STONE`.
- [x] 3.3 Attach a walled 后院 to the v3 templates (and inn v3), paired with the `rear_shed` so silhouette differentiation does not depend on the enclosure.
- [x] 3.4 Verify the enclosure places no fence/woodpile/porch-post blocks (existing "omit Western domestic tells" grammar rule still passes).

## 4. Acceptance gate in validation

- [x] 4.1 Extend the cultivation-town library report aggregation (`quality.py` / library validator) to compute, per archetype, the variant `silhouette_score` spread (max − min).
- [x] 4.2 Add a byte-identity check comparing exported `.nbt` hashes across variants of the same archetype.
- [x] 4.3 Fail the cultivation-town library build when spread < 30 or any two variants are byte-identical, naming the offending archetype/variants.

## 5. Regenerate assets and validate

- [x] 5.1 Regenerate the `cultivation_house/shop/market/inn` `_001/_002/_003` `.nbt` and `place/*.mcfunction`.
- [x] 5.2 Regenerate `reports/cultivation_town_building_library_report.json`; confirm spread ≥30 per archetype and no byte-identical pairs.
- [x] 5.3 Regenerate the per-structure previews for the four archetypes and eyeball that v1/v2/v3 read as distinct 形制 (高低/长宽/胖瘦/后院).
- [x] 5.4 Confirm same-seed reproducibility across two generations (identical output).
- [x] 5.5 Run the existing cultivation-town / town-generation validation tooling and the Gradle build; ensure both pass.

## 6. Cross-check downstream

- [x] 6.1 Verify the new footprints fit current town parcels (no overflow) and that `densify-cultivation-town`'s frontage variant cycling now yields a visibly non-repeating street row.
