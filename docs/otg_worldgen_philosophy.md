# OTG 世界生成理念拆解与 NeoForge 1.21 项目迁移思路

## 0. 使用前提

这份文档不是 OTG API 教程，也不是让 NeoForge 1.21 项目直接依赖 OTG。

OTG 的主要价值在于它提供了一套成熟的世界生成组织方式：

```text
世界预设 Preset
  → 世界级规则 WorldConfig
  → 群系级规则 BiomeConfig
  → 资源生成队列 Resource Queue
  → 自定义对象 CustomObject
  → 大型结构 CustomStructure
  → 分布、碰撞、地形融合、权重、风格约束
```

对 NeoForge 1.21 修仙 Mod 项目来说，真正要迁移的是它的设计理念：

```text
用数据配置把世界生成拆成可组合的层级系统。
```

而不是直接迁移 OTG 的旧版本格式或实现。

---

## 1. OTG 的核心定位

OTG，全称 Open Terrain Generator，可以理解为一个数据驱动的世界生成框架。

它不是简单的“多生成一些结构”，也不是单纯替换地形噪声。它的核心思想是：

```text
不要把世界生成逻辑全部写死在 Java 代码里。
而是把世界生成拆成一批可配置、可组合、可复用的数据规则。
```

普通 Minecraft 世界生成大致是：

```text
噪声地形
  → 原版群系
  → 原版 feature
  → 原版 structure
```

OTG 风格的世界生成更像：

```text
世界预设 Preset
  → 世界级规则 WorldConfig
  → 群系级规则 BiomeConfig
  → 群系资源队列
  → 小型对象
  → 大型结构
  → 地形融合
  → 分布约束
  → 生态、刷怪、战利品、叙事地点
```

所以 OTG 的重点不是某一个算法，而是一套世界生成内容管理体系。

---

## 2. Preset：把“世界风格”打包成完整规则集

OTG 的基本单位是 preset。

Preset 可以理解为一个“世界生成包”。它不是几栋建筑，也不是几种群系，而是包含完整的世界规则。

一个成熟 preset 通常包含：

```text
1. 世界整体生成模式
2. 大陆、海洋、河流、洞穴等宏观规则
3. 群系列表与群系分组
4. 每个群系的地形参数
5. 每个群系的表层/地下方块
6. 树、石头、湖泊、矿物、小装饰
7. 大型结构、小型结构、遗迹、地牢
8. 结构生成权重、间距、限制条件
9. 与其他 Mod 的方块、资源、怪物整合
10. 叙事性地点、固定区域、随机区域
```

这和普通 datapack 的区别在于：

```text
datapack 通常偏“注册一些结构或 feature”。
OTG preset 偏“定义一个完整世界的生成逻辑”。
```

对于修仙 Mod，preset 思维很重要。

你的目标不是单纯生成一个村庄，而是生成一个带有修仙小说世界观的地图：

```text
洲
域
郡
城镇
宗门
坊市
秘境
洞府
遗迹
灵脉
危险区
高阶修士活动区
低阶修士活动区
```

这些内容不应该都散落在代码里，而应该逐步沉淀成类似 preset 的规则体系。

---

## 3. WorldConfig 理念：先决定大地图格局

OTG 的 WorldConfig 控制的是世界/维度级别的规则。

它解决的问题是：

```text
这个世界整体长什么样？
大陆有多大？
海洋有多大？
群系如何分组？
群系之间如何过渡？
河流、洞穴、峡谷怎么生成？
原版结构是否保留？
海平面是多少？
维度规则是什么？
```

这是一种非常重要的分层思想：

```text
不要在生成单个建筑时才决定世界观。
先在世界级规则中决定“这个世界的宏观秩序”。
```

对于修仙 Mod，可以迁移成：

