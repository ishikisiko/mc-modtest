# 中州云山正式方块资源 v1 交接

工作区 `/tmp/mc-default-cultivation-20261028`，既有分支 `feature/province-capitals-20261028`，接手起点 `e5dd97ed`。本实现只写 central 作者源、central authored 输入、本文和中州自己的生成族；未提交、合并、推送或操作旧存档。公共目录、家族注册、Java、Gradle 和区域作者文件由父契约的其他作者维护。

本轮获准从前批整体方向进入正式资源化。产物是可供新 V8 runtime 消费的 Minecraft 方块资源，不是把浏览器白模改名后打包。**资源生成不代表游戏生成成功。** 本作者没有运行 tests、validator、check、lint、编译、Gradle、Minecraft、云端或浏览器验证；后续由 Terra xhigh 执行。

## 入口与资源身份

在指定工作区正常导出：

```sh
python3 tools/province_capitals/central/export_game.py --out build/generated/province_capitals/resources
```

默认 `--out` 即上述目录。只覆盖 `capital_zhongzhou` 的资源，不清空共享 root。输出路径为：

- `data/myvillage/capitals/v1/capital_zhongzhou/city.json`
- 同目录 `terrain.bin.gz`、`buildings.json`、`routes.json`、`waterworks.json`
- `data/myvillage/structure/province_capitals/v1/capital_zhongzhou/tile_<x>_<z>.nbt`

`city.json` 使用 `capital_game_bundle schema=1`；`city_id=capital_zhongzhou`，`settlement_family=province_capital_zhongzhou`。后者由 regional 作者在 groups.py 注册，本作者没有抢写。`layout_family_id` 读取 shared catalog 的中州 `layout_id`，不拿预览布局 SHA 当游戏内容摘要。真正 terrain、tile 摘要逐文件写入描述符；包级冻结由父聚合器执行。来源和状态不冒充几何身份。

`buildings/routes/waterworks.json` 是辅助交接数据；可玩的方块来自 heightfield 和 NBT，服务岗位来自 `life.places`。辅助数据不要求 runtime 为查询加载全城。父聚合器冻结其消费的 terrain、tiles、entries、life 等几何真值；本作者未运行聚合器。

旧 `out/preview/province-capitals/central/layout-original.json` 与 `layout-adapted.json` 仍是前批 preview exchange，顶层 manifest 仍是索引。正式生成不依赖被移出的 generated blueprint 副本；blueprints 新增的仅是 authored `game-spec.json`，旧 `preview-spec.json` 提供高度映射输入。旧 V4、D1、web/yunshan-city 以及 tools/yunshan 均未改。

## 真实范围和导出方式

直接运行原作 `buildPlan(1)` 与去掉墙带后的 `createField`，按 1 格采样 1200×1440 范围。X/Z 统一归零：局部 X=原作 X+600，Z=原作 Z+840；`source_offset=[-600,0,-840]`。`size=[1200,320,1440]`。所有入口、岗位、方块、道路记录共用该坐标系。

terrain 是 gzip(mtime=0) 包裹的 z-major 小端 int16，每列记录**最上层实心地面方块** Y；水域记录河床，不记录水面或站立空气。原作城区与山水范围不缩成 D1，也不把建筑并入山基地表。

山基只保存在 1,728,000 项数组中，不枚举几亿个地基/空气方块。结构写入 append-only 磁盘 spool，导出时只解析一个 16×320×16 区块列；标准 gzip NBT 含 size、palette、blocks、entities=[]。室内和通路仅写必要显式空气。没有叠加内容的列不导出。导出摘要和统计来自实际最后写出的区块列，非白模包络或历史证据。

原作树木只调用小型专用 `buildTree`，每个 unique 模板一次，向下归并成 1 格树体，再在原实例位置投放；没有调用 0.2m 全城体素器，更没有体积扩大 125 倍。道路、建筑、墙和水边留净空，超出本次山水保留范围的树不跨界写入。

本轮最终正常导出为 `20261028T171313914720Z`，命令 exit=0；以下只属于生成统计：

| 产物 | 实际数量 / 大小 |
|---|---:|
| 建筑与桥实例 | 662，含 587 民居、28 商铺、1 茶楼、46 个其他地标/桥 |
| 可复用真实方块模板 | 57 |
| 一格地形列 | 1,728,000 |
| 地形原始 / gzip | 3,456,000 / 398,207 字节 |
| 稀疏 NBT 区块列 | 4,636 |
| 正式资源文件总数 | 4,641（4,636 NBT + city/terrain/3 份辅助 JSON） |
| 正式资源总大小 | 12,846,525 字节，约 12.25 MiB；不含展示页和中间 spool |
| 最终稀疏方块记录 | 2,674,175，含必要空气 311,991、水块 152,088 |
| 最大一次解析的区块列 | 11,396 条记录；不是整城三维字典 |
| 真实建筑最高 Y / 地表范围 | 287 / 55～273；无建筑触发降低基座 |
| 道路与接驳记录 | 691 条路线，含 638 条逐栋地面入口接驳 |
| 生活位置 / 容量上限 | 20 / V8 192，未截断 |
| 原位树木 / 灯具 | 7,051 / 139；居民实体 0 |

