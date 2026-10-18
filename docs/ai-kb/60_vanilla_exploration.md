# 原版探索与城镇向导（0.66.0）

第一版接通主世界／下界建议、普通怪物、有限探索奖励和云山向导；第二版补齐末地普通怪物、四类特殊首领和末地后续路线。默认保留提前进入原版地点的自由；可选传送门境界限制默认关闭。数值和阶段以当前配置为准。

实际源、检查范围与失败保留见 [第二版验证记录](../testing/2026-10-18-vanilla-exploration-v2.md)；[第一版验证记录](../testing/2026-10-18-vanilla-exploration-v1.md)保留当时独立范围，不将旧证据扩写为第二版通过。

## 配置与解释

`data/myvillage/exploration/rules.json` 是探索数值和进度规则的唯一来源；`ExplorationRules` 严格读取字段、整数范围、顺序、境界、池引用与重复键。启动后固定，`/reload` 不承诺重载这些规则。所有原版群系有维度默认建议，常见危险群系再细分；当前原版结构注册表的每个 ID 都映射到一个地点。

- `locations`：维度、结构／群系选择、Y 范围、优先级、建议境界、普通怪物范围、洲域参与开关和基础奖励池。`context_only` 记录刷怪房、袭击和不祥试炼等显式场景，不会误匹配整片主世界。
- `special_births`：巡逻、事件、刷怪笼和试炼出生的场景。真实 `TrialSpawner` 的 owner 与不祥状态区分普通和不祥档，不按玩家境界调整。
- `pools/reward_tiers/loot_tables`：有限战利品追加；升级档次按地点危险，不能按开箱者境界抽更好的物品。
- `advancements/milestones`：原版进度 flag、每一步的最低境界及相关 tips。缺少觉醒或基础吐纳时优先指向入门；已提前取得原版进度的玩家不会重复被要求完成它。末地主线接续击败龙、穿过末地折跃门、进入末地城和取得鞘翅，分别读取原版真实进度。
- `side_routes`：远古守卫者／凋灵／监守者的可选提示，建议境界从 boss profile 读取；不参与 `next()` 主线，不以击杀它们锁住探索进度。
- `portal_gates`：可选原版跨维度门最低境界及服主开关的默认值；下界／末地阈值以同一规则为唯一来源。实际启用开关来自下面的 SERVER 配置，可按世界覆盖，默认关闭。
- `guide_station/settings`：向导住处／接待区和有界查询、提示间隔、交谈距离等。

`ExplorationRuntime` 在已有区块中解析结构引用和已加载 start 的真实包围盒，再按明确优先级选择地点。它不为了查询生成区块、强制加载结构起点或申请区块票；跨大结构边缘且起点不在已加载范围时，可能暂时只有群系／地区建议。结构匹配使用完整三维位置，地下结构不会仅因同一 X/Z 就覆盖地表。

主世界把结构范围的两个端点分别与当地已有 beast 范围取较高值，结果只执行一次采样。结构不能降低高阶洲域难度，也不将两个境界倍率相乘。下界与末地使用各自配置，不借用主世界洲域。无洲域的主世界默认地表仍保留无分配语义，但显式洞穴／结构／出生场景可使用已声明的范围。

末地的 `end` 默认主岛、`end_outer` 外岛群系和 `end_city` 结构分别声明普通怪物范围，均为 `regional=false`；外岛／末地城通过群系和实际结构包围盒选择，不按玩家境界升降。末影人、潜影贝仍须满足普通怪物的实体与出生原因资格；特殊首领不走这些范围。

## 可选传送门限制

`ExplorationServerConfig` 注册 NeoForge SERVER 配置，默认读写实例目录的 `config/myvillage-exploration-server.toml`。服主可在该文件设置：

```toml
[portal_gates]
enabled = true
```

只暴露这一个布尔开关；不需要改源码或重打 jar。若 `<世界目录>/serverconfig/` 中已有同名文件，该世界优先使用该覆盖文件；需要按世界设置时，可手动创建它。`defaultconfigs/` 的同名文件仅在基础配置缺失时用于初始化，不会为每个新世界自动创建覆盖文件。NeoForge 21.1 的 `ServerLifecycleHooks.handleServerAboutToStart` 将实例 `config/` 作为基础路径、世界 `serverconfig/` 作为覆盖路径传给 `ConfigTracker.loadConfigs`；`ConfigTracker.resolveBasePath` 仅在同名覆盖文件存在时选择世界路径。默认值及配置尚未加载时的 fallback 来自探索 JSON 的 `portal_gates.enabled=false`；最低境界仍只读 JSON，不在 TOML 复制阈值。

