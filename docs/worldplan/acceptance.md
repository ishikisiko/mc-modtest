# 修仙大陆：规则、生成链路与验收

> 0.64.0 新世界默认 `planned_v4`，完整 D1 城市为 238 个区块列模板，规则为有界压缩字符串。创建入口不变，具体兼容与使用见 [KB58](../ai-kb/58_yunshan_living_city.md)。以下 V1/V2/V3 尺寸、坐标和历史验证不代表 V4 的当前取值；验收工具已更新，但本次未宣称实际服务器场景通过。


目标版本：Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21 / MyVillage 0.59.1-fix1。

0.59.1-fix1 新建世界使用 `planned_v3`：以 V1 原群系为主体，仅在不相容交界附近 32 格内调整植被，保留原雪林和暖地内部。参数在 `blueprints/worldplan/rules_v3.json`；探测只读原群系，过渡结果不参与下一次探测，因此不会向内部扩散。旧 `planned_v1` / `planned_v2` 存档保持各自算法和资源；查看修复请另建同种子世界，不能删旧区块或修改 `level.dat` 迁移。V2 的全域气候混合会挤走原雪林，本版不再将它用于新世界。

V3 延续 `clear_spawn_canopy`，保护出生安全平台上方整列，防止邻树树冠跨入；首次建世界时将 `spawnRadius` 限制在平台半径以内。仅影响新世界创建和生成，不清理玩家建造，也不覆盖旧世界的游戏规则。

本功能是可选世界预设 `myvillage:cultivation`。它不是把一张图片贴到原版地图上，也不是玩家接近后由命令整城重建。世界种子和冻结规则先生成可查询的宏观规划，实际新区块再读取同一份规划。

## 1. 如何创建并查看

在装有当前分支 JAR 和项目既有依赖的独立测试实例中，新建世界，在“世界类型”选择“修仙大陆（种子蓝图）”。建议第一轮使用种子 `20261017`，开启生成建筑和管理员命令。不要将现有存档的 `level.dat` 改成这个生成器。

本 JAR 不是整合包。保留项目既有 GuideME、PlayerAnimationLibrary 等必需依赖；PAL 使用 `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar`，不是名称相似的旧动画库。依赖详情以 `src/main/resources/META-INF/neoforge.mods.toml` 为准。

专用服务器必须在首次创建该世界前设置：

```properties
level-type=myvillage\:cultivation
level-seed=20261017
generate-structures=true
```

新世界启动后自动生成：

```text
<世界目录>/data/myvillage/worldplan/
    worldplan.json     完整种子、冻结规则、州域图、边界、城市与出生点
    atlas.html         可直接用浏览器打开的交互蓝图，无外部 CDN
    atlas.svg          可缩放的宏观地图
    terrain.png        地形场
    regions.png        区域身份
    qi.png             灵气场
```

先执行：

```mcfunction
/myvillage worldplan info
/myvillage worldplan city
/myvillage worldplan at 4000 0
/myvillage worldplan at 100000 -100000
/myvillage worldplan export
```

`info` 返回当前种子、规则和规划 SHA-256、城市西北角、尺寸、出生点以及文件输出目录。不同种子的地点不同，不要照搬别的种子的传送坐标。

V1 默认规则、种子 `20261017` 的城市西北角为 `(448, 92, 16)`，平面中心为 `(488, 72)`，出生点为 `(381, 75, 1273)`。可先在旁观模式观察：

```mcfunction
/gamemode spectator
/tp @s 488 155 172 180 25
```

离线提前看种子，不启动 Minecraft：

```bash
python3 tools/worldplan/preview.py --seed 20261017
python3 tools/worldplan/preview.py --seed -123456 --out out/preview/worldplan/negative
# 没有 Gradle 缓存时显式提供已有 Gson；工具不会偷偷下载。
python3 tools/worldplan/preview.py --seed 20261017 --gson /path/to/gson-2.10.1.jar
```

