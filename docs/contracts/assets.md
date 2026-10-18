# 数据格式、物品与资源

<a id="blueprint-v1"></a>
<a id="structure-json-dsl"></a>
<a id="mod-block-catalog"></a>
<a id="modset-profile"></a>
<a id="runtime-mod-fallback"></a>
<a id="orientation-adapter"></a>
<a id="resource-export"></a>
<a id="mod-decor-block-family"></a>
<a id="mod-decor-motif"></a>
<a id="plaque-block-family"></a>
<a id="inscription-image-library"></a>
<a id="spirit-stone-resources"></a>
<a id="guideme-cultivation-guide"></a>
<a id="mod-item-pipeline"></a>

本文件保留跨模块的兼容性约束；具体字段、数值和资源清单以当前 schema、生成器与注册表为准。检查范围按 [工作流程](../WORKFLOW.md) 选择。

## 输入和导出

- 当前命名空间是 `myvillage`；历史 `minecraft_town_mod` 不代表另一套运行时资源。
- Blueprint v1 与 Structure JSON 是两种输入。前者包含版本、namespaced id、正尺寸、局部 palette、整数坐标和字符串 state；占位 `export_nbt.py`/`export_schem.py` 不能冒充已实现导出器。
- Structure JSON 面向 Minecraft 1.21.1：有界正尺寸，确定性解析 palette/blockstate，支持 set、fill、轴对齐 line 和旧 blocks；只有显式 `fill_air` 才补空气。元数据尺寸须匹配；道路需至少两个连接锚点。
- 实际 NBT 导出为 gzip Java structure template，包含 DataVersion、author、size、palette、blocks、entities；生成网格先归一化到最小坐标零。模板导出不应被描述为通用实体、战利品或任意复杂 BlockEntity 导出器；模组已有独立世界生成与实体系统。
- 生成库与 gallery/single-place 函数同源。`placeat`/`galleryat`/`townat`/`sectat` 复用玩家命令的放置和回退逻辑；生成结构的地表对齐偏移不能因入口不同而改变。命令以 README 为准。

## 调色板、模组与朝向

- `tools/buildgen/modset.py` 和 `exmod/mod_block_catalog.json` 决定模组合法性；禁止另写 namespace 白名单。区分未启用 namespace 与启用 namespace 中未知 block id。
- 外部模组槽按可用 namespace 过滤，必需槽保留末尾原版 fallback；缺少可选槽就省略对应装饰。`myvillage:` 自有资源在 vanilla/full 模式均合法。
- 运行时先查真实 registry：已注册方块及属性原样保留；缺失外部方块使用确定性、非空气的原版替代。回退表由槽数据生成，冲突使用稳定优先级，不在 Java 复制表格。
- 朝向由注册的方块族语法解释；未知族明确失败，不能猜测原版楼梯属性。屋顶和 motif 经注册表与风格 allow-list 分派。

## 物品闭合与生成归属

- 新增物品需明确 id、注册类型、创造栏、双语名称、模型/贴图、可选 recipe/tag、行为归属和获取方式；这些事实可以直接在代码/数据中表达，不要求独立任务合同或流程审批。
- 功能物品的耗材、冷却、命中、掉落与持久化由服务器决定。注册存在不等于模型/配方/资源完整。
- 生成资源只改 authored inputs 后再生成；生成器的 `--check` 用于被修改的资源族。准确列出的 jar 内容检查只在需要交付/检查 jar 时执行。
- 灵石矿工具等级、Silk Touch/Fortune、掉落和 Overworld 分层以资源 JSON 为准。普通灵石保持普通物品；修炼、炼丹等消费者拥有自己的扣除事务。不要凭空扩展为货币、冶炼或高阶石。
- 炼器矿材由 `tools/forging/acquisition.py` 生成资源；自然矿脉/砂层的参数来自 `forging/acquisition.json`。玄铁使用原 registry ID，天然获取只在新生成区块接入，采集与精准采集不能造成材料重复掉落。
- 两块入门石碑分别注册、分别交互，不拥有玩家档案的 BlockEntity；获取方法与自然生成状态按当前实现说明。
- 经架是上下两格、下格唯一归属根；两格交互均回到该根。拆除最多掉落一次，修复不覆盖障碍，也不丢弃仍有合法归属的旧下格。

