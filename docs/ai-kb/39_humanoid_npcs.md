# Humanoid NPCs

## 0.91.5 自动出生统一使用保留组合

普通新修士、刷怪蛋与命簿投影均使用 100 个男修／72 个女修组合。女性不再按境界切到
旧 `f_adept`；未知性别沿用男性展示语义，但选择正式男修组合。目录中 `female_novice` 与
外观 ID 的旧命名仅为兼容，不再限制可用境界。人物身份与选择盐不变，男女均不会因升境重抽。

`tools/npcgen/appearance_rules.json` 定义普通自动出生族权重，经 `catalog.py` 写入同一运行时目录；
保持原男性一份／女性两份的比例，所有分支只选组合池。`CultivatorAppearance` 在服务器入世时
覆盖未经过刷怪蛋 finalizeSpawn 的创建入口；已选款、显式 Look、从磁盘加载的实体不会重抽。
修士保存时总是写入 Look，包括旧 default，以保持后续跨维度复制与重进稳定。

旧三个 Look ID 继续支持已有存档和管理员显式指定。固定外观采集必须写明 `Look:"default"`；
省略 Look 的普通召唤现在用于自动组合。发色规则不变：命簿随肖像，普通实体未指定 Colours 时使用原配色。
局部原生入口为 Modal `npc-appearance-runtime`；覆盖命令召唤、真实右键刷怪蛋、旧 Look 兼容、
真实命簿高境界女修／男修、撤回重投影和保存重进。
0.91.5 的固定源、六项原生结果、历史失败及测试包见[入口统一验证记录](../testing/2026-10-26-npc-appearance-entrypoints.md)。

## 0.91.4-fix1 墨衣劲装裤型

B 套的 25 个组合使用用户已确认的加宽裤腿与收束靴筒，尺寸由 `male_variants.json`
的 `outfits.B.trousers.current` 定义；鞋底支撑几何、骨架与全部动画保持一致。
`reference` 配置只供旧原型回放，需显式 `reference_trousers=True`；正式生成默认使用新版。
新增候选 SHA 检查保留确认样稿的四份资源，旧 20 组原型检查仍然保留。其他衣装、女性与旧 Look 不变。

## 0.91.4 男修组合接入

`tools/npcgen/male_variants.json` 保存五张脸、五种发型与四套衣装，独立组合为 100 个
`m_<face>_<hair>_<outfit>` look。`male.py` 负责头部组合，`male_clothing.py` 保留已确认
A 青衫、B 墨衣、C 月白和旧版衣装；正式生成不依赖 `reports/`。原型同号脸／发型的
20 组、80 份资源由固定 SHA 回放检查保护；A/C 的正式衣片增加步幅联动及内藏裤装。

男女目录共同生成版本 2 的 `assets/myvillage/npc/cultivator_variants.json`，通过显式 `family`
分组；女性身份盐与原 72 成员不变，男性使用独立盐。男性命簿化身按 `id + birthDay` 稳定选择，
境界变化不重抽；未知性别仍为旧 `default`。刷怪蛋保留原三类比例，在男性类别内选择组合。
已有普通实体的 Look／Colours 原样读写，三个旧 ID 与旧模型均保留；男性命簿化身首次投影会换为新组合。

发色和瞳色仍由人物肖像驱动，不为每种颜色重复创建模型。普通召唤／刷怪蛋未设置 `Colours`
时沿用原配色。例：`/summon myvillage:cultivator ~ ~ ~ {Look:"m_02_04_b",Colours:66}`
为银发、水系瞳色；`Colours` 继续使用现有 `hairOrdinal << 4 | eyeOrdinal` 格式。

生成与检查仍用 `python3 -m tools.npcgen variants [--check]`，换色样本用 `goldens [--check]`。
Modal `npc-variants-runtime` 检查全部 175 个 Look 与四种 Colours 的保存重进，并采集四套
男修衣装的静止、行走画面；这些是开发者证据，不代表玩家视觉、手感或多人验收。
该 preset 的构建阶段保留启动必需的资源任务（包括阡镇场景），不能仅凭 NPC 单测通过就
跳过它们打包。采集启动使用同一构建的 `prebuilt_resources`，禁止在重进时重新生成资源；
缺少 mod 元数据、主类或阡镇场景清单时直接失败，不自动回退到全量生成。
固定源、最终原生采集、历史失败及测试包见[男修组合验证记录](../testing/2026-10-26-male-npc-variants.md)。

