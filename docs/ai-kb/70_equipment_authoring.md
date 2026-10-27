# 装备制作基础与玄铁甲

0.97.0 新增 `myvillage:xuantie_helmet`、`myvillage:xuantie_chestplate`、
`myvillage:xuantie_leggings`、`myvillage:xuantie_boots`。四件通过主创造栏或 `/give` 获取；
沿用原版铁甲属性与穿戴行为。0.98.0 接入器谱、锻器工序、坚韧炼灵、穿戴历练和耐用锻炼，见 [KB54](54_forging.md)；不含套装技能。

## 墨行外观（0.99.3）

用户选定 B“墨行”方向后，玄铁甲四件沿用原身份和数值，改为低额箍、交领衣身、左侧叠肩、
窄护臂与短分片衣摆。额箍放在原版 0.5 像素帽层之外，不附加头发或替换玩家皮肤。
衣身、宽／细臂衣袖、裤衬和靴子覆盖原版 0.25 像素外层皮肤，避免外套和袖子露到装备外。
衣摆分别挂在左右腿，跟随既有站立、走路与蹲下骨骼；没有布料物理。

`moxing_*` 专用材质降低噪点和装饰密度；旧材质色表不变。新增铁材质必须同时列入
`equipment/workshop/stages.json` 的 `armor.metal_materials`，保证热坯、精磨甲片和装配成品同源，
衣衬、皮革与玉扣只在装配时加入，成品加热也不把它们当铁片变红。
生成 `tools/gen_forging_assets.py` 同时更新炼器工件和鼎外炼灵取景。

固定源、两轮问题与最终 Colab 证据见 [墨行重制记录](../testing/2026-10-28-xuantie-moxing.md)。

## 输入与资源归属

- `equipment/*/equipment.json`：身份、槽位、基本材质、名称、骨骼挂接与几何。
- `equipment/materials.json`：共享材质色表和纹理类型。
- `equipment/poses.json`：离线试衣间的骨骼基点与代表性姿态。
- `tools/equipment`：目录读取、校验、UV 分配、图像／JSON 导出、图标渲染和试衣间。
- `equipment/display.json` 与 `equipment/workshop/`：共用物品取景、四种工具实体部件及同源器坯规则。
- `tools/equipment/mesh.py`：精确保留坐标／UV 的 OBJ 导出，覆盖防具物品与工坊部件；背包、地面和手持使用同一模型。
- `tools/equipment/geometry.py`：从青锋剑生成器提取的纯几何／显示／PNG 工具；旧函数保留兼容导出。

运行 `python3 -m tools.equipment build` 生成 `assets/myvillage/equipment`、
`textures/equipment`、物品图标与模型、槽位标签、中文名和
`data/myvillage/equipment/catalogue.json`。`check` 子命令检查漂移，不写资源。
命令、完整字段及限制见 [装备制作源](../../equipment/README.md)。

## 运行时

`EquipmentCatalogue` 从随 jar 打包的目录固定注册身份；`EquipmentContent` 遍历目录注册并加入创造栏。
`EquipmentArmorItem` 复用原版 `ArmorItem`/铁甲材质，只提供自定义贴图，不增加伤害事件。
`EquipmentModelData` 验证版本、身份、两种身形、槽位骨骼、有限几何和 UV。

客户端 `EquipmentModels` 在资源重载时完整解析、烘焙后一次替换缓存；不注册任意资源包中新物品。
每个物品／身形有自己的 `HumanoidModel`，避免不同部位共用可见性。
NeoForge 在每次渲染前复制实际角色姿态与装备槽可见性；每个立方体挂在相应原版骨骼下，
Java 不复制玄铁甲的形状常量。新同类套装不需要写新的渲染器。

第一人称武器解算及 Java/Python parity 未改动；三件既有三维武器的生成资源逐字节回归。

## 最小检查与证据

```bash
python3 -m tools.equipment check
python3 tools/validate_mod_items.py
.venv-preview/bin/python -m unittest tools.tests.test_equipment
```

新增资源包模型必须过有限字段与身份校验；非法资源不静默变成另一件装备。
Java 定向检查为 `com.example.myvillage.equipment.EquipmentDataTest`，重型构建使用授权的云环境。
`tools/equipment_evidence.py` 复用已有真实 X11 输入守卫，记录穿戴、动作、F3+T 重载和两种默认玩家身形。
RCON 只准备物品与场景、读取结果。Colab 会话保留与否遵从当次用户要求。

离线模型预览、云端构建、原生捕获和玩家审美是不同结论；未观察的外观、手感、音效、FPS、
其他模组动画和广泛多人组合仍为 `not_verified`。

本次固定源、原生检查与下载见 [0.97.0 交付记录](../testing/2026-10-27-equipment-authoring.md)。
