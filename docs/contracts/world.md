# 区域、宗门、命簿与境界数值

<a id="region-profile"></a>
<a id="region-topology"></a>
<a id="region-runtime-binding"></a>
<a id="sect-worldgen-structure"></a>
<a id="sect-mountain-derivation"></a>
<a id="cultivation-mountain-siting"></a>
<a id="sect-compound-layout"></a>
<a id="sect-compound-realization"></a>
<a id="realm-combat-stats"></a>
<a id="regional-wilderness"></a>

## 区域图与宗门世界生成

- 区域配置与拓扑规则只在 `data/myvillage/worldgen/` 的 JSON 中维护；Python/Java 生成须同 seed 同结果，覆盖并列等级与 walled 区域的 parity 样本。
- 图构造保持唯一中心 anchor、向外等级阶梯、非封闭区域的可通行生成树及 连/隔 类型；封闭区域最多一个关隘。数量、阶差和范围从当前规则取，不把旧文档数字复制进新代码。
- 运行时区域层是被动查询和一次性出生点绑定；不因此接管地形或生物群系。出生点选最低阶可接纳区域并做有界安全表面搜索；region_at 的最近中心近似和有界范围要明确。
- `next_rung_regions` 在等级并列时返回集合，顶阶返回空集合；不要自行选择一条未定义阵营分支。
- 宗门使用确定性台地与中轴，等级随台地上升，侧翼避开轴道与楼梯。模板实际尺寸决定放置，不能用对称假设吞掉通路。
- 台地、山体、建筑共享绝对高度；山体由台地骨架派生，核心不加噪声，外围与自然地形渐变。填实支撑、清理净空，楼梯最后落地，跨峰桥两端有支承；碰撞不合法的孤峰变体明确跳过并记录。
- 世界生成按当前 chunk 裁切工作，不重复整片宗门扫描，不 force-load 邻区；命令生成与 worldgen 在同 seed/site 下接缝一致。自然宗门以生物群系和间距筛选，支持 locate。

## 命簿权威

- 命簿是世界模拟唯一权威；化身和宗门建筑是投影，通过 `WorldSim` facade 回写。纯 `sim/**` 不依赖游戏类型、时钟或随机源；随机均由 seed/day/subject/固定 purpose code 派生，不能重排 purpose 编号。
- 数值在 `data/myvillage/world_sim/`，年率按调用方 days_per_year 换算；存档版本变更显式迁移，新增文本在双语资源注册。
- 化身仅由 `WorldSimAvatars` 投射，不随 chunk 存档；人物身份唯一，有全局/局部上限和 loaded-chunk 边界。玩家不是命簿 Person，不混入 NPC members。
- 玩家入宗、离宗、晋升、资质变更、任务、借阅、战争和灭宗走各自 facade API。交谈/经架输入只携带意图与必要定位，服务器复查距离、维度、身份、门派与权限。
- GateRealizer 有界分片建造，ScriptureShelves 拥有经架放置；借阅记录与经架下格 Sect 根一致，不给匿名手放经架自动赋门派。
- 客户端天下页只读有上限的查询快照，保持节流；不建立第二份客户端命簿。新视图扩展已有查询/section，而非另加任意数据通道。

## 境界伤害与野外投影

- 境界成长和玩家 stage aliases 来自 `stats/rules.json`；覆盖已实现的金丹后期链。服务器属性修饰符 ID 稳定且刷新幂等，不叠乘、不借刷新回血；加载时最大生命先于原版 Health 恢复。
- 原版护甲与附魔各执行一次，额外分类抗性也只执行一次。护甲压力仅在原版护甲公式入口使用 `D_atArmor / S_def`，所得比例仍乘完整 `D_atArmor`；非法防守者境界退回 scale=1，不能按血量猜境界。
- 原版上限、bypass、护甲损耗、盾、无敌帧增量和吸收次序不改。诊断绑定实际 damage-container 身份，区分请求伤害、护甲入口、后续减伤和真实失血。
- 物理穿透保留旧 registry id 且只用于物理通道；五元素各读自己的属性，不能复制/叠加旧物理值。非生物攻击者穿透为零；未接入的元素攻击不能因属性注册成功就宣称可玩。
- 自然妖兽的出生境界只分配一次并存档，移动/重启不重抽；旧召唤/蛋生无分配个体保留基线。区域外或其他维度不捏造高阶默认遭遇。
- 野外散修只投射本地区既有、存活、无宗门且符合范围的人物，不能另造身份；共享化身去重和容量边界。

入口：[区域](../ai-kb/13_region_topology.md)、[世界生成](../ai-kb/07_neoforge_worldgen.md)、[命簿](../ai-kb/40_world_sim.md)、[入宗](../ai-kb/43_player_sect_entry.md)、[境界与野外](../ai-kb/47_realm_stats_wilderness.md)。