本次原作 `buildPlan(1)` 读到 9,051 个树实例，其中 1,822 在本次 1200×1440 范围外，178 为入口、道路、墙和水边净空主动避让；不沿用前批说明中的树木估计数。

最终地形 SHA-256 为 `71e623939f3d5675afbd4a50453b34314cd88e9b1649b53e83ee19c94b4d234b`；各 NBT 的实际摘要在 city.json，包级身份仍由父聚合器冻结。

## 可进入建筑

- 587 民居、28 商铺及 1 茶楼逐实例保留；一格占地量化，楼体不套地形的非线性高度压缩。民居保留徽派白墙、江南墙体或木构差异；有连续墙体、双格门、实木楼板、玻璃窗、家具和床。多层房屋有真实楼梯及楼板洞口。
- 住宅使用现有注册硬山、悬山、卷棚屋顶；殿宇、亭塔与鼓楼使用现有歇山、庑殿、攒尖、重檐、塔冠和鼓楼处理器。调色板通过既有 modset 的 vanilla profile 解析，不依赖缺失的外部 mod catalogue，不新建 namespace 白名单。允许的屋顶词汇由 central authored 输入明确列出。
- 宫殿有台基、宽门、前后出入口、柱列、连续厅堂及入口台阶。原作主殿与上层两侧殿实际占地重合，正式实现为精确对齐的共用台基，侧殿台基对齐到 3 格，并补连通廊口；主殿和侧殿位置、主体宽深和屋面范围保留。该处是显式 named compound，其他相交仍使导出报错。
- 观瀑阁保留五层，凌霄塔保留十三层，西岭楼保留三层；层间楼梯贯通。亭保留连续多边形台基与开放柱间；城门有真实通洞和上层楼梯，水门保留河道穿口。敌楼底层四面有门，城墙内侧留步行顶面、外缘设置垛口。独立城外石柱亭有可站立入口，但不虚构跨云海陆路。
- 六座桥都按真实本地 X 长轴放置，正式 q=0，原 rot=1 保留在来源字段；修正原作“几何已沿 X 但实例又旋转”的问题。拱桥有弧形台阶桥面、护栏、桥台和承托，廊桥另有柱列和注册屋顶，桥头与两岸平台衔接。

## 高度、街阶和水工

沿用前批主世界分段地表映射，城区原 38～134 区间保持 1:1 地表高差；高山与上台地压缩。中州固定 `absolute / anchor_y=0`，不再整城加 Y。`blend_margin=128` 由 runtime 消费，本作者不额外铺一个抬高全城的外缘。

每栋先生成真实方块模板，再按真实最高方块核算顶空；若确有超过预算的地标，降低该建筑基座并记录 `height_adjustment`，不压扁楼体。最终高度和调整数量见本轮正常生成报告，不能引用白模的 Y≈286.2 作为本轮建筑证据。

街道及原小数街阶量化到半格表面，生成楼梯/半砖/实心平台，陡段补几何折返；heightfield 记录道路实际最高实心块的 Y，半砖也不误写成站立空气格。建筑门外逐栋接驳真实街路，在寻路前建立墙体占用，避开其他建筑地基、封闭墙体和水体。宫城东侧路线避开侧殿屋面，三台地各补有实体门楣的东侧小门；宫后另补真实后门。新增宫后—瀑潭、瀑潭西岸—瀑潭亭支撑栈道、观瀑阁阶梯缺口及峰顶收口。原作飞行 tour 完全不作为步行路径。生成的接驳不等于碰撞或映射按键步行通过。

水体有 4 格深实心河床，水块覆盖其上；邻岸有承托和高于水面的封边。飞云瀑、六处河道内跌水和南出水瀑有石堰、背衬、侧封边、落水片和下方承托；南出水补有限集水池，避免蓝面悬空或任意流散。保留上游—飞云瀑—瀑潭—七级池—镜湖—水门出水关系。原作是七级池、六处内部跌水，加南出水构成七处跌水，飞云瀑另列，未虚增一处原作内部跌水。

飞云瀑等竖向落差受主世界映射压缩，不能称完全复刻原作竖向尺度。流体更新、跨区块防漏、桥下净空与实际步行均需 Terra 原生验证；当前只交付实心承托、封边与水块资源。

按最终整数方块水工，飞云瀑从原作 102 格落差变为 51 格；南出水从 243 变为 24 格。六处河内落差依次为 12、12、16、16、16、24 格。

## 生活设施与岗位

五处服务均落到南门—御街—市集附近的原作真实商铺，不新建全城常驻实体。客栈有两套互不共用的真实床：`inn_rest` 对应当值者床，`guest_west` 保留为未占用客床；有吧台、炊具与社交位置。药铺有酿造台、药缸及储物；铁匠有锻造台、熔炉和铁砧；市场有货柜/箱桶；向导所有讲台、工作位和真实住处。