## 灵石公共标签

- `c:ores/spirit_stone` 同时提供 block/item 标签，包含普通与深层原矿，并挂入 `c:ores`。`c:gems/spirit_stone`、`c:gems` 和 `c:spirit_stones` 仅作分类。
- `c:spirit_stones/low_grade` 是明确的一枚抵一枚下品灵石入口，默认只含 `myvillage:low_grade_spirit_stone`。数据包可追加同级外部物品；不根据名称、namespace 或宽泛 gems 标签推断等价性，不自动接入未知模组。
- 修炼、宗门供奉、丹炉燃料/补料、炼器/维修和灵石合成原料消费此分级标签；产物/掉落仍是原 registry ID。修炼面板采用相同标签；混合库存扣料失败须恢复原堆叠及组件。
- 丹炉保留单燃料槽，不合并不同物品/组件的堆叠；补料受实际堆叠上限限制。交易货币仍由 commerce rules 的具体物品决定，不开放标签兑换。
- 新公共标签为 authored JSON；已有 `c:ores` 汇总由 `tools/forging/acquisition.py` 生成。配方仍由各自生成器维护，不手改生成结果。

外部兼容数据包在 `data/c/tags/item/spirit_stones/low_grade.json` 追加物品（将示例 ID 换成实际物品）：

```json
{"replace": false, "values": [{"id": "othermod:low_grade_spirit_stone", "required": false}]}
```

服务器重载后，标签消费端重新读取当前成员；客户端面板使用同步后的标签。不要把中品/上品灵石加入此一比一支付入口。


## 灵石袋

- 灵石袋持久余额归原物品 `myvillage:spirit_stone_balance` 组件，按精确物品/组件身份聚合并用 BigInteger 记数，无固定容量上限；不额外复制死亡数据。旧 container 幂等迁移，不丢弃失去标签资格的物品。袋子不嵌套，缺款/满背包失败不丢失或重复物品。
- 袋和散装灵石由 `SpiritStoneSources` / `SpiritStoneStorage` 统一暴露。修炼、供奉、炼器、丹炉补料和交易使用其原有服务器事务；交易继续核对具体普通货币，不能按宽泛标签兑换。
- 自动入袋仅服务器执行；额外装备/饰品来源必须提供稳定、去重且可原子替换的槽位，普通产物不能插入这些位置。未接入饰品模组时只承诺物品栏/副手。
- 扫描间隔以 `spirit_stones/bag.json`、品级整数量级以 `spirit_stones/denominations.json` 为唯一来源；当前仅下品，不自动兑换，资源归 `tools/gen_spirit_stone_bag.py`；用法和扩展接口见 [KB59](../ai-kb/59_spirit_stone_bag.md)。

## 装饰块、匾额与书籍

- 装饰类别各有 block id、变体和碰撞表，不以一个 `family` 属性混合不相干类别。保留 variant/facing/moss，以及需要时的 waterlogged；手放 BlockItem 支持 pick-block。
- 模型与碰撞由同一离线来源生成，合并 AABB 控制复杂度；外观细节不自动成为碰撞体。
- 匾额保留横/竖、壁挂/悬挂四种 id；multipart 的左右顺序是外侧观察顺序，不是递增世界坐标。悬挂链是装饰，不作为存活支撑条件。
- frame 与 inscription 是独立目录；binding 校验方向和尺寸桶。整幅题字按完整画布烘焙到共享整牌纹理，各分块用 UV 裁切，不能拆成逐字低清贴图或用 painting 实体替代当前匾额。
- GuideME 通过 Maven 依赖；根 `guidebook/` 是唯一 authored Markdown，构建单向打包。使用绑定组件显示按键，保持 G 不被 MyVillage 默认占用；不自动迁移玩家键位。手册调用 GuideME API，不新增开书网络协议；21.1.17 的 custom_colors 限制见 KB31。

入口：[蓝图](../ai-kb/02_blueprint_schema.md)、[DSL](../ai-kb/05_vanilla_structure_nbt.md)、[物品](../ai-kb/22_mod_item_creation.md)、[GuideME](../ai-kb/31_guideme_cultivation_guide.md)。
