# Changelog

All notable project changes should be recorded here when a version is prepared.

## Versioning Rules

The authoritative version-bump rule (increments and the files that must move
together) lives in `openspec/config.yaml` (`rules.tasks`). Follow it there.

## 0.40.0

## 0.39.1

The Xuantie Gauntlet after the owner's first look (2026-10-07: the moves are
fine; the model and icon are ugly, it reads as a big ski glove, and one item
should be a pair with the left hand wearing one too). Move data, hit samples,
timing and the pose clips are unchanged.

### Changed

- `tools/gen_xuantie_gauntlet_model.py`: a slimmer articulated iron fist in
  place of the 42-element mitten. 26 elements: a cuff flush with the glove and
  a 0.25 px rim (no flare), a thin bronze trim, the glove 0.1 px over the
  skin's outer layer, three overlapping back lames no thicker than 0.5 px with
  a low ridge, four separate finger plates (0.6 px gaps) each with a curl down
  the palm side and a raised knuckle cap on the back, the thumb folded across
  the curls, and a plain leather palm. 12.5 x 11.0 x 10.25 model px against
  16.5 x 12.8 x 11.9 (24 % shorter, 14 % slimmer both ways; 6.25 x 5.5 x 5.1
  player px in third person). Still 2 model px per player px, `rig.weapon_scale`
  0.3 and the third-person display unchanged, grip error 0. The texture gives
  every visible face its own region at two texels per model px (no stretched
  texels) with a bled gutter, so thin faces never sample a transparent edge.
- The contract follows the new parts: butt `y 1..4.4` (cuff), handle
  `4.4..11.4`, collar `11.4..12.6` (the knuckle ridge), head `12.6..13.5`
  (finger plates and knuckle tops), trail `11.4..13.5`.
- Inventory icon: a hand-authored three-quarter (斜着) fist turned 26.57
  degrees, knuckles up and right, cuff down and left, lit from the upper left,
  in place of the upright front view.

### Added

- Weapon files take an optional boolean `paired` (`CombatDataLoader`,
  `WeaponDefinition.paired`, `tools/combat_data.py`); the gauntlet sets it.
  The validator (`COMBAT_WEAPON_PAIRED`) requires a paired weapon's rig to keep
  `rig.off_hand.free` and its contract to have no `off_hand_grip_center`.
- A paired weapon is drawn a second time, mirrored, on the empty off hand
  (presentation only, resolved through `CombatStyles`): in third person by
  `PairedWeaponLayer` (a player render layer that places it like vanilla's
  off-hand item, so the PAL left arm carries it through the guard, chamber and
  strike roles), in first person over the free off hand's fist
  (`FirstPersonWeaponTransform.pairedItem`, scaled by the off arm's
  thickness). An item in the off-hand slot hides it. `PairedWeapons` reverses
  each quad's vertices under a mirroring pose so faces stay outward.
