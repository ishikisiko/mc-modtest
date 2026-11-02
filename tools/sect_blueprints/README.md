# 宗门蓝图试点

`blueprints/sects/cloud_gate.json` 是“云阙·三崖问道”的独立设计输入，复用已注册的 `cultivation_sect` 组件与城镇的 `Construction`、NBT 写入/回读、方块预览几何；方向确认后由 `runtime.py` 导出正式资源，`SectBlueprint`/`SectBlueprintPlacement` 消费同一份施工。

当前山势、布局、动线与整体气质已经确认。当前组件沿用原尺寸、原材质和朝向；山门穿堂是显式开口。主殿独占高崖，外院分置弟子居和丹院，侧峰藏经阁通过石拱桥连接。完整连续山体承托建筑；没有悬浮碎片或新增特效。

生成需要工作区已有的 `exmod/mod_block_catalog.json`，合法性仍由原 `modset.py` 与 catalogue 检查。独立工作区应复用同一份 catalogue，输入摘要会记录其实际内容。生成：

```bash
python3 tools/sect_blueprints/generate.py --out reports/sect-blueprints/cloud-gate
PYTHONPATH=tools python3 -m unittest sect_blueprints.test_generate -v
```

输出同源 `index.html`、`construction.json`、`inventory.json`、高度场和 `minecraft/tile_*.nbt`。每个 NBT 切片都逐块回读，方块实体使用城镇导出器保留原编码；源模板若包含实体则拒绝。生成期间的输入漂移、路径标高冲突、未声明的建筑拆除、道路障碍和断开的入口/锚点都会失败。

坐标与规则只在 authored 输入维护。`bounds` 为 XZ 半开区间，`outline` 描述台地轮廓，`origin` 为模板原点，`entry` 是组件前庭接路端点，路径/锚点 Y 为站立高度。所有路径宽度与入口收窄都显式声明。建筑底层有实物支撑，桥梁实体须经六邻接连接到山体；庭院嵌纹为真实地面方块。预览使用最终施工记录和整数高度场，块名配色与简单形状只用于设计审阅，不能代替正式纹理、碰撞与原生渲染验收。

预览与离线检查覆盖室外道路、设施连接、活动站位与门槛地面。组件内部高层、玩家外观/手感、音效、FPS和多人仍需相应原生证据与玩家接受，不能从离线通过推断。网页产物不随JAR打包。

## 正式游戏资源

```bash
python3 tools/sect_blueprints/runtime.py --out build/generated/sect_blueprints/resources
PYTHONPATH=tools python3 -m unittest sect_blueprints.test_generate sect_blueprints.test_runtime -v
python3 tools/validate_sect_generation.py
```

Gradle 的 `generateSectBlueprintAssets` 接在单体生成之后，产物只包含当前清单、高度场和分块，网页不进入JAR。旧整体宗门模板停止生成和打包。原始组件仍归原 building generator，缺少可选模组仍走原 runtime fallback。

`runtime` 声明入口锚点、执事、活动区、经架及读者；`entry_connections` 只补门槛地面，保留原门体和梁枋，室外道路仍为三格净空。室内门洞按原版两格通行检查。正式导出拒绝未确认设计、断开的设施、站位或门槛，并逐块回读所有NBT。

游戏内使用 `/myvillage sect 42` 试放，或 `/myvillage world sect <id> build [here]` 绑定命簿宗门。正常靠近未实现的山门会分帧建造。经架按声明坐标装配，不再扫描旧台地；活动区与执事也由同一清单平移。请使用当前开发版新世界，旧格式不做迁移。

`bed_pairs` 显式声明本蓝图五对床的头尾和朝向，修正原构件反向的朝向属性；位置、材质、部件类型及建筑归属必须匹配，每格修正都会进入正式清单。批量施工使用与城镇相同的已知形状标记，避免床、门在相邻分块尚未写完时被中间态更新破坏。
