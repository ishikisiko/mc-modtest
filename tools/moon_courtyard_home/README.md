# 月洞水院宅居草案

以原版48×28×40格月洞水院为基础，逐格保留已有建筑、水池、桥、门亭、台基和回廊，在原空间内增补一处后部卧房、茶席、厨房与储物。当前只做这一套，外观接受状态为`pending`。

作者输入为`blueprints/player_homes/moon_courtyard_home.json`。`model.py`从原版组合源读取建筑，新增内容不得修改任何原版非空气格或拆分原有整件。卧房门使用现有0.135.1构件，未新增游戏资源或Java代码。

```bash
python3 -m tools.moon_courtyard_home.build --out reports/moon-courtyard-home/site
python3 -m unittest tools.moon_courtyard_home.test_model tools.player_homes.test_model tools.player_homes.test_door
node tools/moon_courtyard_home/capture.mjs reports/moon-courtyard-home/captures \
  https://assets.ishiki333hhh.win/previews/moon-courtyard-home-20261117/index.html
```

生成需要Pillow及原版1.21.1客户端资源，默认读取已有NeoForm缓存，可用`--assets-jar`指定。输出目录必须不存在。只生成蓝图与离线预览，不启动服务或游戏。

输出包含可旋转、去顶、剖切、查看房间/路线以及隐藏新增内容的三维。原有与新增内容按图层分别实例化，保证同材质隔墙可以单独隐藏。床使用保持占地、9/16格高度、朝向和配色的近似模型；其余建筑构件读取当前实际模型。

`blueprint/home.nbt`为稀疏结构，需空地试放；`home.schem`包含完整体积的空气。需要现有源仙0.135.1构件，配套JAR继续使用上一轮交付。宅田视图中自建、种植区域及灰色未设计地面仅为规划示意，未写入房屋蓝图。

离线检查包含原版10,437格与100个整件保留、灯具实际接触支承、6个到达/操作目标、步行中段碰撞、开门后的可达与关门后的卧房分隔，以及NBT/SCHEM逐格回读。屋顶、水体和工作站位的破坏负例必须失败。检查通过只说明这些边界，原生操作、最终外观、声音、性能与多人尚待验收。本轮不升mod版本，不构建JAR，不修改正式城镇与玩家世界。
