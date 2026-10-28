# 东海与灵岳主城三维样板

本批是供用户审阅城市差异的 authored 三维样板，状态为 `preview`。两城均以当前云山 D1 的 `224×82×272`、70 民居＋4 功能建筑作为规模参照，独立组织城形，不缩放或改色套用完整云山。两城描述名尚未由用户批准为正式城名。

只执行本工具的交付生成；所有测试为 `not_run`，浏览器运行同为 `not_run`。玩家外观 `owner_appearance=not_verified`。没有提交、合并、推送、启动服务、启动 Minecraft、运行 Gradle 或云端任务；没有修改正式 NBT、生产资产和世界生成规则。后续任何测试由 Terra xhigh 负责。

## 交付入口

- [两城切换、自包含三维 HTML](../out/preview/province-capitals/regional/index.html)
- [东海单城 HTML](../out/preview/province-capitals/regional/donghai/index.html) · [东海 manifest](../out/preview/province-capitals/regional/donghai/manifest.json) · [东海显式几何](../out/preview/province-capitals/regional/donghai/scene.json) · [东海 OBJ](../out/preview/province-capitals/regional/donghai/model.obj) · [材质](../out/preview/province-capitals/regional/donghai/model.mtl)
- [灵岳单城 HTML](../out/preview/province-capitals/regional/lingyue/index.html) · [灵岳 manifest](../out/preview/province-capitals/regional/lingyue/manifest.json) · [灵岳显式几何](../out/preview/province-capitals/regional/lingyue/scene.json) · [灵岳 OBJ](../out/preview/province-capitals/regional/lingyue/model.obj) · [材质](../out/preview/province-capitals/regional/lingyue/model.mtl)
- [本批交付索引与源摘要](../out/preview/province-capitals/regional/delivery.json)

HTML 内嵌仓库本地 `web/yunshan-city/vendor/three.module.min.js`、场景数据与交互脚本，通过 gzip 解压和 Blob 模块载入。无需服务器、CDN 或外部字体；浏览器需要 WebGL 和 `DecompressionStream`。Three.js 原许可证文字保留在内嵌模块中。父代理负责最后通过 `html_preview` / `html_render` 展示，本实现代理没有调用浏览器或渲染工具。

全景可环绕、缩放、平移；街景支持拖拽转头、WASD 和 Q/E 自由相机；还可切俯视与城市重点视角。入口列表可跳至门前，点击建筑显示其细化状态。支持屋顶开关、地名开关、人物尺度开关和灰显未细化住宅。人物高 1.8 格，并设有 10 格地面标尺。自由相机不模拟 Minecraft 碰撞，不能据此声称游戏中已经可通行。

## Owning 输入与生成器

- [两城 authored 输入](../blueprints/province_capitals/regional/cities.json)：海岸／山坡剖面、道路、住宅行列、服务建筑、入口朝向、配色与镜头。
- [其余五州 authored 风貌纲要](../blueprints/province_capitals/regional/remaining-regions.json)：仅字段和布局说明，无五城完整模型。
- [生成器](../tools/province_capitals/regional/generate.py)：连续地形、局部土方、支撑结构、独立建筑体量、路网、显式几何、OBJ、exchange manifest、自包含 HTML。
- [显示层](../tools/province_capitals/regional/viewer.js) 与 [HTML 模板](../tools/province_capitals/regional/template.html)：只消费生成数据，不拥有城市布局。

重新交付只运行自身生成器：

```bash
cd /tmp/mc-default-cultivation-20261028
python3 tools/province_capitals/regional/generate.py
```

该命令不是测试、validator、lint 或 check，没有调用其他生成链。不要手改输出。输入改变后由同一生成器重新生成。正式资源化仍须回到仓库拥有的 group、modset、catalogue 和屋顶注册表；本工具不会偷偷激活生产接入。

## 东海主城

`city_id=capital_donghai`，`region_id=donghai`。

城市沿岸线纵向展开，东侧水口直通外海，三条有落地桩柱的卸货木栈桥连接仓门朝海的两座仓院。仓院外侧留转运空间，滨水连续步道串起市场、客栈与药铺；横街接入内陆民居。陆侧城门直接通主街，没有横跨湾口的封闭城墙。

灰瓦、浅石与木柱檐廊形成沿海建筑语言，主要服务建筑采用有门洞的连续墙体、落地柱、通风檐廊与完整屋盖。观海地标是靠南岸的克制二层楼体，凭岸线位置形成辨识，而非复制巨塔。海面背景由显示层向东延展，用来表达模型窗口以外仍是外海；它与天空、灯光相同属于展示情境，不进入 authored 模型闭包络。

已实现 70 栋民居外壳，其中 18 栋具有代表性详细立面和檐廊；另有客栈、药铺、铁匠、市场、向导亭、两座仓房、城门与观海地标。全部住宅有真实入口和入巷支路；这只是生成的几何关系，未经碰撞、寻路或玩家实机验收。

## 灵岳主城

`city_id=capital_lingyue`，`region_id=lingyue`。

