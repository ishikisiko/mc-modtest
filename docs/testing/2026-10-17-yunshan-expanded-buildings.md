# 云山四栋放大建筑：实机建造与蓝图导出

本轮把主入口净高从 2 格提高为 3 格，扩大开间、进深和屋顶，并补充室内设施。
使用独立样板世界，未修改原 `dev1` 或此前候选。原资料 140 个文件的 SHA-256
全部与 `out/preview/yunshan-inn-v1/baseline-sha256.json` 一致。

| 建筑 | 源版本 | 蓝图尺寸 X/Y/Z（含空气） | 样板世界最小角 |
|---|---|---|---|
| 百草堂 | apothecary_v2 | 19/18/17 | 0, -61, 0 |
| 百炼铺 | smithy_v2 | 19/20/23 | 48, -61, 0 |
| 问道院 | initiation_hall_v2 | 19/20/23 | 0, -61, 48 |
| 云栖客栈 | south_gate_inn_v4 | 19/24/23 | 48, -61, 48 |

药铺增加药柜、诊榻、配药与后院区域；铁匠铺保留敞开锻打棚，增加工具储物和双高炉；
问道院补充书架、讲席、桌案与庭院；客栈提供四间客房、两格宽楼梯、侧廊与后厨。
家具和功能方块使用正常游戏尺寸。放大版超出旧城地块，需要重新规划后才能安装。

## 实际证据

- 固定源：`74995259ac1d83c0e57f0f6f705894ba0bd0bf05`。
- Modal preset：`yunshan-buildings`；成功运行：`20261016T220345Z-422450c3`。
- MyVillage `0.58.4`；本轮没有修改 mod 运行时或打包资源，构建用于云端实机启动。
- 云端 focused unittest、四栋离线生成检查、jar 构建与实际客户端采集全部通过。
- `runtime/evidence.json` 的 18 个检查全部通过，六个 selected 阶段全部 completed：
  `place_four`、`mapped_entries`、`mapped_inn_stair`、`mapped_facilities`、
  `structure_block_save_four`、`world_save`。
- RCON 用于放置模板与服务器状态读取。四个入口、客栈楼梯使用实际 W 键通行；
  炼丹炉、炼器鼎、测灵石碑与传承石碑使用实际右键。前三者观察界面，传承读取已学功法。
- 四份 NBT 来自结构方块界面的 SAVE 操作，保留原始字节；SCHEM 从游戏 NBT 转换，
  逐格比较结构状态并核对有类型的方块实体数据。蓝图 ZIP、世界 ZIP 的 CRC 均通过。
- 已下载并核验产物，最终 Sandbox `termination=confirmed`。

实际证据位于 `reports/modal-runs/20261016T220345Z-422450c3/artifacts/`，
便于人工查看的副本在 `out/preview/yunshan-functional-v2/runtime/`。
这两个目录均为忽略的生成输出，不提交到 Git。

## 重试记录与耗时

失败记录均保留，每次 Sandbox 均确认终止；没有本机 Minecraft 回退。

| Run ID | 结果与原因 | 修复 |
|---|---|---|
| 20261016T214904Z-566fba5a | fresh options 缺省键位未显式列出，采集器拒绝 | 隔离客户端启动前固定 forward/use 映射 |
| 20261016T215141Z-b77a9cba | 整数 TP 坐标自动偏移半格 | 发送小数坐标并补回归测试 |
| 20261016T215440Z-51f136a2 | 成功回显是 Loaded template，原检查只接受 Placed | 检查实际地基方块 |
| 20261016T215819Z-b6ca134c | 四栋通行和楼梯通过，炼丹交互未打开界面 | 后退至正常交互距离、等待画面稳定再右键 |
| 20261016T220345Z-422450c3 | 全部 selected 阶段通过 | 产物已下载，云环境已终止 |

成功运行总计 237.767 秒；创建 2.343、上传 13.009、远端工作 216.922、
下载 4.423、终止 1.069 秒。远端步骤：测试 0.472、离线生成 2.703、
jar 构建 30.255、实际游戏采集 180.766 秒。这些是测量的执行耗时，不代表模型思考时间。

## 人工交付与边界

- [实际游戏画面与逐栋文件](http://43.156.135.198:8765/yunshan-functional-v2/runtime/)
- [四栋游戏蓝图 ZIP](http://43.156.135.198:8765/yunshan-functional-v2/runtime/yunshan_game_blueprints.zip)
- [独立样板世界 ZIP](http://43.156.135.198:8765/yunshan-functional-v2/runtime/yunshan_showcase_world.zip)

样板世界使用现有 MyVillage 0.58.4 配套环境打开。四栋入口朝北；SCHEM offset 为零，
最小角对应上表。世界为固定白天、禁自然刷怪的创作检查场，世界模拟已暂停。
未接入商人、委托、收费住宿，也未替换原城。云端使用 llvmpipe 软件渲染；
玩家外观、手感、音效、FPS、多人及自然遭遇密度仍为 `not_verified`。
