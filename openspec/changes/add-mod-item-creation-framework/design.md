## Decision: Skill first, not generator script first

The framework is intentionally a skill + CRAFT pipeline. It does not create a
one-shot item generator script because item creation has several different
failure modes: plain items, block items, decor block items, and functional items
need different ownership and stop conditions.

## Item Contract

Every item task starts with an Item Contract recording:

- item id and kind;
- display name;
- creative-tab visibility;
- Java registration owner;
- model, texture, and lang resource paths;
- recipe/tag expectations;
- behavior contract or `none`;
- acceptance checks and human-verdict state;
- non-goals.

The detailed contract reference lives in
`.codex/skills/mod-item-creation/references/item-contract.md`; machine-readable
JSON contracts use `genops/schemas/item_contract.schema.json`.

## Atomic role split

The pipeline adds two roles because the existing `java-worldgen-engineer` and
`generator-engineer` names were too broad for item work:

- `java-runtime-engineer`: Java registry/runtime surface.
- `resource-asset-steward`: client/data resources such as item models,
  textures, lang, recipes, and tags.

The remaining tasks use existing GenOps roles: cartography, spec review,
visual review, validation, docs, and regression.

## Validation shape

The current framework validates the route itself: skill metadata, pipeline load,
Commander routing, OpenSpec change validation, and front-door provenance. A
future concrete item change may add a dedicated item integrity validator once
there is enough item surface to avoid premature hardcoding.

For visible assets, the visual review task records PNG path/dimensions, item
model texture reference, jar packaging expectation, and human verdict state.
Inventory screenshots remain a stronger final acceptance artifact but are not
required for the framework to materialize.
