## Context

The `chinese_courtyard` compound shipped in change `add-chinese-courtyard-compound` and has not been touched since. The intervening cultivation-form work (`rebuild-cultivation-building-form`, `rebuild-cultivation-settlement-form`, `differentiate-cultivation-variants`) rebuilt the cultivation family's silhouette and plan, leaving the Chinese courtyard on the older `gable_roof`-only skeleton. The result: the compound reads as four near-identical boxes around a lawn — the "Chinese-skinned manor" complaint from the proposal.

Two pieces of prior art make the rebuild cheap:

1. **Cultivation's massing grammar (`cultivation-massing-grammar` spec)** already defines `PLATFORM_STONE` 台基, `COLUMN` 檐廊, and the standoff-column + deep-eave pattern. The work is *reusing* these for the vernacular register, not inventing them.
2. **`differentiate-cultivation-variants`** proved the deterministic-template-table pattern that turns "6 byte-identical variants" into "6 visibly distinct forms". This change applies the same pattern to `CompoundVariant`.

Constraints:

- **No new external-mod dependency**: `chinese_courtyard` is the vanilla-cleanest compound family. New `PLATFORM_STONE` / `COLUMN` slots must resolve to vanilla fallbacks under both `vanilla` and `full` profiles.
- **No `/myvillage` command-surface change**: filenames `chinese_courtyard_001..006` stay; only their content changes. Reviewers keep their `/place` muscle memory.
- **Python-only**: this change does not touch any Java realizer (the Chinese courtyard is a static NBT library, not a runtime-generated compound like the town/sect).
- **Spec-compatible additions**: existing `courtyard-compound` requirements are honored (perimeter closed, gate on perimeter, water/planting structural, corridors connect wings). The outer/main yard split, 影壁, 垂花门, and 抄手游廊 are *additions*, not replacements of those invariants.

## Goals / Non-Goals

**Goals:**

- A reviewer looking at a shipped `chinese_courtyard_NNN.nbt` (preview + in-game `/place`) reads "四合院" within 2 seconds, not "Chinese-skinned manor".
- The 6 shipped NBTs are visibly distinct at thumbnail distance — differ on `layout_type`, `main_bays`, or `roof_grade`, not just decoration.
- Real 硬山 / 悬山 / 歇山 / 卷棚 silhouettes appear in the mod for the first time.
- The form-vocabulary work is reusable: any future style (e.g. 魔修 profile, see `docs/ai-kb/14_deferred_roadmap.md` §D) can list the four `chinese_*` roof forms in `allowed_roof_types` and get them.
- The CompoundGraph gains enough plan vocabulary (outer yard / main yard / 垂花门 / 抄手游廊 / 月台) that a follow-up 二进/三进 change extends rather than rewrites.

**Non-Goals (explicitly deferred, see `docs/ai-kb/14_deferred_roadmap.md` §E):**

