## Why

MyVillage has no custom entity path to exercise registration, server/client separation, pack resources, natural spawning, and visual handoff as one complete feature. A deliberately small fox provides a low-risk end-to-end test of the custom-entity skill without introducing a new animation runtime or complex combat system.

## What Changes

- Add `myvillage:simple_fox` as a summonable, saveable creature that reuses the vanilla fox model, animation set, sounds, and inherited fox behavior.
- Add a custom texture, spawn egg, translations, empty first-iteration loot table, taiga biome tag, biome modifier, and spawn placement registration.
- Add an Entity Contract, focused integrity validator, build/server smoke evidence, usage documentation, and a manual in-game visual verdict handoff.
- Explicitly keep GeckoLib, custom geometry, glow, custom audio, combat skills, taming changes, and new variants out of scope.

## Capabilities

### New Capabilities

- `custom-entity-runtime`: Defines the contract, runtime/client boundary, resource completeness, natural-spawn pairing, validation, and human visual-review requirements for the first MyVillage custom entity.

### Modified Capabilities

None.

## Impact

- Java registration and runtime code under `com.example.myvillage.entity`, client renderer registration, and the existing item/creative-tab registry.
- Client and data resources under `assets/myvillage` and `data/myvillage`.
- Entity contract/art source evidence, a focused validator, README commands, and a concise knowledge-base note.
- No dependency, Minecraft, NeoForge, or GeckoLib version change.
