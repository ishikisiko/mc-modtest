# Item Contract Reference

Optional explicit GenOps item contract shape. Normal work follows docs/WORKFLOW.md.
The contract can live as JSON in GenOps evidence or as a concise markdown block
in a concise design note.

## Required Fields

- `item_id`: `myvillage:<path>`.
- `kind`: one of `plain_item`, `block_item`, `decor_block_item`,
  `functional_item`.
- `display_name`: English `en_us` display text and any Chinese design name.
- `owner_facing_goal`: why the item exists for players or review.
- `creative_tab`: existing tab id or reason it is intentionally hidden.
- `java_registration`: class/file that owns registration.
- `client_assets`: model path, texture path, lang key.
- `data_resources`: recipes, item tags, loot/advancement hooks, or `none`.
- `behavior`: `none` for passive items; otherwise server/client boundary,
  interaction, cooldown/durability/data-component needs, and test surface.
- `acceptance`: commands/checks, jar resource checks, and visual verdict state.
- `non_goals`: explicitly excluded behavior or resource scope.

When the contract is stored as JSON, keep it compatible with
`genops/schemas/item_contract.schema.json`.

## Kind-Specific Expectations

`plain_item` needs `DeferredRegister.Items`, creative-tab entry, lang, model,
texture, and optional recipe/tag resources.

`block_item` needs the block to already exist or to be created by a separate
block task first. A simple full-cube smoke block may stay inside
`mod-item.full` when the contract explicitly names the block registration and
block asset paths. Decor or non-cube blocks must use the decor/block workflow.

`decor_block_item` inherits `mod-decor-block-family`: block id, blockstate,
model variants, textures, VoxelShape, validator treatment, then a BlockItem.

`functional_item` is not a cosmetic resource task. It needs a behavior contract
and focused tests before code edits.

## Defaults

For `plain_item`, use default `new Item.Properties()` unless the owner or
contract explicitly sets max stack size, rarity, durability, food, or data
components. The default max stack size is the vanilla item default. Add the
creative-tab entry after the closest related MyVillage item, or append at the
end when there is no related item.

Do not infer recipe, tag, tooltip, right-click behavior, durability, or rarity
from the item name. If it is not in the contract, it is out of scope.

## Visual Evidence

For a new or changed item texture/model, record at least:

- the source PNG path and dimensions;
- the item model JSON path and `layer0` or custom model reference;
- whether the jar contains both files after build when jar handoff is in scope;
- a human verdict state: `pending`, `accepted`, `rejected`, or
  `accepted_with_changes`.

An inventory screenshot is preferred for final acceptance but not required for
framework-level task completion unless the owner asks for in-game review.

## Default Paths

- Java items: `src/main/java/com/example/myvillage/item/ModItems.java`
- Java blocks: `src/main/java/com/example/myvillage/block/ModBlocks.java`
- Item models: `src/main/resources/assets/myvillage/models/item/<id>.json`
- Item textures: `src/main/resources/assets/myvillage/textures/item/<id>.png`
- Blockstates: `src/main/resources/assets/myvillage/blockstates/<id>.json`
- Block models: `src/main/resources/assets/myvillage/models/block/<id>.json`
- Block textures: `src/main/resources/assets/myvillage/textures/block/<id>.png`
- Lang: `src/main/resources/assets/myvillage/lang/en_us.json`
- Recipes: `src/main/resources/data/myvillage/recipe/<id>.json`
- Item tags: `src/main/resources/data/<namespace>/tags/item/<tag>.json`

## Stop Conditions

Stop and ask for owner direction when the item changes gameplay scope, needs a
new block/decor family, introduces new external dependencies, lacks approved
texture direction, changes release metadata, or would require a broad generated
resource rewrite.
