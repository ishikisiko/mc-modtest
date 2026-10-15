# Mod item creation

Follow [WORKFLOW](../WORKFLOW.md) and [asset contracts](../contracts/assets.md#mod-item-pipeline).
An item must close registration, creative visibility, both languages, model/texture,
recipe/tag and behavior boundaries as applicable. Generated resources are edited at
their generator. Actual new mod content needs a matching jar and usage instructions;
choose focused checks by impact, with separate owner visual/gameplay verdicts.

Use `tools/validate_mod_items.py` for item relationships and the relevant generator
`--check` for authored assets. Existing `genops/contracts/items/` are machine inputs
where consumed by validators; keep them current. Ordinary items need no new process
manifest or mandatory subagent. `genops/pipelines/mod-item.full.yaml` remains an
optional complete pipeline when GenOps is explicitly selected.
