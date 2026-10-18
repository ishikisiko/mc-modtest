# 原版探索与城镇向导第二版（0.66.0）

## 实现与交付边界

实现入口见 [KB60](../ai-kb/60_vanilla_exploration.md)。第二版补齐末地普通怪物、末影龙／凋灵／监守者／远古守卫者的独立境界配置、末地城有限奖励、末地后续向导路线，以及默认关闭的可选传送门境界限制。原版首领阶段、护盾、弱点与特殊机制继续由原版拥有；共用武技通过真实龙部位进入原版受伤入口。

服主可通过实例目录 `config/myvillage-exploration-server.toml` 的 `[portal_gates] enabled` 布尔项启用限制。若 `<世界目录>/serverconfig/` 中已有同名文件，该世界优先读取该覆盖文件；可手动创建以按世界设置。`defaultconfigs/` 同名文件仅在基础配置缺失时供初始化，不会为每个新世界自动创建覆盖文件。默认关闭，最低境界仍由探索 JSON 声明。

本文件是本轮记录，不改写[第一版历史证据](2026-10-18-vanilla-exploration-v1.md)。开发者运行与玩家验收分开记录。检查尚未执行或尚未收取结果时，不记为通过。

## 固定源与检查范围

- Java／原生验证源：`2e9d975136af2083f1eaf2c1cb0f1a31c863ea8c`；第五轮映射源：`274dfac8450f7b992941ff2f40fb10fb9ee314ba`。
- 最终 jar 构建源：`a0bac41473837d8b343ea790d9f63f7e74de55ff`；本报告的后续文档提交不作为 jar 构建源。
- 本地检查：32 项相关 Python 检查、探索资源引用、指南生成一致性、Python 语法和 diff 空白检查通过。
- Java／native producer：`20261018T150716Z-edf585fb`（`exploration-v2-runtime`），315 项 Java、145 项 initial 和 22 项真实重启检查全过。
- 最后映射 producer：`20261018T155544Z-d99d555b`（`exploration-v2-play`），30 项检查全过。
- 最后一轮 `selected=completed=[bosses,end_chest,two_clients,restart]`、`unrun=[]`、`failures=[]`、`status=pass`。
- 七类映射场景按下表分轮覆盖，合并覆盖无未完成项；没有任何一轮被改写成七类同时全部通过。
- 最后一轮云端 `exit_code=0`，artifact `downloaded_and_verified`，`termination=confirmed`；七个 run 均已确认销毁，前六轮失败事实继续保留。

| 映射场景 | 采用的实际完成证据 |
|---|---|
| `progression`、`portal`、`dragon` | 第五轮 `20261018T153523Z-77396358` |
| `bosses`、`end_chest`、`two_clients`、`restart` | 第七轮 `20261018T155544Z-d99d555b` |

最终 jar 有 2,886 个 entry、1,156 个 class。与第三轮 Java／native jar 比较无增删，全部 class 及其他资源相同，差异仅为两份中英探索指南的配置入口与默认开关说明；与第五轮 jar 的全部 entry 完全相同。这一产品身份比较支持上面的分轮复用，不改变第三至六轮各自的失败状态。

具体检查由改动和实际风险决定；本轮不自动继承第一版的全量场景清单。只有确认输入身份相同、并明确列出差异时才复用旧证据。

## 已观察结果

第三轮固定源 `2e9d975136af2083f1eaf2c1cb0f1a31c863ea8c`，运行 `20261018T150716Z-edf585fb`（`exploration-v2-runtime`）：315 项聚焦 Java、资源检查、编译与 jar 通过。原生专用服务器 initial **145 项**、真实重启 **22 项**全过；native 总阶段 `selected=[initial,restart]`、`completed=[initial,restart]`、`unrun=[]`，各阶段内部选择项也全部完成。