默认关闭时没有新增门槛。实际 Pre hook 读取已加载的服务器配置开关。开启后，`ExplorationRuntime.gatePortal` 仅在服务器 `PlayerTickEvent.Pre` 处理原版下界门／末地门的 `PortalProcessor`，在原生处理目的地前清除未达境界玩家的这次门处理，并按现有提示间隔发送拒绝信息。境界取玩家服务器状态，客户端不能声明达标；不会先把人传走再回弹。

返回主世界、同维度、末地折跃门、模组自定义门、命令传送和非玩家实体仍由原有机制处理。此限制只用于明确选择的原版跨维度门规则，不是所有跨维度手段的全局封锁。TOML 由 NeoForge SERVER 配置机制加载；JSON 阈值仍在启动时固定，不承诺通过 `/reload` 重载。

## 怪物与存档

`VanillaMonsterRealms` 消费上述查询；其他出生资格、特殊实体排除和非属性伤害源仍归 `wilderness/vanilla_monsters.json`。先记录出生，再在原版初始化完成后赋属性。出生记录新增 dimension/location 诊断字段，不要求旧记录补齐。旧个体、跨区、重载和转化／分裂保留原来的境界，不补抽、不回血；本期不迁移已保存的无分配怪物。

潜影贝被自身子弹命中后的原版复制不触发 `FinalizeSpawnEvent`，也不是史莱姆 `MobSplitEvent`。`ShulkerReproductionMixin` 仅在 `hitByShulkerBullet` 的实际 `addFreshEntity` 前接入，调用 `VanillaMonsterRealms.inheritShulkerReproduction`：确认同世界、类型和普通怪物资格后，只从已有有效、完成赋值且 realm／stage 一致的父体继承出生记录。新子体沿新生分裂语义初始化自身属性与满血，父体不重抽、不回血；无归属旧父体、pending／异常归属和已有其他归属的子体不补抽、不覆盖。僵尸等原有转化、史莱姆分裂仍使用原有事件和各自生命语义，不扩大到任意 `addFreshEntity`。

共享属性、护甲和非属性伤害执行路径保持 [KB47](47_realm_stats_wilderness.md) 的一次缩放与一次原版减免语义。

## 四类首领的独立接入

`data/myvillage/exploration/bosses.json` 是四类首领的独立配置，不进入普通怪物的洲域抽样。`BossRules` 严格校验且只接受远古守卫者、末影龙、凋灵、监守者：每个版本化 profile 声明 realm／stage、有效生命、可空的有效近战属性，以及原生伤害通道的 factor／maximum。只有原版真正使用攻击属性的首领设置该属性；其他攻击沿各自原生来源处理。具体数值只维护在配置中，不通过通用境界倍率再次放大原版高基数。

`BossRuntime` 在服务器首次 join 接入，覆盖自然生成、构建凋灵、复活龙和命令生成，不依赖 `FinalizeSpawnEvent`。与普通怪物的旧无分配策略不同，尚无本模块／普通怪物出生归属或其他 realm 的旧首领也会一次接入；保留当时生命／最大生命比例，不能把受伤旧首领恢复满血。永久属性修饰符随实体保存，在原版恢复 Health 前恢复最大生命；`myvillage:exploration_boss_birth` 保存版本化 profile、实体、realm／stage、位置与是否从磁盘加载，`myvillage:stats_realm` 供共享境界防御读取。后续跨区、卸载和重启不重新选择、不重新应用属性、不回血；已有或格式异常的归属不作为重新分配许可。

归属保存的是 profile ID 与 realm／stage，非整套伤害配置快照。永久属性不重算，但非属性伤害每次解析当前同 ID 的 profile；若未来删除 ID 或改变其 realm／stage，解析失败便停止首领专用伤害改写，既有属性不自动清除。同 ID 改伤害策略可能影响旧首领，不能宣称全部数值永久冻结；本版不支持在线重载该配置。

非属性通道只在原生 `LivingIncomingDamageEvent` 的同一伤害容器执行一次配置倍率和上限：远古守卫者光束／反伤，龙接触／有龙 owner 的吐息云，凋灵头颅直击／头颅爆炸／出生爆炸，监守者音爆。来源必须匹配原版 damage type 和真实直接／间接 owner；不按附近首领、最近攻击者或受害者状态猜归属。无 owner 头颅、持续凋零 DOT、通用魔法和环境伤害不冒认为首领伤害；属性近战不重复乘倍率。

原版 AI、战斗阶段、龙水晶／部位、凋灵半血护盾／回血、监守者音爆原生绕甲、盾牌与原版减伤流程继续拥有机制，未添加替代 AI 或模型。首领境界用于明确成长定位，配置是开发者初始调平；不能据此宣称真实玩家已完成平衡验收。