- Offline previews show the pair: `fp` (port `paired_item_matrix`, checked
  against the golden's new `paired_item`), `pose` (`--paired auto|on|off`) and
  `model` (`tp_pair`). `FirstPersonPairedHandTest` pins the first-person
  placement; the parity golden is rewritten (sword and spear entries
  unchanged).

## 0.39.0

The 拳掌 (fist) school gets its weapon: the Xuantie Gauntlet (玄铁拳套), worn
on the main hand, with its own five-move style and a pose set where both hands
work. Built along the new-weapon playbook (`docs/ai-kb/36_new_combat_weapon.md`,
"Third weapon").

### Added

- Item `myvillage:xuantie_gauntlet`: `CombatWeaponItem` on `Tiers.DIAMOND`,
  `createAttributes(DIAMOND, 1, -1.8F)` (tooltip 5 damage, 2.2 speed), in
  `myvillage:main` after the Lingxiao Spear and in `#minecraft:swords`; names
  玄铁拳套 / Xuantie Gauntlet; item contract
  `genops/contracts/items/xuantie_gauntlet.json`.
- `tools/gen_xuantie_gauntlet_model.py`: the 3D gauntlet (42 elements: flared
  cuff with a bronze 回 band and rivets, wrist strap, back lames and plate with
  a ridge and engraved 云纹, finger-edge lames, stitched leather palm, the thumb
  across the curled fingers, knuckle bar and four studs), a 128x128 texture
  painted per texel (form light, bevels, chipped edges, cold sheen on dark
  iron), a 32-grid pixel-art icon, the `separate_transforms` wrapper whose
  third-person display puts the gauntlet on the fist, and the format 2
  contract (cuff = butt, hand = handle with the grip at the fist centre,
  knuckle bar = collar, studs = head, trail from the knuckle line).
- Style `myvillage:basic_fist`: 冲拳 straight punch, 横掌 horizontal palm
  (right to left), 上勾 uppercut, 劈掌 chopping palm, 踏步双撞 step-in double
  strike; 9 to 11 ticks, two-tick active windows, range 1.8 to 2.2, one or two
  targets, chain `6/7/7/8`, combo timeout 10, minimum intent interval 1; the
  finisher steps 0.9 block on tick 5 and is the only heavy hit. Hit-stops
  1.0 to 2.0 ticks (the finisher 1.5): captures showed 3- and 2-tick stops on
  the 11-tick finisher dropped in first person (`ignored_too_late_to_catch_up`,
  the hit is confirmed about 7.1 ticks in), so a test now requires every
  shipped move's stop to fit when confirmed on its last active tick.
- Third-person poses `player_animations/fist_combat.json` from the new
  `BASIC_FIST` table: a 三体式-like left-lead guard, a 抱拳礼 mode entry, and
  five strikes with hip turn and stance change; the free left hand pulls back
  to the waist on the punch and chop, guards on the palm and uppercut, and
  strikes level with the right on the step-in.
- First-person rig `combat/xuantie_gauntlet_first_person.json` with a bare
  left guard hand that counter-moves.
- Weapon files take an optional `family` (`sword`, `spear`, `fist` set on the
  three weapons), validated against the schools' `weapon_family`
  (`COMBAT_WEAPON_FAMILY`); no runtime reads it yet.
- First-person rig fields: `rig.off_hand.free` (a bare off hand, no
  `off_hand_grip_center` needed), per-key `off_hand_rest` / `off_hand_reach`
  (the released or free hand's rest, defaulting to the block's), and
  `rig.arm.grip_diagonal` up to 90 (a weapon worn along the hand). Java,
  `tools/combat_preview` port, parity golden and validator updated together.
- Pose generator: `worn` and `free_off_hand` tables, off-hand roles
  (`chamber`, `guard`, `strike`), the free-hand guard and role rules, and
  `arm_body_clearance_px` (rigid arms kept out of the torso and head at and
  between keys).
- Tests: `BasicFistStyleTest`, `FirstPersonFreeOffHandTest`,
  `tools/tests/test_gen_xuantie_gauntlet_model.py`, fist table and rule tests in
  `test_gen_sword_pal_anims`, the fist baseline in
  `test_combat_style_baseline`, family and free-hand validator tests.
- Release gate step `gen-xuantie-gauntlet-model-check`.

### Changed

- `tools/validate_mod_items.py` pins the gauntlet; the creative-order message
  reads `...->lingxiao->xuantie->spirit_stone`.

### Not verified

- Every look and feel item (model, icon, poses, first-person rig, trails,
  sound pitch), real keyboard play, multiplayer, and the owner's verdict; see
  the README "Xuantie Gauntlet (0.39.0)" ledger.

## 0.38.0

Technique manuals (秘籍) and studying them (研读): a technique is now learned
by reading its manual, an item whose technique is a data component like an
enchanted book's enchantment, in a sitting session that can be interrupted,
keeps its progress on the book, and passes gates that cost stability. Brought
forward from the technique system's third phase. Brief:
`docs/technique-manual-brief.md`; how it works:
`docs/ai-kb/41_technique_system.md` ("Manuals and study").

### Added

- 16 items `myvillage:manual_<core|active|movement|body>_<huang|xuan|di|tian>`
  (`TechniqueManualItem`, stack 1, rarity common/uncommon/rare/epic by grade,
  天阶 foil) and two synced data components, `myvillage:technique` (the
  technique taught) and `myvillage:comprehension` (points read). A manual is
  valid when its technique is registered and of the item's category and grade
  (`TechniqueManualItem.check`); without the component it is blank
  (`空白秘籍 · 绝技 · 玄阶`), any other mismatch makes a red `残损秘籍` that
  cannot be read. Valid manuals are named `《功法名》` (`Manual: …`). Tooltip:
  category · grade, school, elements, heritage position, requirement,
  `参悟 n%`, `右键盘坐研读`. The `myvillage:main` tab lists the 16 blank
  manuals, then one per technique of grade 1..4 (129).
- Item art from the new `tools/gen_manual_textures.py` (standard library):
  a detail layer and a tint mask for each of the four categories (线装书,
  卷轴, 折页, 玉简), one shared model per category, tinted by grade in
  `MyVillageClient`; release-gate step `gen-manual-textures-check`.
- `/myvillage cultivation|xiulian manual|miji <target> <technique_id>` gives
  the manual of a technique (grade 0 and unknown ids refused); the vanilla
  form `/give @s myvillage:manual_active_xuan[myvillage:technique="myvillage:gengjin_jianjue"]`
  works as well.
- Optional technique field `study` (`TechniqueStudy`: `points`, `gates`,
  `gate_stability_cost`; default 4000/0/0), written for every generated
  technique from the new `study_by_grade` table in
  `tools/technique_catalogue/rules.json`: 黄 4000/0/0, 玄 12000/1/50,
  地 36000/2/100, 天 96000/3/150 (the `gen-technique-catalogue-check` step
  covers the new blocks).
- Study (研读), `MeditationMode.STUDY`: right-clicking a manual
  (`ManualStudy.use`) checks a valid manual, the technique not learned, an
  awakened root and its requirements, the previous technique of its chain,
  then meditation's eligibility and physical checks, and starts a session
  with meditation's 40-tick preparation and anchor that remembers the slot.
  Every 10 ticks it adds `affinity × (10000 + element bonus bp) / 10000`
  points (no grade multiplier; the bonus is `element_match_bonus` from
  `world_sim/rules.json`), writes them to the manual after the profile
  commit, pays a gate's stability once when crossing it or stops on it, and
  at the total learns the technique and consumes the manual. No randomness.
  Interruptions are meditation's; the points stay on the manual. At
  affinity 10 the grades take 3 min 20 s, 10, 30, and 80 minutes.
- Stop reasons `STUDY_ACCEPTED`, `MANUAL_LOST`, `STUDY_GATE`,
  `STUDY_COMPLETE`, `STUDY_REQUIREMENTS`; chat lines
  `message.myvillage.cultivation.study.*`.
- `MeditationStatus.study()` (`StudyProgress`: technique, points, total,
  next gate or -1, gate cost), present only while a study session runs.
- 修炼 page study card: `研读《功法名》` with the percent as its chip, a
  comprehension bar, `下一关 n 点 · 耗稳定度 m` (amber when stability is
  short; `无关卡` when none is left), and `按 X 停止`; it replaces the normal
  and spirit cards during the session, at the top of the readout column (above
  the figure when narrow). The session text reads 准备研读 / 研读中.
- Tests: `TechniqueManualItemTest`, `TechniqueStudyDefinitionTest`,
  `StudyStartTest`, `StudySettlementTest`, `StudyStepTest`,
  `MeditationStatusStudyTest`, more cases in `MeditationSessionTest`,
  `MeditationStatusPayloadTest`, `CultivationCommandsTest`, and
  `PanelReadoutsTest`; `tools/tests/test_gen_manual_textures.py` and more
  cases in `test_gen_technique_catalogue.py`.

### Changed

- Payload protocol is now `11` (was `10`): the meditation status carries the
  study progress, so client and server need the same jar.
- Study states reuse `PREPARING_NORMAL` and `MEDITATING_NORMAL`; client input
  is unchanged (four meditation intents; stop ends a study). Right-clicking a
  manual during any session ends that session first.
- Docs: KB note 41 ("Manuals and study"), notes 22, 28, 30, 37 and the index,
  README (command, the "Technique Manuals (0.38.0)" section, the 0.38.0
  ledger), AGENTS.

### Deferred

- A visual of the book while reading (held book, floating pages), mastery
  tiers (a chain asks only that the previous technique is learned), and
  manuals from loot, sects, the scripture hall, or NPCs.
- 地 and 天 manuals need Foundation early, which play cannot reach yet.

### Verification

- Automated: `gen_technique_catalogue.py --check`, `gen_manual_textures.py
  --check`, the six cultivation validators, their Python tests, and the Gradle
  tests.
- Not verified: everything in a real client (icons and tints, tooltip,
  creative tab, right-click start without a swing, interruption keeping
  progress, the gate stop message, completion learning and consuming, the
  study card at GUI 480x270, 427x240, and 320x240, `X` stopping a study, the
  `/give` component form). See the README ledger "Technique manuals (0.38.0)".

## 0.37.0

The technique system (功法 / 流派 / 传承), first phase: one technique
catalogue now feeds both the player's technique registry and the world ledger
(命簿); schools and heritages are datapack registries; the running core
technique (心法) scales meditation progress and can be switched; the H panel's
功法 page groups techniques; ledger sects can hold a heritage that is lost
with them and found again. Design: `docs/technique-system-brief.md`; how it
works: `docs/ai-kb/41_technique_system.md`.

### Added

- Technique catalogue: source tables `tools/technique_catalogue/`
  (`catalogue.json`, `schools.json`, `heritages.json`, `rules.json`) and the
  generator `tools/gen_technique_catalogue.py`, which owns
  `data/myvillage/myvillage/technique/*.json` (all but the hand-written
  `basic_breathing.json`), `school/*.json`, `heritage/*.json`,
  `world_sim/techniques.json`, the new `world_sim/heritages.json`, and the
  `cultivation.technique.`, `cultivation.school.`, and
  `cultivation.heritage.myvillage.*` keys in both language files (Chinese
  names in both). It ships 129 techniques (core 116, body 10, active 3;
  grades 1..4 = 41/36/30/22), four schools (`sword`, `spear`, `fist` of kind
  `weapon`; `flying_sword` of kind `special`), and three four-technique
  heritages (太白剑脉 sword/metal, 青帝木脉 wood core chain, 万劫金身脉 metal
  body chain). Requirements follow the grade (黄 Qi Refining I, 玄 Qi
  Refining IV, 地/天 Foundation early; an element technique of grade 2 or
  more also needs 1500 bp affinity in its element).
- Registries `myvillage:school` (`SchoolDefinition`) and
  `myvillage:heritage` (`HeritageDefinition`), synced like techniques, with
  reference checks at server start. `TechniqueDefinition` gains optional
  `school`, `lineage.previous`, and `effects` (one block matching the
  category: `core.meditation_route`; `active`, `movement`, and `body` are
  decoded and validated only); `grade` is 0..4 (0 凡阶, Basic Breathing only).
- Running core technique: `CultivationService.switchCoreTechnique` (pure
  rules in `cultivation/technique/CoreTechniqueSwitch`). The technique must be
  learned and `core`; the running one again is a no-op; otherwise
  `techniques.switch_progress_loss` (0.3, new in `world_sim/rules.json`) of
  the progress is lost (散功) unless both techniques are on one lineage
  chain. The first learned core technique starts running; forgetting the
  running one clears it.
- Meditation progress factor `CoreTechniqueFactor`: the running technique's
  grade cultivation multiplier plus the element match bonus when the root
  has at least `roots.element_threshold_bp` in one of its elements, read from
  `world_sim/rules.json`; progress only, rounded down. Grade 0, no running
  technique, or no ledger data give ×1.0, so Basic Breathing is unchanged;
  玄阶 with a match is ×1.45 (normal batch 10 → 14, spirit batch 50 → 72).
- Serverbound `CoreTechniqueSwitchPayload` (`myvillage:core_technique_switch`,
  a technique id only); its handler calls only the service, a refusal gets
  no reply, a success pushes the snapshot.
- Commands `/myvillage cultivation|xiulian core|xinfa <target>
  <technique_id>`; `info` prints the running core technique.
- 功法 page: 心法 / 绝技 / 身法 / 炼体 cards (plus 未知功法 for unknown ids)
  through the pure `TechniqueShelf`; chips for category, grade name
  (凡阶..天阶), school, heritage with position (`太白剑脉 2/4`), and elements;
  the running core technique marked 运转中, the other core techniques with a
  运转此心法 button. `MeridianRoute` picks the meridian circuit from the
  running technique's `meditation_route`; only `xiaozhoutian` exists, so the
  diagram looks as before.
- World ledger heritages: `heritages.json` in `SimData`
  (`ContentTables.Heritage`, `heritage(id)`, `heritageOfTechnique(id)`),
  `Sect.heritageId`, `WorldState.lostHeritages`. At genesis each sect takes an
  unused heritage with `genesis.heritage_chance` 0.25 (`Purpose` 109); its
  signature is the chain's last technique, its basic the first, and ranks
  practise the chain up to the second-to-last. A sect that ends loses its
  heritage to the lost pool; a founder who practises one of its techniques
  rekindles it; the new fortune `lost_heritage` (ruin, tier 8+, travelling;
  effect kind `heritage`) hands out its manuals while it is lost. New
  chronicle lines `genesis.sect.heritage`, `sect.heritage_lost.1..2`,
  `sect.heritage_rekindled.1`, `fortune.heritage.1`.
- `world sect <id>` prints 传承; `SectView` and
  `WorldSimSnapshot.SectDetail.heritageName` carry it and the 天下 sect detail
  shows a 传承 row; the chronicle report's sect timeline shows heritages.
- Release-gate step `gen-technique-catalogue-check` before
  `validate-world-sim`. `validate_world_sim.py` checks every ledger technique
  against its datapack file (grade, element), heritage consistency, and
  acyclic `lineage.previous`.
- Tests: `TechniqueCatalogueDefinitionTest`, `CoreTechniqueSwitchTest`,
  `CoreTechniqueFactorTest`, `CoreTechniqueSwitchPayloadTest`,
  `TechniqueShelfTest`, `MeridianRouteTest`, `WorldSimHeritageTest`, more
  cases in `CultivationProfileTest`, `BasicBreathingSettlementTest`, and
  `SimDataLoaderTest`; `tools/tests/test_gen_technique_catalogue.py`.

### Changed

- Profile schema `4` adds the optional `active_core_technique`; v3 saves
  migrate with Basic Breathing running when it is learned (v1 and v2 go
  through v3); only v4 is written; a running id that is no longer learned
  reads as none.
- Payload protocol is now `10` (was `9`): the profile snapshot is v4 and the
  switch payload is new, so client and server need the same jar.
- World-sim save payload `version` `2` (was `1`), additive: sect `heritage`
  and top-level `lost_heritages`; version-1 saves load with no heritages.
- The 功法 category chips read 心法 / 绝技 / 身法 / 炼体 in Chinese (were 主修 /
  主动 for the first two).
- Docs: KB note 41 (new, in the index), notes 28, 37 (the panel now has two
  bounded serverbound cultivation payloads), and 40, README (commands, the
  techniques-and-heritages section, the 0.37.0 ledger), AGENTS.

### Deferred

- Runtimes for active, movement, and body effects (绝技, 身法, 炼体); such
  techniques can be learned and listed but do nothing.
- Mastery tiers and mastery carried over within a chain; a breakthrough
  multiplier from the running technique (player advancement is
  deterministic).
- Manuals as items, reading as a meditation mode, the scripture hall, and any
  player route into a ledger sect or heritage; weapon `family` fields;
  meridian routes other than the small circuit.

### Verification

- Automated: `gen_technique_catalogue.py --check`, `validate_world_sim.py`,
  the five cultivation validators, their Python tests, and the Gradle tests;
  the world-sim health bands are unchanged and pass (after prehistory, small
  seed 1 has 3 of 5 genesis sects with a heritage, small seed 5 none).
- Not verified: everything in a real client (page layout at the three GUI
  sizes in both languages, the switch button, 散功, the meridian diagram,
  the 传承 row and `world sect` line, old-save migration, chronicle lines).
  See the README ledger "Technique system (0.37.0)".

## 0.36.1

The cultivation calendar gets a longer year and a week. One cultivation day
stays one Minecraft day-night length (`24000` ticks, 20 real minutes online);
a year is now `24` days (was `6`), and a new week of `6` days sits between day
and year, so a year is four weeks (8 real hours online; the mortal 80 years
are 640 hours).

### Changed

- Server config `cultivation_time`: `days_per_year` defaults to `24` (was
  `6`); new `days_per_week` (default `6`, at least `1`). All three values are
  logged on load and reload; a `days_per_year` that is not a multiple of
  `days_per_week` loads with one operator warning, and its last week is
  shorter. Weeks restart with every year. `CultivationServerConfig.Scale`
  gains `daysPerWeek` (the two-value constructor keeps the default week).
- The cultivation time status and its snapshot payload carry `daysPerWeek`
  (a positive varint after `daysPerYear`); payload protocol `9` (was `8`).
- Panel dates read year, week and day: the H header and the 内视 calendar
  card show `1 年 第 1 周 第 1 日` / `Year 1, Week 1, Day 1`
  (`screen.myvillage.cultivation.calendar_value` now has three parameters;
  the header drops its 宗历 label when the panel is too narrow, as English is
  at GUI 320), and the 天下 总览 date row shows `启元N年 第W周第D日` once the
  time snapshot has arrived (new key
  `screen.myvillage.cultivation.world.date_week_value`), otherwise the old
  form. `PanelReadouts.calendarWeek`/`calendarDayOfWeek` derive them.
- World sim: `rules.json` `time.default_days_per_year` is `24`.
- `validate_cultivation_lifespan.py` pins `24` days per year, the
  `days_per_week` config, and the snapshot's `daysPerWeek`.
- Docs: `cultivation-lifespan-calendar` spec (576000 ticks per year, the week,
  the divisibility warning), KB 30 and 37, README `cultivation_time` with a
  conversion table.

### Existing worlds

Stored calendar and lifespan values are raw ticks and are not rescaled, so an
existing world is reinterpreted under the new scale at once: the calendar year
and day change, each player's consumed and remaining lifespan in years shrink
to a quarter (a year now takes four times as many ticks), and lifespan warnings
move accordingly. The world ledger counts days, so its ages and dates are
read with 24 days per year as well: every age in the ledger shrinks by a
factor of four.

## 0.36.0

The world ledger (命簿) in game: the H panel gets a fourth page, 天下 (World),
where every player can read the ledger that until now only
`/myvillage world` (permission 2) and the offline report showed. The page is
read-only; pause, resume, and advance stay admin commands.

### Added

- The 天下 / World page (`client/cultivation/panel/WorldPage`) after 功法 in
  the H panel, with five sub-views switched from a dock of buttons:
  总览 (era date and day, settlement running or paused with pending days,
  tier, living against target, dead, sects active and destroyed, events so
  far; living per realm as bars; the five foremost people), 宗门 (every sect,
  active first: master, members, top realm, prestige, gate built or not),
  人物 (a live search by name or Daoist title, up to 10 matches, living
  first), 纪事 (the latest 40 notable and major events, newest first, each
  with the line it answers when that is still kept), and 此地 (the player's
  region: tier, qi, danger, cultivators, whether a sect may be founded; the
  sects seated there with the distance to their gate; the strongest people
  present; recent events there). Rows open a sect (founding and founder,
  parent sect, master, resources, prestige, signature technique, gate,
  relations with feud and war, members at the sect, recent events) or a
  person (realm and stage with progress, root grade and five-element shares,
  age, master, sect and rank, whereabouts and status, technique, injury; for
  the dead the death date, cause, and killer; relations grouped by kind;
  recent events), and names inside those open the next one; a back row
  returns. Loading, ledger-inactive (with the server's reason), outside every
  region, and empty states each have a card. Below 300 GUI pixels of body
  width the cards stack in one column and the dock takes two rows.
  `WorldCanvas` (cards measured by the code that draws them, hit boxes,
  hover tint) and `WorldReadouts` (ages, dates, relation grouping, root bar
  widths, colours; `WorldReadoutsTest`). 88 new keys in `zh_cn` and
  `en_us`: `screen.myvillage.cultivation.tab.world` and 87 under
  `screen.myvillage.cultivation.world.*`.
- A bounded read-only query and its answer (`sim/runtime/net/`):
  `WorldSimQuery` (kind, id, text up to 32 characters) goes serverbound in
  `WorldSimQueryPayload`; the server answers with a `WorldSimSnapshot` in
  `WorldSimSnapshotPayload` (hand-written `WorldSimSnapshotCodec`), built by
  the pure `WorldSimSnapshots` with every person, sect, technique, and
  region name resolved and each list capped (`MAX_SECTS` 96, `MAX_MEMBERS`
  24, `MAX_SEARCH` 10, `MAX_PRESENT` 10, `MAX_CHRONICLE` 40, `MAX_RELATED`
  10). `WorldSimPayloads` registers both, answers at most one query per
  player every 4 server ticks (a faster one is dropped unanswered), and
  answers "inactive" with the reason while the ledger is down. The client
  cache `client/sim/ClientWorldSimState` sends the same query at most once
  per 2.5 s and is cleared on logout.
- `WorldSim.event(id)`, `recentEvents(minImportance, limit, filter)`,
  `realmIds()`, `nameOf`, `sectOf`, `livingIn`, and `hasRegion` (pure core);
  `WorldSimText.event(key, params)`.
- H-panel hub hooks on `PanelPage`: `pointer` (mouse position before each
  frame, -1,-1 outside the body), `mouseClicked` (clicks inside the body),
  and `takeScrollToTop`; all no-ops by default, so the other pages are
  unchanged.
- Tests `WorldSimQueriesTest`, `WorldSimSnapshotsTest` (each kind's sections
  against a genesis world, caps, resolved names, causes),
  `WorldSimPayloadCodecTest` (round trip of every kind and the inactive
  answer, unknown kind rejected), `WorldSimPayloadThrottleTest`, and
  `WorldReadoutsTest`.

### Changed

- Payload protocol is now `8` (`ModPayloads.PROTOCOL_VERSION`, was `7`), so
  the client and server need the same jar.
- `WorldSimCommands` sorts people strongest first through the shared
  `WorldSimSnapshots.strongestFirst`, so `/myvillage world` and the panel
  list members in the same order.
- While a text field in the panel has focus (the 人物 search), H types a
  letter instead of closing the panel; Escape still closes it.
- `docs/ai-kb/37_cultivation_panel.md` and `docs/ai-kb/40_world_sim.md`
  describe the page, the query, and its limits.

### Verification

- Automated: the world-sim validator and its Python tests, the five
  cultivation validators (they read the screen and every `panel/` file as one
  source, the 天下 page included), and the Gradle tests for `sim.*` and
  `client.*`.
- On the owner's PC (real GPU, Chinese client, singleplayer world
  `agent-test`, GUI 534x300 at scale 3, then 480x270, 427x240, and 320x240
  by resizing the window): all five sub-views, the live person search,
  cause lines in the chronicle, drilling into a sect and a person and back,
  hover highlight, one-column stacking, and the two-row dock below 300
  wide. Stills in `out/preview/world_sim_panel/pc/` (untracked). Developer
  evidence, not an owner verdict.
- Not verified: English text at narrow widths, multiplayer, the
  ledger-inactive card on a real client, mouse feel on a physical mouse, and
  the owner's verdict on the look and the amount of information. See the
  README ledger "天下 page (0.36.0)".

## 0.35.1

The sect compound (山门) rework, after the owner walked the 0.35.0 compound
built by `/myvillage world sect <id> build here` and rejected it. The two
things the owner asked for come first.

### Fixed

- Floating fragments are gone. The covered galleries (廊), one-block lines of
  stone-brick floor with a top half-slab two blocks up and a lone oak fence
  every third block, are removed entirely, so no half-slab hangs in the air
  around the buildings and no fence stands on the axis. Every sect template
  placement now drops any template block that touches no other template block
  on any face (a new `myvillage:drop_isolated_blocks` structure processor),
  as a safety net for lone eave stairs and trapdoors; the templates are fixed
  at the source separately.
- The axis is connected front to back. A paved corridor (御道, 7 wide, polished
  andesite with a chiseled centre line) runs from a new levelled forecourt in
  front of the gate to the row before the principal hall; a 3-wide, 3-high
  passage is cut through the gate building with stairs in front and behind;
  no building other than the gate and the principal hall stands on the axis;
  and a final pass keeps 5 blocks of air above the corridor. Between terraces
  there are proper grand stairs (9 wide, 4 rises, a 3-row landing, 4 rises,
  facing the right way, solid underneath, cheek walls either side) cut into
  solid retaining bands with a stone-brick face and chiseled coping. The
  13,000-odd stone-brick wall blocks that used to fill the bands are gone;
  the generator writes no wall block anywhere.
- The gate passage is 3 blocks high (the door rows and one above) instead of
  4, so it no longer cuts away the lower row of the gate's two-row hanging
  plaque (牌匾); it still leaves 3 blocks of air over the floor.

### Changed

- Terraces are symmetric about the axis (59/57/55/53/51 wide). Flank
  buildings are mirrored pairs beside the axis, sized and aligned by their
  actual template; the bell and drum towers stand clear of the gate. The
  scripture terrace holds two scripture pavilions instead of an on-axis
  pavilion boxed in by two pagodas; pagodas no longer stand on terraces.
- The derived mountain has no noise inside the compound core or on the
  forecourt, so no stray stone rises onto terraces, bands, stairs or the
  ground in front of the gate; the taper strips beside the narrower terraces
  slope down one block per block.
- The outer skirt no longer reads as a forest of 1x1 stone spikes. It is a
  cone falling one block per block from the terraces and the forecourt, with
  smooth value-noise relief (±4 on a 6-cell lattice plus ±1 on a 2-cell
  lattice, smoothstep-blended, faded in over 4 cells from the core) instead
  of ±5 per-cell noise, slope-limited so neighbouring skirt columns differ by
  at most 2 (except at the cliff back), and graded into natural ground by 24
  cells. Its top blocks are mostly stone with some andesite, tuff and cobbled
  deepslate; the interior stays stone.
- Each retaining face carries one-deep stone-brick pilasters with a chiseled
  top, full face height, every 8 blocks out from the axis (clear of the
  stair and its cheeks, and left out where a building stands against the
  face), standing on the lower terrace's last row.
- The detached spire is built only where it clears every building, terrace
  and stair. None of the three variants does yet (each would stand inside
  the summit), so it is skipped and logged, and its peak is not raised; the
  variants stay in the plan and in the ledger's records.
- The cloud sea is removed (it sat under the old retaining fill and was never
  visible).
- `SectCourtyard` keeps avatars off the stairs and the axis corridor; on seed
  7 the gate terrace has 272 courtyard cells, still room for forty avatars.
  `tools/world_sim_avatar_evidence.py` moves its courtyard box and camera
  rows off the stair.

### Tests

- `SectCompoundRealizationTest` builds whole compounds (seeds 7, -123456789,
  20260618) with the real templates into an in-memory world, on the command
  path and the worldgen path (also chunk by chunk), and checks that the axis
  is walkable from the forecourt to the hall, nothing floats, the generator
  writes no wall block, only the gate and the hall stand on the axis and the
  flanks mirror, every stair climbs one terrace in single steps, courtyard
  cells are open ground, chunk slices join, and the core has no noise. It
  also checks that the gate's hanging plaque survives the passage cut, that
  the pilasters stand where planned and mirror about the axis, that for six
  seeds on rolling and on low flat ground no two neighbouring skirt columns
  differ by more than 2 while the core and forecourt heights are unchanged,
  and that skirt tops are mostly stone with the other three stones present.
  `DropIsolatedBlocksTest`; `SectCourtyardTest` re-derived from the new plan.
- Specs `sect-compound-layout`, `sect-compound-realization`,
  `sect-mountain-derivation`, and `sect-worldgen-structure` describe the new
  behaviour.

## 0.35.0

The world simulation (世界模拟): every world now keeps a ledger, the 命簿, of
sects and named cultivators who cultivate, break through or die trying,
travel the regions, find fortunes (奇遇), befriend, rob, and kill each other,
take revenge, wage sect wars, found new sects and lose old ones, one sim day
per cultivation-calendar day, whether or not a player is near. The ledger is
the only authority; avatars (化身) and sect compounds (山门) are its
projections. It is the first real caller of the region query interface.

### Added

- Pure core `com.example.myvillage.sim` (no Minecraft types; compiles with
  plain `javac` and Gson): the facade `WorldSim` with immutable views, one
  engine class per mechanic (cultivation, great-realm breakthrough, lifespan,
  travel and seclusion, beasts, fortunes, meetings and fights, revenge chains,
  sect economy, sect politics with feuds, wars, truces, tribute, annexation,
  decline, schisms and founding, succession, entrants), genesis with sect
  gates fixed inside their regions and a prehistory run, three population
  tiers (`small`, `medium`, `large`), 启元 era dating, a chronicle whose lines
  state their cause and point at the event that caused them, tombstones for
  the dead, hash-derived randomness per (seed, day, subject, purpose), and a
  canonical, versioned save payload.
- Data `src/main/resources/data/myvillage/world_sim/`: `rules.json` (every
  balance number, rates per year), `realms.json` (炼气, 筑基, 金丹, 元婴; the sim's
  own ladder, lifespans equal to the player realms where both exist),
  `encounters.json` (fortunes), `names.json`, `techniques.json` (功法),
  `lore.json` (artifacts, sites, beasts); strict loaders that name the file
  and field on any error. `world_sim.*`, `commands.myvillage.world.*`, and
  `message.myvillage.world.rumor` keys in `en_us` and `zh_cn`, the Chinese
  written as chronicle prose.
- Runtime `sim/runtime/`: overworld SavedData `myvillage_world_sim` holding
  the core payload; genesis once per world after the region runtime (old
  worlds included), with the tier then fixed in the save; one settled sim day
  per tick while days are pending, capped by the smaller of the config's
  `catch_up_cap_days` and the rules' `scheduler.max_pending_days`; pause and
  resume without catch-up; the ledger stays inactive and its save untouched
  when the region runtime or the data fails to load or the save cannot be
  read.
- `/myvillage world [info]`, `sects [all]`, `sect <id|name>`,
  `person <name>`, `chronicle [1-50]`, `here`, `pause`, `resume`, and
  `advance <1-3650>` (permission 2).
- Rumors (江湖传闻): major events reach every online player in chat, players in
  the event's region first; notable events reach only that region; at most
  `rumors_per_minute` per player, the rest in a capped queue.
- Server config `myvillage-world_sim-server.toml`: `[world_sim]` `tier`
  (default `small`) and `catch_up_cap_days`; `[rumors]` `rumors_enabled`,
  `rumors_per_minute`, `rumor_queue_cap`; `[avatars]` `avatars_enabled`,
  `avatar_spawn_radius`, `max_avatars_per_sect`, `max_avatars`.
- `tools/world_sim_cli.py` (offline runner: JSON dump and prose chronicle),
  `tools/world_sim_report.py` (chronicle pages with population, realm, and
  sect-timeline charts and biographies in `out/preview/world_sim/`), and
  `tools/world_sim_evidence.py` (headless creative-mode session and restart,
  output in `out/preview/world_sim/evidence/`).
- `tools/validate_world_sim.py`: data structure and cross-references, both
  languages' keys and slots, and core purity. `tools/validate_custom_entities.py`
  gains `check_npc_state`: an NPC contract's `state.synced` fields and
  `state.persisted` tags must match the Java.
- Tests under `src/test/java/com/example/myvillage/sim/`: determinism,
  save/load, scheduler, purity guard, realm lifespan agreement, text-key
  coverage, genesis, liveness, long-run health bands per tier, performance,
  and the runtime's driver, rumor board, save wrapper, config, and text.
- `/myvillage world sect <id> build [here]`: builds the sect's compound (山门,
  the worldgen-style compound with its derived mountain) at the ledger gate,
  on the surface of the loaded gate chunk, or with `here` moves the ledger
  gate to the caller first and builds at the caller's feet; seed and spire
  variant are hashed from the world seed and the sect id; the build is
  synchronous and the reply warns that the server waits for it. It records
  the anchor, seed, variant, and sim day in overworld SavedData
  `myvillage_world_sim_gates` and marks the gate realized.
- `sect/SectCourtyard`: the open courtyard ground of a compound from its plan
  (terrace floors + 1, lowest terrace first, minus edge rows, buildings with
  a margin, roofed galleries, the detached spire and its bridge) and the
  compound's site rectangle.
