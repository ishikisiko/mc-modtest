# 阡镇 · 《灯尽之前》地点蓝图

镇名由用户确定为「阡镇」。本目录是独立地点的 authored 建筑蓝图输入，
用于主线场景设计、NBT / SCHEM 导出和离线三维审阅；不是已经接入自然生成、
出生、NPC 对话或任务状态的运行时资源。云山仍保留在中州。

## 共同设计方向

西漠商路上的绿洲小镇。修仙仪式感优先，生活功能真实；浅砂岩、夯土色墙、
深色木构与灰瓦为主，铜、青玉和光源只作克制的节点强调。避免同一长方盒子
换牌匾；复杂度来自多个有主次的体块、真实檐廊、错层与院落，而不是随机贴饰。

六处地点：

| 输入 | 地点 | 主要空间 | 最大边界 |
|---|---|---|---|
| `relay_inn.json` | 阡陌驿 · 出生驿站与客栈 | 门楼、向导接待、围院、马棚、二层客房与连廊 | 47 × 32 × 45 |
| `apothecary.json` | 长明药庐 | 取药前店、诊室、药柜、炼丹位、后院、晾药架 | 43 × 28 × 43 |
| `canal_gardens.json` | 青泉渠圃 | 泉渠、水闸、小桥、分层药畦、田舍与取样点 | 48 × 24 × 48 |
| `old_relay.json` | 沙埋旧驿 | 局部塌顶、残存楼层、藏身室、阵纹与运药遗物 | 45 × 28 × 45 |
| `spring_array.json` | 护泉古阵 | 泉眼、下沉阵庭、环廊、阵脚、维护室与双路径 | 47 × 40 × 47 |
| `initiation_court.json` | 问灵小院 | 测灵、传承两处独立仪式空间、回廊、静修台 | 39 × 30 × 43 |

尺寸顺序均为 x / y / z，可小于最大值。低 z 为前，y=0 为基础底面。
这些范围是独栋设计边界，不等于已选定的自然世界坐标。

## 输入与出口约定

- 沿用 `tools/json_to_nbt.py` 的 JSON DSL，`ops` 只用已支持的 set / fill / line。
  authored JSON 是几何和材质的唯一输入；导出物只在忽略目录生成。
- 每个文件包含 `id=myvillage:qianzhen_<slug>_v1`、`author`、`mc_version`、
  `size`、`fill_air=true`、`palette`、`ops`、`walk_targets`、`signs`、
  `presentation`、`site`、`story_anchors` 与 `design_notes`。
- `walk_targets.entry` 是实际可站立的入口脚部坐标，其他目标必须能从入口
  不跳跃到达。主路净宽至少三格、重要入口净高三格、楼梯净宽至少两格。
  预留楼梯井和顶部净空，平台不得仅靠一个楼梯跨越多格高度。
- `site` 使用 `placement_mode=standalone_candidate`、`facing=N`、
  `rotation_quarters=0`、独立 `lot_id`；不得填入旧存档坐标或暗示已经落地。
- `presentation` 含中文 `name` / `summary` / `spaces` / `stage`、`role`、
  `cut_y`、`eye` / `eye_target`、`accent`；多层可加 `upper_cut=[底,顶]`。
- `story_anchors` 为列表，每项包含稳定 `id`、中文 `purpose`、可达的
  `walk_target` 名称，可加 `pos`。这只是未来任务接入的位置约定，不伪造
  已存在的任务机制或 NPC。
- 设施只用实际存在的 block ID 与合法状态；门床成对，液体封闭，灯有支撑，
  容器为空。优先原版方块，现有模组设施应附 `service_blocks` 和接近点。
- 标准屋顶可声明现有 `roof_checks` / `wall_roof_joins`。遗迹的有意坍塌、
  开放阵庭和植物景观应在说明中区别，不能用无意义检查掩盖设计空洞。

## 输出与验证

统一入口由 `tools/qianzhen/build.py` 提供；导出 NBT、Sponge v2 SCHEM、
展开 blueprint JSON、位置约定、三维预览与逐栋报告。检查边界、主线目标可达、
方块 ID 与状态语法、门床配对、导出回读，以及各栋形体和空间差异。
原版状态属性没有调用原生游戏注册表；步行图也不能替代原生碰撞。

```bash
python3 tools/qianzhen/build.py --out out/preview/qianzhen-blueprints --source-id <固定源身份>
python3 tools/qianzhen/capture.py --out out/preview/qianzhen-blueprints
python3 tools/qianzhen/build.py --out out/preview/qianzhen-blueprints --package-only
python3 -m unittest discover -s tools/tests -p test_qianzhen.py
```

`build.py --only <地点 id>` 可导出单处；`capture.py` 需要 Playwright、Chromium
及其系统库，在用户指定的云端执行。生成器输出总览、每处可旋转和剖切的页面、
检查报告与 `qianzhen_blueprints.zip`。完整捕获后再运行 `--package-only`，
把多角度画面纳入下载包。护泉古阵的 `review_views` 另声明了地下侧剖。
局部修订可用 `capture.py --only spring_array` 只重拍改变的地点；此时
`browser-check.json` 只记录本次选择，其他地点的既有证据需要保留并注明来源。

NBT / SCHEM 的原点均为地块最小角，低 z 为前。NBT 最大尺寸不超过
48 格；SCHEM 使用 Sponge v2。包含模组设施的地点需要匹配的 MyVillage
方块注册；容器为空。文件含显式空气，请在独立世界或存档副本试放。
`manifest.json` 的 `origin` 只供相对排布图，连接线没有生成道路。

初次离线建模集中复用了用户指定的现有 Colab 会话，由父任务串行管理。各地点作者
只做自己输入的轻量检查，没有操作 Colab、提交或切换共享分支，也不覆盖其他作者。
正式交付区分离线蓝图证据、原生游戏证据和玩家视觉验收。

用户要求原生验收时使用 Modal `qianzhen-buildings` 预设，详见
[KB63](../../docs/ai-kb/63_qianzhen_blueprints.md)。该流程在独立测试世界中
实际放置、右键开门、按键行走并用结构方块 SAVE；源码不允许在本机启动
这个采集器。前述离线报告和此原生报告分别保留，不能互相代替。

2026-10-23 的原生验证发现并补齐了渠圃入口边界铺地，修正输入仍在
`canal_gardens.json`。六处路线、设施、原生 SAVE 和失败修复记录见
[MC 内验收](../../docs/testing/2026-10-23-qianzhen-mc.md)。
