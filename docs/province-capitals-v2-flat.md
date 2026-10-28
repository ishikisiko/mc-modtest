# 东海、西漠、北原 v2 实施交接

本作者只写三城 v2 authored 输入、对应 v2 风格、本说明和 `out/preview/province-capitals-v2/flat-design/`。工作区为 `/tmp/mc-default-cultivation-20261028`，分支 `feature/province-capitals-redesign-20261029`，起点 `a223aafc2c150654f01b6f9f8e4ed9eedf8f8937`。未切分支、创建工作树、提交、合并或推送；未修改共享引擎、其他四城、中州或 v1 源与产物。

输入遵守本轮共同约定：所有建筑逐栋列于 `buildings`，`size` 为含屋盖、地坪与前场的本地未旋转实际整数占地，朝向统一旋转；`position.y` 是首层站立空气格，`variant` 仅 0..3。三城均为 224×96×272，有五个不等街坊。所有新增说明与预览为中文，建筑使用连续方块主体和完整屋盖。

## 当前固定输入

精确摘要保存在 [source-identity.json](../out/preview/province-capitals-v2/flat-design/source-identity.json)。下列为本次 authored 源的快照，不代表固定源游戏验收。

| 城市 | 全部建筑 | 住宅 | 一层/二层住宅 | 住宅实际尺寸种数 | 生活点（当前几何） |
|---|---:|---:|---|---:|---:|
| 东海·潮港城 | 71 | 62 | 47/15 | 19 | 138 |
| 西漠·泉驿城 | 69 | 61 | 46/15 | 20 | 136 |
| 北原·朔仓城 | 74 | 65 | 59/6 | 26 | 145 |

住宅共 188 栋，全部建筑 214 栋；每城生活点均低于既有 192 上限。层数由用途与地块明确给出，没有每第 N 户抬高的规则。三份风格只使用已有合法槽与原版方块 fallback，未添加外部模组依赖。

## 三城总图判断与路线

### 东海：顺湾仓市、陆门通港

岸线在北埠收窄成港湾，卸货街以正交折线退让海岸；内陆为盐市店坊、西井曲巷坊、织网里，南部为短宅和共脊连宅，滨岸单独留给鱼市和两种仓院。住宅逐段保持不同宽度和后部深度，门面朝向真实街巷；北盐街的两层店宅、低曲院与南埠低宅有功能差别。

- 西陆门沿 `land_road` 到港，经过向导亭与南仓，转 `merchant_spine` 抵达盐行、绳具铺和仓街。
- `coast_walk` 顺折岸衔接北长仓、湾口鱼市与南转运仓，`north_pier`、`south_pier` 两条有桩基栈桥伸入真实水面。北仓入口东向，距滨街为短接；鱼市东向直接面对滨街，南侧可转入织网坊。
- 南岬观潮书楼是唯一最高主地标，从陆门向港、从鱼市向南都能辨认；楼前巷留在岸内，未将建筑移入海面。
- 水井前场 11×11 与卸货前场 11×17 均避开住宅；卸货前场使用 `kind=quay`，让水岸支撑服从真实河床。

### 西漠：偏东绿洲、北驿大院、贯通密巷

绿洲向东偏置，北岸长市和北侧大驿院围合公共核心，泉南公厅以低厅、侧高阁形成对岸视觉目标。西半城采用窄纵巷和一条共用井巷穿过大小院宅；曲尺院、合院、前店后楼、浅宅按不同深度拼成街坊，已去掉旧七个相同孤岛院落。城内统一首层标高 10，完全平地也成立。

- 南驿门、向导亭、`caravan_spine`、北泉交易街串起南门院坊、老坊与大驿院；主路在井巷与绿洲边界转折，有明确地块原因。
- `cistern_lane`、`west_spine`、`old_cross` 连接密巷北段的浅宅/小铺与南段的深曲院/合院，不依赖逐户跨城开支路。
- 药香堂西门朝绿洲东岸的石岸路；水面完全落在盆地内，未向建筑底层穿水。北岸市场南向，泉南公厅北向，隔岸互见。
- 大驿院 35×35，实际西侧客楼和开前场面向迎客街，前场 13×31 有连续遮阳顶与支柱；9×13 共用井前场及 31×7 泉南广场均保持通路。

