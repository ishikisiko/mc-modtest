# 三维设计接口 v1 · 冻结协作接口

各境设计者只写本境 JSON、详细设计文和可选 authored 脚本；渲染工程师只写 core/。
所有字段可由自己手工编码／程序化绘制，不用图像生成。15 套实际几何必须各不相同。

## 文件与数据

`levels/levelN.json` 是一个数组，**恰好三套**。每套结构：

```json
{
  "id":"qi_moqing", "name":"墨青巡霄", "level":1, "realm":"炼气", "style":"清峻巡天",
  "tagline":"一句清楚的审美定位", "palette":["#253b4b","#c6c9bc","#8e9eaa"],
  "description":"完整的设计描述",
  "silhouette":"比例、轮廓、负空间与前后造型的说明",
  "materialStory":"锭材质到衣甲表面的联系；别编造已经实现的玩法",
  "progression":"比前境高级在哪里，本境保留了哪些后续提升空间",
  "segmentation":"只在哪里用少量分片增强层次；若无则说明",
  "parts":{
    "head":{"name":"中文件名","description":"冠、盔或头饰的具体设计"},
    "chest":{"name":"中文件名","description":"躯干、袖、肩、披帛的具体设计"},
    "legs":{"name":"中文件名","description":"下装、衣摆、腿部护具的具体设计"},
    "boots":{"name":"中文件名","description":"鞋靴的具体设计"}
  },
  "ingot":{"name":"墨青灵钢锭","description":"低扁锭，材质与花纹细节","components":[]},
  "materials":{
    "steel":{"name":"墨青灵钢","color":"#425b68","metalness":0.75,"roughness":0.3},
    "silk":{"name":"月白灵绫","color":"#d4d8cc","metalness":0.0,"roughness":0.6}
  },
  "components":[]
}
```

`description/silhouette/materialStory/progression/segmentation/parts/ingot` 合计每套至少 500 个汉字左右的实质说明；不能只有标签。更长细节可写入本境设计文。
至少 5 种有分工的材质；颜色、metalness、roughness、opacity（默认1）、emissive（十六进制，默认黑）、emissiveIntensity（默认0）用于真实材质。
透明/发光须有层级克制，不给低境界满身光圈，不用黑白金配色一套套换色。

## 坐标与人台

单位近似米，Y 向上，**Z 正向为人物正面**。同一中性、修长的概念人台，不是 Minecraft 方块人。
足底 y=0；头中心 `(0,1.68,0)`，头约宽 .19、高 .24、深 .20；颈中心 y=1.52。
肩峰 `x=±.235,y=1.45`；躯干上缘 y=1.43，宽 .40，深 .24；腰 y=1.0，宽 .29，深 .21；髋 y=.94，宽 .34。
上臂略向外：肩 `(±.235,1.43,0)` → 肘 `(±.31,1.12,0)` → 腕 `(±.35,.85,.02)`。
手中心 `(±.35,.79,.03)`。腿中心 x=±.095；髋 y=.94 → 膝 y=.52 → 踝 y=.12；脚趾向 +Z。
人台只负责脸、手和极少未覆盖处；设计不能只给人台上色。必须真的包覆身体，尤其完整的头/胸/腿/靴。
高阶冠饰/背轮可超出头顶，但全套尽量在 y=0..2.4，宽度<=1.7 内。不同套装可明显改变外轮廓与衣摆长度。

## 组件结构与变换

每个组件：`id`（本套唯一），`name`（中文），`role`（head/chest/legs/boots/aura/ingot），`material`（本套材质键），`shape`，对应参数。
可选统一变换：`position:[x,y,z]`，`rotation:[x,y,z]`（角度，XYZ），`scale:[x,y,z]`。参数坐标先在局部构造，再应用变换。
所有数值有限。主要衣甲禁止只靠 box/ellipsoid 堆起来；它们只做少量连接、扣座等细部。曲面、实心轮廓、真实边缘与衣料是主体。

### 冻结的 shape 参数

