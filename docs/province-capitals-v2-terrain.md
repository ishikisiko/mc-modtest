# 四州 v2 山地与河谷作者交接

本交接固定于 2026-10-29，工作区 `/tmp/mc-default-cultivation-20261028`，分支 `feature/province-capitals-redesign-20261029`，起点 `a223aafc2c150654f01b6f9f8e4ed9eedf8f8937`。遵守本轮协调所有权，仅写四城 authored JSON、对应 v2 style、本文件与自有设计预览目录；没有切分支、创建 worktree、提交、合并、推送或派子代理。

四城合计 **256 栋住宅、288 栋建筑**。每栋建筑都在正式 `buildings` 中，保留实际整数宽深、显式层数、朝向和街路引用；没有 `house_rows`，没有按序号周期插入双层房。住宅类型比例由州域地块决定。

## 当前输入统计

下表及后续分布直接从本轮四份输入提取，是此交接快照；后续修改应以 JSON 为准。

| 城市 | 格数 X×Y×Z | 住宅 / 全建筑 | 一层 / 两层住宅 | 主次街路 / 桥 | 共享院坪 / 连续台地 |
|---|---|---:|---:|---:|---:|
| 灵岳 | 240×96×288 | 61 / 68 | 34 / 27 | 21 / 1 | 6 / 5 |
| 南疆 | 240×96×288 | 62 / 70 | 48 / 14 | 20 / 3 | 7 / 2 |
| 东荒 | 176×96×416 | 70 / 79 | 56 / 14 | 14 / 0 | 6 / 3 |
| 魔域 | 240×96×288 | 63 / 71 | 37 / 26 | 18 / 3 | 7 / 2 |

| 城市 | 住宅 family 数量 | 实际本地宽×深（栋数） | 门面朝向（栋数） |
|---|---|---|---|
| 灵岳 | 店宅 8、台地宅 32、巷宅 12、曲尺院 7、干栏长屋 2 | 7×9（3）、9×11（6）、9×13（23）、9×17（1）、11×15（10）、11×21（1）、13×17（10）、15×15（6）、17×19（1） | 南 18、北 30、东 8、西 5 |
| 南疆 | 店宅 10、巷宅 15、联宅 7、曲尺院 9、深院 4、干栏长屋 17 | 7×9（2）、9×11（12）、9×13（4）、9×17（9）、11×15（4）、11×21（6）、13×17（3）、13×23（2）、15×15（9）、17×11（7）、17×19（4） | 南 9、北 8、东 35、西 10 |
| 东荒 | 店宅 11、巷宅 25、联宅 15、曲尺院 15、深院 4 | 7×9（4）、9×11（17）、9×13（10）、11×13（1）、11×15（2）、13×17（2）、15×15（14）、17×11（15）、17×19（5） | 南 19、北 20、东 9、西 22 |
| 魔域 | 店宅 15、联宅 9、曲尺院 13、巷宅 12、深院 3、台地宅 11 | 9×11（8）、9×13（17）、11×15（5）、13×15（2）、13×17（6）、15×15（11）、17×11（6）、17×19（5）、19×13（3） | 南 12、北 14、东 29、西 8 |

## 灵岳：错台、街阶与厅前折街

五片连续台地分别为南门市街 Y16、东炉坊 Y20、西中院坊 Y28、东中崖坊 Y32、上厅台 Y42。台地宽度、纵深和起止位置不同；东中台沿提前收束，临崖长屋伸向自然坡，柱脚交给同源地形支承。南门 Y12 经完整街阶到下市；西侧两次回折与东崖登厅阶共同接通上层。

下市有前店后住和石井背巷，东炉坊围绕作院前坪；中层曲尺院与药院沟桥组织回路；上层厅前街在东侧向北折十八格，形成两段错位街面与松院巷，保留主厅前场。厅堂、曲尺院和台地宅的宽深与楼层各自指定，不复用旧四层横排坐标。

## 南疆：密西岸、疏东岸与三个水工节点

西岸 35 栋住宅，采用店宅、联宅、巷宅和院坊；东岸 27 栋住宅，包含 17 栋干栏长屋及曲尺/深院。Y14 地面街坊和 Y17 架空长廊分别组织，东岸院坊街通过明确落地阶与廊道连接。长屋首层 Y17 已是最终站立空气格；不再额外叠加 `stilt_raise`。

