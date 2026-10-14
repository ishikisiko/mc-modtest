---
navigation:
  title: Cultivation Loop
  parent: index.md
  position: 20
  icon: myvillage:low_grade_spirit_stone
item_ids:
  - myvillage:low_grade_spirit_stone
  - myvillage:spirit_stone_ore
  - myvillage:deepslate_spirit_stone_ore
---

# Cultivation Loop

The controls below display your current configured bindings. Do not treat their default letters as fixed controls:

- Open the Profile / Meditation / Techniques panel: <KeyBind id="key.myvillage.open_cultivation_profile" />
- Start normal meditation: <KeyBind id="key.myvillage.start_normal_meditation" />
- Start spirit-stone meditation: <KeyBind id="key.myvillage.start_spirit_meditation" />
- Stop the current cultivation session: <KeyBind id="key.myvillage.stop_meditation" />
- Start advancement after both caps are full: <KeyBind id="key.myvillage.start_advancement" />

## Starting And Interruptions

Starting meditation or advancement requires an awakened spiritual root, learned Basic Breathing, Survival or Adventure mode, remaining lifespan, and stable ground. You must also be alive, not riding, swimming, flying, sleeping, or using an item, and must have taken no positive damage during the previous `100` ticks. Meditation begins with `40` continuously eligible preparation ticks.

Movement beyond `0.01` block on any axis, jumping, damage, attacking or swinging, mining, block/entity/item use, riding, swimming, flying, sleeping, game-mode or dimension changes, death, logout, or explicit stop interrupts the session. Using this handbook is item use, so using it again during meditation interrupts that session. Turning the camera, opening or closing the profile screen, and switching its tabs are allowed.

## Normal And Spirit-Stone Batches

After preparation, one batch settles every `10` continuously eligible ticks:

- **Normal meditation** adds progress equal to the current spiritual affinity. New profiles default to `10`, but the server profile is authoritative.
- **Spirit-stone meditation** adds a fixed `50` progress and directly consumes the current stage's cost in <ItemLink id="myvillage:low_grade_spirit_stone" /> from ordinary inventory.

| Current stage | Stones per batch | Progress per batch |
|---|---:|---:|
| Qi-Sensing Mortal | 1 | 50 |
| Qi Refining I | 1 | 50 |
| Qi Refining II | 2 | 50 |
| Qi Refining III | 3 | 50 |
| Qi Refining IV | 4 | 50 |
| Qi Refining V | 5 | 50 |
| Qi Refining VI | 6 | 50 |
| Qi Refining VII | 7 | 50 |
| Qi Refining VIII | 8 | 50 |
| Qi Refining IX | 9 | 50 |
| Early Foundation | 20 | 50 |
| Middle Foundation | 24 | 50 |
| Late Foundation | 28 | 50 |
| Early Golden Core | 40 | 50 |
| Middle Golden Core | 48 | 50 |

A nonempty final batch pays the full current-stage cost from the table before progress is clamped to its cap; a stage already at its progress cap costs no stone. If inventory is short, nothing is consumed, the due normal-affinity result is applied, and the session downgrades directly to normal meditation without another preparation period.

## Progress Before Stability

Only progress grows while progress is below its cap; **the batch that first fills progress grants no stability**. Beginning with the next `10`-tick batch, either mode adds the current spiritual affinity to stability and consumes no stone.

| Current stage | Progress cap | Stability cap |
|---|---:|---:|
| Qi-Sensing Mortal | 1000 | 500 |
| Qi Refining I | 1100 | 550 |
| Qi Refining II | 1200 | 600 |
| Qi Refining III | 1300 | 650 |
| Qi Refining IV | 1400 | 700 |
| Qi Refining V | 1500 | 750 |
| Qi Refining VI | 1600 | 800 |
| Qi Refining VII | 1700 | 850 |
| Qi Refining VIII | 1800 | 900 |
| Qi Refining IX | 1900 | 950 |
| Early Foundation | 3000 | 1500 |
| Middle Foundation | 3600 | 1800 |
| Late Foundation | 4200 | 2100 |
| Early Golden Core | 6000 | 3000 |
| Middle Golden Core | 7200 | 3600 |

## Finding Spirit Stones

<Row>
  <BlockImage id="myvillage:spirit_stone_ore" scale="1.5" />
  <BlockImage id="myvillage:deepslate_spirit_stone_ore" scale="1.5" />
</Row>

<ItemLink id="myvillage:spirit_stone_ore" /> and <ItemLink id="myvillage:deepslate_spirit_stone_ore" /> generate in newly generated Overworld chunks; existing chunks are not retrofitted. Mine them with an iron-tier or better pickaxe. Silk Touch drops the matching ore, while ordinary mining uses vanilla ore and Fortune rules to drop low-grade spirit stones.

## Lifespan Gate

The default scale is `24000` effective ticks per day, `6` days per week and `24` days per year. Lifespan is capped at `80` mortal, `120` Qi Refining, `240` Foundation and `500` Golden Core years. Advancement preserves already consumed lifespan. Personal age advances only while the player is online, alive, and in Survival or Adventure mode. Exhaustion blocks meditation and advancement, but it does not kill the player, clear the profile, or trigger reincarnation.

## Deterministic Advancement

Both current progress and stability must reach their caps before one advancement can start. Advancement reuses the eligibility and interruption boundary above. On completion, the server revalidates the rules, advances exactly one stage, resets progress to zero, and retains the integer floor of actual stability divided by two.

| Advancement | Duration (ticks) | Retained from a full stability cap | Player/world interruption loss |
|---|---:|---:|---:|
| Qi-Sensing Mortal -> Qi Refining I | 100 | 250 | 0 |
| Qi Refining I -> Qi Refining II | 100 | 275 | 0 |
| Qi Refining II -> Qi Refining III | 120 | 300 | 0 |
| Qi Refining III -> Qi Refining IV | 200 | 325 | 5 |
| Qi Refining IV -> Qi Refining V | 140 | 350 | 0 |
| Qi Refining V -> Qi Refining VI | 160 | 375 | 0 |
| Qi Refining VI -> Qi Refining VII | 240 | 400 | 5 |
| Qi Refining VII -> Qi Refining VIII | 180 | 425 | 0 |
| Qi Refining VIII -> Qi Refining IX | 200 | 450 | 0 |
| Qi Refining IX -> Early Foundation | 400 | 475 | 10 |
| Early Foundation -> Middle Foundation | 200 | 750 | 0 |
| Middle Foundation -> Late Foundation | 240 | 900 | 0 |
| Late Foundation -> Early Golden Core | 600 | 1050 | 20 |
| Early Golden Core -> Middle Golden Core | 300 | 1500 | 0 |
| Middle Golden Core -> Late Golden Core | 360 | 1800 | 0 |

Advancement has no random failure and cannot chain across multiple stages. The Qi-III and Qi-VI bottlenecks lose `5` stability when interrupted; Qi IX to Foundation loses `10`, and late Foundation to Golden Core loses `20`. Ordinary transitions have no interruption loss. Orderly server shutdown and registry reload cancel without penalty.

**Late Golden Core is the current playable release ceiling.** Qi Refining I through IX and early/middle/late Foundation and Golden Core are reachable one stage at a time. Late Golden Core cannot gain further progress or advance to Nascent Soul. Pills, cultivation facilities, tribulation, death from old age, and reincarnation are not implemented.

Spiritual-power capacities are `200 / 250 / 300` in Foundation and `400 / 500 / 600` in Golden Core. Raising capacity on advancement does not refill spiritual power or health.
