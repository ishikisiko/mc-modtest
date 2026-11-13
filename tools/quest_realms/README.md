# 委托秘境地图预览

本目录维护六张探索地图与两张纯战斗地图的作者输入和三维模型。预览命令只导出离线模型；`game.py`将两张纯战斗图编译到`build/generated/quest_realms/resources`，由0.131.0的[游戏远征运行时](../../docs/features/combat-quest-runtime.md)营造独立维度。其余六张仍处于设计阶段。

```bash
python3 -m tools.quest_realms.preview --out reports/quest-realms/preview --zip
python3 -m unittest tools.tests.test_quest_realms -v
```

只导出两张纯战斗图，并检查连续通路：

```bash
python3 -m tools.quest_realms.preview --maps bandit_stronghold beast_den --out reports/quest-combat/preview
python3 -m tools.quest_realms.audit --out reports/quest-combat/preview
```

天然岩石、木板和骨块材质从已有Minecraft 1.21.1客户端jar读取，默认查找本机NeoForm缓存；可用`--minecraft-jar /path/to/client.jar`显式指定。预览包包含实际使用的材质及每张图片的SHA-256，原始图片不经过修改。

打开输出目录中的 `index.html` 可审阅。预览包包含本地数据脚本、项目像素材质和已纳入仓库的 Three.js，直接打开文件时不依赖额外服务。HTTPS 发布时使用同目录 JSON。

- 地图的地形、节点、路线、建筑、机关依赖和观察镜头只在 `blueprints/quest_realms/*.json` 维护。
- `model.py` 的 `FORMS`、`ROOFS` 分别注册构造与屋顶。新构造通过注册表接入。
- 两张纯战斗图使用`combat.py`的显式场景与构件注册表，包含木石建筑和不规则岩洞，保留完整屋顶和岩顶。
- `preview.py` 生成场景、像素材质副本、作者输入摘要与检查记录。JSON 和本地数据脚本服务于同一份当前场景。
- `viewer.js` 根据阶段显示闸门和水位，提供全景、地层剖视、隐藏屋顶、分层路线图、固定玩家镜头与沿路镜头漫游。
- 正常玩家镜头恢复完整山体和屋顶。地层剖视切去前侧几何，显示地下洞室；它是设计审阅视图。

预览检查覆盖阶段可达性、依赖、路线回环、关键点的玩家站立体积和固定观察镜头。两张纯战斗图各有24个关键点、一个全景与五个玩家高度镜头；另按0.4格间隔检查连续通路的足下支撑、头部空间和台阶。站立体积使用0.6格宽、1.8格高，镜头高度1.62格。正式方块检查使用`game_audit.py`和`tools.tests.test_quest_game`；原生光照、碰撞与玩法通过服务器和`native_evidence.py`另验。

`capture.cjs`使用已安装的Playwright与Chromium把模型渲染成图，不启动服务。需要时通过`PLAYWRIGHT_MODULE`与`CHROME_BIN`指定本机工具路径：

```bash
node tools/quest_realms/capture.cjs reports/quest-combat/preview
```

它记录固定镜头、阶段和页面宽度检查。`review.py`根据`screenshots.json`中的当前地图生成审阅页，资源基址必须使用约定的HTTPS域名。以上均为模型渲染与界面证据，外观接受待用户确认。

网页中的“沿主路漫游”只移动镜头，网页阶段按钮也只控制模型显示。它们不能作为正式游戏中的战斗或操作证据。