```text
WorldConfig-like 层：
  - 世界是否分为多个大洲
  - 每个大洲的灵气等级范围
  - 每个大洲的危险度范围
  - 每个大洲允许出现哪些群系
  - 高阶宗门主要分布在哪里
  - 凡人城镇主要分布在哪里
  - 秘境、禁地、遗迹的稀有度
  - 道路网络是否连接城镇
  - 是否存在中心洲、边荒、妖域、魔域
```

示例：

```json
{
  "world_profile": "cultivation_overworld",
  "regions": [
    {
      "id": "central_continent",
      "display_name": "中州",
      "qi_range": [6, 9],
      "danger_range": [4, 8],
      "settlement_density": "high",
      "sect_density": "high",
      "forbidden_zone_density": "medium"
    },
    {
      "id": "eastern_wilds",
      "display_name": "东荒",
      "qi_range": [2, 5],
      "danger_range": [2, 6],
      "settlement_density": "medium",
      "sect_density": "low",
      "forbidden_zone_density": "high"
    }
  ]
}
```

这个层级早期不必直接接管 Minecraft chunk generator。可以先作为结构生成器、城镇规划器、宗门生成器的输入数据。

---

## 4. BiomeConfig 理念：每个群系都是内容容器

OTG 的 BiomeConfig 不只是定义温度、降雨、颜色。它更像是一个群系内容包。

它控制：

```text
1. 地形高度
2. 地形起伏
3. 表层方块
4. 地下方块
5. 植被
6. 矿物
7. 水体
8. 小装饰
9. 小型结构
10. 大型结构
11. 生物生成
12. 资源生成队列
```

它的关键思想是：

```text
结构不是孤立刷新的。
结构应该挂在某些群系、区域、环境条件上。
```

例如：

```text
竹林群系：
  - 允许小型修士草庐
  - 允许灵竹
  - 允许低阶灵草
  - 允许小型洞府入口
  - 不允许大型凡人城市
  - 不允许重型石质城墙

雪山群系：
  - 允许冰系宗门
  - 允许寒潭
  - 允许雪山洞府
  - 允许稀有灵矿
  - 村庄密度低
  - 道路生成困难

荒漠群系：
  - 允许废弃城池
  - 允许地下遗迹
  - 允许商队驿站
  - 允许流沙陷阱
  - 植被稀少
```

对于你的项目，后面不要让建筑模板直接全世界随机刷，而是应该让每个群系声明自己允许什么内容。

推荐抽象：

```json
{
  "biome_profile": "spirit_bamboo_forest",
  "region_tags": ["eastern_wilds", "southern_forest"],
  "qi_bias": 3,
  "surface_palette": "bamboo_moss_stone",
  "allowed_settlements": ["hamlet", "small_sect", "herb_garden"],
  "allowed_structures": [
    "bamboo_hut",
    "small_cave_dwelling",
    "herb_pavilion",
    "spirit_spring"
  ],
  "forbidden_structures": [
    "large_city",
    "desert_ruin",
    "snow_sect"
  ],
  "decoration_motifs": [
    "bamboo_cluster",
    "mossy_rock",
    "paper_lantern",
    "small_bridge"
  ]
}
```

---

## 5. Resource Queue 理念：群系内部的生成清单

OTG 的一个关键设计是 resource queue。

可以把它理解成：

```text
这个群系在生成时，要依次尝试生成哪些资源？
```

资源可以是：

```text
树
石头
矿物
草
湖
小型结构
大型结构入口
自定义对象
```

这个设计的价值是：

```text
把“这个群系会出现什么”集中写在群系配置里。
```

对你的项目，可以设计成：

```json
{
  "resource_queue": [
    {
      "type": "decoration",
      "id": "mossy_rock_cluster",
      "rarity": 0.25
    },
    {
      "type": "vegetation",
      "id": "spirit_bamboo_patch",
      "rarity": 0.18
    },
    {
      "type": "structure",
      "id": "small_cultivator_hut",
      "rarity": 0.03,
      "min_spacing": 96
    },
    {
      "type": "encounter",
      "id": "wandering_cultivator_camp",
      "rarity": 0.01,
      "min_spacing": 256
    }
  ]
}
```