- 末地新普通怪物接入、旧无分配个体保留基线；潜影贝真实原生复制子体继承有效父体记录，旧／foreign 父体不补抽，父体生命与归属不改。
- 四首领独立 profile、旧受伤个体按生命比例一次接入及 foreign 归属保护；真实保存／重启后 UUID、生命、最大生命和出生记录保持，凋灵半血阶段仍在。
- 龙部位与原生非玩家免疫、头／身体系数、水晶固定治疗、有 owner 的 HARM 0／1 吐息云一次缩放、四水晶复活动画；飞行阶段 1 HP 致死命中进入原生 DYING 后不再接受重复命中。
- 凋灵真实充能 tick 与出生爆炸、半血箭免疫、头颅直击及有 owner 爆炸；提高最大生命后充能正常达到 profile 满血，普通被动 +1 与外部治疗不被充能规则放大。监守者原生音爆、远古守卫者光束／棘刺与疲劳同时有真实事件证据。
- 实际已加载 SERVER 开关与注册 `PlayerTick.Pre`，门槛关闭／开启／达标、返回、折跃门和非玩家策略；原版末地城箱保留原物品、不可重复追加，真实重启后不回填。

以上属于原生方法／事件和专用服务器 fixture，部分通过 fake player 收件人或反射推进原版行为完成，不是映射玩家操作。

同一轮映射客户端记录 24 项检查，七类场景完成 2 项、`unrun` 5 项；已完成 `progression,bosses`：实际按键查询主线／可选支线，并对三类可选首领及真实 `SPAWNER` 僵尸取得命中、HP 回执与归属不变的检查。`portal` 在进入末地前超时，后续 `dragon,end_chest,two_clients,restart` 未运行；该轮整体仍为失败，不能将原生通过算作七类映射全部通过。失败与复验见下节，最终交付源和产物见本报告固定源与交付项。

第四轮 `d642675795c4bc41277afc3890eec1c115b4ffbc`／`20261018T152113Z-6391ecf2` 的 `exploration-v2-play` 已补齐默认门不锁探索的真实映射证据：筑基初期玩家从主世界经原版末地门进入实际末地，低于启用门槛时的建议境界仍可进入，背包内容与物品损耗组件保持。`progression,bosses,portal` 三类完成；龙映射场景尚未完成，末地城箱、双客户端和映射重启未运行，整体依旧为失败。

第五轮 `274dfac8450f7b992941ff2f40fb10fb9ee314ba`／`20261018T153523Z-77396358`（`exploration-v2-play`）补齐龙映射证据：三个独立 UUID 的真实 phase 5 trial 保持正常 AI／部位 tick，经 NBT 验证位置与朝向稳定。实际鼠标横斩获得 0.95 招式倍率和约 39.900024 生命损失的 owner 回执；真实箭矢观察到接触后点燃而龙不失血；映射剑气造成 44.396484 生命损失并只计一次 owner 命中。未写目标 Health，未出现前轮 Hover 漂移。

该轮 43 项检查均通过，`progression,bosses,portal,dragon` 四类完成；随后末地城箱映射开箱超时，双客户端和映射重启尚未运行，整体仍为失败。该轮不能用 43 项通过掩盖未完成项；其余场景由第七轮局部复验补齐。

第六轮 `8ceab31f35e63e44409f7084fc25cd349724b6ae`／`20261018T154641Z-dc48b8ec` 的局部复验完成 `bosses,end_chest`：玩家实际位置断言为 `[420.5,100,417.5]`，真实打开末地城根表箱子，保留原版物品并追加修炼物资，再通过映射 Shift 点击取走奖励。两名真实客户端也分别完成 guest=`awaken`、主玩家=`complete` 的 guide 读回；随后全属性 NBT 比较失败，双客户端场景未完成，映射 restart 未运行，整体仍为失败。

第七轮 `a0bac41473837d8b343ea790d9f63f7e74de55ff`／`20261018T155544Z-d99d555b` 完成最后四类局部复验，30 项检查全为 true，`selected=completed=[bosses,end_chest,two_clients,restart]`，`unrun=[]`、`failures=[]`、`status=pass`。真实开箱、保留原版与修炼物品、Shift 取物通过；两名普通权限真实客户端分别得到自己的 guide 进度，共享首领的 UUID、HP、出生归属及完整属性严格一致。

