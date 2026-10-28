# 七州 v2 建筑与方块生成器

此入口独立消费七州 authored JSON，沿用 `capital_game_bundle` schema 1。旧 v1 生成器、handler、中州和旧世界资源没有迁移。最终基础 Colab、截图画廊和玩家验收由父会话安排；本工具不会启动 Gradle、Minecraft、云端或服务器。

## 所有权与入口

- authored 家族：`blueprints/province_capitals/regional/v2/building_families.json`。
- 实现：`tools/province_capitals/regional/v2/{common,buildings,city,export_game,preview,showcase,test_geometry}.py`、`__init__.py`、`viewer.js`、`template.html`。
- 公共新增：`tools/buildgen/groups.py` 仅追加七个 v2 group。所有屋顶均使用公共 `buildgen.ops` 已注册 handler，没有修改旧 handler 或区域 import 注册。
- 城市 JSON、州域 style 由两位城市作者所有，本生成器只读消费。生产输出只写新七城 v2。

正常生成全部七城：

```bash
python3 tools/province_capitals/regional/v2/export_game.py
```

指定城市、输出和预览目录：

```bash
python3 tools/province_capitals/regional/v2/export_game.py \
  --out build/generated/province_capitals_v9/resources \
  --preview-out out/preview/province-capitals-v2/final-blocks \
  --regions donghai ximo beiyuan lingyue nanjiang donghuang moyu
```

默认根为 `build/generated/province_capitals_v9/resources`，仅生成 `data/myvillage/capitals/v2/capital_<region>/{city.json,terrain.bin.gz}` 和 `data/myvillage/structure/province_capitals/v2/capital_<region>/tile_*.nbt`。不写共享 index 或 frozen，不向新根复制中州。V9 owning 聚合器分别读取新根七城与旧根 `build/generated/province_capitals/resources` 的中州 v1。

## 输入与精确锚点

`cities/<region>.json` 的必需字段为 `region_id,city_id,name,group_id,dimensions,source_offset,placement,terrain,construction,roads,buildings`。诊断消费 `districts,design_notes,design_cameras`，也兼容平地作者的 `viewpoints`；施工消费 `terrain_patches,courtyards,walls,trees,water_top_y,roof_snow`。建筑记录包含 `id,name,role,family,variant,position,size,stories,facing,road_id,district_id`；`primary_service:false` 为额外公共建筑提供独立岗位键。

`size=[w,d]` **包含屋檐、院落地坪和前场**，是未旋转的实际完整占地，不是墙线尺寸。奇数、偶数直接保留，不扩大、不取奇数、不重新取中心。局部格为 `x∈[0,w)`、`z∈[0,d)`，映射为：

```text
world = position + rotate(x-floor(w/2), y, z-floor(d/2), facing)
south=0，west=1，north=2，east=3；+X东、+Y上、+Z南。
```

例如宽 8 的局部 x 占 `[-4,3]`；宽 9 占 `[-4,4]`。四种朝向的地坪、墙、屋顶、fixture、bounds、入口和生活点均使用这个函数。`position.y` 已是首层站立空气 Y，架空长屋不再偷偷加高。外部 `entrance` 位于 lot 的**前缘 z=d-1**；其横向位置由实际门洞所在翼体推导。内部门洞到前缘有真实地坪和净空通道。因此作者应按导出的 `entrance` 而不是包围盒几何中心验看接路。

建筑 `construction` 可逐栋覆盖城市参数。外墙 `wall_thickness` 明确支持 1、2；内室按厚度侵蚀，门窗贯穿全部厚度，工作设施和双格床候选来自侵蚀后的安全格。小地块若不足真实楼梯或家具空间会报错，不自动降厚或增大尺寸。楼梯优先 3×3 回折，窄楼采用连续直跑/折跑，必须有逐阶净空和独立上层落地格。

## 15 族与 60 个结构变体

