# 山门（宗门建筑）重构任务书

> 2026-10-06 上午。owner 在 agent-test 里看过 0.35.0 用 `/myvillage world sect 1 build here` 建出的天池楼山门，判定不合格，授权任意规模的重构，不需要再确认。实施由 Opus 5.5 子代理完成，统筹者（Fable 会话）负责设计决定、派工、验收和部署。
> 读者：实施子代理。先读本文件，再读第 4 节列出的代码。两个工作包（第 8 节）并行，分别只碰自己的文件。

## 1. owner 的两条原话（验收的底线）

1. **"很多房子周围会有一些类似之前想做飞檐但没做好的东西，显得像半砖方块浮空在空中，没有跟任何东西相连，这种东西看上去非常不好看。"**
2. **"主线中间那条大道没有跟前后连通：前面有建筑挡在路中间，没有建筑的地方，路上也有很多奇怪的方块挡住路；再有，高度跨越的地方衔接得太烂，现在堆了很多很奇怪的石砖墙，感觉那不该存在。"**

owner 回来会直接进游戏看，不看文档。验收标准是：从门前空地沿中轴走到主殿门前，一路没有东西挡，没有悬空残件，台地之间的衔接看上去像人造的大台阶而不是堆出来的墙。

## 2. 取证与根因

截图在 `out/preview/sect_rework/before/`，整座山门的体素在同目录 `voxels.json`（`[x,y,z,block]`，世界坐标；天池楼锚点 -1380 -60 2454，平坦世界）。

**坐标约定（本文件通用）：**局部坐标 = 世界坐标 − base，`base = anchor − (32, 0, 90)`。场地 x∈[0,63]，z∈[0,179]。台地沿 +z 逐级升高，建筑朝 −z（朝山门口）。E = base.y = 山门台地的 elevation；台地的**地面方块**在 `elevation − 1`，人站在 `elevation`。模板原点放在 `(slot.x0, elevation − 1, slot.z0)`，模板 y0 层替换地面方块，y1 层是台基（人站在台基上是 `elevation + 1`）。

**根因（Explore 子代理读完全部代码后确认，行号指 `src/main/java/com/example/myvillage/sect/SectGenerator.java`）：**

| 现象 | 根因 |
|---|---|
| 悬空的顶半砖，房子周围每隔几格一根孤零零的栅栏 | `placeCoveredGalleries`（:955-988）：廊是一条 1 格宽的 Bresenham 线，每格地面石砖、上面两格空气、`elevation+2` 一块 `STONE_BRICK_SLAB[type=top]`，每第 3 格一根 `OAK_FENCE`。栅栏只有 1 格高，顶半砖全部悬空。弟子、殿两级台地的廊横穿中轴，而且正中 x=31 立着一根栅栏。廊还把它经过的台基挖出一条 1 格宽的沟 |
| 屋角"想做飞檐没做好"的半砖 | 模板生成器 `tools/buildgen/ops.py:739-771` `_place_eave_corners`：每个屋角放一块顶楼梯"帽"（eave_y+1）和一块伸出檐口一格的顶半砖"翼"，翼下面是空的、只挨着帽。此外若干模板里有六面都不挨任何方块的楼梯斜串和活板门（`pagoda_003` 89 个、`pagoda_002` 39、`scripture_pavilion_002` 16、`sect_main_hall_00x` 各 4 等，见 `tools/preview_structure.py` 的读取器统计） |
| 门口的建筑挡路 | 山门殿 `sect_gate` 模板骑在中轴上，只有正面一扇门，后墙在中线处是实心的，走不穿 |
| 第四级台地整排堵死 | 藏经台地放了三座：中轴上的 `scripture_pavilion`（x23..39）加两侧强制的 `pagoda_001`（x4..22 / x40..58），从 x4 到 x58 严丝合缝 |
| 台阶是反的、只有 3 格宽 | `placeAxisStairs`（:816-840）用 `STONE_BRICK_STAIRS[facing=NORTH]`，北是升向 −z，而山门往 +z 升，每级都是 1.5 格的坎；`placeRetainingFaces`（:842-862）的 x 循环把台阶边缘 x29、x33 也盖掉了 |
| "奇怪的石砖墙" | `placeRetainingFaces` 不是砌一面墙，而是把整条 8 行 × 台地宽 × 8 高的带状体积全部填成 `STONE_BRICK_WALL`（整座山门 13,440 块），只在台阶处留 3 格宽的槽。这也盖掉了云海玻璃 |
| 路上的杂块 | 廊的栅栏（含中轴 x=31）；山体噪声：台地之间的带状区按插值 ±3、核心区边缘按 ±5 噪声写石头，会在台地边上冒出 1–3 格、在台阶上方 2 格留石头、在门前冒出单个石块 |
| （owner 没提但明显坏的）detached spire | 三个变体的 detached bounds 都压在主殿 slot 上，桥只有 1 格，建筑被放到山尖高度悬在主殿里。天池楼这次抽到 `none` 才没显出来；`GateBuilder` 按哈希选变体，别的宗门 3/4 概率中招 |