这是实际 Java 规划器的 CLI，不是另写一套近似 Python 地图算法。`worldplan.json` 保留完整 64 位种子；HTML 下载原始 JSON，不经 JavaScript 数字重写种子。

## 2. 当前规则

规则源码是 `blueprints/worldplan/rules_v1.json`，街区源码是 `blueprints/worldplan/yunshan_placeholder_v1.json`。构建时合并原有 `worldgen/region_profile` 目录与云山四栋建筑的源蓝图，输出独立的生成资源目录。不要手改 `build/generated/worldplan/resources`。

| 项目 | V1 规则 |
|---|---|
| 核心范围 | 原点半径 4000 格，直径 8000 格；不是世界边墙 |
| 州域数量 | 每种子 5 至 7 个，必有中州与魔域 |
| 布局 | 随机州域集合、旋转、次序、两层中心分布；中州中心可在原点附近偏移 |
| 实力 | 中州最高；普通通行骨架每边差值 2 至 5，额外连接也检查阶差 |
| 身份边界 | 连续噪声扰动后的最近中心分区；所有消费者共用变换 |
| 图与地理 | 先求真实共边，再建立连通骨架，不把不相邻的州画成一条假陆路 |
| 屏障 | 部分州界抬成山脊，部分压成水道；魔域最多一个天然关隘 |
| 外域 | 2400 格尺度的抖动格点，查询固定 5×5 邻域，稳定 ID `outer/x/z` |
| 核心/外域衔接 | 3680 至 4320 格内混合地形和灵气，不形成圆形断崖 |
| 高度 | 地表第一空气格约束在 Y=26 至 248；海平面 63 |
| 出生 | 最弱普通州中寻找干燥较平地点，保留小型安全平台；必要时形成小岛 |
| 建筑 | 当前只有一处主城占位街区，放在中州；不是每州都有完整城市 |

外域不按距离无限提升难度，也不预先生成无限个对象。负坐标采用向下取整；相同种子、坐标和冻结规则给出相同结果，探索顺序不参与抽样。这里的“无限”指持续程序生成到 Minecraft 自身的坐标与世界边界约束内，不宣称突破原版边界。

高阶州不封锁低阶玩家进入。山海屏障是物理地形，不是无法破坏的隐形墙。地图金色虚线代表主要交通关系，**不代表已经修建了跨州道路**。城内连接街道会实际生成。

后续放大新世界时，需要一起校准 `core_radius`、`core_blend`、内外圈半径与相关选址范围，而不是只改一个 4000。现有存档继续使用冻结旧值，不跟随源码变化。

## 3. 粗蓝图为什么能控制真实区块

调用链：

```text
新世界选择 myvillage:cultivation
  → PlannedChunkGenerator.createState(seed)
  → WorldPlan(seed, frozen_rules)
  → 区域形状 / 边界 / 地形与灵气场 / 城市保留区
  ├─ WorldPlanExporter：按网格采样 → JSON、SVG、HTML、PNG
  └─ Minecraft 区块生成
       BIOMES：按相同规划选择表层群系；地下保留原版洞穴群系
       NOISE：原版噪声与地下基础 → 在当前区块内实现目标地表
       SURFACE：原版表层处理
       CARVERS：原版雕刻，城市缓冲区排除
       FEATURES：原版和允许的模组装饰 → 当前城市切片
       FULL：普通可游玩区块，之后不再用蓝图重建
```

规划不是所有方块的清单。它保存“此处属于哪州、目标地面在哪里、哪里是山海边界、哪里保留城市”等较低频约束；洞穴、植被、细部资源和其他结构仍由后续生成阶段产生。

舆图的地形高度指**装饰之前的第一空气格**，不是屋顶、树冠或实时玩家改建后的高度。海面也不等于海床。宏观地图采用较粗采样，不能拿它某一像素判断一格宽的小路。开发者 `probe` 则在真实 NOISE 区块中逐列比较地表与同坐标规划值。

