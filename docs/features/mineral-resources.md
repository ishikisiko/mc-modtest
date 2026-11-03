# 矿物贴图与自然生成参数

当前自然生成矿物包括 `myvillage:spirit_stone_ore`（灵石矿）、
`myvillage:deepslate_spirit_stone_ore`（深层灵石矿）、
`myvillage:xuantie_ore`（玄铁矿）和
`myvillage:edge_tempering_sand_deposit`（淬锋砂层）。两种灵石矿共享矿脉配置，
由石头与深板岩替换目标决定方块种类。

## 贴图输入与导出

四张方块贴图从 32×32 调整为 16×16，与 Minecraft 1.21.1 原版像素尺度一致。
`tools/ore_textures/textures.json` 保存实际本地缓存
`minecraft_1.21.1_client.jar` 中石头、深板岩和沙子的原始像素调色板与布局，
并维护各矿物的矿斑位置、形状和颜色。衬底的原始资源路径记录在同一输入内。
灵石保留浅青色，玄铁保留银蓝色，淬锋砂保留金褐色。
玄铁沿用单一方块种类，使用与其深层分布协调的深板岩衬底。

导出四张实际游戏使用的 PNG：

```sh
python3 -m tools.ore_textures.generate
```

输出目录为 `src/main/resources/assets/myvillage/textures/block/`。
玄铁矿和淬锋砂层的原拥有者 `tools/forging/acquisition.py` 调用同一贴图函数，
因此正常炼器资源生成也使用这份输入。生成器仅使用 Python 标准库，预览制图可用 Pillow。

## 频率和单脉规模调整

本次分别将每区块生成尝试次数与单脉规模参数乘以 3。

| 生成入口 | 每区块尝试次数：原值 → 新值 | 单脉规模：原值 → 新值 | 保持的分布条件 |
|---|---:|---:|---|
| 灵石矿 upper | 30 → 90 | `size` 6 → 18 | 主世界，梯形高度 Y 80..384 |
| 灵石矿 middle | 3 → 9 | `size` 6 → 18 | 主世界，梯形高度 Y -24..56 |
| 灵石矿 deep | 3 → 9 | `size` 3 → 9 | 主世界，均匀高度：世界底部至 Y 0 |
| 玄铁矿 | 5 → 15 | `vein_size` 9 → 27 | 主世界，均匀高度 Y -56..-8 |
| 淬锋砂层 | 5 → 15 | `max_blocks` 10 → 30 | 原河流、风袭砂砾丘陵、风袭丘陵、石岸；原地表／水边判定 |

灵石矿的两份 configured feature 与三份 placed feature JSON 是 authored 静态输入，
直接维护于 `src/main/resources/data/myvillage/worldgen/`。
玄铁和砂层参数的唯一输入为
`src/main/resources/data/myvillage/forging/acquisition.json`，
由 `tools/forging/acquisition.py` 导出对应 configured/placed feature。

砂层搜索半径从 3 调整为 4，因为 `EdgeSandFeature.offsets` 的半径 3 圆盘仅有
29 个候选位置，无法达到新的 30 块数量上限。半径 4 提供 49 个候选位置，
实际放置仍由 `max_blocks=30` 截止；`water_radius=4` 和 `max_water_depth=1` 保持原值。

尝试次数与规模参数的名义乘积为原来的 9 倍。实际生成块数仍受矿脉算法、
可替换方块、地表与水域条件、矿脉交叠影响，不能把这个乘积当作实测产量。
维度、生物群系、高度、空气暴露、掉落、附魔与工具等级规则均沿用原资源。
调整作用于后续新生成区块；实际密度与玩家外观验收尚未验证。
