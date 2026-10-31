# 阡陌交通两城桥接实验

这是显式启用的开发者实验，不会成为 MyVillage 的生产依赖，也不修改城镇、道路生成器或保护规则。它使用指定的 RoadWeaver 原始发布包，在新的修仙世界中选择距离最短、共享可通行州界的普通省城入口对。

`experiment.json` 是依赖身份、种子、模式与实验预算的唯一配置来源。默认分别运行 `COARSE_CORRIDOR` 和 `FULL_REGION`，不启用 OpenCL；原版结构自动发现、路旁聚落和初始小屋关闭，以隔离两城连接。RoadWeaver 的材料、桥梁与铺路实现保留原包行为。

省城的 `entries` 还包含府邸与望楼定位点。实验只选配置声明的实际外部门点，不能把整个入口列表都当成城门。

连接只提交给原生队列；不同时调用立即生成入口。`probe.json` 同时保留请求模式与上游会话的实际模式，避免将回退模式当成所请求模式。

## 在 Colab 中执行

先准备与固定源码一致的 Minecraft／NeoForge 开发环境、PAL 根 JAR 和生成资源。重型运行遵循仓库工作流。本实验不要求构建生产 JAR。

```bash
python3 -m unittest tools.roadweaver_probe.test_audit -v
python3 tools/roadweaver_probe/run.py \
  --jar /content/roadweaver-neoforge-2.3.2-1.21.1.jar \
  --source <固定实验提交> \
  --out /content/roadweaver-evidence
```

也可用 `--mode COARSE_CORRIDOR` 或 `--mode FULL_REGION` 单独复验。每次运行创建独立游戏目录与世界；Gradle init 脚本只为本次运行加载实验 Java 和 RoadWeaver，禁止将实验源码打入生产 JAR。

需要观察实物时增加 `--capture`；它使用 Xvfb／软件 OpenGL 的真实旁观客户端采集两端城门和道路中段，结果为 `captured_for_review`，不代替玩家外观或行走验收。

观测强制选择主世界，并逐张读回玩家实际维度和相机位置，防止首次登录的阡镇秘境自动传送污染截图。`observe.py --help` 可补采本实验已经关闭的世界；它只接受本工具目录下的既有实验世界，不重新请求道路。

如已拥有 `experiment.json` 指定的同源发布包，可以先恢复其打包资源到开发运行目录，再传入 `--runtime-jar`。此时跳过资源重生成，并在每次启动前逐文件比较生产类与资源；差异会直接阻止启动。它是同源资源复用，不是一次新的资源生成或发布构建证明。

服务器的 `roadprobe prepare|generate|bake|status` 是管理员采集入口。`prepare` 只读城门和地形；`generate` 调用 RoadWeaver 原生 API；`bake` 请求声明路幅所需的有界区块并读取实物。采集器不手工铺路、不清障、不绕过城市预留区。

## 读取结果

- `result.json`：每种模式的 selected／completed／failed／unrun；completed 仅表示实验采集完成。
- `probe.json`：源码身份、世界计划、入口、原版快速高度与自定义生成器高度、原生连接状态、耗时。
- `roads.json`：原生道路记录、每段完整声明路幅、目标高度、跨州关系和桥梁区段。
- `columns.json`：完整声明路幅的实物读数；包含城市保护范围和配置高度窗口内的方块、碰撞与液体信息。
- `audit.json`：所有缺失或阻挡坐标、遗漏／重复采集位置、路面连通分量、入口至路面距离。
- `server.log`、`commands.json`、`cleanup.json`：原始运行、准备命令与所属进程退出情况。

`pavement_connectivity_pass` 仅判断材料匹配路面在整数格上的几何连通。它不证明桥墩支撑、精确半砖碰撞、实际玩家行走、外观、手感、FPS 或多人体验。这些未观察项保持 `not_verified`。RCON 在这里仅用于实验准备与读回，不是映射玩家输入。

材料识别也会匹配城市原有同材质铺装，因此预留区内的匹配不单独证明是 RoadWeaver 铺设。高度窗口以外的候选没有被检查。失败列表不按比例截断，也不把原生 `COMPLETED` 状态等同于实体道路已连通。

上游：[RoadWeaver](https://github.com/shiroha-233/RoadWeaver)、[发布包](https://modrinth.com/mod/roadweaver/version/D1y11uNH)。本实验仅调用发布包，没有移植上游源码。

## 2026-11-01 实验结果

生成与几何采集固定源为 `6f2d1465a6d83debdbd2498aec71108a86b99233`，生产源为 `2c4a48808497ca9f768865d669c49f561236f9bb`（MyVillage 0.105.4）。1,948 个生产类与 30,333 个资源逐文件匹配同源发布包。截图补采源为 `aab2ed4ba2135a69fd4b063b4de63bb529f55cf0`；之后仅整理说明和修正客户端合法模拟距离，没有再次生成道路。

种子 `20261101` 选择南疆 `gate` 与西漠 `south_gate`，直线距离约 4,598 格。两个请求模式均完成采集：`selected=2 / completed=2 / failed=2 / unrun=0`；失败指严格的匹配铺装连通检查，不是服务器启动失败。两组均生成一个原生 `COMPLETED` 连接，只跨允许的州界，均完整读回 31,175 个位置，无遗漏、重复或额外采样位置。

| 请求模式 | 实际模式 | 规划耗时 | 区块及实物读回耗时 | 未匹配铺装 | 匹配铺装但被挡或有液体 |
|---|---|---:|---:|---:|---:|
| COARSE_CORRIDOR | COARSE_CORRIDOR / CPU | 154.30 秒 | 92.91 秒 | 3,549 | 16 |
| FULL_REGION | COARSE_CORRIDOR / CPU | 132.92 秒 | 97.11 秒 | 3,602 | 10 |

第二组明确回退：`FP64 OpenCL GPU is unavailable`，因此没有验证真正的全域精采样，也不能把两次耗时差解释为精采样性能差。沿城门直线的 65 个点中，快速采样与生成器高度最大相差 61 格，平均相差 27.69 格；这不是对实际道路每个位置的高度误差测量。

默认组有 3,156 个未匹配铺装位置位于城市保护范围。补充地面诊断发现，3,214 个未匹配材料的位置仍有接近目标高度、具备地面碰撞和两格净空的候选表面；匹配材料缺失不等于不能行走。两端至已识别同材质铺装最近约 16.19 和 198.95 格，且城市原有同材质铺装可能被匹配。野外确实出现了石砖道路，但不能据此宣称已连续接门或通过玩家实走。

三张正式观察图均已核对 `minecraft:overworld` 与实际相机位置，状态为 `captured_for_review`。桥梁支承、真实玩家行走、外观接受、FPS、多人和八城完整路网未验证。原生服务器／客户端与 Xvfb 均结束，专用 Colab 会话停止后再次确认无活动会话。

接入判断：保留 MyVillage 对州界、城市预留和城门向外走廊的所有权；优先复用 RoadWeaver 的野外寻路与铺路能力。后续需解决自定义地形采样、城门朝向与外部端口、原有城内铺装的衔接，不能用覆盖城市地坪或放松保护规则代替。

原始日志、全部失败坐标和有读回的截图：[实验交付](https://assets.ishiki333hhh.win/experiments/roadweaver-bridge-20261101/)。初始资源元数据缺失、内部定位点误选、立即生成与原生队列并发的调查记录均保留在证据包中，没有计入正式完成结果。
