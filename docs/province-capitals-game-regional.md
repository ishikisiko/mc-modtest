# 七州主城正式方块资源交接

本交付属于 `regional`，完成七州资产源和 `capital_game_bundle schema=1` 资源导出。工作区为 `/tmp/mc-default-cultivation-20261028`，既有分支 `feature/province-capitals-20261028`，HEAD 仍为 `e5dd97ed`。没有创建或切换 checkout，没有提交、合并或推送。中央和运行时的共享改动保持其作者所有。

七城都有 70 栋完整方块民居、客栈、药铺、铁匠、市场、向导、一个主地标和一个对外城门。东海另有两座仓房。建筑有连续墙体、计划门洞、完整注册屋顶、楼板、室内清空、实际床和工作设施；两层建筑有五步双格内梯和楼板开口。采用完整连续主体，不采用分片、悬浮城块或西式尖刺城堡。

这表示七州资源已生成。八城聚合、V8冻结、jar打包、世界放置与 NPC 服务接续由父代理和对应作者负责；本交付不声称八城游戏接入或玩家验收完成。

## 最终资源数量和体量

以下数值直接来自 owning exporter 的最终生成清单，未执行独立回读验证。每行均为一份 `city.json` 和一份 `terrain.bin.gz`，字节数为实际文件大小；NBT 列为该城模板总大小。

| 城市 | 规划格数 X×Y×Z | 民居 / 全部建筑 | city.json 字节 | 高度场字节 | NBT 数量 | NBT 字节 | life 位置 / 设施声明 |
|---|---|---:|---:|---:|---:|---:|---:|
| 东海 `capital_donghai` | 224×96×272 | 70 / 79 | 285972 | 733 | 237 | 503645 | 159 / 252 |
| 灵岳 `capital_lingyue` | 224×96×272 | 70 / 77 | 250000 | 4005 | 156 | 306969 | 159 / 238 |
| 西漠 `capital_ximo` | 224×96×272 | 70 / 77 | 273942 | 1946 | 226 | 299638 | 159 / 236 |
| 南疆 `capital_nanjiang` | 224×96×272 | 70 / 77 | 269715 | 1635 | 230 | 460425 | 159 / 242 |
| 北原 `capital_beiyuan` | 224×96×272 | 70 / 77 | 261198 | 528 | 233 | 405600 | 159 / 238 |
| 东荒 `capital_donghuang` | 160×112×416 | 70 / 77 | 274884 | 1311 | 238 | 388556 | 159 / 242 |
| 魔域 `capital_moyu` | 224×112×272 | 70 / 77 | 258979 | 1427 | 202 | 334799 | 159 / 238 |

合计 490 栋民居、541 栋建筑、1113 个 life 位置、7 份城市 JSON、7 份高度场、1522 个 NBT。七城正式资源合计 **4585907 字节**，其中 NBT 2699632 字节、城市 JSON 1874690 字节、高度场 11585 字节。体量不含预览、日志、父代理索引或中州文件。

| 城市 | 最终 NBT 记录数 | 必需显式空气记录数 |
|---|---:|---:|
| 东海 | 170683 | 53201 |
| 灵岳 | 91641 | 43422 |
| 西漠 | 94585 | 47242 |
| 南疆 | 148050 | 67571 |
| 北原 | 124068 | 59785 |
| 东荒 | 116477 | 60264 |
| 魔域 | 99237 | 48809 |

高度场负责整片土石地基，NBT 仅记录建筑、桥、支承、道路覆盖、水、河床、设施以及室内和通路净空。没有枚举全城空气或实心山体。生成器只持有一个 `16×cityHeight×16` tile 的最终方块字典，构造操作按 tile 索引，随后逐块写出。

## 来源与七城完成程度

