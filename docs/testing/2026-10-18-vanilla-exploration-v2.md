# 原版探索与城镇向导第二版（0.66.0）

## 实现与交付边界

实现入口见 [KB60](../ai-kb/60_vanilla_exploration.md)。第二版补齐末地普通怪物、末影龙／凋灵／监守者／远古守卫者的独立境界配置、末地城有限奖励、末地后续向导路线，以及默认关闭的可选传送门境界限制。原版首领阶段、护盾、弱点与特殊机制继续由原版拥有；共用武技通过真实龙部位进入原版受伤入口。

服主可通过实例目录 `config/myvillage-exploration-server.toml` 的 `[portal_gates] enabled` 布尔项启用限制。若 `<世界目录>/serverconfig/` 中已有同名文件，该世界优先读取该覆盖文件；可手动创建以按世界设置。`defaultconfigs/` 同名文件仅在基础配置缺失时供初始化，不会为每个新世界自动创建覆盖文件。默认关闭，最低境界仍由探索 JSON 声明。

本文件是本轮记录，不改写[第一版历史证据](2026-10-18-vanilla-exploration-v1.md)。开发者运行与玩家验收分开记录。检查尚未执行或尚未收取结果时，不记为通过。

## 固定源与检查范围

- 产品源提交：`pending`。
- 最终交付源提交：`pending`。
- 本地检查：32 项相关 Python 检查、探索资源引用、指南生成一致性、Python 语法和 diff 空白检查通过。
- Modal run ID／preset：`pending`。
- `selected`：`pending`。
- `completed`：`pending`。
- `unrun`：`pending`。
- 原生服务器／映射客户端／双客户端／真实重启分别完成情况：`pending`。
- 云端退出状态与 `termination` 销毁确认：`pending`。

具体检查由改动和实际风险决定；本轮不自动继承第一版的全量场景清单。只有确认输入身份相同、并明确列出差异时才复用旧证据。

## 已观察结果

`pending`。后续填写必须区分规则／单元检查、原生事件 fixture、真实客户端映射输入、重启读回与玩家验收。命令造景、直接方法调用或姿态 probe 不作为映射输入证明。

## 失败与复验

首轮源 `3990c8615e83996e2e8a9b40271d1028610663ca`，运行 `20261018T144607Z-0a64e632`（exploration-v2-runtime）：资源、编译及 jar 通过；310 项 Java 中 308 项通过，2 项既有断言失败，原生与客户端未运行，Sandbox 销毁确认。

- `CombatPayloadTest` 仍期望协议 33，`9d9d76b2` 基线产品已为 34；V2 未修改协议。只修正准确期望值。
- `CombatDataLoaderTest` 的 trail 引用白名单漏掉基线已有的 `ArtDefinition` 元数据适配器。保留全文件扫描并精确允许既有两个路径，增加排序消除遍历顺序，不放宽至任意读取者。

独立审查另外发现并修复两个产品边界：飞行龙 1 HP 真实转入 DYING 的成功回执；凋灵提高最大生命后充能只剩约 56% 血量。新增原生 DYING 两次命中、真实充能 tick／被动回血／外部治疗检查，固定新源复验；未将首轮状态改写为通过。

第二轮源 `5a715bfe9e896301128c777d0dfc78009363a9de`，运行 `20261018T145555Z-014535a1`（`exploration-v2-runtime`）：312 项聚焦 Java 全部通过，资源、编译和 jar 通过。原生 initial 在 `native_source_indirect_magic: []` 失败；真实 restart 及七类映射客户端场景全部未运行，不能称本轮 runtime 通过。Sandbox `termination=confirmed`，artifact 已下载并校验。

- native 总阶段 `selected=[initial,restart]`、`completed=[]`、`unrun=[initial,restart]`；initial 内部已完成 `end_birth_compatibility,boss_profiles`，随后 dragon 场景未完成。部分部位、水晶治疗与接触伤害检查留有通过记录，不提升为整个场景完成。
- 失败现场尚未记录到预期的龙吐息云 `indirect_magic` 事件，`native_evidence` 正在定位 fixture 的云触发／取样过程；当前不能归因产品伤害错误，也不能跳过断言写成通过。
- 证据保留于 `reports/modal-runs/20261018T145555Z-014535a1/artifacts/reports/exploration-v2-native/` 的 initial／summary 和 stdout；该轮失败状态保留。

独立检查另外补齐潜影贝原生复制路径：普通出生与史莱姆分裂 hook 均不会覆盖这一分支，因此在实际 `hitByShulkerBullet` 子体加入世界前继承有效父体归属；旧无分配父体和异常／外来归属不补抽。此后续代码不属于第二轮 `5a715bfe` 的已验证范围，等待固定新源复验。

## 交付产物

- jar 路径／大小／SHA-256：`pending`。
- jar 中必需配置、指南与 runtime 类，以及不含专用 evidence 类：`pending`。
- 截图／运行证据／日志路径：`pending`。
- 玩家电脑安装／启动：`not_performed`（除非另有明确授权并实际执行）。

## 未观察项

玩家手感、长期平衡、声音、真实玩家电脑 FPS、全部种子自然密度、广泛多人体验和最终玩家验收保持 `not_verified`。软件渲染、开发者截图与局部自动化证明不替代这些体验。
