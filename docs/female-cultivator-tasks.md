# 女修两套造型 · 拆包说明（0.41.0）

状态（2026-10-07 06:00）：开工。计划见 `docs/female-cultivator-brief.md`；本文是执行用的拆包说明。分支 `feat/sect-entry`（与入世切片 1 共用，文件不相交，版本 0.41.0）。本轮只做离线预览与服务器无头采集，真机判断留待以后。

## 0. 统筹已定的默认值（owner 未反对即照此执行，写在这里以便推翻）

| 项 | 决定 |
|---|---|
| 小成版配色 | **A：绛紫外衫 + 月白裙 + 金饰**，披帛取淡金/杏色 |
| 入门版发型 | **低马尾 + 发带**（双丫髻在 32 texel/格下容易读成两个方块；低马尾与男修后发板同构，走路有现成的摆动通道） |
| 化身按性别与境界自动换外观 | 是：`gender == f` 且境界 ≤ 炼气 → `f_novice`；≥ 筑基 → `f_adept`；男性 `default` |
| 男修分档 | 不做 |
| 第二只妖兽 | owner 未点名，不做 |
| 外观名 | `default` / `f_novice` / `f_adept`；实体 id 仍只有 `myvillage:cultivator` |
| 文件布局 | `assets/myvillage/npc/cultivator_f_novice_model.json`、`..._animations.json`、`textures/entity/cultivator/cultivator_f_novice.png`（贴图放实体目录下，不另开目录） |
| 骨架 | 与男修同名的身体骨骼（root/body/head/arm_*/forearm_*/leg_*/hair_back），`HITBOX (0.6, 1.9)`、`look` 骨 `head`、`scale 0.5` 相同；女修可以多骨骼（披帛、坠物、马尾）但不能少 |

## 1. 已落的契约（统筹）

- `NpcEntity.DATA_LOOK`（String，默认 `default`），`look()/setLook()`，存档标签 `Look`（`/summon myvillage:cultivator ~ ~ ~ {Look:"f_novice"}`），`protected List<String> looks()` 由子类给出；`CultivatorEntity.LOOKS = List.of("default", "f_novice", "f_adept")`。
- `cultivator.yaml` 已有 `state.synced` 的 `look` 条目；`looks:` 一节由包 F1 加。

## 2. 工作包（文件互不相交；只 `git add` 自己的路径；提交信息末尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；Gradle 一律 `flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew <task> -x generateAllStructures`，只跑 `compileJava compileTestJava` 和自己的 `test --tests`；`/usr/bin/python3` 跑单测；预览用 `python3 -m tools.npcgen preview <look>`（它自己换到 `.venv-preview`）；不 push）

### 包 F0：共享件抽取（先行，Python）

拥有：`tools/npcgen/humanoid.py`（新）、`tools/npcgen/defs/cultivator.py`、`tools/npcgen/build.py`、`tools/npcgen/README.md`（模块表加一行）、`tools/tests/test_npcgen.py`（只改 import，不改断言）。

1. 把男修定义里与"这一套衣服"无关的通用件搬进 `humanoid.py`：`_box`、`_pair`、`_rgb`、`_clamp`、`_mix`、`_tone`、`_hash`、`_xhz`、`_form`、`_weave`、`_fret`、`_rx`、`_loop`、`_leg_angle`、`_sole_low`（参数化 `HIP`/`SOLE_Z`/`WALK_SWING`）、皮肤/头发/布料的基础材质函数（`light`、`skin`、`neck`、`hand`、`nose`、`skull`、`jaw`、`hair_tone` 等能脱离具体衣服的），以及一个 `HumanoidPaint` 基类（`__init__(model, hollow, by_cube)`、`__call__`）。`cultivator.py` 改为 `from ..humanoid import …` 并保留自己的衣服、脸、发型、步态表。
2. **输出字节不变**：`python3 -m tools.npcgen build cultivator --check` 必须仍 PASS；`/usr/bin/python3 -m unittest tools.tests.test_npcgen` 全过。
3. `build.py`：`DEFINITIONS = ("cultivator", "cultivator_f_novice", "cultivator_f_adept")`；`paths(name)` 用定义里的 `ENTITY`（贴图目录）与 `NAME`（文件名）：`npc/{NAME}_model.json`、`npc/{NAME}_animations.json`、`textures/entity/{ENTITY}/{NAME}.png`；男修定义加 `ENTITY = "cultivator"`、`LOOK = "default"`。两个女修定义文件此时还不存在：`definition()` 对缺失模块给出明确报错即可（gate 的 `npcgen_check` 由统筹最后加）。
4. 提交：`refactor(npcgen): shared humanoid parts for more than one cultivator look`。

### 包 F1：外观机制（Java）

