## Why

The mod has cultivation-themed settlements and structures but no player-facing cultivation data contract. A small, server-authoritative foundation is needed now so later awakening, meditation, progression, and technique-runtime changes can build on stable registries, persistence, synchronization, and validation instead of inventing incompatible state models.

## What Changes

- Add data-driven datapack registries for realm, spiritual-element, and technique definitions, with the minimum shipped definitions needed to validate the contracts.
- Add an immutable, codec-backed player cultivation profile stored with NeoForge Data Attachments and changed only through a validating server-side service.
- Define persistence and lifecycle behavior for login, true-death respawn, End return, dimension changes, and client disconnect cleanup.
- Add a server-to-client read-only cultivation snapshot and a side-safe client cache without any client-authoritative mutation path.
- Add permission-level-2 `/myvillage cultivation ...` administrator commands for inspection and deterministic profile editing.
- Add codec, stream-codec, invariant, lifecycle/service, registry-reference, and shipped-data validation, plus dedicated-server smoke coverage and current operator documentation.
- Keep the slice deliberately infrastructural: it does not add awakening generation, meditation, cultivation gain, breakthroughs, technique execution, combat effects, spiritual-power recovery, cooldowns, equipment slots, UI/HUD, items, blocks, entities, NPC systems, region-qi integration, sect-facility integration, or flying-sword restrictions.
- Treat version publication and changelog updates as explicitly outside this change, per the owner's accepted scope.

## Capabilities

### New Capabilities

- `cultivation-player-profile`: Immutable cultivation profile schema, value invariants, codec behavior, and the single server-side mutation boundary.
- `cultivation-definition-registries`: Datapack registry contracts for realms, spiritual elements, and techniques, including shipped definition paths and reference rules.
- `cultivation-persistence-lifecycle`: Data Attachment registration, persistence, true-death preservation, End-return semantics, and player lifecycle hooks.
- `cultivation-state-synchronization`: Server-authoritative snapshot delivery, synchronization triggers, client cache behavior, and dedicated-server side safety.
- `cultivation-debug-commands`: Permissioned `/myvillage cultivation` inspection and deterministic profile-editing commands with registry-backed validation and suggestions.
- `cultivation-core-validation`: Automated codec, stream-codec, invariant, registry-reference, shipped-data, build, and dedicated-server smoke validation.

### Modified Capabilities

None. Existing baseline requirements remain unchanged; archive will add these six capabilities as new baseline specs.

## Impact

- Java runtime: a new `com.example.myvillage.cultivation` package, a client-only cultivation cache, Data Attachment/event registration, minimal `/myvillage` command delegation, and payload registration through the existing `ModPayloads` architecture.
- Data/resources: three synced datapack registries, minimal `myvillage` definitions, and `en_us`/`zh_cn` translations. No structure NBT, town/sect worldgen, or optional-mod resource changes are included.
- Validation: focused Java tests, `tools/validate_cultivation_core.py`, existing Gradle test/build integration, and a bounded dedicated-server/acceptance smoke. No new third-party Python dependency is required.
- Documentation: `docs/ai-kb/28_cultivation_core.md`, the KB index, README administrator commands and validation steps, and archive-time baseline spec synchronization.
- Compatibility: Minecraft `1.21.1`, NeoForge `21.1.233`, Java `21`, mod id `myvillage`; existing flying-sword input payload flags, wire format, direction, and server checks remain untouched.