南埠货桥连接栈院、转运仓与东岸客舍；中桥连接水坊市场和长廊节点；北桥连接议事长堂、药篓坪与晒药深院。南埠另有五格宽卸货码头，从西岸 Y14 经六格长的明确阶道降至 Y11 栈台，深入水面到 X145；地面与栈道都使用合法丛林木槽。岸台为两片连续地面，不使用每户单独的台地补垫。沿岸长廊与长屋下的柱脚需要一直落到最终岸坡或河床。桥头和卸货坪都有显式坐标输入。

## 东荒：谷口驿院—折街市场—北关前场

三片街坊地面为 Y17、Y21、Y25，主道经过南段回车驿院、中段折街市场、北端关前院街。自然地形从南至北约十格缓升，谷边增高仅七格；地形用于服务驿道，不以高峭壁围住住宅。

谷口配置转角店宅、修造院和粮秣仓；中段把活跃铺面、后坊院巷和谷药堂分开；北段采用更多曲尺院、深院与 23 处林缘树木输入。四个朝向都有住宅：南19、北20、东9、西22。第二座北关楼拥有独立前场，既是驿道终点也是次级对外节点。

## 魔域：低岸炉市、高岸深院与重檐主阁

低岸 Y18 有 38 栋住宅，围绕北炉作院、百工市厅和南关栈院；高岸 Y27 有 25 栋住宅，以台地宅、曲尺/深院及后巷住处构成。主阁位于中桥高岸，厚重厅体和一侧高阁有独立前场。结构差异来自地块宽深、围合翼体、楼层与朝向，深色材质只负责区域氛围。

南桥接登岸仓院，中桥接主阁前场，北桥接药藏深院；每个桥头都有对应街路和院坪。双格厚墙只在大体量栈院与主阁的逐栋 `construction.wall_thickness=2` 中启用；小型两层住宅保留一格墙及完整楼梯空间，不对整城机械加厚。

## 公共建筑与院坪

五类主要服务各有一栋主建筑，均通过 `primary_service=true` 明确唯一性；辅助仓院与城门分别登记。以下列出实际本地宽深、结构变体与门面，具体房间/屋盖由共享 family 实现施工。

