# 河谷区域离线样板

把地形、建筑入口、道路和基础编译为同一份可导出的区域。当前只有
`river_valley_v1` 配方：溪畔主村、半山院落和松岭驿点。不是新的世界类型，
不会在任何存档自动生成，也没有接入剧情或 NPC。

## 生成

需要 JDK 21、已有 Gson 依赖缓存以及安装 NumPy 的 Python。命令只调用
`javac` 编译纯规划 CLI，不运行 Gradle、Minecraft 或远程服务。

```bash
python -m tools.regiongen.build --seed 20261024 --out reports/regiongen/river-valley-v1
python -m unittest tools.tests.test_regiongen
```

`index.html` 可独立打开，包含实际方块的色块模型、六个相机预设、通路叠加及
总平面。网页链接默认相对目录；对外托管时用 `--base-url` 指定对应的
`https://assets.ishiki333hhh.win/` 路径。不要为它重启既有预览服务。

产物包括 `plan.json`、`report.json`、`preview.json` 和 `river_valley.schem`。
完整 SCHEM 包含空气，局部最小角为零；计划里的绝对最小角与尺寸记录在
`report.json` 的 `export` 中。导出回读逐格比较，不代表已经在游戏中放置。

## 数据与实现归属

- `blueprints/regiongen/river_valley_v1.json`：尺度、山形、水系、布局骨架、
  建筑引用、道路坡度预算、植被与构造调参的唯一 authored 来源。
- `java/RegionPlan.java`：纯 Java 规划核心，输出高度场、建筑范围及旋转、
  实际道路格、台阶方向、支撑标高。当前放在工具目录，不进入 mod jar。
  后续运行时接入可复用此纯核；当前没有声称实现区块生成。
- `assets.py`：只适配既有云山建筑作者的输出。原建筑、屋顶及门窗保持原样，
  归一化后按入口旋转；新区域家族经 `tools/buildgen/groups.py` 登记。
- `scene.py`：生成实心地形、道路、桥、院墙及植被，应用完整建筑模板，
  在最终方块上检查通路、支撑、楼梯方向与模板一致性，再导出 SCHEM。
- `viewer.html`：从同一体素结果拆出地形表面及显式构造进行渲染。
  楼梯、半砖与门等沿用云山预览的模型解释；家具、栅栏等仍为简化模型。

方块地表高度表示首个空气格；道路高度表示支撑方块顶面。楼梯有半格碰撞，
离线检查采用保守的两格净空、打开的门与有方向楼梯模型，不能代替实际玩家碰撞。

## 当前边界

配方的聚落关系、街道骨架和建筑清单是 authored 的；种子改变山形、河道相位与
建筑群偏移。连接道路在直达坡度预算不足时采用有界绕行，地基和道路对周边地形
做有界过渡。这不是能为任意输入自动求解所有布局的通用规划器。

河道首版共用一个水面高度，贯穿有限区域。相邻区域边界协商、水系梯级、
生成状态恢复及 READY 准入仍未实现。新的种子可能因坡度、冲突或挖填预算被明确
拒绝，不会静默削平或放宽检查。没有自动运行多种子搜索来替换请求的种子。

本轮最小检查为固定种子重放、另一种子差异、实际模板完整性、道路障碍／
支撑缺失／楼梯反向／基础空洞的负例，以及完整 SCHEM 回读。
以上是最初离线样板的检查范围。此后固定河谷样板已完成实际放置与存档方块比对，
并于 2026-10-25 获得用户人工验收。自动步行采集只完成 9/16 栋入口往返，
不能将人工接受改写为自动全通过；NPC 寻路、FPS 与其他种子仍未由这次验收证明。

## 固定样板的原生验收

`native_pack.py` 复用云山原有 NBT 切片导出器，把同一体素结果转成独立数据包。
`native_run.py` 只允许显式选择的 Colab 环境，在独立检出内创建新世界；已有世界
会被拒绝。开始前校验输入哈希。分批强加载并放置模板、逐片读回，释放本次票据。跨切片上沿的吊灯由生成器列入
延后装配清单，所有支撑到位后才补齐；仍需通过全体素存档比对。
鸟瞰是明确标记的旁观者相机 fixture；随后单次设置起点，连续步行覆盖各建筑
首层内部、山路和桥。旋转、移动、开门均经所属 X11 客户端的实际鼠标／键盘输入，
自动跳跃关闭。鼠标位移等待服务端角度确认后才发下一次修正，短距离移动先确认
实际蹲姿，松开移动键后等待惯性停止。完整首层入口检查不代表每个客房／楼梯均已验收。

```bash
python -m tools.regiongen.native_pack --plan reports/regiongen/river-valley-v1/plan.json --out reports/regiongen/native-pack
python -m unittest tools.tests.test_regiongen_native
# 以下仅在用户指定的 Colab VM 固定检出中执行，先传入上述 pack 目录：
MC_HEAVY_LOCK=/content/qianzhen-heavy.lock python -m tools.regiongen.native_run --pack /content/river-pack --out /content/river-native-evidence
python -m tools.regiongen.native_compare --pack /content/river-pack --world run-acceptance/combat_capture_world --run /content/river-native-evidence
```

采集器正常关闭录制，单次发送 `save-all flush` 并记录回应，正常停服后打包世界。
保存世界的检查读取全部样板体素和告示牌：方块 ID、朝向、上下半格、含水等属性
保持严格；原版补齐的默认属性和动态邻接形状单列统计。只有实际记录开门输入的门
允许 `open` 改变。报告区分 attempted、completed、failed、unrun，失败录像、日志
和世界不能覆盖。会话文件含 RCON 凭据，不得放入公开证据包。

这些工具提供的是单个固定区域的原生碰撞与落地证据；完整区域生成器接入、相邻
区域、自然分布、NPC 寻路、玩家手感和 FPS 仍需各自的验证。

## 湾口台地集的自然生成

第二份围场坡巷布局使用 `bay.py` 与 `bay_assets.py`，authored 输入为
`blueprints/regiongen/bay_terraces_v1.json`。它保留 14 栋完整建筑，统一求解道路、
门前与交叉点标高，再生成版本化地表与区块覆盖层。

```bash
python -m tools.regiongen.bay
python -m tools.regiongen.bay_assets
python -m tools.regiongen.bay_assets --check
python -m unittest tools.tests.test_bay_terraces
```

普通主世界开启结构生成后可用 `/locate structure myvillage:bay_terraces` 查找。
世界种子控制自然位置和整体旋转，内部布局仍来自已确认配方；旧区块不会回填。
打包、存档和实际证据边界见 [KB65](../../docs/ai-kb/65_bay_terraces.md)。
