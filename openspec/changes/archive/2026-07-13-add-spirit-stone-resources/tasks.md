## 1. Contracts And Registration

- [x] 1.1 Validate the three existing Item Contracts and classify the stone as `plain_item` and both ores as block items.
- [x] 1.2 Register the low-grade item, two ore blocks, their BlockItems, creative-tab entries, and registered-block verification without adding item-use behavior.
- [x] 1.3 Add focused registration tests for exact ids, ordinary item properties, and iron-tier harvest intent.

## 2. Assets Loot And Tags

- [x] 2.1 Add bilingual names, blockstates, block/item models, and distinct item/stone/deepslate textures under the implemented `myvillage` namespace.
- [x] 2.2 Add loot tables for exact Silk Touch block drops, one-stone base drops, vanilla Fortune ore bonuses, and explosion decay.
- [x] 2.3 Add both ores to `minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool`, with negative wrong-tool coverage.
- [x] 2.4 Land every new asset/data output in the mod resource tree and verify all files are packaged in the practical jar; do not generate structure NBT.

## 3. World Generation

- [x] 3.1 Add the size-6 main and size-3 small configured features with stone and deepslate replacement targets.
- [x] 3.2 Add upper/middle/deep placed features with counts `30/3/3`, sizes `6/6/3`, iron-shaped upper/middle ranges, and a bottom-through-Y-0 deep range.
- [x] 3.3 Add one Overworld-only NeoForge `underground_ores` biome modifier for all three placed features.

## 4. Validation And Handoff

- [x] 4.1 Add focused resource/loot/tag/worldgen/jar validation and negative fixtures, then keep existing mod-item validation green.
- [x] 4.2 Update README usage and command-manual guidance with all `/give` ids, harvest/loot rules, new-chunk generation behavior, and the visual/manual checklist.
- [x] 4.3 Record texture/model evidence and leave inventory, placed-block, mining, and natural-frequency verdicts pending until real-client observation.
- [x] 4.4 Run strict change and baseline spec validation, focused Python tests, mod-item validation, Gradle tests/build, jar inspection, and a bounded acceptance-server smoke.

## 5. Shared Feature Release

- [x] 5.1 Participate in the owner-approved five-change `0.24.0` feature release: the final serial integration task SHALL update `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together exactly once, while this change records its resource/worldgen release notes and SHALL NOT perform a duplicate bump.
