# 女修造型计划：两套女性修仙者（刚入门 / 小有所成）

状态（2026-10-07）：两套外观 `f_novice`、`f_adept` 已落地于 0.41.0（分支 feat/sect-entry，未合并、未 push）；离线预览（`out/preview/cultivator_f_novice/`、`out/preview/cultivator_f_adept/`）与无头采集（`out/preview/cultivator/ingame_f_novice/`、`ingame_f_adept/`）齐全，三套对比页 `out/preview/cultivator/looks/index.html`；待 owner 验收。2026-10-07 owner 在真机看过后说"还是不好看，可能脸部偏二次元的会好看一些"，第一版二次元草稿（细眉、高光大眼、点鼻）owner 看预览后仍说不行、要照着皮肤仿，0.44.1 于是研究了 119 张二次元 MC 皮肤（`out/preview/cultivator/anime_refs/`）并照其画法重画三张脸（刘海盖上半脸且分发丝、眼在下半脸且上深下浅、外侧一列白、无眉无鼻、腮红、小嘴；入门版琥珀眼，小成版紫眼口脂），见 `docs/ai-kb/39_humanoid_npcs.md` Faces 一节；两张脸的验收待 owner。机制见 `docs/ai-kb/39_humanoid_npcs.md` 的 Looks 一节。

owner 2026-10-07："npc 和妖兽建模，修仙者女性增加两个不同的建模，一个是偏向普通古风意味刚刚踏入门道；一个是更华丽一些的意味小有所成，并且女性特征要和男性区分。"本文是计划，开工前另写拆包说明。与 `docs/player-sect-entry-brief.md` 并行，文件不相交。本轮 owner 电脑不可用，只做离线预览与服务器无头采集，真机判断留待以后。

## 1. 现状（能复用什么）

- 现有男修 `myvillage:cultivator`：`tools/npcgen/defs/cultivator.py`（874 行）产出模型、动画、贴图三件，半格单位（`scale 0.5`，32 texel/格），一层衣一个 cube，贴图按三维位置着色（`shade.py`），剪切透出下层。owner 对衣服、背身、姿势、走路已认可，脸按"俊朗英气"重做过一次（`docs/ai-kb/39_humanoid_npcs.md` 的 Faces 一节是经验清单）。
- 运行时：`NpcEntity` 通用，`NpcRenderer` 按实体 id 读三个文件；一个实体类型只有一套外观，"同一实体的贴图变体还没做"（KB 39）。
- 命簿人物有性别字段 `Person.gender`（m/f），化身现在不分性别，全用男修的样子。
- 工具：`python3 -m tools.npcgen build|preview <npc>`，`python3 -m tools.combat_capture npc --parts idle,walk`，gate 里有 `npcgen_check("cultivator")`，`validate_custom_entities.py` 钉同步字段与资源路径，契约 `genops/contracts/entities/cultivator.yaml`。

## 2. 机制：一个实体类型，多套外观

不加新实体 id。给 `NpcEntity` 一个同步字段 `DATA_LOOK`（字符串或小整数，默认 `default`），`NpcRenderer` 按 `<entity>_<look>` 读模型、动画、贴图：

```
assets/myvillage/npc/cultivator_model.json                 默认（现男修）
assets/myvillage/npc/cultivator_f_novice_model.json         女·刚入门
assets/myvillage/npc/cultivator_f_adept_model.json          女·小有所成
textures/entity/cultivator/cultivator_f_novice.png  …
```

- `/summon myvillage:cultivator ~ ~ ~ {Look:"f_novice"}`；刷怪蛋随机三选一（或按配置）。
- 命簿化身按人物选外观：`gender == f` 且境界 ≤ 炼气 → `f_novice`；`gender == f` 且 ≥ 筑基 → `f_adept`；男性照旧。这样院子里的人群立刻有了性别与资历层次，不需要新实体。
- 每套外观是独立的 npcgen 定义：`tools/npcgen/defs/cultivator_f_novice.py`、`cultivator_f_adept.py`，共享男修定义里的通用件（`_box`、`_pair`、`_Paint` 的材质函数、步态函数），身体骨架与 `HITBOX (0.6, 1.9)` 相同，所以 `animateWalk`、名牌、化身格子都不用改。
- 契约：`cultivator.yaml` 加 `looks:` 一节列出三套文件路径与 `state.synced` 的 `look`；校验器跟着校验每一套的三个文件与骨骼。

## 3. 女性特征怎么与男性区分（两套共有）

在 32 texel/格的密度下，差异必须做进几何而不是只靠贴图（和脸的教训一致）：

| 部位 | 男修（现状） | 女修 |
|---|---|---|
| 肩与腰 | 肩宽、肩头斜削 | 肩窄 2 texel、不削；腰在腰带处收 1–2 texel；裙摆比腰宽，臀位略外放，整体轮廓上窄下宽 |
| 身高 | 头顶 60，发顶 61 | 头顶 58，发髻另加，总高相近，比例显得头稍大、颈稍长 |
| 头 | 颅骨 + 三段渐窄下颌（13/11/9/7） | 下颌更窄更短（11/9/7/5），颏尖；脸更窄长、颧线柔 |
| 眉眼 | 剑眉入鬓、一行眼 | 柳叶眉（中段高、两端细）、眼多一行 texel 显大、外眼角上挑的睫线、虹膜更亮 |
| 口 | 三 texel 暗色 | 三 texel 偏暖的唇色，中间一 texel 略深 |
| 头发 | 束发戴冠、后披一板长发 | 入门：双丫髻或低马尾加发带；小成：高髻加步摇与垂鬟，后发更长到腰，发丝分两绺垂胸前 |
| 手 | 袖下露手 | 手更小一圈，袖口更长盖半手 |
| 步态 | 摆腿 26°、步幅大 | 摆腿 20°、步幅短、髋部有 1–2° 的左右摆、后发与披帛有次级摆动；站姿双手交叠于腹前（现在男修是垂手） |

