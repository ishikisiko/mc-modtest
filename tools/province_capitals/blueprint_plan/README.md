# 八州整城蓝图

目录入口为 `blueprints/province_capitals/blueprint_plan/catalog.json`。七个区域城使用 `manor_relayout` 正式蓝图后端：将已选定的府邸选址、地标内收、住宅撤并、街坊与庭院重排落实到真实方块。中州继续使用 `central` 适配器。本工具和独立数据包不进入 Gradle、模组 JAR、默认自然世界生成或 NPC 城镇登记。

## 七府正式输入

逐城入口为 `blueprints/province_capitals/blueprint_plan/<region>.json`，布局权威为其 `sources.layout` 指向的 `layouts/<region>.json`。

- `buildings` 逐栋列出保留/迁排位置，允许刚性四向旋转；沿用原建筑族、原尺寸、楼层和功能家具。`retired_buildings` 只能撤并明确住宅，禁止撤并商住门面、干栏住宅或功能建筑。七城住宅由 437 栋减至 386 栋，51 栋原功能建筑全部保留。
- `manor` 引用已有 authored 府邸和已注册 MC 构件切片。必须使用完整原尺寸，不缩放或裁切；剥离独立样板的土块与水面，保留原建筑构件和雕刻 profile。仅在干地预留内整地；河海、沟谷、裂隙保持原地形，以实际结构接触格向下补支承，保留架空层。
- `gardens` 声明私有/公共、原主人或公共维护方、地块及当前设计理由。撤并住宅可同时撤并其原私院；公共院场全部保留，原卸货、廊坪、井场等用途继续经 owning courtyard generator 构造，不能统改成花园。
- 布局中的 XZ `box` / `bounds` 均为半开区间 `[x0,z0,x1,z1]`。编译后的 UI 地块和 `public_passages.bounds` 使用整数闭区间；不能混用最大端点。
- `roads` 是选定的公共主路，`access_hint` 只是原草案的 XZ 接巷提示。编译器补充实际公共巷道；遇保留城门时绕到主路，不能从门墩横穿。交叉路面共用明确标高，城门首层和城外接口保持固定高度；输出逐条道路的 `surface_columns` 记录全部路幅 XYZ。
- `public_passages` 明确门楼、首层、净空和允许开口范围；保留两侧门墩、上部和功能岗位。入户路继续遵守原建筑 12 格上限，私院 16 格、公共庭院 48 格；楼梯在高端放置朝向上坡的踏步，禁止重复 XZ 绕圈升降。
- 七府外部接路对准原府门轴线；南疆原三门府楼位于局部 X38，不能按整个地块中心假造入口。府内建筑和构件沿用原作，不代表已完成玩家在府内行走验收。
- 灵岳东炉石作院保留原位置和功能。东海 X 容量扩至 256 格以容纳海侧外挑，保留岸线海床及公共双埠长度，新增引潮渠独立声明。魔域保留裂隙及三桥，蓝图 Y 容量为 128 格。未新增特效。

## 生成与交付

单城设计迭代可生成同源平面、三维与施工记录：

```bash
python3 tools/province_capitals/blueprint_plan/generate.py \
  --input blueprints/province_capitals/blueprint_plan/donghai.json \
  --out out/preview/donghai-formal
```

正式交付使用实际方块入口，生成 NBT 后逐块回读，预览消费同一次最终施工：

```bash
python3 tools/province_capitals/blueprint_plan/build_all.py \
  --regions beiyuan nanjiang lingyue ximo donghai donghuang moyu \
  --out reports/seven-manor-formal-r1
```

七府不需要基础资源映射，直接读取当前 owning 建筑输入和原府邸 NBT。单城导出：

```bash
python3 tools/province_capitals/blueprint_plan/export_minecraft.py \
  --input blueprints/province_capitals/blueprint_plan/donghai.json \
  --out reports/donghai-formal-r1/minecraft \
  --preview-out reports/donghai-formal-r1/preview
```

