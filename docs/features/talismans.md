# 符箓 demo：朱纹制符案

本页是实现与真实输入验收入口。源码、资源与离线预览已完成；Gradle/JUnit 执行、真实服务器/客户端输入、玩家外观、手感、FPS 和多人均待父代理固定源 Colab 验证，不能把下列操作说明视为已经通过的游戏证据。

## 生存入口

- 工作台合成朱纹制符案：上排红石／纸／红石，中排三铜锭，下排木板／空／木板。
- 一张原版纸 + 一颗下品灵石（公共标签）得到八张素符纸。
- 红石 + 墨囊 + 一颗下品灵石得到四份朱砂灵墨。
- 觉醒灵根后，右击制符案，放入素符纸和朱砂灵墨；直接选择引雷／镇步／护身符，不需要独立秘籍。
- 鼠标左键持续按住「注灵绘制」。松开、移出按钮、失焦、关闭菜单、离开、死亡或断线都会暂停。40 个服务器有效脉冲完成一张，默认每 5 tick 一个脉冲，每次支付 1 点灵力。心跳每 3 客户端 tick 发送，服务器租期仅 7 tick。客户端不提交时间、成本、品质或进度。
- 第一有效脉冲消耗一纸一墨并形成归属制符者的持久符稿。冻结资质、品质上限／品质、所需脉冲和单脉冲成本。关闭后留在案内，重登／重启／换菜单不重抽、不补离线进度。只有原制符者可以续画。
- 材料不足、灵力不足或输出不兼容／满堆叠时拒绝当前脉冲，不扣原料／灵力。活动符稿锁定两输入槽与符式；产物只生成一次，完成后必须松开重新按住才能另画一张。
- 生存拆案掉落**唯一封存制符案物品**，库存和已付符稿随 `BLOCK_ENTITY_DATA` 保存；不会同时散落库存。放回后继续。方块无漏斗／管道能力；创造模式删块沿用原版不掉落语义。

所有数字与效果来自 `tools/talisman/rules.json` 经专属生成器发布的 `data/myvillage/talisman/rules.json`；上面只是当前默认值说明。

## 三种符

符箓使用独立于普通静坐会话，可在正式渡劫中右键使用。每次成功激活消耗一张符和配置中的少量灵力（当前 2），三种符一起进入共用短间隔（当前 12 tick），换符／换堆叠不能绕过。准备、容量、落点或护盾授予失败不扣符不扣灵；创造模式沿用原版物品免耗，灵力仍支付。

| 符 | 操作与结果 | 下／中／上／极品 |
|---|---|---|
| 引雷符 | 指向 16 格内已加载、无遮挡的地面上表面右键；地符短预警后一次范围雷击 | 伤害 7／10／14／19 |
| 镇步符 | 同样指地右键布下可见纸符；敌人进入后只触发一次定身 | 35／50／65／80 tick |
| 护身符 | 直接右键自用，限时有限护盾，同源替换 | 护盾量 6／9／13／18 |

引雷仅粒子／雷声表现，**不创建原版 LightningBolt**（原版 visualOnly 仍会驱动避雷针和铜块除锈）。真实伤害通过 `CombatDamageService.hurtDerived` 与 `StatsRuntime` 处理一次减伤，不触发递归进攻天命；范围内再次检查友军、同宗、PvP、创造玩家和从落点到目标的遮挡。

镇步复用 `ControlService.root`，强敌缩短、霸体／免控拒绝，重复根缠递减；自己、友军／同宗、禁 PvP 玩家不会触发。每张符至多控制一个合法目标。潜行空主手右击未触发地符附近可收回，背包满时不移除。每玩家同时最多 3 张地符（引雷与镇步共用），服务器总量有界；离线、死亡、跨维度、区块卸载或到期消散，不作为持久生产器。展示使用不可拾取的 ItemDisplay 视觉副本；重启遗留展示在加载时删除。

护身符唯一稳定来源为 **`myvillage:talisman_ward`**，复用 `ShieldRuntime`。新增通用 `grantPaid` 在独立账簿副本上检查容量，成功付款后才安装护盾；失败付款保留所有旧盾余额和期限。雷劫可通过同一共享护盾管线吸收，符系统不导入雷劫管理器。

## 独立制符资质

`CraftingAptitudes` 保留 version 1 与旧四参数构造，新增可选 `talisman`。缺失／-1 表示仅该职业未初始化，不代表合法零资质；旧炼丹、炼器分数原样保留。新固定 seed domain 独立生成，`CultivationService` 唯一安装，既有 `copyOnDeath` 继续拥有死亡复制；无玩家刷经验系统。

H → 技艺页显示炼丹／炼器／制符三项资质与服务器计算品质上限；宽屏并列，窄屏可滚动。品质沿用 crafting 统一上限规则；本 demo 每批确定地产出其冻结上限品质，不按重开或每笔重新随机。

管理员 fixture：`/myvillage cultivation crafting set <player> talisman 90`；读回 `/myvillage cultivation crafting <player>`。既有 `xiulian jiyi`、`shezhi`、`zhifu` 等别名同一服务器处理器。真实路径以 `CultivationCommands` 现有根命令为准；这些指令不完成任何玩家绘制。

`CultivationSnapshotPayload` 增加制符资质与上限。**父代理须把统一网络 protocol 从基线 47 提升至至少 48**，在两端使用同一版本；本分支依分工不改 protocol、mod 版本、CHANGELOG 或 README。

