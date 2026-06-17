## Context

The cultivation builders in `tools/buildgen/archetypes.py` (`build_cultivation_house/shop/market/inn`) currently produce near-identical variants. Evidence from `reports/cultivation_town_building_library_report.json`:

- `cultivation_house_001 == _002`, `cultivation_inn_001 == _002`, `cultivation_market_002 == _003` are byte-identical.
- `cultivation_house`/`shop`/`market` are all pinned at `silhouette_score == 55`.

Two structural causes:

1. **The variant index is decorative.** `footprint = rng.choice(SCALE_TIERS[arch]["footprints"])` picks from a 2–3 entry pool of nearly-equal sizes, so adjacent variants collide. `variant_index(tier)` is read only to flip `cultivation_house`'s roof on `v3`.
2. **Every other dimension is a constant.** `wall_h=4`, `fh=2`, `roof_axis="x"`, and a single `main_volume` are hardcoded across `house`/`shop`/`market`.

The silhouette score is computed in `quality.py` as:

```
silhouette = 55 + 15*(n_volumes-1) + 10*(has_chimney) + 12*(vertical_landmark) + max(0, tallest_wall-8)
```

Cultivation buildings never have chimneys or landmark roofs, and single-story walls cap `tallest_wall` at ~6 (`fh+wall_h`), so `height_bonus` is 0. That leaves **`n_volumes` as the dominant, reliable silhouette lever for small/medium buildings: each attached volume is worth +15.** `graph.volumes()` counts only `{main_volume, great_hall_volume, side_wing, shed, rear_shed, tower_volume}` — a `courtyard_patch` or a 院墙 wall ring does **not** count.

The full vocabulary needed already exists and is exercised by `build_medium_house`: variable `wall_h`/`fh`, `side_wing` + `cross_gable_roof`, `rear_shed` + `lean_to_roof`, `courtyard_patch`. None of it is wired into the cultivation builders.

## Goals / Non-Goals

**Goals:**
- Each of `cultivation_house`, `cultivation_shop`, `cultivation_market`, `cultivation_inn` produces three variants that read as distinct 形制, differing on ≥2 massing axes.
- Variant form is a deterministic function of the variant index; same-seed regeneration stays bit-reproducible.
- A measurable acceptance gate: per-archetype silhouette spread ≥30 and no byte-identical variant pair.
- Reuse existing massing/roof vocabulary; add only the one missing block (院墙 walled courtyard).