- `loft`：`rings:[{"y":0,"rx":.2,"rz":.12,"cx":0,"cz":0}, ...]`，至少两圈；每圈可选 `twist`（度）、`wave`（米）。截面是椭圆。`segments` 默认32；`startAngle` 默认0、`endAngle` 默认360（度，圆周 x=rx*cos(a), z=rz*sin(a)）。`thickness` 默认.004；`capTop`/`capBottom` 默认false。可做贴身躯干、展开衣摆、冠盔壳和袖筒；勿把所有套装都做同一裙筒。
- `tube`：`path:[[x,y,z],...]`，`radius` 或 `radii`（每个路径控制点一个），`radialSegments` 默认8，`steps` 默认32；圆截面沿 Catmull-Rom 路径。做细嵌线、冠饰、扣环、主骨架和刻纹。
- `ribbon`：`path:[[x,y,z],...]`，`width` 或 `widths`（逐控制点），`normal:[0,0,1]` 默认朝+Z，`thickness` 默认.003，`steps` 默认32。做有宽度、曲度和厚度的披帛/衣缘/羽翼局部；不可用大平片遮住简陋主体。
- `panel`：`polygon:[[x,y],...]` 至少3点、非自交；`depth`（米，默认.012），`bevel`（默认.003）。局部正面沿+Z，中心在z=0。可选 `trimMaterial` 与 `trimRadius`（默认.002），沿轮廓走一条真实细嵌边。适合整体胸腹护面、冠额、护腕、羽叶等。
- `torus`：`radius`，`tube`，`arc` 默认360（度）；局部在XY平面、法线+Z；适合冠环、背轮、飘浮饰环。不是越多越高级。
- `ellipsoid`：`radii:[rx,ry,rz]`，默认细分24×16；只做少量宝石/小连接/主体之外的细节，避免泡泡人。
- `gem`：`radii:[rx,ry,rz]`，八面宝石，可通过rotation改变方向；不要把所有进阶都变成更大的胸口宝石。
- `box`：`size:[x,y,z]`；仅小扣件/连接，禁止作为主要胸甲、整盔、整腿。
- `ingot`：`size:[宽,高,深]`，典型 `[.36,.07,.20]`，高/宽<=.25。低扁六角/削角锭；顶面略收、下缘细倒角。材质分工可用 `topMaterial`、`edgeMaterial`，另用 tube/panel/gem 组件刻出自己的纹理/内含物。不得改成方砖或圆饼。

仅使用这些 shape。需要新形状时用现有 panel/loft/ribbon/tube 组合，不能自己发明渲染器不认识的字段。
至少有头、胸、腿、靴四个 role；aura 可选且不能代替主造型。锭组件独立放入 ingot.components，使用同一材质表，坐标以锭中心原点建模，放置台由 viewer 控制。
建议每套 35–120 组件，高境界可更细，但要有主次，不能填满噪声。

## 渲染内核接口（core 工作者拥有）

使用仓库自带 `tools/web/three.min.js`；不依赖外部 CDN/服务。引擎为 `core/engine.js`，IIFE 暴露 `globalThis.ArmorEngine`，Node 可 `module.exports`，从 `globalThis.THREE` 取 THREE。

必须提供：

- `validateDesign(spec)`：抛出具体无效字段错误，不把不支持的 shape 静默换成 box。
- `buildDesign(spec,{avatar:true})`：返回 `THREE.Group`，每个几何 Mesh 的 userData 保留 componentId、role、materialKey。
- `buildIngot(spec)`：返回同一套锭的 Group。
- `buildAvatar()`：真实三维中性概念人台，修长人体比例；不要方块人、像素脸或大圆球脑袋。
- `meshStats(group)`：顶点、三角形、组件等信息。
- `exportOBJ(group)`：返回真实几何 OBJ 文本，应用世界矩阵；不只是截图。
- `dispose(group)`：释放 geometry/material/texture。

`core/viewer.html` 为自包含页面模板，占位符 `__THREE_JS__`、`__ENGINE_JS__`、`__CATALOGUE_JSON__`。把依赖脚本直接内联，目录 JSON 是15套设计数组。可以增加 helper JS，但须全由模板内联。
页面需要十五套矩阵总览（真实模型缩略图）、五境筛选、每境三款、主三维视窗、锭/穿戴/单件切换、前侧背与拖动缩放、材质与四件详细说明、清晰的等级递进。
艺术品的颜色可固定，页面 UI 用主题变量。不要外层背景卡或横幅。光照用相同摄影棚布光；金属/玉/织物/晶体应明显可区分。不能把高阶高级感完全交给 bloom/粒子。
中间与最终预览走 html_preview → html_render；不启动服务器、不使用 preview_* 浏览器工具。
