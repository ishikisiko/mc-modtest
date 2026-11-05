# Waystones 灵石袋适配验证（0.114.1）

本次为 Waystones 的标准物品费用接入灵石袋，价格仍由 Waystones 配置决定。服务器合计散装灵石与袋内精确物品余额，先规划后提交；余额不足不扣款，传送失败退回本次实际付款。不会自动兑换品级，也不会覆盖玩家已有配置。

## 源码和交付身份

- 基线：`9c61db58112dac63261855403258a5447a9f0790`。
- 最终运行时代码与 JAR：`ae8b62fc4cc8ec1454db221c0cd97e1c9a2548d5`。之后仅修改定向检查工具与说明，未改变打包输入。
- 菜单交互检查脚本：`6d76b0d42`；失败夹具和局部复验工具：`b65a06679`。单元测试支付代码来自 `61810c50d54bbde62ea0fa544a1ad5d63a4d3aa4`，至最终运行时代码没有变化。
- Minecraft `1.21.1`，NeoForge `21.1.233`，Java 21；Colab CPU 会话 `waystones-20261105`，隔离目录 `/content/mc-waystones-01141`。

| 文件 | SHA-256 |
|---|---|
| `myvillage-0.114.1.jar`（54,287,214 字节） | `eb20eb4b58427a2f137e9a24f0c84e1fe3b97f7d81a6f479729805c9afd3d750` |
| `waystones-neoforge-1.21.1-21.1.46.jar` | `ca76a4457160e8353b1f013a21ab377b264141f466d84ec8952a123058b8af78` |
| `balm-neoforge-1.21.1-21.0.67.jar` | `eaae887582a67a02f55a970ec2d7353f1886a99924ed6144aa4d898266606c34` |
| 被 Git 忽略的 `exmod/mod_block_catalog.json` | `56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24` |

云端解析到的第三方 JAR 与本地已下载文件哈希相同。源码归档另配精确 PAL 根 JAR 和上述 catalogue，未借用不明来源缓存。

## 必要检查

| 检查 | selected | completed | failed | unrun |
|---|---:|---:|---:|---:|
| 支付及相邻回归单元测试 | 57 | 57 | 0 | 0 |
| 正常资源处理和 JAR 构建 | 1 | 1 | 0 | 0 |
| 最终可选加载修复的增量 JAR 构建 | 1 | 1 | 0 | 0 |

57 项测试包括新支付测试 13 项、库存原子扣除 9 项、灵石材料兼容 6 项、灵石袋 18 项、品级算术 2 项和主动兑换 9 项，无跳过。覆盖大整数余额、只读报价、暂停与多袋、短款、失效快照、精确物品及组件、退款幂等、库存被其他事件修改、移动袋、退款溢出、副手以及原版嵌套容器。

单元测试通过 `tools/waystones_smoke/unit.init.gradle` 直接读取当前源码资源，单独运行时跳过 `processResources`。之后执行正常资源处理与 `jar -x generateAllStructures`，沿用仓库已有的未变更通用结构输出；该构建成功耗时 1022.745 秒。最终修复仅改变可选加载类，资源输入相同，增量打包复用这次资源产物。未运行全量 release。

JAR 的 ZIP 完整性、版本、9 个适配类与原生运行所用编译类的逐字节一致性均通过；袋、品级、下品标签资源与测试输入一致。交付包没有第三方 `net/blay09/` 类，没有开发探针，也没有修复前遗留的匿名渲染类。

## 原生服务器和客户端

`native-r4/without` 在未安装 Waystones 和 Balm 的环境启动专用服务器与真实客户端，客户端成功进入服务器，结果为 `passed`。客户端可选接口提前加载的问题已经通过实际启动复验。

`native-r5/with` 的真实菜单支付为 `passed`：在生存模式放置暂停收纳的灵石袋，余额为 `10000000000000000000000000000000000000017`，没有散装灵石。通过映射鼠标右键打开 Waystones 菜单，点击列表后玩家由 `(0.5, 80, 2.5)` 到达 `(64.5, 80, -0.5)`，余额精确变为 `10000000000000000000000000000000000000016`。RCON 只用于夹具和读回；本次传送由客户端菜单点击触发。

