# 五境衣甲运行时导出

在仓库根目录运行：

```bash
python3 -m tools.immortal_equipment build
python3 -m tools.immortal_equipment check
python3 -m unittest tools.immortal_equipment.test_exporter
```

仅使用当前 `art/immortal-armor-atlas/levels/level1.json` 至 `level5.json`、
`core/engine.js` 和 `effects/catalogue.js`；Node 使用仓库自带 Three r128，
通过 `buildDesign(spec, {avatar:false})` 与 `buildIngot(spec)` 取得真实三角面、
世界矩阵、法线、材质分组和 UV，不重写几何算法，不包含预览人台。
新的造型应在原作者源修改并更新 levels，本导出器没有第二份造型常量。

`equipment/immortal/rig.json` 显式绑定全部组件 ID。披衣、肩根、背部法相、
整体腰带和连续前后衣摆绑定 body；真实袖子、袖缘、护腕绑定对应手臂，
裤腿、成对侧摆和靴子绑定对应腿骨；头饰绑定 head。
作者左右文字以正面观看命名，不等于 MC 骨骼名。正 X 侧是 MC left_arm/left_leg，
例如紫霄 `zx_gauntlet`、`zx_gauntlet_ridge` 与全部护臂流光、折雷均为 left_arm。
听雨袖雨、凌云绕袖与太阴霜露分别跟随对应左右袖臂；肩背、披衣和法相上的效果留在 body。

作者几何单位为米。穿戴顶点先乘 16，再转为 MC 全局模型像素 `(x,24-y,-z)`，
最后减对应骨骼原点。X 从不镜像；这个变换行列式为正，不反转三角面绕序。
法线经过原作者世界矩阵的逆转置后再做同样的坐标轴转换与单位化。
标准骨骼原点读取 `equipment/poses.json`。slim 的左右臂根为 `(±5,2.5,0)`，
手臂模型像素全局 X 以靠身体的 `±4` 为锚，宽度乘 `.75`，全局 Y 加 `.5`；
非手臂组件完全不变。非均匀缩放的手臂法线重新做逆转置和单位化。

**客户端效果适配接口：** `effects` 保持作者原始模型像素坐标。
flows/arcs 的骨骼逐条读取 `effect_bindings`，motions/glows 从目标组件取骨骼。
slim 手臂效果点（仅手臂，不是 body 特效）也必须在减骨骼原点前执行：
`mcX = sign*4 + (authorX-sign*4)*.75`，
`mcY = 24-authorY+.5`，`mcZ = -authorZ`，然后减 slim 原点。
这里 sign 在 left_arm 为 +1，right_arm 为 -1；效果原始数据不可预先改写为 MC 坐标。
所有当前 motions 都在 body 骨骼，头冠 glow 则通过目标组件留在 head。

tile UV 是原引擎面投影，fit UV 是原引擎包围框归一化。
tile 三角面按 U/V 的每个整数周期裁切，插值位置、法线，再折回该周期；
不独立对跨缝顶点取余。穿戴和 OBJ 共用这些面、材质与 UV。
PNG 行序严格对应作者 pixelArt.rows，保留 16/32 像素与原色表；
Three 底朝上的 V 转为 MC 顶朝上的 V，OBJ `flip_v=false`。
PNG 自身 alpha 为 255；穿戴材质 opacity 和物品 MTL `Kd r g b a` 各自应用作者透明度一次，
避免透明度相乘变暗。MTL 同时保留标准 `d`，但不能仅依赖该字段：
本仓库 NeoForge 21.1.233 的 ObjModel 烘焙只使用 diffuseColor.a，忽略 dissolve。
物品包含透明材质时使用 translucent render_type；穿戴由客户端分材质批次选择透明渲染。
`side` 字段保留原引擎 front/double 材质信息，供客户端选择是否剔除背面。

导出十五份穿戴 JSON、七十五套 OBJ/MTL/物品 JSON、全部像素材质与采样元数据、
七十五个中文物品语言键、四槽标签及不可套用原版 trim 的移除列表。
共享语言和标签仅合并本批 ID，保留旧内容。目录 tiers 直接复制父代理拥有的
`equipment/immortal/balance.json`，不另设数值、配方或玩法。

**blocks 图集交接：**
`assets/myvillage/immortal_equipment/atlas_sources.json` 是完整
`{"sources":[{"type":"minecraft:single","resource":"...","sprite":"..."}]}` 清单。
父代理应让 blocks 图集的原生成器合并 sources；本工具绝不写共享 blocks.json。
旧 `tools.equipment` 的槽位标签与 trimmable_armor 输出同样需要父代理接入本批目录，
避免旧生成器覆盖新 ID；单独重跑新导出器 build 可以恢复合并，但不替代拥有者修复。

`provenance.json` 记录实际 levels、引擎、动态、Three、rig、balance、poses、display
和导出代码的 SHA-256；`check` 不写文件，只检查当前所有输出是否逐字节一致。
源级检查、轻量几何核验不等于游戏外观、动画、FPS 或多人验收；这些项目保持
`not_verified`，由父代理统一云端构建与原生采集。
