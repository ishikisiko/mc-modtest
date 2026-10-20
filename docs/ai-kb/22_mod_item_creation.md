# Mod item creation

Follow [WORKFLOW](../WORKFLOW.md) and [asset contracts](../contracts/assets.md#mod-item-pipeline).
An item must close registration, creative visibility, Chinese names, model/texture,
recipe/tag and behavior boundaries as applicable. Generated resources are edited at
their generator. Actual new mod content needs a matching jar and usage instructions;
choose focused checks by impact, with separate owner visual/gameplay verdicts.

Use `tools/validate_mod_items.py` for item relationships and the relevant generator
`--check` for authored assets. Existing `genops/contracts/items/` are machine inputs
where consumed by validators; keep them current. Ordinary items need no new process
manifest or mandatory subagent. `genops/pipelines/mod-item.full.yaml` remains an
optional complete pipeline when GenOps is explicitly selected.

0.68.0 的[月洞水院构件](../features/reference-courtyard-minecraft.md)使用独立生成器
`tools/reference_courtyard/minecraft.py`：十类统一入口、稳定款式索引、整体多格放置与根节点掉落、
专用创造栏及独立结构模板。目录注册由 `CourtyardContent` 完成，物品校验器读取同一生成目录。