## 3. 目标设计（统筹者定；数字是两个工作包共同遵守的契约，改了要双方一起改）

### 3.1 常量与台地

- `TERRACE_COUNT=5`，`RISE=8`，`DEPTH=28`，`Z_MARGIN=4`，`CLIFF_BACK_HEIGHT=12`，`SITE=64×180`，`MOUNTAIN_MARGIN=28` 不变。z 跨度不变：gate 4..31 / disciple 40..67 / assembly 76..103 / scripture 112..139 / summit 148..175；elevation E, E+8, E+16, E+24, E+32。带状区（band）z 32..39 / 68..75 / 104..111 / 140..147。
- 台地宽度改为**关于 x=31 对称**：`TERRACE_WIDTH=59`，每级收 2（`SUMMIT_TAPER=8` 总收口，即 59/57/55/53/51），x 范围 2..60 / 3..59 / 4..58 / 5..57 / 6..56。
- 新常量：`AXIS_W=7`（中轴走廊 x28..34），`STAIR_W=9`（x27..35），`STAIR_PROJECT=3`（台阶向下一级台地凸出 3 行），垂带 x26 和 x36，侧翼建筑内侧边界 `FLANK_INNER_LEFT_X1=25`、`FLANK_INNER_RIGHT_X0=37`，门前空地 `APRON_ROWS=12`（z −8..3，x21..41）。
- `AXIS_STAIR_W`（5）废弃，Python 校验器的 parity 表同步改。

### 3.2 中轴走廊（御道）与门前空地

- 走廊 x28..34，从 z=−8 到 z=150（主殿 slot 前一行），在每级台地上与地面同高，铺 `POLISHED_ANDESITE`，x=31 一列 `CHISELED_STONE_BRICKS` 作中线。台地其余地面仍是 `STONE_BRICKS`。
- 走廊上方 5 格（地面方块之上 y+1..y+5）必须是空气。做成 `realizeCompound` 的**最后一个 pass**（`clearAxisCorridor`），在所有模板和台阶之后执行；它跳过山门殿 slot 的 z 范围（那里由穿堂处理）。
- 门前空地 x21..41 × z−8..3 整平到 E−1（`STONE_BRICKS`），上方 5 格清空；走廊部分照常铺御道。山体在这个矩形里不许高出 E−1。
- 山门殿穿堂：在 gate slot 内沿 x30..32、z 从 slot.z0 到 slot.z1、y 从 E+1 到 E+3 全部置空气（去掉门、后墙、任何家具；匾额挂在 E+4..E+5，不碰），地面（E 层，即模板台基顶）若非实心则补 `STONE_BRICKS`。穿堂前（z=slot.z0−1）和后（z=slot.z1+1）各放一行 3 格 `POLISHED_ANDESITE_STAIRS`（前 `facing=south`、后 `facing=north`，`half=bottom`）让 1 格的台基高差可走。放完模板后再切，切完再放台阶。

