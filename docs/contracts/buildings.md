# 建筑生成

<a id="building-generation"></a>
<a id="building-orientation-variants"></a>
<a id="multi-story-massing"></a>
<a id="style-profile"></a>
<a id="form-registry"></a>
<a id="chinese-vernacular-roof-vocabulary"></a>
<a id="cultivation-form-vocabulary"></a>
<a id="cultivation-massing-grammar"></a>
<a id="cultivation-variant-differentiation"></a>
<a id="vertical-landmark"></a>
<a id="settlement-group"></a>
<a id="ganlan-stilted-house"></a>

## 层次和数据归属

- 生成层次为 group → style → archetype/tier → MassingGraph → facade → 注册 form/ops → pass/protection → 检查 → 导出。新家族从 `tools/buildgen/groups.py` 添加，不能按 style 名称前缀猜家族。
- archetype 先生成语义体块，不能把整个形体退化为直接堆方块。group 绑定 roster、style 和 layout；soft brief 是指导而非偷偷增加的硬约束。
- 风格槽负责主材、允许的屋顶/motif、比例与禁用材料；不要在 Java、pass 或 validator 中复制调色板。修仙城镇的凡俗用材与宗门灵材是不同风格约束。
- 形体变体由稳定变体表决定；同 seed 可重现。材质噪声只能修饰变体，不能取代体量、层数、屋顶、附属体块等真实差异。阈值由现有 distinctness 测试/数据维护。

## 几何与 pass

- 体块坐标保持既有约定：低 z 为前，高 z 为后，x 向东，y=0 为基础底部。门墙 facing 可变，但不能借此旋转屋顶轴或 footprint。
- `PROTECTED` 不被正常后续写入覆盖；其他优先级不能代替冲突检测。家具、烟囱、连通口不得写入另一体块的外墙平面。
- 现有 pass 次序由 `tools/buildgen/passes.py` 等实现维护：夹层先于普通楼板/楼梯，立面先于屋顶清理，材质变化不破坏保护格。夹层由自己的 pass 写入，普通楼板跳过该层。
- 封闭外墙在基础至屋顶之间连续，只有计划过的门窗/连接口可开洞；窗避开角柱、门洞和被附属体块遮挡的区间。山墙材料来自风格，不硬编码深色屋面木板。
- 多层楼板、预留楼梯井与开口必须贯通且避开门窗。楼阁保留可达第二层；附塔独立升高、独立屋顶，belfry 才放置钟标记。
- entry-detail 与牌坊通过 plaque bindings 放匾额；无 binding 才使用普通 SIGNAGE 处理。

## 建筑族形态

- 环境树使用原版或新生魔艺已有的完整树种，保留真实原木、树叶、树苗和种植土；不得把树干、树冠做成只有外观的雕构方块。院内空间不足时种真实树苗，按原版净空规则生长。
- 新增自有树种须同时完成树苗种植、自然生长与催长、整树空间检查、砍伐掉落、树叶连接/消退、树苗掉落和再种植，以及对应木材物品、资源与必要标签；未完成生长链不能作为新树接入建筑。

- 民居、商铺、大宅、公建与修仙建筑保持独立功能身份。酒馆有高大厅、局部夹层和住宿；领主宅有议事标记、纹章和高于主体的附塔。
- 修仙建筑使用台基、檐廊、层叠/收分楼塔、三开间山门、仪式丹炉等语汇，不复用西式小屋体块或塞入烟囱、柴堆、木篱院等默认装饰。
- 四种民居屋顶 `chinese_flush_gable`、`chinese_overhang_gable`、`chinese_half_hip`、`chinese_round_ridge` 与宗门 monumental 屋顶分别注册；允许共享几何 helper，不能用字符串分支偷偷互换。
- 飞檐应有真实楼梯/台阶曲线、中段低与角部抬升；檐廊是带顶、柱列和步行空间的构造，不是地面路径。可选斗拱、脊饰缺槽时省略。
- 干栏样本保持架空生活层、可见支柱、开放底层、可达入口、外廊和深檐。徽派样本保持天井、内向围合和马头墙的独立语言，不能冒称整个村落/生物群系已实现。
- 参考图和来源结构用于研究/提炼语法；研究许可、采用决定、分发权是不同事实，不把第三方 NBT 或世界数据直接作为原创输出。
- 遗迹的坍塌是显式建筑体块变更，仍保留可行走通路与交互目标；不能用随机孔洞代替形态设计。丹霞旧观经 `danxia_ruin` group 和专用生成器导出，屋顶继续使用注册处理器。

实现入口：[建筑词汇](../ai-kb/10_civic_family.md)、[修仙变体](../ai-kb/24_pagoda_landmark_rebuild.md)、[徽派](../ai-kb/21_huipai_reference_slice.md)、[干栏](../ai-kb/23_ganlan_reference_slice.md)。具体比例和已接受样本以生成器、风格 JSON、回归基线和相关 KB 为准。
