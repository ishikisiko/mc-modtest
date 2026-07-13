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

- Open the read-only Profile / Meditation screen: <KeyBind id="key.myvillage.open_cultivation_profile" />
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

A nonempty final batch pays the full `1 / 1 / 2 / 3` cost before progress is clamped to its cap; a stage already at its progress cap costs no stone. If inventory is short, nothing is consumed, the due normal-affinity result is applied, and the session downgrades directly to normal meditation without another preparation period.

## Progress Before Stability

Only progress grows while progress is below its cap; **the batch that first fills progress grants no stability**. Beginning with the next `10`-tick batch, either mode adds the current spiritual affinity to stability and consumes no stone.

| Current stage | Progress cap | Stability cap |
|---|---:|---:|
| Qi-Sensing Mortal | 1000 | 500 |
| Qi Refining I | 1100 | 550 |
| Qi Refining II | 1200 | 600 |
| Qi Refining III | 1300 | 650 |

## Finding Spirit Stones

<Row>
  <BlockImage id="myvillage:spirit_stone_ore" scale="1.5" />
  <BlockImage id="myvillage:deepslate_spirit_stone_ore" scale="1.5" />
</Row>

<ItemLink id="myvillage:spirit_stone_ore" /> and <ItemLink id="myvillage:deepslate_spirit_stone_ore" /> generate in newly generated Overworld chunks; existing chunks are not retrofitted. Mine them with an iron-tier or better pickaxe. Silk Touch drops the matching ore, while ordinary mining uses vanilla ore and Fortune rules to drop low-grade spirit stones.

## Lifespan Gate

The default scale is `24000` effective ticks per day and `6` days per year. Mortal lifespan is capped at `80` years and Qi Refining lifespan at `120` years. Personal age advances only while the player is online, alive, and in Survival or Adventure mode. Exhaustion blocks meditation and advancement, but it does not kill the player, clear the profile, or trigger reincarnation.

## Deterministic Advancement

Both current progress and stability must reach their caps before one advancement can start. Advancement reuses the eligibility and interruption boundary above. On completion, the server revalidates the rules, advances exactly one stage, resets progress to zero, and retains the integer floor of actual stability divided by two.

| Advancement | Duration (ticks) | Retained from a full stability cap | Player/world interruption loss |
|---|---:|---:|---:|
| Qi-Sensing Mortal -> Qi Refining I | 100 | 250 | 0 |
| Qi Refining I -> Qi Refining II | 100 | 275 | 0 |
| Qi Refining II -> Qi Refining III | 120 | 300 | 0 |
| Qi Refining III -> Qi Refining IV | 200 | 325 | 5 |

Advancement has no random failure and cannot chain across multiple stages. Only the Qi Refining III -> IV bottleneck loses `5` stability when interrupted by a player or world event; the other listed interruption losses are `0`.

**Qi Refining IV is the current playable release ceiling.** It cannot gain more progress or advance to Qi Refining V. Foundation Establishment gameplay, pills, cultivation facilities, tribulation, death from old age, and reincarnation are not implemented.