### 3.3 台地之间：大台阶 + 挡土面

设 lower/upper 为相邻两级，`bf = upper.z0 − 8`（带状区第一行），`L = lower.elevation − 1`（下一级地面方块 y）。

- 带状区 z∈[bf, bf+7]、x∈[upper.x0, upper.x1]：填成实心，顶面在 `upper.elevation − 1`（`STONE_BRICKS`，等于把上一级台地向前延伸 8 行），内部用 `STONE`，**不用任何 `*_WALL` 方块**。朝 −z 的那一面（z=bf）暴露的 8 层用 `STONE_BRICKS`，最上一层（y = upper.elevation − 1）用 `CHISELED_STONE_BRICKS` 作压顶；不做城垛、不做栏杆。带状区在 upper 宽度之外、lower 宽度之内的格子按 lower 地面处理（这是收口自然留下的 1 格条）。
- 台阶占 x27..35（9 格）、z∈[bf−3, bf+7]（11 行）：
  - 行 bf−3..bf：`STONE_BRICK_STAIRS[facing=south, half=bottom]`，y 依次 L+1, L+2, L+3, L+4；
  - 行 bf+1..bf+3：平台，`STONE_BRICKS` 整块，y = L+4；
  - 行 bf+4..bf+7：楼梯，y 依次 L+5, L+6, L+7, L+8（L+8 = upper.elevation − 1，与上一级地面齐平）。
  - 每个踏步/平台方块下方到 L 填实 `STONE_BRICKS`；每个踏步上方 4 格空气。
  - 垂带：x26 和 x36 两列，z∈[bf−3, bf+7]，`STONE_BRICKS` 整块，每行顶面 = 该行踏步顶面 + 1（平台处 = L+5），到最上一行封在 upper.elevation（比上一级地面高 1 格）为止；不用 `*_WALL`。
  - 台阶是**最后放的台地构件**：带状区填充、挡土面、山体都不能再写到台阶格子及其上方 4 格。
- `SectCourtyard` 把台阶矩形含垂带（x26..36 × z[bf−3, bf+7]）外扩 1 格排除。

### 3.4 建筑摆放

- 中轴上只允许山门殿（gate slot，x21..41，z4..19，穿堂见 3.2）和主殿（summit，关于 x=31 居中：宽 27 → x18..44，宽 25 → x19..43；z 后对齐 151..175）。
- 侧翼成对、关于 x=31 镜像：左翼 x1=25、右翼 x0=37，宽度用**各自实际模板**的宽度（不是 archetype 的最大宽度），z 对齐也按实际模板深度算（back/center 真正生效）。各级：
  - gate：`bell_drum_tower` 左右各一，后对齐（z1=31）。
  - disciple：`disciple_quarters` 左右各一，居中（21×18 → z45..62）。
  - assembly：`alchemy_room` 左右各一，居中。
  - scripture：**`scripture_pavilion` 左右各一**，居中（17×19 → z116..134，x9..25 / x37..53）。中轴上那座藏经阁取消；`pagoda` 不再出现在台地上（只作为 detached spire 的变体保留）。
- 廊（galleries）**整个取消**：plan 里不再生成 `GalleryLink`，`placeCoveredGalleries` 删除；`SectCourtyard` 对空列表照常工作。（真正可走的廊以后另做。）
- 云海 `placeCloudSea` 删除（现状本来就被挡土体盖没了）。
- detached spire：`realizeFeature` 加门槛——只有 detached bounds 外扩 1 格与所有 slot、所有台地矩形、所有台阶矩形都不相交时才建；否则跳过并在 `BuildStats` 里计数、日志一行。变体列表和 plan 记录不动（`GateBuilder`、`AvatarPlannerTest` 依赖它们）。正确的 spire 放到下一阶段。
- 模板放置的安全网：放置前丢弃模板里六面都不接触任何非空气方块的方块（孤立楼梯串、孤立活板门）。模板本身的修正由工作包 B 在生成器里做，两边都做。

