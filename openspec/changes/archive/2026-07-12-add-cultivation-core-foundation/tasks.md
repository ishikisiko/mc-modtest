## 1. Profile Values And Codecs

- [x] 1.1 Add immutable `CultivationProfile`, `SpiritualRoot`, and `TechniqueProgress` values with the exact v1 defaults, defensive deterministic maps, `ResourceLocation` identifiers, and shared constructor/codec invariant validation.
- [x] 1.2 Implement version-1 profile, root, and technique-progress codecs that preserve unknown definition ids, reject unsupported schema versions and invalid numeric/root data, and leave an explicit versioned migration seam for later schema changes.
- [x] 1.3 Add focused profile tests for defaults, full codec round trip, root total/ranges, stability, negative progress/power/mastery, unknown ids, defensive copies, and immutable old-instance behavior.

## 2. Definition Registries And Shipped Data

- [x] 2.1 Add immutable codec-backed `RealmDefinition`, `RealmStageDefinition`, `SpiritualElementDefinition`, `TechniqueDefinition`, stable-string `TechniqueCategory`, and `TechniqueRequirements` with only the foundation metadata and invariants in the specs.
- [x] 2.2 Register synced datapack registries `myvillage:realm`, `myvillage:spiritual_element`, and `myvillage:technique` through `DataPackRegistryEvent.NewRegistry`, using persistent and network codecs and current `RegistryAccess` lookups.
- [x] 2.3 Add the five required element JSONs, three realm JSONs with all required stages, and `basic_breathing` under the exact `data/myvillage/myvillage/<registry>/` paths without adding gameplay thresholds or executors.
- [x] 2.4 Add complete `en_us` and `zh_cn` translations for every shipped realm, stage, element, and technique key.
- [x] 2.5 Add definition-codec and reference/membership tests, including stable category strings, duplicate/order/range failures, and valid realm-stage pairs.

## 3. Attachment, Service, And Lifecycle

- [x] 3.1 Register codec-backed attachment `myvillage:cultivation_profile` with `copyOnDeath` and no manual cultivation copy in `PlayerEvent.Clone`.
- [x] 3.2 Implement `CultivationService` as the only mutation entry with `getProfile`, replace/update, reset, realm-stage, scalar, root, and learned-technique operations; make failures atomic and successes call `setData` then synchronize once.
- [x] 3.3 Implement `CultivationEvents` for login, respawn, and dimension-change synchronization, preserving the full profile on true death and End return without penalties or duplicate copying.
- [x] 3.4 Add service/lifecycle-focused tests for registry validation, realm-stage membership, learn/forget/mastery invariants, reset behavior, and unchanged state after failed mutations.

## 4. Server-To-Client Snapshot

- [x] 4.1 Add clientbound `CultivationSnapshotPayload` with id `myvillage:cultivation_snapshot` and a registry-friendly `StreamCodec` for the complete validated v1 snapshot, excluding definition records.
- [x] 4.2 Integrate the snapshot through the existing single `ModPayloads` registration path while preserving `FlyingSwordInputPayload` flags, wire format, serverbound direction, and all current handler checks.
- [x] 4.3 Add a side-safe snapshot handler using `IPayloadContext#enqueueWork`, a read-only latest-snapshot `ClientCultivationState`, and `Dist.CLIENT` disconnect cleanup; keep common/server classes free of physical-client-only references.
- [x] 4.4 Add a real registry-friendly-buffer StreamCodec round-trip test covering non-default values and unknown ids, plus regression coverage for the existing flying-sword payload.

## 5. Administrator Commands

- [x] 5.1 Add a permission-level-2 `/myvillage cultivation` subtree in `CultivationCommands` and minimally delegate it from the existing root without refactoring other command branches.
- [x] 5.2 Implement `info [target]` and `reset <target>` with complete schema/profile output, `unawakened`, learned mastery, and raw-id `unavailable` markers.
- [x] 5.3 Implement `setrealm`, `setprogress`, `setstability`, and `setpower` through `CultivationService`, with current-registry realm-stage membership and atomic numeric validation.
- [x] 5.4 Implement deterministic five-element `setroot` and `clearroot`, requiring registered element ids, per-value `0..10000`, and an exact `10000` total while retaining a generic root model underneath.
- [x] 5.5 Implement `learn`, `forget`, and `setmastery` with current technique-registry validation, explicit learned-state rules, non-negative mastery, immediate synchronization, and no random awaken command.
- [x] 5.6 Add dynamic realm/stage/technique suggestions from current `RegistryAccess` and specific diagnostic errors that never partially modify a profile.

## 6. Deterministic Validation

- [x] 6.1 Add standard-library-only `tools/validate_cultivation_core.py` for the exact custom-registry paths, JSON schema/types/bounds, required ids, unique stage ids/orders, cross-registry references, realm-stage membership, and bilingual translation coverage.
- [x] 6.2 Add focused validator tests with positive shipped-data coverage and negative missing-reference, mismatched-stage, invalid-number, missing-required-id, and missing-translation fixtures.
- [x] 6.3 Wire the cultivation validator into the repository's documented validation flow without adding a third-party Python dependency or a brittle source-text-only substitute for codec/network tests.

## 7. Documentation And Capability Handoff

- [x] 7.1 Add concise `docs/ai-kb/28_cultivation_core.md` covering implemented and excluded behavior, v1 schema, registry keys/paths, attachment lifecycle, sync triggers, commands, validation, unknown-id behavior, migration policy, and later integration points.
- [x] 7.2 List and cross-link the new KB entry from `docs/ai-kb/INDEX.md` and the same-topic cultivation capability specs in accordance with docs-knowledge-base governance.
- [x] 7.3 Update README with every administrator command, the cultivation validation/build/server-smoke steps, registry/data paths, and the explicit statement that only cultivation data infrastructure exists with no meditation, breakthrough, or technique gameplay.
- [x] 7.4 Align `AGENTS.md` and directly related validation/command guidance with the new command and acceptance-prep workflow, while leaving existing town, sect, gallery, structure, and flying-sword instructions intact.

## 8. Verification And Closeout Evidence

- [x] 8.1 Run and record `openspec validate add-cultivation-core-foundation --type change --strict`, `python3 tools/validate_cultivation_core.py`, `./gradlew test`, and `./gradlew build`; any non-zero result blocks closeout.
- [x] 8.2 Run a bounded, explicitly stopped dedicated/acceptance server smoke and capture evidence that all three datapack registries, the attachment, cultivation commands, the clientbound snapshot, and the existing serverbound flying-sword payload register without registry, codec, path, direction, duplicate-handler, or client-only classloading errors.
- [x] 8.3 Execute or prepare the eleven specified command/persistence/death/End/dimension acceptance checks and record each real result as pass, fail, or `not_verified` without upgrading automated startup evidence into gameplay proof.
- [x] 8.4 Verify the final diff contains no generated reports, run worlds, caches, logs, structure/worldgen changes, new items/blocks/entities, excluded gameplay implementation, or flying-sword protocol changes.
- [x] 8.5 Apply the explicit owner scope exception for this change: intentionally omit the generic `openspec/config.yaml` feature version task and verify that `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, release metadata, and `CHANGELOG.md` remain unchanged. This owner decision overrides the conflicting general task rule for `add-cultivation-core-foundation` only.
- [x] 8.6 Complete CRAFT front-door evidence and verify the change is eligible for Commander-owned archive with all six new cultivation capabilities ready to synchronize after every implementation task and required gate passes.