`life.layout_id=capital_zhongzhou_v1`；共同岗位含 `inn_work, inn_rest, inn_social, guest_west, herbalist_work, herbalist_rest, smith_work, smith_rest`，另有各入口、市场、向导、修炼与社交位。岗位 pos 是显式站立空气格，fixture 坐标由最终模板同一旋转变换导出。实际 NPC 身份、职业、服务营业和货栈仍交 runtime 的 WorldSim facade 幂等登记，本作者不造人或库存、不宣称岗位已可用。

中州实际导出 **20 个 life.places**，不超过父协调补充的 V8 `life_places_limit=192`，无需扩大模型容量。没有截断清单、修改旧世界 128 上限或改动他人的断言。服务工作位紧邻对应设施，床位旁站立点与床头分别记录。

下面均是归零后的本地坐标，Y 已是主世界绝对高度；不可再加整城 Y 偏移：

| 场所 / 岗位 | 站立位置 | 实际设施位置 |
|---|---|---|
| 客栈 `inn_work` | [553,83,1185] | 木桶 [553,83,1184] |
| 药铺 `herbalist_work` | [540,98,972] | 酿造台 [540,98,971] |
| 铁匠 `smith_work` | [549,86,1139] | 锻造台 [549,86,1138] |
| 市集 `market` | [578,106,921] | 货桶 [578,106,922] |
| 向导 `guide_work` | [547,86,1166] | 讲台 [547,86,1165] |
| 正阳门 `main_gate/city_entry` | [570,84,1236] | 实心入口平台 |
| 修炼位 `cultivation` | [578,84,1239] | 安山岩平台 |

## 生成报告、预览与原始失败

- 正常导出报告：`out/preview/province-capitals/central/game/export-report.json`。
- 实际模板展示页：`out/preview/province-capitals/central/game/index.html`。由本生成器输出、内嵌真实方块模板；材质是示意色，不是 MC 原生截图。本作者未打开浏览器、未调用渲染或检查工具；由父代理展示。
- 每次生成输入、spool 与原始失败留在 `out/preview/province-capitals/central/game-export-runs/<run>/`，失败未删除、未借其他提交结果覆盖。

已发现并处理的正常导出失败包括：主殿与侧殿原作占地相交；瀑潭亭没有岸路候选；瀑潭亭周边真实为水体、必须补栈道；vanilla profile 的来源清单误引用本工作区不存在的 `exmod/mod_block_catalog.json`；预览报告遗漏顶层 `blocks` 统计键。五次失败各自的原始异常保留在 run 目录。本轮通过修改所属 authored 输入和生成源解决，没有运行测试或放宽外部断言。

原始失败 run：`20261028T165305430370Z`、`20261028T165517340415Z`、`20261028T165621445736Z`、`20261028T165749096400Z`、`20261028T170113962040Z`。每个目录有 `failure.log`，源导出另有 `source-export.log`。此前正常成功的生成也保留各自输入，最终交接只引用 `20261028T171313914720Z`，不拼接不同轮次统计。

## 边界与尚未验收

原作 8 艘摆件船的细模型尚未资源化；262 个摆件人物不变成常驻实体，后续由命簿近场投影负责。树种沿用原生成器的体形，方块材质为原版近似；完整逐户独立装潢、原作细灯饰、瓦件细描与题字仍可继续细化。原作 616 普通 lot 已全部有正式建筑，不把这些细节待办当作白模占位的借口。

城外石柱亭仍无原作给定的完整陆路；墙顶坡度、复杂廊口、楼梯转角、个别街路交叉的碰撞与头部净空尚未原生观察。外缘自然融合、自然结构避让、旧世界 FULL 区块保护、V8 实际生成和 WorldSim 服务由 runtime 作者负责；本作者未观察它们运行。不可把本导出器的有界寻路或几何生成称为玩家已走通。

相对原作的明确实现调整为宫殿共台基廊口、三处东侧小门与宫后门、瀑潭亭支撑栈道、南端有限集水池，以及为主世界上限作的地表/瀑布落差适配。格式按父 schema=1 导出，尚未由 Terra 独立检查，不宣称全部契约断言已通过。八城聚合由父代理负责，本作者没有运行聚合器，也没有改动其恢复的 frozen_v7/preset 缓存。

tests / validators / lint / compile / Gradle / Minecraft / cloud：全部 `not_run`。玩家外观 / 步行 / 手感 / 水流 / 音效 / FPS / 多人：全部 `not_verified`。未启动 Terra 或其他子代理；未提交、合并、推送。

源文件：`blueprints/province_capitals/central/game-spec.json`；`tools/province_capitals/central/{export_game.py,source_game.mjs,game_models.py,game_world.py,game-viewer.html}`；本文。派生资源、预览和失败日志均留在忽略的生成目录。