- Ledger avatars (化身): while a player is within `avatar_spawn_radius` of a
  built compound's site, the sect's members at the sect stand on its
  courtyard as `myvillage:cultivator` avatars named 姓名 · 境界 · 宗门
  (`entity.myvillage.cultivator.avatar`, the realm through
  `world_sim.realm.*`): master, then elders, then by realm, capped per sect
  and in total, one entity per person, two blocks apart; withdrawn beyond the
  radius plus 32 blocks, reconciled after every settled day, discarded when
  the ledger is inactive or the server stops. `NpcEntity.becomeLedgerAvatar`
  gives an avatar a synced ledger person id (-1 for ordinary NPCs); an avatar
  is never saved with its chunk, cannot be attacked or hurt except by damage
  that bypasses invulnerability, is fire immune and not pushable, does not
  stroll, and ignores interaction; a copy the manager did not spawn is
  refused when it joins a level. `/summon` and the spawn egg are unchanged.
- `tools/world_sim_avatar_evidence.py` (headless compound build and avatar
  checks, output in `out/preview/world_sim/avatars/`).
- Tests `SectCourtyardTest` (courtyard cells against the real plan),
  `AvatarPlannerTest`, and `GateRealizationsTest`.
- `docs/ai-kb/40_world_sim.md`.

### Changed

- The shared cultivation calendar (修仙历) now advances on every server tick on
  which at least one player is online, in any game mode, so the world clock
  keeps running while testing in creative or spectator; personal lifespan is
  still consumed only in survival or adventure mode while alive.
  `tools/validate_cultivation_lifespan.py` and its tests check the new rule.
- Spec `cultivation-lifespan-calendar`: the calendar requirement and its
  scenarios now describe online-player presence in any game mode (a new
  scenario for creative or spectator players only, and one for no player
  online); the personal-lifespan requirement is unchanged.
- Spec `humanoid-npc-runtime`: the requirement "An NPC is a body without a
  disposition" now also allows removal by the world simulation withdrawing an
  avatar and states the avatar's behaviour (synced ledger person id, never
  saved, no stroll, not attackable or hurt except by invulnerability-bypassing
  damage, no burning or pushing, no interaction); the scenario "A cultivator
  stays when the player leaves" becomes "A summoned cultivator stays…"; new
  scenarios "An avatar follows the ledger, not the chunk" and "An avatar
  cannot be hurt". The requirement "The cultivator is summoned only" becomes
  "The cultivator is summoned or projected, never spawned naturally", with a
  new scenario "The simulation projects a sect's members".
- Entity contract `genops/contracts/entities/cultivator.yaml`: `state.synced`
  lists `ledger_person_id`, `state.persisted` lists `WorldSimPerson`, and a
  new `world_sim_avatar` section describes the avatar mode.
- `tools/release_gate.py` runs `tools/validate_world_sim.py`.
- `docs/ai-kb/14_deferred_roadmap.md` §A records the world sim as the region
  query interface's first caller.

### Verification

- Automated: the world-sim validator, its Python tests, and the Gradle tests
  (the pure-core suite including the health bands, and the runtime tests).
- Developer evidence: chronicle pages for small and medium tiers in
  `out/preview/world_sim/`; the headless session and restart
  (`out/preview/world_sim/evidence/summary.md`) passed all 17 checks:
  every command, calendar and ledger advancing in creative, pause and resume
  without catch-up, restart keeping the ledger with genesis once and a later
  tier change ignored, `advance` bounds, and rate-limited rumors. The avatar
  session (`out/preview/world_sim/avatars/`) passed all 19 checks: 12 named
  avatars on the courtyard floor under open sky, 0 with the player 300 blocks
  away, 12 again on return with new UUIDs, the set following the ledger after
  `advance 60`, no duplicates, avatars invulnerable while a summoned
  cultivator still takes damage.
- Not verified: the Chinese chronicle, rumor, and name-tag text on a zh_cn
  client, how rumors and avatars look on a physical client, multiplayer,
  `sect <id> build` without `here` on real terrain far away, the gate record
  surviving a restart in game, genesis on an existing old world in game, the
  inactive and untouched-save paths in game, and the calendar acceptance step
  9 under the new rule. See the README ledger "World
  simulation (0.35.0)".

## 0.34.1

The cultivator's face is rebuilt. The owner reviewed 0.34.0 on their own PC
on 2026-10-06 and accepted the clothes, the back view, the poses seen from
behind, and the walk, but rejected the face: "有点像大佛的脸，没有修仙者那种
俊朗英气". The face was wider than tall with a full square jaw, carried a teal
dot between the brows that read as a Buddha's urna, had heavy lids over
wide-set eyes, and a small saturated pink mouth. Only the head changes; the
body, clothes, hair back view, strands, and clips are as in 0.34.0.

### Changed

- Head geometry: the skull is now the cranium only (eight rows), and the jaw
  is three cubes flush with the face plane that narrow toward the chin (11, 9,
  and 7 texels wide), each shallower than the one above so the jaw line rises
  toward the ear from the side; the face is now taller than wide. The nose is two texels tall instead of three.
- Hair cut-outs: the forehead is open under a straight one-row hairline (no
  fringe, no centre parting), the temple corners come one row lower, and the
  sideburns run down to the cranium's bottom.
- Face paint: no mark between the brows and no separate lash line; the brow
  lies directly on a one-row eye with white either side of a blue-grey iris,
  and its tail fades into the sideburn on the same row (剑眉入鬓); a plain jaw
  contour; a muted three-texel mouth. The top of the nose, which faces up and
  so draws at full brightness, is painted as dark as the lit front; the
  cranium's underside is hair and the jaw's undersides a light skin tone, so
  from below there is neither a light line on the nose nor brown patches
  beside the cheeks.
- NPC previews draw without back-face culling, as the game does
  (`NpcModel` draws with `entityCutoutNoCull`): the inside of the hair shell
  shows through its cut-outs instead of the background. `render` in
  `tools/beastgen/preview.py` gains `cull=True`; beast previews are unchanged.

### Added

- `python3 -m tools.npcgen preview <npc> --only face`: `face.png`, the head
  large from the front, from slightly below, from both front three-quarters,
  at a shallow angle, and from the side, plus the same six views at about the
  size the face has a few blocks away.

### Verification

- Automated: the release gate (validators, generator checks, Python and
  Gradle tests, build, jar listing); `tools/tests/test_npcgen.py` pins the
  tapering jaw, the two-tall nose, the open forehead, the brow on the eye, the
  eye's texels, the mouth, and the absence of the old mark.
- Owner, 0.34.0 on their own PC, 2026-10-06: clothes, back view, poses seen
  from behind, and walk accepted; face rejected.
- Not verified: the 0.34.1 face, and every surface the owner did not speak
  to (see the README ledger "Cultivator NPC").

## 0.34.0

The first humanoid NPC, `myvillage:cultivator` (修仙者), and the framework it
runs on: an NPC is an `NpcEntity` subclass plus one generated art definition.
The cultivator is a body without a disposition: it stands, strolls, and looks
at players; friend or foe is decided later. No natural spawning and no drop;
`/summon` and its spawn egg only.

### Added

- `myvillage:cultivator` (en_us Cultivator, zh_cn 修仙者): a 0.6 x 1.9 block
  humanoid in mob category `misc` with its own model, not the player model or
  a skin. The look is built from layers: a crossed robe collar, a sleeveless
  indigo vest open down the front with border bands and sloped shoulder caps,
  a belt with a buckle, sash ends, and a jade pendant, sleeves that deepen to
  an open cuff with a hanging drape, a two-tier skirt under the vest's panels,
  and a cut-out hair shell with a bun, crown, pin, ribbons, loose strands, and
  long back hair. It never attacks, does not despawn by distance, and cannot
  be leashed.
- `myvillage:cultivator_spawn_egg` (修仙者刷怪蛋) in `myvillage:main`, after the
  demon wolf egg; an empty loot table.
- `entity/npc/`: `NpcEntity` (float, stroll, look at players; no target,
  attack, trade, or dialogue) and `CultivatorEntity`.
- `client/entity/npc/`: a generic `NpcRenderer` and `NpcModel` that read an
  NPC's model and animation JSON with the beast parsers, run `idle` from the
  first client tick, and drive `walk` at the rate that keeps the planted foot
  still.
- Model schema: an optional `scale` (1 when absent). The cultivator's file
  uses 0.5, so its geometry and texture are twice as fine as a vanilla mob's
  (32 texels per block). `BeastRenderer` and `BeastGait` honour it; the demon
  wolf's files are unchanged.
- `tools/npcgen`: `build <npc> [--check]` writes or verifies the model,
  animations, and texture from `defs/<npc>.py`; `preview <npc>` renders a
  turnaround, close-ups, a scale image, the atlas, clip sheets, and GIFs to
  `out/preview/<npc>/`. `shade.py` bakes shadows cast by the layer above and
  crevices beside raised bands from the rest-pose geometry. It reuses
  `tools/beastgen`'s cuboid, painter, and clip modules.
- `python3 -m tools.combat_capture npc [--parts idle,walk]`: in-game stills
  (four sides, close-ups of the layered parts, scale beside the player) and
  walk footage to `out/preview/<name>/ingame/`.
- `genops/contracts/entities/cultivator.yaml`, spec `humanoid-npc-runtime`,
  and `docs/ai-kb/39_humanoid_npcs.md`.

### Changed

- `tools/validate_custom_entities.py` validates every NPC `tools/npcgen`
  builds (schemas, required clips, texture size and binary alpha, names, spawn
  egg, loot table, registration against the contract, renderer registration,
  no natural spawning) and accepts the optional model `scale`; its report
  gains `npcs`. The registration, spawn-egg, resource, and spawning checks are
  shared by beasts and NPCs.
- `tools/release_gate.py` runs `python3 -m tools.npcgen build cultivator
  --check`.

### Verification

- Automated: the release gate (validators, generator checks, Python and
  Gradle tests, build, jar listing).
- Developer evidence: offline previews in `out/preview/cultivator/` and a
  headless in-game capture in `out/preview/cultivator/ingame/` (stills beside
  the player and from four sides, close-ups, walk footage).
- Not verified: everything on a physical client, including the look, the
  walk, `F3+T` reload, save and reload, and multiplayer. See the README
  ledger "Cultivator NPC (0.34.0)".

## 0.33.0

The first hostile beast, `myvillage:demon_wolf` (妖狼), and the framework it
runs on: a beast is a `BeastEntity` subclass plus a data file of attack moves
plus one generated art definition. The wolf has no natural spawning and no
drop; `/summon` and its spawn egg only.

### Added

- `myvillage:demon_wolf` (en_us Demon Wolf, zh_cn 妖狼): a monster with a
  1.3 x 1.45 block hitbox, its own cuboid model, keyframe clips (idle, walk,
  run, bite, pounce, stagger), glowing eyes, mane edges, and tail tip, and
  vanilla wolf sounds at a lower pitch. It hunts the nearest visible player
  with two moves: a close bite (撕咬) whose recovery is the punish window, and
  a mid-range pounce (扑击) whose aim locks on where the target stood before it
  leaps. No GeckoLib or other new dependency.
- `myvillage:demon_wolf_spawn_egg` (妖狼刷怪蛋) in `myvillage:main`, after the
  simple fox egg; an empty loot table.
- `entity/beast/`: `BeastEntity` (server move runtime: selection by range,
  cooldown, gap, and weight; turn then aim lock; one lunge sized under vanilla
  drag to land on the aim point; an oriented hit box on the active ticks;
  per-move knockback), `BeastDataLoader` and `BeastDefinitions` (strict
  schema-1 loading of `data/myvillage/beast/index.json` and one file per
  beast, a startup error on bad data), `BeastMoveSelector`, `BeastGeometry`,
  `BeastMotion`, and `BeastCommands`. Attributes and every move number live
  only in `data/myvillage/beast/demon_wolf.json`.
