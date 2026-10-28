# 五境十五套装备实装接口

本次用户已确认 `c376d8e4` 的十五套设计，并授权全部接入游戏与多个 GPT-6.1 Sol high 并行实施。工作目录为 `/tmp/mc-immortal-armor-atlas-20261028`，分支为 `feature/immortal-armor-runtime`，从当前main引入已确认设计；主工作区的阡镇未提交工作不得改动。

## 范围

用户明确本次只做建模进游戏，不做来源及玩法。只交付原造型、纹理、骨骼、动效、物品与创造栏接入。全部护甲统一铁甲基础数值与铁锭修复，不按境界提升；不新增配方、器谱、炼器工序、套装技能或网络协议变更。

## 身份与资源

- 保留十五个设计ID；每套注册 `<design>_helmet`、`_chestplate`、`_leggings`、`_boots` 和 `_ingot`，namespace为myvillage。旧玄铁甲、武器与其ID不变。
- 美术唯一来源仍为 `art/immortal-armor-atlas/authors/` 经minecraft.py生成的levels和effects/catalogue.js；不能用近似立方体、二维图标或预览人台替换真实三角面。
- 新导出器为 `tools/immortal_equipment/`，入口 `python3 -m tools.immortal_equipment build|check`。骨骼绑定输入为 `equipment/immortal/rig.json`；数值输入为 `equipment/immortal/balance.json`。
- 生成穿戴数据：`assets/myvillage/immortal_equipment/<design>.json`；PNG：`assets/myvillage/textures/immortal_equipment/<design>/<material>.png`；物品OBJ/MTL与JSON位于相应models子目录，均由导出器生成。
- 公共注册目录为 `data/myvillage/immortal_equipment/catalogue.json`，版本1。结构见下。中文名与槽位标签由导出器维护；现有语言文件只按本批75个ID合并，保留其他所有键。不得手改现有blocks图集，父代理负责接入其原生成器。

```json
{"version":1,"tiers":{"1":{"defense":{"head":2,"chest":6,"legs":5,"feet":2},"durability_multiplier":15,"enchantability":9,"toughness":0,"knockback_resistance":0}},"sets":[{"id":"qi_moqing","name":"墨青巡霄","level":1,"model":"myvillage:immortal_equipment/qi_moqing.json","texture":"myvillage:textures/immortal_equipment/qi_moqing/silk.png","ingot":{"id":"myvillage:qi_moqing_ingot","name":"以作者数据为准"},"armor":[{"id":"myvillage:qi_moqing_helmet","slot":"head","name":"以作者数据为准"}]}]}
```

示例仅展示结构，实际包含五份相同的铁甲基础数值、十五套、每套四件和一个锭。`texture`引用该套实际存在的一张材质PNG，供ArmorItem纹理入口兼容；穿戴渲染按每个材质引用选择纹理与透明类型。

## 穿戴网格版本1

```json
{"version":1,"set_id":"qi_moqing","materials":{"silk":{"texture":"myvillage:textures/immortal_equipment/qi_moqing/silk.png","opacity":1}},"profiles":{"standard":{"bone_origins":{"head":[0,0,0],"body":[0,0,0],"right_arm":[-5,2,0],"left_arm":[5,2,0],"right_leg":[-1.9,12,0],"left_leg":[1.9,12,0]},"parts":[{"id":"body","slot":"chest","bone":"body","material":"silk","vertices":[[0,0,0,0,0,0,1,0]],"indices":[0,1,2]}]},"slim":{"bone_origins":{},"parts":[]}},"effects":{"level":1,"title":"墨行微息","description":"...","flows":[],"motions":[],"glows":[],"arcs":[]},"effect_bindings":{"flows":["body"],"arcs":[]}}
```

- `vertices`每点为`x,y,z,u,v,nx,ny,nz`，坐标为MC骨骼局部模型像素，UV为Minecraft贴图方向，法线单位化；索引必须为有效三角形。示例不是可用几何。
- 作者坐标为脚底Y=0、正面+Z。变换到MC全局模型像素为`(x,24-y,-z)`，再减去上述骨骼原点。标准与细臂均保留真实几何；手臂适配保持内侧边缘，不能整体缩放整套。slim实际原点与3像素手臂以当前PlayerModel为准。
- 每个组件只挂一个明确骨骼；背披、光脊、肩根、浮空法相不能因空间接近手臂就误绑。`rig.json`显式列出组件绑定，材质、披衣附件和动态同组须一致。模型不含预览人台，不替换玩家皮肤。
- 纹样保持原16/32像素Nearest采样；tile UV若用于物品图集，须切分跨周期三角面并折回UV，不能串到别的图集区域。穿戴透明材质与不透明材质分批渲染，不能统一强制cutout。
- effects保留原作者像素坐标。动作枢轴/流光路径转换使用相同坐标约定；`effect_bindings.flows/arcs`与配置数组一一对应，指定具体骨骼。motions/glows从目标组件取绑定；每个motion必须在同一骨骼内。
- Java数据解析拒绝缺身份、非法骨骼、越界索引/UV、非有限数值、未知材质与动态目标。资源重载完整解析后原子替换缓存。

## Java公共接口与所有权

- 注册代理拥有 `com.example.myvillage.equipment.immortal.ImmortalEquipmentCatalogue`、`ImmortalArmorItem`、`ImmortalEquipmentContent` 及相关服务器数值类。
- `ImmortalEquipmentCatalogue.armor()`返回`List<ArmorDefinition>`；record至少提供`id():ResourceLocation`、`setId():String`、`level():int`、`type():ArmorItem.Type`、`model():ResourceLocation`、`texture():ResourceLocation`。`ingots()`返回注册锭定义。
- `ImmortalArmorItem.definition()`返回上述ArmorDefinition。`ImmortalEquipmentContent.register(IEventBus)`、`creative(CreativeModeTab.Output)`、`armorItems():ImmortalArmorItem[]`供集成调用。
- 客户端代理拥有 `client/immortal/` 与公共纯数据 `equipment/immortal/ImmortalMeshData.java`。公共解析入口`ImmortalMeshData.parse(Reader)`，不能导入client类型。客户端注册入口可采用单独的CLIENT @Mod；仅父代理改中央入口。
- 客户端代理同时负责穿戴渲染与效果迁移，选择真实NeoForge缓冲/骨骼接口；禁止玩家固定站姿、以静态item OBJ代替穿戴动画、全身贴图假装透明效果。支持宽/细臂、头/身体/手臂/腿运动、混穿、单件、资源重载；可穿戴实体边界须明确。
- 数值仅由balance输入决定，经原版ArmorItem/ArmorMaterial属性处理一次；不增设重复减伤事件。装饰光效不产生伤害或状态。

## 并行范围

1. 导出代理：tools/immortal_equipment、rig输入与该导出器产物。不得改balance或Java。
2. 注册代理：上述公共注册/物品/材质Java。不得改中央入口、客户端、生成器或资源产物。
3. 客户端代理：client/immortal与ImmortalMeshData。不得改其他公共注册类与生成资源。
4. 检查代理：专用Python/Java测试、原生采集脚本与验收范围。不得改实现断言来绕过失败，不运行Gradle/Minecraft/云端。

父代理负责中央注册/创造栏、共享图集与槽位标签、版本、Colab构建/原生模型检查及最终合并交付。所有代理共享同一工作树，不回滚他人编辑，不自行提交/推送/再派代理。先完成自己的接口与代码，发现接口问题及时报告具体字段与证据。
