# A 青石官道 0.105.6 定向验证

用户选择四种离线样段中的 A 后，本版将完整五格青石通行面、外侧石缘及有界缓坡接入正式驿道。南疆门洞仍为 3 格，西漠为 5 格，城内只读。石缘／缓坡在城市预留边界及水域边收口；不能称为全程五格连接或全地形统一十五格施工。

## 固定身份

- 运行源码 `bb19a7526f449427c2dacfc3de4c376e02056364`，逐项核对 26953 个受跟踪文件。
- JAR SHA-256 `81f16bd94bdc69c1c83e0d19f7ecae29a293cdf91c90d9d7b2d5a8855c3d9fc7`；全部 32329 项运行内容逐字节匹配，生产类 1975 个，不加载外部 RoadWeaver。
- 源码归档 SHA-256 `bc55e361ce7e5ea9a3ade07e6397eff08fbc127e92fc7ed075665357a6332b4e`。
- 未修改生成资源复用 0.105.5 JAR `96a07cb10b6e48c1c74a89a65c63dba462cf4ae26e9c351c29a60b9ff6df968c`；新出口／样式和三种半砖模型、配方、掉落、中文名与标签由拥有它们的生成器生成，资源叠加列表在 summary.json。
- PAL 根 JAR SHA-256 `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`。依赖缓存独立恢复，没有复制旧项目生产类。
- 所有重型构建、服务器和软件 OpenGL 客户端运行位于 Colab 的专用会话；种子 `20261101`，隔离新世界。

## 实际结果

- 29 个真实 Java JUnit 方法通过，failed/errors/skipped 均为零；5 项出口／半砖资源 Python 检查通过。
- 原生 selected/completed 为 10/10，failed/unrun 均为零。
- 路线约 7.00 千米；全部施工及保留通道 110696 列，地坪／净空失败 0，期望方块状态不匹配 0。
- 施工列按 band 分开：中央青石板 26017，通行面青石砖边带 12508，外侧石缘 14498，缓坡整地 57560。完整五格覆盖仅使用 band 0/1 独立核对。
- 城内 113 列地坪与三个净空方块状态保持一致；普通 WorldPlanGuard 没有放宽。
- 避障预检 14 次拒绝均保持零施工。`roads check` 成功后仍为 ready；最终完整施工前读回完成后才进入实际铺设。
- 暂停／重启保存并续建全部路带；完工重启后再次完整读回为零失败，build 不重复施工。
- 两处门口使用映射前进键走过城界，自动跳跃关闭，未输入跳跃；服务器实际位置、旋转和维度轨迹在 capture.json。

## 范围和失败记录

初次离线构建缺少 Jade 依赖缓存，在 compileClasspath 解析时失败；原日志保留为 build-offline-missing-jade.log。随后联网补齐依赖，在同一固定源码上构建并通过检查，没有放宽测试。

实装可见青石铺装和外侧石缘；城门前仍受原窄走廊约束，切坡形态没有整体重做。第 5 项 Python 检查对应后续非打包校验器接线，运行时源码保持上述固定源；工具补丁和最终来源见 delivery.json。

截图只覆盖门口和野外局部，远景受采集渲染距离限制。截图为 captured_for_review，方向 A 已由用户选择，实际游戏外观仍未作玩家接受结论。全程徒步、其他种子、多人和 FPS 保持 not_verified。自动八城路网、正式桥梁和额外路边设施不在本次范围。本次未安装玩家电脑、未启动或修改玩家存档。

## 交付

[原生画面](https://assets.ishiki333hhh.win/releases/myvillage-0.105.6-official-road-20261101/index.html) · [JAR](https://assets.ishiki333hhh.win/releases/myvillage-0.105.6-official-road-20261101/myvillage-0.105.6.jar) · [源码](https://assets.ishiki333hhh.win/releases/myvillage-0.105.6-official-road-20261101/source.tar.gz) · [完整原始证据](https://assets.ishiki333hhh.win/releases/myvillage-0.105.6-official-road-20261101/evidence.zip) · [机器可读摘要](https://assets.ishiki333hhh.win/releases/myvillage-0.105.6-official-road-20261101/summary.json)

使用方法见[城际驿道](../features/intercity-roads.md)。旧 0.105.5 数据保持原来的范围，不能作为新增石缘与缓坡的证据。