注意这里的重点不是具体参数名称，而是理念：

```text
群系不只是地表颜色。
群系应该决定结构、装饰、资源、遭遇、危险度。
```

---

## 6. CustomObject 理念：小物件也应该模块化

OTG 中 BO3/BO4 可以用来生成树、石头、洞穴、建筑等对象。

这背后的理念是：

```text
不要把所有内容都当成“大结构”。
世界真实感来自大量小对象的密度和一致性。
```

很多地图不好看，并不是缺一座大城，而是缺这些中间层内容：

```text
路边石头
树根
倒木
小桥
井
废弃推车
破旗帜
小祭坛
灵草丛
山门残柱
洞府石阶
河边码头
灯笼柱
摊位
墙角杂物
```

你的修仙世界要想接近 Dregora 这种丰富度，不能只做：

```text
宗门
城镇
地牢
```

还要有大量：

```text
micro_object
small_object
medium_object
landmark
```

推荐分级：

```text
Micro Object：
  - 1~5 格范围
  - 石头、草丛、灯笼、箱子、木桩、灵草

Small Object：
  - 5~15 格范围
  - 小亭、井、路牌、废弃营地、摊位

Medium Object：
  - 15~40 格范围
  - 小洞府、小庙、驿站、药园、桥

Large Structure：
  - 40 格以上
  - 宗门、城镇、遗迹、秘境入口、大型地牢
```

---

## 7. CustomStructure 理念：大型结构不是一次性粘贴

OTG 的 BO4 大结构理念很值得借鉴。

大型结构通常不是一个完整巨型文件，而是：

```text
master 配置
  + 多个 16x16 子块
  + branch 连接规则
  + 分布规则
  + 碰撞检测
  + 地形融合规则
```

这对应 Minecraft 世界生成中的现实问题：

```text
大型结构如果一次性生成，容易：
  - 卡顿
  - 和地形不贴合
  - 和其他结构重叠
  - 跨 chunk 生成异常
  - 难以随机变化
```

OTG 的思路是：

```text
大型结构应该拆块。
结构块之间通过规则连接。
整体由 master 控制。
```

对你的城镇/宗门系统，应该避免一开始就做：

```text
one_big_city.nbt
```

更推荐：

```text
sect_master.json
  - gate.nbt
  - main_hall.nbt
  - scripture_pavilion.nbt
  - alchemy_room.nbt
  - disciple_house_a.nbt
  - disciple_house_b.nbt
  - training_ground.nbt
  - wall_segment.nbt
  - corner_tower.nbt
  - road_segment.nbt
```

然后通过布局规则拼接：

```json
{
  "structure_group": "small_sect",
  "layout_type": "courtyard",
  "required_parts": [
    "sect_gate",
    "main_hall",
    "training_ground"
  ],
  "optional_parts": [
    "scripture_pavilion",
    "alchemy_room",
    "disciple_house",
    "storage_house"
  ],
  "connectors": [
    "stone_path",
    "wooden_bridge",
    "wall_segment"
  ],
  "collision_radius": 96,
  "terrain_policy": "flatten_core_blend_edges"
}
```

这比直接生成一个大 NBT 更适合长期扩展。

---

## 8. Branching 理念：结构之间用连接规则组合

OTG 的 BO3/BO4 分支系统体现了一个重要理念：

```text
复杂建筑群 = 起点结构 + 分支结构 + 连接规则
```

例如一座城镇可以这样生成：

```text
town_center
  → road_north
    → house
    → shop
    → well
  → road_east
    → blacksmith
    → warehouse
  → road_south
    → gate
    → farm
  → road_west
    → market
    → inn
```

这比完全随机撒建筑更自然。

你的项目后面可以把城镇拆成：

```text
Anchor：
  - 城镇中心
  - 宗门大殿
  - 山门
  - 坊市中心

Connector：
  - 道路
  - 台阶
  - 桥
  - 墙
  - 山路

Module：
  - 民居
  - 商铺
  - 炼丹房
  - 藏经阁
  - 客栈
  - 仓库

Decoration：
  - 灯笼
  - 牌匾
  - 摊位
  - 树
  - 石碑
```

