## 1. Contract And Art Source

- [x] 1.1 Write `genops/contracts/entities/simple_fox.yaml` and pass the skill's Entity Contract schema validator.
- [x] 1.2 Extract the Minecraft 1.21.1 fox texture truth source and record its hash, filled UV island mask, semantic regions, and vanilla-model route under `art/entities/simple_fox/`.
- [x] 1.3 Produce the orange/cream simple-fox texture through a deterministic masked transform and run texture validation.
- [x] 1.4 Exercise Codex built-in imagegen for concept and atlas candidates, enforce deterministic composite boundaries, and record an adopted or rejected visual verdict without direct API credentials.

## 2. Runtime And Client

- [x] 2.1 Register `myvillage:simple_fox`, its attributes, and its fox-compatible spawn placement on the mod event bus.
- [x] 2.2 Implement `SimpleFoxEntity` as a bounded vanilla `Fox` subclass with no custom tick scan or custom combat, and keep offspring on the `myvillage:simple_fox` type.
- [x] 2.3 Add a client-only renderer using the vanilla fox model layer and the MyVillage simple-fox texture.

## 3. Items And Data Resources

- [x] 3.1 Register `simple_fox_spawn_egg`, expose it in `myvillage:main`, and add its item model plus `en_us` and `zh_cn` translations.
- [x] 3.2 Add the intentional empty loot table, taiga biome tag, conservative NeoForge spawn modifier, and verify all resource ids agree with the Entity Contract.

## 4. Validation, Documentation, And Handoff

- [x] 4.1 Add a focused custom-entity integrity validator and tests covering registration, client boundary, texture/model/lang/loot completeness, and the paired natural-spawn layers.
- [x] 4.2 Update `README.md`, `docs/ai-kb/26_custom_entities.md`, and the KB index with `/summon`, `/give`, `/data get`, save/reload, and natural-spawn/manual visual checks.
- [x] 4.3 Run the skill script tests, contract/texture validators, focused tests, `./gradlew test`, and `./gradlew build`; inspect the jar for entity resources.
- [x] 4.4 Start the dedicated `acceptanceServer` run far enough to prove registration and data-pack loading without client-class leakage, then stop it cleanly and save the log evidence.
- [x] 4.5 Bump this new runtime capability to `0.21.0` by updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.
- [x] 4.6 Prepare atlas and command evidence, record server summon/save-reload smoke, inspect the visible source PNGs, and leave spawn-egg use, natural-frequency, multiplayer, and rendered multi-view acceptance at `human_review_pending` until an owner verdict is recorded.
- [x] 4.7 Revalidate the revised rectangular-texture skill, migrate the Entity Contract, and rerun the focused validator, tests, build, and OpenSpec checks.
- [x] 4.8 Record the owner's in-game acceptance verdict and close the visual-review handoff.