该轮随后在开发者失败夹具处中断，因此 `native-r5` 整轮仍保留 `failed`；仅复用已完成的 `mapped_payment=passed`，不将未运行的失败探针计为通过。

`native-r6/with` 仅选择 `--failure-only`，开发者失败探针为 `passed`，本轮菜单支付保持 `unrun`。原生同步接口实际形成一次性支付回执后，因目标维度不存在返回 `WaystoneTeleportError.InvalidDimension`，原袋恢复为扣款前的完整物品及组件。重复 Complete 通知不重复退款；空袋拒绝付费传送且位置不变；两颗钻石的普通费用继续使用原生扣款。

| 最终复验范围 | selected | completed | failed | unrun | 证据 |
|---|---:|---:|---:|---:|---|
| 无 Waystones 的专用服务器与客户端启动 | 1 | 1 | 0 | 0 | `native-r4/without` |
| 客户端菜单付费传送 | 1 | 1 | 0 | 0 | `native-r5/with` 的 `mapped_payment` |
| 已扣款失败退款及相邻 API 探针 | 1 | 1 | 0 | 0 | `native-r6/with` 的 `developer_failure_probe` |

上述结果来自三个范围明确的运行记录。前两项运行时代码没有变化，局部复验未重复它们，也未修改已失败的历史结果。

## 保留的失败与修复

1. 初次和第二次构建在宗门资源导出阶段因归档缺少被忽略的 catalogue 失败，尚未运行测试。上传本地精确输入后重新运行；没有修改导出断言或生成结果。
2. `native-r3` 在未安装 Waystones 的真实客户端启动时出现 `NoClassDefFoundError: net/blay09/mods/waystones/client/requirement/RequirementRenderer`。将渲染接口和匿名类移至没有自动订阅注解的独立实现类后，重新验证两侧可选加载。
3. `native-r4` 已在带依赖客户端读到袋余额并打开菜单，随后截图工具因菜单占用鼠标而拒绝采集，付费点击与失败探针未运行。修复仅调整菜单截图入口，原游戏输入检查及金额、位置断言保持不变。
4. 局部重跑前发现采集环境缺少 ImageMagick，安装后确认 `import`、`convert` 均可用。未将环境准备失败计为玩法通过。
5. `native-r5` 的菜单付费传送通过，但拆除目标方块未能使同步 API 返回失败，触发 `Removed target unexpectedly teleported`。核对固定版本字节码后，将夹具改为在创建费用上下文后，通过公开的 `MutableWaystone` 接口指定不存在的目标维度。要求返回精确的 `InvalidDimension`，并检查已形成一次性支付回执和原袋完全恢复；没有删掉或放松退款断言。

原始构建日志、`native-r3`/`native-r4`/`native-r5` 失败结果及最终复验分别保留。证据归档 SHA-256 为 `5592c41539cf56b5154656ef1042f95a9f1f08c1cfbc70a537b1f0b13850dac6`，包含 81 份日志、结果或截图及 6 份 JUnit XML。原生场景使用新建隔离世界，不读取或修改玩家存档。测试服务器、客户端和虚拟显示均已退出；Colab 会话保留，等待用户决定是否关闭。

## 使用与未观察范围

安装上表对应 NeoForge 依赖，参考 [Waystones 配置示例](../../config-examples/waystones-common.toml)。示例仅从传送石出发收费，每次一枚下品灵石；去掉来源条件可对其他方式收费。原 XP 费用项需自行移除，否则会同时收费。修改后重启游戏或服务器，已有配置不会被自动改写。

玩家电脑安装、玩家外观与手感验收、声音、FPS 和多人场景均为 `not_verified`。本次软件渲染客户端用于加载、实际菜单操作和费用状态核对，不作为上述接受结论。

交付：[MyVillage JAR](https://assets.ishiki333hhh.win/waystones-spirit-bag-0.114.1/ae8b62fc4/myvillage-0.114.1.jar)、[收费配置](https://assets.ishiki333hhh.win/waystones-spirit-bag-0.114.1/ae8b62fc4/waystones-common.toml)、[原始证据](https://assets.ishiki333hhh.win/waystones-spirit-bag-0.114.1/ae8b62fc4/evidence.zip)。