生成时不要问“随机放哪个建筑”，而是问：

```text
这个 anchor 还能向哪些方向扩展？
这个 connector 两侧允许挂哪些 module？
这个 module 是否满足地形、间距、风格、等级条件？
```

---

## 9. Collision 理念：结构生成必须有空间规划

OTG 的 BO4 有碰撞检测思想，这对大型结构非常重要。

没有碰撞检测会导致：

```text
房子插进房子
道路穿过墙
宗门大殿压到山洞
地牢入口刷进河里
村庄刷到悬崖边
两个大型结构重叠
```

你的生成系统应该尽早引入 footprint 概念。

每个结构模板都应该声明：

```json
{
  "id": "small_house_a",
  "size": [11, 8, 10],
  "footprint": {
    "min": [0, 0],
    "max": [10, 9]
  },
  "entrance": {
    "pos": [5, 0],
    "facing": "south"
  },
  "clearance": 2,
  "terrain_requirement": {
    "max_slope": 2,
    "allow_water": false
  }
}
```

生成前先做规划：

```text
1. 计算候选位置
2. 检查地形坡度
3. 检查水体/悬崖
4. 检查和已有结构的 footprint 是否重叠
5. 检查入口是否能接路
6. 检查风格、群系、区域是否匹配
7. 通过后才放置 NBT
```

这一步比“多做几个建筑模板”更重要。

---

## 10. Terrain Blending 理念：结构必须融入地形

OTG 的 BO4 支持地形 smoothing/blending，这体现了另一个核心理念：

```text
结构不是简单盖在地表上。
结构应该和地形互相适配。
```

常见错误是：

```text
建筑悬空
建筑半埋
门口对着土墙
道路突然断裂
城墙下方露空
宗门刷在极端坡地上
```

你可以先不用做复杂地形融合，但要从一开始设计 terrain policy。

建议结构声明：

```json
{
  "terrain_policy": {
    "mode": "flatten_core_blend_edges",
    "core_padding": 2,
    "edge_blend_radius": 4,
    "max_fill_depth": 5,
    "max_cut_height": 4,
    "foundation_block": "minecraft:stone_bricks"
  }
}
```

几种常用策略：

```text
none：
  不处理地形，只用于小装饰。

surface_snap：
  找地表高度后直接放置，适合树、石头、小物件。

flatten_core：
  核心区域铲平，适合小屋。

flatten_core_blend_edges：
  中心铲平，边缘缓坡过渡，适合村庄和宗门。

terrace：
  台地式处理，适合山地宗门。

embed：
  半嵌入地形，适合洞府、遗迹入口、地牢入口。
```

修仙项目尤其需要 `terrace` 和 `embed`，因为宗门、洞府、山门往往依赖山地视觉。

---

## 11. FromImage 理念：用图像控制大尺度世界格局

Dregora 使用过 FromImage 思路：通过图片控制部分地图区域的群系/区域分布，再在边界之外继续随机生成。

这个理念非常适合修仙世界。

完全随机的问题是：

```text
世界没有叙事中心
区域等级不稳定
高阶区和低阶区混在一起
玩家无法形成地理记忆
```

FromImage 或类似区域图的价值是：

```text
先设计大地图骨架，再让局部细节随机。
```

例如你可以先做一张低分辨率区域图：

```text
黑色：随机荒野
绿色：低阶森林区
黄色：凡人平原区
红色：危险禁地
蓝色：水域
紫色：高阶灵脉区
白色：中州核心区
```

然后系统读取这张图，把坐标映射成 region id：

```text
玩家坐标 x,z
  → 映射到区域图像素
  → 得到颜色
  → 转成 region_profile
  → 决定群系、结构、灵气、危险度
```

可以做成简化版：