- 二进 / 三进 compounds and the `jin_count` master axis abstraction.
- The 花园 / 假山 / 自由曲线水池 that break the orthogonalCompound axis (these are the most invasive part of 三进 and want their own capability).
- Cross-family integration: making the small-courtyard unit (used by `cultivation_town`'s street blocks) inherit from the rebuilt 一进. The small-courtyard may stay simplified on purpose (街面建筑 less formal than a 府邸).
- A bay-grammar *interior* walkable model (the bays are placed as zones inside the sub-building, not as a separately-traversable timber frame). Full walkable timber framing is a deeper change.

## Decisions

### D1: Four new roof forms in `ROOF_REGISTRY`, not a `chinese_roof` super-handler

**Choice:** Register `chinese_flush_gable`, `chinese_overhang_gable`, `chinese_half_hip`, `chinese_round_ridge` as four separate handlers in `ROOF_REGISTRY` (the existing name→handler registry from `form-registry`).

**Alternatives considered:**

- *One `chinese_roof` handler taking a `grade` parameter.* Rejected: the four forms have **different silhouettes** (歇山 has a 45° skirt; 卷棚 has no ridge block), so the dispatch would be hidden inside one handler — re-creating the old `if` chain the form-registry was built to replace. Per `AGENTS.md`: "Do not add new form dispatch by string-matching inside passes."
- *Reuse the cultivation `sweeping_eave_roof` / `hip_roof` / `pyramidal_roof` forms directly.* Rejected: those are monumental (仙府) proportions — deep overhang, tall platform, dramatic curve. Vernacular (民居) roofs are smaller overhang, plain rafter feet, no drama. Sharing the form would force a `register=monumental|vernacular` parameter and complicate every call site. Better to have distinct, narrowly-scoped handlers.

**Rationale:** Four handlers, one per form, mirrors how cultivation's roof vocabulary is structured and keeps each handler's contract narrow. The shared geometry helpers (eave curve, ridge cap) live in a private `_chinese_roof_helpers` module the four handlers import.

### D2: Outer yard / main yard split is plan-only — no new top-level type

**Choice:** Keep the existing `CompoundGraph` dataclass. The two-yard split is encoded as **z-band regions of the same `CompoundGraph`** with a `垂花门` `ParcelNode` between them. `CompoundGraph.meta` gains `outer_yard_band` and `main_yard_band` `(z0, z1)` tuples for downstream consumers.

**Alternatives considered:**

- *A new `MultiYardCompound` subclass with per-yard subgraphs.* Rejected: it would fork every consumer (validator, exporter, realizer) into "is this a single-yard or multi-yard compound?" branches. The y-band approach keeps the graph flat — the validator just checks the z-band invariants.
- *Wait for the `jin_count` abstraction (D4) and do it then.* Rejected: it would force this change to ship nothing visible while waiting for the larger 二进/三进 work. The z-band abstraction generalizes cleanly to `jin_count` later: `yard_bands = _compute_yard_bands(jin_count, lot_d)`.

**Rationale:** The smallest data-model change that lets the layout express "two yards, one gate between them". The follow-up `jin_count` change is then `(jin_count, lot_d) → list of z-bands` plus the per-band node population — a refactor, not a redesign.

### D3: Variant axes are a deterministic template table, not RNG-stream choice

**Choice:** `select_variant(seed)` becomes a lookup against a fixed table of 6+ hand-authored `CompoundVariant` rows, each pinning a distinct `(layout_type, main_orientation, main_bays, roof_grade, platform_tier, gate_type)` tuple. `seed % len(TEMPLATES)` picks the row; the remaining minor axes (`water_form`, `planting_layout`) are still RNG-derived.

**Alternatives considered:**

- *Independent RNG `rng.choice` per axis.* This is today's design and is exactly why the 6 NBTs collapse to near-identical: with 3 bays × 4 roofs × 3 platforms × 3 gates = 108 combinations, but the 6 sampled seeds cluster on the same low-entropy corner of the space. Rejected (already failed in production).
- *RNG but with `differentiate-cultivation-variants`-style acceptance gate.* Rejected as overkill for 6 NBTs: a hand-authored table of 6 visibly-distinct rows is reviewable in one screen, requires no silhouette-score machinery, and is trivially extensible if a future change wants 9 or 12 NBTs.

**Rationale:** Same reasoning as `differentiate-cultivation-variants`: "Make each archetype's three variants deliberately distinct 形制, not three random rolls." The compound has six slots to fill, so the table has six rows.

### D4: `chinese_courtyard` style gains `PLATFORM_STONE` and `COLUMN` slots with vanilla-only fallbacks

**Choice:** Add `PLATFORM_STONE` (`minecraft:stone_bricks`, `minecraft:polished_andesite`, `minecraft:smooth_stone`) and `COLUMN` (`minecraft:stripped_dark_oak_log`, `minecraft:oak_fence` [as infill between standoff columns]) slots to `chinese_courtyard.json`. No external-mod ids in the lists — the vernacular register stays vanilla-clean by design.

**Alternatives considered:**

- *Inherit `PLATFORM_STONE` / `COLUMN` from `cultivation_sect`.* Rejected: the sect profile unlocks 灵材 (quartz/copper/gold/prismarine/purpur) that are explicitly forbidden in the vernacular profile. Slot inheritance would either bypass the `forbidden_blocks` gate or require a per-style override machinery we don't have.
- *Skip the slots and hardcode the block ids in the renderer.* Rejected: violates the "no hardcoded block ids in a renderer" rule from `AGENTS.md`.

**Rationale:** Two new slots, vanilla-only, kept narrow. The `cultivation-massing-grammar` spec's slot definitions are reused verbatim; only the populated block list differs.

### D5: 抄手游廊 is a 3×3 covered gallery, not a 1-wide ground path

**Choice:** Replace `_route_circulation`'s side-corridor BFS path with a **3-wide × 3-tall roofed gallery** along each side of the main yard, running from the 垂花门's flank to the main hall's flank. The gallery has: standoff columns at the inner edge, an outer half-wall with 漏窗, and a single-eave roof tying into both the 垂花门 and the main hall's eave line.

**Alternatives considered:**

- *Keep the ground-gravel path as "corridor" and add a separate covered-gallery ParcelNode type.* Rejected: it would let the renderer silently regress to drawing just the ground path. Replacing the corridor outright makes the renderer's contract honest.
- *Wait until 二进 (where 抄手游廊 is more dramatic) and skip it in 一进.* Rejected: 抄手游廊 is the single biggest visual lever for "this is a 四合院 not a manor" in 一进. Without it the outer/main yard split reads as two lawns, not a 序列.

**Rationale:** Reuses the gallery geometry pattern from `sect.py:_place_sect_link`'s covered-gallery code path, scaled down to vernacular proportions.

### D6: `layout_type=three-sided` (三合院) is included even though the proposal framing is "一进"

**Choice:** The `layout_type` axis includes `three-sided` (三合院: no 倒座, U-shaped opening toward the gate). Three-sided is plan-distinct from `standard`, and is the dominant form for smaller rural 府邸, so a 6-NBT library that includes one three-sided variant reads as more authentic than six standard ones.

**Alternatives considered:**

- *Strictly "one-courtyard only" — defer 三合院 to its own change.* Rejected: 三合院 is a one-line branch in `_layout_outer_yard` ("if `layout_type == "three-sided"`: skip the front_row, move wings forward"). Splitting it out would inflate ceremony over substance.

**Rationale:** The `courtyard-compound` spec's "Chinese one-courtyard axial layout" requirement lists "two side_wing buildings" and "perimeter on four sides" but does not require a `front_row`. Three-sided is spec-compatible by deletion.

### D7: No plaque block on 厢房/倒座, only on `main_hall`'s central bay

**Choice:** Only `main_hall` gets a plaque (`myvillage:wall_plaque`) — bearing a 名 (name) like "XX堂" or "XX斋". 厢房 / 倒座 / 垂花门 are unplaqued. The 垂花门 gets a smaller signature detail (the hanging-lotus-column ends) instead.

**Alternatives considered:**

- *Plaque every building.* Rejected: it dilutes the main hall's visual primacy and breaks the architectural-hierarchy rule that plaque = 名 of the principal hall.

**Rationale:** Matches historical practice (only the 堂/斋 gets a 匾) and reinforces the compound's hierarchy at no extra plaque-block cost.

## Risks / Trade-offs

- **[NBT byte-stability for worlds that placed old `chinese_courtyard_*`]** → The 6 NBTs are regenerated; old placements keep old blocks because they're already in chunk NBT, but `/myvillage place chinese_courtyard_001` after the upgrade places the new form. **Mitigation:** document in `CHANGELOG.md` as a breaking-regeneration; the mod is pre-1.0 and ships no migration story for placed structures.
- **[Visual regression: the rebuilt compound's silhouette_score may shift enough to look like a different family]** → Run `validate_compound_library` and the cultivation/medieval regression guards; confirm medieval and cultivation libraries stay byte-stable. The Chinese courtyard library is expected to diff heavily — that is the point of the change.
- **[歇山 form is the most complex — risk of a roof-hole class bug like the side-wall incident (`fix-side-wall-cleanup`)]** → The 歇山 handler records its gable-triangle and 抱厦-skirt placement cells so the post-build enclosure check can re-verify. Add a `validate_compound` rule that every 歇山 roof plane is closed.
- **[Compound lot size grows — risk of breaking the 16-chunk force-load path]** → Largest rebuilt lot stays ≤ `48 × 48` (well under any chunk boundary). No realizer change needed; the library path doesn't force-load.
- **[Variant template table is hand-authored — risk of "two rows look too similar"]** → Add an acceptance rule in `validate_compound_library`: the 6 shipped NBTs must differ on `silhouette_score` by ≥ 15 within the library, mirroring `differentiate-cultivation-variants`'s gate.
- **[Platform raises the whole main yard — risk of a 1-block air gap under the perimeter wall where it meets the raised platform]** → `_add_perimeter` runs *after* `_layout_main_yard` so the wall extends down to the original ground; the platform stops short of the wall by 1 cell. Verified by a `validate_compound` rule that no perimeter wall cell has air below it.
- **[Plaque block requires `myvillage:` namespace — vanilla-profile validation must exempt it]** → Already covered by `add-custom-plaque-blocks`'s validator change (the `myvillage:` self-namespace exemption). No new validation work here.

## Migration Plan

1. **Form vocabulary first** (Step 1): register the four `chinese_*` roof forms, add `PLATFORM_STONE` / `COLUMN` slots to the style, rewrite the four sub-building builders. Regenerate the 6 NBTs but keep the *old* layout (single yard). Diff preview. The visual should already read "more Chinese" without any layout change.
2. **Layout second** (Step 2): split `generate_compound` into outer/main yard, add 影壁 / 垂花门 / 抄手游廊 / 月台, swap `select_variant` to the template table, regenerate. Diff preview.
3. **Validate and close**: `validate_compound_library` green; cultivation/medieval regression guards green; `CHANGELOG.md` notes the regeneration; the staged manual acceptance review places ≥2 NBTs in-game to confirm.

No runtime migration (no saved-world NBT migration is performed or promised).

## Open Questions

- **Should the rebuilt compound expose ` CompoundVariant.layout_type == "three-sided"` to the town-block tiler?** The town block uses `generate_small_courtyard` (a different entry), so this change does not affect it — but if a future change lets the town block tile full 一进 compounds (e.g. for civic plots), the answer affects which `layout_type` values are town-safe. Defer until that change is proposed.
- **Should `chinese_round_ridge` (卷棚) appear in the shipped library at all?** 卷棚 is more refined (often used on 园林 / 书房), not on the main hall of a 府邸. The current proposal allows it on any building; an alternative is to restrict it to `side_wing` only. Pick during implementation review based on visual read.
- **Should the `垂花门` carry a `myvillage:wall_plaque` of its own (a small 额)?** Historical practice varies. Default: no (D7), but a single small plaque on the 垂花门 could be a compelling detail. Defer to visual review.