**Non-Goals:**
- No change to the runtime `TownGenerator.java`; this is a generator/asset change. (It is the upstream that makes `densify-cultivation-town`'s variant cycling meaningful.)
- No rework of the pure vertical landmarks (`pagoda`, `pavilion`, `bell_drum_tower`, `town_shrine`) beyond reusing shared knobs.
- Not introducing new external mods or block families.
- Not changing the archetype roster or the `_vN` tier-count contract (still three variants per archetype).

## Decisions

### Decision 1: Variants are a deterministic 1 → 2 → 3 volume 形制 ladder

Each archetype defines an explicit, ordered table of three templates selected by `variant_index(tier)`. The backbone is a volume-count ladder, because that is the reliable silhouette lever and it maps directly onto a Chinese form progression (单体 → 加厢房 → 三合/后院). Each rung also shifts footprint, platform/wall height, and ridge axis so the variants differ on far more than volume count.

```
cultivation_house  (single-story 民居)
  v1 「单进平房」   main 11×9, fh1 wall3, ridge-x, sweeping_eave        1 vol  →  silhouette 55
  v2 「带厢房」     main 13×9 + 厢房 side_wing 5×9, fh2 wall4,           2 vol  →  70
                    cross_gable (双脊)
  v3 「三合后院」   main 13×11 + 东西厢房 ×2, fh2 wall4, hip_roof,        3 vol  →  85
                    + walled 后院 (院墙+天井) behind                     (+plan depth)

cultivation_shop  (店铺 — emphasise 胖瘦 via ridge axis)
  v1 「单开间窄店」 main 9×11 deep, ridge-z (瘦高山墙脸), fh1 wall4       1 vol  →  55
  v2 「双开间带廊」 main 13×9 + 摊廊 side_wing, ridge-x, fh2 wall4         2 vol  →  70
  v3 「前店后坊」   main 13×9 + 后罩房 rear_shed(作坊) + side store,        3 vol  →  85
                    + walled 后院

cultivation_market
  v1 「单棚市」     main 13×9 open-front, fh1 wall4                       1 vol  →  55
  v2 「L 形摊廊」   main 13×11 + 摊廊 side_wing                            2 vol  →  70
  v3 「回廊内院」   main 15×11 + 东西摊廊 ×2 around inner court            3 vol  →  85

cultivation_inn  (already 2-story; base silhouette 57)
  v1 「阔面客栈」   main 17×13 wide, fh2 wall8, hip                        1 vol  →  57
  v2 「高台重檐」   main 17×13, fh3 + tiered/重檐 eave + 厢房 side_wing      2 vol  →  72
  v3 「后院客栈」   main 17×11 + 后罩客房 rear_shed + 马厩 side_wing,         3 vol  →  87
                    + walled 后院
```

Spread per archetype is 30 by construction (1/2/3 volumes). The walled 后院 adds plan-level 层次 (and satisfies the "presence of rear courtyard" differentiation axis) but is deliberately **not** counted as a silhouette volume; templates that lean on the 后院 always pair it with a `rear_shed` (后罩房) that is.

**Alternatives considered:** (a) *Keep RNG, widen the footprint pool* — rejected: still collides and can't guarantee the gate. (b) *Drive variation purely by height* — rejected: `height_bonus` caps at `tallest_wall>8`, so single-story buildings can't gain spread from height alone. (c) *Continuous parametric sampling per seed* — rejected: harder to guarantee determinism and the ≥30 gate; a small fixed table is auditable and reproducible.

### Decision 2: Thread template parameters through the existing builders, do not fork them

Introduce a small `CultivationTemplate` parameter set (footprint, `fh`, `wall_h`, `ridge_axis`, `roof_type`, and an ordered list of ancillary volumes: `side_wing`/`rear_shed`/`courtyard`). `build_cultivation_*` looks up `TEMPLATES[archetype][variant_index]` and passes those values into the existing `_cultivation_main` / `_main_volume` / `_set_roof` helpers, plus `_cultivation_*` decorators. The ancillary volumes reuse the **existing** `side_wing`/`rear_shed` node construction patterns from `build_medium_house` (with `cross_gable_roof`/`lean_to_roof` handlers already registered in `ops.py`). This keeps one code path per archetype and avoids duplicating massing logic.

`SCALE_TIERS` footprint pools for the four archetypes are replaced by the per-variant footprints in the template table (or the table indexes deterministically into a widened pool); `rng` is retained only for within-template jitter (decoration placement, material `variation_rate`).

### Decision 3: 院墙 walled courtyard is a new ground-enclosure element, not a volume

Add a `courtyard_enclosure` node (tags `DETAIL`/`STRUCTURE`, **not** in `VOLUME_TYPES`) and an `ops.py` renderer that lays a low wall ring (1–2 cells) around an existing `courtyard_patch`, leaving a one-cell gate opening on the entry-adjacent side. Wall blocks resolve through `BALUSTRADE`/`RIDGE_ORNAMENT`/`PLATFORM_STONE`; the floor reuses `courtyard_patch`. This honours the existing cultivation-massing-grammar requirement "omit Western domestic tells" (no fences/woodpiles) and is the only genuinely new block in the change.

### Decision 4: Acceptance gate lives in the cultivation library validation

Extend the cultivation-town library report/validation (`quality.py` aggregation + the library validator) with two post-generation checks over each archetype's variant set:
- **Silhouette spread**: `max(silhouette) - min(silhouette) >= 30`.
- **Byte-identity**: the exported `.nbt` of any two variants of the same archetype must not be byte-identical (compare file hashes).

Failing either fails the library build and names the offending archetype/variants, so a future regression (e.g. someone re-collapsing the footprints) is caught automatically.

## Risks / Trade-offs

- **[Wider/taller footprints overflow town parcels]** → `densify-cultivation-town` already flags that frontage must account for variant footprints differing in width; cap template footprints to the existing parcel/module envelope (≤ the current largest cultivation footprint, ~17 wide) and verify against town generation before shipping.
- **[Added volumes raise block counts / interior-fill requirements]** → ancillary volumes get their own interior `_zone`s (storage/work) so they don't trip the "interior_required_function_blocks" quality check; budget is small (single-cell furniture clusters).
- **[3-volume v3 reads as cluttered at the smallest footprints]** → keep v3 footprints at the upper end of the pool and the 厢房/后罩房 proportioned (breadth derived from main depth, as `build_medium_house` does).
- **[院墙 conflicts with the "no fence patch" grammar rule]** → enforced by construction: enclosure uses stone/tile wall slots only; validation's existing Western-tell rejection still applies.
- **[Re-baselined assets churn the repo]** → expected and intended; the diff is the point. Land the generator change and the regenerated assets/report together in one commit.

## Migration Plan

1. Land the template tables, knob threading, and 院墙 renderer in `tools/buildgen/`.
2. Add the acceptance checks to the library validator.
3. Regenerate the four archetypes' `_001/_002/_003` `.nbt` + `place/*.mcfunction`, and `reports/cultivation_town_building_library_report.json`; confirm the gate passes (spread ≥30, no byte-identical pairs).
4. Regenerate per-structure previews and run the existing cultivation-town/town-generation validation + Gradle build.
5. Verify same-seed reproducibility across two generations.

Rollback: revert the generator change and regenerate, or restore the prior `.nbt`/report assets from git — no runtime/world-format coupling.

## Open Questions

- Should `cultivation_inn` v2's "重檐" reuse the shrine's `tiered_eave_roof` handler, or a lighter single-skirt eave? (Leaning reuse for silhouette consistency.)
- Do town parcels currently reserve enough rear depth for a v3 后院 + 后罩房, or does `densify-cultivation-town`'s back-row work need to land first for the largest variants? (Footprint cap should make them independent; confirm during step 4.)