## 0.91.3 女修组合接入

0.91.3 的组合资源检查通过，但该测试包后来发现漏打包启动必需的阡镇场景清单；
请使用上文 0.91.4 测试包。历史 0.91.3 编译结果不代表游戏启动通过。

`tools/npcgen/female_variants.json` 是组合配置唯一来源；`female.py` 将保留的脸 01–03、
四组发型与配套刘海、六套衣装组合为 72 个完整 look。衣装包括原 01 靛蓝交领、02 月白青裙、
03 朱砂直领、05 藕紫对襟，以及 A 短打裤装／B 束袖长衣。脸 04/05 和旧衣 04 修补版不进入目录。
0.91.2 仅接入 A/B，遗漏了前四款；0.91.3 已按用户纠正补齐，原型回放逐份核对原预览 SHA。
A/B 的 `02 + A 发型` 原型回放与用户已确认产物有固定 SHA 回归。
正式版另加裤腰内藏搭接，并隐藏长衣内部不应穿出的裤腿纹素；支撑脚几何和动画不变。
三号脸的眼线高一格，其配套刘海按五官列裁短，防止遮住睫毛。

生成：`python3 -m tools.npcgen variants`；检查追加 `--check`。此命令同时写入
`assets/myvillage/npc/cultivator_variants.json`、全部组合资源和跨语言身份选择样本。
普通 `build <name>` / `preview <name>` 也支持完整组合名，例如 `cultivator_f_novice_02_a_pants`。
所有正式生成不依赖 `reports/`；每次构建隔离调色板，角色材质探针不会串色。

`CultivatorVariants` 在公共侧读取生成目录；`CultivatorEntity.LOOKS`、客户端模型层和资源加载
使用同一目录。旧 `default`、`f_novice`、`f_adept` 文件和 ID 均保留；男性与进阶女修定义不变。
命簿女修在炼气及以下按 `id + birthDay + selection_salt + look` 的 FNV-1a 64 rendezvous 选择
稳定组合；刷新、改名、迁居不会重抽。化身仍不存盘，不增加第二份人物权威；普通 NPC 使用原有
`Look` NBT，刷怪蛋先保持三类外观原比例，再在入门类别内选组合。已有旧 Look 的普通实体原样加载。
发色与瞳色仍由 `NpcColours` 和人物肖像系统控制：命簿化身同步人物配色；普通召唤／刷怪蛋
没有设置 `Colours` 时沿用贴图原配色，不自动随机发色。已有 `Colours` 存档字段继续保留。
新增目录成员时，部分命簿化身会切换到新款；同一目录版本内身份选择不变。已保存的普通 NPC Look 不改写。

离线组合页：`python -m tools.npcgen.variant_preview --out reports/female-npc-hair-colours --workers 4`
（使用装有 numpy／Pillow 的预览 Python；对外发布时用 `--asset-base` 指定 HTTPS 资源前缀）。
页面支持同衣装比较四种保留发色、同发色比较六套衣装，脸型／发型／角度均可切换。
墨蓝使用模型原调色板，不能与人物 `ink_blue` 调色板混称；栗棕／银灰／灰紫分别复用
`chestnut`／`silver`／`grey_ink_violet`。预览固定原瞳色以单独比较头发，逐组合检查
换色与 painter 重绘一致且所有非头发纹素不变。此工具不改变人物发色分配或原生验收状态。

召唤示例：`/summon myvillage:cultivator ~ ~ ~ {Look:"f_novice_02_a_pants"}`；长衣可用
`f_novice_02_a_robe`。局部云端入口：Modal `npc-variants-build` / `npc-variants-runtime`；
后者在独立世界检查目录全部 Look 的保存重进并采集 A/B 静止及行走，不宣称玩家输入或完整战斗验收。
0.91.3 补衣装使用生成／换色／姿态检查和 `npc-variants-build`，未重复原生生命周期采集。
本次结果、固定源、失败保留和测试包见[接入验证记录](../testing/2026-10-25-female-npc-variants.md)。
历史原型记录如下，不代表本轮新组合的当前验收结果。

