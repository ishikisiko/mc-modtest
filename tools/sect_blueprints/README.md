# 宗门蓝图试点

`blueprints/sects/cloud_gate.json` 是“云阙·三崖问道”的独立设计输入，复用已注册的 `cultivation_sect` 组件与城镇的 `Construction`、NBT 写入/回读、方块预览几何；不修改正式宗门生成器、风格或世界生成入口。

本轮先审阅山势、布局、动线与整体气质。当前组件沿用原尺寸、原材质和朝向；山门穿堂是显式开口。主殿独占高崖，外院分置弟子居和丹院，侧峰藏经阁通过石拱桥连接。完整连续山体承托建筑；没有悬浮碎片或新增特效。

生成需要工作区已有的 `exmod/mod_block_catalog.json`，合法性仍由原 `modset.py` 与 catalogue 检查。独立工作区应复用同一份 catalogue，输入摘要会记录其实际内容。生成：

```bash
python3 tools/sect_blueprints/generate.py --out reports/sect-blueprints/cloud-gate
PYTHONPATH=tools python3 -m unittest sect_blueprints.test_generate -v
```

输出同源 `index.html`、`construction.json`、`inventory.json`、高度场和 `minecraft/tile_*.nbt`。每个 NBT 切片都逐块回读，方块实体使用城镇导出器保留原编码；源模板若包含实体则拒绝。生成期间的输入漂移、路径标高冲突、未声明的建筑拆除、道路障碍和断开的入口/锚点都会失败。

坐标与规则只在 authored 输入维护。`bounds` 为 XZ 半开区间，`outline` 描述台地轮廓，`origin` 为模板原点，`entry` 是组件前庭接路端点，路径/锚点 Y 为站立高度。所有路径宽度与入口收窄都显式声明。建筑底层有实物支撑，桥梁实体须经六邻接连接到山体；庭院嵌纹为真实地面方块。预览使用最终施工记录和整数高度场，块名配色与简单形状只用于设计审阅，不能代替正式纹理、碰撞与原生渲染验收。

这个试点只检查道路至组件前庭及明确锚点。组件内部上下层可达、经架实际放置/借阅、NPC 站位与投射、命簿绑定、自然选址、跨区块运行、玩家视觉和手感均未验收。输出没有自动放置函数，不应将预览包当作正式游戏更新。

方向确认后，再将宗门实例的入口、前庭、经架与活动区域改为消费编译清单，并接入有界放置；届时按实际影响同步世界契约和 KB40/43。设计预览产物不随 JAR 打包。
