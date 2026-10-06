# Technique System (功法 / 流派 / 传承)

0.37.0, first phase of the technique system: one catalogue feeds both the
player's technique registry and the world ledger (命簿); schools (流派) and
heritages (传承) are datapack registries; the running core technique (心法)
scales meditation progress and can be switched; the H panel's 功法 page groups
techniques and switches the running one; ledger sects can hold a heritage that
is lost with them and found again. The design, with the owner's decisions and
the later phases, is `docs/technique-system-brief.md` (Chinese, owner-facing).
0.38.0 adds technique manuals (秘籍) as items and studying them (研读) as a
third meditation mode, brought forward from the third phase; its brief is
`docs/technique-manual-brief.md`, and "Manuals and study" below describes it.
There is no capability spec for it; this note and the code are the reference.

## Where each fact lives

| Fact | Source of truth | Generated or read by |
|---|---|---|
| Techniques: id, Chinese name, category, grade 1..4, element, school, `previous` | `tools/technique_catalogue/catalogue.json` (one row per technique) | `tools/gen_technique_catalogue.py` |
| Schools | `tools/technique_catalogue/schools.json` | the generator |
| Heritages | `tools/technique_catalogue/heritages.json` | the generator |
| Grade → requirement, element-affinity rule, the core `effects` block, the classification used for the initial import, hand-written ids | `tools/technique_catalogue/rules.json` | the generator (`importer.py` used the classification once to seed `catalogue.json`) |
| `basic_breathing` (grade 0, 凡阶) | `data/myvillage/myvillage/technique/basic_breathing.json`, hand-written | never touched by the generator |
| Grade multipliers, element bonus, `switch_progress_loss`, `roots.element_threshold_bp`, `genesis.heritage_chance` | `data/myvillage/world_sim/rules.json` | the ledger, `CoreTechniqueFactor`, `CultivationService.switchCoreTechnique`, the study element bonus (`StudyRules`) |
| Study points, gates and gate cost per grade (0.38.0) | `tools/technique_catalogue/rules.json` `study_by_grade` | the generator writes each technique's `study` block |
| Manual item art (0.38.0) | `tools/gen_manual_textures.py` (character grids in the script) | the eight `textures/item/manual_<category>[_tint].png` |

