# 七城千机阁围院

七座分阁各占一块完整街坊。每城只有一处正门通向公共街道，办事厅、库房、藏卷楼及本型原有的水院、阶道和廊桥均在同一圈院墙内。原模型的房间尺寸、标高、层数、任务榜和题匾随整院一起平移、旋转。中州总舵使用自己的生成器。

城内街坊、宅田与院外场地采用七城地面分区稿。整院模板和全城结构切片已生成并回读。本稿供布局验收，正式世界生成仍沿用已发布版本。[七城三维预览](https://assets.ishiki333hhh.win/previews/seven-qianji-enclosed-20261118/index.html)

## 楼房立面

七城围院内的 22 栋楼共有 46 层。委托厅的墙面退到檐柱后两格，玩家可从廊下去任务榜或大厅。卷宗楼用厚墙、阶梯形木窗格和铜格高窗收住书架一侧，器物库的侧墙开窄窗。各州按本地材料调整墙色、木料和窗芯。

各院主楼的入口设挑檐门廊。中层勘图窗向庭院挑出两格，玩家可从室内走入，站着越过木窗台看院中。顶层的铜星盘嵌在深色石板里，斜向铜轨、水平轴和星钉依次突出。院墙、门楼、屋顶和地块沿用街坊布局。可以在同一机位切换原版和新版，也可从勘图窗内向外看。[立面三维模型](https://assets.ishiki333hhh.win/previews/qianji-branch-facades-20261118/index.html) · [近景对照](https://assets.ishiki333hhh.win/previews/qianji-branch-facades-20261118/gallery.html)

立面使用 `blueprints/qianji/studies/branch_facades.json` 的地区材料与盘纹，由 `tools/qianji/branch_facades.py` 在逐层施工时生成。下方的街坊导出命令会生成新版楼房。网页提供作者朝向的单院 NBT，全城施工时建筑和方块状态按街面一起旋转。

## 落位

城内范围使用局部方块坐标，包含起点，不包含终点。表中迁排数量与上一版七城地面分区稿逐栋比较，包含原有民居和已选的 NPC 院宅。七城共保留原有 378 栋民居和新增 29 座院宅。

| 州城 | 围院占地 | 正门 | 楼房 | 迁排住宅 |
|---|---:|---|---:|---:|
| 东荒 | 67 × 60 | 朝东，经北关横街入院 | 2 | 4 |
| 北原 | 66 × 64 | 朝西，接府东绕街 | 2 | 2 |
| 灵岳 | 70 × 62 | 朝东，接南市横街 | 3 | 8 |
| 东海 | 120 × 108 | 朝东，接府西公街 | 3 | 22 |
| 西漠 | 118 × 108 | 朝西，横街接北驿商街 | 4 | 11 |
| 南疆 | 104 × 116 | 朝北，接东岸公共长廊 | 4 | 17 |
| 魔域 | 118 × 108 | 朝东，接北炉横街 | 4 | 12 |

本稿迁排 72 栋原有民居和 4 座新增院宅，撤并住宅数为 0。两个市集、仓库、药铺、引路亭、铁匠铺和客栈分别保留原有身份和整栋建筑，随所在街区调整位置。旧稿的 35 栋拟迁排和 8 栋拟撤并是按分栋落位估算的历史数字。

西漠北驿商街的两个旧街口均予保留。五格宽的中心线依次经过 `(194,21) → (163,21) → (163,113) → (194,113)`。门前横街从 `(163,71)` 通到 `(197,71)`，紧接西向门槛。街道和泉岸分别占地，院内自有通道。

## 方块与检查

围墙由连续石脚、墙身、间柱和瓦顶组成。正门净宽五格、净高五格。检查从最终城市方块读取完整外圈、实际门槛和任务榜朝向，从市场走到院门后，再沿院内台阶查到各层功能点。屋面检查覆盖四坡屋脊及八角屋面的边缘与斜缝。

分阁构造由 `tools/qianji/enclosure.py` 复用原有分阁模型。院址、朝向、围墙、门楼与各条改线由 `blueprints/qianji/studies/urban_enclosure.json` 管理。整城街坊生成器位于 `tools/province_capitals/inner_neighborhoods/`。

下载并解压本轮输入后，在仓库根目录运行以下命令。输入包含原城市切片、六套已选的 NPC 模板及其构件描述。方块合法性沿用仓库的 `exmod/mod_block_catalog.json`，全新检出可从输入包的同名路径补齐该文件。生成器使用 Python、NumPy、SciPy 和 Pillow。[本轮生成输入](https://assets.ishiki333hhh.win/previews/seven-qianji-enclosed-20261118/generation-inputs.zip)

```bash
YUANXIAN_HOUSING_RESOURCES=/path/to/input/housing/resources \
python -m tools.province_capitals.inner_neighborhoods.export \
  --resources /path/to/input/resources \
  --output reports/housing/enclosed-qianji --structures --volume
```

每城的 `qianji.json` 保存院门、榜位、楼房、目标点和题匾的城市坐标，`plan.json` 保存实际迁排、街道折点与路幅。`structures/compound.nbt` 是带题匾的整院模板，`structures/layout.json`、`terrain.bin.gz` 和 `tiles/` 一起描述整城的方块几何。导出器从 NBT 和高度场逐格重建并比较整城，也逐项核对方块实体。场地净空与高差检查属于离线方块证据。这一布局的游戏内寻路及玩家外观验收尚待进行。[七城结构文件](https://assets.ishiki333hhh.win/previews/seven-qianji-enclosed-20261118/seven-city-layouts.zip)
