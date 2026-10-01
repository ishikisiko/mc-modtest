## 1. Contract

- [x] 1.1 Author `data/myvillage/combat/index.json`, `style/basic_sword.json`, and `weapon/qingfeng_sword.json` from the current Java constants.
- [x] 1.2 Record the schema, runtime shape, probe grammar, and worker boundaries in `design.md` and strictly validate this change.

## 2. Java Runtime (style data and weapon registry)

- [x] 2.1 Add the JSON loader and `CombatStyles` registry; extend the definition records with kind, sound ids, and camera cues; reject unknown fields and invariant violations with file and field named.
- [x] 2.2 Add the equivalence test against the legacy constants, then delete `BasicSwordStyle` and keep the expectations as literals.
- [x] 2.3 Select the style by held item on the server and remove every direct `ModItems.QINGFENG_SWORD` combat check.
- [x] 2.4 Carry the move id in `CombatImpactPayload` and bump the payload protocol to `7`.
- [x] 2.5 Load rigs and geometry per weapon on the client, register the item extension for every registered weapon, read feedback and camera cues from the move, and rename the `Qingfeng*` client classes to weapon-neutral names.
- [x] 2.6 Make the smoke commands follow the held weapon and add `third_person <move> <tick>` and `third_person release`.
- [x] 2.7 Update and extend the Java tests; `./gradlew test` and `./gradlew build` pass.

## 3. Generator And Validator

- [x] 3.1 Make `tools/gen_sword_pal_anims.py` read move timing and kind from the style file, group pose tables per style, and keep `sword_combat.json` byte-identical.
- [x] 3.2 Rework `tools/validate_sword_combat_foundation.py` to validate the data files and cross-file constraints, without per-move literals.
- [x] 3.3 Reduce source checks to the refactor-stable authority and side invariants and record which removed check is covered by which data check or Java test.
- [x] 3.4 Add the Qingfeng baseline regression test and update the validator and generator tests.
- [x] 3.5 Reconcile the validator with the merged Java runtime, including the packaged-jar checks for the new data files.

## 4. Capture Tooling

- [x] 4.1 Add `tools/combat_capture/` session control, rcon, scene setup, and process cleanup on the repository's own run tasks, holding the shared heavy-work lock.
- [x] 4.2 Add hot reload of combat client resources with a confirmed rig-load wait.
- [x] 4.3 Add per-move first-person stills at rig key ticks, and third-person stills through the freeze probe.
- [x] 4.4 Add before/after comparison sheets and the static review page under `out/preview/combat_capture/<label>/`.
- [x] 4.5 Add unit tests for the pure parts (key-tick derivation, manifest, pairing, page) and a usage section.

## 5. Integration And Evidence

- [x] 5.1 Merge the three work streams and resolve conflicts.
- [x] 5.2 Run the focused and aggregate validators, Python tests, `./gradlew test`, `./gradlew build`, jar inspection, and a dedicated acceptance-server start and clean stop.
- [x] 5.3 Run a physical-client smoke with mapped clicks through all five moves and confirm server damage.
- [x] 5.4 Capture the five moves before (0.27.1-fix1) and after with the new tool and compare per move and key.
- [x] 5.5 Review the merged diff independently for behavior drift.

## 6. Docs And Release

- [x] 6.1 Add the KB note for combat data and capture tooling, list it in `docs/ai-kb/INDEX.md`, and update `docs/ai-kb/32_pal_combat_integration.md`.
- [x] 6.2 Update `README.md` commands and the real-client ledger, and the combat rule in `AGENTS.md`.
- [x] 6.3 Apply the version rule: bump to `0.28.0` in `gradle.properties` and mod metadata, update README jar-name examples, and add the CHANGELOG entry.
- [x] 6.4 Re-run the release-sensitive build and server gates after the version update.
- [x] 6.5 Keep every unobserved real-client item `not_verified`; hand the before/after page to the owner.
