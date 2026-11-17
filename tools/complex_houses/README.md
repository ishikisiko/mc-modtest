# 较大住宅的外观与空间预览

这批住宅以体量比例、屋顶轮廓、院落留白和人在廊下行走时的视线变化为主要判断。房间用途用于支撑布局，当前只做3D设计预览。

主代理与独立上下文的 Astra max 各规划三套。主代理的三套如下：

| 方案 | 宅院范围 | 空间安排 |
|---|---|---|
| 双院水廊宅 | 56×52格 | 前部水院、后部竹庭、月洞廊墙与二层卧房；主屋和敞亭采用完整曲线屋顶构件 |
| 叠台书楼宅 | 54×52格 | 主屋与高台书楼隔院错开，上层廊桥形成较高的横向连接，前部楼梯露天 |
| 曲院作坊宅 | 56×50格 | 铜绿屋顶与浅色墙面围出前院、中院、后部小庭，后台地高出两格 |

Astra 的三套为杉廊双院宅（54×50格）、赤陶桥院楼（56×52格）和岩台水庭居（52×48格）。作者输入保存在 `astra/`，构建时从原始 frame、geometry、renderer 重新生成页面，并与子任务原交付的 SHA-256 对照。父代理仅补充作者说明和网址选择，不修改这组三套的建筑几何。

宅田暂按64×72格显示。宅院放在地块后部，前侧留有完整横向空地，范围和用途后续可调整。房间、院落、楼梯、屋顶和相机信息由 `model.py` 维护，构建后可查看 `plans.json`。

```bash
python3 tools/complex_houses/build.py --out reports/complex-houses/site
python3 -m unittest tools.complex_houses.test_preview_models
node tools/complex_houses/capture.mjs reports/complex-houses/capture file:///绝对路径/reports/complex-houses/site/index.html
node tools/complex_houses/capture_astra.mjs reports/complex-houses/astra-capture file:///绝对路径/reports/complex-houses/site/astra/index.html
```

预览读取当前庭院构件和 Minecraft 1.21.1 的模型、像素材质；床和单格灰瓦细部作简化显示，完整曲线屋顶保留原模型。灰瓦使用已注册的庭院屋顶构件，铜屋面使用原版铜台阶。未修改正式建筑生成器，也不导出NBT、SCHEM或JAR。

单格灰瓦的预览表面依据碰撞体轮廓重建并合并内部面，建筑占地、整件曲线屋顶和游戏资源均未改动。共享预览烘焙器只新增可选模型工厂，默认行为保留；旧竹庭廊宅场景字节对照未变化。Astra 使用自己的方块几何和程序像素材质，材质仅为设计示意。

必要检查覆盖完整构件、房间范围的屋面投影、悬灯支承和离线碰撞通路，不能证明所有屋面接缝或游戏行为。浏览器采集用于检查全貌、院内人视、分层、剖切和窄屏显示。视觉方向仍由玩家预览判断。

交付地址使用 `https://assets.ishiki333hhh.win/previews/complex-houses-20261117/`；仅在人工预览交付时发布到既有静态资源服务，不启动新服务。

入口：[六套总览](https://assets.ishiki333hhh.win/previews/complex-houses-20261117/gallery.html)、[主代理三套3D](https://assets.ishiki333hhh.win/previews/complex-houses-20261117/index.html)、[Astra三套3D](https://assets.ishiki333hhh.win/previews/complex-houses-20261117/astra/index.html)。
