# Mod Item Creation

This note documents the CRAFT route for creating or revising `myvillage:` mod
items. It is a workflow contract, not a generator script.

See also: change spec `add-mod-item-creation-framework/specs/mod-item-pipeline`
and [GenOps Orchestration](19_genops.md).

## Current baseline

The implemented item surface is small: `ModItems.java` registers
`DeferredRegister.Items`, exposes the `myvillage:main` creative tab, and
currently provides `rockery_block` as a `BlockItem`. Item-facing resources live
under:

```text
src/main/resources/assets/myvillage/models/item/
src/main/resources/assets/myvillage/textures/item/
src/main/resources/assets/myvillage/blockstates/
src/main/resources/assets/myvillage/models/block/
src/main/resources/assets/myvillage/textures/block/
src/main/resources/assets/myvillage/lang/en_us.json
src/main/resources/data/myvillage/recipe/
src/main/resources/data/*/tags/item/
```

The project-local skill `.codex/skills/mod-item-creation/SKILL.md` is the
procedural entry point. The CRAFT pipeline is
`genops/pipelines/mod-item.full.yaml`. JSON contracts use
`genops/schemas/item_contract.schema.json`.

## Item kinds

- `plain_item`: Java item registration, creative-tab entry, lang, item model,
  item texture, optional recipe/tag.
- `block_item`: inventory exposure for an already registered block. If the
  block does not exist, only a simple full-cube smoke block may stay in
  `mod-item.full`; decor or non-cube blocks use the block/decor workflow first.
- `decor_block_item`: a `myvillage:` decorative block exposed as an item. It
  inherits `mod-decor-block-family`; the item is only the final inventory
  surface.
- `functional_item`: any item with behavior. It needs a behavior contract before
  code edits: interaction, server/client boundary, data components, tests, and
  game acceptance.

## Atomic CRAFT route

`mod-item.full` splits the work into role-owned tasks:

```text
context-cartographer -> pipeline-architect -> spec-guardian
  -> java-runtime-engineer
  -> resource-asset-steward
  -> visual-reviewer
  -> validator-engineer
  -> docs-steward
  -> regression-steward
```

The important boundary is that Java registration, resource assets, validation,
docs, and regression evidence are separate tasks. A request for a new item does
not authorize one large unscoped patch.

## Completion bar

Item work is not complete until the relevant parts are true:

- item id and kind are recorded in an Item Contract;
- Java registration and creative-tab exposure match that contract;
- item model JSON exists and all referenced textures exist;
- `en_us.json` has the display key;
- recipes/tags exist when promised;
- blockstate, block model, and block texture exist when the item is a block item;
- hand-placeable decorative block items support creative pick-block
  (`getCloneItemStack`) so middle-clicking the placed block returns the exposed
  item stack;
- default item properties, stack behavior, rarity, and creative-tab ordering are
  either explicit or intentionally vanilla-default;
- validators or focused checks cover the new surface when the change is
  repeatable;
- `python3 tools/validate_mod_items.py` passes;
- `./gradlew build` passes for jar handoff;
- new visible textures/models or gameplay behavior have a human verdict state.

Visual item assets are allowed to be task-complete while human verdict remains
pending. Do not claim aesthetic acceptance from file existence alone.

For a simple passive item that is only available through the creative tab and
`/give`, README/KB updates may be a documented no-op. Do not bloat user-facing
docs with every internal test item unless it changes commands, gameplay, or
acceptance prep.

## Smoke item block

`myvillage:test_item_block` is the first framework smoke target. It is a plain
full cube registered in `ModBlocks`, exposed as a `BlockItem` in `ModItems`,
shown in `myvillage:main`, and backed by normal blockstate/model/texture/item
model/lang resources. It exists to prove the CRAFT item route can produce an
actual placeable block item without touching generator structures.