```json
{
  "region_map": "data/my_mod/worldgen/region_maps/main_world.png",
  "color_mapping": {
    "#00aa00": "low_qi_forest",
    "#cccc66": "mortal_plains",
    "#aa0000": "forbidden_land",
    "#5555ff": "water_domain",
    "#aa00aa": "high_qi_mountains",
    "#ffffff": "central_continent"
  }
}
```

这不一定要马上接管原版地形。前期可以只用它控制：

```text
结构生成概率
宗门等级
城镇等级
怪物强度
灵草等级
遗迹类型
```

---

## 12. Random Mode 理念：固定骨架之外仍要无限扩展

如果世界完全由图片控制，会有边界问题。

成熟世界生成通常应该同时保留：

```text
核心区域有设计感。
边界之外有探索性。
```

修仙世界可以这样规划：

```text
核心 20k x 20k：
  - 手工区域图控制
  - 有明确洲域、宗门、主城、禁地

外部区域：
  - 按 region profile 随机生成
  - 可以继续出现随机宗门、秘境、遗迹
  - 不保证主线叙事，但保证风格一致
```

这比纯随机更有世界观，比纯手工更可扩展。

---

## 13. OTG 对“审美一致性”的启发

OTG preset 的强项不是单个建筑特别精致，而是整体世界风格统一。

这种统一来自：

```text
1. 群系限制结构
2. 区域限制群系
3. 材料 palette 限制建筑
4. 小装饰和大结构使用同一风格
5. 地形、植被、建筑、资源共同表达主题
```

对你的项目，应该把“风格”抽出来，而不是每栋建筑自己随便选方块。

推荐维护 Style Profile：

```json
{
  "style_id": "cultivation_medieval_village",
  "material_slots": {
    "base_stone": "minecraft:stone_bricks",
    "wall_main": "minecraft:oak_planks",
    "frame_wood": "minecraft:dark_oak_log",
    "roof_dark": "minecraft:spruce_stairs",
    "detail_wood": "minecraft:spruce_trapdoor",
    "lighting": "minecraft:lantern"
  },
  "allowed_roof_types": [
    "gable",
    "hip",
    "overhanging_gable"
  ],
  "forbidden_blocks": [
    "minecraft:quartz_block",
    "minecraft:purpur_block",
    "minecraft:diamond_block"
  ],
  "motifs": [
    "lantern",
    "wooden_frame",
    "stone_foundation",
    "paper_window",
    "signboard"
  ]
}
```

这样生成建筑时，不是写死：

```text
oak_planks + spruce_stairs
```

而是写：

```text
WALL_MAIN + ROOF_DARK + FRAME_WOOD
```

以后一个建筑模板可以套不同区域风格。

---

## 14. OTG 对“内容规模”的启发

Dregora 这类世界生成项目给出的最大启发是：

```text
世界丰富度不是靠 10 个超大结构堆出来的。
而是靠大量小、中、大内容分层叠加出来的。
```

应该避免：

```text
先做一个巨型宗门
先做一个巨型城市
先做完整自定义地形
```

更合理的顺序是：

```text
1. 建筑部件 DSL
2. 小建筑模板库
3. 小装饰对象库
4. 建筑风格 Profile
5. 群系/区域 Profile
6. 建筑池与权重
7. 小型聚落拼接
8. 道路与入口规则
9. 中型城镇
10. 宗门
11. 大地图区域规则
12. 最后才考虑自定义地形生成器
```

---

## 15. 对 NeoForge 1.21 项目的迁移原则

OTG 本身不应该作为 NeoForge 1.21 项目的直接依赖目标。它更适合作为设计参考。

可以这样对应：

```text
OTG Preset
  → my_mod worldgen profile / project config

WorldConfig
  → region/world profile JSON

BiomeConfig
  → biome/region content profile JSON

Resource Queue
  → feature/structure spawn rules

BO3/BO4 CustomObject
  → NBT structure + metadata JSON

BO4 Master Structure
  → structure_group / settlement_layout JSON

Branch()
  → connector / road / module attachment rules

Collision Detection
  → footprint + occupied area planner

Terrain Blending
  → terrain_policy + placement adapter

FromImage
  → region map / qi map / danger map
```

