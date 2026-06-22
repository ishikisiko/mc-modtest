## Why

The shipped `chinese_courtyard` compound (`chinese_courtyard_001..006.nbt`, ~6.6k voxels each) is structurally a Chinese-skinned medieval manor, not a 四合院. Four independent root causes:

1. **All four sub-buildings share one skeleton.** `_chinese_graph` builds `main_hall` / `side_wing` / `front_row` / `gate_house` from the same `fh=1, wall_h=4` shell; only the footprint differs (`archetypes.py:1231-1320`). The result is four near-identical silhouettes — only `main_hall` is 2 stories — so the compound reads as four sheds around a lawn, not a hierarchy.
2. **The "硬山/悬山/歇山" roof grades are fake.** `_apply_chinese_roof` maps all three grades to `gable_roof` with overhang `1/2/1` (`archetypes.py:1239-1246`). `歇山` should have a 45° 抱厦 + 围脊; `悬山` should overhang past the gable wall; `硬山` should sit flush. The `add-cultivation-style-system` proposal called this out ("even the Chinese 歇山/悬山/硬山 collapse back to `gable_roof`") and fixed it for `cultivation_*` — but `chinese_courtyard` was never touched.
3. **There is no outer/main yard split.** `generate_compound` places the gate, front row, two wings, and main hall in one undifferentiated rectangle (`compound.py:498-559`). A real 一进四合院 has an **outer yard (外院, with 倒座 + 影壁)** and a **main yard (主院, with 厢房 + 正房 + 月台)**, separated by exactly one **垂花门**. There is no 影壁 (you see straight into the main yard from the street), no 垂花门 (the "一进" namesake), and no 抄手游廊 (the side corridors are ground-gravel BFS paths, not 3-wide 3-tall covered galleries).
4. **The 6 shipped NBTs differ only at the decoration layer.** `select_variant` axes are `courtyard_size / water_form / planting_layout / roof_grade / gate_style / symmetry` — every axis changes a surface treatment, never the **形制** (plan, building count, orientation, bay count). Two of the six shipped NBTs are visually near-identical at thumbnail distance.

This change rebuilds the 一进 courtyard's **built form** (Step 1) and **plan** (Step 2) so a reviewer reads "四合院" instead of "Chinese-skinned manor". It deliberately does **not** deliver 二进/三进 compounds — those land in a follow-up tracked in `docs/ai-kb/14_deferred_roadmap.md`.

## What Changes

### Step 1 — Form vocabulary (built form)

- **Register four real Chinese roof forms** in the `ROOF_REGISTRY` (`tools/buildgen/ops.py`): `chinese_flush_gable` (硬山, gable flush with the side wall), `chinese_overhang_gable` (悬山, roof overhangs past the gable wall), `chinese_half_hip` (歇山, upper half is 悬山 over the gable, lower half is a 45° 抱厦 skirt with 围脊), and `chinese_round_ridge` (卷棚, no main ridge — a smooth circular bar instead). These are the same forms the `cultivation-form-vocabulary` spec already names abstractly; this change realizes the 民居 (vernacular) variants distinct from the 仙府 (monumental) variants already shipped for cultivation.
- **Delete `_apply_chinese_roof`** and its 1/2/1 overhang lie. The Chinese courtyard style's `allowed_roof_types` lists the four new forms.
- **Feed back cultivation's massing grammar to `chinese_courtyard`**: each Chinese sub-building gains a **`PLATFORM_STONE` 台基** (1–2 block stone plinth under the building), a **`COLUMN`-slot 檐廊** (standoff columns + deep eave on the street-facing side), and the `chinese_courtyard` style JSON gains those slots with vanilla fallbacks. Cultivation already proved this grammar; this is a re-skin for the vernacular register.
- **Plaque integration**: the `main_hall`'s central bay carries a `myvillage:wall_plaque` (the plaque-block family from change `add-custom-plaque-blocks`) with a cultivation/civic-tier inscription. The plaque-binding JSON gains a `main_hall` entry.