## 入门女修外观原型分支（2026-10-25）

`feature/female-npc-skin-rebuild` 的 `f_novice` 是待审美确认的 MC 古风皮肤系候选：
方头与自然垂手、独立连续裙片沿用前一轮比例；本轮将设计样稿的薄发片、分束像素头发、
单行小眼、叠压交领及靛蓝／象牙白／朱红衣装落实到 npcgen。面部和头发的索引色在 painter
内读取调色板，角色材质图仍可识别全部换色纹素。几何与像素规则的唯一输入为
`tools/npcgen/defs/cultivator_f_novice.py`；model、animations、texture、roles 均由生成器输出。

当前证据仅为代表性离线渲染、生成一致性及局部资源／换色检查。玩家审美、原生游戏与完整动作未验证，
最终 golden 与旧女修造型断言尚未完整迁移；不能把局部通过称为全套测试通过。进阶女修和男性外观继续沿用原定义。
设计历史见 [女修造型计划](../female-cultivator-brief.md)；下文早期女性造型参数不约束本原型。

0.63.0 已接入持剑自卫、实际装备与命簿伤亡，当前行为见 [KB57](57_npc_sword_combat.md)。下文早期“只有身体／不可伤害”描述属于对应历史版本。

0.34.0 adds a framework for humanoid NPCs drawn from their own layered model,
and its first NPC, `myvillage:cultivator` (修仙者): a sect disciple in a pale
robe under a long sleeveless indigo vest. The cultivator has no disposition
yet. It stands, strolls, and looks at players; whether it becomes a friend or a
foe is a later decision, and this version is built so either can be added
without redoing the art. The normative rules are in
[humanoid-npc-runtime](../contracts/combat-entities.md#humanoid-npc-runtime); the
Entity Contract is `genops/contracts/entities/cultivator.yaml`.

No natural spawning and no drop: `/summon` and
`myvillage:cultivator_spawn_egg` only. Since 0.35.0 the world simulation also
projects ledger members as never-saved, invulnerable cultivator avatars on a
built sect compound while a player is near. Since 0.47.0 the same avatar manager
also projects eligible local rogue ledger people in wilderness regions; it does
not create independent naturally spawned NPC identities or add hostility. See
[40_world_sim.md](40_world_sim.md) and
[47_realm_stats_wilderness.md](47_realm_stats_wilderness.md).

## Where each fact lives

| Fact | File | Owner |
|---|---|---|
| Model, clips, texture | `assets/myvillage/npc/<name>_model.json`, `<name>_animations.json`, `textures/entity/<name>/<name>.png`; other looks `<name>_<look>` (see Looks) | generated by `tools/npcgen/defs/<name>.py` (`<name>_<look>.py` per look) |
| Looks an entity wears; avatar look rule | `CultivatorEntity.LOOKS`; `entity/npc/CultivatorLooks.java` | Java |
| Body behaviour (goals, no despawn, no leash) | `entity/npc/NpcEntity.java` | Java |
| NPC id, attributes, stroll speed | `entity/npc/CultivatorEntity.java` | Java (placeholder body values until the disposition is designed) |
| Hitbox, eye height, mob category, tracking range | `entity/ModEntities.java` | Java |
| Identity, intent, what is verified | `genops/contracts/entities/<name>.yaml` | contract (names files, repeats no generated value) |

Bone and cube counts, the atlas size, the model scale, and every keyframe are
generated; docs and the contract point at the files rather than copying them.

## How the look is built

The figure does not use the player model or the skin format. Three things make
it read as layered cloth instead of a painted box:

1. **Half-size units.** The model file carries `scale: 0.5`: one model unit is
   1/32 block, so both the geometry and the texture are twice as fine as a
   vanilla mob's. A band can stand half a vanilla pixel proud of the cloth under
   it, and a face has room for a brow, an eye with white either side of the
   iris, a jaw that narrows in real steps, and a one-texel nose.
   `scale` is an optional field of the shared model schema (1 when absent); the
   beast renderer honours it too, and the wolf's file is unchanged.
2. **A cube per layer.** The crossed robe collar is two rotated bands on the
   chest (left lapel over right); the vest is a shell over the torso with
   border bands down its opening and sloped caps over the sleeve heads; the
   belt wraps over both, with a buckle, two sash ends, and a jade pendant on
   their own bones; each sleeve leaves the cap, deepens toward an open cuff,
   and ends in a hanging drape with the hand below it; each leg carries half of
   a two-tier skirt, with the vest's panels hanging over it front and back; the
   head is a cranium with three jaw cubes under it (`jaw`, `jaw_low`, `chin`)
   and a nose; the hair is a shell around the head with a stepped-in top, a
   bun, a crown, a pin, two ribbons, two loose strands, and a slab of long hair
   down the back.
3. **Cut-outs and painted depth.** NPC textures are drawn with the entity
   cut-out render type, so a texel with zero alpha in a shell is a hole: the
   vest is open down the front onto the collar bands, and the hair shell is cut
   away round the face and ear. The painter then colours every texel from where
   it sits in 3D (`tools/npcgen/shade.py`): each box is lit as if it were round,
   a layer that sticks out above casts a short shadow on the one under it, the
   crevice beside a raised band darkens, and cloth gets folds, a dye gradient
   toward cuffs and hem, woven bands (袖襕, 膝襕), and trim. Alpha is always 0
   or 255; nothing is blended.

Entity faces are drawn at 50 to 74 % brightness unless they face up, so base
tones sit high in their ramps and up-facing ledges are painted darker than the
cloth beside them.

## Faces

Three rounds of owner feedback shaped the face. 0.34.0 was rejected as a
Buddha's face without a cultivator's handsome spirit (俊朗英气); 0.34.1
rebuilt it realistically (tapering jaw, open forehead, brow on a one-row eye,
no mark). On 2026-10-07 the owner found that still ugly and asked for 二次元
faces; a first anime draft (thin brow a row above a big eye with a
highlight, a dot nose, an M fringe) was rejected from the offline previews
the same day ("不行还是不好看，你去照着仿照一些皮肤看看"), so 0.44.1 copies
real anime-style skins instead. What holds now:

- **The study.** 119 skins tagged anime/kawaii/genshin/chibi/vtuber were
  pulled from the MineSkin index (`api.mineskin.org/get/list/<page>?filter=`,
  textures at `textures.minecraft.net`) and their 8 x 8 faces with the hat
  layer laid over them were put on one sheet
  (`out/preview/cultivator/anime_refs/index.html`, sources in `index.json`).
  Nearly all of them do the same five things: the bangs (hat layer) cover
  the top half of the face in strands of two or three tones with pointed
  tips; each eye is two pixels wide in the lower half, the inner pixel an
  iris that shades dark at the top to light at the bottom and the outer
  pixel white, with a dark lash row on top (often only its inner pixel one
  row higher, a slant); there is no brow (under the bangs) and no nose; the
  mouth is one or two pink pixels or absent; a pink blush pixel sits under
  the eye's outer corner; the skin is a very pale, flat peach.
- **The texel map (0.44.1).** Rows `r` from the cranium's top, columns `a`
  from the centre, scaled from 8 x 8 to the 13 x 12 face: the fringe
  (`_hairline(a)`) covers r0..r4 with tips to r5 on the odd columns (the
  adept parted in the middle: a 0 to r3, a 1/3/5 to r4, a 2/4 to r5), and
  the `hair()` NORTH branch stripes its columns
  (`(1.1, -0.3, 0.7, -0.5, 1.0, -0.1)[a]`, lighter at the tips); r5 a 2 the
  lash's rising inner corner; r6 a 2..4 the lash line (`LASH`), a 5 its
  tail; r7..r9 a 2..3 the iris (`IRIS_TOP`, `IRIS`, `IRIS_LOW`) and a 4
  white (`EYE_WHITE`) on r7..r8; a blush under the outer corner (male r9
  a 5; the women r9 a 4, since their jaw row has no a 5); r10 a 0..1 a small
  mouth mixed into the skin (the adept's rouge is `LIP_DEEP`); every other
  texel plain skin at base tone 5.5 on a pale ramp. No nose cube and no jaw
  cubes on any look: the head is one cube. Eye colours: the male blue (`#1E3F8F` to `#8FD0F5`), the novice warm
  amber, the adept violet; the gradient must stay bright enough to read as
  a spot of colour at 70 pixels wide.
- **Why the earlier faces failed.** Realistic proportions (small eyes high
  on the face, heavy brows, an open forehead) read as crude at 13 x 12
  texels; the first anime draft kept the eyes in the middle of the face with
  white on both sides, a brow and a nose, which is not what the skins do.
  Big low eyes, a covered forehead and almost nothing else is the style.
- **One cube, no taper.** 0.34.1's stepped jaw (three cubes narrowing to
  the chin, "taper in geometry") was kept under the first skin-style face
  and the owner found the face shape uncanny on their PC ("脸型有点诡异了"):
  with the eyes low on the face the steps sat right under them and read as
  a wedge. The skins' heads are plain cubes, so the head is now one cube
  from chin to crown and the face shape comes from the fringe and the side
  hair of the shell, which runs down to the chin (`_hairline(a >= 6)` is the
  chin height, `_hair_on_side` keeps the front strip). The skull's underside
  is skin in front of z 1.5 and hair behind (the `skull()` override).
- **No forehead mark.** A dot between the brows reads as an urna.
- **Undersides belong to what is above them.** The cranium's underside beside
  the jaw is painted as hair, since it sits under the sideburn, and the jaw's
  steps are a light skin tone underneath; painted as shadowed skin they drew
  brown patches round the lower face from below.
- **Tests.** `tools/tests/test_npcgen.py` pins this face (gradient eyes
  with the white outside, the fringe's rows and stripes, the small mouth,
  no brow, mark or nose); change the tests with the face.

Judge a face at in-game size: `python3 -m tools.npcgen preview <npc> --only
face` draws the head large from six angles and again at about 70 pixels wide.
NPC previews draw without back-face culling, as the game does
(`entityCutoutNoCull`), so the inside of the hair shell shows through its
cut-outs.

## 0.91.1 女修外观调整

本节覆盖上文 0.44.1 对女性五官和裙摆的旧参数描述；男性外观沿用原定义。
两套女性外观保留单方块头和原配色：睫毛位于 r5，虹膜位于 r6–8，内侧深色瞳仁与
外侧单格高光打破整行渐变，嘴位于 r9，下巴留两行。刘海改成成组的明暗。
瞳仁保持底图深色，高光沿用 role map 的虹膜白混合，人物换瞳色后仍保留这两个细节。

入门女修收窄肩距、降低肩线和双手，双手的穿插检测要求为零；裙摆宽度为
14/15/16，前侧每次仅外扩半个模型单位，前后深度台阶缩小，保留内藏重叠和步态反摆。
进阶女修收拢上臂，上下裙片重叠以消除原来的开放缝隙，并减小下裙的外扩。
共享骨架、碰撞、Look 存档名和持剑动作适配契约保持兼容。

检查选择两套生成器与重着色 golden 一致性、女修/角色材质/战斗资源测试，以及
`NpcAssetFilesTest`、`NpcSkinComposerTest`。初步云端验收使用用户指定的 Colab，
采集两套外观的静止多视角、脸部近景和原生行走视频；这些是开发者证据，
玩家审美、手感、音效、FPS 与多人体验仍由玩家验收。

## Runtime

`NpcEntity extends PathfinderMob`. Goals: float, random stroll at the
subclass's speed modifier, look at a player within eight blocks, look around.
There is no target goal, attack, or trade. It does not despawn by distance and
cannot be leashed; as a non-monster it stays in Peaceful. Synced fields: the
ledger person of an avatar (`DATA_LEDGER_PERSON`, 0.35.0), the look
(`DATA_LOOK`, saved as `Look`, 0.41.0), and an avatar's dialogue role
(`DATA_LEDGER_ROLE`: `none`, `steward`, `elder`; never saved, 0.41.0). Only a
world-sim avatar with a role talks: it opens the sect dialogue
([43_player_sect_entry.md](43_player_sect_entry.md)); every other NPC does
nothing when used.

`CultivatorEntity` supplies the id and plain attributes. They are written in
Java because they describe only a placeholder body; when the disposition is
designed they move with it (a hostile cultivator would take its numbers from a
data file as beasts do).

## Client

`NpcRenderer` serves any NPC: from the id `ns:name` and each look it reads the three files
with the beast parsers (`BeastModelFile`, `BeastAnimationFile`), checks that
every channel names a model bone and that `idle` and `walk` exist and loop,
applies the model's scale, and logs the walk rate. `NpcModel` resets the pose,
turns the model's look bone by the clamped head yaw and pitch, plays `idle`
from the entity's first client tick, and drives `walk` through `animateWalk`.

The walk rate comes from the clip: `BeastGait.plantedFootSpeed` measures how
fast the planted foot moves back (scaled by the model's scale), and the rate
`5 / speed` makes the clip advance exactly that far per block walked. The
cultivator's walk is authored for it: the leg angle follows a triangle wave
through an arcsine so the planted foot travels at an even speed, the hips ride
on that foot, the other foot lifts as it swings, and the vest panels, sash,
and pendant turn with the leg under them.

`MyVillageClient` (client dist only) registers the renderer and a layer per look; no
file under `entity/**` imports client code. The layer definition reads the
model file on every resource reload, so `F3+T` is meant to pick up regenerated
files.

## Art generator

`tools/npcgen` (standard library; previews use numpy and Pillow) owns the three
art files; never hand-edit them. It shares `tools/beastgen`'s cuboid, painter,
and clip modules. Since 0.41.0 the parts that do not depend on one set of
clothes (box and mirror helpers, paint maths such as `_form`, `_weave`,
`_fret`, the gait functions `_leg_angle` and `_sole_low`, and the
`HumanoidPaint` base with the skin materials) live in
`tools/npcgen/humanoid.py`, shared by every cultivator look; each definition
keeps its own garments, face, hair, palette, and pose tables.
`tools/npcgen/README.md` has the modules.

```bash
python3 -m tools.npcgen build cultivator            # write the three files
python3 -m tools.npcgen build cultivator --check    # fail when they differ (a release-gate step)
python3 -m tools.npcgen preview cultivator          # out/preview/cultivator/
```

`preview` writes a six-view turnaround, close-ups of the layered parts, a face
sheet (`--only face`), a scale image beside the vanilla player, the atlas,
walk and idle sheets, a turntable GIF, walk GIFs, and `index.html`. It lights
the model with the game's two fixed entity lights, so it is darker than a
daylight screenshot, and draws back faces as `entityCutoutNoCull` does.

## In-game capture

```bash
python3 -m tools.combat_capture npc                 # out/preview/cultivator/ingame/
python3 -m tools.combat_capture npc --parts idle    # stills only
```

`idle` takes full-figure stills from four sides with the world frozen,
close-ups (face, head, collar, belt, sleeve, hem, back panel), and a scale
still beside the player. `walk` films two NPCs strolling in a barrier pen from
the front and from the end. It is developer evidence, never an owner verdict.

## Looks

0.41.0: one entity type can wear several looks, each a full set of model,
clips, and texture. The cultivator has three (`CultivatorEntity.LOOKS`):
`default` (the male disciple above), `f_novice` (女·刚入门), and `f_adept`
(女·小有所成). Design: `docs/female-cultivator-brief.md`; the defaults the
owner may overturn (palette A, the low ponytail): `docs/female-cultivator-tasks.md`.

- **Mechanism.** `NpcEntity.DATA_LOOK` (string, synced, default `default`);
  `look()` / `setLook()`; saved as `Look` only when not the default, and a
  saved name the type does not list in `looks()` is ignored
  (`/summon myvillage:cultivator ~ ~ ~ {Look:"f_adept"}`). A spawn egg draws
  one of `looks()` at random, the default included (`finalizeSpawn`).
  `NpcRenderer.register(event, type, looks)` loads a model, layer, texture,
  scale, shadow, and walk rate per look and, per frame, swaps
  `LivingEntityRenderer.model` to the entity's synced look before vanilla
  renders; an unknown look draws the default and warns once per name.
  `registerLayer(event, id, looks)` registers `<id>#main` for the default and
  `<id>#<look>` for each other look. A missing or invalid file of any
  registered look is a startup or reload error.
- **Files.** The default look keeps `npc/<name>_model.json`,
  `npc/<name>_animations.json`, `textures/entity/<name>/<name>.png`; any
  other look is `npc/<name>_<look>_model.json`,
  `npc/<name>_<look>_animations.json`,
  `textures/entity/<name>/<name>_<look>.png` (every texture in the entity's
  own directory).
- **Skeleton.** A look keeps the body bones the runtime relies on (`root`,
  `body`, `head`, `arm_*`, `forearm_*`, `leg_*`, `hair_back`), the look bone
  `head`, the model `scale`, and the hitbox (registered once per entity
  type). It may add bones (a ponytail ribbon, skirt ties, a drape, a hairpin
  pendant), never drop one, so `NpcModel`, `animateWalk`, name tags, and
  avatar cells need no change.
- **Generator.** One `tools/npcgen/defs/<entity>_<look>.py` per look, with
  `ENTITY` (the texture directory), `NAME` (file prefix), and `LOOK`; its name
  goes in `build.DEFINITIONS` and gets an `npcgen_check` step in
  `tools/release_gate.py`. Garment-independent parts come from
  `tools/npcgen/humanoid.py`.
- **Avatars.** `CultivatorLooks.forPerson(person, realmOrder)` (pure): gender
  `f` at or below `qi_refining` in the ledger's realm order is `f_novice`,
  above it `f_adept`; anyone else `default`. `WorldSimAvatars` sets it at
  spawn.
- **Contract and validation.** `genops/contracts/entities/cultivator.yaml`
  has a `looks:` list (id, model, animations, texture, definition,
  `model_layer`) and `look` in `state.synced`.
  `tools/validate_custom_entities.py` groups the npcgen definitions by
  `ENTITY` and `LOOK`, and checks for every look the three files, the model
  schema, look bone, the body bones above, the default look's scale, the
  clips, the texture size and binary alpha; the contract's list against the
  definitions and the Java `LOOKS`; and one renderer and one layer
  registration per look in `MyVillageClient`. `NpcAssetFilesTest` runs over
  every look.
- **Capture.** `python3 -m tools.combat_capture npc --look <look>` summons
  with `{Look:"<look>"}` and writes `out/preview/cultivator/ingame_<look>/`
  (the default still writes `ingame/`). Both female looks were captured on
  2026-10-07: four-side stills, eight close-ups, a scale still, and the two
  walk videos each in `ingame_f_novice/` and `ingame_f_adept/`; the
  `idle_q_front` still showed the look drawn from the `Look` tag.
- **Comparison page.** `python3 tools/npc_looks_page.py` writes
  `out/preview/cultivator/looks/index.html`: one column per look, rows of the
  offline turnaround, face sheet, close-ups, and walk GIFs
  (`out/preview/<definition>/`) and the headless stills, close-ups, and walk
  videos; a file not captured yet shows as 未采集. On 2026-10-07 all 21 cells
  had content.

## A second NPC, a variant, a disposition

A second NPC is an `NpcEntity` subclass, its registration (`EntityType`,
attributes, spawn egg and item model, names in both languages, loot table,
`NpcRenderer.register` and `registerLayer`), a `tools/npcgen/defs/<name>.py`
with its name in `build.DEFINITIONS`, an `npcgen_check("<name>")` step in
`tools/release_gate.py`, and a contract with `kind: npc`. The validator reads
every definition in `DEFINITIONS`, groups them by their `ENTITY` and `LOOK`,
and checks each entity with all its looks.

A recolour of the cultivator (another sect, a demonic cultivator) is a palette
change: the painter takes every colour from the ramps at the top of the
definition. A variant on the same entity type is a new look ("Looks" above):
a `defs/<entity>_<look>.py` with the same `ENTITY`, its three files, an entry
in the contract's `looks:` and the Java `LOOKS`, and a gate step.

When the disposition is decided:

- **Foe.** The art files use the beast schemas, so a hostile cultivator can be
  a `BeastEntity` with move clips added to the same definition and its files
  written under `assets/myvillage/beast/`; or `NpcEntity` can grow its own
  combat. The model has shoulder, elbow, and hip joints and no knees or hand
  attachment yet; a weapon in the hand needs an attachment point on the
  forearm bone.
- **Friend.** Interaction (dialogue, trade, teaching a technique) goes on an
  `NpcEntity` subclass; the body, clips, and renderer stay as they are.

## Validation

```bash
python3 tools/validate_custom_entities.py
python3 -m tools.npcgen build cultivator --check
python3 -m tools.npcgen build cultivator_f_novice --check
python3 -m tools.npcgen build cultivator_f_adept --check
/usr/bin/python3 -m unittest tools.tests.test_validate_custom_entities tools.tests.test_npcgen tools.tests.test_npc_capture tools.tests.test_npc_looks_page
./gradlew test    # NpcAssetFilesTest (every look), CultivatorLooksTest, BeastAssetFilesTest
./gradlew build
```

The validator checks, for every NPC `tools/npcgen` builds: the model (bones
parents first, look bone, UVs inside the texture, a positive scale when
present) and clips (channels on real bones, `idle` and `walk` looping); a
texture whose size equals the model's atlas and whose alpha is binary; the
generator definition; names in both languages; the spawn-egg model and
colours; the loot table; the Java registration against the contract (size, eye
height, tracking range, category); renderer registration in the client class;
no client import under `entity/**`; and no spawn placement, biome modifier, or
spawn biome tag. `tools/tests/test_npcgen.py` adds the art rules: layers stack
outward on the chest, only declared shells are cut out, large cloth faces
carry several tones, the face is symmetric with its features on the expected
texels, the jaw tapers in steps under the cranium, the planted foot stays down
and travels evenly, and panels follow the leg.

## Verified and not verified

Automated checks, offline previews (`out/preview/cultivator/`), and the
headless capture (`out/preview/cultivator/ingame/`) are implementation
evidence. The capture showed the model at the intended size beside the player,
the cut-outs and layers drawing as in the preview, and NPCs strolling with the
walk clip. The owner observed 0.34.0 on their own PC on 2026-10-06 and
accepted the clothes, the back view, the poses seen from behind, and the walk
(`pass`), and rejected the face (`fail`); on 2026-10-07 they found the 0.34.1
and 0.41.0 faces still ugly, so 0.44.1 redrew them after real anime skins (unverified). The 0.34.1 face, and everything the
owner did not speak to (`F3+T` reload, save and reload, multiplayer, frame
rate, night and indoor lighting, hurt and death), are `not_verified`. The
README ledger "Cultivator NPC real-client acceptance surface" records each
surface.

The two female looks (0.41.0) were judged only from offline previews and
headless captures (`out/preview/cultivator/ingame_f_novice/`,
`ingame_f_adept/`, the comparison page `looks/index.html`); the owner's PC
was not available. `f_novice` and
`f_adept` look, face, walk and idle, name tag, the random spawn-egg look, and
the avatars' look by gender and realm are `not_verified` (README ledger
"Looks (0.41.0)").

## See Also

- Runtime spec: [`humanoid-npc-runtime`](../contracts/combat-entities.md#humanoid-npc-runtime)
- Shared model and animation schemas, beasts: [38_hostile_beasts.md](38_hostile_beasts.md), [`hostile-beast-runtime`](../contracts/combat-entities.md#hostile-beast-runtime)
- Custom entities and the fox route: [26_custom_entities.md](26_custom_entities.md), [`custom-entity-runtime`](../contracts/combat-entities.md#custom-entity-runtime)
- Capture tooling: [34_combat_data_and_capture.md](34_combat_data_and_capture.md), [`combat-capture-tooling`](../contracts/tooling.md#combat-capture-tooling)
- Knowledge-base index: [INDEX.md](INDEX.md)