---

## 16. 建议的项目目录结构

可以逐步演进成：

```text
src/main/resources/
  data/my_mod/
    worldgen/
      region_profile/
        central_continent.json
        eastern_wilds.json

      biome_profile/
        spirit_bamboo_forest.json
        mortal_plains.json
        forbidden_wasteland.json

      style_profile/
        medieval_village.json
        mountain_sect.json
        desert_ruin.json

      structure_meta/
        small_house_a.json
        blacksmith_a.json
        sect_gate_a.json

      settlement_layout/
        small_village.json
        market_town.json
        small_sect.json

      structure_pool/
        village_houses.json
        village_shops.json
        sect_buildings.json

      region_map/
        main_world_regions.png
        qi_density.png
        danger_level.png

  data/my_mod/structures/
    village/small_house_a.nbt
    village/blacksmith_a.nbt
    sect/sect_gate_a.nbt
    sect/main_hall_a.nbt
```

当前阶段可以先不完全接入 Minecraft worldgen，只要这些数据能驱动离线生成和测试即可。

---

## 17. 推荐生成流水线

短期流水线：

```text
JSON DSL
  → 生成单体建筑 NBT
  → 生成 structure_meta
  → 加入 structure_pool
  → 用 gallery mcfunction 横向摆放验证
```

中期流水线：

```text
style_profile
  → archetype
  → building generator
  → NBT
  → structure_meta
  → settlement_layout
  → 城镇平面规划
  → 批量导出测试
```

长期流水线：

```text
region_profile
  → biome_profile
  → settlement_layout
  → structure_pool
  → terrain_policy
  → NeoForge worldgen placement
  → 游戏内自然生成
```

最终目标不是“生成一栋建筑”，而是：

```text
给定一个坐标
  → 判断它属于哪个洲/域
  → 判断灵气等级和危险度
  → 判断当地群系和风格
  → 判断是否适合生成聚落/宗门/遗迹
  → 选择结构池
  → 规划道路和建筑 footprint
  → 检查碰撞和地形
  → 放置 NBT
  → 添加装饰、战利品、刷怪、事件
```

---

## 18. 当前项目最应该吸收的 OTG 思想

当前项目已经验证结构生成链路，下一步不应该直接进入大型 worldgen，而应该吸收 OTG 的五个核心思想：

```text
1. 数据驱动
   世界规则尽量写成 JSON/Profile，而不是散落在代码里。

2. 分层生成
   世界 → 区域 → 群系 → 聚落 → 建筑 → 装饰。

3. 内容池
   建筑和装饰都进入 pool，通过权重、条件、风格选择。

4. 结构元数据
   每个 NBT 不只是方块文件，还要有 size、entrance、footprint、style、tags、terrain_policy。

5. 大结构拆块
   城镇和宗门不要做成一个巨型 NBT，而是由 anchor、connector、module、decoration 拼接。
```

---

## 19. 下一阶段建议

不建议现在直接做 NeoForge 自定义地形生成。

建议下一阶段目标是：

```text
OTG-like Profile System v0
```

交付物：

```text
1. style_profile/medieval_village.json
2. biome_profile/plains_village.json
3. structure_meta/test_house_03.json
4. structure_pool/village_houses.json
5. settlement_layout/gallery_village_line.json
6. 一个脚本：根据 pool 横向生成所有建筑 gallery
7. 一个脚本：根据 settlement_layout 生成简单村庄平面
```

这样项目会从：

```text
我能生成一个 NBT
```

升级到：

```text
我有一套可扩展的世界内容规则系统
```

这才是 OTG 真正值得学习的地方。

---

## 20. 给 Codex 的下一步提示词

可以直接把下面这段发给 Codex：