- [game-cities.json](../blueprints/province_capitals/regional/game-cities.json) 是七城正式整数布局和施工参数的 authored 输入；七个新 style 的材质槽分别位于 `tools/buildgen/styles/province_capital_<region>.json`。
- 东海、灵岳继续引用 [cities.json](../blueprints/province_capitals/regional/cities.json) 中原住宅行列、道路、服务建筑、城门与地标坐标。原 OBJ 和三角网格没有作为正式资源输入。建筑中心取整数格，宽深取覆盖中心的奇数格，一源单位对应一格；不是将浏览器 0.2m 网格成倍放大。
- [remaining-regions.json](../blueprints/province_capitals/regional/remaining-regions.json) 保留五州风貌说明，并指向新增完整游戏输入；旧两城预览产物仍是其历史阶段，不代表当前七城资源缺失。
- [block_model.py](../tools/province_capitals/regional/block_model.py) 拥有整数地形、地块、绕开房间的入巷支路、建筑和设施；[export_game.py](../tools/province_capitals/regional/export_game.py) 拥有高度场、稀疏 NBT 与城市清单的正式写入。

| 城市 | 已落地的地域差分 | 保留的细节范围 |
|---|---|---|
| 东海 | 原纵向滨海街区、真实外海水面与沙质海床、三条落地桩柱栈桥、两仓院、观海楼；民居全部补成正式墙/屋顶/楼板/家具 | 没有潮汐、港外航行和居民商贸运行证据 |
| 灵岳 | 原四层住区和连续山体、回折石阶、山腰谷桥、上层主街与制高点宽体厅堂；局部地块土方 | 山路碰撞、楼梯寻路和自然地貌接缝未验收 |
| 西漠 | 七组十户内向院落、公共绿洲盆地、水源外环、连续砂岩平屋盖和实体遮阴廊；绕开公共建筑的外联街 | 院内杂物、个性装修和水源玩法未实现 |
| 南疆 | 两岸各35户架空木楼、真实支柱到河岸/溪床、深檐屋盖、三桥与南门木廊、西岸地表14到架空门廊17的有支撑入城阶道、独立低位排水沟和林缘 | 排水是静态方块水工，没有动态水文模拟；药圃生产未实现 |
| 北原 | 五组双排背风院巷、围墙、中央宽街、东侧服务带、完整陡坡屋盖、受实心屋脊支撑的积雪和雪土地面 | 屋面是局部真实积雪，不宣称整屋覆雪；天气和热量没有专门模拟 |
| 东荒 | 独立160×416长谷形、谷侧70户、折行驿道、中段交易厅、南口驿站和北端关楼、两侧林缘 | 自然峡谷与外部驿路的游戏过渡未验收 |
| 魔域 | 裂谷西岸 Y=20／东岸 Y=26 的独立生活街区、三座有基脚的桥、完整东岸主阁、暗石和低饱和赭墙 | 居民来源交 runtime；不把普通宗门准入或空服务锚点当成已有常住居民 |

全部住宅已从外壳补成有最小生活设施的方块房屋；完整住户专属装修、院落杂物、药铺/锻造玩法和 NPC 日程不在这次完成范围。建筑轮廓和入口只是生成结果，没有通过碰撞、寻路或玩家验收。

## 固定导出与目录

实际执行的正式资源生成命令：

```bash
cd /tmp/mc-default-cultivation-20261028
python3 tools/province_capitals/regional/export_game.py --out build/generated/province_capitals/resources
```

不传 `--out` 时也输出到上述默认资源根。导出器不调用 `tools/province_capitals/game/compile.py`，也不依赖 runtime 或 central 新增 Python。只覆盖七个自己拥有的城市族；旧清单中被移除的本城 tile 可按本城旧 manifest 删除，其他城市和未知文件保留。没有清空共同 root。

每城正式路径：

```text
build/generated/province_capitals/resources/
  data/myvillage/capitals/v1/capital_<region>/city.json
  data/myvillage/capitals/v1/capital_<region>/terrain.bin.gz
  data/myvillage/structure/province_capitals/v1/capital_<region>/tile_<chunkX>_<chunkZ>.nbt
```

`city.json` 仍为父契约 `format=capital_game_bundle, schema=1`，没有另造格式。扩展字段包括建筑/道路说明、完整 palette 状态、fixture、生成数量、材质解析来源和未完成事项。`layout_family_id` 原样读取共享 catalog 的 `layout_id`。未自行伪造包级 `layout_sha256`；父聚合器负责稳定汇总冻结身份。