### 北原：主轴、东仓西市与不等背风院坊

南关楼—大街—镇雪公厅保留规整主轴，西市暖廊与东仓大院错位形成体量变化。西市暖院、西驿长短院、东炉背风院各自有不同深度与入口方向，西侧生活横街和东侧车马纵街没有共用坐标骨架。二层主要给粮商店宅、客栈、粮仓和关楼；低暖院和长体连宅保留避风轮廓。

- 从南门街景沿主轴可见前仪厅与后高堂，北关公厅为唯一最高地标；西北角戍楼收束侧面视线。
- 西市廊厅面向主街，药堂接独立小街，西驿客栈东向大街；29×27 共用暖庭位于客栈北侧，连接集散街，未覆盖宅院。
- 冬粮大院西门短接 `granary_road`，大仓与卸货场形成明显横纵体量；7×19 卸场接车马道，和小宅地块分离。
- 东炉铁作院西门面对作坊街，南侧锻坊街、东侧储雪巷串接曲院与工具店宅；不会把住宅再次排成两行相同雪屋。

## 家族与实际尺寸分布

以下尺寸均为 authored 本地宽×深；东西向建筑落地时交换 X/Z 包络，尺寸未归并或偷偷取奇数。括号为栋数。

### 东海·潮港城

| 住宅族 | 栋数 | 实际宽×深分布 |
|---|---:|---|
| `lane_house` | 33 | 7×9（5）、7×11（1）、9×9（6）、9×11（9）、9×13（5）、11×9（3）、11×11（1）、11×13（3） |
| `shop_house` | 15 | 9×13（2）、11×13（1）、11×15（5）、11×17（3）、13×13（1）、13×15（1）、13×17（2） |
| `l_court` | 5 | 15×17（2）、15×19（1）、17×19（2） |
| `u_court` | 1 | 17×17（1） |
| `row_pair` | 8 | 17×11（7）、19×13（1） |

| 街坊 | 住宅数 |
|---|---:|
| 盐市店坊 | 14 |
| 西井曲巷坊 | 18 |
| 织网里 | 14 |
| 南埠连宅坊 | 14 |
| 潮湾仓市 | 2 |

| 功能建筑 | 家族/结构变体 | 本地尺寸/层数 | 中心 X,Y,Z | 朝向/真实街路 |
|---|---|---|---|---|
| 听潮会馆客栈 | `courtyard_inn` v0 后客楼双翼院 | 27×27 / 2层 | 145,8,24 | south / `north_salt` |
| 海草药铺晒药院 | `herb_hall` v2 通堂侧晒场 | 21×21 / 1层 | 127,8,179 | north / `fish_cross` |
| 修船铁作院 | `forge_yard` v1 东作坊后库 | 23×17 / 1层 | 169,8,29 | east / `coast_walk` |
| 湾口鱼市廊厅 | `market_hall` v1 双廊后市厅 | 23×23 / 1层 | 155,8,149 | east / `coast_walk` |
| 陆门潮港向导亭 | `guide_pavilion` v1 纵亭侧廊 | 9×9 / 1层 | 91,8,207 | south / `land_road` |
| 北埠长仓院 | `warehouse_yard` v1 西长仓东卸场 | 35×25 / 1层 | 135,8,106 | east / `coast_walk` |
| 南埠转运仓院 | `warehouse_yard` v3 横库短翼卸场 | 27×29 / 1层 | 156,8,195 | east / `coast_walk` |
| 观潮书楼 | `civic_hall` v2 东阁横厅院 | 23×25 / 3层 | 168,8,244 | west / `headland_link` |
| 潮港陆门 | `gatehouse` v1 后退关楼 | 21×11 / 1层 | 8,8,218 | west / `land_road` |

### 西漠·泉驿城