## 4. 两套造型的设计稿

### 4.1 女·刚入门（`f_novice`）：素雅古风

- 意味：刚踏入门道的外门弟子，衣料是布不是绸，没有首饰。
- 服饰：齐腰襦裙。上襦交领右衽、窄袖到腕；下裙从腰到脚踝，一层素色裙、裙头一条布带打结垂两短带；外罩一件半臂（短袖对襟小衫）作为"层"；脚下布鞋。
- 色：月白上襦、青灰或藕荷下裙、半臂取一种低饱和的靛青或茶色；布带同裙色略深。全身只有一处点缀：发带（绛红或青）。
- 头发：双丫髻（两个小髻在头顶两侧）或低马尾一束垂背，前额开阔、鬓角到颅底，不留刘海（与脸的教训一致）。
- 纹理：布的经纬（`_weave` 调大一点）、袖口与裙摆的浅色窄边、裙面纵向褶（三到四条明暗交替）。

### 4.2 女·小有所成（`f_adept`）：华丽

- 意味：内门以上、筑基有成的女修；绸料、层次更多、有饰物，但不是花枝招展，"华丽"落在层数与饰件上。
- 服饰：齐胸襦裙或广袖长裙。内层立领中衣露一线；外层广袖大袖衫，袖深而垂（独立袖口 cube，再加一段垂袖）；裙从胸下到脚，两层裙摆（外层短内层长，形成层次）；腰间细带配玉佩与丝绦；一条**披帛**（独立骨骼的长带，从双臂后方垂到腿侧，idle 微漂、走路摆动）；绣花裙摆用规则纹样（回纹、云纹织带），不画自由形云朵（在此密度读成噪点）。
- 色：两套可选默认，owner 点一套：A 绛紫外衫 + 月白裙 + 金饰；B 青碧外衫 + 素白裙 + 银饰。披帛取与外衫互补的浅色。
- 头发：高髻（一个大髻在头顶偏后）+ 一支步摇（金或银，细杆加三 texel 坠）+ 两侧垂鬟（小环状发束）+ 后发垂至腰；额前仍开阔。
- 饰件：步摇、玉佩、耳坠各一个独立小 cube，坠物在 idle 里有 1 texel 幅度的摆。
- 纹理：绸的高光（`_form` 圆度高、高光带窄亮）、织金边、裙摆渐变（往下略深）、披帛半透明感用边缘浅色与中段深色模拟（alpha 仍只有 0/255）。

### 4.3 脸：两套各一张

- 共享"女性五官"规则（第 3 节），入门的更稚气（眼略大、眉略平、唇色淡），小成的更英气（眉稍扬、眼角上挑更明显、唇色稍深）。
- 先 2D 草图再建模：按 KB 39 的做法，`python3 -m tools.npcgen preview <look> --only face` 在游戏尺寸与放大两档看，脸比宽高、下颌、眉眼位置都用 texel 数写进定义，便于调。

## 5. 动画

- 走：各自的 `walk` 剪辑，`WALK_SWING 20`，髋骨 1–2° 侧摆，后发与披帛各一条次级旋转通道（相位滞后四分之一拍）。入门版没有披帛，只有发带与马尾摆。
- 站：`idle` 4 秒，双手交叠腹前的基础姿势，呼吸起伏 0.5 texel，披帛与坠物微漂。
- 不做：表情、战斗、坐姿。化身的 look-at 与随机环视照旧。

## 6. 工序与产出

1. `NpcEntity.DATA_LOOK` 同步字段、`NpcRenderer` 多外观读取、化身按性别与境界选外观、summon/刷怪蛋支持；契约与 `validate_custom_entities.py` 跟进。（Java 包，与美术并行）
2. `defs/cultivator_f_novice.py`、`defs/cultivator_f_adept.py` 与共享件抽取（男修文件不改输出：`build cultivator --check` 保持通过）。
3. gate 加 `npcgen_check("cultivator_f_novice")`、`npcgen_check("cultivator_f_adept")`。
4. 证据：`tools.npcgen preview` 的六视图、面部页、层次特写、走路 GIF；`combat_capture npc --look f_novice|f_adept` 的无头四面立绘、特写、步行视频，三套并排的一张对比页 `out/preview/cultivator/looks/index.html`。
5. 文档：KB 39 加 "Looks" 一节与女修两套的设计事实；README 的 NPC 节与 ledger（本轮全部 `not_verified`）；CHANGELOG。

## 7. 需要 owner 定的

1. 小成版配色选 A（绛紫金）还是 B（青碧银），或另给。
2. 入门版发型：双丫髻还是低马尾。
3. 化身按性别与境界自动换外观，是否就这么定（默认是）。
4. 男修要不要也分"入门/小成"两档（默认先不做，等女修定型后用同一机制加）。
5. 妖兽建模的具体对象：第二只妖兽叫什么、什么性格（现在只有妖狼），点名后另写计划。

## 8. 风险

- 密度限制：32 texel/格下，耳坠、步摇坠这类 1–2 texel 的饰件在远处读不出，只在近景成立；按"近景有、远景不乱"来取舍。
- 披帛的独立骨骼要在 `animateWalk` 之外摆动，男修没有这类部件，是动画管线第一次加次级运动通道。
- 一个实体类型多外观是新机制，`NpcAssetFilesTest` 与校验器都要能枚举多套文件。
