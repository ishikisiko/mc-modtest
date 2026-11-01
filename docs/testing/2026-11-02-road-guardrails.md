# 青石官道危险段护栏验证（0.105.7）

用户选择在临崖、临水和高路基设置同材青石护栏。平地保持开敞；外侧石缘承载方石柱和单道横梁，城外完整五格通行面保留。南疆原有门洞仍为三格，西漠为五格，城内通道只读。

## 固定输入与交付

- 最终生产源码：`e61458be79fba6ccaf59355090bb544f632d7882`，全部 27,127 个 Git blob 已核对。
- 完整源码归档 SHA-256：`108a5798e4d900d2d551be830be7f66bd0e6bca146d7aa6f7f2ed24169ac17fd`。
- 最终 JAR SHA-256：`3666d88ba79ae98b694adc6c3e1a843781f0637b6780e28f89251ed4cd87016e`；32,502 个非 manifest 条目、1,980 个生产类，与实际启动目录逐项相等。
- 构建在 Colab High-RAM CPU 进行，实际分配 8 核、内存记录 `MemTotal:       53467180 kB`。生产类首次重新编译；复用的依赖缓存与生成资源身份见 evidence。
- 未改动的生成资源继承 0.105.6 JAR `81f16bd94bdc69c1c83e0d19f7ecae29a293cdf91c90d9d7b2d5a8855c3d9fc7`；本次重新生成并检查道路资源。
- [下载最终 JAR](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/myvillage-0.105.7.jar)、[完整源码](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/source.tar.gz)、[原始证据](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/evidence.zip)、[机器摘要](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/summary.json)。

## 检查范围与结果

| 候选 | Python 道路资源测试 | Java 道路测试 | 原生场景 |
|---|---:|---:|---|
| 道路实现 `275d1e14a` | 7 | 38 个实际 JUnit 方法，失败、错误及跳过均为 0 | selected 11 / completed 11 / failed 0 / unrun 0 |
| 最终模型 `e61458be7` | 8 | 按下述字节一致性复用 | selected 5 / completed 5 / failed 0 / unrun 0 |

两候选之间仅改变 162 个护栏模型的显式 UV 和标准物品视角继承，1,980 个生产类及全部服务器数据字节相等。因此复用道路逻辑测试和施工保护证据；最终包重新构建后，另行验证 JAR 与启动目录、同版测试路线完整读回、两个城门的映射行走、两类支撑上的横梁阻挡和原生截图。

首轮原生近景出现杂色，资源检查定位为下移模型默认侧面 UV 超出石材纹理（V=24）。原始失败保留在 `python/uv-before-fix.log`；生成器修正后，仍采用原有的 0–16 纹理范围断言，8 项测试通过。首次逻辑 JAR 为 `2899ab1eb52a34ed69e35b7675841619ef1ffb2720de277753a40b70d0aa8c3c`，最终 JAR 和两次原生结果分别保留身份。

## 隔离测试世界

使用种子 `20261101` 的新测试世界。采集器注入箱子检查拒绝覆盖，并注入黑曜石触发保护避让；重现最终试验线需执行完整 `tools/roads_smoke/run.py` fixture。普通手动规划不包含这些注入物。

- 13 次零写入的障碍避让，最终 110,946 列施工/只读范围，916 个区块；城外 `band=0/1` 通行面、`2` 石缘、`3` 缓坡分别完整审计。
- 1,179 列护栏全部处于外侧石缘，其中完整方块支撑 806 列、下半砖支撑 373 列。
- 最终读回 `failed_columns=0`、`missing_material=0`；护栏列 `verified=true`，不计入可步行宽度。
- 原有城门 113 列地坪和头顶状态与施工前相等；暂停后重启续建、完成后重启不自动重建均通过。模型修正后再次完整读回，路线摘要一致。

## 实际输入与碰撞

两个城门使用 X11 映射 W 键出城，无跳跃且 `autoJump=false`。防坠试走对准两根石柱之间的横梁接缝，持续映射 W 键五秒；服务器读回确认玩家先前进、随后停止，末段位置稳定且未跌落。RCON 负责 fixture 准备和读回，移动由实际映射输入驱动。

| 支撑 | 最终位置相对护栏中心的外向投影（格） | 结果 |
|---|---:|---|
| 完整方块 | -0.425000 | 通过 |
| 下半砖 | -0.425000 | 通过 |

完整轨迹、位置、维度、预期阻挡位置和原始命令见 `native-final/guardrail-walks.json`、`capture.json` 与 `commands.json`。临水规则另有专门纯逻辑用例及离线样段；本次原生试走范围是这条路线上的两类支撑横梁。

## 画面与接受范围

[原生护栏近景](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/guardrail_close.png)、[南疆出口](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/from_gate.png)、[西漠出口](https://assets.ishiki333hhh.win/releases/myvillage-0.105.7-road-guardrails-20261102/to_gate.png) 均为未经编辑的原生 PNG，状态为 `captured_for_review`。[三种可旋转离线样段](https://assets.ishiki333hhh.win/previews/official-road-guardrail-20261101/index.html) 用于比较平地、临崖和临水外观。

已检查原生画面是否正常呈现模型与材质。玩家外观接受、整体手感、全程步行、FPS 和多人仍为 `not_verified`。当前仍是管理员显式触发的南疆—西漠试验线；全部城镇自动联网、桥梁与关隘接入不属于本次交付。
