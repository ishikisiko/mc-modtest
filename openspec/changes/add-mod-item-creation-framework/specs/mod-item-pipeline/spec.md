## ADDED Requirements

### Requirement: Mod item work starts from a skill-backed Item Contract

Requests to create, expose, revise, or validate a `myvillage:` mod item SHALL
route through the repo-local `mod-item-creation` skill before protected Java or
resource edits. The route SHALL classify the item as `plain_item`,
`block_item`, `decor_block_item`, or `functional_item`, and SHALL record an Item
Contract before implementation tasks proceed.

#### Scenario: A plain item is requested

- **WHEN** the owner asks to add a simple inventory item
- **THEN** the Commander SHALL route the work to `mod-item.full`
- **AND** the item contract SHALL name the item id, display name, creative tab,
  Java registration owner, model, texture, lang key, optional recipe/tag state,
  acceptance checks, and non-goals.

#### Scenario: A JSON item contract is written

- **WHEN** a task writes `artifacts/item_contract.json`
- **THEN** it SHALL conform to `genops/schemas/item_contract.schema.json`
- **AND** it SHALL explicitly state item properties or vanilla-default property
  intent.

#### Scenario: A functional item is requested

- **WHEN** the owner asks for an item with right-click behavior, cooldown,
  durability, data components, commands, or server-side effects
- **THEN** the item contract SHALL include the behavior boundary and test surface
- **AND** implementation SHALL stop for OpenSpec/spec-impact review when the
  behavior is not already covered.

### Requirement: Mod item work is split into atomic GenOps role tasks

The `mod-item.full` pipeline SHALL split item work across scoped roles instead
of allowing one unscoped patch to edit Java registration, assets, validation,
and docs together.

#### Scenario: Java and resources are separate tasks

- **WHEN** a mod item needs both Java registration and client resources
- **THEN** Java registration SHALL be owned by `java-runtime-engineer`
- **AND** item model, texture, lang, recipe, and tag resources SHALL be owned by
  `resource-asset-steward`.

#### Scenario: A simple smoke block item is added

- **WHEN** the framework adds `myvillage:test_item_block`
- **THEN** it SHALL register a plain block and BlockItem
- **AND** it SHALL ship blockstate, block model, block texture, item model, and
  lang resources
- **AND** `tools/validate_mod_items.py` SHALL pass for the item
- **AND** it SHALL NOT add gameplay behavior or generated structure resources.

#### Scenario: Visual item assets are new

- **WHEN** an item introduces or changes a visible texture or model
- **THEN** visual-review evidence SHALL record PNG path/dimensions, item model
  texture reference, jar packaging expectation, and whether human verdict is pending
- **AND** task success SHALL NOT be summarized as human visual acceptance.

### Requirement: The Commander routes natural-language item requests

The Commander SHALL recognize natural-language item requests, including
"物品", "item", "创造栏", "贴图", "模型", and "recipe", and recommend the
`mod-item.full` pipeline when that route is the strongest match.

#### Scenario: Owner asks for a mod item

- **WHEN** the owner says "用 CRAFT 创建一个新的 myvillage 物品"
- **THEN** Commander routing SHALL recommend `genops/pipelines/mod-item.full.yaml`
- **AND** the owner-facing surface SHALL remain decision-oriented rather than
  asking the owner to choose task ids or pipeline internals.

### Requirement: Front-door provenance covers item framework artifacts

Front-door checking SHALL treat project skills and project custom agents as
protected governance artifacts, and SHALL accept item resource/runtime ownership
from the new item roles when matching run evidence exists.

#### Scenario: A skill file is changed with evidence

- **WHEN** `.codex/skills/mod-item-creation/SKILL.md` changes
- **AND** CRAFT run evidence records docs-steward or pipeline-architect
  ownership for that path
- **THEN** front-door checking SHALL accept the change.

#### Scenario: A runtime item file is changed by the runtime worker

- **WHEN** a future item task changes `src/main/java/com/example/myvillage/item/ModItems.java`
- **AND** run evidence records `java-runtime-engineer` ownership for that path
- **THEN** front-door checking SHALL accept the runtime provenance.