随后真实停止并重新启动服务器和客户端：重新开箱时已取物不补回、奖励仍在玩家背包，映射受伤后的远古守卫者 HP、UUID、完整属性和出生记录保持不变。最后运行退出 0，artifact 下载并校验，Sandbox 销毁确认。三类可选首领映射采用受控静止目标；龙采用真实栖息阶段，并非从自然遭遇到完整 AI 击杀的胜利证明。

开发者结果与玩家验收分开；命令造景、直接方法调用或姿态 probe 不作为映射输入证明。

## 失败与复验

首轮源 `3990c8615e83996e2e8a9b40271d1028610663ca`，运行 `20261018T144607Z-0a64e632`（exploration-v2-runtime）：资源、编译及 jar 通过；310 项 Java 中 308 项通过，2 项既有断言失败，原生与客户端未运行，Sandbox 销毁确认。

- `CombatPayloadTest` 仍期望协议 33，`9d9d76b2` 基线产品已为 34；V2 未修改协议。只修正准确期望值。
- `CombatDataLoaderTest` 的 trail 引用白名单漏掉基线已有的 `ArtDefinition` 元数据适配器。保留全文件扫描并精确允许既有两个路径，增加排序消除遍历顺序，不放宽至任意读取者。

独立审查另外发现并修复两个产品边界：飞行龙 1 HP 真实转入 DYING 的成功回执；凋灵提高最大生命后充能只剩约 56% 血量。新增原生 DYING 两次命中、真实充能 tick／被动回血／外部治疗检查，固定新源复验；未将首轮状态改写为通过。

第二轮源 `5a715bfe9e896301128c777d0dfc78009363a9de`，运行 `20261018T145555Z-014535a1`（`exploration-v2-runtime`）：312 项聚焦 Java 全部通过，资源、编译和 jar 通过。原生 initial 在 `native_source_indirect_magic: []` 失败；真实 restart 及七类映射客户端场景全部未运行，不能称本轮 runtime 通过。Sandbox `termination=confirmed`，artifact 已下载并校验。

- native 总阶段 `selected=[initial,restart]`、`completed=[]`、`unrun=[initial,restart]`；initial 内部已完成 `end_birth_compatibility,boss_profiles`，随后 dragon 场景未完成。部分部位、水晶治疗与接触伤害检查留有通过记录，不提升为整个场景完成。
- 失败现场当时未记录到预期的龙吐息云 `indirect_magic` 事件，先定位 fixture 的云触发／取样过程；未归因产品伤害错误或跳过断言。第三轮保留真实事件生产与断言后复验通过。
- 证据保留于 `reports/modal-runs/20261018T145555Z-014535a1/artifacts/reports/exploration-v2-native/` 的 initial／summary 和 stdout；该轮失败状态保留。

独立检查另外补齐潜影贝原生复制路径：普通出生与史莱姆分裂 hook 均不会覆盖这一分支，因此在实际 `hitByShulkerBullet` 子体加入世界前继承有效父体归属；旧无分配父体和异常／外来归属不补抽。此后续代码不属于第二轮 `5a715bfe` 的已验证范围，由第三轮固定源完成复验。

第三轮 `2e9d975136af2083f1eaf2c1cb0f1a31c863ea8c`／`20261018T150716Z-edf585fb` 保留整体失败状态：315 项 Java 与两次原生 boot 全部通过，映射 `selected=[progression,bosses,portal,dragon,end_chest,two_clients,restart]`，`completed=[progression,bosses]`、`unrun=[portal,dragon,end_chest,two_clients,restart]`，记录 24 项检查；`portal` 已尝试但未完成，后四项未运行。超时原文为 `mapped default-advice End entry below recommendation`，玩家读回仍在 `minecraft:overworld`。artifact 已下载并校验，`termination=confirmed`。

根因已确认是 fixture 的实例配置继承：当前 NeoForge 默认读取实例 `config/myvillage-exploration-server.toml`，native 阶段启用的 `true` 已写入该基础配置；mapped 启动只重写 `defaultconfigs/`，不会覆盖已存在的基础文件，造成预期默认关闭的门实际仍开启。采集器 `start` 现同时写实例 `config/` 与 `defaultconfigs/` 为 false，仍仅允许 disposable Modal 环境；未把产品的默认配置或门槛放宽。`26a7e807` 同步纠正 README、KB 与生成指南的真实配置入口。

