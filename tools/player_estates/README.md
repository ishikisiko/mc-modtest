# 灰瓦庭院庄园的独立参考蓝图

依据 Sereyka《Japanese Temple #5 Gray》的公开预览图片重新编排方块，保留前部门楼、两侧多组楼阁、后方三层主楼、灰瓦浅色檐边及庭院关系。原作页面和图片来源保存在 `blueprints/player_estates/gray_courtyard.json`。

本稿没有读取或使用原作者的付费蓝图、世界文件。公开图片未展示的后侧、室内、楼梯和连接处为本稿补全；不能称为原作逐方块副本。当前仅用于独立选型，未注册 settlement family、未写入模组资源或玩家存档。

## 生成

在本仓库根目录运行，需要 Python 3、Pillow，以及 Minecraft Java 1.21.1 的客户端 JAR：

```sh
python3 tools/player_estates/build.py \
  --assets-jar /path/to/minecraft_1.21.1_client.jar \
  --out out/preview/gray-courtyard
python3 -m unittest tools.player_estates.test_geometry -v
```

作者输入负责尺寸、位置、楼层、材质和相机；`geometry.py` 生成实际方块；预览和两种结构文件均读取同一方块集合。输出只能经生成器更新。

## 产物

- `gray_courtyard.schem`：Sponge v2 蓝图，完整体积包含空气，DataVersion 3955。
- `gray_courtyard.nbt`：同尺寸的稀疏原版结构模板；建筑内部未显式填满空气，适合空地或程序放置。整体宽度和纵深超过原版结构方块界面的尺寸限制，优先使用 WorldEdit 的 SCHEM。
- `gray_courtyard.blueprint.json`：逐方块的 schema-1 蓝图。
- `index.html`、`scene.js`、`scene.json`、`atlas.png`：从 1.21.1 原始方块模型与贴图生成的可旋转预览，支持剖切、隐藏屋顶和树木。通过 `--base-url` 指定已有 HTTPS 资源目录；不启动服务。首屏全景图与交互模型来自同一方块集合。
- `plan.svg`：同源俯视图。
- `materials.json`、`rooms.json`、`checks.json`、`manifest.json`：材料数量、房间、实际检查和文件身份。
- `gray_courtyard_source.zip`：作者输入、生成器及所需仓库公共导出/预览代码；客户端 JAR 不随包分发。

`capture.mjs` 可对已发布到资源目录的同源模型生成全景、正面、庭院与剖面图，使用环境变量 `PLAYWRIGHT_CORE` 和 `CHROMIUM_EXECUTABLE` 指定已有浏览器依赖。它只运行离线 WebGL 预览，不启动 Minecraft。截图后刷新文件清单：

```sh
node tools/player_estates/capture.mjs out/preview/gray-courtyard \
  https://assets.ishiki333hhh.win/previews/gray-courtyard-blueprint-20261111/index.html
python3 tools/player_estates/build.py --out out/preview/gray-courtyard --finalize \
  --base-url https://assets.ishiki333hhh.win/previews/gray-courtyard-blueprint-20261111/
```

## 检查边界

生成时全量逐坐标回读 NBT 与 SCHEM，检查方块名在 1.21.1 客户端资源中存在，验证原版模型/贴图可解析，并从前门检查全部 18 处楼层目标。步行检查为离线整数网格，未替代游戏碰撞、流体、寻路或操作验收；预览中的床使用简化几何，其余主体按原版模型显示。

首次检查保留了门前装饰遮挡、入院落差和楼梯平台缺失的失败记录。局部回归保留第三层楼梯平台缺失的负例，不能删除平台后仍通过。玩家视觉、实际游戏导入、音效、性能、多人和住宅归属均为 `not_verified`。