- `client/entity/beast/`: a generic `BeastRenderer` and `BeastModel` that read
  a beast's model and animation JSON into vanilla `ModelPart` and
  `AnimationDefinition`, with an emissive layer and walk/run rates that keep
  planted feet from sliding. Clips follow the synced move tick and pause with
  the hit-stop. While a move shows, each position update is applied in one
  step and the move clip plays two ticks behind the newest synced move tick,
  so the pose matches the drawn position (with vanilla's three-step smoothing
  the pounce's landing pose hung about 0.6 blocks in the air); uneven packet
  arrival can show as per-tick judder during a move.
- `tools/beastgen/`: generates a beast's model, clips, texture, and glow layer
  from `defs/<name>.py`, keying move clips on the server ticks read from the
  data file, including the landing tick of a leaping move from a port of the
  server's lunge physics, and holding planted paws at their world point while
  a ground lunge carries the body (`build`, `build --check`, `preview`; the
  previews move the beast along the server's path).
- `/myvillage beast move <targets> <move_id>`, `status <targets>`, and
  `debug on|off` (permission 2) for server checks and frozen-tick stills.
- `python3 -m tools.combat_capture beast` with six parts (`idle`, `moves`,
  `locomotion`, `fight`, `dodge`, `slowmo`): headless stills, footage, a
  recorded 60 s fight with the server's debug log and per-beast counts, slowed
  dodge trials, and slowed move videos, under `out/preview/demon_wolf/ingame/`.
- `genops/contracts/entities/demon_wolf.yaml`,
  `docs/ai-kb/38_hostile_beasts.md`, and the `hostile-beast-runtime` spec.

### Changed

- Combat hit reactions honour a new `combat/runtime/StaggerResistant`
  interface: a target that resists at the moment of the hit takes the damage
  and the hit-stop freeze but no stun and no knockback impulse. Every other
  target reacts as before. A beast resists inside its running move's
  `immune_ticks` (the bite from its first tick through its last active tick,
  the pounce from mid wind-up through its last active tick) and is otherwise
  staggered out of the move, which then takes a short cooldown.

### Validation

- `tools/validate_custom_entities.py` now checks every beast in the data index
  (data invariants, model and clip files against the server ticks, textures,
  generator ownership, names, spawn egg, loot, Java registration against the
  contract, client-only rendering, a beast-neutral combat package, no natural
  spawning), with new tests; the simple fox checks are unchanged.
- The release gate runs `python3 -m tools.beastgen build demon_wolf --check`.
- `tools/release_gate.py`: all 44 steps passed, including 418 Java tests and
  630 Python tests.
- Headless capture on the build host (developer evidence,
  `out/preview/demon_wolf/ingame/`; offline previews in
  `out/preview/demon_wolf/`): in a 60 s fight against a survival player with
  the Qingfeng sword, no armour, and natural regeneration off, 35 player hits
  inside an immune window caused no stagger, and 36 outside caused 20, 17 of
  which cancelled a move. A player who stood still and traded took the pounce
  and two bites per wolf before killing it (about 14 of 20 health on Normal, no
  deaths). Strafing during the bite's wind-up avoided it in the dodge trial.
  Stepping away from a pounce after its lock was not measured. The client
  position and clip timing during moves have no unit test (they need a client
  level); the landing stills and slowed videos are their evidence.
- Nothing about the wolf has been observed by the owner on a physical client;
  its look, fight feel, sound, `F3+T` reload, and multiplayer are
  `not_verified` in the README ledger.

## 0.32.0

The Meditation page of the H panel is rebuilt around a meridian diagram. It is
a client view of values the server already sends: no packet, server rule, or
data file changed, and there is no meridian state on the server.

### Changed

- The Meditation page (修炼) centres on a figure seated cross-legged in profile
  with the small circuit drawn on it: 督脉 up the back, 任脉 down the front,
  through the lower dantian, with 会阴, 命门, 大椎, 百会, 膻中, and 丹田 labelled,
  plus a hand channel to the palm and a foot channel to the sole.
- The diagram follows the session state. At rest it is dim and still; preparing
  gathers qi toward the dantian; normal meditation circulates jade motes;
  spirit-stone meditation is blue, faster, and draws qi in from palm and sole;
  an ordinary advancement is gold with a halo that fills with the elapsed
  share of the advancement; a bottleneck advancement is red-orange and the qi
  bunches at 玉枕. Without an awakened root or Basic Breathing it is grey and
  names what is missing.
- The dantian fills with cultivation progress over the stage cap and the ring
  around it with stability over its cap. Mote counts and speeds are decoration.
- The readouts the page had before (progress, stability, both modes' yields and
  costs, the advancement's target, conditions, duration, and costs) sit beside
  the diagram, or below it in a narrow window. The card of the running mode
  takes the diagram's colour. The four buttons and their intents are unchanged.

### Added

- `MeridianChart` (acupoints and channels as data), `MeridianPath`,
  `MeridianLook`, `MeridianView`, and `VectorBrush` under
  `client/cultivation/panel/`, with unit tests for the first three.
- `textures/gui/cultivation/meridian_figure.png`, generated by
  `tools/gen_meridian_figure.py` (`--check` fails when the committed file
  differs from the script's output).

## 0.31.0

The cultivation screen (H) is rebuilt as a panel with a page rail, so later
systems can each add a page. What the client may read and send is unchanged:
the same three clientbound caches, the same four bounded meditation intents,
and every decision on the server.

### Changed

- H opens a framed panel instead of the two-tab screen. A header (name, realm
  and stage, calendar, remaining lifespan) and a footer (session state) stay
  visible on every page; the body scrolls when a small window cannot hold a
  page. Reopening H returns to the page it was left on.
- The Profile tab is 内视 in Chinese and now shows the current realm's stage
  ladder, progress and stability bars, a lifespan bar, the spiritual root as
  shares, and the next advancement's target and conditions.
- The Meditation page groups session state, the two meditation modes, and the
  advancement (target, conditions, duration, stability cost, interruption
  loss). Its four buttons are labelled with their bound keys.
- The screen title and the Chinese key name read 修仙面板 / 打开修仙面板. The
  English key name stays `Open Cultivation Profile`.

### Added

- A Techniques page (功法): each learned technique's category, grade,
  elements, mastery, and requirements.
- `client/cultivation/panel/`: `PanelPage` (the contract a system's page
  implements), `PanelContext`, `PanelReadouts`, `PanelTheme`, `PanelButton`,
  and the three pages. `docs/ai-kb/37_cultivation_panel.md` describes the
  structure and how to add a page.

### Removed

- The advancement rows of the old Profile tab (rule, stability, runtime) and
  their six language keys; the Meditation page and the Profile page's
  advancement card carry that information.

### Validation

- The five cultivation validators that inspect the H screen read
  `CultivationProfileScreen.java` and every file under `panel/` as one source,
  so their rules (one button per action, no legacy reserve, no profile write,
  page switches send nothing) cover any page. New validator tests reject a
  second action binding on another page and a reserve readout on a page.
- `PanelReadoutsTest` covers the panel's display arithmetic.
- Checked on the development host's headless client at GUI 480x270, 427x240,
  and 320x240, in English and Chinese at the first size, including preparing,
  meditating, and a completed advancement driven through the panel buttons;
  then opened on the owner's Windows client through DevBridge with its mortal
  profile. The owner's verdict on the new panel is `not_verified`.

## 0.30.0

Combat base capabilities after the Lingxiao Spear: the combat data format is
revised to weapon-neutral names (format 2), and the tooling a third weapon
needs is in the repository. Nothing a player sees or the server computes
changes; both weapons look and play as in 0.29.0-fix1.

### Changed

- Geometry contract `format` 2: `axes.blade` is `axes.length`, `pommel` is
  `butt`, `guard` is `collar`, `blade` is `head`, `blade_base`/`blade_tip` are
  `head_base`/`head_tip`. First-person rig: `rig.sword_scale` is
  `rig.weapon_scale`. A format-1 contract, an old field name, or a rig with
  `sword_scale` fails to load with a message naming the new field (Java loader,
  `tools/combat_data.py`, validator); nothing falls back to a default. The
  client classes follow (`WeaponGeometry`, `FirstPersonWeaponTransform`,
  `FirstPersonWeaponTrail`, `WeaponTrailShape`). Tool file names, sound ids,
  animation files, and item, style, and move ids keep their names. Migration
  table in `docs/ai-kb/34_combat_data_and_capture.md`.
- `COMBAT_JAR_STALE` compares content: a jar older than a source is stale only
  when it lacks that resource's bytes or the class files compiled since the
  source changed. Before, a comment-only Java edit left a current jar reported
  stale, because Gradle keeps a jar whose bytes do not change.

### Added

- Per-move `trail.samples` in a style file (optional): the samples the
  third-person world trail follows instead of the hit samples, in the same
  form and under the same sample rules. Presentation only; the server never
  reads it. No shipped style uses it.
- `rig.off_hand` overrides (optional): `upper_arm`, `forearm`,
  `rest_direction`, `rest_reach`; defaults are the main arm's bones and the
  former fixed rest. The shipped spear rig does not use them.
- `tools/combat_preview/` (`python3 -m tools.combat_preview fp|pose|model`):
  the offline preview tools, formerly outside the repository, with
  `requirements.txt` and `.venv-preview` / `$MC_PREVIEW_PYTHON` interpreter
  resolution. A golden fixture pins the first-person solver:
  `FirstPersonPreviewParityTest` checks the Java against it and
  `tools/tests/test_combat_preview_parity.py` checks the Python port against
  the same file (`-PupdatePreviewParity` rewrites it).
- `python3 -m tools.combat_preview sweep`: candidate sheets for a rig value
  (grid, zoomed strips of the region that differs, each candidate's rig file).
  `python3 -m tools.combat_preview diff <before> <after>`: before/after stills
  side by side with changed-pixel counts and boxes.
- `tools/release_gate.py`: the documented release checks as one command, with
  a jar rewritten by that run. `tools/bump_version.py <version>`: the version
  rule applied to its four files.
- `docs/ai-kb/36_new_combat_weapon.md`: the procedure for a third weapon in 22
  numbered steps. `tools/new_combat_weapon.py scaffold --from <weapon> --id
  <item> [--style <style> [--moves ...]] [--dry-run]` writes the renamed
  copies (weapon file, index entries, first-person rig, the style with every
  id renamed, `TODO(new_combat_weapon)` translations) and prints the other
  steps with their files; it writes no Java and no generator-owned file, and
  refuses, writing nothing, when an id or file is taken. `progress <weapon>`
  sorts the existing validators' and tests' findings into the playbook's steps
  and reports a copy that is still the template's as `PLACEHOLDER`.
- README: the Qingfeng first-person rig's `jar tf` line in both jar listings
  (found by `progress`; the spear's rig already had one).

### Validation

- `tools/release_gate.py`: all 42 steps passed, including 364 Java tests
  (0 failed, 0 skipped), 578 Python tool tests (13 skipped), the combat
  preview parity tests, `generated-resources-current` ("regenerating changed
  nothing"), and `./gradlew build` producing `myvillage-0.30.0.jar`, which
  the focused combat validator passed and all 42 README jar-listing patterns
  matched.
- `tools/new_combat_weapon.py`, rehearsed on a scratch copy: a new-style
  scaffold from the spear and a shared-style scaffold from Qingfeng loaded
  with no data issues, and `progress` reported the copied style, rig, and
  names as `PLACEHOLDER` and the unwritten steps as `MISSING` or `BLOCKED`;
  six kinds of bad request were refused with nothing written. Both shipped
  weapons report every checked step `DONE`. A third weapon was not authored
  through the remaining steps.
- Format 2 migration: the offline renders were byte-identical, 25 in-game
  first-person stills showed zero pixel difference, and combo damage was
  unchanged (spear 80 to 38.28, sword 80 to 44.18 target health).

## 0.29.0-fix1

### Fixed

- The Lingxiao Spear's first-person left arm was too thin (owner review). The
  off arm was drawn at the main arm's `thickness` (`0.42`), but its hand holds
  the shaft farther from the eye and its whole forearm shows, so it read as a
  stick. `rig.off_hand` takes an optional `thickness` (0.2 to 1.2, default the
  main arm's), and the spear's off arm is now `0.56`. Bone lengths stay the
  main arm's, so the grip point on the shaft and every pose are unchanged; the
  main arm, the third-person poses, and Qingfeng are untouched. Presentation
  only.

### Validation

- 355 Java tests (0 failed, 0 skipped), including the block's default, range,
  and that a thicker off arm keeps its shaft point and the main arm;
  `./gradlew build` produced `myvillage-0.29.0-fix1.jar`; the focused combat
  validator (which now checks the field) and the mod-item validator passed
  against it; strict OpenSpec validation of `add-lingxiao-spear` passed.
- Headless capture `spear-fix1-offarm` (25 first-person stills, capture skin
  with slim arms): the left arm is drawn at the new thickness in all five
  moves. Against the offline renderer the stills agree to at most 0.002
  silhouette XOR over union with 0 px tip, main-fist, and grip offset.
  Before and after, the candidate values (0.42, 0.50, 0.56, 0.62), and a
  wide-arm skin rendered offline are in
  `out/preview/lingxiao_spear/off_arm_thickness/`.
- The owner accepted the spear with the new thickness on 2026-10-02.
  `not_verified`: real keyboard and mouse play on this host, and a wide-arm
  skin in game.

## 0.29.0

The Lingxiao Spear, a second combat weapon, built on the 0.28.0 combat data
as its validation. Qingfeng's moves, timing, damage, rig, model, and generated
animation are unchanged; the fixes below change how it behaves in live play.

### Added

- `myvillage:lingxiao_spear` (凌霄枪): a two-handed spear on the diamond tier
  (8 attack damage, 1.2 attack speed), in `#minecraft:swords` and in
  `myvillage:main` after the swords, no recipe. Its 3D model (79 elements,
  fullbright head and inlays), 128x128 texture, 64x64 icon, wrapper model, and
  geometry contract come from the new `tools/gen_lingxiao_spear_model.py`.
- Combat style `myvillage:basic_spear` with five moves: 中平扎 mid thrust, 横扫
  sweep, 上挑 rising flick, 劈枪 overhead smash, 游龙突刺 dragon lunge. The
  thrusts use the `thrust` generator; the sweep (three per active tick), flick,
  and smash (four per active tick) use explicit samples that follow the posed
  spearhead. Weapon entry, index entries, translations, and the reused
  `myvillage:combat.sword.*` sounds.
- `player_animations/spear_combat.json`: two-handed third-person poses with a
  bladed guard; the leading hand stays on the shaft for the guard and the mid
  thrust and lets go for the sweep, flick, smash, and lunge.
- `combat/lingxiao_spear_first_person.json`, a first-person rig with both
  hands on the shaft.
- Optional first-person off-hand arm: `rig.off_hand` and the per-key
  `off_hand_slide`, `off_hand_roll`, `off_hand_elbow`, and `off_hand_hold`. It
  is drawn only while the off-hand slot is empty. Rigs without the block render
  as before.
- Optional geometry contract fields `off_hand_grip_center` and `trail`.
- `CombatWeaponItem`, the item class for every item with a combat weapon
  entry.
- `ClientCombatClock`, the client's own combat tick count, and
  `LocalSwingTimeline`.
- Data rules: explicit hit samples in non-decreasing tick order (Java loader
  and `tools/combat_data.py`); equal sample counts per active tick once any
  tick has several (`tools/combat_data.py`); every drawn frame of a cut's far
  end at or beyond the weapon's world-trail tip radius, per weapon, and no
  game-clock read in `client/combat` outside `ClientCombatClock` (validator).
- Capture tool: default views by weapon length (quarter views for weapons of
  1.8 blocks or more), cropped quarter-view sheets, `shot`, `view`, `motion`,
  `combo --camera back`, `combo --layout default|sweep|line`,
  `combo --tick-rate`, per-move hit attribution (`hits`, `hits_by_move`), the
  client's hit-stop and resync lines in the manifest (`fp_log`), an experience
  reset in the scene, and a record of the rig the client loaded.
- `docs/ai-kb/35_lingxiao_spear.md`: the spear, the two-hand rules, and what
  the validation found.

### Changed

- `tools/gen_sword_pal_anims.py` holds one pose table per style, each with its
  own weapon geometry, rules, cut paths, and output file, and can solve the
  left arm onto a shaft. Long-weapon checks cover the model's real corners
  against the ground and the skin's outer layers and the neck against the
  shaft. `sword_combat.json` is byte-identical.
- The first-person trail spans the contract's `trail` (else the blade). The
  world trail is sized from the contract (shoulder-to-grip reach plus grip to
  trail tip, and the trail span, at the model's third-person scale) instead of
  a 1.7-block cap and a clamped blade length; samples that share a tick are
  spread through it. The Qingfeng world trail draws exactly as before.
- Client combat timing maps a server tick onto the local clock once, when the
  START arrives. If the client's game time is k ticks off the server's at that
  moment, the whole move is drawn k ticks off (about −1 to +2 on the capture
  host) and the server's STOP cuts the last k recovery ticks or the next start
  is slewed; before, the next time packet corrected it mid-move as a visible
  skip.
- `tools/validate_sword_combat_foundation.py` checks every weapon's models,
  textures, model generator, and jar contents, the off-hand and trail fields,
  and the new data rules. `tools/validate_mod_items.py` requires
  `CombatWeaponItem` for every combat weapon. Fixture tests that used to skip
  now fail.
- Capture stills accept three grabs within 2 colour levels as stable, and a
  refused probe stops the wait at once with its reason.
- Messages no longer name the sword: "Cultivation combat enabled." /
  "已切换为修行战斗。", and "Combat hitbox debug particles enabled/disabled." /
  "已开启/已关闭战斗判定粒子。".

### Fixed

Each of these changes how the Qingfeng Sword behaves in live play as well as
the spear.

- First person: when the hit confirmation arrived after the rig's contact
  tick, the hit-stop started on a pose the weapon had already passed, so the
  weapon jumped back, held, and swept on (the horizontal cut by about 114 px
  at normal speed). The hit-stop now freezes the pose on screen, and the swing
  never moves backwards.
- First person: every landed hit costs durability, and the resent stack
  replayed the equip animation, so the weapon sank out of view for 5 to 6
  ticks after each hit. The re-equip rule is now vanilla's minus that replay:
  only a stack that differs in nothing but `minecraft:damage` keeps its place.
  A rename, an enchantment, another item, or another count still plays the
  equip animation; switching between two copies of the same weapon that differ
  only in durability no longer does.
- Every client combat timeline (first-person swing, chain prediction, impact
  freezes, the attacker's stop, the world trail, camera kicks, arm lag) read
  the client's game clock, which the server's time packet re-sets every 20
  ticks. A reset mid-move was read as swing time (a +2 reset skipped the swing
  two ticks). They now run on the client's own combat clock.
- Another player's figure and world trail stopped once per impact message
  instead of once per action, so a three-target sweep froze the attacker about
  two ticks longer and ran its trail backwards. They now stop once per action;
  each struck target still freezes and shudders.
- First person: only the first hit confirmation of an action starts a
  hit-stop, and a confirmation for an action that is no longer current is
  dropped instead of stopping the move on screen.
- A refused third-person probe now logs
  `PAL_SMOKE third_person rejected reason=<reason>`, and a refused ready-idle
  transition is logged once instead of every tick.

### Validation

- No change to `combat/session`, `combat/runtime`, `combat/network`, or the
  payload protocol (still `7`). `BasicSpearStyleTest` drives a real
  `CombatSession` through the five spear moves.
- Release gates on `26b99e9`: 354 Java tests (0 failed, 0 skipped);
  `./gradlew build` produced `myvillage-0.29.0.jar`; the focused combat
  validator passed against it; every README jar-listing line matched; the
  acceptance server and a standalone NeoForge 21.1.233 dedicated server on the
  packaged jar logged two styles, two weapons, ten moves, and protocol 7, and
  stopped cleanly; strict OpenSpec, the flying-sword validator, and the seven
  cultivation validators passed. The GuideME validator and its tests need a
  Python with PyYAML (as on `main`).
- Python suites: validator, data, and baseline 132 tests; generators 122;
  capture 71; mod items 23.
- Final headless capture (developer client, 960x540, software rendering):
  - Spear, default layout (one target in reach): all five moves from mapped
    clicks; the target went from `80.0` to `38.28` (per move `7.085`,
    `7.478`, `7.872`, `9.053`, `10.234`) in every run, at normal and quarter
    speed and from first person and F5 back.
  - Spear, sweep layout (2.5 blocks out, ahead and 45 degrees either side):
    the sweep hit all three targets. Line layout (2.5, 3.5, 4.5 ahead): the
    lunge hit all three, the sweep, flick, and smash two each, the thrust one.
  - Qingfeng: `80.0` to `44.18` as in 0.28.0; third-person stills identical to
    the previous capture and first-person stills within 2 colour levels, apart
    from the experience bar row.
  - One first-person hit-stop per move; the three-target sweep logged one
    started stop and two ignored confirmations. Every resync was −1.00 tick on
    a combo's first move; no game-clock reset was logged during a local
    action.
  - The offline first-person renderer matched 25 spear stills with at most
    0.18 % silhouette difference and 0 px tip offset.
- Forced clock changes: `/tick sprint 20` re-set the client clock by +20 as
  the spear's flick started and the swing played through it without a skip;
  `/tick freeze` and `/tick step` held and resumed the swing; a Nether
  teleport and a death mid-move stopped the action cleanly and the next run in
  the session played normally. Not shown in the game: a reset caused by server
  lag, a camera kick running through a reset, and a clean track of a +1 or +2
  reset mid-swing.
- Not verified: sound; a second client (another player's view of the moves
  and of one stop per action); real keyboard and mouse play; the flick's and
  smash's world trails seen from the side; foot sliding; frame rates on a real
  GPU; other skins, armour, and capes; the owner's verdict on the spear and on
  the changed Qingfeng behaviour.

## 0.28.0

Combat infrastructure. Qingfeng's five moves, timing, damage, and look are
unchanged.

### Added

- Bundled combat data under `data/myvillage/combat/`: `style/basic_sword.json`
  (timing, damage, hit shapes, steps, reactions, sound ids, hit-stop, camera
  cues), `weapon/qingfeng_sword.json` (item, style, first-person rig, geometry
  contract), and `index.json`. The files load from the jar at startup on both
  sides; unknown fields, duplicate keys, and broken timing invariants are
  startup errors that name the file and field. A datapack cannot override them.
- `CombatStyles`, a registry that resolves the style by held item and a move by
  id. Rigs and geometry load per weapon on resource reload.
- `/myvillage_pal_smoke third_person <move> <tick>` and `third_person release`:
  an exact full-body freeze probe. The smoke probes now use the style of the
  weapon in the main hand.
- `tools/combat_data.py`, the shared reader for the data files.
- `tools/combat_capture/`: session control, hot reload, per-move stills in first
  and third person, a mapped-click combo run with video and server-side target
  health, before/after comparison sheets, and a review page under
  `out/preview/combat_capture/`.
- `docs/ai-kb/34_combat_data_and_capture.md`.

### Changed

- `BasicSwordStyle`, the index-aligned camera arrays in `CombatCameraFx`, and
  every direct `ModItems.QINGFENG_SWORD` check in combat code are removed. The
  `Qingfeng*` first-person classes are renamed `FirstPersonWeaponAnimator`,
  `FirstPersonArmRenderer`, `FirstPersonArmIk`, and `FirstPersonArmModel`.
- `CombatImpactPayload` carries the move id instead of a move index. Payload
  protocol is `7`, so the client and server need the same jar.
- `tools/gen_sword_pal_anims.py` reads move timing from the style file. The
  generated `sword_combat.json` is byte-identical.
- `tools/validate_sword_combat_foundation.py` validates the data files and
  their consistency with animations, rigs, translations, and sounds. It holds no
  per-move numbers and no checks tied to class names; it rejects a named item in
  combat code.

### Validation

- A Java equivalence test compared the loaded style with the old constants,
  including every hitbox sample, before the constants were deleted; the values
  are now pinned in `BasicSwordStyleTest` and
  `tools/tests/test_combat_style_baseline.py`.
- 310 Java tests and the Python tool tests pass; the focused validator passes
  against the built jar. A standalone dedicated server loaded the data from the
  packaged jar.
- Headless capture of the game before and after (same probe, same camera): all
  75 stills (5 moves, 5 key ticks, first person and F5 back/front) match at a
  2% colour tolerance, and five mapped clicks took the target from `80.0` to
  `44.18` in both.
- Not verified: two-client behaviour after the protocol change, a weapon change
  during an action in a real client, real keyboard play, and sound.

## 0.27.1-fix1

### Fixed

- Walking with the Qingfeng Sword drawn in cultivation mode no longer freezes
  the legs. The third-person ready idle keys a planted stance (fixed leg
  angles, a 15 degree body turn and a hip drop), so the legs did not swing and
  the player glided. The PAL layer state now blends the legs back to vanilla
  walking by the vanilla limb-swing amount (planted below `0.05`, fully vanilla
  from `0.3`, at most `0.25` per tick in or out), and returns the body root's
  hip drop and turn to neutral while keeping its forward lean. Arms, torso,
  head and the sword keep the guard. It applies only during the ready idle and
  the mode entry; moves keep their authored footwork. Presentation only.

### Validation

- `LocomotionBlendTest` covers the thresholds, the ramp, the bone split, the
  blend, and the phases that allow it. A headless front-view capture of the
  local player showed the legs swinging while walking with the guard kept, and
  the planted stance returning after stopping. Remote players and real
  keyboard play are `not_verified`.

## 0.27.1

### Changed

- The Qingfeng Sword in hand is now a 3D element model of a jian: a thin
  double-edged blade with a raised ridge and a straight tapered tip, a guard with
  upswept wings, a wrapped grip, and a jade pommel. It replaces the extruded
  sprite, and its colors follow the old texture. `qingfeng_sword.json` is a
  `neoforge:separate_transforms` wrapper: `base` is the new
  `qingfeng_sword_3d.json`, while the `gui` perspective keeps the existing 2D
  `qingfeng_sword.png` icon.
- The display transforms of the 3D model are derived from the old sprite's
  poses, not hand-tuned. In third person the grip centre lands in the fist, and
  the blade keeps the direction the PAL moves were designed for.
- Third-person PAL grip compensation: PAL rotates `right_item` about the item
  origin, so at large item rotations the handle swung out of the fist. This
  happened with the old sprite as well. `tools/gen_sword_pal_anims.py` now adds a
  `right_item` position per key so the rotation pivots on the grip centre. The
  generator reads the 3D model's display transform and the geometry contract,
  and new self-checks bound the grip drift at keys and between keys.
  `sword_combat.json` is regenerated.
- The first-person arm is now an upper arm, forearm, wrist, and a separate fist.
  The handle crosses the fist diagonally, with the guard showing on the thumb
  side and the pommel below the little finger. The IK solves from the shoulder to
  the wrist, and the wrist bend stays within anatomical flexion and deviation
  limits. The arm's cross-section is thinner, so it takes up less of the screen.
- The first-person sword is aligned from the geometry contract and the baked
  model's own first-person display transform, which is undone at runtime. The
  hard-coded `GRIP_*` sprite offsets are gone, so the model's display values no
  longer move the grip.
- The rig file gains `rig.sword_scale`, optional `rig.arm` tuning (bone lengths,
  thickness, grip diagonal, follow-through), and per-key `grip_roll` (hand turn
  about the handle) and `elbow` (elbow swivel). All of them are interpolated like
  the other pose fields.
- The first-person trail takes its blade base and tip from the geometry contract.
  The world trail's blade length is the contract's blade length at the model's
  third-person display scale; without the contract it falls back to 1 block.

### Added

- `tools/gen_qingfeng_sword_model.py` (stdlib only, deterministic, with
  `--check` and `--report`). It writes the 3D model, its 64x64 texture
  `textures/item/qingfeng_sword_model.png`, the `qingfeng_sword.json` wrapper,
  and the geometry contract `assets/myvillage/combat/qingfeng_sword_geometry.json`
  (grip centre, handle, guard, pommel, blade base and tip, axes, in model pixels).
- `SwordGeometry` loads the contract with the swing rig on every resource
  reload (`F3+T`). A missing or invalid contract leaves Qingfeng on the vanilla
  hold.
- `FirstPersonArmLag`: presentation-only wrist secondary motion. The grip's
  recent path runs through an under-damped low-pass, so the arm trails a cut and
  follows through past the stop. It freezes with the hit-stop and fades out at
  the end of a move. A faint idle breath moves the neutral hold.
- Validator checks:
  - `tools/validate_mod_items.py` checks the `separate_transforms` wrapper, the
    2D gui icon, and the element model (bounds, rotations, faces, textures, UVs,
    hand display contexts, no `gui` display, no generated parent).
  - The focused validator checks the 3D model, texture, and geometry contract,
    the generator drift (`COMBAT_SWORD_MODEL_GENERATOR_DRIFT`), the display
    undo, the contract reload, the fist, wrist limits, grip roll, elbow swivel,
    wrist lag, the trail's contract blade points, and the world-trail blade
    length. It rejects reintroduced `GRIP_*` or hard-coded `BLADE_*` constants
    and requires the new jar entries and focused Java tests.

### Notes

- Owner feedback on 0.27.0 (2026-09-30): "自研的动作好一些了现在，但是握持这部分完全不行现在就像插入肉里的，非常僵硬。剑的建模也不太行可以优化一下。"
  This revision responds to it. Owner words on 0.27.1 (2026-10-01, after viewing
  the 0.27.0 vs 0.27.1 comparison page): "感觉271看上去可以"; then "暂时就这样".
- Presentation only: server authority, timing, hit windows, damage, steps, and
  the empty C2S payloads are unchanged. Payload protocol stays `6`.
- Lab station F (2026-09-30, `combat-lab/out/F`) observed in a physical client:
  the 2D hotbar icon with the 3D sword in hand and no missing textures; the
  handle through the fist in third person, staying in the fist during moves; and
  in first person, the fist on the handle with the guard visible. These are
  implementation evidence only.

## 0.27.0

### Changed

- Qingfeng combo timing: the one-slot buffer now opens at each move's active
  start, and a held click cancels recovery into the next move at `chainTick`
  `7/8/10/13/20`. Totals and active windows are unchanged. Without a held click a
  move still plays to its total. Move five cannot chain.
- Attacks face the view yaw. Body and head snap to it at start, and each chained
  move re-aims. A temporary `myvillage:combat_commit` speed modifier slows the
  attacker during the swing, and sprinting stops at start.
- Steps: every move now steps (`0.30/0.25/0.30/0.45/1.40` blocks, bound
  `(0, 1.6]`). The step is a server-decided motion impulse executed by client
  physics instead of a server-side move. It keeps the collision and support
  checks, adds target magnetism, and hits use the server-planned origin.
- Knockback: vanilla hurt knockback is replaced for our hits by each move's
  slide/lift/lateral reaction along the attack facing. Enchantment and attribute
  knockback still apply, scaled by knockback resistance.
- Hit-stop is per move (`1.5/2/2/3/4` ticks): a full freeze, then a creep, then
  catch-up to the server total, anchored at the rig's per-move contact tick.
- Trails are now thin alpha-blended ribbons (`SWORD_TRAIL_TRANSLUCENT`); the
  additive trail is removed. The vanilla sweep particle is removed, and crit
  sparks are cut to 3 per hit (6 on heavy moves).
- The first-person rig is re-authored with cubic and overshoot easing, per-move
  contact ticks, and 2-tick chain cross-fades.
- `sword_combat.json` is regenerated from the new `tools/gen_sword_pal_anims.py`
  (planted feet, cut directions that match the hitboxes, lunge on the step
  tick). `CombatAnimationController` cross-fades chains and holds stops briefly.

### Added

- Server target reaction (`ReactionDefinition`, `CombatReactionService`):
  - mobs freeze for the hit-stop, then are staggered for `9-16` ticks;
  - repeat stuns fall off, and bosses are exempt;
  - players are never frozen and get a 60% `myvillage:combat_stun` slow instead.
- `CombatImpactPayload` (S2C to the attacker and trackers: struck ids and
  contact points, no damage or health) drives client-side target freeze and
  jitter and remote attacker hit-stop.
- `CombatCameraFx`: attacker-local shake and kicks, scaled by the Screen Effect
  Scale and FOV Effect Scale options. It also cancels the slowness FOV zoom from
  the combat commit and stun modifiers.
- A complete first-person skin/sleeve arm on the pivot rig
  (`QingfengFirstPersonArmRenderer` with a two-bone IK). It never cancels the
  hand render event.
- `myvillage:blade_cut` particle with an original procedurally generated sprite
  (`tools/gen_blade_cut_sprite.py`).
- Sound event `combat.sword.impact_heavy` (heavy-hit layer) with bilingual
  subtitles, plus swing pitch variation. The swing sound now leads the active
  start by one tick.
- Focused validator checks for these invariants, including the chain
  invariants, the S2C-only impact payload, no player freeze, no additive trail
  or sweep particle, the FOV correction, the arm registration, and both
  generators' `--check` drift checks.

### Notes

- Payload protocol is now `6`. Both C2S combat payloads remain empty.
- On 2026-09-30, after watching 0.26.2 (lab station A) next to Epic Fight, the
  owner said "现在不太行看上去" and asked for an optimized A:
  "我要的是那种战斗真实动作游戏的感觉". The 0.26.2 swings were not accepted.
  There is no owner verdict on 0.27.0 yet.
- Lab station E capture (2026-09-30, `combat-lab/out/E`) observed the
  first-person arm, thin trails, blade-cut particles, target slide, move-five
  lunge displacement, camera roll on heavy hits, and third-person poses in a
  physical client. All other 0.27.0 real-client surfaces are `not_verified`.

## 0.26.2

### Changed

- Replaced the Qingfeng first-person curves with a tick-authored shoulder-pivot
  swing rig in `assets/myvillage/combat/qingfeng_first_person.json`. Each
  move's visible strike now covers the server active window, cuts show the
  blade's flat, and resource reload (`F3+T`) applies rig edits.
- Hid the segmented first-person skin/sleeve arm at the owner's request; a
  complete arm will be re-authored on the new rig.

### Added

- First-person 剑光 ribbon during each strike and a world-space ribbon along the
  move's hitbox samples for other players and detached cameras.
- Swing, thrust, hit, and heavy-hit sound events with bilingual subtitles,
  aliased to vanilla attack sounds through `sounds.json`.
- Post-damage hit sound plus crit and sweep particles, and an attacker-only hit
  confirmation that triggers a short client hit-stop ending on the server total.
- `/myvillage_pal_smoke first_person <move> <tick>` and `release` to hold one
  first-person frame for review.

### Notes

- Payload protocol is now `5`: attack starts carry the frozen server facing yaw
  and the new hit confirmation carries only attacker id, revision, and hit
  count. Client C2S payloads remain empty.
- Owner verdict on the revised swings, trails, sounds, and hit-stop is pending.

## 0.26.1

### Added

- Added the diamond-tier `myvillage:xuanyue_zhenshan_sword`,
  `myvillage:chilian_lihuo_sword`, and `myvillage:qingxiao_liuyun_sword` with
  independent 64x64 transparent textures derived from the supplied reference
  art, handheld models, bilingual names, creative-tab entries, and vanilla
  sword-tag membership.

### Fixed

- Replaced the three reference-derived textures' partially transparent edge
  pixels with neighboring solid sprite colors, leaving only fully transparent
  background pixels and fully opaque sword pixels.

### Notes

- The three swords currently use ordinary diamond-sword behavior. Their names
  and reference art do not imply recipes, elemental abilities, or Qingfeng's
  PAL-backed five-move combat integration.

## 0.26.0

### Added

- Added the independent diamond-tier `myvillage:qingfeng_sword`, bilingual
  resources, recipe, sword tag, creative-tab entry, original pixel texture,
  Item Contract, and focused resource validation.
- Added persistent server-owned vanilla/cultivation combat preference with a
  configurable default-`R` toggle, bounded input-only payloads, and lifecycle
  synchronization.
- Added the complete five-move Basic Sword cultivation style with centralized
  timings, server-tick combo and recovery state, per-move swept hit geometry,
  wall and target legality checks, server-owned fifth-move stepping, damage and
  enchantment hooks, bounded debug particles, and remote-player synchronization.
- Integrated Player Animation Library 1.1.4 from an externally installed local
  jar with seven original full-body animations, ready/enter transitions, local
  prediction correction, and strict client/common isolation.
- Added a separate Qingfeng-only first-person held-item layer with five bounded
  move curves, authoritative elapsed-time correction, and clean pose recovery;
  it does not move the camera or add client gameplay authority.
- Added an independent first-person local skin-arm and sleeve layer that shares
  the corrected five-move item frame and neutral cultivation hold, supports
  wide/slim skins, leaves the ordinary item pass intact, and keeps PAL
  full-body first person disabled.
- Added a MyVillage-owned segmented first-person joint viewmodel with an
  invisible shoulder driver, visible forearm and hand, a screen-edge elbow
  connector, authored shoulder/elbow/wrist tracks, and right/left grip
  correction; it copies no Epic Fight or GeckoLib code or assets.

### Fixed

- Restored visible first-person response for intercepted cultivation attacks
  with predicted five-move held-item playback and a client-only fallback swing;
  neither sends a vanilla attack packet nor changes server authority.
- Revised the first-person five-move curves after owner feedback with exactly
  20 percent more displacement and earlier wind-up/later strike and recovery
  keyframes, preserving every server move duration and hit window; this fixed
  factor is retained as history rather than the current viewport contract.
- Replaced both rejected complete-arm revisions: separately damped rotation had
  split the hand from the handle, while the later pivot-locked cuboid exposed a
  whole arm floating in the middle of the screen. The segmented chain now pins
  the distal hand to the grip through forward kinematics, connects its elbow to
  the screen edge, omits internal caps, and compacts to `0.45` around the grip
  during active motion without changing server authority.
- Superseded fixed-factor-only first-person amplification with per-move viewport
  calibration targeting a temporal sword-and-arm envelope of at least half one
  screen axis, central-band entry, and no lower-right confinement under the
  `960x540`, FOV-70 reference view, without changing server timing or authority.

### Notes

- PAL remains a required separately installed client-and-server dependency and
  is not shaded, unpacked, copied, or distributed in the MyVillage jar.
- PAL 1.1.4's third-person-model first-person path clipped with this animation
  set, so PAL body arms/camera remain disabled; the dedicated held-item layer
  supplies first-person combat animation instead.
- Automated gates and bounded two-client evidence are recorded; the Qingfeng
  texture, animation feel, and complete owner-led gameplay ledger remain pending
  explicit manual acceptance.

## 0.25.1-fix1

### Fixed

- Changed MyVillage's default stop-meditation binding from `G` to `X`, leaving
  GuideME's default `G` item-index hotkey unreserved.
- Kept the integration free of conflict-specific input logic: MyVillage adds no
  GuideME-specific key interception, remapping, or automatic saved-binding
  migration. The pre-existing ordinary screen guard is unchanged. Existing
  clients that saved `G` must reset or rebind Stop Meditation once in Controls.

### Validation

- Recorded the accepted GuideME UI review and the historical 0.25.1 global-G
  failure separately. Post-fix GuideME `G` behavior remains `not_verified`
  until repeated on the fixed client artifact.

## 0.25.1

### Added

- Added the data-driven `myvillage:cultivation` GuideME guide with three Chinese
  default pages, complete path-matched English translations, live configurable
  key displays, and indexes for the initiation steles and spirit-stone resources.
- Added the one-stack `myvillage:cultivation_handbook` item, which opens the guide
  through GuideME's client API without adding a MyVillage network payload.
- Added root-source `runGuide` live preview plus focused dependency, content,
  resource, documentation, release, and practical-jar validation.

### Changed

- GuideME 21.1.17 is now a required client-and-server dependency, resolved from
  Maven Central for development and installed separately from the MyVillage jar.
- The first handbook slice documents the complete released initiation,
  meditation, progress/stability, lifespan, and deterministic advancement loop
  through the Qi Refining IV ceiling.

### Notes

- The handbook reuses GuideME's base book model; custom art and the known-bad
  GuideME 21.1.17 custom-color path remain outside this compatibility slice.
- Language rendering, navigation/search, item-index jumps, component/model
  appearance, live reload, key remapping, handbook reopen behavior, and existing
  gameplay regression remain pending real-client acceptance.

## 0.25.0

### Added

- Upgraded cultivation profiles to schema v3 with server-owned spiritual
  affinity, default value `10`, explicit v1/v2 migration, and lossless
  preservation of the now-inert legacy meditation reserve.
- Added Profile and Meditation tabs to H. The Meditation tab exposes normal,
  spirit, stop, and advancement buttons that reuse the bounded V/B/G/N action
  payload without accepting client-authored rates, costs, targets, or results.

### Changed

- Normal meditation now grants the current spiritual-affinity value every ten
  eligible ticks. Spirit meditation grants a fixed `50` progress per ten ticks
  and directly consumes the current stage's complete low-grade-spirit-stone
  batch: sensed/Qi I `1`, Qi II `2`, and Qi III `3`.
- Revised released target-layer thresholds to `1000`, `1100`, `1200`, and
  `1300` for advancement into Qi I through Qi IV. Stability stays locked until
  progress is full, then gains current affinity per ten ticks in either mode up
  to stage caps `500/550/600/650` without a stone cost. Successful advancement
  retains integer-floor half of current stability; mastery remains `10` per
  configured cultivation year, and Qi IV remains the release ceiling.

### Notes

- H-tab layout, button interaction, exact ten-tick progress and post-cap
  stability gain, multi-slot stone consumption, and advancement halving remain
  pending real-client acceptance.

## 0.24.0

### Added

- Added low-grade spirit stones plus stone/deepslate spirit-stone ores with
  iron-tier harvest rules, Silk Touch/Fortune loot, three-layer Overworld ore
  generation, bilingual assets, and deterministic resource validation.
- Upgraded cultivation profiles to schema v2 with explicit v1 migration,
  persistent lifespan consumption and meditation reserve, realm-owned maximum
  lifespan, a shared active-player calendar, configurable time scale, relative
  lifespan warnings, and non-lethal exhaustion.
- Added one server-authoritative V/B/G/N session manager for normal meditation,
  spirit-stone meditation, stopping, and deterministic advancement. Basic
  Breathing settles in 100-tick batches, spirit stones fund retained reserve,
  progress is stage-local and capped, and Qi-sensed through Qi III can advance
  one stage at a time to the Qi IV release ceiling.
- Extended the read-only H profile with capped progress, reserve, calendar,
  lifespan, session, and advancement status. Added protocol-v2 payloads,
  focused Java/Python tests, strict OpenSpec coverage, and a documented
  real-client acceptance ledger.

### Notes

- Spirit-stone appearance/distribution, real-client controls and interruption
  feel, H-screen layout, multiplayer time behavior, and in-game advancement
  remain pending manual acceptance. Lifespan exhaustion does not kill or reset
  the player, and Qi IV+, Foundation advancement, pills, facilities, and
  reincarnation remain deferred.

## 0.23.0

### Added

- Added deterministic one-time spiritual-root awakening from the Overworld
  seed, player UUID, and the sorted current spiritual-element ids and
  `awakening_weight` values. The fixed generator selects one through five
  distinct elements and produces positive affinities totaling exactly `10000`.
- Added separate `myvillage:spirit_testing_stele` and
  `myvillage:technique_inheritance_stele` blocks and BlockItems. The first
  atomically installs the root plus `mortal_qi_sensed`; the second evaluates the
  current `basic_breathing` definition and learns it at mastery `0` without
  resetting existing mastery.
- Added `awaken`/`juexing` and `initiate`/`rumen` under both cultivation command
  roots, complete bilingual resources, focused Java/Python validation, and
  dedicated-server handoff coverage. The H profile remains read-only and the
  cultivation snapshot remains clientbound-only.
- Kept meditation, technique execution, spiritual-power recovery, cultivation
  gain, mastery growth, qi-refining advancement, root quality/reroll, worldgen,
  and profile schema changes outside this release.

## 0.22.2-fix1

### Fixed

- Fixed the cultivation profile screen calling the vanilla `Screen#render`
  background-blur pass after drawing its panel. The screen now keeps its owned
  translucent backdrop while rendering the player face, text, bars, and
  dividers without post-process blur.

## 0.22.2

### Added

- Added `/myvillage xiulian` and complete pinyin aliases for every cultivation
  inspection and mutation command. Both cultivation roots accept both English
  and pinyin subcommands and share the same arguments, registry suggestions,
  handlers, permission boundary, atomic service mutations, and synchronization.
- Added command-tree equivalence tests and synchronized the alias contract in
  the cultivation command spec, technical notes, README, and agent guidance.

## 0.22.1

### Added

- Added a configurable `H` key binding and non-pausing, read-only personal
  cultivation profile screen for testing synchronized realm, stage, progress,
  stability, spiritual power, spiritual-root affinities, learned techniques,
  grades, categories, mastery, and profile schema version.
- Added synchronized display colors for the five shipped spiritual elements and
  English/Chinese screen translations. The panel reads only the owning-client
  snapshot and sends no cultivation mutation payload.

## 0.22.0-fix2

### Fixed

- Prevented a descending flying-sword vehicle from forwarding its accumulated
  fall distance to the mounted player when it touches a solid block. Collision
  remains active and ordinary player fall damage outside a mounted sword is
  unchanged. The owner confirmed the damage-free mounted touchdown in game on
  2026-07-12.

## 0.22.0-fix1

### Fixed

- Smoothed received server position and yaw snapshots on the client so the
  mounted flying sword no longer snaps directly between tracking packets. The
  server remains authoritative, and the client still sends only key-state input.
- Corrected the vanilla item-render transform so the horizontal placeholder
  sword follows the entity yaw with its blade tip pointing forward instead of
  stacking the parent model's `FIXED` transform. The owner confirmed both the
  riding stability and blade-tip direction in game on 2026-07-12.

## 0.22.0

### Added

- Added `myvillage:rideable_flying_sword` as a stack-size-one functional item
  and transient one-player vehicle. Right-click toggles server-owned summon,
  mounting, and recall; W/S, A/D, Space, and Shift send only a six-bit input
  intent while the server computes bounded collision-aware flight, hover drag,
  yaw, fall safety, singleton ownership, and lifecycle cleanup.
- Added a client-only vanilla item-model renderer, English/Chinese names, a
  `32x32` placeholder texture, focused protocol/resource validation, usage
  documentation, and dedicated-server startup coverage. In-game control,
  multiplayer, collision, cleanup, and appearance review remain pending.

## 0.21.0

### Added

- Added `myvillage:simple_fox` with vanilla fox model/AI inheritance, a spawn
  egg and translations, intentional empty loot, taiga natural spawning, and
  deterministic `48x32` UV provenance. Focused validators, the build, and
  dedicated-server startup pass, followed by accepted in-game client validation
  on 2026-07-12. This path adds no GeckoLib, paid GPT image call, or custom audio.

## 0.20.0

### Changed

- Rebuilt the existing `pagoda_001..003` cultivation landmarks as three
  deterministic large profiles: compact five-storey, broad five-storey, and
  slender seven-storey. Every occupied level now has a projecting two-band
  eave, bracket rhythm, lifted corners, framed openings, stepped taper, a
  pyramidal crown, and a taller finial; ground colonnades stop at the first
  eave instead of rising as uninterrupted posts to the crown.
- Expanded the two larger pagoda resources and synchronized their measured
  footprints across Python town/sect planning and Java runtime mirrors while
  keeping the compact profile inside the fixed civic-core parcel.
- Added pagoda-specific geometry/provenance metrics, a three-profile
  distinctness and NBT-hash gate, focused tests, updated previews/docs, and a
  new owner visual-review handoff. `candidate_006` remains calibration-only;
  no external structure asset is copied.

## 0.19.1

### Added

- Added the second external-reference-driven generated structure slice:
  `ganlan_stilted_house_001..002`, derived from the `candidate_005` Ganlan /
  干栏式 breakdown as original generator output rather than copied source
  assets. The slice adds a dedicated settlement group/style profile, raised
  living floor, support-post grid, open underside, raised veranda/entry stair,
  deep-eave gable roof, wet-ground context, deterministic provenance and
  stilt-cue validation, focused tests, NBT/place/gallery resources, preview
  handoff coverage and KB/README command docs. The owner accepted the narrow
  generated visual slice on 2026-07-11; broader village/worldgen work remains
  out of scope.

## 0.19.0

### Added

- Added the first external-reference-driven generated structure slice:
  `chinese_huipai_mansion_001..002`, derived from the `candidate_003`
  Hui-style breakdown as original generator output rather than copied source
  assets. The slice adds a dedicated settlement group/style profile, closed
  facade + stepped 马头墙 registry vocabulary, deterministic validation for the
  门堂 → 天井一 → 享堂 → 天井二 → 寝堂 sequence plus paired side-wing enclosure,
  an expanded review-lot footprint with clear inter-building gaps and scaled-up
  hall/side-wing/height massing, sample NBT/place/gallery resources, focused
  tests, KB/README command docs, and report provenance that keeps the
  implementation partial until owner visual verdict.

## 0.18.4

### Added

- Added the visual-reference structure pipeline
  (`add-visual-reference-structure-pipeline`): a CRAFT-routed middle layer that
  decomposes a visual reference or `research/source_structures/` candidate into
  a Reference Breakdown Contract with four typed buckets
  (`direct_component`, `atomic_component`, `generative_grammar`,
  `calibration_only`), explicit downstream routes, source-fact preservation,
  and a pending human verdict. Shipped the KB note
  (`docs/ai-kb/20_visual_reference_structure_pipeline.md`), the contract JSON
  schema, the `candidate_003` Hui-style worked-example breakdown card, the
  lightweight validator (`tools/check_reference_breakdown.py`), and the
  dedicated `genops/pipelines/reference-decomposition.full.yaml` pipeline with
  Commander routing cues. Decomposition is planning evidence; it routes
  downstream work and does not implement it.

## 0.18.3

### Added

- Added CRAFT front-door governance for high-impact project work:
  `openspec-change.full` now routes OpenSpec proposal/change authoring through
  GenOps task ownership, and `tools/genops/check_frontdoor.py` checks protected
  changed paths against run evidence.

### Changed

- GenOps run manifests and summaries now expose per-task artifact indexes so
  Commander handoffs can report run id, pipeline, worker/task ownership,
  artifacts, gates, human verdict state, and next decision.
- Documented that the pre-existing `add-visual-reference-structure-pipeline`
  proposal must be re-entered through CRAFT before implementation continues.

## 0.18.2

### Changed

- Prepared the accepted `path-surface-zoning` water-court pass as 0.18.2:
  regenerated the six Jiangnan mansion NBTs with the reference-style 水亭,
  removed the separate pond-side shed, kept the new visual validators/docs
  current, and rebuilt the packaged jar.

## 0.18.1

### Added

- **Chunky acceptance automation** (`add-chunky-acceptance-automation`):
  staged in-game acceptance now covers the isolated server + Chunky + RCON
  lifecycle, coordinate-addressable `/myvillage ...at` command smoke, full
  optional-mod server startup/cases, and natural `myvillage:sect` worldgen via
  `/locate structure` plus bounded Chunky generation.
- Added RCON/console-safe coordinate commands:
  `/myvillage placeat`, `/myvillage galleryat`, `/myvillage townat`,
  `/myvillage sectat`, and `/myvillage sectat worldgen`.

### Changed

- The acceptance script now verifies staged optional-mod jar ids and mandatory
  jar dependencies from `exmod/mod_jars.zip` before full-modset startup. The
  full run writes `reports/chunky_acceptance_report.json`; Chunky remains an
  acceptance-only jar and is not packaged into MyVillage.
- Visual acceptance prep now has `tools/write_visual_acceptance_report.py`,
  which links representative offline preview PNGs with the latest Chunky command
  and worldgen targets in `reports/visual_acceptance_report.json/.md`.
- `validate_generated_structures.py` treats the standalone `hero_rockery` review
  fragment as a non-building landscape specimen for key-building-block checks.
- Replaced the pond-side water pavilion with a reference-image-style heavy
  scenic pavilion: raised stone base, dark wooden deck, heavy timber posts,
  railings, lattice/trapdoor bracket details, hanging lanterns, broad dark-oak
  double eaves, and grey stone roof ornaments; removed the separate right-side
  pond `waterside_gallery` shed.
- Completed the final `path-surface-zoning` mansion review repairs: 水边廊 and
  主院 抄手游廊 now render as 3D galleries with floors, columns, balustrades, and
  roofs; 后院 and 花园 are separate bands, the 绣楼 stays in 后院, and the 主院
  heart remains grass.
- Tightened the waterside mansion garden after low-angle visual review: 水边廊 is
  now one short straight run instead of the whole noisy shoreline, avoids the
  pond/rockery/bridge, and bridge/gallery clear-water lanes are kept free of
  lily-pad clutter. `validate_mansion` now reports
  `waterside_gallery_clutter:*` and `pond_lily_clutter:*` for regressions.
- Fixed the focused 水亭 composition: `garden_pavilion` now chooses a dry
  pond-bank footprint adjacent to pond water instead of the stale west-band
  placement, and `validate_mansion` reports
  `garden_pavilion_detached_from_pond:*` if it drifts away again.
- Added `tools/render_structure.py --target X Y Z` for focused Chunky look-at
  renders when the scanned bbox center is not the visual subject.
- Replaced the water pavilion's raw stair ridge cap with a low two-step slab
  roof so focused low-angle renders no longer show a floating default-stair
  artifact above the 亭.
- Lightened the pond closeup wood forms after visual review: the 水亭 now uses
  fence posts with a thin explicit stair eave and one center cap, and the short
  水边廊 uses compact posts with roof limited to the post line so it stays open
  instead of reading as a 3x2 shed.

## 0.18.0

### Added

- **江南大宅/庭院 路径表面分区** (`path-surface-zoning`): the courtyard/mansion
  ground + path layer is now a two-axis surface model so the path reads as
  **three routes with six surfaces** instead of one flat gravel stripe.
  - Material follows the **zone** (six zones → six style slots): the formal
    axis is 青石 (`PATH_FORMAL` / `smooth_stone`), the 天井/院心 ring is 灰砖
    (`GROUND_YARD_HEART`), 廊下 is 木石 (`PATH_GALLERY` / `oak_planks`), 夹道 is
    砖 (`PATH_ALLEY`), the garden tour is 苔石 (`PATH_TOUR` /
    `mossy_stone_bricks`), and the waterside edge is 石阶 + 木板桥
    (`PATH_WATERSIDE`).
  - Shape follows the **route**: the formal/service backbone keeps the
    single-source shortest-path tree (`PATH_FORMAL`); the garden tour is a
    winding waypoint polyline (假山南 → nearest pond shore → 亭) via
    `_route_tour_path`, each segment a single-source shortest path with an
    obstacle set forcing any segment that would cut through the rockery/pond
    to route around it.
  - Three new path termini make the routes real: the `moon_gate_passage`
    (月洞门 穿墙通道 through the garden screen wall, the formal↔tour material
    boundary), the 水边廊 (shoreside `covered_gallery` variant along the pond
    shore), and the `service_house` archetype (仆役房 along the 倒座 夹道, a
    mandatory path endpoint).
  - The cross-pond 汀步 spike-row (deleted `rockery_block` cells) is replaced
    by a flat slab bridge (`oak_slab`/`spruce_slab`) spanning the pond's
    narrowest crossing to the 亭/island — the spike problem was the block, not
    the crossing.
  - Each family realizes only the zones it has space for: `chinese_mansion`
    gets the full vocabulary; `chinese_courtyard` gets formal + heart + gallery
    + alley; embedded `small_courtyard` (in `cultivation_town`) gets formal +
    heart. Styles that did not adopt the slots (`cultivation_town.json`,
    `cultivation_sect.json`) fall back to the legacy ground/path tiles and
    regenerate byte-identical.
- New `validate_mansion` checks: `surface_zone_material:<zone>:<cell>` (each
  cell's block matches its zone's slot primary), `tour_segment_disconnected`
  (every tour waypoint segment is a connected single-source tree), and
  `waterside_bridge_incomplete` (the slab bridge spans both shores). Added
  `docs/ai-kb/16_path_surface_zoning.md`.

### Changed

- Regenerated `chinese_mansion_001..006.nbt` (Stage 4: the slab-bridge
  `PATH_WATERSIDE` writer + the three new validators). `chinese_courtyard_*`
  and embedded `cultivation_town_*` regenerate with the four-zone ground +
  formal/heart path subset. `cultivation_sect_*` and `medieval_*` stay
  byte-identical (byte-stability guard extended in
  `tools/buildgen/tests/test_chinese_courtyard_regression.py`).

## 0.17.0

### Added

- **江南大宅 enclosure-planning skeleton** (`rebuild-mansion-enclosure-plan`):
  `chinese_mansion_001..006.nbt` now use a building-enclosure model instead of
  fixed z-band placement. The south entrance is a real `gate_house`
  through-building, not a carved wall hole; each mansion role gets a form-rule
  door wall (倒座 north, 厢房 inward, 敞厅 south, 楼阁 north), and every
  door-front is routed into the gravel backbone from the gate-house inner
  opening.
- Added regression coverage for the orientation mechanism, gate-house
  perimeter sealing, derived-yard contiguity, inner-gate adjacency, and
  non-mansion byte stability.

### Changed

- Regenerated the six shipped `chinese_mansion_*` structures under the full
  profile. `chinese_courtyard_*`, embedded small courtyards, cultivation town,
  cultivation sect, and medieval structures remain on their existing planners;
  propagating the enclosure skeleton to those courtyard families is tracked as
  follow-up work.
- `validate_mansion` reports `facing_per_slot` and `door_reachable_rate` and
  enforces gate-house presence, role-facing invariants, door-on-path, and
  derived-yard adjacency in addition to the preserved grid and voxel checks.

## 0.16.2-fix1

### Fixed

- **山顶树恢复微缩比例**：不再用完整草方块、原木和树叶替代源雕塑；`g/t/l`
  微体素现在直接烘焙进峰顶 hero 模型，形成带倾斜树干、横枝和不对称云片树冠的
  半格高盆景。碰撞仍只取岩石，不影响峰顶通行。
- **泉水真正从山里流出**：移除固定在山体外侧的 `rockery_cascade` 方块列，
  改为将源雕塑的 `w` 微体素作为带水色的透明模型几何烘焙。生成器保证泉洞、
  贴岩阶流和山脚内池构成一个六向连通水体；真实流体仅保留在封闭山脚水池，
  不会扩散淹山。
- 重生成独立 `hero_rockery` 与六座 `chinese_mansion_*`，更新字节稳定基线；
  新增微缩植被、连续水路、无整方块树及无外置瀑布的回归断言。

## 0.16.2

### Changed

- **重塑 hero 太湖石假山的形态与水景**：`docs/rockery_compressed.json` 改由
  参数化生成器 `tools/buildgen/gen_hero_rockery_sculpt.py` 重新雕刻，按参考图
  `docs/mt.png` 做成层叠收分的太湖石（石为主、青苔点缀），并配合泉自山体内
  涌出、沿台阶跌落的细瀑与嵌入山脚、与山体相连的水池。新增离线微体素预览工具
  `tools/buildgen/preview_voxel_field.py`（直接渲染 48³ 体素，便于在不进游戏时
  比对参考图）。

### Fixed

- **修复假山被流水淹没的问题**：原 hero 路径把山顶出水口与山脚 cell 设为
  `waterlogged=true`，而含水方块本身是会向相邻空气扩散的水源，导致水流如
  “蓝色帐篷”般盖住整座假山并溢成护城河。改为完全不使用 waterlog；可见水景仅由
  封闭水池（source）+ 非流体 `rockery_cascade` 细瀑构成，泉眼由烘焙进岩体模型的
  石窟表现，确定性且不再泛滥。
- hero cell 数由 19 增至 20；六个 `chinese_mansion_001..006.nbt` 与独立
  `hero_rockery` 片段已重新生成并通过结构 / voxel-walkability / fallback 校验，
  字节稳定基线同步刷新。

## 0.16.1

### Added

- **Hero 太湖石假山 (`add-hero-rockery`)**：将
  `docs/rockery_compressed.json` 的 48³ 微体素雕塑切成 19 个可堆叠的
  `rockery_block` cell，形成一座 3×3×3 实体假山；石/苔材质按微体素分别
  烘焙，并增加真实水池、水浸山脚与峰顶出水口、无碰撞细瀑
  `myvillage:rockery_cascade`、峰顶草木和可站立亭基。
- 新增独立验收结构与命令 `/myvillage place hero_rockery`；六个
  `chinese_mansion_001..006.nbt` 已重新生成并通过 3D voxel-walkability
  校验。

### Fixed

- 修复江南大宅假山由二维 heightfield 每格仅放一个 block，导致整体呈现为
  1–2 格高“尖刺阵列”而非山体的问题。通用 codebook 假山路径保持不变，
  hero 路径改为固定、字节稳定的三维堆叠 cluster。

## 0.16.0-fix2

### Fixed

- **中式宅邸院子地板被沙砾铺满**：`_route_complete_path` 原本把多源 BFS
  的整个可达集合都铺成 `GROUND_PATH`（gravel），覆盖了
  `_place_yard_ground` 写的露天草地 (`GROUND_YARD_OPEN`) 与屋檐下石砖
  (`GROUND_YARD_UNDER_EAVE`)。改为只铺**最短路径骨架**——以街门入口为
  单一源点的 BFS 前驱树，对每个 endpoint（各门/水井/花圃/月台）沿前驱
  回溯到街门，取并集。骨架外的格子保留其地面材质，院子恢复以草地为主、
  gravel 路径为辅的自然形态。
  - 复核：多源 predecessor 树不可用作骨架——所有 endpoint 都是源点，相邻
    endpoint 互相回溯，骨架退化为不相连的零散点，既不穿过台基 (plinth)
    边界也不放台阶，导致主院门 `voxel_unreachable_door`。必须用街门入口
    单源，骨架才会从外院穿过 plinth 到达主院门，台阶生成器
    (`_place_band_transition_stairs`) 才能在边界放 `stone_brick_stairs`。
  - 影响：`chinese_courtyard_*`、`chinese_mansion_*`、`cultivation_town_*`
    （内嵌小庭院）NBT 重新生成；`cultivation_sect_*` 与 `medieval_*` 不受
    影响（字节稳定，回归测试 `test_chinese_courtyard_regression.py` 47 个
    NBT 哈希不变）。
  - spec：`courtyard-path-network/spec.md` 同步——多源 BFS 现仅用于
    `endpoint_unreachable` 可达性校验，骨架改由街门入口单源 BFS 产生。

## 0.16.0-fix1

### Fixed

- **服务器启动崩溃 → 所有指令不可用**：将 `SectStructures` 的 `StructureType` /
  `StructurePieceType` 注册从 `DeferredRegister`（依赖 `RegisterEvent` 时序）改为
  直接 `Registry.register()`（在 mod 构造函数执行期间、注册表解冻后立即写入
  `BuiltInRegistries`），消除 `Unknown registry key: myvillage:sect` 崩溃。

## 0.16.0

### Added

- **江南大宅 (chinese_mansion) compound family — 3-进 大宅** (`rebuild-jiangnan-mansion`).
  Six new NBTs `chinese_mansion_001..006.nbt`, each a full 3-进 compound:
  street gate → 照壁 (照壁侧立 off-axis) → 前院 → 仪门 → 主院 (敞厅 + 正房 on
  台基 plinth) → 二门 → 后院 (绣楼/藏书楼 tower_house ×1 or ×2) → 花园 (假山 +
  水池 + 亭 + 汀步). Variant axes: `courtyard_size ∈ {small, medium, large}`,
  `gate_form ∈ {recessed, flush, paifang}`, `garden_scale ∈ {small, large}`,
  `tower_count ∈ {1, 2}`, `main_bays ∈ {3, 5}`. In-game commands:
  `/myvillage place chinese_mansion_001` … `_006` and
  `/function myvillage:gallery/chinese_mansion`. New style profile
  `chinese_mansion.json` adds slots `FACADE_OPEN`, `GARDEN_PATH`,
  `ROCKERY_STONE`, `GARDEN_PAVEMENT`, `POND_STONE`.
- **假山 (garden_rockery) + 水池 (garden_pond) parcel nodes.** 假山 uses
  `myvillage:rockery_block` (new self-namespace block, registered in
  `ModBlocks.java` / `RockeryBlock.java`) with `variant`, `facing`, and
  `moss_level` blockstate properties and per-variant `VoxelShape` for partial
  climbability. 水池 uses freeform value-noise shoreline with
  `minecraft:water` at y=-1.
- **`open_hall` archetype (敞厅).** Front facade resolves through `FACADE_OPEN`
  slot (columns + open eave, no full-height front wall). Placed on main-yard
  台基 plinth in the mansion compound.
- **`tower_house` archetype (绣楼/藏书楼).** Two-story sub-building via
  `multi-story-massing` (`stories=2`, floor slab, stairwell, per-story facade
  band). Placed off-axis in 后院.
- **`garden_pavilion` archetype (亭).** Four-column standoff with
  `chinese_round_ridge` roof. Placed on or near 假山 peak in the 花园.

### Fixed

- **Voxel-walkability validator added** (`rebuild-jiangnan-mansion`). Both
  `validate_compound` (一进 四合院) and `validate_mansion` (三进 大宅) now run
  a 3D BFS (`_voxel_walk_bfs`) from the gate-entry standable column. All
  building door positions and path endpoints must be reachable. New error codes:
  `voxel_unreachable_door:<archetype>`, `voxel_unreachable_endpoint:<x,z>`,
  `voxel_step_cliff:<a>-><b>`, `voxel_blocked_by_solid:<x,z>`. The
  `plinth_edge_missing_stair` check now fires only when `plinth_h >= 2`
  (Δy=1 is a free Minecraft autostep).
- **照壁侧立 — screen wall moved off-axis in `chinese_courtyard`.** The 影壁 now
  stands at `axis_x ± offset` (照壁侧立 form, `meta.form = "jingbi"`), leaving
  the central path clear. The old on-axis placement blocked the player walking
  from the gate to the 垂花门 without detour. **Breaking (NBT regeneration):**
  `chinese_courtyard_001..006.nbt` regenerate with the off-axis 照壁.
- **垂花门 passage expanded to ≥3 cells.** Both 一进 inner gate (垂花门) and
  mansion inner gates (仪门, 二门) now open at `axis_x-1`, `axis_x`, `axis_x+1`
  minimum.

### Changed (internal, no gameplay change)

- **Courtyard ground + path layer rebuilt** (`fix-courtyard-ground-walkability`).
  The shipped `chinese_courtyard_NNN.nbt` files were structurally correct 一进
  plans but practically unwalkable: the yard floor was AIR outside a 1-cell
  gravel strip (the player fell through), the path stopped at the 正房 leaving
  厢房 / 倒座 / 井 / 鱼缸 / 种植 unreachable, and the plinth edge was a 2-block
  jump. Two new data-driven passes fix all three: `_place_yard_ground` fills
  every non-building cell with 露天 `grass_block` / 屋檐下 `stone_bricks`
  (courtyard-ground-layer spec), `_route_complete_path` runs a multi-source BFS
  from every door + water + planting + moon-platform endpoint to write one
  connected `GROUND_PATH` network (courtyard-path-network spec), and
  `_place_plinth_stairs` drops a single `stone_brick_stairs` at each plinth
  boundary. The same fix applies to the embedded small-courtyards in
  `cultivation_town_NNN.nbt`. **Breaking (NBT regeneration):** the 6
  `chinese_courtyard_*` and 6 `cultivation_town_*` NBTs regenerate with new
  content (same filenames); `cultivation_sect_*` and `medieval_*` stay
  byte-stable. No `/myvillage` command-surface change.
- **Cross-platform build and report determinism.** `build.gradle` now selects
  the Python interpreter per OS (`python` on Windows, `python3` elsewhere),
  falling back to the `PYTHON` environment variable when set, so `gradlew build`
  works out of the box on Windows without the Microsoft Store `python3` stub
  breaking the resource-generation task. Generator/validator/preview tools now
  emit repo-relative paths as POSIX strings (forward slashes) via
  `tools/buildgen/export.py::repo_relpath`, so committed JSON reports and
  human-facing output are byte-identical across Linux and Windows.
- **Library reports slimmed** (`slim-library-reports`, 0.15.0-fix2). The
  `reports/*_library_report.json` files had grown to tens of thousands of lines
  (largest: `cultivation_town_compound_library_report.json` at 164 269 lines /
  3.2 MB) because each generator serialized the full compound/massing graph,
  including per-cell coordinate lists (`parcel_nodes[].cells`,
  `building_slots[].footprint`) that no validator or runtime ever reads. The
  generators now emit a compact `to_summary_dict()` form (cells/footprints
  folded into counts + bounding boxes; non-volume massing nodes and node `meta`
  dropped; `meta.frontage` and `meta.terrace_levels` kept because the
  validators read them). All `*_library_report.json` shrink 59–96% (e.g.
  `cultivation_town_compound` 164 269 → 8 469 lines, `compound` 126 048 → 6 094,
  `cultivation_sect_compound` 72 775 → 2 561). **No `.nbt`, mcfunction, or
  gameplay change** — the in-memory `to_dict()` and generation logic are
  untouched, and regenerated NBTs are byte-identical to the pre-edit state.
- **`reports/` now git-ignored as generated output.** All library/validation
  reports under `reports/` are deterministic generator/validator outputs, so
  `.gitignore` now excludes `reports/*` and the 20 previously-committed report
  files were `git rm --cached`'d (kept on disk; regenerate locally with the
  `tools/` generators). Two files stay tracked via `!`-exceptions because the
  build cannot re-derive them: `reports/town_distinctness_calibration.json`
  (tuning floors read by `validate_runtime_town_plan.py`) and
  `reports/cultivation_style_baseline_hashes.txt` (a pre-migration historical
  hash snapshot). No build behavior changes — the generators and validators
  continue to read/write the files on disk exactly as before.

## 0.15.0

### Changed

- **Chinese courtyard library rebuilt as 一进四合院**
  (`rebuild-chinese-courtyard`). Replaced the shared manor-like shell with real
  硬山/悬山/歇山/卷棚 registry forms, 台基, 檐廊, and 3/5/7-bay hierarchy. The
  parcel plan now separates outer and main yards with 影壁, one 垂花门, two
  returning 抄手游廊, and a 月台. Six deterministic templates vary plan and
  roofline, with full/vanilla validation and legacy-family byte-stability
  guards. **Breaking:** `chinese_courtyard_001..006.nbt` retain their resource
  names but have regenerated footprints, silhouettes, and interiors; existing
  placed blocks are unaffected, while future placements use the rebuilt NBTs.

- **Region runtime binding (洲/域 runtime)** (`add-region-runtime-binding`).
  The macro layer's offline-only era ends: a passive runtime companion now
  consumes the per-seed region graph in-game. The anchor (中州) center is placed
  at the world origin with all 洲 within an anchor-centered ~4000-block radius
  (pure coordinate transform — `SCALE = RADIUS_WORLD(4000) /
  RADIUS_GRAPH_OUTER(1.45 = 1.4 walled ring + 0.05 max embed jitter)`, no
  rotation, no world writes). World spawn is bound **once** per world,
  deterministically from the seed, to the lowest-tier eligible non-walled
  region (sort key `(assigned_tier ASC, distance_from_anchor DESC,
  qi_midpoint ASC, id ASC)`) with a safe-surface spiral search; existing
  custom/admin spawns are preserved on first load (`SavedData`-gated), and
  `/myvillage spawn recompute` (admin) forces an override. A server-side query
  API exposes `region_at(x, z)`, `current_region`, `current_rung`, and
  `next_rung_regions`, where the rung ladder is the ascending distinct tiers
  among non-walled regions and `next_rung_regions` returns a **set** at tier
  ties (灵岳 + 西漠 both at 18) — a branch point the deferred 正道/魔道
  alignment system will resolve. `/myvillage spawn info` (player) prints the
  spawn region/block and the caller's region/rung/next-rung set. The runtime is
  passive: it reads the world seed, caches the graph, answers queries, and calls
  `setDefaultSpawnPos` exactly once — it overrides no biome, hooks no chunk-gen,
  and writes nothing beyond spawn metadata; the offline `region-topology`
  generator's contract is unchanged. The region RNG + generator are ported to
  Java (`com.example.myvillage.region.runtime`) bit-identical to
  `tools/buildgen/region_topology.py`, enforced by golden fixtures under
  `src/test/resources/region_runtime_fixtures/` (regenerate via
  `tools/buildgen/tests/generate_region_runtime_fixtures.py`). Downstream
  consumers (compass/map indicator, alignment tie resolution, mobility gating,
  runtime subject placement, 隔-edge terrain relief, region extents) remain
  deferred. See `docs/ai-kb/13_region_topology.md` and the
  `region-runtime-binding` spec.

## 0.14.0

### Changed

- **Region topology (洲/域) layer — offline-first** (`add-region-topology`).
  Added the macro layer the mod was missing: a per-seed region graph of 5–7 洲
  with a single centered 中州 `anchor`, rule-governed 连 (passable) / 隔
  (separated) relations, a tier gradient under `tier_step N = 5`, and a sealed
  魔域-style `walled` region (all 隔 except ≤1 关隘). Topology is authored as a
  ruleset (count range, tier range/step, separator palette `{特殊山脉, 特殊海洋}`,
  role rules) while geometry is randomized per seed; generation is constructive
  (a 连 spanning tree + outward tier assignment make connectivity and the
  tier-step hold by construction), so it is seed-deterministic and never
  re-rolls. New data under `worldgen/region_profile/` + `worldgen/region_topology.json`
  (+ a shipped `region_topology_example.json`), and new `tools/buildgen/region_topology.py`
  (single shared source), `tools/generate_region_topology.py`,
  `tools/validate_region_topology.py`, and
  `tools/generate_region_topology_preview.py` (SVG + ASCII previews wired into
  the aggregate). Added `docs/ai-kb/13_region_topology.md`. This layer is
  offline-only — **no runtime worldgen, no in-game command** this change; a
  later change consumes the typed edge list for terrain relief.

## 0.13.0

### Changed

- **Town shape vocabulary and seed-driven grid** (`town-shape-vocabulary`).
  `/myvillage town [seed]` now selects independently from square, 天圆 circle,
  oval, 半月 D-shape, true octagon, and trapezoid wall families plus optional
  barbican/bastion modifiers. The spine, three cross-lanes, and outer district
  widths vary within bounded orthogonal ranges. Outer district cell sets clip
  to the perimeter curve while the civic core remains rectangular. Python and
  Java share `town_hash`, a five-seed parity fixture, integer circle/ellipse
  sweeps, and a calibrated pairwise distinctness gate. Added
  `docs/ai-kb/12_town_shape_vocabulary.md` and updated command examples.

## 0.12.0

### Changed

- **Town shape is no longer a hard square** (`town-shape-irregularity`). The
  runtime cultivation town (`/myvillage town`) wall is now a deterministic,
  seed-derived variant (`square` / `chamfer` / `indent`) selected from a fixed
  set, with geometry a pure function of (site, shape id) so the Java realizer
  mirrors it from the id alone (no shared RNG). All deformation is inward-only,
  confined to the empty east/west margin and corner triangles, so the south-gate
  segment, the street grid, and every district are untouched; the bitten cells
  are emitted as a `moat` negative space. `TownDistrict` now carries an
  authoritative `cells` set (with `bounds` kept as the AABB), subdivision is
  cell-set-aware (`_parcel_fits`), and the `chamfer` shape also chamfers the two
  fringe districts' exterior corners (the only safe non-rectangular districts —
  parcel-bearing and civic-core districts stay rectangular because the
  civic-precinct derivation is coupled to `core.bounds`). Python⇄Java parity
  gains perimeter-variant and fringe cell-count descriptors. No command-surface
  change; `/myvillage town [seed]` behaves the same. See
  `docs/ai-kb/11_town_shape_irregularity.md`.

### Fixed

- **Build configuration** (`build.gradle`). The `net.neoforged.moddev` 2.0.141
  plugin removed the `runs { configureEach { modSource ... } }` DSL —
  `RunModel` has no `modSource` in this version, so `./gradlew compileJava`
  failed at evaluation time. Source registration now uses the top-level
  `neoForge { mods { "${mod_id}" { sourceSet sourceSets.main } } }` block.

## 0.11.0-fix2

### Changed

- **Documentation knowledge-base maintenance** (`add-docs-kb-governance`). Added a
  knowledge-base entry map at `docs/ai-kb/INDEX.md` and linked it from `README.md`
  and `AGENTS.md`; cross-linked the worldgen / validation / blueprint-schema doc and
  spec pairs with see-also references; corrected the README "Current Scope" lists so
  shipped sect worldgen is listed as included and no longer excluded; split the two
  oversized `AGENTS.md` conventions (settlement composition into sect/worldgen/town
  sub-items, acceptance command checklist moved into `docs/ai-kb/09_validation_checklist.md`);
  made the version-bump rule single-source in `openspec/config.yaml`, referenced from
  `AGENTS.md` and this changelog. Documentation-only; no code or asset changes.

## 0.11.0-fix1

### Fixed

- **Worldgen sect no longer stalls chunk loading on approach**
  (`fix-sect-worldgen-chunk-stall`). The single `SectStructurePiece` spans
  ~8×15 chunks, and `postProcess` re-ran the *entire* mountain + compound build
  for every overlapping chunk — relying on the sink to merely discard
  out-of-chunk writes — so each of ~120 chunks redundantly performed tens of
  thousands of `getBaseHeight` samples plus re-parsed every slot template's NBT.
  The worldgen thread pool saturated and the feature-stage dependency front
  could no longer advance, freezing chunk loading server-wide as a player
  approached (before the sect was even visible, and through `/tp`). The realizer
  now clips its iteration (not just its writes) to a `SectSink.clip()` — the
  current chunk's column area in worldgen, unbounded for the on-the-spot
  command — so each chunk does work proportional only to its own slice; total
  work drops from O(footprint × overlapping-chunks) to O(footprint). Parsed
  templates are cached in `ModBlockFallback` (cleared on reload) instead of
  re-read per chunk, and per-volume placement RNG is derived from the stable
  sect site + volume origin (not the chunk) so a building straddling a chunk
  seam rolls the same variant/orientation in both halves. Siting, biome gating,
  separation, `/locate`, the `/myvillage sect` command, and the derived-mountain
  geometry are unchanged (Python/Java parity still validated).
- **Worldgen sect terrain no longer floats above the compound.** Exposed once the
  stall fix let the worldgen path complete: the mountain/terrace passes placed via
  `base.offset(x, absoluteY, z)`, double-adding `base.getY()`, so the derived
  terrain (mountain, terraces, stairs, retaining faces, cliff-back, galleries,
  cloud-sea, flying-bridge deck) baked ~a base-height above the buildings, which
  `realizeSlots` placed at the correct absolute Y — the terrain and the sect were
  "not unified." A `SectGenerator.at(base, localX, worldY, localZ)` helper now
  places all terrain passes at the absolute Y directly, so terrain and buildings
  share one elevation frame. The on-the-spot `/myvillage sect` command shares the
  realizer and benefits from the same fix.

## 0.11.0

### Added

- **Sect worldgen with derived mountain** (`add-sect-worldgen`). A custom
  worldgen `myvillage:sect` `Structure` (`SectStructure` + `StructureType` +
  `SectStructurePiece`, registered through `SectStructures`) sites cultivation
  sects during chunk generation — rare, biome-gated to a high-relief biome tag
  (`data/myvillage/tags/worldgen/biome/has_sect.json`), spaced as a regional
  landmark (`worldgen/structure_set/sect.json`), world-seed reproducible, and
  baked into chunks with no force-load and no build pop-in. The structure is
  locatable via `/locate structure myvillage:sect`. The "no worldgen is
  registered" note is removed.
- **反推山形 mountain derivation.** Rather than search for matching natural
  terrain, the generator derives the mountain from the compound's exported
  terrace profile: terrace elevations as the skeleton, seed-driven value noise
  for the inter-terrace and outer slopes, an outer blend skirt grading the
  man-made relief into the natural heightmap (no cut-off seam), a sheer
  cliff-back face behind the summit, a placed translucent cloud-sea (云海面)
  sheet with feathered edges + powder-snow wisps between the gate and disciple
  terraces, and a solitary peak (孤峰) raised under the detached-spire feature.
  Implemented in `SectMountain.java` and mirrored/validated offline by
  `tools/buildgen/sect_mountain.py`.
- **Shared realizer + force-generate command.** The `SectGenerator` realizer is
  refactored onto a `SectSink` so the same plan + geometry serve both the
  on-the-spot `/myvillage sect [seed]` command (unchanged, rests on the live
  surface) and worldgen (rests on the derived mountain, clamped per chunk).
  `/myvillage sect worldgen [seed] [variant]` force-generates a worldgen-style
  sect and can force one of the three detached-spire variants (or `none`).
- **Validation + preview.** `validate_sect_generation.py` adds worldgen checks
  (biome gating, minimum separation, blend-skirt seam, terraces at planned
  elevations, cliff-back, cloud-sea, deterministic spire, mountain parity
  constants, feature presence survey) into `reports/sect_generation_validation.json`;
  `generate_sect_plan_preview.py` adds a top-down derived-mountain heightfield
  preview (`mountain.png` / `mountain.json`) to each sect plan viewer.

## 0.10.0

### Added

- **Terraced cultivation sect compound** (`sect-compound`). A deterministic
  terraced axial sect-compound planner (`tools/buildgen/sect.py`) and a
  structurally-equivalent runtime realizer
  (`src/main/java/com/example/myvillage/sect/SectGenerator.java`) compose an
  ordered terrace stack ascending a single fall-line ritual axis from the
  mountain gate (山门) on the lowest terrace to the cliff-backed principal hall
  (主殿) on the summit. The default skeleton is five terraces
  (gate / disciple / assembly / scripture / summit), parametric on terrace
  count (4–6), rise, depth, width taper, axis-stair width, and cliff-back
  height. Slot importance grades with terrace level — the principal hall and
  scripture pagoda hold the top tiers — and flanking volumes (disciple rows,
  paired pagodas, flanking bell/drum towers) mirror about the axis and are
  joined by covered galleries (廊) recorded as circulation links with both
  endpoints. Each terrace meets the next through a retaining face and an
  on-axis stair flight. The new `/myvillage sect [seed]` command builds the
  compound against terrain, force-loading the footprint, carving and retaining
  each terrace so platforms step the slope with no floating or buried slabs,
  routing palette ids through the mod-fallback resolver, and reporting any
  extent it cannot build.
- **Detached-spire flying-bridge feature** (`sect-flying-bridge`). The sect
  compound ships an optional detached-spire feature as three deterministic
  form variants (differing on detached volume, bridge span/shape, and spire
  offset/bearing), selected per seed (or absent). When present, a detached
  volume sits on its outcrop reachable only by a flying bridge (飞桥) recorded
  with both endpoints on the compound and the detached volume.
- **Sect terrace-profile export (反推山形 contract)**. The planner exports the
  terrace skeleton and geometry parameters (per-terrace elevation/bounds, rise,
  depth, taper, axis-stair width, cliff-back height) as explicit outputs for
  the planned sect worldgen change to derive the man-made mountain from the
  same parameters.
- **Sect validation + preview**. `tools/validate_sect_generation.py` asserts
  the plan invariants (ascending axis, importance grading, gallery/bridge
  endpoint anchoring, variant distinctness, reproducibility), template fit
  against the shipped `.nbt`, and Python/Java planner parity, emitting
  `reports/sect_generation_validation.json`. `tools/generate_sect_plan_preview.py`
  renders top-down sect-plan previews into the preview aggregate.

## 0.9.0

### Added

- **Districted cultivation town plan** (`town-districts`). The runtime
  `/myvillage town` realizer now produces a ~160×160 修仙坊市 partitioned into
  named districts (gate/market/residential/civic_core/fringe), each carrying
  its own density, storey band, material register, and archetype roster from
  the `cultivation_town` group's `district_brief`. The ritual axis (plaza /
  paifang / lantern approach) is expressed inside the civic core rather than
  spanning the whole town. The footprint is force-loaded via chunk tickets so
  the town generates in one command.
- **Street frontage with party-wall rows** (`street-frontage`). Market and
  residential parcels align to the street wall and share gable walls with
  neighbors (沿街连排 / 共墙铺面), producing continuous row frontages and
  intentional narrow alleys (窄巷) instead of centered-lot plinths.
- **Vertical landmark archetypes** (`vertical-landmark`). `pagoda` (塔),
  `pavilion` (楼阁), and `bell_drum_tower` (钟鼓楼) archetypes composed from
  the existing terrace + tiered flying-eave vocabulary and registered as
  roof forms in the form registry. A skyline rule requires the civic core to
  carry at least three above-threshold tall volumes, with at least one being
  a vertical landmark, so the core silhouette rises above the surrounding
  roofline. The `silhouette_score` heuristic now rewards tall rooflines and
  vertical-landmark bonuses.
- **Cultivation street life** (`cultivation-street-life`). The town realizer
  replaces the prior placeholder vanilla furniture (campfire / oak fence /
  white wool / podzol) with a cultivation-themed vocabulary: 幌子 shop
  banners, 药圃/灵田 tending beds and crop rows, 炼丹炉 alchemy furnaces,
  法器摊 artifact stalls (profile-gated `fetzisdisplays` racks), and 阵纹
  formation floor patterns in the civic plaza. Villager inhabitants and
  occasional 灵狐 spirit foxes populate the districts at scale.
- **Profile-gated runtime decor.** Runtime-placed decor fixtures resolve
  through `ModBlockFallback.resolveBlockState()`, so external mod blocks
  (`fetzisdisplays`) are used when loaded and fall back to vanilla barrels
  when absent, mirroring the same modset catalog the Python generators use.

### Changed

- **`cultivation_town` group roster now includes vertical-landmark archetypes**
  (`pagoda`/`pavilion`/`bell_drum_tower`). The `civic_core` district brief
  draws them; the static `cultivation_town_NNN` compound library is reclassified
  as district-fill courtyard tissue — the roster filter in `compound.py`
  restricts the small-block generator to the courtyard-compatible subset.
- **Town footprint raised to 160×160.** `MAX_FOOTPRINT_AXIS` lifted from 96 to
  160 in both Python planner and Java realizer; the `loaded()` hard gate replaced
  with chunk-ticket forced loading released in a `finally` block.
- **`validate_town_plan` / `validate_runtime_town_plan` extended.** District
  partition, core-outranks-fringe hierarchy, skyline relief, and frontage
  sparsity invariants are asserted; the validator now checks every structure
  template fits its parcel with a non-empty ground layer.
- **`quality.py` silhouette score enhanced.** Vertical-landmark roof forms and
  tall roofline heights contribute to the silhouette heuristic so the building
  report reflects the civic core's vertical relief.
- **`check_cultivation_forms.py` extended with vertical-landmark smoke tests.**
  The three new roof forms (pagoda/pavilion/bell_drum_tower) are resolved
  through the form registry and checked for spire finials, upturned corners,
  and belfry bells.

## 0.8.1-fix2

### Fixed

- Closed the corner holes in the upper stories of pagoda buildings
  (`scripture_pavilion`). The stairwell was reserved flush against the volume
  edge, but pagoda story insets step the upper-floor perimeter walls inward
  onto that shaft; the stair's protected void then blocked the inset wall from
  sealing, leaving open corners on the 2nd and 3rd floors. `_reserve_stairwell`
  now offsets the shaft inward by the deepest story inset so it stays inside
  the most-inset footprint and the outer shell closes on every story. Visible
  in `cultivation_sect_001` (summit pagoda) and standalone `scripture_pavilion`.

## 0.8.1-fix1

### Fixed

- Closed side-wall holes on gabled buildings: `gable_roof()` now fills each
  gable-end column from the eave up to the true roof skin directly above it
  (climbing to the real `ridge_y`), and backs any cell carrying only a roof
  stair with a full gable block one step inboard. Apex gaps, edge gaps where
  an overhung slope arrived late, and see-through half-block roofline cells
  are gone.
- Stopped interior furniture mounting on a neighbour volume's exterior wall:
  the blacksmith `smithy` zone is now inset like `forge`/`storage`, and
  `spots_along_walls()` only mounts on a wall cell belonging to the zone's own
  volume. Leaked anvils/barrels/furnaces against the main wall are eliminated.
- Replaced the gable-triangle 60/40 dark-roof-plank mix with a style-declared
  gable infill: stone styles (`cultivation_sect`, `chinese_courtyard`,
  `cultivation_town`) get a solid `WALL_MAIN` gable with no scattered dark
  planks; `medieval_village` opts into a timber-infill look via a new optional
  `GABLE_INFILL` slot. Each gable cell is tagged with the slot it holds.
- Connection openings are now carved only on real (non-open) walls and clear of
  the parent facade's post/window/door columns (re-sealing any crossed post),
  via a new post-facade `connection_carve_pass`. Chimney placement offsets or
  flips around an abutting `side_wing`/shed instead of force-overwriting its
  facade/structure wall cells. Small wings/sheds now always keep a one-row
  stone plinth (`wall_frame` floors `stone_rows` at 1 for `wall_h >= 3`).

### Added

- Two build-quality hard-error checks that gate export: `open_side_wall`
  (every closed gable-family volume's wall plane must be enclosed from the
  foundation top to its roofline, modulo planned openings) and
  `furniture_on_wall` (no `INTERIOR`/`PROTECTED` non-opening block may sit
  against a different volume's exterior wall). These inspect the actual wall
  plane, not just cells a roof op recorded.
- Optional `GABLE_INFILL` material slot (registered in
  `OPTIONAL_MATERIAL_SLOTS`).

### Changed

- `flat_wall` counting now excludes roof-skin (gable-infill) facade cells so
  the new solid gable is not flagged as a flat run.

### Deferred

- Opposite-wall post-layout sharing and side/back speckle clamping were
  evaluated and dropped: both destabilized byte-stable output without offsetting
  payoff, per the change's §6 "drop if it destabilizes" clause.

## 0.8.1

### Added

- Rebuilt the cultivation `sweeping_eave_roof` silhouette as a real flying-eave
  (飞檐翘角) curve instead of a straight gable with corner bumps. The eave line
  now droops at mid-span and swoops up toward each gable end via a per-column
  corner-lift heightfield, and each eave side runs through a flat eave band
  (举折) before climbing to a level ridge. `tiered_eave_roof` inherits the curve
  on every tier. All geometry stays stair/slab-only; no new mod is required.
- Added a slot-resolved dougong / 额枋 bracket course (`DETAIL_WOOD` `_fence`)
  set under the deep eaves of sweeping-eave roofs; styles without the slot skip
  it, so mortal roofs are unchanged.
- Strengthened `tools/check_cultivation_forms.py` to assert the eave actually
  lifts at the corner and that eave brackets are placed, locking the curve
  against regression.

## 0.8.0-fix5

### Fixed

- Fixed horizontal wall-mounted plaque column placement so north/east-facing
  facades read inscriptions in exterior-view order instead of reversing names
  such as `庄园正门` and `藏经阁`.
- Added generated-structure validation for horizontal wall plaque visual
  column order to catch reversed multipart `col` sequences.

## 0.8.0-fix4

### Fixed

- Changed plaque inscription rendering from per-tile baked calligraphy PNGs to
  one full plaque texture per bound frame/mount/orientation, with each
  multipart block model sampling its own UV window from that full texture.
- Preserved HD inscription resolution in generated full plaque textures
  instead of downsampling calligraphy to 16x16 block parts.
- Updated offline preview rendering and plaque binding validation to understand
  full plaque textures plus model UV windows.

## 0.8.0-fix3

### Fixed

- Fixed generated multipart plaque frame textures so `inner_left` and
  `inner_right` tiles no longer render as outer border columns.
- Refit baked inscription artwork into the full plaque interior instead of
  stretching each source texture across the whole multipart target.
- Retinted low-contrast inscription sources against their plaque surface so
  dark calligraphy remains visible on dark wood and lacquer signboards.
- Extended plaque binding validation to check generated block textures for
  visible-but-not-overfilled inscription pixels.

## 0.8.0-fix2

### Fixed

- Reworked plaque inscriptions from runtime `minecraft:painting` entities into
  block-native baked plaque textures, eliminating the vanilla hanging-entity
  survival path that could break inscriptions into dropped painting items.
- Added distinct multipart plaque row/column states for 4w, 5w, and 4h plaques
  so every tile can display the correct slice of the calligraphy texture.
- Updated generated-structure validation to reject `myvillage:inscription/...`
  painting entities in shipped structures.

## 0.8.0-fix1

### Fixed

- Fixed custom calligraphy paintings rendering as missing black-purple textures
  in game by shipping inscription PNGs under the painting atlas path
  `assets/myvillage/textures/painting/inscription/`.
- Fixed even-height plaque painting anchors so 5w_2h and vertical inscriptions
  no longer shift upward, lose support, and drop as vanilla painting items.
- Updated plaque blocks to provide a full hanging support shape without
  colliding with the painting entity in front of the plaque.

## 0.8.0

### Added

- Added four shipped `myvillage` plaque blocks: wall, vertical wall, hanging,
  and vertical hanging plaque variants.
- Added an eight-preset plaque frame catalog with generated blockstates,
  models, frame textures, and artist-facing asset documentation.
- Added image-based inscription plaques through `painting_variant` resources
  and v1 HD calligraphy textures.
- Added data-driven archetype-to-plaque bindings for shops, inns, taverns,
  manors, sect gates, paifang, scripture pavilions, and treasure pavilions.
- Added hanging-plaque chain integration plus preview support for plaque block
  textures and inscription painting overlays.

### Changed

- Extended generated-structure, style-policy, and fallback validators with a
  `myvillage:` self-namespace carve-out while keeping external-mod validation
  strict.
- Extended offline previews and acceptance prep to report missing inscription
  assets and validate plaque binding data.

## 0.7.1

### Added

- Rebuilt cultivation town and sect building form around raised platforms,
  columned entries, balustrades, dougong-style brackets, mountain gates,
  pagoda massing, and alchemy-room furnace features.
- Added sweeping-eave, hip, pyramidal, and revised tiered-eave roof handlers
  for cultivation styles.

### Changed

- Replaced Western retagged cultivation structures with cultivation-specific
  building graphs and validation rules that reject porch, chimney, and other
  Western domestic tells in cultivation families.
- Extended cultivation validation, preview, and acceptance documentation for
  the rebuilt form vocabulary and sect compound checks.

## 0.7.0-fix2

### Fixed

- Restored Supplementaries awning canopies on market stalls and generated
  fence posts plus solid roof beams behind each awning so attached placement
  survives in game.
- Extended generated-structure validation to reject unsupported
  `supplementaries:awning_*` blocks.

## 0.7.0-fix1

### Fixed

- Fixed optional-mod decor motifs that placed attached blocks without valid
  support, causing sect-gate signs/displays or Supplementaries awnings to drop
  or disappear during in-game structure placement.
- Changed free-standing market stall canopies to use stable `ROOF_TILE`
  stairs/slabs instead of wall-attached awnings, while keeping modded market
  fittings visible under the `full` profile.
- Extended generated-structure validation to reject unsupported wall-attached
  sign/banner blocks.

## 0.7.0

### Added

- Added generated runtime fallbacks for optional decor-mod block ids and routed
  `/myvillage place` plus `/myvillage town` template loading through the
  fallback resolver so absent decor mods degrade to vanilla blocks instead of
  air.
- Added optional NeoForge dependency declarations for Ars Nouveau, Farmer's
  Delight, Supplementaries, Fetzi's Displays, Macaw's Furniture, and Macaw's
  Windows.
- Added fallback-map validation for shipped structure palettes.

## 0.6.0-fix3

### Fixed

- Fixed runtime `/myvillage town` site-fit placement so building templates use
  their `y=1` ground layer against the parcel surface and receive a continuous
  footprint support layer, preventing one-block hollow gaps under houses.
- Extended the runtime town-plan validator to check the shipped templates'
  ground-layer convention used by the Java realizer.

## 0.6.0-fix2

### Fixed

- Fixed runtime `/myvillage town` ground-detail placement so campfires,
  lantern posts, and central street-room furniture are anchored to parcel
  ground, free side-yard cells, or actual street cells instead of roof
  heightmap hits after templates are placed.
- Extended the runtime town-plan validator to check smoke/light detail space
  and street-room furniture candidate cells.

## 0.6.0-fix1

### Fixed

- Fixed the runtime `/myvillage town` plan geometry so all seed-selected lane
  offsets keep parcels, negative-space regions, and placed template footprints
  disjoint from streets.
- Added a runtime town-plan regression validator for the Java layout variants.

## 0.6.0

### Added

- Added `/myvillage town [seed]`, an on-demand runtime living-town generator
  with a closed wall, gates, main-street spine, dominant landmark, terrain
  plinths, active frontage, street furniture, smoke/light, wear, and daily-life
  props.
- Added deterministic town-plan data, validation, budget checks, JSON dumps,
  and top-down PNG/HTML plan previews.
- Added frontage metadata and optional importance-tier hooks to generated
  town building graphs.
- Split `/myvillage gallery` into the full gallery plus
  `/myvillage gallery original` and `/myvillage gallery cultivation`.

### Changed

- Rebound `cultivation_town` to the runtime town-generation layout while
  preserving the existing courtyard-street block outputs as reusable parts.
- Extended cultivation town validation to require resolvable frontage metadata
  on embedded town building parts.

### Documentation

- Updated command, preview, validation, and acceptance docs for the living-town
  flow.

## 0.5.1

### Added

- Added the `cultivation_town` courtyard-street block library:
  `cultivation_town_001.nbt` through `cultivation_town_006.nbt`.
- Added small-courtyard and courtyard-street block generation/validation,
  including street, lane, party-wall, gate-orientation, and traversability
  checks.

### Changed

- Changed the `cultivation_town` settlement group from standalone structures to
  the `courtyard_street_block` layout strategy.
- Updated command and acceptance documentation for
  `/myvillage place cultivation_town_001` and the cultivation town gallery.

### Removed

- Removed the old standalone cultivation town NBT/place-function outputs from
  the default generated resource set.

## 0.5.0

### Added

- Generated civic structures, cultivation town structures, standalone
  cultivation sect structures, and cultivation sect compound structures.
- Grouped gallery support for civic, cultivation town, and cultivation sect
  columns.
