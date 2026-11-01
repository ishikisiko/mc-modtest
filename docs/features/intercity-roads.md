# 城际驿道原型

0.105.5 首版只试南疆和西漠，不会在新世界自动修路。无需安装 RoadWeaver；移植的是其 MIT 基础 A*，原作者声明和许可随 JAR 打包。完整原包的自动网络、GPU 采样、桥梁建筑、路边村落尚未移植。

路幅明确分开：城外驿道 5 格，南疆原有门柱之间实际净宽 3 格，西漠门洞 5 格。城内按现有净宽保留和审计，不改门柱；整条连接因此不能称为全程 5 格通道。最初将南疆元数据范围当成 5 格净门洞的原生检查失败，失败记录保留。

本次可复现试验使用种子 `20261101`。新建该种子的修仙大陆测试世界，以管理员执行：

```mcfunction
/myvillage roads plan nanjiang ximo
/myvillage roads status
```

状态为 `ready` 后再执行：

```mcfunction
/myvillage roads build
```

`validating` 会先加载并检查完整路幅。全部预检通过才进入 `building`；完成为 `complete`。遇到箱子、未获准替换的方块、地坪或净空异常会保留失败和已完成区块，不能将 `blocked` 当作成功。`pause` 暂停当前任务，重启后进行中的施工也会暂停；再次 `build` 重新预检并续建。原型每个世界只保留一条路线，适用于隔离的新测试世界。

尚未施工的路线若碰到受保护结构，可再次执行 `plan nanjiang ximo`，把仍存在的障碍及其配置缓冲范围加入避让；移除的障碍会重新核查。重新规划仍需 `build` 才施工。已完成部分施工的路线拒绝换线，避免留下重复道路。

```mcfunction
/myvillage roads pause
/myvillage roads audit
/myvillage roads export
```

`audit` 逐列读回整个声明路幅，包括保留的城门通道，保留地坪、净空、材质以及全部失败位置。`export` 将规划写入世界的 `data/myvillage/worldplan/roads.json`；检查写入同目录 `roads-audit.json`。材质一致和碰撞可通行分别统计，现有城内材料不会强制换成驿道石砖。

## 施工边界

- `RoadAStar` 只读纯地形输入，每一步含中间格和对角两侧验证；搜索预算用尽返回失败，不接受半条路线。
- 跨州路线经过 WorldPlan 连通边界对应的实际世界坐标。规划采用分块的原始值缓存，并由配置限制缓存与搜索规模。
- `WorldRoadPlanner` 使用 WorldPlan 高度，首版只允许直接相邻、可连且无需关隘的两个州；整幅道路都必须位于这两个州。其他城市、聚落、出生保护区保持避让。
- 南向出口来自正式蓝图 `public_passages`。实际门内地坪、台阶和净空先由服务器读取，城内只记录现状。施工永不进入城市本体，不放宽其他模组的 `WorldPlanGuard`。
- 城市预留边缘仅开放声明出口延伸的直线窄走廊。西漠出口横向存在原生错台，门外按每一列原高度约束接坡，保留城内台阶；外部完整路幅和相邻衔接均限制为半砖坡度。超过配置土方、水深或不相容的门口高差即失败。
- 后台线程只规划；区块读取、方块预检、施工和保存都由服务器线程执行。预检后发生变化会停止当前施工；进度写入 `myvillage_roads.dat`，重启不自动续写。
- 允许清理的天然植被、地表方块由配置列出。箱子等方块实体、铁轨和未声明的营造方块保持保护；预检发现的障碍仅用于之后显式重新规划，不直接清除。

## 配置与检查

参数仅改 `config/roads/prototype.json`；门洞几何来自正式城市蓝图。生成资源只通过以下命令更新：

```bash
python3 tools/gen_road_ports.py
python3 tools/gen_road_ports.py --check
python3 -m unittest discover -s tools -p test_gen_road_ports.py
```

Java focused tests 为 `com.example.myvillage.worldplan.roads.*`；云端原生场景由 `tools/roads_smoke/run.py` 执行。路幅检查、门口局部映射步行、截图和玩家整体接受度是不同证据，报告分别说明。构建可复用输入一致的现有生成资源，必须记录来源 JAR 和新源码身份，不能把复用资源说成本次重新生成。