### 3.5 山体（Java 侧，本阶段只改核心区）

`SectMountain.height()`：核心区（x2..60 × z4..175，加门前空地 x21..41 × z−8..3）内**不加噪声**：台地格 = elevation−1；带状区格 = upper 宽度内取 upper.elevation−1，否则 lower.elevation−1；门前空地 = E−1；核心区内不属于任何台地/带状区的格子（收口留下的边条）= 最近台地的地面 − 到它的距离（坡度 1，无噪声）。核心区外的裙摆本阶段不动（下一阶段再把 ±5 的逐格噪声改成粗网格平滑噪声）。

### 3.6 不动的

台地数量和 z 跨度、建筑角色、`generateForcedAt` 签名、运行时建造路径、`GateRealizations`、`templateFootprint` 那个 switch 的**行格式**（`tools/buildgen/tests/test_pagoda_landmark.py` 用正则解析它）、NBT 模板的尺寸。

## 4. 代码地图（来自 Explore 报告，行号为改前）

- `SectGenerator.java`（1276 行）：常量 :54-73；`buildAt` :112-174（base = anchor−(32,0,90)，worldgen 路径 buildMountain→writeMountain→placeCloudSea→realizeCompound；命令路径只 realizeCompound）；`computePlan` :332-398（台地 :339-350，slots roster :403-441，`slotBounds` :451-478，`zSpan` :480-490，axisCells :374-379 只用于校验，stairs/retaining :383-392，`buildGalleries` :492-517，`buildFeature` :519-553，变体 :587-606）；`validatePlan` :686-755；`realizeCompound` :776-785 顺序 carveTerraces(:791-814) → placeAxisStairs(:816-840) → placeRetainingFaces(:842-862) → placeCliffBack(:879-907) → realizeSlots(:909-949，origin=(slot.x0, elev−1, slot.z0)，先 clearVolume，默认 StructurePlaceSettings，无过滤) → placeCoveredGalleries(:955-988) → realizeFeature(:995-1038)；`templateFor` :610-632；`templateFootprint` :655-673；记录类型 :1205-1275（`Rect(x0,z0,x2,z1)`，x2 是最大 x）。
- `SectMountain.java`：常量 :22-27，`height()` :149-184，带状区插值 :192-201，cloudSeaY :84。
- `SectSink.java`：`set/clip/surfaceY/loadTemplate/placeTemplate`。模板加载经 `town/ModBlockFallback.loadTemplate`（:85-100），缺 mod 时按 `data/myvillage/mod_block_fallbacks.json` 替换且丢掉方块属性。
- `SectCourtyard.java` :50-94：读台地、slot（并模板尺寸，外扩 1）、廊格子、feature；排除台地边行；不读台阶和挡土面。
- `SectStructurePiece.java`：worldgen 逐区块调用同一套 realizer，按区块盒裁剪。
- `sim/runtime/avatar/GateBuilder.java` :39-117：命簿建造入口，锚点 y 取 MOTION_BLOCKING_NO_LEAVES，调 `generateForcedAt`。
- 钉住几何的东西：`src/test/java/.../sect/SectCourtyardTest.java`（种子 7 的格数 355/507/627、具体格子、footprint）；`sim/runtime/avatar/AvatarPlannerTest.java` :46-106（最低台地放得下 40 个化身；40 个宗门覆盖所有变体）；`tools/world_sim_avatar_evidence.py` :61,:223-247（院落 x22..40 z21..30 必须是石砖地面且露天）；`tools/validate_sect_generation.py` 只跑 Python 镜像 `tools/buildgen/sect.py`（:867-1143 的 plan 校验、:121-160 模板尺寸、:163-182 parity 常量）和 `tools/buildgen/sect_mountain.py`（:307-408 山体契约：台地高度、裙摆坡度、崖、云海、spire）；`tools/buildgen/tests/test_pagoda_landmark.py` :124-155（正则读 Java 的 footprint 表）；发布闸门 `tools/release_gate.py` :298-316（generate-all-structures 重生成模板并要求 `src/main/resources` 无 git drift、各校验器、gradle build）。
- 模板生成：`tools/generate_all_structures.py` → `tools/buildgen/archetypes.py`（`build_sect_gate`/`build_sect_main_hall`/`build_scripture_pavilion`/`build_alchemy_room`/`build_disciple_quarters` :2172-2293）→ `tools/buildgen/ops.py`（`_place_eave_corners` :739-771，`_add_eave_brackets` :773-799，`sweeping_eave_roof` :802-961，tiered/pagoda/pavilion/bell 屋顶 :1124/:1247/:1308/:1327）。NBT 读取器 `tools/buildgen/nbtread.py`、`tools/preview_structure.py`。
- spec：`openspec/specs/sect-compound-layout/spec.md`、`sect-compound-realization/spec.md`、`sect-mountain-derivation/spec.md`、`sect-worldgen-structure/spec.md`。