### Step 2 — Plan (layout)

- **Introduce an outer-yard / main-yard split** in `generate_compound` so the 一进 is now two z-bands separated by exactly one **垂花门** (inner gate), matching the canonical Beijing definition ("一进 = one 垂花门").
- **Add a 影壁 (screen wall)** parcel node inside the street gate, blocking the direct sightline to the main hall. The screen wall is a free-standing 6-tall wall with a cap ridge, distinct from the perimeter.
- **Add a 垂花门 (inner gate) parcel node** between the outer and main yards — an independent roofed gate-house with the signature 垂莲柱 (hanging-lotus-column) detail, on the central axis.
- **Replace the side-corridor ground paths with 抄手游廊 (covered galleries)**: 3-wide × 3-tall roofed corridors running along both sides of the main yard, connecting the 垂花门's two flanks to the main hall's two flanks. Reuses the covered-gallery geometry pattern from `sect.py`'s gallery link code.
- **Add a 月台 (moon platform)** parcel node: a 2-tall stone apron in front of the main hall, between the hall's 台基 and the main yard, where ceremonial activity happens.
- **Add courtyard dressing**: one 院中树 (large tree — 枣/槐/石榴) in the main yard center-offset, and optional 鱼缸 / 石榴缸 at the yard corners. These are new `courtyard_tree` / `water_jar` parcel-node types.
- **Perimeter wall gets a wall cap**: the current 4-tall wall + slab cap becomes a proper tile-cap with end-of-wall 墙垛 (piers) and optional 漏窗 (lattice window) cutouts. Same footprint, real silhouette.

### Variant axes rebuilt (so the 6 NBTs actually differ)

- **`layout_type`** (new, primary axis): `standard` (full 一进, default), `three-sided` (三合院 — no 倒座, U-shaped), `mu` (目字 — narrow north-south outer yard band). **This is the first plan-level variant axis the compound has ever had.**
- **`main_orientation`** (new): `south` (default), `east`, `north` (back-of-lot facing). Gates the `gate_side` meta plus main-hall placement.
- **`main_bays`** (new): `3` / `5` / `7`. The main hall footprint widens to match, with a true bay grammar inside (明间 / 次间 / 梢间 / 尽间 zone pattern).
- **`roof_grade`** (rebuilt): one of the four new forms (above), no longer a 1/2/1 overhang tweak.
- **`platform_tier`** (new): `none` / `stone_2` / `xumi_3` — controls whether the main yard sits on a 2-tall stone plinth or a 3-tall 须弥座 (sumi pedestal).
- **`gate_type`** (new): `guangliang` (广亮大门 — gateway passes through a building), `manzi` (蛮子门 — gate flush with wall), `jinzhu` (金柱门 — gate set into the front columns). Replaces the old `gate_style` axis which only changed gate-half width.
- **`water_form` / `planting_layout`** kept but demoted to minor axes.
- **`select_variant` becomes a deterministic template table** keyed on `seed % len(templates)` (same pattern as `differentiate-cultivation-variants`), so each shipped NBT lands on a visibly different `layout_type × main_bays × roof_grade` combination.

### Breaking changes

- **BREAKING (NBT regeneration)**: the 6 shipped `chinese_courtyard_*.nbt` files regenerate with different silhouettes, footprints, and interiors. Same filenames, different content. Worlds that placed the old NBTs keep them; only new placements get the new form.
- **BREAKING (compound JSON schema)**: `CompoundVariant` gains 5 new required fields (`layout_type`, `main_orientation`, `main_bays`, `platform_tier`, `gate_type`); `gate_style` is renamed `gate_type` with a new value set. Existing serialized `compound_library_report.json` entries with the old schema are obsolete.
- No `/myvillage` command-surface change. `/myvillage place chinese_courtyard_001..006` keeps working.

## Capabilities

### New Capabilities

