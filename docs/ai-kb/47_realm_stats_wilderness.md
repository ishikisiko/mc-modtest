# Realm Statistics and Wilderness

0.75.0 新增[筑基根基](../features/foundation-pills.md)：玩家基础生命/攻击添加独立稳定修饰（先于原境界倍率），不额外放大武器本身；灵力容量经共享 SpiritualPower 读取同一根基加成。旧档根基缺省零。

The numerical contract is shared by physical player combat and beasts. It is
defined by [realm-combat-stats](../contracts/world.md#realm-combat-stats)
and [regional-wilderness](../contracts/world.md#regional-wilderness).
The pure code is in `stats/`; server adapters are in `stats/runtime/`.

## Growth and authority

`data/myvillage/stats/rules.json` owns the cultivated entry multiplier, major
ratio, minor slope, complete realm ladder, player stage aliases, damage types
and numerical limits. For a cultivated realm:

```text
factor = entry_multiplier * major_ratio^major_index * (1 + minor_slope * minor_index)
```

Both indices start at zero. Mortal is a separate baseline. The same minor
index in successive cultivated realms has the configured ratio; differences
between adjacent minor stages within one realm are constant. Validation also
requires the next realm's first stage to exceed the preceding realm's last.

Only maximum health and attack damage receive realm growth. Speed, vanilla
armor, percentage resistance and the existing spiritual-power caps do not
silently acquire the same multiplier. Player stage IDs are explicitly mapped:
the playable player ladder now reaches Golden Core late (0.48.0), while
Nascent Soul remains available only to ledger avatars and beasts. Original entity base
attributes remain in their existing definitions, including beast JSON and
vanilla item attributes; the common layer composes their effective values.

`CultivationService` remains the only player profile writer. Attribute changes
are server-owned, use stable modifier identities, and replace rather than stack
the old contribution. Saved modifiers are intentional: vanilla loads attributes
before `Health`, so a high-realm entity's current health is not prematurely
clamped to its unscaled maximum during load. Ordinary refresh and promotion do
not heal; a lower maximum only clamps excess health. True death respawn and End
return are distinct lifecycle cases.

## Damage

The outgoing calculation records each stage:

```text
(attack_attribute + flat_bonus) * move_multiplier * (1 + additive_bonus_sum) * total_multiplier
```

The attack attribute already includes realm and equipment modifiers. It must
not be scaled by the realm a second time. The existing item target bonus and
enchantment path remains explicit, followed by ordinary `hurt`; vanilla armor,
effects and enchantments apply once. The additional typed resistance step is
applied once after that mitigation and before absorption. Unknown or bypassing
damage sources are not silently treated as physical attacks. Diagnostics
distinguish requested damage, post-vanilla damage, resistance and health loss.
`stats info` also lists base/effective values and modifier identities, amounts
and operations, so realm, equipment and temporary contributions can be traced.
Diagnostics cap entity output and modifier lists; `modifierCount` exposes
whether a list was truncated.

The available resistance channels are physical and the five elements. Adding
a numerical channel does not itself imbue a weapon or activate an unimplemented
technique. Body-technique effects remain data until an explicit equipment and
activation policy connects them. The ledger's abstract duel power is also
unchanged: physical avatar attributes are derived from the person's realm,
while simulated off-screen outcomes still use `world_sim/realms.json`.

### Independent penetration (0.48.2)

`StatsRuntime.penetration(DamageType)` resolves exactly one of the synchronized
penetration attributes. Metal, wood, water, fire and earth each use
`<element>_resistance_penetration`; physical retains the old
`resistance_penetration` registry ID so saved physical bases and modifiers remain
readable. That ID is now physical-only, with explicit English/Chinese labels.
Old values are not copied into elemental attributes; every new channel starts
at zero. Their common limits still come from `StatsRules.limits`.

The selected attacker's value subtracts percentage points from the selected
resistance once, with no negative effective resistance. An absent living
attacker contributes zero. Source classification and armor remain unchanged:
physical and fire currently have live mappings; the four other elemental
attack routes are not implemented by this attribute split. The preview command
still accepts an explicit scalar; live `stats trace` records the selected type
and penetration, and `stats info` exposes all six values and modifiers.

Run `tools/elemental_penetration_evidence.py` after clean stage-1 acceptance for
bounded physical/fire isolation, mapped attacks and native save/restart of
independent bases/modifiers. See [the verification report](../testing/2026-10-15-elemental-penetration.md).

### Defender-normalized armor pressure (0.48.1)

`CombatRulesMixin` changes only the first armor clamp's input in vanilla
`CombatRules.getDamageAfterAbsorb`. `D_atArmor / defender_factor` supplies
pressure; full `D_atArmor` still supplies final damage. The original constants,
clamp bounds, weapon enchantment call and native armor/toughness limits remain
Minecraft-owned. Server realm assignment resolves through the existing rules;
missing, malformed or invalid assignment falls back to scale one. Scale one
preserves the original float without arithmetic reconstruction.

Vanilla armor bypass, shields, armor wear, invulnerability-frame differences,
potion resistance, Protection, typed resistance and absorption keep their native
ordering. Normalization applies to every source reaching armor, including an
otherwise unmapped damage type. Breach changes the normalized armor reduction
once. The base armor cap does not cap combined reduction across other layers.

Armor observations use the current NeoForge damage container identity so
nested damage cannot reuse another hit's values. The trace exposes actual armor
input, defender scale, pressure, effective armor before enchantment and the
actual armor-stage output; skipped armor has no fabricated input. The common
required mixin configuration is packaged and registered in mod metadata.

No equipment or innate armor is added. `tools/realm_armor_evidence.py` exercises
explicit fixtures and mapped client attacks; `validate_realm_stats.py --jar
build/libs/myvillage-0.48.2.jar` also verifies packaged hooks. Keep owner combat
feel, multiplayer and third-party damage-overhaul compatibility unverified
until observed. See [the armor verification report](../testing/2026-10-15-realm-armor.md).

## Regions and wildlife

`data/myvillage/wilderness/rules.json` maps region IDs to inclusive beast and
rogue realm-stage ranges. Region IDs, generated region tiers and realm indices
are separate concepts. The existing region runtime is read-only; wilderness
consumes its position queries. Outside its supported area or outside the
overworld, no region is invented.

Natural wolves have biome spawn resources and a registered placement rule.
Their realm is sampled once at birth and saved. Moving across a region boundary
or reloading never rerolls it. Legacy command/egg wolves retain their baseline;
diagnostic assigned wolves are explicitly distinguished from natural spawns.
The existing empty loot table and combat presentation remain unchanged.

### Ordinary vanilla monsters (0.63.1)

**0.65.0:** [KB60](60_vanilla_exploration.md) extends new births with structure/scenario floors and explicit Nether ranges. The paragraphs below describe the original regional integration. Spawn eligibility, saved-birth compatibility and the shared damage path remain in force; the older other-dimension baseline statement no longer describes configured Nether births. End mobs and the four excluded special bosses remain unchanged in V1.

`VanillaMonsterRealms` consumes the same region `beast` ranges. Its separate
`wilderness/vanilla_monsters.json` declares eligible spawn reasons, excluded
special monsters and non-attribute damage types. Only `minecraft` entities in
the `MONSTER` category are eligible. Natural/chunk births, spawners (including
trial spawners), structures, patrols, events, reinforcements and other declared
gameplay births are covered; command/egg births and previously saved unassigned
entities retain baseline. Wither, dragon, warden and elder guardian are excluded.
No region assignment is invented outside the supported overworld area.

The finalize-spawn event records a pending birth before vanilla initialization;
the entity-join event applies permanent health/attack modifiers after native
size and attribute initialization, then fills health once. The saved
`myvillage:vanilla_monster_birth` records realm/stage, profile, actual region,
birth coordinates and reason. Later joins do not heal or reroll. Native slime
splits and hostile conversions inherit that record. Outer worldplan births
retain their actual outer region ID while consuming their profile's range;
actual region `tier` does not change that range.

Native bases such as ravager health can exceed the authored player/beast base
envelope. `RealmStats.vanillaDerived` preserves those bases and caps derived
values at the configured effective limits; it does not loosen authored-data
validation. The realm assignment still supplies the common growth factor for
armor pressure and non-attribute damage.

Melee and slime contact already consume the attack attribute. Declared arrow,
trident, projectile, magic, explosion and thorns sources from assigned vanilla
monsters instead apply one growth factor at `LivingIncomingDamageEvent` before
defense. This also covers explicitly assigned ruin monsters. Environmental
damage and unassigned attackers are unchanged. Potion duration, ongoing poison
without an attributable owner, explosion radius and block destruction are not
scaled. Existing armor/typed-resistance stages still execute once.

Bounded validation: `.venv-modal/bin/python -m tools.modal_runner run --preset vanilla-monster-realms --ref <commit>`.
The preset runs stats/wilderness unit tests, builds the deliverable jar, and
boots an isolated dedicated server twice for native spawn/damage/convert/split
fixtures and real save/restart. `src/realmEvidence/java` is opt-in through
`-PvanillaRealmEvidence`; packaging with that flag is forbidden. These are
developer fixtures, not stochastic spawn-density or mapped-player evidence.
See [the measured 0.63.1 report](../testing/2026-10-18-vanilla-monster-realms.md).

Rogue encounters select existing living, eligible unsected ledger people in
the local region. `WorldSimAvatars` is still the sole avatar spawner, and its
global identity map prevents a person appearing twice for two players. Wild
projections share global caps with sect projections and use bounded attempts
in already loaded chunks. If a region has no eligible person, no fictional
person is added to the ledger. Changed eligibility or distance withdraws the
projection. These avatars keep the existing invulnerability and disposition;
this change does not make them combatants.

Both numerical and wilderness rules are bundled startup data. Rebuild/restart
after changing them; `/reload` is not a numerical hot-reload promise.

## Validation

Run `tools/validate_realm_stats.py`, its mutation tests and the Java `stats.*`
and `wilderness.*` tests, alongside the gameplay/entity/cultivation/world-sim
gates required by `AGENTS.md`. The custom-entity validator checks declared
natural spawning resources instead of blanket-forbidding natural wolves.
The jar must contain both rule files and the wolf biome resources.
`wilderness info` reports the actual biome spawn table, difficulty, lighting and
a fixed-seed placement probe only for an already loaded FULL chunk; queries
neither load chunks nor consume the world's RNG.

`tools/realm_stats_evidence.py` collects bounded developer evidence using the
isolated physical-client capture session, server diagnostics and mapped input.
Retain failure attempts and distinguish setup, server, client, test and restart
times. The README command manual and acceptance ledger are the user-facing
entry point. Automated evidence does not establish owner combat feel, encounter
density or multiplayer acceptance.
See [the measured verification report](../testing/2026-10-14-realm-stats.md).


0.61.0 adds `myvillage:fire_spirit`, tagged as fire damage, for attributed fate burns
and bursts. It uses the existing fire-resistance channel without changing physical
hits or granting the other elemental attack routes. Derivative amounts enter the
ordinary mitigation path once, with explicit no-proc attribution; see [48](48_innate_fates.md).

## 玩家生命显示（0.80.1）

`client/hud/HealthBarLayer` 替换原版玩家爱心图层，读取已同步的 `getHealth`、`getMaxHealth`、`getAbsorptionAmount`，不改属性、治疗或伤害。朱砂条以真实生命上限为分母，吸收生命独立金条和数值；中毒、凋零、冰冻、回复及濒危有文字区分。`HealthBarLayout` 固定宽度和占用高度，不随生命值增加行数。

护甲／饥饿继续由原版绘制在共同基线，随后更新 NeoForge 的左右 HUD 高度预算，气泡、坐骑生命、物品名与动作提示避开生命条。创造、旁观及隐藏 GUI 沿用原版可见条件。定向检查为 `HealthBarLayoutTest` 与 Colab `tools/health_bar_evidence.py` 的画面；全套战斗与玩家手感不由此推定通过。

[0.80.1 生命条实机与构建记录](../testing/2026-10-22-player-health-bar.md)。