| 住宅族 | 栋数 | 实际宽×深分布 |
|---|---:|---|
| `lane_house` | 27 | 7×9（1）、9×9（5）、9×11（3）、9×13（3）、11×9（8）、11×13（1）、13×9（3）、13×11（1）、15×9（2） |
| `shop_house` | 15 | 9×13（6）、11×13（4）、11×17（1）、13×13（2）、13×17（1）、15×13（1） |
| `l_court` | 12 | 15×15（6）、15×17（5）、15×19（1） |
| `u_court` | 6 | 17×17（2）、17×19（3）、19×17（1） |
| `row_pair` | 1 | 17×11（1） |

| 街坊 | 住宅数 |
|---|---:|
| 北驿商坊 | 14 |
| 西泉密巷坊 | 20 |
| 绿洲水岸坊 | 4 |
| 南门深浅院坊 | 14 |
| 南泉工市坊 | 9 |

| 功能建筑 | 家族/结构变体 | 本地尺寸/层数 | 中心 X,Y,Z | 朝向/真实街路 |
|---|---|---|---|---|
| 泉路大驿院 | `courtyard_inn` v2 西楼开敞前场 | 35×35 / 2层 | 174,10,40 | west / `inn_forecourt` |
| 泉岸药香堂 | `herb_hall` v1 东药楼晒院 | 25×19 / 1层 | 200,10,128 | west / `oasis_bank` |
| 南驿铁作坊 | `forge_yard` v3 长工坊前晒场 | 27×25 / 1层 | 141,10,232 | north / `south_souk` |
| 泉北香料市廊 | `market_hall` v2 纵向货廊 | 31×19 / 1层 | 165,10,73 | south / `north_trade` |
| 沙路向导亭 | `guide_pavilion` v2 后亭深前场 | 9×9 / 1层 | 116,10,238 | west / `caravan_spine` |
| 泉南会水公厅 | `civic_hall` v1 西侧高阁前厅 | 31×29 / 3层 | 180,10,189 | north / `oasis_bank` |
| 背风驿门 | `gatehouse` v2 偏西高关楼 | 21×11 / 2层 | 98,10,260 | south / `caravan_spine` |
| 驼商储货大院 | `warehouse_yard` v0 后仓双卸货廊 | 29×23 / 1层 | 41,10,39 | east / `cistern_lane` |

### 北原·朔仓城

| 住宅族 | 栋数 | 实际宽×深分布 |
|---|---:|---|
| `lane_house` | 35 | 7×9（2）、7×11（1）、9×9（7）、9×11（2）、9×13（2）、11×9（5）、11×11（2）、11×13（5）、11×15（1）、13×9（4）、13×11（2）、13×13（1）、15×11（1） |
| `shop_house` | 6 | 11×15（1）、13×13（2）、13×15（1）、15×13（1）、17×13（1） |
| `l_court` | 9 | 15×15（2）、15×17（4）、17×15（1）、17×17（1）、17×19（1） |
| `u_court` | 4 | 17×17（2）、17×21（1）、19×21（1） |
| `row_pair` | 11 | 17×11（7）、17×13（1）、19×11（2）、23×11（1） |

| 街坊 | 住宅数 |
|---|---:|
| 北关公厅坊 | 10 |
| 西市暖院坊 | 18 |
| 东仓粮坊 | 10 |
| 西驿长短院坊 | 11 |
| 东炉背风坊 | 16 |

