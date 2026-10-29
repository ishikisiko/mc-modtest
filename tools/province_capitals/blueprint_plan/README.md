# 八州整城蓝图

入口是 `blueprints/province_capitals/blueprint_plan/catalog.json`，逐城引用独立 authored 方案。区域城读取现有区域生成器；中州适配原作地形、模板、院场和道路。蓝图工具与独立数据包不进入 Gradle、模组 JAR 或默认自然世界生成，也不会自动登记 NPC 城镇。

## 方案与施工

- 每栋在用建筑都有真实足迹地块，明确临街入口、道路及附属庭院。未分配空地保持原状。
- 私院必须连接所属建筑的可达室内，不得穿越公共街道或公共前场。公共庭院直接接路；`courtyard_bindings` 记录既有公共前场的维护归属，不能将其改为私院。
- `garden_overrides` 只调整明确庭院 ID 的位置、标高和类型，不回写原生产环境。中州以 `gardens` 声明新增庭院，并分别记录原有公共院场和住宅地坪，不能把宅基统计成花园。
- `reserve` 是地标的 XZ 整数闭区间；缺省按同源配方、旋转与缩放推导。明确预留可以扩大，但不能缩小真实几何占地。
- `public_passages` 明确门楼、道路、首层标高和开口范围。只在两者交集内施工，保留门墩、上部结构和岗位。普通院门与公共门洞的允许修改格分别记录。
- 局部整地和庭院净空读取方案配置；原有道路使用其实际路幅和身体净空约束。离线连通、原生方块、映射输入和玩家外观接受分开报告。

中州的 `road_adjustments` 保留原路线身份、原失败格、新路线、逐段横断面及实际方块变化。公共支路优先保宽，受原作檐口约束的窄入户段须逐段说明；架空梁板、支墩和地形整地分别限界。`entry_doors` 明确独立入户门，并进入统一门洞施工清单。全城网络分别检查室外主入口，以及允许完整原版门交互、沿原两格室内净空到达的旧入口。原作四座孤峰亭保留真实地块和公共院场，继续明确标记无陆路，不并入临街接驳通过数。

只检查或修改单城方案：

```bash
python3 tools/province_capitals/blueprint_plan/generate.py \
  --input blueprints/province_capitals/blueprint_plan/donghai.json \
  --out out/preview/blueprint-design/donghai
```

该入口产生 `plan.json`、`construction.json`、`checks.json` 和自包含 `index.html`。它用于设计迭代；最终交付使用下面的实际方块入口，预览与数据包消费同一次方块规划。

## 同源实际方块输出

先由原建筑 owning generator 生成基础资源，不能手改切片。七个区域城使用 `regional/v2/export_game.py --regions ...`；中州使用 `central/export_game.py`。已有基础资源仅在全部源哈希与切片哈希匹配时复用。

将各城基础资源根目录写入一个 JSON 对象，例如：

```json
{
  "lingyue": "reports/blueprint-base/lingyue",
  "donghai": "reports/blueprint-base/donghai",
  "zhongzhou": "reports/blueprint-base/zhongzhou"
}
```

所选城市必须全部具有目录映射。串行导出所选城市：

```bash
python3 tools/province_capitals/blueprint_plan/build_all.py \
  --bases reports/blueprint-bases.json \
  --regions lingyue donghai zhongzhou \
  --out reports/blueprint-delivery-r1
```

每城输出 `preview/` 与 `minecraft/`，总入口 `index.html` 链接到对应三维和数据包。`result.json` 保留 `selected/completed/failed/unrun`；失败立即停止，原始 traceback 和产物保留。每轮必须选择新输出目录。

单城原生导出仍可直接调用：

```bash
python3 tools/province_capitals/blueprint_plan/export_minecraft.py \
  --input blueprints/province_capitals/blueprint_plan/donghai.json \
  --base-v2 reports/blueprint-base/donghai \
  --out reports/donghai-native-r1 \
  --preview-out out/preview/donghai-native-r1
```

`--base-v2` 指向基础资源根目录；区域城从该根读取 v2，中州从同一参数读取 v1。实际导出保留原环境、地标、城防及街景设施，并按最终方块重新检查庭院避障。新增门材质来自原建筑生成器；原位置、原方块类型不变的招牌等方块实体保留原始 NBT 字节与类型。

输出包含完整地形与稀疏建筑合并后的分块模板、逐模板 SHA、放置批次、实际方块检查函数及确定性 ZIP。生成前后核对输入哈希，拒绝混合来源清单。每块 NBT 回读比对方块及保留的方块实体；原建筑只允许声明门洞内的变更，岗位和道路支撑必须保留。中州额外逐点检查原模板全部实体，包含地板与桥墩。

数据包只能放进**新建独立测试世界**的 `datapacks/`，使用匹配的 MyVillage JAR，开启作弊，按 `inventory.json` 的原点和批次放置。原生函数路径为 `myvillage:blueprint_trial/<region>/batch_XX`，检查函数为 `myvillage:blueprint_trial/<region>/audit`。放置前需加载相应区块；测试原点必须预留空白空间。

## 针对性检查与展示

```bash
PYTHONPATH=tools python3 -m unittest \
  province_capitals.blueprint_plan.test_inputs \
  province_capitals.blueprint_plan.test_plan \
  province_capitals.blueprint_plan.test_minecraft \
  province_capitals.blueprint_plan.test_public_passages \
  province_capitals.blueprint_plan.test_nbt_entities
```

各城有自己的必要回归；中州检查需将 `MYVILLAGE_CENTRAL_BASE` 指向当前匹配的基础资源。它是资源路径，不是另一套生成器。

可选本地界面检查不启动服务：

```bash
CHROME_BIN=/path/to/chrome PLAYWRIGHT_MODULE=/path/to/playwright \
  node tools/province_capitals/blueprint_plan/capture.cjs /path/to/city/preview
```

检查源身份、地块选择、平面/三维切换、街坊定位、手机布局及控制台错误。图片仅为 `captured_for_review`，不自动代表玩家审美接受。面向 T3 的外部预览资源使用 `https://assets.ishiki333hhh.win/`，沿用现有服务。

最初灵岳试验的原始记录见[灵岳原生试验](../../../docs/testing/2026-10-30-lingyue-blueprint.md)；其中旧轮次证据不转移为后续改动的通过结论。
