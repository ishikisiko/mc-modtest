# 仙铸 · Minecraft 方块防具十五式

本轮明确替代先前的写实人体建模方向。需求是 Minecraft 修仙模组防具，而不是可以任意建模的玄幻服装。

## 查看

打开 `index.html`。这是自包含 HTML，纹理、几何、缩略图、编辑模型全部内嵌，无 CDN、服务器或安装依赖。试衣间可查看十五套、四件防具、对应扁锭、标准臂/细臂、站立/迈步/举手/蹲下、正侧背面、素模、剪影，以及独立法相开关。可导出当前 BBModel、模型 JSON、PNG 贴图和截图。

## 设计约束

- 原版玩家方头、方身与四肢比例；不得使用写实人台、收细人体腰肢或加长腿来美化展示。
- 几何为有厚度的硬边立方体，允许克制的旋转和局部肩冠辅件。没有球面、圆柱袖、曲面披帛或光滑高模。
- 主要衣身、胸壳、袖、靴是完整大面。允许少量局部分片，禁止整体碎片化、鳞甲化或补丁衣。
- 纹样、高光与材质层次使用有限像素色阶。最近邻采样，不用图片生成、照片纹理、PBR 镜面或泛光掩盖造型。
- 同级三套通过冠、肩、袖、衣摆和冷暖色板区分。高阶的法相可单独隐藏，主体服装不能依赖法相才能成立。
- 所有文字均为中文设计说明。名称、效果方向和 registry ID 均是提案，不代表已启用的游戏内容。

## 文件

`models/<id>/` 中包含：

- `armor-standard.bbmodel` / `armor-slim.bbmodel`：标准臂与细臂的 cuboid 编辑模型，六骨骼分组和像素图集内嵌。
- `ingot.bbmodel`：同材质扁锭编辑模型。
- `texture.png`：实际 512×512 像素图集。图集含两种臂宽所需 UV。
- `ingot-icon.png`：32×32 共用扁锭轮廓图标。
- `armor.obj` / `ingot.obj` 与 MTL：同一几何的标准臂静态导出，引用同目录 `texture.png`。
- `model.json`：HTML 实际使用的模型和说明。
- `armor.authored.json`、`materials.json`：与项目 authored 装备字段对齐的禁用草稿；`enabled: false`。
- `armor.compiled.json`：同一 UV 与立方体的 native equipment 数据草稿，剔除展示人台和法相。

`src/` 为可重新生成的源码；`previews/` 为实际模型渲染；`tests/` 为验证脚本及记录；`DESIGN_BOOK.md` 为完整设计说明。

## 重建

```bash
python -m pip install Pillow
python art/mc-style-armor/src/build.py
```

不需要 Gradle、GPU 或启动 Minecraft。可指定 `--out` 到输出目录。

缩略图来自实际浏览器渲染，不是备用插画。先运行 `build.py`，再运行 `tests/capture.py --all`，运行 `tests/contact.py` 裁为缩略图后再次构建。无缩略图缓存时，试衣间模型仍可用；总览图片需要先采集。

测试需要 Python Playwright 和 Chromium。本次环境通过 `page.set_content` 加载最终 HTML 字节，因为浏览器策略禁止 `file://` 导航；没有对策略作修改，没有外部网络请求。

## 项目对应关系

基线 `fdd3ba3e5227b7b543dfb29cfe5e8362beea6df5`。骨骼和姿态依据 `equipment/poses.json`；cuboid UV 展开、4 纹素/单位及细臂缩放依据 `tools/equipment/export.py`；扁锭图标依据 `tools/ingot_shape.py` 的三个多边形。没有修改正式装备、注册、配方、战斗系统或 Mod 版本。

`armor.authored.json` 的耐久与铁材质是 schema 占位，不是数值设计。现有默认材质绘制器不会自动保留本稿像素纹样；正式集成时应保留本包图集绘制来源和 UV，而不是用默认材质重新烘焙后宣称外观一致。法相是独立概念层，不写入 native 装备编译稿。

## 已知边界

本次交付是 **MC 风格设计与可编辑模型**，不是可安装 JAR。原版骨架姿态预览不等于游戏内穿模验收；长后披仍是胸骨刚性附件，举手和蹲下时可能与其他部位相交，尚未接入披风物理或专用动画。BBModel 已作几何、UV、分组、嵌入纹理和导出回读检查，但未启动 Blockbench 检验导入；UV 面朝向仍需编辑器实测。NeoForge 渲染、PAL 动作、玩家皮肤、多人和 FPS 均未验证。审美认可由用户决定。
