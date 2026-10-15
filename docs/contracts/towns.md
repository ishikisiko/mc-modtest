# 城镇规划与实现

<a id="civic-archetype-family"></a>
<a id="civic-precinct-framing"></a>
<a id="town-plan"></a>
<a id="town-districts"></a>
<a id="town-realization"></a>
<a id="town-block-variety"></a>
<a id="district-densification"></a>
<a id="street-frontage"></a>
<a id="street-room"></a>
<a id="lived-in-tissue"></a>
<a id="cultivation-street-life"></a>
<a id="courtyard-street-town"></a>

- `groups.py` 的 district brief 提供密度、层数、材质和 roster；规划器不按风格前缀或区名在 pass 中硬分支。软需求不被暗中升级为精确数量求解。
- 外围为单一闭环，门落在边界且有可容纳门体的直线段。基础形态及 barbican/bastion 等修饰由稳定 seed/hash 选择，不全局重抽。
- 街道网格保持既有正交约束；主街连接城门与核心，恰有一个最高等级主地标。核心祭祀轴以广场、牌坊、灯列抵达祠庙，不把整座城压成一条宗门中轴。
- district 的 cells 是占地真值，bounds 只为包围盒；非道路/保留空地按区唯一归属且不重叠。外缘区可裁切至城墙内，civic_core 保留矩形以服务围场规划。
- market/residential 的建筑沿临街边拼接，共用山墙而非居中摆在孤立地基上；变体宽度决定分段宽度，旋转/镜像仍保持门面连续。窄余地成为有类型的巷道，不是无意义草缝。
- 深地块可有后排附属体块，但前后需留通路。保留负空间可放井、种植、座椅等生活细节，不能堵街/巷/门；道具使用地面引用，不用建筑完成后的屋顶 heightmap 当地面。
- 主街比支巷宽、铺装等级高，活跃门面与陈设密度有主次。修仙城镇使用自己的摊位、幌子、药圃和阵纹词汇；外部装饰仍经合法槽与 fallback。
- civic_core 保留前导仪门、神道、广场、侧殿/柱廊与有侧门的围墙；附属体量不超过主地标，主街可一路走通。
- 静态 courtyard_street_block 是街区填充样本，不是完整运行时城镇。`/myvillage town` 与 `townat` 才实现有分区的完整城镇；不得恢复旧草案对 group.layout 的早期绑定。
- 现场建城先取得所需 chunk tickets，结束释放；不静默跳过加载失败。地形适配有坡度边界，支撑贯通到地面，不强行抹平或留下悬空一格。
- 模板放置先解决缺失外部方块，不让 registry 默认空气吞掉构造。固定 seed/site 输出可复现；外围闭合、道路净空、地块可达、临街连续和核心天际线是结构检查目标。
- 当前城镇主动命令建造不等于自然 worldgen；居民、宗门命簿和其他系统的实际支持范围按 KB40/43 说明，不能从旧“possible NPC”描述推断全部未实现或全部实现。

实现入口：[城形](../ai-kb/11_town_shape_irregularity.md)、[城形词汇](../ai-kb/12_town_shape_vocabulary.md)、[公建](../ai-kb/10_civic_family.md)、`tools/buildgen/town_plan.py` 及运行时 town 代码。尺寸和 distinctness 阈值从代码/校准数据读取。
