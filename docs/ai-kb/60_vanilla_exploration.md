# 原版探索与城镇向导（0.65.0）

本期接通主世界／下界的建议境界、普通怪物、有限探索奖励和云山向导。原版地点允许提前进入。末地提供完整路线建议，末地怪物和末影龙、凋灵、监守者、远古守卫者不在本期独立调平范围。

## 配置与解释

`data/myvillage/exploration/rules.json` 是探索数值和进度规则的唯一来源；`ExplorationRules` 严格读取字段、整数范围、顺序、境界、池引用与重复键。启动后固定，`/reload` 不承诺重载这些规则。所有原版群系有维度默认建议，常见危险群系再细分；当前原版结构注册表的每个 ID 都映射到一个地点。

- `locations`：维度、结构／群系选择、Y 范围、优先级、建议境界、普通怪物范围、洲域参与开关和基础奖励池。`context_only` 记录刷怪房、袭击和不祥试炼等显式场景，不会误匹配整片主世界。
- `special_births`：巡逻、事件、刷怪笼和试炼出生的场景。真实 `TrialSpawner` 的 owner 与不祥状态区分普通和不祥档，不按玩家境界调整。
- `pools/reward_tiers/loot_tables`：有限战利品追加；升级档次按地点危险，不能按开箱者境界抽更好的物品。
- `advancements/milestones`：原版进度 flag、每一步的最低境界及相关 tips。缺少觉醒或基础吐纳时优先指向入门；已提前取得原版进度的玩家不会重复被要求完成它。
- `guide_station/settings`：向导住处／接待区和有界查询、提示间隔、交谈距离等。

`ExplorationRuntime` 在已有区块中解析结构引用和已加载 start 的真实包围盒，再按明确优先级选择地点。它不为了查询生成区块、强制加载结构起点或申请区块票；跨大结构边缘且起点不在已加载范围时，可能暂时只有群系／地区建议。结构匹配使用完整三维位置，地下结构不会仅因同一 X/Z 就覆盖地表。

主世界把结构范围的两个端点分别与当地已有 beast 范围取较高值，结果只执行一次采样。结构不能降低高阶洲域难度，也不将两个境界倍率相乘。下界使用自己的配置，末地的 encounter 为空。无洲域的主世界默认地表仍保留无分配语义，但显式洞穴／结构／出生场景可使用已声明的范围。

## 怪物与存档

`VanillaMonsterRealms` 消费上述查询；其他出生资格、特殊实体排除和非属性伤害源仍归 `wilderness/vanilla_monsters.json`。先记录出生，再在原版初始化完成后赋属性。出生记录新增 dimension/location 诊断字段，不要求旧记录补齐。旧个体、跨区、重载和转化／分裂保留原来的境界，不补抽、不回血；本期不迁移已保存的无分配怪物。

共享属性、护甲和非属性伤害执行路径保持 [KB47](47_realm_stats_wilderness.md) 的一次缩放与一次原版减免语义。

## 奖励与原版材料

`ExplorationLoot` 是注册的 NeoForge global loot modifier，只追加配置中的精确箱子和宝库根表，保留原版物品。不会匹配实体掉落、村民交易、试炼刷怪笼重复结算或宝库的内部子表。原版箱子的一次展开、宝库的原生玩家领取状态继续拥有次数；不另外建立领奖副本，不回填已开箱。

奖励复用六味可种植药材、灵石、玄铁／赤铜／淬锋砂、丹方、器谱及带正确 technique 组件的秘籍。启动时校验物品和秘籍类别／品级，避免空白秘籍。高阶主世界地点提高奖励池，下界保留独立档次；本期没有新增末地奖励。

`tools/gen_forging_assets.py` 拥有 `chitong_from_blaze` 配方：铜锭标签、烈焰粉和下品灵石标签合成一块既有赤铜锭。`tools/forging/materials.json` 拥有对应来源提示。原灌灵路线继续可用；没有改写现有炼器事务或品质。

## 城镇向导

`LifeEngine` 增加 `guide` 职业；`WorldSim.staffTownGuide/townGuide` 是唯一写入／查询入口。`NpcLifeSites` 在观察实际住处与接待区后，从本地既有空闲散修中确定性选任，默认使用云山 `guest_west` 与 `inn_social`。旧已绑定且有对应真实设施的场所也可安排向导；没有人物、容量不足或设施不可用时保持空缺。

白天沿实际寻路前往接待区并执行 social，夜间回住处；向导活动不制造商店工资或商品。身体仍由 `WorldSimAvatars` 唯一管理，复用现有外观、战斗与生活租约。名字显示“城镇向导”。右键交谈沿用近距、视线、活体和 epoch 检查，再从当前玩家权威状态生成下一步和轮换 tips。两位玩家看到同一身体，但各自得到自己的进度指引；客户端不能提交“已完成”或生成奖励。

`LifeState.guideAssignments` 保存场所到人物 ID 的关系，人物解除岗位或死亡后保留空缺标记，自动调度不会制造替代身份。命簿 payload **7**，继续读取 v1–v6；旧 life 中没有该字段时使用空表。对话冷却、tips 游标和观察身体均不作为新的长期人物状态。

## 玩家入口与指南

普通生存玩家可用：

```mcfunction
/exploration
/exploration guide
/exploration guide tips
/exploration guide where
/exploration catalogue
```

管理员可用 `/myvillage exploration` 同名别名与 `info` 诊断；原 `/myvillage` 根权限不放宽。地点建议在实际切换且满足冷却后发送两条聊天信息，反复跨边界不刷屏。

`tools/gen_exploration_guide.py` 从同一规则及中英语言输入生成两份 GuideME exploration 页面；`processResources` 自动生成，手册首页链接它。不要手改生成页。

## 检查入口与边界

本地：`python3 tools/validate_exploration.py`、两个相关生成器 `--check`、`tools.tests.test_exploration/test_forging/test_modal_runner`。Modal 的 `exploration-build` 选择规则、境界、命簿生活／存档／商业和炼器相关 Java 检查，并打包 jar。

`exploration-native` 增加实际 V4 结构寻找、原生出生和箱子／下界怪物／向导保存重启。它是专用服务器 fixture，不是映射玩家操作。`exploration-runtime` 使用实际客户端映射右键向导、聊天查询、箱子取物、下界材料合成与导灵炼器、共用剑招命中、双客户端与真实重启。开发者检查不能确认玩家手感、长期平衡、声音、FPS、所有种子自然密度或广泛多人体验；这些仍为 `not_verified`。