| 功能建筑 | 家族/结构变体 | 本地尺寸/层数 | 中心 X,Y,Z | 朝向/真实街路 |
|---|---|---|---|---|
| 朔风暖庭客栈 | `courtyard_inn` v3 双后楼长廊院 | 33×31 / 2层 | 87,12,226 | east / `avenue` |
| 暖窖药堂 | `herb_hall` v3 前药堂后宿舍 | 23×21 / 1层 | 60,12,149 | north / `herb_cross` |
| 东炉铁作院 | `forge_yard` v0 西作坊后宿 | 25×27 / 1层 | 158,12,187 | west / `east_work` |
| 西市暖廊厅 | `market_hall` v3 前厅后货库 | 35×27 / 1层 | 87,12,112 | east / `avenue` |
| 朔仓入城向导亭 | `guide_pavilion` v3 偏东书亭 | 9×9 / 1层 | 83,12,260 | north / `south_cross` |
| 东仓冬粮大院 | `warehouse_yard` v2 东长仓西卸场 | 35×31 / 2层 | 175,12,91 | west / `granary_road` |
| 北关镇雪公厅 | `civic_hall` v3 前仪厅后高堂 | 31×29 / 3层 | 116,12,28 | south / `citadel_cross` |
| 朔仓南关楼 | `gatehouse` v0 双阙通楼 | 23×11 / 2层 | 116,12,260 | south / `avenue` |
| 西北角戍楼 | `gatehouse` v2 偏西高关楼 | 15×11 / 2层 | 19,12,28 | south / `tower_link` |

## 预览、截图和输入身份

[三城中文施工总图](../out/preview/province-capitals-v2/flat-design/index.html)读取实际施工翼体；细线为地块，实色为建筑翼体，不以包围盒冒充房屋。三城三维均来自共享 v2 施工器的实际逐格记录，包含真实屋盖、门窗、楼板、床与工作设施，未写正式资源根。

| 城市 | 三维 | 总图截图 | 全景截图 | 入口街景 | 公共节点/代表院落 |
|---|---|---|---|---|
| 东海·潮港城 | [同源设计预览](../out/preview/province-capitals-v2/flat-design/donghai/index.html) | [总图](../out/preview/province-capitals-v2/flat-design/donghai/plan.png) | [全景](../out/preview/province-capitals-v2/flat-design/donghai/panorama.png) | [入口街景](../out/preview/province-capitals-v2/flat-design/donghai/street.png) | [节点](../out/preview/province-capitals-v2/flat-design/donghai/feature.png) / [院落](../out/preview/province-capitals-v2/flat-design/donghai/courtyard.png) |
| 西漠·泉驿城 | [同源设计预览](../out/preview/province-capitals-v2/flat-design/ximo/index.html) | [总图](../out/preview/province-capitals-v2/flat-design/ximo/plan.png) | [全景](../out/preview/province-capitals-v2/flat-design/ximo/panorama.png) | [入口街景](../out/preview/province-capitals-v2/flat-design/ximo/street.png) | [节点](../out/preview/province-capitals-v2/flat-design/ximo/feature.png) / [院落](../out/preview/province-capitals-v2/flat-design/ximo/courtyard.png) |
| 北原·朔仓城 | [同源设计预览](../out/preview/province-capitals-v2/flat-design/beiyuan/index.html) | [总图](../out/preview/province-capitals-v2/flat-design/beiyuan/plan.png) | [全景](../out/preview/province-capitals-v2/flat-design/beiyuan/panorama.png) | [入口街景](../out/preview/province-capitals-v2/flat-design/beiyuan/street.png) | [节点](../out/preview/province-capitals-v2/flat-design/beiyuan/feature.png) / [院落](../out/preview/province-capitals-v2/flat-design/beiyuan/courtyard.png) |

截图明确标注“当前施工几何设计预览 · 非原生游戏”。使用本地 Chromium/SwiftShader 按机位静帧绘制，关闭阴影图以避免连续软件绘制消耗；未改变施工几何，不能据此评价游戏 FPS。父会话仍应在最终七城固定源正式导出后生成最终截图画廊，并逐城显示。对 T3 发布资源时必须使用 `https://assets.ishiki333hhh.win/`，本作者未新开服务器或更改 8765 服务。

