# 怪物掉落默认随模组更新（0.134.1）

服务器默认读取当前JAR内的 `data/yuanxian/drops/monster_drops.json`。修改源码表、重新构建并替换模组后，重新启动游戏或服务器即可使用新规则；游戏目录中已有的外置表保持原内容，默认不参与加载。

服务器需要自定义时，将 `config/yuanxian/monster_drops_settings.json` 的 `use_external_config` 设为 `true`，再通过外置 `monster_drops.json` 和 `/yuanxian drops reload` 调整。改回 `false` 并重载即可即时切回包内规则。`status`、目标查询和JSON导出标明实际来源。开关与规则完整校验后一起生效，失败保留当前来源、规则和版本；启动失败使用本次包内规则并记录错误。完整用法见[怪物掉落配置](../features/monster-drops.md)。

## 固定源码与交付产物

- 生产构建提交：`95e4e23b925a4639450ca76c9a723c83a0bb0f31`。
- 固定源码档案SHA-256：`28c419eb8db64a26ad966899e92b4487738286059d75669ac66b25aab4ffcbf8`，共52,024个文件，云端逐文件核对通过。
- 最终采集器提交：`5b9e31714871d25d461c763c407f1f3224c1fcb5`。相对生产构建仅修改采集工具，生产Java、Java单元测试、构建输入和包内资源逐字节保持；复验使用同一个JAR。
- [下载 yuanxian-0.134.1.jar](https://assets.ishiki333hhh.win/previews/drops-follow-mod-0.134.1-20261116/yuanxian-0.134.1.jar)，53,568,967字节。
- JAR SHA-256：`963e9fcd275457b12ece34af242b2345366d810a9150f5648d16861502023fc3`。

全部生产Java在复用的High-RAM Colab内重新编译。本次输入与原缓存源码逐项核对后，复用未变的建筑、世界规划等生成结果；资源复制、整理和JAR归档正常执行。与0.134.0相比，资源内容差异仅为版本元数据。当前掉落表、语言资源与源码逐字节一致，JAR和本次服务器实际运行目录的2,444个类及资源完全一致，独立测试探针未进入交付JAR。

## 针对性检查

| 检查 | selected | completed | failed | unrun |
| --- | ---: | ---: | ---: | ---: |
| 云端掉落Java测试 | 19 | 19 | 0 | 0 |
| 本地相关资源测试 | 3 | 3 | 0 | 0 |
| 服务端专项场景 | 12 | 12 | 0 | 0 |

Java测试包含新包内默认值覆盖旧外置表、显式启用与关闭、已有上下文即时切换、无效配置保留快照、外置文件保留、重启来源，以及数量、概率和场景规则。专项服务器检查使用既有自有测试世界的独立副本，通过真实死亡回调夹具核对事件列表和落地物品；本次未运行客户端或玩家映射攻击输入。

| 服务端场景 | 结果 | 原始通过记录 |
| --- | --- | --- |
| 候选JAR与运行目录一致 | 通过 | `native-3/report.json` |
| 初次启动默认使用包内规则并导出 | 通过 | `native-1/report.json` |
| 默认忽略既有外置表及无效外置表 | 通过 | `native-1/report.json` |
| 开关即时改变已有怪物的实际掉落 | 通过 | `native-2/report.json` |
| 无效开关保留来源与当前快照 | 通过 | `native-2/report.json` |
| 外置覆盖在保存重启后保留 | 通过 | `native-3/report.json` |
| 包内规则重启后继续忽略旧表 | 通过 | `native-3/report.json` |
| 目标与场景查询 | 通过 | `native-3/report.json` |
| 已有怪物应用重载规则 | 通过 | `native-3/report.json` |
| 无效掉落表保留当前快照 | 通过 | `native-3/report.json` |
| 普通权限拒绝重载 | 通过 | `native-3/report.json` |
| 恢复包内规则并导出 | 通过 | `native-3/report.json` |

## 保留的失败与复验

首轮在CSV检查中断：新增用例已把山碑守卫设为替换掉落，采集器仍要求其包含继承表。默认导出继续核对山碑守卫的原生表；外置替换用例改用仍处于继承模式的荆背獾核对原生概率表达式，并额外检查山碑守卫只导出配置的两颗钻石及空继承表。

第二轮已通过开关切换、来源导出和无效开关保留快照；重启后数量检查读到事件内两颗钻石、地面四颗钻石。多出的两颗来自前一用例随世界保存的物品，重启后的探针没有上一采样区域的内存记录。采集器在每次目标区块进入实体运行状态后清理既有物品，并明确断言采样区为空，再执行死亡回调；事件与落地物品必须相等、具体掉落数量必须精确匹配的断言保留。

第三轮复验中断项和剩余场景，通过后逐项汇总。前两轮的原始失败、日志和逐项通过结果全部保留，`case_evidence` 标明每项采用的通过记录。测试工具修正没有改动生产代码或重建JAR。

六次自有服务器运行均正常保存停服，存档锁及游戏/RCON端口释放，本任务没有残留活动Java进程。Colab会话按约定保留；未操作用户电脑上的游戏或存档。

## 可复查记录

- [检查汇总](https://assets.ishiki333hhh.win/previews/drops-follow-mod-0.134.1-20261116/acceptance.json)、[完整证据档案](https://assets.ishiki333hhh.win/previews/drops-follow-mod-0.134.1-20261116/drops-follow-mod-evidence.tar.gz)、[JAR回读](https://assets.ishiki333hhh.win/previews/drops-follow-mod-0.134.1-20261116/jar-readback.json)、[SHA-256清单](https://assets.ishiki333hhh.win/previews/drops-follow-mod-0.134.1-20261116/SHA256SUMS)。
- 证据档案SHA-256：`312c6b5d0c8411b35058805952c1a3003518b467e6d578cf37465b9e8f8dd396`。