拥有：`entity/npc/CultivatorEntity.java`、`entity/npc/CultivatorLooks.java`（新）、`client/entity/npc/NpcRenderer.java`、`client/entity/npc/NpcModel.java`（如需要）、`client/MyVillageClient.java` 的 NPC 两行、`src/test/java/.../client/entity/npc/NpcAssetFilesTest.java`、新 `CultivatorLooksTest`、`genops/contracts/entities/cultivator.yaml` 的 `looks:` 一节与 `rendering.*` 说明、`tools/validate_custom_entities.py`（looks 校验）、`tools/tests/test_validate_custom_entities.py`。不碰 `NpcEntity.java`（统筹已冻结）。

1. `NpcRenderer`：按实体 id + look 读文件：`default` 用现在的三条路径；其他 look 用 `npc/<entity>_<look>_model.json`、`npc/<entity>_<look>_animations.json`、`textures/entity/<entity>/<entity>_<look>.png`。每个 look 一个 `NpcModel` 与一个 layer（`ModelLayerLocation(id, look)`），`getTextureLocation`/`getModel` 按 `npc.look()` 切换（`MobRenderer.model` 是 final 字段：渲染前 `this.model = models.get(look)` 可行，或改继承 `LivingEntityRenderer` 自己管；选最小改动）。未知 look 回落到 default 并 warn 一次。`registerLayer` 对每个 look 注册（`CultivatorEntity.LOOKS`）。
2. `CultivatorLooks.forPerson(PersonView p, List<String> realmOrder)`：`gender` 为 `f` 时按 `realmOrder.indexOf(realmId)`：≤ `qi_refining` 的下标 → `f_novice`，否则 `f_adept`；`m` → `default`。纯函数 + 单测。
3. 刷怪蛋随机：`NpcEntity.finalizeSpawn` 已由统筹写好（`MobSpawnType.SPAWN_EGG` 时在 `looks()` 里随机）；你只确认 `CultivatorEntity.looks()` 返回 `LOOKS`。
4. 契约 `cultivator.yaml` 加：
   ```yaml
   looks:
     - id: default
       model: assets/myvillage/npc/cultivator_model.json
       animations: assets/myvillage/npc/cultivator_animations.json
       texture: assets/myvillage/textures/entity/cultivator/cultivator.png
       definition: tools/npcgen/defs/cultivator.py
     - id: f_novice
       ...
     - id: f_adept
       ...
   ```
   `validate_custom_entities.py`：每个 look 的三个文件存在、模型 schema/骨骼/贴图尺寸同 default 的检查、`look` 骨为 `head`、`scale` 相同、身体骨骼集合 ⊇ default 的；`CultivatorEntity.LOOKS` 与契约一致；`MyVillageClient` 为每个 look 注册 layer。女修文件在包 F2/F3 完成前不存在：校验器报 `missing_file` 是预期，统筹最后合并时再跑。
5. `NpcAssetFilesTest` 改成参数化遍历 looks（文件缺失时 `Assumptions.assumeTrue` 跳过，合并后全跑）。
6. 提交：`feat(npc): one entity, several looks (DATA_LOOK, per-look renderer files, avatar look by gender and realm)`。

### 包 F2：女·刚入门 `f_novice`（Python 美术）

拥有：`tools/npcgen/defs/cultivator_f_novice.py`（新）、`tools/tests/test_npcgen_f_novice.py`（新）、`out/preview/cultivator_f_novice/`（预览产物，不提交）。等包 F0 提交后开工（统筹会说）。

设计稿照 brief §3、§4.1：
- 几何：肩窄 2 texel、不削（无 vest cap）；腰在腰带处收 1–2 texel；裙摆比腰宽、臀位略外放；头顶 58、马尾另加；下颌 11/9/7/5；手小一圈、袖口更长盖半手。
- 服饰：齐腰襦裙——交领右衽窄袖上襦（月白）、半臂短袖对襟小衫（低饱和靛青或茶色，作为 HOLLOW 壳层，前襟开口露出上襦）、从腰到脚踝的一层素色裙（青灰或藕荷，三四条纵向明暗褶）、裙头布带打结垂两短带（两根独立骨骼，走路摆动）、布鞋。只有一处点缀：发带（绛红）。
- 脸：共享女性五官规则（柳叶眉：中段高两端细；眼多一行 texel；外眼角上挑的睫线一 texel；虹膜更亮；唇三 texel 偏暖中间一 texel 略深）+ 稚气（眼略大、眉略平、唇色淡）。额头敞开、不留刘海、鬓角到颅底（KB 39 的教训）。
- 头发：低马尾一束垂背（`hair_back` 骨骼 + 发带 `ribbon` 小 cube），前额开阔。
- 步态：`WALK_SWING 20`、步幅短、髋 1–2° 左右摆（`body` 的 z 旋转）、马尾与裙带次级摆动（相位滞后四分之一拍）；站姿双手交叠腹前（`arm_*`/`forearm_*` 的静态偏转写进 idle 基础姿势），呼吸 0.5 texel。
- 纹理：布的经纬 `_weave` 调大、袖口与裙摆浅色窄边、裙面纵向褶。

