## Why

MyVillage currently has no small, player-controlled aerial vehicle that exercises a server-authoritative input path. A first-iteration flying sword provides that gameplay slice while keeping combat, progression, persistence, and complex animation out of scope.

## What Changes

- Add the functional item `myvillage:rideable_flying_sword` to the `myvillage:main` creative tab with English and Chinese names, an item model, and a placeholder texture.
- Add a one-passenger flying-sword entity that the item summons beneath the player and mounts immediately.
- Add client-to-server movement input for forward/back, strafe, ascend, and descend; the client sends only bounded key state while the server computes position and velocity.
- Add owner binding, single-sword enforcement, recall, hover/deceleration, block collision, fall-distance reset, horizontal view-following orientation, and automatic cleanup.
- Add a client-only entity renderer that reuses the sword item model without GeckoLib or custom animation.
- Add focused validation, automated tests, dedicated-server startup coverage, usage documentation, and an explicit manual multiplayer/gameplay handoff.

## Capabilities

### New Capabilities

- `rideable-flying-sword`: Defines the item interaction, one-player vehicle, authoritative input protocol, movement rules, owner binding, lifecycle cleanup, side-safe rendering, resources, and acceptance behavior.

### Modified Capabilities

None.

## Impact

- Java runtime surfaces: item/entity registration, the flying-sword item and entity, custom payload registration and handling, player/entity lifecycle hooks, and the common mod entry point.
- Client surfaces: key-state collection, payload sending, entity renderer registration, and item-model rendering.
- Pack resources: item model, placeholder texture, and `en_us`/`zh_cn` translations.
- Validation/docs: contract validation, focused source/resource checks, Gradle tests/build, dedicated-server acceptance startup, and player-facing controls/verification commands.
- No new runtime dependency, GeckoLib integration, combat system, progression system, multi-passenger support, or cross-dimension persistence is introduced.
