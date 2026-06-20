# Knowledge Base Map

The single entry point for this project's knowledge base. Two bodies of documentation:

- **`docs/ai-kb/`** — narrative, factual technical notes (schemas, formats, conventions, checklists). Read these to learn *how things work*.
- **`openspec/specs/`** — normative capability specifications (SHALL/SHALL-NOT + Gherkin scenarios). Read these for *what the behavior must be*.

When a doc here and a spec cover the same topic they carry **see-also** links to each other; follow them to move between the narrative and the contract.

## Learning chain (`docs/ai-kb/`)

| # | Doc | Purpose |
|---|-----|---------|
| 00 | [Project Brief](00_project_brief.md) | Long-term target: multi-category town generation, not a simple village generator. |
| 01 | [Versions](01_versions.md) | Target Minecraft / NeoForge versions. |
| 02 | [Blueprint Schema](02_blueprint_schema.md) | JSON blueprint format for structures and town pieces. |
| 03 | [Block Palette](03_block_palette.md) | Namespaced block id conventions. |
| 04 | [Blockstate Rules](04_blockstate_rules.md) | How blockstates are represented and oriented. |
| 05 | [Vanilla Structure NBT](05_vanilla_structure_nbt.md) | `.nbt` structure-block format. |
| 06 | [Sponge Schematic](06_sponge_schem.md) | `.schem` external-editor format. |
| 07 | [NeoForge Worldgen](07_neoforge_worldgen.md) | Worldgen data layout for NeoForge data packs. |
| 08 | [Converter API](08_converter_api.md) | Shared converter tool flow. |
| 09 | [Validation Checklist](09_validation_checklist.md) | Pre-acceptance validation + preview/acceptance command checklist. |
| 10 | [Civic Family Vocabulary](10_civic_family.md) | Civic structure family (`tavern`, `lord_manor`). |
| 11 | [Town Shape Irregularity](11_town_shape_irregularity.md) | Design note: de-square the town via wall deformation (A) and/or district de-gridding (B). |
| 12 | [Town Shape Vocabulary](12_town_shape_vocabulary.md) | Implemented wall families, seed-driven grid jitter, and clip-to-shape districts; see-also `town-plan`, `town-districts`, and note 11. |
| 13 | [Region Topology](13_region_topology.md) | Offline-first 洲/域 layer: region-profile catalog + constrained-random topology generator (连/隔 edges, tier-step, walled 魔域); OTG WorldConfig/FromImage mapping; see-also `07_neoforge_worldgen.md` and the `region-profile` / `region-topology` specs. |

Topic notes: [Plaque Frame Brief](plaque-frame-brief.md), [Plaque Inscription Style](plaque-inscription-style.md). Reference material under [`references/`](references/), [`style_guides/`](style_guides/), [`examples/`](examples/).

## Capability specs (`openspec/specs/`)

Grouped by layer. Each links the spec; same-topic ai-kb docs are noted.

- **Building** — [building-generation](../../openspec/specs/building-generation/spec.md), [style-profile](../../openspec/specs/style-profile/spec.md), [form-registry](../../openspec/specs/form-registry/spec.md), [cultivation-form-vocabulary](../../openspec/specs/cultivation-form-vocabulary/spec.md), [cultivation-massing-grammar](../../openspec/specs/cultivation-massing-grammar/spec.md), [multi-story-massing](../../openspec/specs/multi-story-massing/spec.md), [orientation-adapter](../../openspec/specs/orientation-adapter/spec.md)
- **Compound / parcel** — [courtyard-compound](../../openspec/specs/courtyard-compound/spec.md), [sect-compound-layout](../../openspec/specs/sect-compound-layout/spec.md), [sect-compound-realization](../../openspec/specs/sect-compound-realization/spec.md), [civic-archetype-family](../../openspec/specs/civic-archetype-family/spec.md), [civic-precinct-framing](../../openspec/specs/civic-precinct-framing/spec.md), [plaque-block-family](../../openspec/specs/plaque-block-family/spec.md)
- **Town** — [town-plan](../../openspec/specs/town-plan/spec.md), [town-districts](../../openspec/specs/town-districts/spec.md) (see-also [11_town_shape_irregularity.md](11_town_shape_irregularity.md)), [town-realization](../../openspec/specs/town-realization/spec.md), [town-block-variety](../../openspec/specs/town-block-variety/spec.md), [district-densification](../../openspec/specs/district-densification/spec.md), [street-frontage](../../openspec/specs/street-frontage/spec.md), [street-room](../../openspec/specs/street-room/spec.md), [lived-in-tissue](../../openspec/specs/lived-in-tissue/spec.md), [cultivation-street-life](../../openspec/specs/cultivation-street-life/spec.md), [settlement-group](../../openspec/specs/settlement-group/spec.md), [vertical-landmark](../../openspec/specs/vertical-landmark/spec.md), [cultivation-variant-differentiation](../../openspec/specs/cultivation-variant-differentiation/spec.md)
- **Worldgen** — [sect-worldgen-structure](../../openspec/specs/sect-worldgen-structure/spec.md), [sect-mountain-derivation](../../openspec/specs/sect-mountain-derivation/spec.md), [cultivation-mountain-siting](../../openspec/specs/cultivation-mountain-siting/spec.md) · see-also [07_neoforge_worldgen.md](07_neoforge_worldgen.md)
- **Pipeline / integration** — [blueprint-v1](../../openspec/specs/blueprint-v1/spec.md), [structure-json-dsl](../../openspec/specs/structure-json-dsl/spec.md), [resource-export](../../openspec/specs/resource-export/spec.md), [validation](../../openspec/specs/validation/spec.md), [interactive-preview](../../openspec/specs/interactive-preview/spec.md), [modset-profile](../../openspec/specs/modset-profile/spec.md), [mod-block-catalog](../../openspec/specs/mod-block-catalog/spec.md), [mod-decor-motif](../../openspec/specs/mod-decor-motif/spec.md), [runtime-mod-fallback](../../openspec/specs/runtime-mod-fallback/spec.md), [inscription-image-library](../../openspec/specs/inscription-image-library/spec.md) · see-also [02_blueprint_schema.md](02_blueprint_schema.md), [09_validation_checklist.md](09_validation_checklist.md)
- **Governance** — [spec-baseline-governance](../../openspec/specs/spec-baseline-governance/spec.md); `docs-knowledge-base` (added by change `add-docs-kb-governance`; synced into `openspec/specs/` on archive)

## Adding documentation

New factual technical notes go under `docs/ai-kb/`, are listed in this map, and gain a see-also link to any same-topic spec — all in the same change. Shared rules (e.g. the version-bump rule) live in one authoritative place and are referenced, not restated. See the `docs-knowledge-base` capability spec (change `add-docs-kb-governance`).
