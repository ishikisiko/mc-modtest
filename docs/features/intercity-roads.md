# 八城官道路网

0.106.0 新建修仙大陆自动规划全部八座省城的连通主干网。普通七城通过合法可通行州界相连，魔域固定保留一处正式关隘并经该关隘接入。无需另装 RoadWeaver；基础 A* 保留原作者 MIT 声明。

规划在后台读取不可变 WorldPlan，服务器在玩家附近的已加载完整区块逐步施工。全网规划连通与世界中全部路段已经铺完是不同状态；尚未探索的远处区块无需强制生成，接近时继续施工。`status` 显示全部路线、施工区块、受保护区块和失败原因。

```mcfunction
/myvillage roads status
/myvillage roads pause
/myvillage roads resume
```

管理员可用 `build` 逐区块强制加载并批量施工全部待建路段；`network` 或不带参数的 `plan` 用于启动全网规划。默认自动规划失败后保留原因，不无限重试；排查后可再次启动。`plan 起点 终点` 可在现有任务完成后添加直接相邻的合法路线，`select 起点--终点` 选择路线。`export` 输出世界目录 `data/myvillage/worldplan/roads.json`。`check`、`audit` 只读检查当前加载区块的已施工道路，不把未加载路段称作通过。

城外五格中央通行面，另设一格石缘和有界缓坡。保持已选 A 青石官道：青石板中央、青石砖边带、青石砌块石缘；半砖坡道衔接。护栏仅在临崖、临水、高路基等危险边缘设置，不占中央五格。护栏参数及模型仍归 `config/roads/guardrail.json` 和 `tools/gen_road_ports.py`。

八城公共出口从正式蓝图及当前城市 NBT、地形资源导出只读通道，避开原有阻挡。南疆、北原、灵越、中州保留三格出口；其余四城为五格。支持四向朝向，东海从陆侧西门接出。城内不施工、不拆门柱、不改建筑；城市预留边缘仅开放配置限定的城门外接坡范围，可在城外迂回降坡，不进入城市本体。

多条道路共用一份列状态。新增路线锁定已有通行面的高度，交汇处按通行面并集重建石缘及护栏，避免在路口横置石缘。施工前逐区块检查完整路幅；方块实体、未授权材料、已有道路的玩家修改均保留现场并记为受保护区块，其他可施工区块继续推进。`resume` 会重试这些区块。所有世界访问和方块写入均在服务器线程；重启保留已完成区块集合，显式暂停不会因重启取消。

单段与全网预算分别配置，保留城市、聚落、出生区域、州界、水深及土方限制；水面路段使用同材质石桥面、间隔落底桥墩及临水护栏，浅水低门口增加挡水石缘；未实现独立桥楼建筑和路边村落。某一合法边无法形成完整道路时尝试其他可通边，若仍不能全部连通则整个规划失败并保留原因，不发布半张网为全通。开发阶段只支持当前格式，使用新世界检查。

道路输入：`config/roads/prototype.json`，中州正式门洞声明：`config/roads/capital_zhongzhou_port.json`。先生成当前 V11 城市资源到 `build/resources/main`，再运行：

```bash
python3 tools/gen_road_ports.py
python3 tools/gen_road_ports.py --check
python3 -m unittest discover -s tools -p test_gen_road_ports.py
```

未变城市资源可从身份固定的当前 JAR 提取后，使用 owning compiler `tools/province_capitals/game/compile_v11.py --out build/resources/main --refresh-current` 重建规则及预设。正常从蓝图生成同样应用当前 V11 的正式关隘配置。

开发者逻辑和编译检查不代替游戏外观与手感接受；本次按用户要求由玩家实看。历史原型与护栏记录保留在 `docs/testing/`，其结果不作为本版全网验收。