Generator outputs, never edited by hand (`--check` fails on any difference):
`data/myvillage/myvillage/technique/*.json` except the hand-written ids,
`data/myvillage/myvillage/school/*.json`, `data/myvillage/myvillage/heritage/*.json`,
`data/myvillage/world_sim/techniques.json`, `data/myvillage/world_sim/heritages.json`
(each generated technique file carries a `study` block since 0.38.0),
and the keys `cultivation.technique.myvillage.*`,
`cultivation.school.myvillage.*`, `cultivation.heritage.myvillage.*` in both
`en_us.json` and `zh_cn.json` (Chinese literal names in both languages, as for
the ledger's other names). Existing keys are updated in place, new ones
appended as a group, stale ones removed; other keys are left alone. A stale
technique file in the datapack directory is removed.

```bash
/usr/bin/python3 tools/gen_technique_catalogue.py           # write what differs
/usr/bin/python3 tools/gen_technique_catalogue.py --check   # release-gate step gen-technique-catalogue-check
```

Shipped catalogue: 129 generated techniques (core 116, body 10, active 3;
grades 1/2/3/4 = 41/36/30/22) plus `basic_breathing`, so 130 in the registry
and 129 in the ledger. Four schools: `sword`, `spear`, `fist` (kind `weapon`,
`weapon_family` of the same name) and `flying_sword` (kind `special`,
`runtime: flying_sword`). Four techniques carry `school: sword` (the core
庚金引气法 and the three active 剑诀/剑典/剑经). Three heritages, four
techniques each, one element each:

| Heritage | School | Chain (grade) | `exclusive` |
|---|---|---|---|
| 太白剑脉 `taibai_jianmai` | sword | 庚金引气法 core (1) → 庚金剑诀 (2) → 天罡剑典 (3) → 太白剑经 (4), all metal; the last three active | true |
| 青帝木脉 `qingdi_mumai` | none | 青木长春功 (1) → 乙木生华诀 (2) → 甲乙长生经 (3) → 青帝道藏 (4), all core, wood | true |
| 万劫金身脉 `wanjie_jinshen_mai` | none | 铁骨功 (1) → 金鳞淬体诀 (2) → 庚金不灭体 (3) → 万劫金身诀 (4), all body, metal | false |

Requirements by grade (`rules.json` `requirements.by_grade`): 1 → Qi
Refining I, 2 → Qi Refining IV, 3 and 4 → Foundation early; a technique of
grade ≥ 2 with an element also requires that element's affinity ≥ 1500 basis
points. Only core techniques get an `effects` block
(`{"core": {"meditation_route": "xiaozhoutian"}}`); active and body rows ship
without one.

## Registries and JSON shapes

`ModCultivationRegistries` registers two more synced datapack registries next
to realm, element and technique:

| Registry key | Class | Resource root |
|---|---|---|
| `myvillage:school` | `SchoolDefinition` | `data/<ns>/myvillage/school/` |
| `myvillage:heritage` | `HeritageDefinition` | `data/<ns>/myvillage/heritage/` |

```json
// school: kind weapon needs weapon_family ([a-z0-9_]+) and no runtime; kind special needs runtime and no weapon_family
{"translation_key": "cultivation.school.myvillage.sword", "kind": "weapon",
 "weapon_family": "sword", "runtime": null, "element_lean": []}

// heritage: ordered chain, lowest first, at least 2, no repeats; school optional; exclusive defaults to false
{"translation_key": "cultivation.heritage.myvillage.taibai_jianmai", "school": "myvillage:sword",
 "techniques": ["myvillage:gengjin_yinqi_fa", "myvillage:gengjin_jianjue",
                "myvillage:tiangang_jiandian", "myvillage:taibai_jianjing"], "exclusive": true}
```

`TechniqueDefinition` keeps `translation_key`, `category`, `grade`,
`elements`, `requirements` and gains three optional fields, so older files
stay valid:

- `grade` is 0..4 (0 凡阶, only `basic_breathing`; 1 黄 2 玄 3 地 4 天, the
  ledger knows only 1..4).
- `school`: a school id.
- `lineage`: `{"previous": "<technique id>"}`, the technique before this one
  in its chain.
- `effects`: at most the one block matching the category (a block of another
  category is a decode error). `core {meditation_route}`; `active {skill,
  qi_cost ≥ 0, slots ≥ 1}`; `movement {dash_distance > 0, invulnerable_ticks,
  qi_cost, cooldown_ticks ≥ 0}`; `body {attributes: {attribute id: amount},
  stagger_resistance ≥ 0}` (needs one of the two). Only `core` drives anything
  (the meridian route); the other three are decoded and validated only.

At server start (`ModCultivationRegistries.validateRequiredEntries`, which logs the
school and heritage counts) a school's `element_lean`
must name registered elements, a technique's `school` and `lineage.previous`
must exist (and `previous` must not be itself), and a heritage's `school` and
every technique must exist. `heritagesContaining(registryAccess, techniqueId)`
lists the heritages whose chain holds a technique, by heritage id.

## Profile v4 and the running core technique

`CultivationProfile` schema v4 adds `active_core_technique` (optional id; it
must be a learned technique). Decoders for v1, v2 and v3 stay; v3 → v4 sets it
to `myvillage:basic_breathing` when that is learned, otherwise empty (v1 and v2
migrate through v3); only v4 is written. A v4 save whose running id is no
longer learned reads as none running. The snapshot carries the v4 profile.

- Learning the first core technique while none runs makes it the running one,
  so initiation leaves Basic Breathing running.
- Forgetting the running core technique leaves none running.
- `CultivationService.switchCoreTechnique(player, id)` is the only way to
  change it; the pure rules are in `cultivation/technique/CoreTechniqueSwitch`.
  The id must be registered, learned and `core`. The running one again is a
  no-op success. Otherwise the switch succeeds and removes
  `floor(progress × techniques.switch_progress_loss)` cultivation progress (散功,
  0.3 in `rules.json`), unless nothing was running or both techniques lie on
  one chain: one reaches the other by following `lineage.previous` (either
  direction, any distance; the walk stops at an unregistered or repeated id or
  after 64 steps). Without world-sim data nothing is lost.

## Meditation factor

`cultivation/technique/CoreTechniqueFactor`, in basis points (10000 = ×1.0),
computed by `MeditationManager` for each ten-tick batch and applied by
`BasicBreathingSettlement` to progress only (rounded down; stability, mastery
and stone costs are unchanged):

```text
factor = grade cultivation multiplier (techniques.grades.<huang|xuan|di|tian>.cultivation)
       + element_match_bonus, when the root's affinity in one of the technique's elements ≥ roots.element_threshold_bp
```

This is the ledger's additive formula (`sim.engine.Cultivation`), read from
`world_sim/rules.json` through `WorldSimRuntime.data()`. Grade 0, no running
technique, an unregistered or non-core one, or no world-sim data give ×1.0,
so every Basic Breathing number is unchanged. Example: a 玄阶 technique with a
matching root is 1.3 + 0.15 = 1.45, so a normal batch of 10 gives 14 and a
spirit batch of 50 gives 72. Meditation is still gated on Basic Breathing
being learned and eligible (`BasicBreathingEligibility`); the running core
technique only scales the gain.

## Network and commands

- Serverbound `CoreTechniqueSwitchPayload(ResourceLocation)`, type
  `myvillage:core_technique_switch`; the id is its only data. The handler
  calls only `CultivationService.switchCoreTechnique`. A refusal sends
  nothing back; a success pushes the usual snapshot. Sent only by
  `ClientCultivationIntentSender.sendCoreSwitch`.
- `ModPayloads.PROTOCOL_VERSION` is `11` since 0.38.0 (the meditation status
  carries study progress); it was `10` in 0.37.0 (was `9`).
- `/myvillage cultivation|xiulian core|xinfa <target> <technique_id>`
  (permission 2, suggestions are the registered core techniques) calls the
  same service; `info` prints `running core technique: <id>` or `none`.

## Panel

The 功法 page (`TechniquesPage`, arithmetic in `TechniqueShelf`) groups learned
techniques into 心法, 绝技, 身法, 炼体 cards and a last 未知功法 card for ids
the synchronized registry does not know; inside a card highest grade first,
then by name. Each entry has its name, mastery, chips (category, grade name
凡阶..天阶, school, each heritage with its position such as `太白剑脉 2/4`,
elements) and its stated requirement. The running core technique shows a
运转中 chip; every other learned core technique a 运转此心法 button drawn in the
page body (hover through `pointer`, clicks through `mouseClicked`). The page
reads only `PanelContext`. On the 修炼 page `MeridianRoute` maps the running
core technique's `meditation_route` to the circuit and lit channels; only
`xiaozhoutian` exists and looks as before, and an unknown or absent route
falls back to it. Details: [37_cultivation_panel.md](37_cultivation_panel.md).

## Manuals and study (秘籍 / 研读, 0.38.0)

### Items

- 16 items `myvillage:manual_<category>_<grade>`, category `core`, `active`,
  `movement`, `body`, grade `huang`, `xuan`, `di`, `tian` (1..4; 凡阶 has no
  manual), class `item/TechniqueManualItem`, registered in `ModItems`
  (`ModItems.MANUALS`, `ModItems.manual(category, grade)`), stack size 1.
  Vanilla `Rarity` by grade: COMMON, UNCOMMON, RARE, EPIC; 天阶 has the foil.
- The technique is data, as an enchanted book's enchantment
  (`item/ModDataComponents`, both persistent and synced):

  | Component | Type | Meaning |
  |---|---|---|
  | `myvillage:technique` | `ResourceLocation` | the technique this manual teaches; absent = blank |
  | `myvillage:comprehension` | int ≥ 0 | study points already read; absent = 0 |

- Validity, `TechniqueManualItem.check`: empty when readable, otherwise the
  first `Mismatch`: `NOT_A_MANUAL`, `BLANK` (no component), `UNKNOWN_TECHNIQUE`,
  `WRONG_CATEGORY`, `WRONG_GRADE` (the technique must match the item's
  category and grade). The last three are "damaged".
- Name: `《功法名》` (`item.myvillage.manual.named`; English `Manual: …`) for a
  valid manual, `空白秘籍 · 绝技 · 玄阶` for a blank one, red `残损秘籍` for a
  damaged one.
- Tooltip: category · grade; then for a valid manual school, elements, each
  heritage with its position (`太白剑脉 2/4`), the requirement as the 功法 page
  states it, `参悟 n%` when the manual has points (whole percent, rounded
  down), and `右键盘坐研读`; a blank manual says it is blank, a damaged one gives
  its reason and that it cannot be read.
- Creative tab `myvillage:main`: the 16 blank manuals, then one manual per
  registered technique of grade 1..4 (129 today), by category, grade, id
  (`TechniqueManualItem.creativeStacks`, read from the tab's
  `HolderLookup.Provider`).
- Art: four 16x16 icons, one per category (`manual_core` 线装书,
  `manual_active` 卷轴, `manual_movement` 折页, `manual_body` 玉简), each an
  untinted detail layer `manual_<category>.png` and a tint mask
  `manual_<category>_tint.png`; a shared model per category (`layer0` detail,
  `layer1` mask), the 16 item models parent to it. `MyVillageClient` tints
  layer 1 by grade (黄 `0xC9A227`, 玄 `0x3F6FB5`, 地 `0x8B5A2B`, 天 `0xE8D9A0`).
  The PNGs come only from `tools/gen_manual_textures.py` (standard library;
  `--check` is the release-gate step `gen-manual-textures-check`, `--preview
  DIR` draws every icon in the four colours); never edit them by hand.
- Command `/myvillage cultivation|xiulian manual|miji <target> <technique_id>`
  (permission 2) gives the manual of that technique (into the inventory, else
  dropped at the target); an unknown technique or grade 0 is refused. Vanilla
  `/give @s myvillage:manual_active_xuan[myvillage:technique="myvillage:gengjin_jianjue"]`
  works too; a component that does not match the item makes a damaged manual.

### Study numbers

`TechniqueDefinition.study` is `TechniqueStudy(points > 0, gates ≥ 0,
gate_stability_cost ≥ 0)`, optional in JSON with the default `{4000, 0, 0}`
(`basic_breathing` has none). The generator writes it from
`study_by_grade`; change the table there and regenerate:

| Grade | `points` | `gates` | `gate_stability_cost` | Gates at |
|---|---:|---:|---:|---|
| 黄 | 4000 | 0 | 0 | none |
| 玄 | 12000 | 1 | 50 | 6000 |
| 地 | 36000 | 2 | 100 | 12000, 24000 |
| 天 | 96000 | 3 | 150 | 24000, 48000, 72000 |

### Starting

Right-click (server `TechniqueManualItem.use` → `cultivation/study/ManualStudy.use`).
The client predicts `consume` for a valid manual and `pass` otherwise, never
`success`: a hand swing reaches the server and would stop the session that just
started. Checks in order, each refusal a chat line
(`message.myvillage.cultivation.study.*`), nothing started:

1. a valid manual (`StudyStart.Refusal.INVALID_MANUAL`);
2. the technique is not learned yet (`ALREADY_LEARNED`);
3. an awakened root (`NOT_AWAKENED`), the technique's realm, stage and element
   requirements through `TechniqueRequirementEvaluator` (`REALM_TOO_LOW`,
   `AFFINITY_TOO_LOW`, `REQUIREMENTS_UNAVAILABLE`);
4. its `lineage.previous`, when it has one, is learned (`PREVIOUS_REQUIRED`;
   mastery tiers do not exist yet);
5. `MeditationManager.requestStudy`: meditation's eligibility and physical
   checks (survival or adventure, Basic Breathing learned, lifespan left, on
   the ground, not mounted, swimming, flying, sleeping or using an item, no
   damage in the last 100 ticks). Unlike meditation it does not need a stage
   that still gains progress.

Right-clicking a manual while any session runs first ends that session
(`INTERACTED`, from `CultivationEvents` on `RightClickItem`), so the `BUSY`
refusal does not occur in play; right-click again to start reading. A start
replies `STUDY_ACCEPTED`. The session is `MeditationMode.STUDY` with
meditation's 40-tick preparation and anchor and remembers the inventory slot
(the held hotbar slot or the offhand) and the technique id. Its states are
`PREPARING_NORMAL` and `MEDITATING_NORMAL`; only `MeditationStatus.study()`
tells it apart.

### Settlement

Every 10 meditating ticks (meditation's batch), in `MeditationManager` through
the pure `StudyStep` and `StudySettlement`, with no randomness:

- The remembered slot must still hold a valid manual of the same technique,
  else stop `MANUAL_LOST`; the technique must still be learnable (rules 2–4
  above), else stop `STUDY_REQUIREMENTS`.
- Gain `affinity × (10000 + element bonus bp) / 10000`, rounded down;
  `affinity` is the profile's `spiritualAffinity`, the bonus is
  `techniques.element_match_bonus` (0.15 = 1500 bp) when the root reaches
  `roots.element_threshold_bp` in one of the technique's elements, both from
  `world_sim/rules.json` (`StudyRules`; 0 without world-sim data). No grade
  multiplier. At affinity 10 that is 10 per batch, 11 with a match.
- Gates sit at `floor(points × k / (gates + 1))`, k = 1..gates; a gate is
  passed once the points are above it. A batch that would cross an unpassed
  gate pays `gate_stability_cost` once and goes on (chat line
  `gate_passed`), or, with too little stability, stops on the gate with
  `STUDY_GATE` and a line naming the cost and the shortfall. Nothing else is
  stored: the points say which gates are behind.
- Stability and the learned technique change in one profile replacement
  through `CultivationService.replaceProfile`; the points are written to the
  manual's `comprehension` only after that commit succeeds.
- Reaching `points` learns the technique (a core technique starts running
  when none runs, as for any learn), consumes the manual and stops
  `STUDY_COMPLETE`.

Interruption is meditation's (movement, jump, damage, attack or swing, mining,
use, mount, dimension change, death, logout, the stop key or button, a config
or definition reload); the points already written stay on the manual, so the
next right-click continues. New stop reasons: `STUDY_ACCEPTED`, `MANUAL_LOST`,
`STUDY_GATE`, `STUDY_COMPLETE`, `STUDY_REQUIREMENTS`.

At affinity 10 and 20 TPS, without the 40-tick preparation and gate stops:

| Grade | Batches | Real time | With an element match (11 per batch) | Stability for all gates |
|---|---:|---:|---:|---:|
| 黄 | 400 | 3 min 20 s | 364 batches, about 3 min 2 s | 0 |
| 玄 | 1200 | 10 min | 1091, about 9 min 6 s | 50 |
| 地 | 3600 | 30 min | 3273, about 27 min 17 s | 200 |
| 天 | 9600 | 80 min | 8728, about 72 min 44 s | 450 |

The requirements still apply: 黄 needs Qi Refining I, 玄 Qi Refining IV, 地
and 天 Foundation early, which play cannot reach in this release (only
`setrealm`).

### Status and network

`MeditationStatus.study()` is an `Optional<StudyProgress>` (`techniqueId`,
`points`, `totalPoints`, `nextGatePoints` or `-1` when none is left,
`gateStabilityCost`), present only while a study session runs;
`status.studying()` tests it. `MeditationStatusPayload` carries it, so
`ModPayloads.PROTOCOL_VERSION` is `11`. Client input is unchanged: the four
meditation intents, and STOP (key X or the 修炼 page button) ends a study like
any session. Starting is item use, not a payload. The 修炼 page shows a study
card while it runs ([37_cultivation_panel.md](37_cultivation_panel.md)).

### Not done

No held-book or floating-page visual while reading, no mastery tiers (the
lineage rule asks only that the previous technique is learned), and no
manuals from loot, sects, the scripture hall or NPCs; manuals come from the
creative tab, `/give`, and the `manual` command.

## World ledger (命簿)

[40_world_sim.md](40_world_sim.md) covers the ledger; the heritage parts:

- `world_sim/heritages.json` (schema 1, `heritages: [{id, name, school,
  techniques}]`, generated) loads into `SimData` as `ContentTables.Heritage`;
  `SimData.heritage(id)`, `heritageOfTechnique(id)`. Every chain member must
  be in `techniques.json` and in no other heritage.
- `Sect.heritageId` ("" for none, kept after the sect ends);
  `WorldState.lostHeritages` (`LostHeritage(heritageId, sectId, day)`);
  `StateCodec.VERSION` 2 adds the sect field `heritage` and the top-level
  `lost_heritages`; a version-1 payload loads with both empty.
- Genesis: each genesis sect rolls `genesis.heritage_chance` (0.25) under
  `Purpose.GENESIS_HERITAGE` (109); a hit takes a heritage no other sect has.
  Its signature technique is the chain's last, its basic the first. Members
  practise by rank: outer the first, inner the second, elders and master the
  second-to-last, and promotions and succession upgrade to it; nobody
  practises the last at genesis (a 天阶 technique on every heritage master
  pushed 元婴 past the health bands). Genesis line `genesis.sect.heritage`.
- A sect that ends (destroyed in war, annexed, ruined by decline, or extinct)
  puts its heritage in the lost pool with a `sect.heritage_lost.1..2` line
  caused by that event.
- A founder whose technique is in a lost heritage's chain takes it up: it
  leaves the pool, the new sect's signature and basic come from the chain,
  and `sect.heritage_rekindled.1` names it.
- Encounter `lost_heritage` (site `ruin`, weight 3, rarity 2, importance 2,
  `min_tier` 8, status `travelling`) with effect kind `heritage`, possible
  only while the pool is not empty: the finder takes the chain's highest
  technique not above the grade they practise (at least the first), even if
  it is no stronger (`fortune.heritage.1`). The heritage stays in the pool.
- `/myvillage world sect <id>` prints `commands.myvillage.world.sect.heritage`
  (传承); `SectView` and `WorldSimSnapshot.SectDetail` carry the heritage
  name, shown on the 天下 sect detail; `tools/world_sim_report.py` shows it on
  the sect timeline; `WorldSim.lostHeritageIds()` lists the pool.

Health bands are unchanged and pass. After genesis and prehistory, small seed
1 has 3 of its 5 genesis sects holding a heritage, small seed 5 none.

## Validation

```bash
/usr/bin/python3 tools/gen_technique_catalogue.py --check
/usr/bin/python3 tools/gen_manual_textures.py --check
/usr/bin/python3 tools/validate_world_sim.py
/usr/bin/python3 tools/validate_cultivation_core.py   # and the other four cultivation validators
/usr/bin/python3 -m unittest tools.tests.test_gen_technique_catalogue tools.tests.test_gen_manual_textures tools.tests.test_validate_world_sim
./gradlew test   # under the heavy-work lock
```

`validate_world_sim.py` also checks every ledger technique against its
datapack file (file exists, grade and element agree), the heritages (each
technique known, in one heritage only, one element per chain, the datapack
`school` equal to the heritage's, `lineage.previous` following the chain) and
that `lineage.previous` has no cycle. Java tests:
`cultivation/data/TechniqueCatalogueDefinitionTest` (every shipped file
decodes), `CultivationProfileTest` (v4, migrations), `technique/CoreTechniqueSwitchTest`,
`technique/CoreTechniqueFactorTest`, `meditation/BasicBreathingSettlementTest`,
`network/CoreTechniqueSwitchPayloadTest`, `CultivationCommandsTest`,
`client/cultivation/panel/TechniqueShelfTest`, `MeridianRouteTest`,
`sim/WorldSimHeritageTest`, `SimDataLoaderTest`, `WorldSimSnapshotsTest`,
`WorldSimPayloadCodecTest`. Manuals and study (0.38.0):
`item/TechniqueManualItemTest`, `cultivation/data/TechniqueStudyDefinitionTest`,
`cultivation/study/{StudyStartTest,StudySettlementTest,StudyStepTest}`,
`meditation/{MeditationSessionTest,MeditationStatusStudyTest}`,
`network/MeditationStatusPayloadTest`, `CultivationCommandsTest`,
`client/cultivation/panel/PanelReadoutsTest`. Nothing of either release has
been seen in a real client; see the README ledgers "Technique system (0.37.0)"
and "Technique manuals (0.38.0)".

## Not implemented yet

- Runtimes for active (绝技), movement (身法) and body (炼体) effects; their
  techniques can be learned and shown but do nothing.
- Mastery tiers, and mastery carried over when switching within a chain.
- A breakthrough multiplier from the running core technique (player
  advancement is deterministic).
- Mastery-tier requirements for the next technique of a chain (study asks
  only that the previous one is learned), manuals from loot, sects, NPCs or
  the scripture hall, a reading visual, and any player route into a ledger
  sect or its heritage; a heritage's `exclusive` flag is data only.
- Weapon `family` fields and anything that ties a school to combat styles.
- Meridian routes other than `xiaozhoutian`.

## See also

- Design brief: `docs/technique-system-brief.md`; manuals and study: `docs/technique-manual-brief.md`
- [28_cultivation_core.md](28_cultivation_core.md), [30_cultivation_playable_loop.md](30_cultivation_playable_loop.md), [37_cultivation_panel.md](37_cultivation_panel.md), [40_world_sim.md](40_world_sim.md)
- [cultivation-definition-registries](../../openspec/specs/cultivation-definition-registries/spec.md), [cultivation-player-profile](../../openspec/specs/cultivation-player-profile/spec.md), [cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md), [cultivation-debug-commands](../../openspec/specs/cultivation-debug-commands/spec.md), [cultivation-meditation](../../openspec/specs/cultivation-meditation/spec.md), [cultivation-core-validation](../../openspec/specs/cultivation-core-validation/spec.md)
- Knowledge-base index: [INDEX.md](INDEX.md)