家族配置为每族四个明确翼体表：`bounds` 是千分比墙线范围，映射到 `[1,w-2]×[1,d-2]`，`upper` 决定该翼是否跟随 authored 层数，`ridge_axis` 决定屋脊轴。窄翼在 lot 内保持至少三格内宽，不改变外部实际尺寸。`variant=0..3` 改变连贯主翼、前场进深、高楼位置、廊口及屋顶组织，同尺寸同材质下每族四种几何摘要不同。

| 家族 | 形态 | 单格外墙建议最小 w×d |
|---|---|---|
| lane_house | 深屋、后庭、短屋前场、前厅后楼 | 7×9 |
| shop_house | 前铺后楼、偏巷深铺、侧坊前堂、纵向长铺 | 9×13 |
| l_court | 西/东曲尺翼及不同高翼和院深 | 15×15 |
| u_court | 正房两厢、偏东楼、偏西楼、浅双厢 | 17×17 |
| row_pair | 共墙齐前沿、两侧退台、长短纵脊 | 17×11 |
| stilt_longhouse | 两侧长廊、横厅后屋、宽前廊后楼 | 9×15 |
| terrace_house | 后楼前台、左右侧楼、前厅后露台 | 9×13 |
| courtyard_inn | 后客楼、偏侧客楼、开前场、双后楼廊院 | 17×17 |
| herb_hall | 后堂药廊、东药楼、侧晒场、前堂后宿 | 13×13 |
| forge_yard | 两侧作坊、横炉堂侧库、长工坊 | 13×13 |
| market_hall | 横开市厅、双廊厅、纵货廊、前厅后库 | 13×11 |
| guide_pavilion | 横亭、纵亭侧廊、深前场、偏东书亭 | 7×7 |
| gatehouse | 双阙通楼、退关楼、左右高楼 | 13×9 |
| civic_hall | 双前翼、西阁、东阁、前仪厅后高堂 | 15×15 |
| warehouse_yard | 双卸廊、左右长仓、横库短翼 | 13×13 |

上述建议没有提高协调已约定最小住宅地块。双格墙、更高楼层和不同参数可能需要更多内室，由生成时的几何检查明确判定；不能用最小表代替实际内部检查。

住宅含真实双格床、储物和工作台；连宅有两户床，店住有铺面设施。客栈含住店床、两组客床、柜台、厨房及可用区域的客房分隔。药堂有酿造台、药架与炼药釜，锻造院有铁砧、炉台、锻造台和储燃料，市场有多个货摊，向导有讲台。设施、其相邻站立空气、真实支撑及 life 声明出自同一局部格，最终施工再次核对不会穿墙、漂浮或被别栋覆盖。

主服务键保留 `inn_work,inn_rest,inn_social,guest_west,herbalist_work,herbalist_rest,smith_work,smith_rest`。life 固定上限 192；住宅仅一 rest、一 social，额外床不膨胀全局岗位。城市还需具有 entry、market、cultivate。重复主服务、重复地块、缺设施、站位被覆盖、无实际入口地板或超出短距连接上限都明确失败。

## 地形、短接与稀疏施工

天然地形仅保存 `width×depth` 的 top-solid int16 高度场。街坊台地由 authored `terrain_patches` 连续生成；普通建筑地基贯通到实际地面，架空屋不填死底层，柱脚下探地面。道路点的 Y、楼板站立 Y 和地基 top-solid 语义统一：地坪/道路写在 `air_y-1`，楼梯的半格踏面以相邻高度决定朝向。桥和栈道保留河床、水体及真实支柱。

入口只在本楼前场、公共开放格与短距外部空隙连接 `road_id`。连接上限读取城市 `max_access_length`（现有作者值 12），搜索节点上限来自家族库；绝不跨墙或用长 BFS 重新开辟城内主干道。城门使用实际贯通门洞连接自己覆盖到的街路。配置不合适时交还精确建筑 ID、入口、道路和距离上限，作者调整自己的输入。

