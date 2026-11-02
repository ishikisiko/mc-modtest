# 修士与怪物选敌验证（0.109.1）

18 款新行尸、骸弓主动搜索修士；原版敌对生物在已有的村民目标查询中识别修士。具体范围与资源归属见[功能说明](../features/cultivator-hostility.md)。

## 固定来源与交付

- 生产提交：`3cb6c71af46920141dd4f7c0db74920376f8391b`，基于 `27fadf50371f308d8de6ddfc802b578c17e570a9`；后续只有不打包的说明文档变更。
- 源码归档 SHA256：`3a3e0ceec20ee1f103e3a09ddc7665065dd8cee95294cbee87e090afa2c773e2`。
- [JAR](https://assets.ishiki333hhh.win/cultivator-hostility-20261102/myvillage-0.109.1.jar)：52,875,182 字节，SHA256 `d7bd51bebffe345df442757e05f56fc8a8503fbe1d2c56da2479a3a5dbb2f1ae`。
- [原始证据](https://assets.ishiki333hhh.win/cultivator-hostility-20261102/cultivator-hostility-0.109.1-evidence.tar.gz)：SHA256 `bb0a6733d88fd67cd855017ef2f31ad5ca8bd636ac4e99ff728a285dad34c581`，含 JUnit XML、原生结果、完整构建／服务端日志、命令和来源摘要。
- [机器摘要](https://assets.ishiki333hhh.win/cultivator-hostility-20261102/summary.json)。

## 实际结果

| 检查 | selected | completed | failed | unrun |
|---|---:|---:|---:|---:|
| 本地 Python 定向测试 | 6 | 6 | 0 | 0 |
| Java 定向测试，逐项读取 JUnit testcase | 59 | 59 | 0 | 0 |
| 原生自然选敌场景 | 36 | 36 | 0 | 0 |

生成数据、模型资源、UV 与时点检查以及 `git diff --check` 通过。Java 选择 `*Undead*`、`*Beast*`、`*WildernessRulesTest`、`*NpcCombatLedgerTest` 和 `*CombatTargetsTest`，没有因合并扩大到全量 release。

原生场景共 36 项：18 款新亡灵、9 类原版敌人、1 项原村民目标、2 项最近候选竞争、5 项排除／边界，以及 1 项旧目标删除后取得新 UUID。五项排除为原版骷髅保持原行为、同队行尸、同队僵尸、墙后的新骸弓目标和超距目标。

全部初始攻击目标为空，夹具调用 `setTarget` 次数为零，注入伤害次数为零。18 款新亡灵和 9 类原版敌人均自然选中修士并造成实际生命损失；修士的目标随后指向真实施害者。唤魔者场景同时观察到修士把恼鬼作为自卫目标。原村民仍被正常追逐，村民和修士同场时保留原查询的最近候选选择。

`npc_retaliated` 字段表示修士进入自卫并取得施害者目标，不能单独当作反击招式已经造成伤害的证据。探针没有逐招验收修士剑招。

## 环境与打包

复用 Colab High-RAM CPU 会话 `sect-blueprint-runtime`，在新目录 `/content/cultivator-hostility` 解开固定归档。只复用 VM 的依赖缓存，14 项必要资源生成任务都从本轮来源重新生成；为满足已有道路端口任务的资源读取顺序，先复制本轮 V11 资源并逐文件核对城市输入。测试／JAR 阶段复用这些同源生成输出，保留所有 verifier 和选定测试。

原生使用 seed `20261102` 的隔离平地与独立端口 `25597`；命令创建测试实体，提高双方生命上限以留出观测时间。战斗场景中的修士运行正常 AI，边界／最近目标场景关闭目标 AI 固定初始条件。创建前等待实体区块就绪，本轮等待 17 tick，随后观测 400 tick。每 tick 检查活动实体引用，避免未就绪区块替换测试引用。

专服从开发 classpath 启动。交付 JAR 的 **2005 个生产类、30,896 个资源与实际运行目录逐字节相同**，包含新的选敌 Mixin 与配置，evidence 类数量为零。没有在本地运行 Gradle、Minecraft，也没有启动或修改玩家存档。

会话与本轮构建缓存保持原状；关闭由用户决定。

## 验证边界

本次证明隔离场景中的自然选敌、攻击伤害和自卫目标交接。玩家映射输入、多人、FPS、自然种群密度与长期平衡均为 `not_verified`；没有进行新的外观或音效验收。原版适配沿用各怪物是否追村民的既有规则，原版骷髅没有新增追逐修士的目标。