- [layout-check.json](../out/preview/province-capitals-v2/flat-design/layout-check.json)：三城建筑范围、旋转占地、道路切入、声明路接近程度与完整路网连通检查；当前无问题。
- [preview-snapshots.json](../out/preview/province-capitals-v2/flat-design/preview-snapshots.json)：每城施工时的精确输入、共享实现摘要、家具/生活点/短接统计、前场实际铺地统计。每张设计图只对应本次 snapshot，不与其他提交或原生证据混合。
- [capture-report.json](../out/preview/province-capitals-v2/flat-design/capture-report.json)：每机位 HTML 摘要、截图路径、页面异常与明确未验收范围。
- [initial-layout-failures.json](../out/preview/province-capitals-v2/flat-design/initial-layout-failures.json)、[initial-construction-probe.log](../out/preview/province-capitals-v2/flat-design/initial-construction-probe.log) 保留首次地块/接街失败，不能充作当前通过记录。

本作者实际执行了本地 JSON/家族最小尺寸/原版风格加载自查、轻量旋转总图检查、当前施工几何探针和设计预览截图。未执行正式七城资源导出、Gradle、Minecraft、Colab、Modal 或交易细节验收。原生入口安全、传送确认/取消、JAR 编译与资源身份由父代理统一安排；玩家外观、手感、音效、FPS、多人均为 `not_verified`。

## 共享接口交接与未完成项

1. 本作者三城输入已经固定；若共享施工器再改，父代理应按固定的三城 JSON 和新共享源码统一正式导出，不拼接本次设计 snapshot 作为正式通过证据。
2. `courtyards` 的 2/3/3 个前场已按当前接口参与实际铺地、支撑、净空；西漠驿前遮阳棚、北原暖庭风墙均由共同施工器消费。所有前场避开建筑占地，保留已有道路格。
3. `decorations` 额外给出每城三项货篮、公井/坐凳等锚点，含坐标与道路引用。当前共享引擎尚无该扩展字段消费者，因此这些额外道具未生成；这是供父代理/建筑作者接入的明确需求。床、药架、炉台、铁砧、市场货摊、仓储设施、树木和前场已有实际几何。
4. 最终生产导出由父代理执行；本作者没有写共享引擎、放宽入口距离或生活点上限，也没有触碰另外两个工作区。

复现本作者轻量设计检查和预览：

```bash
python3 out/preview/province-capitals-v2/flat-design/check_layout.py
python3 out/preview/province-capitals-v2/flat-design/design_preview.py
node out/preview/province-capitals-v2/flat-design/capture_design.cjs
```

父代理固定共享源码后的正式导出入口（本作者未执行）：

```bash
python3 tools/province_capitals/regional/v2/export_game.py --out build/generated/province_capitals_v9/resources --regions donghai ximo beiyuan
```

## 精确改动文件

- `blueprints/province_capitals/regional/v2/cities/donghai.json` — SHA-256 `2b51d848d030e36af3339d61481b64c9ab2718a036dab79850c2391f07734223`
- `tools/buildgen/styles/province_capital_donghai_v2.json` — SHA-256 `69c8a8ca788ff271dc83e1bedbbd082fd10327a3aadf54032749cfb68ec31ca4`
- `blueprints/province_capitals/regional/v2/cities/ximo.json` — SHA-256 `5dbefa438fc25290b78c7343db38ba2006beef9b7c8e0fa9da5df5ecd2efa258`
- `tools/buildgen/styles/province_capital_ximo_v2.json` — SHA-256 `414821ac83770468752cbc8bc97a63f2793fecc249a45f0b0999e3a51bea53e5`
- `blueprints/province_capitals/regional/v2/cities/beiyuan.json` — SHA-256 `025319af210fa52cf22c5e88f8c931a6c99ef4829c821092e9c9de047bd36010`
- `tools/buildgen/styles/province_capital_beiyuan_v2.json` — SHA-256 `0020d60274b183e76a4aebb728f9cece59c1e5a31c441c53dd2062c4be4ab7f7`
- `docs/province-capitals-v2-flat.md`（本交接）
- `out/preview/province-capitals-v2/flat-design/`：本作者只读检查/设计展示/捕获脚本、三城 HTML/scene/总图、15 张设计截图、旧版参考截图和轻量自查记录（生成预览，不作为 mod 生产源）。
