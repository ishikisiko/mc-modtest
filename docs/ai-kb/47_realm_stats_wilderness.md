# Realm Statistics and Wilderness

The numerical contract is shared by physical player combat and beasts. It is
defined by [realm-combat-stats](../../openspec/specs/realm-combat-stats/spec.md)
and [regional-wilderness](../../openspec/specs/regional-wilderness/spec.md).
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
the player's incomplete advancement ladder is not expanded by the numerical
ladder used for higher-realm ledger avatars and beasts. Original entity base
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