第四轮源 `d642675795c4bc41277afc3890eec1c115b4ffbc`，运行 `20261018T152113Z-6391ecf2`（`exploration-v2-play`）：jar 构建通过；映射共 29 项检查，28 项通过、1 项失败。`selected=[progression,bosses,portal,dragon,end_chest,two_clients,restart]`，`completed=[progression,bosses,portal]`，`unrun=[dragon,end_chest,two_clients,restart]`；dragon 已尝试但未完成，后三项未运行。第三轮的配置残留问题已由真实原版门进入末地及背包读回复验通过；本轮 artifact 已下载并校验，`termination=confirmed`。

失败在 `dragon_mapped_basic_sweep_owner_receipt_and_dedup`：横斩前后龙生命均为 800，trace 为 `not_observed`。fixture 以 yaw 0 的部位坐标安排攻击，但真实 NBT 的 yaw 是 `-128.64758`，同时仍有 Motion；原版 Hover 即使目标零距离也保留 `-0.02` 推进并继续转向。该轮证据说明场地几何前提错误，不能将未命中写成产品部位伤害故障，也不能跳过命中断言称为通过。

后续仅修正龙 fixture：每个 trial 生成独立 UUID 的真实 `SITTING_FLAMING`（phase 5）龙，保留正常 AI 和部位 tick、原版栖息阶段箭免疫，不写 Health；攻击前后严格读回并断言位置、rotation、motion 和 phase。继续要求映射横斩、剑气的实际 HP／owner 回执与去重，以及真实箭矢拒伤。该修复由第五轮固定源实际复验，不把场地准备或 NBT 前提检查本身当作攻击通过。

第四轮 jar 已与第三轮通过原生验证的 jar 逐条目比较：无新增／删除条目，1156 个 class 全部相同，差异仅为两份中英探索指南的配置入口与默认开关说明。另将世界资源生成任务的工具树输入排除 Python 缓存；源码、JSON 与蓝图仍是原输入；第五轮验证后续启动的 up-to-date 行为并再次比较 jar。

第五轮源 `274dfac8450f7b992941ff2f40fb10fb9ee314ba`，运行 `20261018T153523Z-77396358`（`exploration-v2-play`）：jar 通过，43 项已记录检查全部为 true；`selected=[progression,bosses,portal,dragon,end_chest,two_clients,restart]`，`completed=[progression,bosses,portal,dragon]`，`unrun=[end_chest,two_clients,restart]`。`end_chest` 已尝试但未完成，错误原文为 `End-city root chest opens: True`，表示 pointer 判断为 true 后实际开箱等待超时；后两项未运行。整体 `failed`，artifact 保留，`termination=confirmed`。

重复世界资源生成也在本轮完成复验：服务器约 22 秒进入客户端启动阶段，真实日志 `generateWorldPlanAssets UP-TO-DATE`。第五轮 jar 与第三轮原生验证 jar 逐 entry 比较无新增／删除，1156 个 class 全同，差异仍仅为两份中英探索指南的配置入口与默认开关说明，因此复用第三轮 315 项 Java 与 167 项原生检查，并保留这两份文档资源差异。

开箱 fixture 先传送进入 void 后等待、再铺 floor；修复在 floor 完成后再次传送并严格检查实际 Pos，双客户端场地同样增加铺好 floor 后的二次传送。第六轮仅用 `exploration-v2-play --cases bosses,end_chest,two_clients,restart` 复验：`bosses` 提供受伤首领重启前提，并补齐 actor 觉醒／入门状态以支持独立选取；双客户端显式以管理 fixture 设置主玩家主线完成、guest 为新进度，再分别进行真实 guide 读回。该场地修复由第六轮实际开箱与位置读回证据复验；双客户端的完整场景该轮仍未通过，由第七轮补齐。