实际区块不向“已生成的邻居”猜测地形，也不从玩家位置扩散随机数。目标地形来自全局连续坐标场，所以陌生区块可以独立重建。世界生成路径没有 `getChunk(FULL,true)` 或整城强制加载；只有明确命名的管理员验收命令会有界请求区块。Minecraft 自己为生成依赖而处理邻域属于原生流程，不代表预生成整个文明圈。

V1 保留了原版地下噪声与部分洞穴，但不是完整地质模拟：新增山体上部主要通过地表密封层和填充形成。地形高度连续，群系身份是离散选择，不能把本版描述成所有生态色彩都已完美渐变。

## 4. 云山占位街区与跨区块安全

可用源资料为 `blueprints/yunshan/functional_buildings_v1.json` 以及其中四栋建筑。完整古城的外部 D1 导出文件没有随 Git 仓库提供，所以本版明确叫“云山古城·功能街区占位”，不是完整古城复刻。

保留了原有客栈、铁匠铺、药铺、引仙堂的相对坐标、旋转和高程关系；新增台地、街道、边墙和南北入口。平面 80×112 格，高 32 格，拆成 **35 份 16×32×16 的模板**。

城市位置在世界规划阶段选定并对齐区块。每个相交区块只放自己的一个模板，不重新抽取变体，不跨界写方块，不要求远处城市区块同时存在。模板包含必要空气，整体源蓝图先完成旋转和拼装，再切片，因此跨区块的门、屋顶、道路属于同一个总体设计。

保留区有三层保护：原版结构在生成起点/引用建立前以包围盒排除城市；邻区块的普通 `WorldGenRegion.setBlock` 装饰写入被保留区拦住；原版矿脉直接写 section，所以另在 `OreFeature.canPlaceOre` 的逐格放置判定中检查当前生成作用域，防止邻区块矿脉把已放好的地基改成砂砾。城市自身写入使用当前切片作用域。保护仅覆盖生成调用，**不阻止玩家在 ServerLevel 中挖掘和建造**。

命簿宗门选址也避开城市保留区，避免稍后由宗门实际化覆盖城市。上述保护覆盖原版矿脉及正常 WorldGenRegion 写入；第三方绕开这两条路径直接写区块的兼容性不在本轮保证范围内。

## 5. 存档与更新规则

完整冻结规则随生成器 Codec 保存进 `level.dat`，并保存 `plan_hash`。重启从存档配置重建规划，而不是用后来改过的 JSON 把洲域移走。已有普通世界仍使用旧生成器，不自动改变地形。

城市资源的 SHA-256 也在冻结规则中。后续保留旧版本资源 ID 与算法，是继续生成旧存档新区块的必要条件。发现计划或模板哈希不符时明确失败，而不是静默换版、产生接缝。后续算法继续使用新的版本 ID；V1/V2 与它们引用的城市资源不能被覆盖。城市内容未改动，因此 V2 仍引用固定的 `yunshan_v1` 模板。

`/reload` 不用于迁移现有世界规划。普通玩家已修改的 FULL 区块不会重新运行城市模板。导出舆图是只读运算，不生成游戏区块，也不还原被玩家拆掉的建筑。

## 6. 如何直接验收

先用全新测试世界以实际探索方式接近城市，观察道路、门和屋顶是否跨区块连续。可根据 `worldplan city` 返回的西北角加上宽深的一半找到街区中心；传送高度选城市 baseY+50，先用创造飞行观察，随后落地步行。

管理员自动验收接口：

```mcfunction
/myvillage worldplan pregen_city forward 8
/myvillage worldplan audit_city
/myvillage worldplan pregen_city reverse
/myvillage worldplan audit_city
/myvillage worldplan probe 249 0
/myvillage worldplan probe 250 0
/myvillage worldplan probe -251 0
/myvillage worldplan probe 6250 -6250
```

