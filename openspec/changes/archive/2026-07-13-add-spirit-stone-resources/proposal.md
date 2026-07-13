## Why

The initiation ritual currently teaches a cultivation technique but gives survival players no cultivation resource to discover or stockpile. This change adds the smallest useful spirit-stone resource slice without coupling it to meditation before that runtime exists.

## What Changes

- Add `myvillage:low_grade_spirit_stone`, `myvillage:spirit_stone_ore`, and `myvillage:deepslate_spirit_stone_ore` with complete registration, creative-tab, bilingual, model, and texture resources.
- Require an iron-tier or better pickaxe for both ores; Silk Touch drops the matching ore block, while ordinary mining drops one low-grade spirit stone and Fortune uses the vanilla ore-drop formula.
- Add stone/deepslate ore targets plus upper, middle, and genuinely deep placed features through a NeoForge Overworld biome modifier: counts `30/3/3`, vein sizes `6/6/3`, and a deep-small band from the Overworld bottom through Y `0`.
- Mirror iron's three-layer placement shapes while pinning the spirit-stone values above; the result is intentionally below one third of vanilla iron's expected block output and is not an exact equivalence claim.
- Add Item Contracts, focused validation, jar checks, and a pending visual verdict for the three new textures.
- Add no raw item, smelting chain, recipe, higher grade, storage block, fragment, refining machine, currency behavior, or cultivation consumption.

## Capabilities

### New Capabilities
- `spirit-stone-resources`: Low-grade spirit-stone acquisition, ore drops/tool rules, three-layer Overworld placement, resource completeness, and visual handoff.

### Modified Capabilities
- `resource-export`: Package the hand-authored item, block, loot, tag, configured-feature, placed-feature, and biome-modifier resources without changing structure generation.
- `validation`: Validate registration, loot/tool semantics, worldgen references, jar contents, and truthful visual evidence.

## Impact

This affects item/block registration, client assets, loot tables, block tags, dynamic worldgen registries, NeoForge biome modifiers, validators, tests, README usage, and the coordinated feature release. It adds no Java worldgen bootstrap or external dependency.