所有坐标使用同一规范本地空间：X/Z非负，整数格，七城 `source_offset=[0,0,0]`，旋转均0。`size` 的X/Z是16倍数。`terrain.bin.gz` 是 `gzip(mtime=0)` 的小端int16，索引 `z*width+x`，记录**最高实心地基格Y**；不是站立空气Y。楼板和普通道路位于 `floorAirY-1`，上坡楼梯叠加在其下方实心基底上；桥和架空层不抬高下方河床/裂谷。水从真实河床上方填至配置水位。

每个模板的 `size=[16,cityHeight,16]`，包含标准 `palette/blocks/entities=[]/DataVersion`。`tiles[].x/z` 是城市原点起算的**方块偏移**（0、16、32……），不是 chunk 序号；文件名中的数字才是 chunk 序号。高度场摘要和每个 NBT 摘要均由实际写出文件计算 SHA-256；各城 `source.hashes` 保留输入、构造器、注册源、既有 roof/nbt/modset 组件摘要，没有时间戳加入几何身份。

本批材质采用既有 `load_modset("vanilla")` 和 style 槽解析，全部为 `minecraft:`。当前工作区缺少可选 `exmod/mod_block_catalog.json`；vanilla profile 原实现不依赖外部 catalogue，因此 JSON 明确记录 `external_catalogue=null, external_catalogue_required=false`，没有捏造摘要或外部方块合法性。真实注册表有效性留给 Terra，不能将 namespace 解析视为注册验证。

## life 接口与运行时交接

每城 `life.places` 都是 **159 个**，低于父协调的 V8 `life_places_limit=192`；输出没有依据上限截断。没有修改旧世界128上限、旧断言、Java或公共 schema。中州实际容量由中央作者和父代理另行处理，本作者没有修改其中任何位置。

复用键包括 `inn_work/inn_rest/inn_social/guest_west`（另有 `guest_center/guest_east`）、`herbalist_work/herbalist_rest`、`smith_work/smith_rest`、`market_work/market_social`、`guide_work/entry_social`、`main_entry` 和 `cultivation`。kind 使用既有 `rest/work/social/market/entry/cultivate`。住宅有各自 rest/social 位置，标识不重复合并为一栋样本。

`pos` 是房间或门廊的站立空气格，床位位置另指相邻站立格，`block_pos` 指向真实床脚/工作设施。床由 `occupied=false` 的 head/foot 两半写入；客栈有掌柜床和三张客床、厨房炉子、储物与柜台。药铺有真实酿造台、药柜和炼药锅，铁匠有真实铁砧、熔炉、锻造台；市场有货桶、向导亭有讲台。楼层位置与所有建筑/地形使用同一本地坐标。

这些设施和位置进入了生成源与资源，不代表 NPC 已绑定。`life.binding=runtime_owned_not_bound`，魔域另有 `resident_source_hint=runtime_worldsim_moyu_city_population_required`。运行时应通过 WorldSim facade 幂等绑定 `cityId`、观察真实已加载设施和可站立条件，缺居民或设施未加载时保留空缺。不得把 fixture 声明数量当 NPC 数量。

## 八州 group/style/roof 注册用法

本作者独占新增了 `tools/buildgen/groups.py` 的八个条目，旧条目不改：

| group_id | style_id | layout_strategy | owning exporter |
|---|---|---|---|
| province_capital_zhongzhou | chinese_huipai_mansion | full_yunshan_authored | central/export_game.py |
| province_capital_donghai | province_capital_donghai | coastal_harbour | regional/export_game.py |
| province_capital_lingyue | province_capital_lingyue | supported_ridge_terraces | regional/export_game.py |
| province_capital_ximo | province_capital_ximo | oasis_courtyard_routes | regional/export_game.py |
| province_capital_nanjiang | province_capital_nanjiang | stream_bank_boardwalks | regional/export_game.py |
| province_capital_beiyuan | province_capital_beiyuan | sheltered_courtyard_streets | regional/export_game.py |
| province_capital_donghuang | province_capital_donghuang | long_valley_relay_streets | regional/export_game.py |
| province_capital_moyu | province_capital_moyu | connected_rift_neighbourhoods | regional/export_game.py |

