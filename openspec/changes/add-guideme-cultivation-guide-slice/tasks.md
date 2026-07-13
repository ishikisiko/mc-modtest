## 1. Dependency And Preview Wiring

- [x] 1.1 Resolve GuideME 21.1.17 API and runtime artifacts from Maven Central, declare the required `BOTH`-side NeoForge dependency, and keep the root release jar outside build wiring and packaging.
- [x] 1.2 Package authoritative root `guidebook/` pages through `processResources` and add a `runGuide` client configuration that watches that source and validates/opens `myvillage:cultivation`.

## 2. Guide And Handbook

- [x] 2.1 Add the `myvillage:cultivation` guide definition and the exact three Chinese pages plus path-matched `_en_us` translations covering only the released loop.
- [x] 2.2 Register the one-stack `myvillage:cultivation_handbook`, open the guide client-side through GuideME without a MyVillage payload, and expose it in `myvillage:main`.
- [x] 2.3 Add the handbook model inheriting `guideme:item/guide_base` and bilingual item name/tooltip resources without adding a custom handbook texture.

## 3. Deterministic Validation

- [x] 3.1 Add `tools/validate_guideme_cultivation_guide.py` to check dependency, guide, content, handbook, preview, documentation, release, and practical-jar invariants.
- [x] 3.2 Add negative validator tests for representative dependency, translation, reference, content, model, preview, command, and package drift.

## 4. Documentation And Release

- [x] 4.1 Add the concise GuideME cultivation KB page, index it, and synchronize README, validation guidance, AGENTS probes/gates, installation, commands, live preview, and explicit real-client `not_verified` acceptance entries.
- [x] 4.2 Bump the small-feature release from 0.25.0 to 0.25.1 and update `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.

## 5. Closeout Verification

- [x] 5.1 Run strict change and baseline spec validation, the focused validator/tests, existing mod-item and cultivation validators/tests, and Gradle tests/build.
- [x] 5.2 Inspect the practical jar for the exact GuideME/MyVillage resources and absence of embedded GuideME code or a handbook texture.
- [x] 5.3 Run bounded `runGuide` client and acceptance-server startup smokes, stop both cleanly, and preserve every unobserved real-client rendering or interaction item as `not_verified`.

## 6. Owner-Reported G Hotkey Correction

- [x] 6.1 Release GuideME's default `G` item-index hotkey by changing MyVillage stop meditation from `G` to `X`, without adding GuideME-specific interception, remapping, or saved-binding migration; update focused validators and negative fixtures.
- [x] 6.2 Record the accepted guide UI and historical 0.25.1 G-hotkey failure, synchronize README/AGENTS/KB and relevant baseline/change specs to V/B/X/N, and bump the validated fix to 0.25.1-fix1 under the repository version rule.
- [x] 6.3 Run strict change/baseline validation, all focused cultivation/GuideME validators and tests, Gradle tests/build, practical-jar inspection, and bounded acceptance-server smoke; leave post-fix G behavior `not_verified` until a real client retests it.
