# 阡陌交通两城桥接实验

这是显式启用的开发者实验，不会成为 MyVillage 的生产依赖，也不修改城镇、道路生成器或保护规则。它使用指定的 RoadWeaver 原始发布包，在新的修仙世界中选择距离最短、共享可通行州界的普通省城入口对。

`experiment.json` 是依赖身份、种子、模式与实验预算的唯一配置来源。默认分别运行 `COARSE_CORRIDOR` 和 `FULL_REGION`，不启用 OpenCL；原版结构自动发现、路旁聚落和初始小屋关闭，以隔离两城连接。RoadWeaver 的材料、桥梁与铺路实现保留原包行为。

省城的 `entries` 还包含府邸与望楼定位点。实验只选配置声明的实际外部门点，不能把整个入口列表都当成城门。

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