生产端按完整 id 调 `get_group("province_capital_<region>")`，通过描述符的 `style_id/layout_strategy/scale_params["exporter"]` 衔接；不按名称前缀 dispatch。中州主材 style 与中央 authored 的 `materials.style=chinese_huipai_mansion` 对齐，`auxiliary_style_ids=("cultivation_sect",)` 记录其既有主殿辅材风格；未修改中央源。中州 group 明确 `target_scale=full_yunshan`，范围由中央 authored 源拥有，未施加 D1 footprint 上限。已有 style→group 首次匹配顺序保留。

七州复用现有 `chinese_overhang_gable/chinese_flush_gable/chinese_half_hip` 处理器。只新增了 [forms.py](../tools/province_capitals/regional/forms.py) 中的 `province_oasis_terrace`：连续砂岩屋盖和低矮围沿，经 `register_roof` 注册。区域 exporter 导入该 owning module 后按配置 roof id 调 `roof_handler`；若其他 Python 消费者需要加载西漠 style，也应先导入该模块进行注册。没有修改原 `ops.py`，没有用名称前缀替换 roof，也没有新增全局 motif 行为。

## 审阅产物与证据边界

- [七州切换三维HTML](../out/preview/province-capitals/regional-game/index.html)
- 单城HTML：[东海](../out/preview/province-capitals/regional-game/donghai/index.html)、[灵岳](../out/preview/province-capitals/regional-game/lingyue/index.html)、[西漠](../out/preview/province-capitals/regional-game/ximo/index.html)、[南疆](../out/preview/province-capitals/regional-game/nanjiang/index.html)、[北原](../out/preview/province-capitals/regional-game/beiyuan/index.html)、[东荒](../out/preview/province-capitals/regional-game/donghuang/index.html)、[魔域](../out/preview/province-capitals/regional-game/moyu/index.html)
- 各单城同目录 `scene.json` 保留显式审阅几何及对应正式资源 manifest。
- [资源清单](../out/preview/province-capitals/regional-game/resource-inventory.json) 与 [最终正式导出日志](../out/preview/province-capitals/regional-game/generation.log)。

预览直接消费与 NBT 写入相同的最终 tile 字典，没有执行 NBT 回读验证。HTML 将方柱尺寸/位置压缩为1/32格数值列，将完整整数地形压缩为高度场，再由显示层恢复；未减少房屋或城市范围。床、栏杆和工作设施使用方块状态的近似显示轮廓，材质采用块名配色，不是 Minecraft 客户端截图。Three.js 与场景均自包含，无外部资源、服务器或新端口。

另外执行过仅更新HTML包装的 owning 生成命令；最终正式导出也会按相同压缩方式重建HTML：

```bash
python3 tools/province_capitals/regional/game_preview.py --root out/preview/province-capitals/regional-game
```

父代理负责人工展示；若发布资源链接，使用 `https://assets.ishiki333hhh.win/` 并保持资源路径，不修改或重启既有8765服务。本作者没有启动服务、浏览器或截图工具。

首次正式生成在缺失可选 external catalogue 的来源摘要阶段失败，记录位于 [generation-failures/01-missing-optional-catalogue.txt](../out/preview/province-capitals/regional-game/generation-failures/01-missing-optional-catalogue.txt)。修复为明确的 vanilla 无外部依赖语义后重新生成七城，最终清单不拼接失败阶段或中间源的证据。

**所有测试均 `not_run`**：单测、validator、check、lint、Gradle、编译、Minecraft、云端、聚合器、NBT独立回读和浏览器均未运行。碰撞/寻路、自然地形放置、设施可用性、NPC绑定、玩家外观、手感、音效、FPS和多人均为 `not_verified`。后续测试/编译只交 Terra xhigh；本作者没有再派代理。