| 城市 | 建筑 | role / family | variant | 宽×深 / 层 | 朝向 / road_id |
|---|---|---|---:|---|---|
| 灵岳 | 听泉栈院 | `inn` / `courtyard_inn` | 1 | 21×23 / 2 | 东 / `lower_turn` |
| 灵岳 | 登城问路亭 | `guide` / `guide_pavilion` | 1 | 11×11 / 1 | 东 / `entry` |
| 灵岳 | 东炉石作院 | `smith` / `forge_yard` | 1 | 21×25 / 1 | 西 / `kiln_lane` |
| 灵岳 | 曲尺晒药堂 | `apothecary` / `herb_hall` | 2 | 19×23 / 1 | 西 / `mid_w_spine` |
| 灵岳 | 沟桥药材市厅 | `market` / `market_hall` | 1 | 25×21 / 1 | 南 / `mid_e_cross` |
| 灵岳 | 灵岳议事上厅 | `landmark` / `civic_hall` | 2 | 33×27 / 2 | 南 / `upper` |
| 灵岳 | 石阶南关 | `gate` / `gatehouse` | 1 | 19×9 / 1 | 南 / `entry` |
| 南疆 | 南埠雨廊客栈 | `inn` / `courtyard_inn` | 3 | 25×23 / 2 | 南 / `south_market` |
| 南疆 | 南埠转运仓院 | `warehouse` / `warehouse_yard` | 1 | 23×21 / 1 | 北 / `south_market` |
| 南疆 | 货桥问路亭 | `guide` / `guide_pavilion` | 3 | 11×11 / 1 | 西 / `west_quay` |
| 南疆 | 水坊工具作院 | `smith` / `forge_yard` | 3 | 23×23 / 1 | 南 / `waterwork` |
| 南疆 | 水坊百货廊厅 | `market` / `market_hall` | 3 | 25×19 / 1 | 北 / `waterwork` |
| 南疆 | 东岸晒药深院 | `apothecary` / `herb_hall` | 3 | 23×23 / 1 | 南 / `east_n_step` |
| 南疆 | 南疆议事长堂 | `landmark` / `civic_hall` | 3 | 29×25 / 2 | 南 / `west_north` |
| 南疆 | 南埠木关 | `gate` / `gatehouse` | 3 | 19×9 / 1 | 南 / `entry` |
| 东荒 | 谷口回车驿院 | `inn` / `courtyard_inn` | 0 | 29×23 / 2 | 北 / `south_cross` |
| 东荒 | 驿门问程亭 | `guide` / `guide_pavilion` | 0 | 11×11 / 1 | 东 / `relay` |
| 东荒 | 车马修造院 | `smith` / `forge_yard` | 0 | 21×23 / 1 | 东 / `smith_front` |
| 东荒 | 谷中折街市厅 | `market` / `market_hall` | 0 | 25×23 / 1 | 南 / `relay` |
| 东荒 | 谷药前堂后院 | `apothecary` / `herb_hall` | 0 | 21×23 / 1 | 南 / `market_top` |
| 东荒 | 北关议事厅 | `landmark` / `civic_hall` | 0 | 29×25 / 2 | 南 / `north_top` |
| 东荒 | 谷口南关 | `gate` / `gatehouse` | 0 | 19×9 / 1 | 南 / `relay` |
| 东荒 | 长谷北关楼 | `gate` / `gatehouse` | 2 | 19×9 / 2 | 北 / `relay` |
| 东荒 | 驿路粮秣仓院 | `warehouse` / `warehouse_yard` | 0 | 19×21 / 1 | 西 / `relay` |
| 魔域 | 岩关深栈院 | `inn` / `courtyard_inn` | 2 | 27×25 / 2 | 南 / `south_low` |
| 魔域 | 岩关问路堂 | `guide` / `guide_pavilion` | 2 | 11×9 / 1 | 东 / `west_spine` |
| 魔域 | 北炉厚墙作院 | `smith` / `forge_yard` | 2 | 25×25 / 1 | 南 / `north_low` |
| 魔域 | 低岸百工市厅 | `market` / `market_hall` | 2 | 27×21 / 1 | 北 / `middle_low` |
| 魔域 | 高岸药藏深院 | `apothecary` / `herb_hall` | 1 | 23×25 / 1 | 北 / `east_n_front` |
| 魔域 | 魔域重檐主阁 | `landmark` / `civic_hall` | 1 | 31×27 / 2 | 西 / `east_spine` |
| 魔域 | 登岸货栈深院 | `warehouse` / `warehouse_yard` | 2 | 23×25 / 1 | 西 / `east_spine` |
| 魔域 | 低岸岩关 | `gate` / `gatehouse` | 2 | 21×9 / 1 | 南 / `entry` |

共享院坪分别为：

- 灵岳：上厅前场、药院转阶坪、西阶石井坪、沟桥药市前坪、东炉作院前坪、听泉栈院门坪。
- 南疆：南埠货桥西前坪、水坊桥西市坪、北桥药篓坪、东岸中桥长廊节点、晒药院前坪、议事长堂前场、转运仓卸货坪。
- 东荒：谷口回车前坪、车马修造坪、折街主市前场、谷药晒药坪、北关议事厅前场、北关验货前坪。
- 魔域：百工市厅前场、岩关深栈院前场、北炉作院前坪、主阁临桥前场、登岸仓院桥坪、炉药桥东坪、药藏深院前坪。

## 文件与坐标约定

正式源共九个文件：

- `blueprints/province_capitals/regional/v2/cities/lingyue.json`
- `blueprints/province_capitals/regional/v2/cities/nanjiang.json`
- `blueprints/province_capitals/regional/v2/cities/donghuang.json`
- `blueprints/province_capitals/regional/v2/cities/moyu.json`
- `tools/buildgen/styles/province_capital_lingyue_v2.json`
- `tools/buildgen/styles/province_capital_nanjiang_v2.json`
- `tools/buildgen/styles/province_capital_donghuang_v2.json`
- `tools/buildgen/styles/province_capital_moyu_v2.json`
- `docs/province-capitals-v2-terrain.md`

