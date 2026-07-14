## Why

MyVillage has no player melee-combat foundation that can replace one supported weapon's vanilla attack path while keeping timing, hit detection, damage, and movement server-authoritative. The supplied PAL 1.1.4 library makes a scoped five-move sword style possible, but its exact API, resource format, side safety, licensing, and runtime compatibility must be proven before combat behavior depends on it.

## What Changes

- Integrate the supplied `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` as a required, non-shaded local dependency with an explicit missing-file failure, a documented MIT-license/distribution decision, and client/dedicated-server smoke evidence.
- Register client-only PAL controllers for local and tracked remote players, load original Blockbench/Bedrock-format player animations, and support deterministic play, correction, stop, transition, priority, and first-person behavior.
- Add the independent functional item `myvillage:qingfeng_sword` with diamond-sword-equivalent durability, attack damage, attack speed, enchantability behavior, creative-tab exposure, bilingual names, an original pixel texture, model, recipe, Item Contract, and packaged-resource checks.
- Add a persisted server-authoritative vanilla/cultivation combat preference plus a separate transient server combat session that never enters `CultivationProfile`.
- Add configurable `R` mode switching and attack-key interception only for the Qingfeng Sword in cultivation mode; vanilla mode and every unsupported held item retain their normal attack and mining behavior.
- Add input-only combat payloads, authoritative nearby-player animation broadcasts, rate limiting, sequence-based correction, lifecycle cleanup, and protocol-version evolution.
- Add one data-oriented five-move basic sword style with buffered combo progression, deterministic server-tick timing, move-specific swept hit volumes, wall/PvP/team filtering, per-move hit deduplication, bounded fifth-move stepping, and optional debug visualization.
- Add a combat damage service that preserves the applicable vanilla/NeoForge damage-event, armor, invulnerability, PvP, enchantment, knockback, fire-aspect, and durability behavior without a duplicate vanilla attack or undeclared sweeping attack.
- Add focused Java tests, resource/source validators with negative fixtures, Gradle test/build gates, PAL client and dedicated-server smoke gates, jar inspection, documentation, and a real-client/manual multiplayer ledger whose unobserved items remain `not_verified`.

## Capabilities

### New Capabilities
- `player-animation-integration`: Defines the exact PAL dependency, client-only controller lifecycle, animation resource contract, playback/stop/transition/priority behavior, remote-player visibility, first-person boundary, and dedicated-server compatibility gate.
- `sword-combat-foundation`: Defines the Qingfeng Sword, persisted mode preference, transient combo state machine, bounded input and authoritative synchronization, five move definitions, hit resolution, damage compatibility, lifecycle cleanup, debug surface, and gameplay acceptance behavior.

### Modified Capabilities
- `cultivation-meditation`: Treat accepted sword-attack intent as an attack interruption while preventing meditation and a combat action from remaining active together.
- `cultivation-advancement`: Treat accepted sword-attack intent as an attack interruption while preventing advancement and a combat action from remaining active together.
- `resource-export`: Package the Qingfeng Sword's client/data resources and PAL player-animation JSON resources in the practical mod jar without changing generated structure export.
- `validation`: Add focused PAL/combat/item/resource/protocol/side-safety validation, staged Gate A/B/C evidence, jar checks, bounded server smoke, and explicit real-client `pass`/`fail`/`not_verified` handoff.

## Impact

- Build/dependency surfaces: root PAL jar resolution, NeoForge required dependency metadata, and client/server run classpaths; no PAL shading, unpacking, or copied classes.
- Common runtime: item and attachment registration, combat definitions/session service, event integration, authoritative hit/damage logic, lifecycle hooks, and payload registration.
- Client runtime: key mapping, cancelable attack-key handling, read-only combat snapshots, PAL controller integration, animation correction, and optional debug rendering.
- Resources: bilingual item/messages, model, original texture, recipe, seven original player animations, Item Contract, and validator fixtures.
- Existing cultivation behavior: meditation/advancement interruption routing is extended, while `CultivationProfile`, cultivation snapshot authority, flying-sword input authority, GuideME bindings, and unsupported-item vanilla controls remain intact.
- Acceptance and release surfaces: README, KB index/integration note, relevant baseline specs, focused validators/tests, build/server/client evidence, jar contents, changelog, and the single-sourced release rule in `openspec/config.yaml`.
