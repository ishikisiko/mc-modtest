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

## 中州选址草案

`blueprints/player_estates/zhongzhou_placement.json` 为独立选址输入，允许撤并相交民居及其私院，保留功能建筑、主街、城墙、水体、公共院场和传送亭。当前首选西坡中段；原 98×111 格占地旋转后为 111×98 格，四周另留三格退界带供巷道调整。

```sh
python3 tools/player_estates/zhongzhou_plan.py --out out/preview/zhongzhou-player-estate-plan
```

该命令重建真实建筑包围盒，并经中州 owning `source_game.mjs` 取得一格地形。缓存同时核对全部源摘要及数据文件摘要。输出规划 JSON、同源总平面、地形剖面和三个候选的交互比较；道路按完整路幅及保护距离检查，拟撤并建筑必须全部为 `house`。需要 NumPy、Pillow 和 Node。

选址比较发现该地块有约五十格现状高差。脚本保留既有每列三格的整地约束，明确记录 `flat_paste_feasible=false`；填挖量按庄园自身占地计算，三格退界带不强制铺平。本节命令只确定占地、撤并范围、朝向及生活巷绕行的平面草案，分台地构造见下一节。原巷尽头若在撤并区内则撤去，只将保留街段接入新的退界环巷，不能把旧尽头接到新主楼内部。规划一致性通过不能视为施工通过。正式中州蓝图、JAR 和玩家存档均未修改。

独立平底稿的 `gray_courtyard_source.zip` 只包含其自身生成器及公共依赖；中州选址输入和生成器在本目录另行维护，规划输出不进入该蓝图包。

## 已选定的西坡中段与分台地样稿

用户已选择西坡中段。`zhongzhou_placement.json` 的 `approved_retirements` 固定获准撤并的 31 栋民居；重新计算的范围必须逐项一致。其余候选仅保留为选址比较，不能自动切换到另一地块。

`blueprints/player_estates/zhongzhou_terraces.json` 管理坡地适配。每栋房屋仍用原灰瓦构造；东侧厅和主楼前移，前厢廊内收，后方主楼与两阁共用台地。首层标高由同源地形与原整地边界派生。房间和前廊仅做有界下挖，低侧使用真实柱墩和承梁；高架支承高度单独报告，不把高架结构写成小幅整地。

```sh
python3 tools/player_estates/build_terraces.py --geometry-only
python3 -m unittest tools.player_estates.test_terraces -v
python3 tools/player_estates/build_terraces.py
```

构造先核对替换清单，重建当前中州的显式街路及 638 条原入户路线，再生成外巷、门前登阶、中央登阶与六段有顶阶廊。逐栋地板、阶廊三格路幅、路口平台和连通检查读取同一方块集合。窗下花槽和突出百叶只在明确通道净空内让开，不挖掉整面建筑外墙。共享生成器还修正了奇数层高的 U 形梯顶端平台覆盖最后一级台阶的问题。

连通检查要求上升一格处有正确朝向的楼梯，不能靠把普通方块当作可直接迈上的台阶通过；缺梯、楼梯反向以及累计超限下挖均有负例。该检查仍为离线格点模型，不替代玩家碰撞、操作与手感验收。新庄园 18 处楼层和所有已建路径中心分别检查；保留民居的完整入户接路和实际世界状态仍单独记录，不能从原路线重建推断全部游戏验收完成。

完整生成输出 `west_middle_terraces.schem`、`west_middle_terraces.nbt`、逐项回读结果，以及使用原版模型和贴图的局部三维预览。下载文件包含展示用山体，坐标方向为独立展示方向，**只用于空白测试区域**。它没有正式中州注册、自动拆房、NPC 迁移、领房或所有权逻辑；不写玩家世界，不升级 mod 或构建 JAR。坡地外观确认与正式世界接入是后续独立步骤。

预览截图后可刷新内嵌首屏与文件身份，不重新生成建筑：

```sh
python3 tools/player_estates/build_terraces.py --finalize
```
