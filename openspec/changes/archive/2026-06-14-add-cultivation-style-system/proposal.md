## Why

The building library currently produces traditional medieval / Chinese-courtyard architecture, but the project wants a 修仙 (cultivation/xianxia) identity. That identity lives mostly in *built form* — multi-eave pagoda roofs, moon gates, spirit-arrays, terraced sect compounds — yet the generator can only produce three roof shapes (`gable`/`cross_gable`/`lean_to`) hardwired as `if` chains in `ops.py`, where even the Chinese `歇山/悬山/硬山` collapse back to `gable_roof`. There is no concept above `style` to distinguish whole settlement families. To grow a 修仙 world — and to keep adding bigger forms later — we need (1) an extensible form vocabulary instead of frozen `if` chains, and (2) a settlement-group layer that lets 城镇 (mortal town) and 宗门 (immortal sect) diverge cleanly while sharing one engine.

## What Changes

- Introduce a **settlement-group** layer above `style`: each group binds a style profile + an archetype roster + a layout strategy. Ship two groups — `cultivation_town` (mortal, street/market layout) and `cultivation_sect` (immortal, terraced axial compound).
- Replace the hardcoded roof-type and motif `if` chains with a **name→handler registry** so `allowed_roof_types` / `allowed_motifs` in a style profile become truly pluggable. Existing roofs/motifs are migrated into the registry with **no behavior change**. **BREAKING** for any code that imported the old dispatch functions directly.
- Add a **修仙 form vocabulary** to the shared engine: `tiered_eave_roof` (true multi-eave), `moon_gate` (round opening), `spirit_array` (ground formation motif), `incense_altar`, `cloud_rail`. Groups that don't list them never invoke them.
- Add two flat, independent style profiles: `cultivation_town.json` (mortal palette — timber/stone/clay tile, no spirit materials) and `cultivation_sect.json` (mortal base **plus 灵材**: quartz/calcite jade, amethyst, prismarine, oxidized copper, purpur/end stone, and spirit-glow lighting).
- Make `forbidden_blocks` genuinely per-style: the sect profile **unlocks** `quartz`, `copper`, `gold_block` (and similar) that the Chinese profile forbids; the town profile keeps them forbidden.
- Extend the style-profile schema with spirit material slots `SPIRIT_CRYSTAL` and `RITUAL_METAL`, omittable by mortal styles.
- Extend the courtyard-compound layer to support a sect terraced/axial layout (monumental scale, hierarchical building slots) alongside the existing courtyard.

Out of scope (deferred): final sub-flavor split (仙宫 bright vs 魔修 dark — default to 仙宫); whether spirit materials may bleed into town (small 道观 / 坊市 法器铺) — left as an open question in design.

## Capabilities

### New Capabilities
- `settlement-group`: A group layer above style that binds a style profile, an archetype roster, and a layout strategy into a named settlement family (`cultivation_town`, `cultivation_sect`); the documented extension hook for future families.
- `form-registry`: An extensible name→handler registry for roof types and decoration motifs, replacing hardcoded dispatch and letting style vocabularies be pluggable.
- `cultivation-form-vocabulary`: The 修仙 built-form set (`tiered_eave_roof`, `moon_gate`, `spirit_array`, `incense_altar`, `cloud_rail`) registered as engine-level forms.

### Modified Capabilities
- `style-profile`: Adds `SPIRIT_CRYSTAL` / `RITUAL_METAL` slots, makes `forbidden_blocks` per-style so the sect style unlocks 灵材, and adds the `cultivation_town` / `cultivation_sect` profiles.
- `building-generation`: Routes roof and motif placement through the form registry; selects archetypes via the active settlement group's roster.
- `courtyard-compound`: Adds a sect terraced/axial layout strategy alongside the existing courtyard compound.

## Impact

- **Code**: `tools/buildgen/ops.py` (roof + motif dispatch → registry), `tools/buildgen/passes.py` (registry calls), `tools/buildgen/archetypes.py` (group rosters), `tools/buildgen/compound.py` (sect layout), `tools/buildgen/style.py` (new slots, per-style forbidden), new `tools/buildgen/styles/cultivation_town.json` and `cultivation_sect.json`, `tools/buildgen/export.py` / `quality.py` (group-aware ids and gates).
- **New blocks unlocked**: quartz/calcite, amethyst, prismarine family, oxidized copper, purpur/end stone, sea lantern / end rod / froglight / soul lantern — all vanilla, MC 1.21.1.
- **Specs**: new `settlement-group`, `form-registry`, `cultivation-form-vocabulary`; deltas to `style-profile`, `building-generation`, `courtyard-compound`.
- **Compatibility**: medieval and Chinese-courtyard outputs must remain byte-stable after the registry migration (regression guard).