施工索引为有界 box/cell 操作按 tile 分桶；每次只展开一个 `16×height×16` 的方块字典写 NBT、提取预览，然后释放。不创建整城空气/山体三维字典。显式空气限于室内、门洞和实际道路净空。清理旧 tile 仅检查同城旧 v2 manifest 的明确拥有路径。

## 同源预览与最小自查

`out/preview/province-capitals-v2/final-blocks/<region>/index.html` 是自包含页面，`scene.json` 来自最终 NBT 同批 tile 格；显示实际楼梯屋面、薄门、双格床、墙窗、地板与河床，不使用建筑包围盒代替屋盖。四个 authored 相机映射到 `panorama,street,feature,courtyard`，另有总图 `aerial`。父会话可用 `?view=street` 等固定入口，或调用 `PROVINCE_V2.setView(name)` 截图；合并页可用 `?region=moyu&view=panorama`。

页面只使用内嵌 Three.js 和内嵌 gzip 数据，不启动服务或修改 8765。若父会话发布外部图片和文件，必须使用 `https://assets.ishiki333hhh.win/`。父会话负责最终全景/街景截图并逐城展示；本域不把离线相机当成原生碰撞、纹理或玩家审美验收。

家族独立展厅和 focused 检查：

```bash
python3 tools/province_capitals/regional/v2/showcase.py
PYTHONPATH=tools python3 -m unittest province_capitals.regional.v2.test_geometry -v
```

展厅在 `out/preview/province-capitals-v2/families/`，每族四变体使用相同材质和尺寸。focused 检查覆盖 60 变体的最小/样例尺寸与 1/2 层、奇偶尺寸四向旋转、几何差异、双格墙贯通门洞与设施安全、次服务键隔离及冷进程公共屋顶注册。正常 exporter 还调用既有 schema1 `life_inputs(...,192)`；正式 V9 聚合器的 `inspect_city` 是后续身份消费者检查入口。

生成清单记录 `coverage`、逐族实际宽深/层数/variant 统计、源文件 SHA-256 和设计相机；输入在一城生成期间变动会拒绝发布混合清单。当前日志和验收结果在本文件交接附录更新。

## 交接附录（共享代码就绪）

本域基线 `a223aafc2c150654f01b6f9f8e4ed9eedf8f8937`，工作区 `/tmp/mc-default-cultivation-20261028`，分支 `feature/province-capitals-redesign-20261029`。只写本节列出的自有源文件和自有 v2 派生物；未修改城市/style、v1 生成器、公共 roof handler、中州、构建或 Java 文件；未提交、切换分支、合并或推送。

精确源文件清单：

```text
blueprints/province_capitals/regional/v2/building_families.json
tools/buildgen/groups.py                              # 仅追加29行v2注册
tools/province_capitals/regional/v2/__init__.py
tools/province_capitals/regional/v2/common.py
tools/province_capitals/regional/v2/buildings.py
tools/province_capitals/regional/v2/city.py
tools/province_capitals/regional/v2/export_game.py
tools/province_capitals/regional/v2/preview.py
tools/province_capitals/regional/v2/showcase.py
tools/province_capitals/regional/v2/test_geometry.py
tools/province_capitals/regional/v2/template.html
tools/province_capitals/regional/v2/viewer.js
docs/province-capitals-v2-generator.md
```

机器交接为 `out/preview/province-capitals-v2/generator/handoff.json`：含12个可执行/配置源文件的 SHA-256、七城当前输入与 style 摘要、逐族实际尺寸/层数/变体分布、检查边界和预览根。`ready-input-status.json` 记录当前七城构造及前坪实际 deck/pier/保留街阶列数，**属于冻结前的当前模型检查，不冒充最后统一导出的资源清单**。

| 当前模型 | 建筑 | 住宅 | life | 施工前坪 |
|---|---:|---:|---:|---:|
| 东海 | 71 | 62 | 138 | 2 |
| 西漠 | 69 | 61 | 136 | 3 |
| 北原 | 74 | 65 | 145 | 3 |
| 灵岳 | 68 | 61 | 136 | 6 |
| 南疆 | 70 | 62 | 138 | 7 |
| 东荒 | 79 | 70 | 155 | 6 |
| 魔域 | 71 | 63 | 140 | 7 |