每城输出 `preview/index.html`、`blueprint.svg`、`plan.json`、`construction.json`、`checks.json`，以及 `minecraft/` 内的完整地形/建筑模板、`inventory.json`、输入指纹、放置批次与确定性 ZIP。`build_all` 的 `result.json` 保留 `selected/completed/failed/unrun`；失败立即停止并保留原始 traceback 与产物。每轮使用新目录，不覆盖失败证据。

七城全部完成后，可经 `tools/province_capitals/blueprint_plan_review.py --delivery <交付目录> --base <HTTPS资源根路径>` 生成 `review.html` 选图页并附上 authored 输入。该展示器核对布局源摘要，将实际编译摘要内嵌页面；T3 展示不依赖跨域读取 JSON，不需要修改静态服务。

导出核对 authored 输入、生成代码、原府邸切片 SHA；检查全路幅三格净空、门口与庭院实际支撑、逐格高差、全部原功能站位及府邸原构件未被覆盖。所有输出状态通过 modset/catalogue 核对。所有 NBT 模板都回读比对方块；离线通过、Minecraft 运行、映射输入和玩家接受分开记录。

数据包只用于**新建独立测试世界**，使用具有现有七府注册构件的 MyVillage 0.104.0 或当前匹配版本。不要覆盖已有城市或存档。包本身没有自动放置入口；按 `inventory.json` 的 `origin`（默认 `[0,64,0]`）和 `batches` 顺序手工执行 `/function myvillage:blueprint_trial/<region>/batch_XX` 及后续完整组件批次。每批自行有界加载并释放所需区块；执行区域应为空白。七府共用试验原点，须分世界或明确修改 authored 原点后重新生成。

原生清单 schema 2：先放 `tiles`，再放 `atomic_groups`。跨切片的床、晒架及魔域两座完整魔契柱独立后置；模板有独立 `x/y/z` 偏移与 `size`，不能仅按 Z 重新分组或忽略 Y。原地区建筑从 owning generator 重新构造；这套新七府包不声明保留旧城市存档的方块实体，也不执行 NPC 绑定。

## 中州与原区域适配器

`central` 继续适配原地形、模板、公共院场和门路。原适配器导出须先运行 owning generator，并使用全部源哈希、切片哈希匹配的基础资源。中州经 `central/export_game.py` 生成基础资源，然后：

```bash
python3 tools/province_capitals/blueprint_plan/build_all.py \
  --bases reports/blueprint-bases.json --regions zhongzhou \
  --out reports/zhongzhou-blueprint-r1
```

`blueprint-bases.json` 为 `{"zhongzhou":"基础资源根目录"}`。单城入口仍通过 `--base-v2` 指向该根目录，实际读取中州 v1。原适配器继续保留原位置原类型的方块实体 NBT 字节；`nbt_entities.matches_saved_entity` 仅允许原版无样式牌子消息的等价编码，不忽略颜色、样式或其他源字段。中州孤峰亭的无陆路状态和历史未通过证据不转移为七府的验收结论。

## 必要检查与展示

纯蓝图与工具改动不升 mod 版本，不运行 Gradle 或 Minecraft。选择受影响的 Python 输入、门路与原生组件检查即可。示例：

```bash
PYTHONPATH=tools python3 -m unittest \
  province_capitals.blueprint_plan.test_inputs \
  province_capitals.blueprint_plan.test_plan \
  province_capitals.blueprint_plan.test_public_passages \
  province_capitals.blueprint_plan.test_minecraft \
  province_capitals.blueprint_plan.test_nbt_entities
```

可选界面检查：`CHROME_BIN=/path/to/chrome PLAYWRIGHT_MODULE=/path/to/playwright node tools/province_capitals/blueprint_plan/capture.cjs /path/to/city/preview`。它不启动服务；截图为 `captured_for_review`，不能替代玩家审美接受。面向 T3 的资源使用 `https://assets.ishiki333hhh.win/`，复用已有服务。
