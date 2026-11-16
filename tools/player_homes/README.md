# 月洞风格的可选住宅

复用现有月洞水院完整构件，提供三套住宅和原版水院对照。作者输入为 `blueprints/player_homes/catalog.json`，全部以64×72格宅田地块比较；现有月洞构件、原模板、正式城市和运行时注册均不修改。

```bash
python3 tools/player_homes/build.py --out reports/player-house-templates/site
python3 -m unittest tools.player_homes.test_model -v
```

需要Python、Pillow及当前项目生成资源，原版客户端资源默认读取 `/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar`，可通过 `--assets-jar` 指定。输出目录必须尚不存在。`--base-url` 指向实际交付使用的HTTPS资源目录，命令不启动服务器。

每套输出 `home.nbt`、`home.schem`、`home.blueprint.json`、`checks.json`、`plan.svg` 和可旋转、去顶、剖切的三维页面。NBT采用稀疏方块，需在空地放置；SCHEM包含整个包围体积的空气。50格、54格宽的两套超出原版结构方块界面的48格尺寸限制，手动导入优先使用SCHEM。所有模板都依赖当前源仙模组，不能作为纯原版蓝图使用。

`model.py` 通过现有 `Site` 放置完整月门、窗、柱、梁和屋顶截面，保留不可切割构件的检查。`preview.py` 使用实际原版和月洞方块模型与像素材质；完整构件共享网格，仅合并重复显示数据，不改变蓝图方块。显示实例逐格核对源状态，床仍采用简化显示。

生成时逐坐标回读NBT与SCHEM，检查宅田尺寸、示意种植区域、完整构件、入口与房间动线。动线使用当前构件碰撞目录及格点站位，原版门按可开启处理、台阶按半级节点处理；不能替代Minecraft碰撞和玩家操作。负例覆盖入口被堵、台阶移除及完整窗被隔墙覆盖。

可用已有离线浏览器导出同源模型画面，不启动Minecraft或新服务：

```bash
node tools/player_homes/capture.mjs reports/player-house-templates/captures/compact \
  https://assets.ishiki333hhh.win/previews/player-house-templates-20261117/compact_court/index.html
```

默认复用已有Playwright与Chromium路径，可用 `PLAYWRIGHT_CORE`、`CHROMIUM_EXECUTABLE` 覆盖。导出全貌、院内、去顶及宅田摆放四个机位，并记录控制台和窄屏溢出。开发者预览不代表游戏导入或玩家外观接受。本轮不构建JAR、不启动游戏、不写存档；游戏选房菜单、材料消耗及自动施工另行接入。
