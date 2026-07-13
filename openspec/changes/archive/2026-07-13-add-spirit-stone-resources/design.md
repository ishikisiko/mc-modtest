## Context

The mod has a CRAFT-backed item route and hand-authored block resources, but it
does not yet own a mineable cultivation resource or ordinary ore-feature data.
This change must add a complete item/block/loot surface and natural Overworld
acquisition without changing the structure NBT exporter or coupling the item to
the later meditation runtime.

## Goals / Non-Goals

**Goals:**

- Register one plain item and two ordinary ore blocks with matching BlockItems.
- Give both ores iron-tier harvest rules, Silk Touch preservation, and standard
  Fortune ore-drop behavior.
- Place the ore through deterministic configured/placed-feature JSON and a
  NeoForge biome modifier at the fixed `30/3/3` count and `6/6/3` size baseline.
- Package complete bilingual client/data resources and produce mechanical,
  packaging, and pending visual evidence.

**Non-Goals:**

- No raw ore, furnace chain, recipe, fragment, storage block, refining machine,
  currency, higher grade, XP reward, or cultivation consumption.
- No Java worldgen bootstrap, structure-template generation, sect/region qi
  binding, Nether/End placement, or changes to vanilla iron.

## Decisions

### Item and block ownership follows the existing item pipeline

`myvillage:low_grade_spirit_stone` is a `plain_item`.
`myvillage:spirit_stone_ore` and
`myvillage:deepslate_spirit_stone_ore` are blocks with ordinary BlockItems. The
three existing Item Contracts are the implementation boundary; Java
registration, resource assets, validation, and visual review remain separate
role tasks. This is preferred to a special ore item class because the first
slice has no use behavior or data components.

### Loot is data-driven and matches vanilla ore semantics

Both blocks require a correct iron-tier-or-better pickaxe. The block tags are
`minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool`. A Silk Touch tool
drops the exact mined block. Otherwise the loot table starts from one
`myvillage:low_grade_spirit_stone`, applies the vanilla `ore_drops` Fortune
formula, and applies explosion decay. No intermediate raw item or smelting
fallback exists.

### Worldgen pins values rather than comparing dynamically with iron

The main configured feature has stone and deepslate targets and size `6`; a
small configured feature has the same targets and size `3`. The placed features
are:

| id | count | configured size | height provider |
|---|---:|---:|---|
| `spirit_stone_ore_upper` | 30 | 6 | trapezoid, absolute 80 through 384 |
| `spirit_stone_ore_middle` | 3 | 6 | trapezoid, absolute -24 through 56 |
| `spirit_stone_ore_deep` | 3 | 3 | uniform, Overworld bottom through absolute 0 |

Each placement also uses `in_square` and `biome`. One
`neoforge:add_features` biome modifier injects all three into Overworld biomes
at `underground_ores`. This mirrors iron's layer shapes while avoiding a
version-sensitive claim that the final block count is exactly one third of
iron.

### Worldgen resources remain hand-authored pack data

Configured features, placed features, and the biome modifier are packaged
directly under `data/myvillage/`; they do not expand the current generated
structure exporter. A dedicated validator resolves every feature reference and
checks the fixed numeric and target contract before the bounded server smoke
proves datapack loading.

### Appearance requires an explicit verdict

The item and two ore textures receive deterministic asset evidence and jar
checks. Automated evidence cannot approve in-game readability against stone and
deepslate, so the final visual status remains `human_review_pending` until a
client observes inventory and placed/mined blocks.

## Risks / Trade-offs

- [The upper count is high even with a smaller vein] -> Pin all values in data
  and validator tests so later balance changes are explicit deltas.
- [Loot JSON can accidentally bypass the tool requirement] -> Test wrong-tool,
  Silk Touch, no-Fortune, and Fortune paths and verify both harvest tags.
- [Worldgen JSON can build but fail registry loading] -> Run strict resource
  validation, jar inspection, and a bounded dedicated-server startup.
- [New textures can be mechanically valid but visually unclear] -> Keep the
  human visual verdict pending until real-client evidence exists.

## Migration Plan

Add registrations before assets/data references, then add loot/tags, worldgen,
validators, tests, docs, and jar evidence. Existing worlds gain ore only in
newly generated chunks. Rollback removes the two features, modifier, blocks,
item, and resources; already generated unknown blocks require the mod to remain
installed or an operator-led world migration.

## Open Questions

None. Counts, sizes, heights, tool tier, loot behavior, and non-goals are fixed.