## 5. 验收

### 5.1 Java 侧（进 gradle test）

先试 `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` 能否在本仓库的测试环境里跑通（现有测试都没碰过方块状态）。能跑通就写一个内存 `SectSink`（`set` 进 HashMap；`loadTemplate` 用 `NbtIo.readCompressed` 读 `src/main/resources/data/myvillage/structure/*.nbt` 再 `StructureTemplate.load(BuiltInRegistries.BLOCK.asLookup(), tag)`；`placeTemplate` 不走 `placeInWorld`，而是遍历模板的方块列表直接 `set`，可用反射取 `palettes` 或 `filterBlocks`），对固定种子（至少 7 和另两个）跑完整 `realizeCompound`（和 worldgen 路径的 writeMountain），断言：

1. **中轴可走**：z 从 −8 到 150，x∈{29,30,31,32,33}（gate slot 的 z 范围内只看 x∈{30,31,32}），每格"落脚高度"（该列最高非空气方块的顶面；楼梯按整格算）相邻 z 之差 ≤ 1，且落脚面之上 3 格空气。
2. **无悬空残件**：输出里六面都不接触任何非空气方块的方块数 = 0。
3. **无墙块**：输出里 `*_WALL` 方块数 = 0。
4. **建筑不压中轴**：除 gate 和 summit 外，每个 slot 的矩形与 x28..34 不相交；每级台地的两座建筑关于 x=31 镜像（偶数宽度允许 1 格误差）。
5. **台阶形态**：四处带状区各存在 x27..35 的台阶区，每列落脚高度沿 +z 单调不减、每步 ≤ 1，总升高 8。
6. `SectCourtyard` 的格子都不落在台阶、垂带、slot、走廊之外的障碍上（用 1 的结论复核：每个院落格的上方 2 格空气、脚下实心）。

如果 Bootstrap 在测试环境跑不通，把 1–5 中不需要模板的部分（台地、带状区、台阶、走廊、门前空地、山体核心）用内存 sink 在"模板返回空"的情况下断言，并在报告里写明模板相关的断言靠统筹者在 owner 电脑上的扫描脚本（`out/preview/sect_rework/` 那套体素扫描）验收。

### 5.2 Python 侧（进发布闸门）

- 所有宗门系模板（`sect_gate_*`、`sect_main_hall_*`、`scripture_pavilion_*`、`alchemy_room_*`、`disciple_quarters_*`、`bell_drum_tower_*`、`pagoda_*`、`pavilion_*`）重生成后：六面都不接触任何非空气方块的方块数 = 0；`_place_eave_corners` 不再放无支撑的翼。写成 `tools/buildgen/tests/` 下的一个测试并加进 `tools/release_gate.py`。
- `tools/validate_sect_generation.py` 在新几何下通过。

### 5.3 整体

`python3 tools/release_gate.py` 全过（由统筹者在合并两个工作包后跑）。owner 电脑上的目视验收由统筹者做。