`pregen_city` 是显式开发命令，可能有生成耗时，不应在正常服务器循环执行。它不设置永久强制加载票据。`audit_city` 只读已经加载的 FULL 城市切片；缺少切片不会为了审计而自动加载。`raw_differences` 保留全部差异，`natural_changes` 只统计原模板不透明遮挡仍完整的草方块变泥土；`differences` 是剩余实际改动。`complete` 要求全部加载且实际差异为零，`strict_complete` 还要求原始方块完全相同。没有遮挡的泥土改建、缺路、缺墙和遮挡损坏不会被忽略。

在一部分城市加载后，在已加载切片内建一个标记方块，保存退出；重新打开，再探索余下区块。标记应保留。另一份同种子世界反向探索，未被玩家改建的城市应得到相同结果。

完整有界自动测试：

```bash
python3 tools/worldplan/generate_assets.py
python3 tools/worldplan/generate_assets.py --check
./gradlew test --tests '*worldplan*' --tests '*region.runtime*' \
  --tests '*WorldSimGenesisTest*' --tests '*SimPurityGuardTest*' \
  jar -x generateAllStructures --no-daemon --max-workers=2
python3 tools/worldplan/acceptance.py
# 需要 Xvfb、xdotool、ImageMagick、Mesa 的物理客户端证据。
python3 tools/worldplan/capture.py
```

`acceptance.py` 只创建唯一命名的验收世界，备份/恢复 `run-acceptance` 的服务器属性和 EULA，只终止自己启动的子进程。不会删除已有世界，也不会写普通 `run/` 实例。服务器仅监听回环地址。生成完成后输出 `reports/worldplan/runtime/acceptance.json`、各阶段日志、清理记录，以及真正运行服务器生成的验收存档 ZIP。

`capture.py` 使用另一份独立世界与客户端目录，连接原生 Minecraft 客户端，输出实机截图和坐标记录。没有调用 DevBridge，没有把示意图标成实机截图。截图采集通过不等于用户认可视觉质量或性能。

测试目标覆盖：冻结配置和极端种子、510 个种子拓扑与出生点、实际 NOISE 地表、正反加载顺序、城市逐格对照、分段加载后重启继续生成、玩家标记保存、实际 level.dat 中的规则与哈希、JAR 资源完整性、普通预设不被转换。具体哪些通过，以该提交对应的测试报告为准，不以本文描述测试目标代替通过证据。

## 7. 与现有玩法及本轮边界

旧 region 查询接口在新预设中读取同一份核心州域图；命簿继续模拟有限的核心命名州域。外域可以查询地形、灵气、危险度和生态，妖兽引用已有对应风格的出生境界配置并保存外域出生身份。**没有无限生成命簿人物，也没有重做修士境界人口分布**。州域 tier、个人 realm 和灵气 qi 仍是不同概念。

本轮交付的是可生成、可导图、可检查、可继续探索的世界生成底座和云山街区占位。没有宣称完整古城、多座主城、全世界道路网、天堑绝对不可穿越、第三方地形模组全面兼容或用户审美认可已经完成。

## 8. Dregora 参考与构建环境

参考的是 RLCraft **Dregora**。作者项目页将“程序生成 + 带世界背景的持久区域”列为特征，使用 OTG；其平台是 Minecraft 1.12.2 / Forge。借鉴的是设计区域与程序随机并存的分层方法，不迁移 OTG 旧 API、不复制地图或建筑，也不把未经核实的“距原点越远越强”当作它的精确实现。

参考来源：
- 作者项目页：https://www.curseforge.com/minecraft/modpacks/rlcraft-dregora
- NeoForge 1.21.1 Biome Modifier 文档：https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/

本轮工作环境无法使用已有 Modal 凭据，已采用独立 GitHub Actions Linux 运行编译和测试；没有使用用户 Windows 本机作为开发机。物理客户端截图、用户视觉裁决、多玩家长期测试在验证报告中分别注明，不能互相替代。
