# 六套 NPC 住宅的设计预览

2026年11月17日用户确认：本批六套用于 NPC 住宅多样性扩展。当前仍为独立设计预览，具体接入见[房屋接入安排](../../docs/features/housing-integration.md)。

本目录只制作离线三维设计，不向游戏写入建筑、选房菜单、结构模板或资源。主代理负责前三套布局和原版模型预览；Astra max 在独立上下文中提出后三套，并制作自己的简化三维预览。

| 作者 | 方案 | 宅院范围 | 空间组织 |
|---|---|---|---|
| 主代理 | 竹庭廊宅 | 48×48格 | 三面生活房与竹庭、连续檐廊 |
| 主代理 | 坡台石木宅 | 44×46格 | 下层起居，上层卧房，侧梯与挑廊 |
| 主代理 | 临溪工坊宅 | 52×42格 | 沿溪深檐廊串联工坊、起居和卧房 |
| Astra max | 松廊小院 | 48×48格 | 主屋内分卧房，西厢厨房，东厢书房 |
| Astra max | 赤陶长屋 | 36×40格 | 两层集中长屋与前门廊 |
| Astra max | 石阶露台居 | 48×44格 | L形低屋、抬高卧房和屋顶露台 |

`model.py` 是前三套的作者输入和预览搭建脚本；读取现有庭院构件与 Minecraft 1.21.1 模型，不改共享生成器。`astra/viewer.html` 与 `astra/design.md` 保留子任务原始交付；构建时只补充材质说明和网址选择，不改建筑几何。Astra 的材质是程序生成的示意像素材质，不能作为游戏资源已制作的证据。

```bash
python3 tools/house_choices/build.py --out reports/house-choices/site
node tools/house_choices/capture.mjs reports/house-choices/capture file:///绝对路径/reports/house-choices/site/index.html
node tools/house_choices/capture_astra.mjs reports/house-choices/astra-capture file:///绝对路径/reports/house-choices/site/astra/index.html
```

本地默认读取 `~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar`；可通过 `--assets-jar` 指定。构建不运行 Gradle 或 Minecraft。`index.html` 可用本地文件打开，`embed.html` 使用 `--base-url` 对应的 HTTPS 数据地址。人工交付前将截图分别放入站点 `captures/` 和 `captures/astra/`；`gallery.html` 是六套方案入口。

前三套检查实际构件的完整实例覆盖、屋面在房间范围的投影覆盖、悬灯支承，以及按当前碰撞计算的13个房间/服务点通路。该投影检查不声称验证所有屋面接缝。浏览器采集检查各视角、去顶、门状态、窄屏和快速切换。Astra 的浏览器检查只证明预览能显示和操作，不含游戏碰撞或原生运行证据。本批的 NPC 住宅用途已确认，真实方块落地、游戏内外观和 NPC 通行仍未验证。

当前交付：[六套总览](https://assets.ishiki333hhh.win/previews/house-choices-six-20261117/gallery.html)、[主代理三套](https://assets.ishiki333hhh.win/previews/house-choices-six-20261117/index.html)、[Astra三套](https://assets.ishiki333hhh.win/previews/house-choices-six-20261117/astra/index.html)。