## 6. 运行条件

- 主机 4 核 7 GB。Gradle 和 Minecraft 任务用 `flock /home/ubuntu/code/mc/.mc-heavy.lock <cmd>` 串行；`python3 tools/release_gate.py` 自己拿锁，外面不要再套。Python 校验器用 `/usr/bin/python3`（默认 python3 没有 PyYAML）。
- 不碰 owner 的未跟踪文件 `docs/jev.md`、`docs/yunshan-city-build-plan.md`、`image.png`。
- 不建 OpenSpec change。被新几何推翻的 spec 条目直接改原文到与现状一致，`openspec validate --specs --strict` 保持通过。
- 子代理不碰 DevHost/DevBridge。

## 7. 交付

提交说明写清改了什么、跑了哪些测试、结果如何。回复里给一份简短报告：第 3 节每条做到没有、怎么做的；偏离和理由；改动的 spec 条目、校验器和测试；没做完的。

## 8. 工作包

### 工作包 A（Java，主工作树，分支 `feat/world-sim`）

文件：`src/main/java/com/example/myvillage/sect/*`、`src/test/java/com/example/myvillage/sect/*`、`sim/runtime/avatar/GateBuilder.java`（如需）、`AvatarPlannerTest`（如需）、`tools/world_sim_avatar_evidence.py` 的院落矩形（如需）、`openspec/specs/sect-*/spec.md`、`docs/ai-kb/40_world_sim.md` 的 P3 段、`CHANGELOG.md`。
内容：3.1–3.6 全部、5.1 的测试、spec 原文修正。最后用 `python3 tools/bump_version.py 0.35.1` 升版本并在 CHANGELOG 新段落里写本次重构（owner 的两条原话对应的修正放最前）。跑 `flock ... ./gradlew build`（含测试）、`openspec validate --specs --strict`、`/usr/bin/python3 tools/buildgen/tests/test_pagoda_landmark.py`。**不要跑完整发布闸门**（Python 镜像由工作包 B 同步改，合并后由统筹者跑）。
不碰：`tools/buildgen/*`（除上面提到的 evidence 脚本）、NBT 模板、`tools/validate_sect_generation.py`。

### 工作包 B（Python，独立 worktree）

文件：`tools/buildgen/ops.py`、`tools/buildgen/archetypes.py`（如需）、`tools/buildgen/sect.py`、`tools/buildgen/sect_mountain.py`、`tools/validate_sect_generation.py`、`tools/buildgen/tests/*`、`tools/release_gate.py`（只加一步）、`src/main/resources/data/myvillage/structure/*.nbt`（重生成的产物）。
内容：
1. 模板源头修正：`_place_eave_corners` 去掉翼和帽（檐口做直）；找出并修正产生孤立楼梯串/孤立活板门的 op（pagoda 各层角、scripture pavilion、main hall、bell tower），使 5.2 的断言成立。斗拱/贴墙的栅栏托（有面接触的）保留。
2. 重生成全部模板（`tools/generate_all_structures.py`，用闸门同样的调用方式），只提交内容变化的 NBT；跑 `validate_generated_structures`、`validate_mod_block_fallbacks`、`validate_plaque_bindings`、`check_style_policy` 确认仍过。
3. Python 镜像同步到 3.1–3.4 的几何：宽度与对称、slot 规则（左翼 x1=25 / 右翼 x0=37，按实际模板尺寸对齐，scripture 两座 pavilion、无 pagoda）、无廊、台阶矩形（9 宽、带状区前凸 3 行）、挡土面、parity 常量表（去掉 AXIS_STAIR_W，加 AXIS_W/STAIR_W/STAIR_PROJECT 等，和 Java 同名同值）、山体契约（去掉云海检查；核心区无噪声、门前空地平）。`validate_sect_generation.py` 和 `generate_sect_plan_preview.py` 都要能跑。
不碰：Java、openspec、README、CHANGELOG、版本号。