第六轮源 `8ceab31f35e63e44409f7084fc25cd349724b6ae`，运行 `20261018T154641Z-dc48b8ec`（`exploration-v2-play`）：jar 通过；stdout 记录 25 项 PASS、1 项 FAIL，其中 PASS 包含失败后的第二客户端自有进程回收检查。`selected=[bosses,end_chest,two_clients,restart]`，`completed=[bosses,end_chest]`、`unrun=[two_clients,restart]`；双客户端已尝试但未完成，restart 未运行，`termination=confirmed`，失败日志与 artifact 保留。

失败为 `two_real_clients_do_not_rescale_shared_boss` 的严格属性快照比较。唯一属性 NBT 差异是 after 新出现原版默认 `movement_speed=0.30000001192092896`、`oxygen_bonus=0`；UUID、HP、出生归属及两项首领属性修饰相同。源码核对确认 `LivingEntity` 正常首 tick 的呼吸／移动路径会通过 `getAttribute` 实例化这些默认属性，而 `AttributeMap.save` 只保存已实例化项目；这是读取时机造成的默认属性落盘差异，不是按玩家自适应缩放。

采集器在 before 快照前增加只读轮询，等待这两个原版默认属性出现，再保留原有全部属性的严格比较；不放宽断言、不修改属性，也不改产品逻辑。第七轮固定源仅复验 `bosses,end_chest,two_clients,restart`，没有重跑全部七类；30 项检查全过、四类全部完成，真实重启通过。第三轮 315 项 Java／167 项原生检查和第五轮 `progression,portal,dragon` 的完成证据，经最终 jar 的逐条目比较确认可复用。

第七轮 `a0bac41473837d8b343ea790d9f63f7e74de55ff`／`20261018T155544Z-d99d555b` 的成功记录补齐余下能力，前六轮失败各自保留；七类完成属于同一产品身份的分轮证据，不声称一轮七类全过。

## 交付产物

- [myvillage-0.66.0.jar](../../build/libs/myvillage-0.66.0.jar)，5,749,294 bytes，构建源 `a0bac41473837d8b343ea790d9f63f7e74de55ff`。
- SHA-256：`758ae263a64a5197aaec05e6cc7998dbd1426232c865e1d5be24c38393ad40ee`；同目录保留 `.jar.sha256`。
- 必需配置、runtime 类、Mixin 声明和两份生成指南逐项匹配源码，metadata 为 `0.66.0`，无专用 Evidence 类；[交付身份与证据汇总](../../reports/exploration-v2-0.66.0-handoff.json)。
- [第五轮龙映射横斩截图](../../reports/modal-runs/20261018T153523Z-77396358/artifacts/out/preview/exploration-v2/dragon-mapped-basic-sweep.png)、[剑气截图](../../reports/modal-runs/20261018T153523Z-77396358/artifacts/out/preview/exploration-v2/dragon-sitting-sword-qi.png)。
- [第七轮开箱取物前](../../reports/modal-runs/20261018T155544Z-d99d555b/artifacts/out/preview/exploration-v2/end-city-chest-before-take.png)、[真实重启后重新开箱](../../reports/modal-runs/20261018T155544Z-d99d555b/artifacts/out/preview/exploration-v2/end-city-chest-after-restart.png)、[guest 向导](../../reports/modal-runs/20261018T155544Z-d99d555b/artifacts/out/preview/exploration-v2/guide-AlchemyGuest-awaken.png)、[主玩家向导](../../reports/modal-runs/20261018T155544Z-d99d555b/artifacts/out/preview/exploration-v2/guide-CaptureDev-complete.png)。
- [第七轮完整映射 evidence](../../reports/modal-runs/20261018T155544Z-d99d555b/artifacts/out/preview/exploration-v2/evidence.json) 与 [退出／销毁状态](../../reports/modal-runs/20261018T155544Z-d99d555b/state.json)。截图只作交互旁证，记录中的 `stable:false` 不作为稳定画面或外观验收。
- 玩家电脑安装／启动：`not_performed`。本次重型构建、服务器和客户端均在 Modal。

## 未观察项

完整自然 AI 首领战胜利、玩家手感、长期平衡、声音、真实玩家电脑 FPS、全部种子自然密度、广泛多人体验和最终玩家验收保持 `not_verified`。软件渲染、开发者截图与局部自动化证明不替代这些体验。
