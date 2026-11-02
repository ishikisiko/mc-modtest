# 地域行尸与骸弓（0.108.0）

两种通用款与八洲各两种特色款，共 18 个独立实体 ID。原版僵尸、骷髅继续保留。普通怪物不带首领血条和阶段锁血；外观保持原版硬边比例与连续主体，通过贴图表达衣着、骨色和环境侵蚀。

## 遭遇与应对

行尸有拖步二连抓：先横抓，停半拍，再前攫。通用特色招为伏身扑攫，起手锁定方向，短距扑出，扑空后撑地收招。命中只附加短暂迟滞，不锁住玩家操作。

骸弓停步蓄射，放箭前锁定瞄准位置；射完放低弓整顿。近身时使用一次弓扫，有明确前摇、推开与收招，不靠无限后退拉开距离。各洲第一招采用不同箭型，弓扫共用。

| 地区 | 行尸 ID、特色招 | 骸弓 ID、特色箭 |
|---|---|---|
| 通用 | `wandering_corpse` 游荡行尸：伏身扑攫 | `bone_archer` 枯骨弓手：定步重箭 |
| 西漠 | `ximo_corpse` 沙埋行尸：扇面扬沙扫臂 | `ximo_bone_archer` 风蚀骸弓：三箭扇形散射 |
| 东海 | `donghai_corpse` 溺潮尸：合臂拍潮 | `donghai_bone_archer` 盐蚀骸弓：落点单次环浪 |
| 南疆 | `nanjiang_corpse` 蛊斑行尸：短程吐瘴 | `nanjiang_bone_archer` 蛊纹枯骨：短时蛊区 |
| 北原 | `beiyuan_corpse` 霜僵：锁向滑扑 | `beiyuan_bone_archer` 冻骨弓手：快慢双箭 |
| 东荒 | `donghuang_corpse` 荒林尸：伏地扫踝 | `donghuang_bone_archer` 藤纹骸弓：弧线曲射 |
| 灵岳 | `lingyue_corpse` 青岚行尸：窄气浪 | `lingyue_bone_archer` 云纹骸弓：贯穿实体的窄箭，墙体阻挡 |
| 魔域 | `moyu_corpse` 血煞尸：重抓命中少量回血 | `moyu_bone_archer` 乌骨弓手：落点先预警再爆发一次 |
| 中州 | `zhongzhou_corpse` 古道尸卒：旧兵器横扫 | `zhongzhou_bone_archer` 遗迹骸弓：短促三连点射 |

完整命名空间均为 `myvillage:`；对应生成蛋为 `<id>_spawn_egg`。创造物品栏可搜索中文名称。`/myvillage beast status <目标>` 读取当前动作与时点；强制动作命令仅作诊断，不代替普通 AI 或真实玩家输入。

## 生成、境界与掉落

每款声明群系 tag、biome modifier 和 Java placement。通用款跨适宜生境出现，特色款额外检查实际地区 profile；沿用主世界夜间怪物光照、难度与脚下生成条件。数量权重由 catalogue 决定；自然密度和长期平衡仍待玩家观察。

自然出生从 `WildernessRules` 的地区范围抽取一次境界，经 `BeastBirth` 保存。外层世界使用实际 outer region ID 与 profile 的境界范围；搬迁或保存重载不重新抽取。命令和生成蛋保留诊断基准属性，不伪称自然出生。没有命簿人物或独立 NPC 持久身份。

掉落仅沿用腐肉、骨头与原版抢夺增量，注册原版亡灵相关标签；本批不增加炼丹、炼器材料或配方。声音明确复用原版僵尸／骷髅事件及既有字幕，不宣称新录音。

## 单一数据源与资源归属

- `tools/undead_roster/catalogue.json` 拥有 18 款身份、属性、尺寸、攻击时点、伤害参数、粒子与数量上限、出生条件、配色、中文招式名和应对说明。
- `python3 -m tools.gen_undead_roster [--check]` 导出运行规则、beast 数据/index、中文名称、蛋模型、掉落、标签、生成配对与指南。现有英文翻译保持原样，新内容以中文为准。
- `tools/beastgen/undead.py` 拥有共用模型、UV、原生像素细节和动画；`python3 -m tools.beastgen build <id> [--check]` 导出模型、动画、基础贴图和渲染器要求的透明发光层。UV truth/mask 由生成器导出，不手改。
- `art/entities/undead_roster/{corpse,archer}/material_tones.png` 为内置 imagegen 候选经填充 UV mask 回贴、最近邻归一化到 64×32、32 色最终化后的素材；仅用于有界材质明暗，脸部、眼口、衣纹和地域标记由 native painter 决定。provenance 保存候选与素材 SHA。

动画支点在腰部，前倾时躯干与髋部保持连接。伤害时点与同源关键帧一致；两次抓击和多箭分别结算，原版受击间隔继续生效。服务器决定命中、状态和结果；没有新增玩家输入包。真实箭矢复用原版移动、重力、实体／方块碰撞和遮挡，伤害经 `StatsRuntime` 进入原版减免一次。潮浪／蛊区／爆破箭只在落点结算，不额外叠加一份直击伤害。

发射物、区域、目标数、寿命与更新频率均有配置上限；不破坏或放置世界方块，不强制加载攻击落点。重载安全取消临时动作、区域与箭矢，恢复出生身份和生命状态。

## 检查与证据

本地轻量入口：`python3 -m tools.check_undead_roster`、`python3 -m unittest tools.tests.test_undead_roster`，解释器需要 Pillow 与 jsonschema。定向 Java 检查包括 `UndeadRulesTest`、`UndeadAssetsTest` 及既有 beast／wilderness 直接消费者。

`-PundeadEvidence` 只向独立服务端加入 `src/undeadEvidence/java` 探针，禁止带该开关打包。探针在隔离平地使用固定、不行动的铁傀儡目标，让 18 款小怪通过普通 AI 选择并执行攻击，记录伤害与起手，另检查近身攻击、墙体挡箭及真实 FinalizeSpawnEvent 的出生／拒绝分支，再保存／重启读取出生身份、生命和取消后的动作。目标与境界由管理员夹具准备；不是玩家映射输入、自然刷怪密度或实战手感证明。

离线预览由 `python3 -m tools.undead_roster.preview` 读取真实模型、贴图与招式配置生成，公开资源使用 HTTPS。人工外观状态为 `human_review_pending`；玩家手感、音效质量、FPS、自然密度、长期平衡和多人实战为 `not_verified`。实际构建与探针结果见本次交付记录。