## 真实键鼠验收几何

屏幕逻辑尺寸固定 **300 × 238**；原点 `leftPos=(GUI宽度-300)/2`、`topPos=(GUI高度-238)/2`。以下为相对左上角 GUI 坐标；实际像素需乘真实 GUI scale。

| 控件 | 范围 x,y,w,h | 中心 | 菜单按钮 ID |
|---|---|---|---|
| 引雷符 | 8,30,88,20 | 52,40 | 0 |
| 镇步符 | 8,54,88,20 | 52,64 | 1 |
| 护身符 | 8,78,88,20 | 52,88 | 2 |
| 持续注灵 | 110,111,178,22 | 199,122 | 10 心跳；11 释放 |

| 菜单槽索引 | 用途 | 槽左上角 | 中心 |
|---|---|---|---|
| 0 | 素符纸输入 | 116,34 | 124,42 |
| 1 | 朱砂灵墨输入 | 152,34 | 160,42 |
| 2 | 成符输出（只取） | 262,34 | 270,42 |
| 3..29 | 玩家主背包 3 行，每行 9 槽 | 70+列×18,156+行×18 | 左上角+8 |
| 30..38 | 玩家快捷栏，列 0..8 | 70+列×18,214 | 左上角+8 |

菜单／物品／资源 ID：`myvillage:talisman_desk`，`myvillage:talisman_paper`，`myvillage:spiritual_ink`，`myvillage:talisman_lightning`，`myvillage:talisman_snare`，`myvillage:talisman_ward`。创造栏同样可取。

## 只读诊断 schema 1

权限 2：

```text
/myvillage talisman status
/myvillage talisman status <x> <y> <z>
/myvillage talisman status <player>
/myvillage talisman status <player> <x> <y> <z>
```

RCON 请用显式玩家和坐标。省略坐标优先当前打开的真实制符案，其次当前视线内方块。命令不加载未知区块，不初始化资质，不推进脉冲，也不扣费。

输出严格为 **`TALISMAN_STATUS ` + 单行 JSON**，不会把展示文本当 JSON。字段：

- `schema:1`、`player` UUID、`playerName`、`dimension`、`pos:[x,y,z]`、`loaded`。
- `talismanAptitude`（未生成 -1）、`qualityCap`（未生成 0）、`currentPower`、`useCooldown`、`shieldTotal`、`shieldSource`、`activeSeals`（整数，当前玩家活动地符总量）。
- 找到案时 `desk:true`、`recipe:0..2`、`recipeId:lightning|snare|ward`、`stage:idle|drawing|corrupt`、`progress`、`required`、`batch` 布尔、`frozenQuality`、`frozenAptitude`、`holding` 布尔、`owner` UUID 或 null、`viewer` UUID 或 null、`status` 整数。
- `inventory` 长度 3，每项 `slot`、`item`、`count`；非空成符另含 `quality`。无案时 `desk:false`、`stage:missing`，不输出案内字段。
- `status`：0 暂停，1 绘制，2 缺材料，3 堵输出，4 缺灵力，5 未觉醒，6 非符稿主人，7 数据异常保留，8 已完成待松开。以进度／库存判实际事务，不能仅凭状态码推断完成。

推荐验收链：真实鼠标插料 → 按住到部分进度 → 松开观察不推进 → 关界面等待再开续制 → 完成仅一张 → 保持按住不得自动第二批 → 取出右键施符；分别测试低／高资质同符式强度、缺灵力／满输出、共用施放间隔、收回地符、重启／拆放保留，以及渡劫护身符共享来源。

## 资源与检查

`tools/gen_talisman_assets.py` 拥有所有新模型、纹理、生存配方、台子空 loot 声明、规则发布及新增中文语言键（默认语言同样使用中文，保留原有英文键）。输入在 `tools/talisman/`。不手改输出。

```bash
# 已运行的轻量资源检查；当前使用仓库已有预览环境
/home/ubuntu/code/mc/mc-modtest/.venv-preview/bin/python tools/gen_talisman_assets.py --check
/home/ubuntu/code/mc/mc-modtest/.venv-preview/bin/python -m unittest tools.tests.test_talisman_assets
python3 -m unittest tools.tests.test_validate_crafting_aptitudes
python3 tools/validate_crafting_aptitudes.py
```

前两项通过。资质 Python 27 项中 25 通过，2 项由待父代理统一提升的 protocol 47 拒绝；原始日志保留 `reports/talisman-checks/`。`git diff --check` 与相关 Python 语法检查通过。离线正式模型／物品／布局预览在 ignored `reports/talisman-preview/`，不提交。

父代理统一固定源 Colab 执行建议：

```bash
./gradlew test --tests '*cultivation.crafting.*' --tests '*CultivationSnapshotPayloadTest' \
  --tests '*CraftingReadoutsTest' --tests '*ClientCraftingStateTest' \
  --tests '*talisman.*' --tests '*ShieldLedgerTest' --tests '*ShieldRuntimeTest' build
```

新增／扩展源测试覆盖旧资质仅补新增职业、稳定独立 domain、snapshot roundtrip、客户端同一快照、hold 到期、满输出／缺料／缺灵不提交、暂停续制不补算、同 tick 仅一次支付、冻结品质上限／持久化、共用激活前置、友军／PvP、付费护盾准备失败／付款失败、资源注册闭环。JUnit 源码未在本机执行；对应真实 UI、生命周期与多人仍为 `not_verified`。