合计502栋、444栋住宅，当前七城构造、主服务、实际fixture、入口地板/净空及12格短接检查均通过。库中15族各四变体均已实现；实际城市按 authored 地块选择，不强行使用每族每个变体。精确 `actual_sizes`、`variants`、`stories` 计数以 `handoff.json/current_model_statistics` 为准。

已实际执行的正常生成：

```bash
python3 -u tools/province_capitals/regional/v2/export_game.py --regions lingyue
python3 -u tools/province_capitals/regional/v2/showcase.py
```

初次灵岳生成日志 `generator/first-export.log` 记录68栋/61住宅、136 life、207 templates、NBT 364277 bytes、约4.68秒；其 manifest 已通过真实 V9 `inspect_city(...,192)` 消费者检查（schema、资源版本路径、SHA-256、地形长度、tile边界、主服务键）。**这次导出早于最终厚墙和共享前坪代码，不能当成最终七城交付**。按父会话最新协调，由父代理冻结全部源后只统一正常导出一次七城，并据此更新最终预览、资源清单和 V9 聚合结果。

家族真实方块展厅已生成并解包核对：`families/index.html` 含15族、60建筑、四机位；每族独立页为 `families/<family>/index.html`。初次正常方块预览在 `final-blocks/lingyue/index.html`，最终七城仍使用本文件前述命令和四机位接口，由父代理截图、展示。本域没有启动服务或修改8765。

检查日志均在 `out/preview/province-capitals-v2/generator/`：

- `geometry-tests-final.log`：7项 focused checks 全部通过，包含240组最小/样例×1/2层家族构造；`py_compile`、`node --check viewer.js` 和 scoped `git diff --check` 通过。
- `family-showcase.log`、`first-export.log`：已正常执行生成。
- `ready-input-check.log`、`ready-input-status.json`：七城当前轻量构造结果。
- `first-input-check.log`、`seven-input-check.log`、`latest-input-check.log`、`shared-code-ready-input-check.log`：保留早期地块重叠、接路、厚墙小楼和新前坪kind的真实失败，不拼接成最终全部通过。
- `legacy-comparison.json`：本域最后修改阶段前后，对旧根已有6179个v1 manifest/NBT/terrain/frozen文件逐字节SHA比较，变化0。它不代替父代理持有的起点基线和重新生成旧资源身份比较；新增group注册会影响旧manifest的诊断source.hashes，仍须按协调单独报告。

冻结前还需父代理/作者核对的精确诊断（不会静默抹掉）：

- 灵岳 `lower_switch` 中心采样 `[126,27,174]`、`[126,27,173]`，`upper_switch` `[24,34,84]`、`[24,34,83]`、`[52,41,82]` 位于同路更高一级铺面内。这是折返路宽在邻近段相互覆盖的诊断，原生逐格通行尚未验证。
- 东荒 `south_w [72,17,406]`、`south_e [88,17,406]` 碰南门楼的墙/窗；由城市作者核对支路与门楼实际占地，不用清墙代替解决。
- 西漠 `public_well` 的原遮阴角柱占街路，保留实际铺地并记录 `shade=blocked_by_authored_road_or_building`；`inn_square` 已建连续顶和真实柱。若该井前坪仍要求遮阴，作者须提供可用的角柱空间，生成器不把柱子插进街道。

前坪 `kind` 当前配置支持 `square,quay,wagon_court,work_yard,well_court,drying_yard`；缺省 `square`。深低地及 quay 使用稀疏支柱，保留河床和水；已有街阶列保持自己的Y和NBT，房间占地不覆盖。架空屋柱脚在水体之后施工，避免水覆盖水下支柱。

原生 Minecraft、Colab、Gradle、多人、FPS、声音、手感与玩家外观均未在本域执行。最终全景/街景、V9统一资源及基础云端验收交给父会话；交易细节不在本轮验收范围。