```text
当前项目目标是 NeoForge 1.21 修仙世界生成，不直接依赖 OTG，但要吸收 OTG 的数据驱动世界生成理念。

当前已有 JSON DSL → NBT → Minecraft /place template 验证链路。下一步请不要直接实现完整 worldgen，也不要直接做自定义 chunk generator。

请实现 OTG-like Profile System v0，目标是让结构生成从“单体 NBT”升级为“风格、群系、结构池、布局元数据驱动”。

请完成：

1. 新增数据目录：
   - data/myvillage/worldgen/style_profile/
   - data/myvillage/worldgen/biome_profile/
   - data/myvillage/worldgen/structure_meta/
   - data/myvillage/worldgen/structure_pool/
   - data/myvillage/worldgen/settlement_layout/

2. 新增 style_profile/medieval_village.json：
   - 定义 material_slots：BASE_STONE、WALL_MAIN、FRAME_WOOD、ROOF_DARK、DETAIL_WOOD、LIGHTING
   - 定义 allowed_roof_types
   - 定义 forbidden_blocks
   - 定义 motifs
   - 定义比例范围，例如墙高、屋顶高度、屋檐宽度、地基高度

3. 新增 biome_profile/plains_village.json：
   - 定义允许出现的 settlement_types
   - 定义 allowed_structures
   - 定义 forbidden_structures
   - 定义 decoration_motifs
   - 定义 qi_bias、danger_bias、settlement_density

4. 新增 structure_meta/test_house_03.json：
   - 指向已有或即将生成的 test_house_03 NBT
   - 包含 size、footprint、entrance、clearance、style_tags、terrain_requirement、terrain_policy

5. 新增 structure_pool/village_houses.json：
   - 包含 test_house_03
   - 支持 weight
   - 支持 required_tags / forbidden_tags

6. 新增 settlement_layout/gallery_village_line.json：
   - 用于横向摆放 structure_pool 中的建筑
   - 支持 spacing、axis、origin

7. 新增或更新脚本：
   - validate_profiles.py：验证上述 JSON 是否字段完整、引用存在
   - generate_profile_gallery_function.py：根据 settlement_layout 和 structure_pool 生成 mcfunction，用于游戏内横向展示所有建筑

8. 不要破坏现有 test_house_01/test_house_02/test_house_03 的 JSON DSL → NBT 流程。

9. README 增加：
   - OTG-like Profile System v0 的目的
   - 每类 profile 的作用
   - 如何运行 validate_profiles.py
   - 如何生成 gallery mcfunction
   - 如何在 Minecraft 中测试

10. 最后总结：
   - 新增文件
   - 修改文件
   - 当前支持的 profile 字段
   - 暂不支持的能力
   - 下一步如何接入真实 NeoForge worldgen
```

---

## 21. 一句话总结

OTG 的本质不是“更强的地形算法”，而是：

```text
用数据配置把世界生成拆成可组合的层级系统：
世界规则、群系规则、资源队列、结构对象、结构组、地形融合、分布约束。
```

对修仙 Minecraft Mod 来说，最重要的迁移方向是：

```text
不要直接追求巨型城市或完整自定义地形。
先建立 OTG-like 的 Profile / Pool / Layout / Metadata 系统。
```

这样后面无论是做城镇、宗门、秘境、灵脉，还是接入 NeoForge worldgen，都不会变成坐标堆砌。

---

## 参考来源

- Open Terrain Generator - CurseForge: https://www.curseforge.com/minecraft/mc-mods/open-terrain-generator
- Dregora RL - CurseForge: https://www.curseforge.com/minecraft/mc-mods/dregora-rl
- RLCraft Dregora - CurseForge: https://www.curseforge.com/minecraft/modpacks/rlcraft-dregora
- OTG WorldConfig 说明: https://openterraingen.fandom.com/wiki/WorldConfig.ini_1.16
- OTG Custom Objects and Structures: https://openterraingen.fandom.com/wiki/Custom_Objects_and_Structures_(BO2/BO3/BO4_configs)
- OTG BO4 CustomStructures: https://openterraingen.fandom.com/wiki/BO4_CustomStructures