- `chinese-vernacular-roof-vocabulary`: four real Chinese roof forms (硬山 flush gable, 悬山 overhang gable, 歇山 half-hip with 抱厦 + 围脊, 卷棚 round ridge) registered in `ROOF_REGISTRY` as the vernacular (民居) counterparts to cultivation's monumental forms, available to any style that lists them in `allowed_roof_types`.

### Modified Capabilities

- `courtyard-compound`: gains the outer-yard / main-yard split with exactly one 垂花门 between them; gains the 影壁 / 垂花门 / 抄手游廊 / 月台 / 院中树 / 鱼缸 parcel-node types; gains `layout_type` as a first-class plan-level variant axis (三合院 / 目字 alongside standard); gains the platform-tier and bay-count knobs; rewrites the "Chinese one-courtyard axial layout" requirement to reflect the two-yard, one-inner-gate definition.
- `style-profile`: the `chinese_courtyard` profile gains `PLATFORM_STONE` and `COLUMN` slots (vanilla fallbacks only — no new external mod deps); its `allowed_roof_types` lists the four new `chinese_*` roof forms.
- `cultivation-massing-grammar`: explicitly notes that `PLATFORM_STONE`, `COLUMN`, and the 檐廊 pattern are shared with the vernacular (Chinese courtyard) register, not cultivation-exclusive — the slot definitions and column-rendering path are reused.
- `plaque-block-family`: gains the `main_hall` archetype binding (a Chinese-courtyard main-hall central-bay tablet).

## Impact

- **Code (form)**: `tools/buildgen/ops.py` — four new roof handlers + wall-cap / 垂花门 / 月台 renderers; `tools/buildgen/archetypes.py` — rewrite `build_main_hall` / `build_side_wing` / `build_front_row` / `build_gate_house` onto the cultivation massing grammar (platform + standoff columns + bay zones), delete `_apply_chinese_roof`.
- **Code (layout)**: `tools/buildgen/compound.py` — `generate_compound` splits into `_layout_outer_yard` / `_layout_inner_gate` / `_layout_main_yard`; new parcel-node renderers for 影壁 / 垂花门 / 抄手游廊 / 月台 / 院中树 / 鱼缸; `select_variant` becomes a deterministic template table; `CompoundVariant` gains 5 fields; `_add_perimeter` gains a wall-cap pass.
- **Style**: `tools/buildgen/styles/chinese_courtyard.json` — adds `PLATFORM_STONE` / `COLUMN` slots (with vanilla fallbacks), updates `allowed_roof_types`, retunes `proportions` (deeper overhang, taller plinth, ~½ roof ratio).
- **Assets**: `src/main/resources/data/myvillage/structure/chinese_courtyard_001..006.nbt` regenerate (silhouettes visibly differ across the six); `reports/compound_library_report.json` / `compound_library_validation.json` regenerate.
- **Specs**: new `chinese-vernacular-roof-vocabulary`; deltas to `courtyard-compound`, `style-profile`, `cultivation-massing-grammar`, `plaque-block-family`.
- **Docs**: `docs/ai-kb/10_civic_family.md` (the courtyard family note) gains the form/layout rebuild summary with a see-also to `courtyard-compound`; `docs/ai-kb/14_deferred_roadmap.md` §E entry for "multi-courtyard (二进/三进)" is enriched with the design sketch from this change's exploration so the follow-up has a starting point; README command list unchanged (filenames preserved).
- **Compatibility**: `cultivation_*` and `medieval_*` libraries stay byte-stable (they never invoke the `chinese_*` roof forms). Vanilla-profile `chinese_courtyard` output resolves every new slot to its `minecraft:` fallback.
- **Out of scope (tracked in `docs/ai-kb/14_deferred_roadmap.md` §E)**: 二进 / 三进 compounds, the 花园 / 假山 / 自由曲线水池 that break the orthogonal axis, the `jin_count` master axis abstraction, and any town-block-level integration of multi-jin compounds.