建筑 `size` 是未旋转的本地实际整数宽深，局部左界为 `-floor(size/2)`，右界为左界加尺寸减一。canonical 前侧为 +Z，旋转到 `facing`；Y 是首层站立空气格。本轮住宅宽深选择奇数是地块设计选择，不要求引擎把其他整数归类或取奇数。屋檐和前场计入真实 lot；额外短接只靠已有街路，`max_access_length=12`。

样式全部通过既有 vanilla modset 与 style 槽解析；必需槽具有 Minecraft fallback。保留 MC 硬边、连续实体和完整屋盖。没有修改外部白名单、旧 family handler、v1 输入/产物或中州；V9 聚合和旧档兼容仍归父会话。

## 设计证据与生成快照

[四城中文坐标总图](../out/preview/province-capitals-v2/terrain-design/index.html)及四份 PNG 来自最终 authored 坐标。色块表示规划占地，图中明确标注不是最终方块或原生游戏截图。逐栋记录见 [building-ledger.csv](../out/preview/province-capitals-v2/terrain-design/building-ledger.csv)，精确源摘要见 [input-identity.json](../out/preview/province-capitals-v2/terrain-design/input-identity.json)。

[设计统计](../out/preview/province-capitals-v2/terrain-design/design-inventory.json)、[轻量审查](../out/preview/province-capitals-v2/terrain-design/input-audit.json)和 [审查日志](../out/preview/province-capitals-v2/terrain-design/input-audit.log)保留住宅族、尺寸/楼层/朝向、公共建筑和桥头数据。当前四城规划路网各为一个可逐格衔接的分量，交叉道路超过一格的高差为零；地块互不重叠，主服务唯一，公开院坪不压实体建筑，实际宽深满足本轮族最小建议。以上是离线输入检查，不是原生寻路通过。

在共享引擎变化期间正常导出自身资源四次，原始日志分别为 `terrain-design/export-attempt-01.log` 至 `export-attempt-04.log`。已记录南疆仓院/工坊和东荒修造院短接失败，按实际入口改回 authored 坐标或明确的短支街；灵岳南门与炉坊登阶交叉高差已在总图审查中修正。没有放宽 12 格接驳限制。

第三轮魔域触发生成期间源变化的 SHA 护栏；第四轮魔域触发全局双格墙挤占小型两层房楼梯的真实错误。最终输入已将双格墙限于主阁与栈院，但未继续导出复验。父代理要求停止变化期间重复整批导出，待共享代码固定后统一生成七城。本作者遵守。

第四轮已有的三个局部快照分别为灵岳 136 life / 208 tiles、南疆 138 life / 251 tiles、东荒 155 life / 257 tiles；它们都在 192 life 上限内，入口接驳最大12格，原快照的几何/接驳诊断为空。**这三份是旧输入/旧共享源的局部生成快照，不是当前四城同源全部通过。** `final-blocks/` 仅沿用导出参数目录名，其中三城 HTML/scene.json 也保留这一证据范围；魔域没有完成的对应 manifest。

本作者实际正常导出命令（当前不再重跑）：

```bash
python3 tools/province_capitals/regional/v2/export_game.py \
  --out out/preview/province-capitals-v2/terrain-design/resources \
  --preview-out out/preview/province-capitals-v2/terrain-design/final-blocks \
  --regions lingyue nanjiang donghuang moyu
```

## 交接与未验证范围

- 父代理在共享代码固定后重新导出全部七城，保证 manifest、terrain 与 tile 来自当前同一源；本交接不将各轮局部结果拼接为通过。
- 四城均提供全景、入口街景、公共节点、代表院落四个中文命名的 authored 相机。最终截图必须从统一最终施工几何或原生游戏获取，并由父会话逐城展示；本作者的规划 PNG 不能替代这一交付。
- `courtyards` 的实际施工和逐栋 `wall_thickness` 已作为共享接口交给引擎作者；最新实现已看到相应消费，但当前输入尚未经过统一导出。魔域两栋双格厚墙的最终家具/楼梯容量保留待复验。
- 单测套件、Gradle、编译/JAR、Minecraft、云端、NBT独立回读均 `not_run`。本作者只做正常生成和上述轻量输入审查。
- 原生入口安全、碰撞、支柱、自然地形接缝、NPC岗位与寻路、地图确认/取消传送由父代理安排基础验收；玩家外观、手感、音效、FPS、多人均为 `not_verified`，不扩展交易细节。