工序：
1. 先 `python3 -m tools.npcgen preview cultivator_f_novice --only face` 看脸（大图 + 游戏尺寸），再全身六视图；自己判断是否读得出"女性、入门、素雅"，在定义文件头部写一段设计事实（texel 数字）。
2. 测试 `test_npcgen_f_novice.py`：照 `test_npcgen.py` 的套路（schema、UV 不重叠、alpha 二值且只有声明的壳层镂空、布不平涂、脸对称、下颌逐级收窄、眉在眼上、无刘海、脚着地、走路一脚着地且匀速、idle 不动腿）+ 女修专项：肩宽 < 男修肩宽 2 texel、腰收、裙摆 > 腰、眼两行、唇三 texel 暖色。
3. `python3 -m tools.npcgen build cultivator_f_novice` 写三个文件并提交它们；`--check` PASS。
4. 提交：`feat(npc): female novice cultivator look (f_novice)`。

### 包 F3：女·小有所成 `f_adept`（Python 美术）

拥有：`tools/npcgen/defs/cultivator_f_adept.py`（新）、`tools/tests/test_npcgen_f_adept.py`（新）、`out/preview/cultivator_f_adept/`。等包 F0 提交后开工。

设计稿照 brief §3、§4.2，配色 A（绛紫外衫 + 月白裙 + 金饰，披帛淡金/杏色）：
- 几何同 F2 的女性共有规则（肩/腰/臀/头/下颌/手）。
- 服饰：内层立领中衣露一线；外层广袖大袖衫（绛紫，独立袖口 cube + 一段垂袖，HOLLOW 壳层前襟开口）；齐胸两层裙摆（外层短内层长，月白）；腰间细带配玉佩与丝绦；**披帛**：两条独立骨骼的长带（`drape_right/left`，从双臂后方垂到腿侧），idle 微漂、走路摆动；裙摆用规则纹样（回纹 `_fret`、云纹织带），不画自由形云朵。
- 头发：高髻（头顶偏后一个大髻）+ 一支步摇（金，细杆 + 三 texel 坠，独立 `pin_drop` 骨骼）+ 两侧垂鬟（小环状发束）+ 后发垂至腰；额前开阔。
- 饰件：步摇坠、玉佩、耳坠各一个独立小 cube，idle 有 1 texel 幅度的摆。
- 脸：女性共有规则 + 英气（眉稍扬、眼角上挑更明显、唇色稍深）。
- 步态：`WALK_SWING 20`、髋 1–2° 侧摆、后发与披帛各一条次级旋转通道（相位滞后四分之一拍）；站姿双手交叠腹前。
- 纹理：绸的高光（`_form` 圆度高、高光带窄亮）、织金边、裙摆往下略深的渐变、披帛用边缘浅中段深模拟半透明（alpha 仍只有 0/255）。

工序同 F2（脸先、六视图、测试含专项：披帛骨骼存在且走路有次级通道、饰件 cube 存在、两层裙摆外短内长）。提交：`feat(npc): female adept cultivator look (f_adept)`。

### 包 F4：证据与门禁（F1–F3 合入后统筹另派或自做）

1. `tools/release_gate.py` 加 `npcgen_check("cultivator_f_novice")`、`npcgen_check("cultivator_f_adept")`。
2. `python3 -m tools.npcgen preview cultivator_f_novice`、`… cultivator_f_adept`（含 GIF）。
3. `tools/combat_capture/npc.py` 加 `--look`（summon 带 `{Look:"…"}`，输出到 `out/preview/cultivator/ingame_<look>/`）；跑 `python3 -m tools.combat_capture npc --look f_novice`、`--look f_adept`。
4. 对比页 `out/preview/cultivator/looks/index.html`：三套并排（离线六视图 + 面部页 + 无头四面立绘 + 步行视频）。

## 3. 验收

- `build cultivator --check`、`build cultivator_f_novice --check`、`build cultivator_f_adept --check` PASS；`test_npcgen*` 全过；`validate_custom_entities.py` PASS；`NpcAssetFilesTest` 三套都过。
- 预览页与对比页存在；无头采集两套各有四面立绘与步行视频。
- README 的 Cultivator NPC 节加 Looks 小节与 ledger（全 `not_verified`）；KB 39 加 Looks；CHANGELOG。

## 4. 留给 owner

1. 配色 A 还是 B（现在是 A）。
2. 低马尾还是双丫髻（现在是低马尾）。
3. 脸：两张脸是否"女性、稚气 / 英气"读得出来，只能真机看。
4. 男修要不要也分档。
5. 第二只妖兽叫什么。