四层住区在一整块从 Y=0 支撑的连续山体上展开。道路与住宅沿等高线排列，东折路、西折路和上岭阶道回折升高；山腰主街跨过真实下切的谷地，谷桥有桥身、桥台和到谷底的支承。上层主街再接制高点主地标。阶地之间保留山坡，只在地块和道路范围局部整地，不把整城削成巨型平板。

建筑采用连续石基、石木屋身与完整灰瓦屋盖。高处主殿是宽体连续建筑，轮廓高差主要来自山地，未使用漂浮楼板、悬空拼片台地或堆叠分片造型。

已实现 70 栋民居外壳，其中 23 栋具有代表性详细立面；另有客栈、药铺、铁匠、市场、向导亭、山麓城门与制高点主地标。未详细区域可灰显，不能把这些住宅外壳叫作最终完成模型。

## Exchange 采纳与路径基准

保留父契约 schema=1 的全部 required keys：`schema`、`city_id`、`region_id`、`stage`、`units`、`bounds`、`source`、`terrain`、`districts`、`roads`、`buildings`、`entries`、`services`。没有修改或导入第一位代理拥有的公共 `schema.py`、`catalog.json`。

采纳 `/tmp/city-plan-research-20261028/coordination-addendum.md`：

- city_id 使用 `capital_donghai` / `capital_lingyue`。
- `bounds_semantics=closed_geometric_envelope`。`bounds` 由生成后的全部 authored 网格与实体柱角点求闭包络，允许小数；不是体素末索引。`authored_urban_frame` 单独保留 D1 量级的规划框，不能拿规划框顶高替代实际几何包络。
- 坐标为 `+X` 东、`+Y` 上、`+Z` 南；q 正向使 `+X→+Z`，绕模型作者原点旋转后平移，不自动归零。模型默认正面为 `+Z`，故朝南 q=0、朝西 q=1、朝北 q=2、朝东 q=3。Three.js 使用 `rotation.y=-q*Math.PI/2`；显式网格和 OBJ 使用相同变换。
- 两城为独立 authored 方案，没有从原作复制 `rot`，因此没有未经转换的源旋转。未来导出原作时仍应转换为 `(4-source_rot)%4`，本工具未冒充做过该转换。
- `terrain.data="scene.json"` 相对 manifest 目录，`data_pointer="/terrain_heightfield"` 指明 JSON 内的数据字段，`path_base="manifest_directory"` 写在清单中。
- `artifacts` 路径也相对 manifest 目录；`source.ref`、`source.generator` 和源哈希路径明确相对仓库根目录。
- 每城写出独立 `profile_id`、`height_profile` 与 `layout_identity`。本批只有独立 authored 的区域高度，没有原高度／适配高度切换；中州两套高度身份由第三位代理负责，本实现未触碰其文件。

扩展字段用于表达规划框、细化覆盖、路径基准、几何数据指针、展示情境与证据状态，未删除 required keys。接口结构无已知偏差；新增消费端尚未运行检查。父代理如需进一步消费，注意 `scene.json` 的实体柱 `p` 已是旋转后世界中心，`q` 只负责该柱局部几何朝向；网格顶点已经是世界坐标，不能再次旋转。`building.position` 是模板作者原点经平移后的位置，位于建筑地面中心；门口位置在 `entrance` 与 `entries` 中显式给出。

HTML 为降低传输量，只省去规则高度场可推导的重复网格索引；显示时由同一高度数据恢复。显式 `scene.json` 与 OBJ 保留全部几何，住宅数量与城形没有因此简化。`scene.json` 不依赖公共 schema 实现，布局身份也不代替正式生产布局哈希。

## 未完成与证据边界

未完成全部住宅室内、其余院落的细化、NPC 生活、商业与住宿服务运行、药铺／锻造玩法、正式 Minecraft 方块化、生产世界生成和正式 NBT。药架、货堆、柜台、铁砧与人物是模型道具；`services.state=geometry_and_anchor_only`，`npc_runtime=not_verified`。

单测、validator、lint/check、Gradle、Minecraft、云端任务和浏览器运行全部 `not_run`。通行碰撞、寻路、玩家外观、手感、音效、FPS 与多人保持 `not_verified`。本次正常生成产物不构成上述任何一项通过证据。

西漠、南疆、北原、东荒、魔域只交付短 authored 风貌与布局纲要，其中魔域明确拥有主城；没有抢做五城完整模型。七州 D1 量级、中州原作巨城、每州一座核心主城且外域重复地貌不重复主城的总体约束由父契约承接。

## 父代理展示整理

父代理将显示模板改为 T3 主题变量和固定画布高度，并通过同一 owning generator 重新生成产物；未改城市布局和几何。已调用 HTML 展示工具提供交互预览，首次截图仍处于异步载入阶段，因此不记录视觉或交互通过。上述“浏览器运行 not_run”是作者交接时状态；本轮未运行任何自动化测试、校验器、编译或游戏。玩家外观仍为 `not_verified`。

## 父代理布局身份对齐

生成器现在只读共享 authored catalog，输出 `layout_family_id`、`appearance_id`、独立 `layout_id` 和 `layout_sha256`；保留原 `layout_identity` 摘要作为兼容别名。具体布局 ID/摘要与候选匹配，布局族与目录匹配；未执行消费端测试。
