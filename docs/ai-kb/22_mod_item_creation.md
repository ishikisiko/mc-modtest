# Mod item creation

Follow [WORKFLOW](../WORKFLOW.md) and [asset contracts](../contracts/assets.md#mod-item-pipeline).
An item must close registration, creative visibility, Chinese names, model/texture,
recipe/tag and behavior boundaries as applicable. Generated resources are edited at
their generator. Actual new mod content needs a matching jar and usage instructions;
choose focused checks by impact, with separate owner visual/gameplay verdicts.

独立多格建筑构件示例：[魔契柱](../features/demon-pact-pillar.md)，由 `DemonPactPillarContent` 注册独立方块与物品，专用生成器同步整体纹理、逐格模型与实际碰撞。

[七府独立样板](../features/seven-lord-manors.md)由 `tools/lord_manors/generate.py` 维护整座构造、专用材质与雕构状态；`ManorContent` 注册营造材质，雕构中键取样保留造型，独立样板不绑定城市。
精雕的重叠面裁切归 `tools/lord_manors/surfaces.py`，`python3 -m tools.lord_manors.check_models` 独立检查共面与实心外壳；面裁切不改 profile 或碰撞身份。默认城市接入见[七府默认城市](../features/default-manor-capitals.md)。
部件显示名与材质说明由 `blueprints/lord_manors/presentation.json` 经同一生成器输出，`ManorPresentation` 供物品与可选 Jade 适配共用；用途标签不进入造型身份。Jade 兼容叶位于 `compat/jade/` 与 `client/manor/ManorJadeProvider`，公共方块、物品不导入 Jade 或客户端 API；归属说明由当前世界的三维施工位置决定。

独立装饰样品：[剑意灵台](../features/sword-qi-decoration.md)；资源归
`tools/gen_sword_qi_decoration.py`，服务器方块状态控制开关，客户端只负责剑气表现。

0.103.1 的[符灯与晒布架](../features/street-furniture.md)由
`blueprints/street_furniture/catalog.json` 和 `tools/street_furniture/generate.py` 维护。
两类设施各自注册方块及物品，多格占用、碰撞与模型同源，根格拥有唯一掉落；旧城市保持原样。

武器／防具制作先看 [装备制作源](../../equipment/README.md) 与
[装备制作基础](70_equipment_authoring.md)。防具可从目录草稿起步，复用挂接、UV、图标、注册与试衣间；
已有武器继续由原专用生成器拥有外形与动作。不要把方块模型的坐标／旋转限制直接套到穿戴模型上。

Use `tools/validate_mod_items.py` for item relationships and the relevant generator
`--check` for authored assets. Existing `genops/contracts/items/` are machine inputs
where consumed by validators; keep them current. Ordinary items need no new process
manifest or mandatory subagent. `genops/pipelines/mod-item.full.yaml` remains an
optional complete pipeline when GenOps is explicitly selected.

0.68.0 的[月洞水院构件](../features/reference-courtyard-minecraft.md)使用独立生成器
`tools/reference_courtyard/minecraft.py`：十类统一入口、稳定款式索引、整体多格放置与根节点掉落、
专用创造栏及独立结构模板。目录注册由 `CourtyardContent` 完成，物品校验器读取同一生成目录。