凋灵充能的回血只在 `WitherBoss.customServerAiStep` 原生充能 `heal` 调用点按有效最大生命／原生基础最大生命调整；不在阶段结束补满，不改普通被动回血或外部治疗。龙水晶仍是原生固定每次 +1 生命，其相对回血速度随独立最大生命变弱，长期强度由玩家试玩判断。

## 末影龙部位与共用武技

`CombatTargets` 归一部位的 owner／命中 ID，但不替代真实接触几何：近战、高级武技、主动和派生剑气均保留 NeoForge 查询返回的 `PartEntity`，排除 multipart 父体的大盒。先对部位执行墙体、几何和距离排序，再按父体去重、计容量；一次活动段不会因命中多块龙部位而多次扣血。

`CombatDamageService` 通过 `StatsRuntime.hurt(attacker, part, source, damage)` 提交给原版部位受伤路径；父体只用于权限、血量回执、无敌帧、天命上下文与命中账簿。头／非头系数、`phase.onHurt`、死亡阶段拒伤和玩家来源归属继续生效，不绕过原版阶段，也不冻结／强制击退父龙。主动与派生剑气继续使用既有 `playerAttack` 伤源，不额外伪装为原版箭矢；这不是对所有原版投射物语义的重新实现。

## 奖励与原版材料

`ExplorationLoot` 是注册的 NeoForge global loot modifier，只追加配置中的精确箱子和宝库根表，保留原版物品。不会匹配实体掉落、村民交易、试炼刷怪笼重复结算或宝库的内部子表。原版箱子的一次展开、宝库的原生玩家领取状态继续拥有次数；不另外建立领奖副本，不回填已开箱。

奖励复用六味可种植药材、灵石、玄铁／赤铜／淬锋砂、丹方、器谱及带正确 technique 组件的秘籍。启动时校验物品和秘籍类别／品级，避免空白秘籍。高阶主世界地点提高奖励池，下界保留独立档次；末地城只在精确的 `minecraft:chests/end_city_treasure` 根表追加末地奖励池。鞘翅、船和原版箱物继续由原版拥有，不创造额外领取入口，已开箱不回填。

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
/exploration guide side
/exploration guide where
/exploration catalogue
```

`/exploration guide side` 显示三条可选首领路线及准备 tips，向导主对话同时提供可点击入口；已完成主线仍可查看。管理员可用 `/myvillage exploration` 同名别名与 `info` 诊断；原 `/myvillage` 根权限不放宽。地点建议在实际切换且满足冷却后发送两条聊天信息，反复跨边界不刷屏。

`tools/gen_exploration_guide.py` 从探索规则、首领配置与中英语言输入生成两份 GuideME exploration 页面；包含完整主线、可选三首领支线和可选门槛说明，支线建议境界随 boss profile 生成，不在页面复制另一套常量。`processResources` 自动生成，手册首页链接它。不要手改生成页。

## 检查入口与边界

第二版按改动选择检查，不要求全量 release：

- `exploration-v2-build`：战斗、境界、野外、探索、天命相关聚焦 Java 检查、资源一致性和 jar。
- `exploration-v2-native`：在上述检查后运行两次真实服务器 boot，保存后重启同一世界。`tools/exploration_v2_native_evidence.py` 覆盖末地普通出生与旧体兼容、四首领归属／属性／精确来源、原生龙部位和水晶／复活、凋灵与监守者／守卫者机制、已加载 SERVER 开关的原版门处理、末地城箱子有限展开和重启读回。
- `exploration-v2-runtime`：同一固定源先执行 build／native，再运行客户端映射场景；可用 `--cases progression,bosses,portal,dragon,end_chest,two_clients,restart` 选择需要的场景。
- `exploration-v2-play`：已有相同产品输入的 Java/native 证据后，仅重建对应产品并进行映射局部复验；必须记录复用的源与最终 jar 内容身份。

原生证据输出在 `reports/exploration-v2-native/`，包括 initial／restart／summary、重启 fixture 和服务器 stdout。它使用专用服务器 fixture，部分检查通过 fake player 收件人、反射推进原版方法或动画完成；不证明映射输入、自然 AI 战术或玩家潜行探索。每轮的 selected／completed／unrun、失败日志、源身份、artifact 与销毁确认以[第二版报告](../testing/2026-10-18-vanilla-exploration-v2.md)为准；preset 存在不代表已经通过。

本地入口为 `python3 tools/validate_exploration.py`、相关生成器 `--check` 和对应聚焦 Python 检查。第一版 `exploration-build/native/runtime`、`exploration-play` 与箱子专项 `exploration-chest-restart` 保留历史范围，不将它们的通过自动推广到第二版。

开发者检查不能确认玩家手感、长期平衡、声音、FPS、所有种子自然密度或广泛多人体验；这些仍为 `not_verified`。
