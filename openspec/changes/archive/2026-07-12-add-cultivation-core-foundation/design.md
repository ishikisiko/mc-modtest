## Context

MyVillage currently has cultivation-themed structure generation but no player cultivation runtime. The existing Java entry point owns a large `/myvillage` command tree, `ModPayloads` owns one NeoForge `RegisterPayloadHandlersEvent` listener for the flying-sword input payload, and client registrations are isolated behind a `Dist.CLIENT` subscriber. This change must add the first player-data slice without turning those existing classes into cultivation implementations or changing the flying-sword protocol.

The target is Minecraft `1.21.1`, NeoForge `21.1.233`, and Java `21`. The design therefore uses NeoForge Data Attachments, `DataPackRegistryEvent.NewRegistry`, custom payloads registered through `PayloadRegistrar`, and the current player/client lifecycle events. It does not use Forge capabilities, `SavedData` for per-player state, `SimpleChannel`, or older registry/network APIs.

## Goals / Non-Goals

**Goals:**

- Establish a stable, immutable, codec-backed player cultivation profile with explicit numeric and collection invariants.
- Make the logical server authoritative and route every profile mutation through one validating service.
- Load realm, spiritual-element, and technique definitions from synced datapack registries.
- Persist the profile across saves, true death, respawn, End return, and dimension travel without duplicate copying.
- Deliver a read-only profile snapshot to the owning client at all required lifecycle and mutation points.
- Provide deterministic administrator commands, automated validation, dedicated-server smoke coverage, and concise operator/AI documentation.
- Preserve unknown or removed definition identifiers in persisted profiles so a datapack removal does not make the player record undecodable.

**Non-Goals:**

- No meditation/cultivation loop, random or automatic spiritual-root awakening, awakening monument/item, cultivation-gain rule, automatic advancement/breakthrough, technique executor, damage, projectile, buff, spiritual-power cap/recovery, cooldown, combat attribute, or technique equipment slot.
- No HUD, character screen, technique manual item, other new item, block, entity, NPC, quest, alchemy, or crafting system.
- No region-qi, sect-facility, structure/worldgen, or flying-sword realm-restriction integration. `RegionProfile.qi` is not player spiritual power.
- No change to `FlyingSwordInputPayload` flags, wire format, direction, ownership/passenger/world/liveness checks, or input handler registration.
- No release preparation, version bump, jar-name update, or changelog update.

## Decisions

### 1. The server owns profile truth and `CultivationService` owns mutation

`CultivationProfile` is the persisted truth on `ServerPlayer`. The client receives snapshots for display/read access only; this change registers no client-to-server cultivation payload. Commands and lifecycle listeners may read the profile, but they MUST call `CultivationService` for changes rather than calling `setData` themselves.

The service exposes `getProfile`, `replaceProfile`/`updateProfile`, `resetProfile`, `setRealmAndStage`, `setProgress`, `setStability`, `setSpiritualPower`, `setSpiritualRoot`, `clearSpiritualRoot`, `learnTechnique`, `forgetTechnique`, `setTechniqueMastery`, and `syncToClient`. Each mutator follows one transaction-shaped path:

1. Read the old immutable value.
2. Validate command/service input and any registry references.
3. Construct and intrinsically validate a new immutable value.
4. If validation fails, return a controlled failure and leave the old attachment untouched.
5. On success, call `ServerPlayer#setData` exactly once and send exactly one new snapshot.

`learnTechnique` adds a registered technique at zero mastery; `setTechniqueMastery` requires that the technique is already learned, so changing mastery cannot silently learn it. Technique requirements remain descriptive data in this foundation and are not a gameplay eligibility gate. `setRealmAndStage` validates membership but does not invent progress loss, power changes, or breakthrough behavior.

Alternatives rejected:

- Mutable attachment objects were rejected because in-place mutation can bypass attachment replacement/dirty behavior and can alias old snapshots.
- Event/command-specific mutation logic was rejected because it would duplicate invariants and make later progression code bypassable.
- Client-authoritative updates were rejected because profile state affects future gameplay and must remain cheat-resistant.

### 2. The profile is an immutable versioned value graph

The v1 shape is:

```text
CultivationProfile
  schemaVersion: int (= 1)
  realmId: ResourceLocation (default myvillage:mortal)
  stageId: ResourceLocation (default myvillage:mortal_unawakened)
  cultivationProgress: long (default 0, >= 0)
  stability: int (default 0, 0..100)
  currentSpiritualPower: long (default 0, >= 0)
  spiritualRoot: Optional<SpiritualRoot> (default empty)
  learnedTechniques: Map<ResourceLocation, TechniqueProgress> (default empty)

SpiritualRoot
  affinitiesBasisPoints: Map<ResourceLocation, int>
  every value: 0..10000
  total when present: exactly 10000

TechniqueProgress
  masteryPoints: long (>= 0)
```

There is no persisted `awakened` boolean; awakening is derived from `spiritualRoot.isPresent()`. All identifiers use `ResourceLocation`, no enum ordinal is serialized, and maps are defensively copied into deterministic immutable maps. A generic affinity map deliberately permits more than the shipped metal/wood/water/fire/earth definitions. Zero-valued entries are legal, while an existing root still must total exactly `10000`.

Factories/canonical constructors enforce the same invariants as the codecs. `CultivationProfile.CODEC`, `SpiritualRoot.CODEC`, and `TechniqueProgress.CODEC` return structured decode errors for invalid values. Equality and full codec round trips cover every field, including unknown `ResourceLocation` values.

The chosen integer widths avoid premature caps: progress, current power, and mastery use non-negative `long`; stability and basis points use bounded `int`. Saturating arithmetic is not introduced because this change does not perform gameplay accumulation.

### 3. Three synced datapack registries own definition data

`ModCultivationRegistries` declares and registers these `ResourceKey<Registry<T>>` keys on the mod event bus:

| Registry | Registry key | Shipped JSON directory |
|---|---|---|
| Realm definitions | `myvillage:realm` | `src/main/resources/data/myvillage/myvillage/realm/` |
| Spiritual elements | `myvillage:spiritual_element` | `src/main/resources/data/myvillage/myvillage/spiritual_element/` |
| Techniques | `myvillage:technique` | `src/main/resources/data/myvillage/myvillage/technique/` |

The doubled `myvillage` path segment is intentional under the NeoForge `1.21.1` datapack-registry rule: `data/<entry namespace>/<registry-key namespace>/<registry-key path>/`. Registration uses `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, persistentCodec, networkCodec)` with a non-null network codec for each registry. The same immutable definition codec can be used for persistence and registry synchronization. Runtime queries use the current `RegistryAccess#registryOrThrow`/registry lookup instead of static definition tables.

Definition records are deliberately narrow:

- `RealmDefinition`: non-empty translation key, non-negative sort order, a non-empty ordered list of `RealmStageDefinition`, and optional next-realm id.
- `RealmStageDefinition`: globally stable stage `ResourceLocation`, non-empty translation key, and non-negative stage order.
- `SpiritualElementDefinition`: non-empty translation key, non-negative sort order, and optional `0x000000..0xFFFFFF` display color. There are no matchup or multiplier fields.
- `TechniqueDefinition`: non-empty translation key, stable-string `TechniqueCategory`, non-negative numeric grade, a duplicate-free element-id list, and `TechniqueRequirements`.
- `TechniqueCategory`: encoded by stable strings, initially `core`, `active`, `movement`, and `body`; it is never encoded by ordinal.
- `TechniqueRequirements`: optional minimum realm, optional minimum stage, and a map of element ids to affinity basis points in `0..10000`. A minimum stage requires a minimum realm, and cross-reference validation confirms the stage belongs to that realm.

No threshold, qi cost, cooldown, damage, executor, effect-script, or equipment-slot schema is added. `basic_breathing` is an element-neutral definition and therefore may use an empty element list/affinity map; empty means no element restriction, not an implicit five-element effect.

The minimum shipped entries are the five named elements, `mortal`, `qi_refining`, and `foundation_establishment`, stages `mortal_unawakened`, `mortal_qi_sensed`, `qi_refining_1` through `qi_refining_9`, and `foundation_early`, plus `basic_breathing`. Realm data links each stage and the next realm where applicable. Matching `en_us` and `zh_cn` translation entries are shipped for every definition and stage.

Alternatives rejected:

- Java enums/static maps for realms, elements, or techniques were rejected because they prevent datapack extension and reload semantics.
- Storing registry holders in the player profile was rejected because raw ids survive definition removal more predictably and remain codec/network friendly.
- Adding speculative balance fields was rejected because later gameplay changes should define thresholds only when their rules exist.

### 4. Unknown IDs are preserved; availability is resolved at use time

Profile codecs validate identifier syntax but do not consult live registries. Therefore an old player record containing a removed realm, stage, element, or technique id still decodes, saves, and synchronizes. Reads expose the raw id and resolve it against the current registry only for presentation or an operation that needs a definition.

Unavailable ids are handled as follows:

- `/myvillage cultivation info` prints the raw id with an `unavailable` marker.
- Existing unknown entries are not silently deleted, remapped, or replaced during login.
- Snapshot decoding preserves them exactly.
- Administrator mutations cannot set a missing realm/stage, learn a missing technique, set mastery for a missing technique, or install a root whose elements are not registered. Reset remains the explicit route back to a known default profile.
- A stage supplied to `setRealmAndStage` must occur in the selected realm's stage list.
- Definition requirements that reference missing ids are treated as unavailable/unsatisfied at runtime; the shipped-data validator fails such references before build rather than silently ignoring them.

This separates persistence compatibility from definition availability. It avoids login/world-load crashes after datapack removal without pretending the missing definition can be used.

### 5. Schema versioning is explicit and migrations are additive

`schemaVersion` is serialized and v1 accepts only version `1`. Unknown definition ids are valid v1 data; an unsupported profile schema is a different problem and produces a controlled codec error rather than being guessed or silently reset.

When the schema changes, the implementing change must retain a decoder for each supported old shape, decode into a version-specific DTO, apply a pure `vN -> vN+1` migration, validate the resulting current profile, and only then write the current version back on a successful mutation/save. New fields require explicit defaults in that migration. The planned technique-equipment-slot change is the first likely schema bump; it must not retrofit a field into the v1 codec without migration coverage.

Alternative rejected: accepting arbitrary future versions into the v1 record would preserve bytes poorly, allow invariants the current runtime does not understand, and make downgrade behavior unsafe.

### 6. Data Attachment `copyOnDeath` is the only copy mechanism

`CultivationAttachments` registers `myvillage:cultivation_profile` in the NeoForge attachment-type registry with `CultivationProfile::defaultProfile`, `CultivationProfile.CODEC`, and `AttachmentType.Builder#copyOnDeath()`.

This is chosen instead of a manual `PlayerEvent.Clone` copier. In NeoForge `21.1.233`, serializable entity attachments already copy when the player returns from the End; `copyOnDeath()` adds preservation for true-death respawn. The implementation therefore MUST NOT register a second clone-copy path. The attachment's codec copy creates a distinct immutable value graph, avoiding aliases.

Lifecycle listeners have synchronization responsibilities only:

- `PlayerLoggedInEvent`: send the current/default profile.
- `PlayerRespawnEvent`: send the already-copied profile after true death or End return.
- `PlayerChangedDimensionEvent`: send the current profile after travel.
- `ClientPlayerNetworkEvent.LoggingOut` on `Dist.CLIENT`: clear the local snapshot.

True death preserves the complete profile with no cultivation/progress/power/stability/root/mastery penalty. End return performs the framework's normal attachment copy and then synchronization; no profile field is merged or duplicated. Multiple idempotent snapshot triggers are acceptable, but there is exactly one attachment-copy owner.

Alternatives rejected:

- Manual `PlayerEvent.Clone` was rejected because it would have to branch on `isWasDeath()` and would be easy to combine accidentally with `copyOnDeath`, causing double-copy behavior.
- Per-player `SavedData` and legacy capability APIs were rejected because Data Attachments are the current native entity persistence mechanism.

### 7. One clientbound snapshot is integrated into the existing payload registrar

`CultivationSnapshotPayload` is a play-to-client custom payload with id `myvillage:cultivation_snapshot` and one immutable profile/snapshot field. Its `StreamCodec<RegistryFriendlyByteBuf, ...>` is derived from the validated profile codec (for example through `ByteBufCodecs.fromCodecWithRegistries`) so tests exercise the same shape used by persistence. It does not retransmit realm, element, or technique definitions; their non-null datapack registry network codecs provide those definitions to the client registry access.

`ModPayloads` retains one `RegisterPayloadHandlersEvent` listener and one protocol registrar. It delegates registration of the new clientbound payload while leaving `FlyingSwordInputPayload` registered as the existing play-to-server payload. No second event listener registers either type and no handler direction is broadened.

`CultivationService#syncToClient` uses `PacketDistributor.sendToPlayer` so only the owning player receives their profile. Snapshot handling uses `IPayloadContext#enqueueWork`; on the registrar's current main-thread default this executes immediately, and it remains correct if the handler is later moved to the network thread.

`ClientCultivationState` stores only the latest immutable snapshot and exposes read-only access plus internal replace/clear operations. A client-only subscriber installs/owns the snapshot sink and handles disconnect cleanup. Common registration, payload, service, attachment, registry, event, and command classes import no `net.minecraft.client` or other physical-client-only type. A small common-safe receiver bridge may carry the snapshot from the common payload handler to the installed client sink; that bridge itself has no client-only type references and defaults to no-op until client initialization.

Alternatives rejected:

- Automatic attachment synchronization was rejected because this change explicitly needs a narrow owning-client snapshot contract and explicit lifecycle triggers, not tracking-based broadcast.
- Embedding definitions in every snapshot was rejected as redundant and prone to divergence from synced datapack registries.
- Reusing the flying-sword input payload was rejected because the directions, trust boundaries, and data lifetimes are unrelated.

### 8. Administrator commands are a thin dynamic-registry adapter

`CultivationCommands` builds a `cultivation` literal that `MyVillageMod` attaches beneath its existing permission-level-2 `/myvillage` root. No existing town, sect, gallery, spawn, or flying-sword command is refactored.

Targets use the standard single-player argument. `info` without a target uses the executing player and `info <target>` accepts an explicit player. Numeric Brigadier arguments enforce their basic lower/range bounds, while the service remains the final validator. Resource-id suggestions enumerate keys from the command source's current `RegistryAccess`, not hard-coded lists.

Realm, stage, element, and technique ids are resolved against the current registries. The five-argument `setroot` convenience command maps metal/wood/water/fire/earth to the shipped `myvillage:` element ids, checks that all five definitions exist, checks every value in `0..10000`, and requires an exact total of `10000`; the data model and service still accept any valid registry-backed element map. Failures identify the offending id/range/sum/membership rule and do not call `setData` or synchronize. Success responses identify the target and resulting value.

There is no random `awaken` command. Random/deterministic root generation belongs to the next awakening change.

### 9. Validation is layered and does not overclaim manual behavior

The implementation adds focused JUnit tests for default profile state, profile and definition codec round trips, spiritual-root sum/range rejection, profile numeric rejection, snapshot stream-codec round trip, immutable update behavior, unknown-id decoding, realm/stage membership, and learn/forget/mastery transitions. Stream-codec tests use a registry-friendly buffer/provider suitable for the current mapped API rather than a text-only source assertion.

`tools/validate_cultivation_core.py` uses only the Python standard library. It parses the shipped registry JSONs at their actual custom-registry paths, validates required fields/types/numeric bounds, enforces unique realm/stage ids and valid stage order, resolves next-realm/technique element/minimum realm-stage references, verifies stage membership, checks the five foundation elements and `basic_breathing`, and checks required English/Chinese translation keys. Bad references are hard failures.

The automated gate order is:

1. `python3 tools/validate_cultivation_core.py`
2. `./gradlew test`
3. `./gradlew build`
4. A bounded repository acceptance/dedicated-server run that is explicitly stopped after startup evidence is captured.

The server smoke must show that all three datapack registries load, the attachment type and `/myvillage cultivation` tree register, both payload directions register without conflict, and no client-only class, registry freeze, codec, datapack-path, or payload-direction error occurs. Existing flying-sword validation remains in the regression set because its protocol is an explicit preservation boundary.

The eleven requested gameplay lifecycle checks remain a manual/acceptance checklist. Any item not actually exercised is reported `not_verified`; successful compile/startup cannot be reported as proof of save/restart, death, End-return, or client snapshot behavior.

### 10. Documentation and later changes stay on explicit boundaries

The implementation adds `docs/ai-kb/28_cultivation_core.md`, indexes and cross-links it, and updates README with the administrator commands, validation steps, registry paths, and the explicit statement that only cultivation data infrastructure exists. Archive synchronizes the six new capability specs into the baseline. Command/acceptance guidance changes are kept aligned with relevant repository docs/specs as required by `AGENTS.md`.

The next change must start from exactly one of these boundaries: deterministic spiritual-root generation/awakening, actual basic-breathing execution, spiritual-power cap/recovery, a meditation state machine, cultivation gain, or qi-refining levels one through three advancement. Those changes consume `CultivationService`, registry lookups, and snapshots rather than bypassing them.

### 11. Owner scope exception: no version or changelog task

`openspec/config.yaml` currently contains a general `rules.tasks` requirement that every feature/fix change include synchronized version, mod metadata, README jar-name, and changelog work. The owner explicitly defined version publication and changelog updates as non-goals for this foundation change. That is a direct scope conflict, not an accidental omission.

For this change only, the accepted owner decision is treated as an explicit owner scope exception: `tasks.md` intentionally contains no version-bump, `neoforge.mods.toml`, README jar-name, release, or `CHANGELOG.md` task. README cultivation usage text remains in scope, but release metadata does not. Any later release change must follow the general configuration rule in full.

## Risks / Trade-offs

- [Custom registry paths are easy to misplace] -> Keep the exact doubled-namespace paths in specs/docs, validate those paths in Python, and require a dedicated-server load smoke.
- [A removed datapack definition leaves a semantically unavailable profile] -> Preserve the raw id, mark it unavailable, block new writes to it, and offer explicit reset rather than destructive login repair.
- [Codec-based attachment failure can surface during player load] -> Keep v1 decoding small and fully tested; reject only structurally invalid/unsupported schema data, not unknown definition ids.
- [Copy behavior can duplicate if a later contributor adds a Clone handler] -> Specify and validate `copyOnDeath` as the sole copy owner and document that End-return copying is framework behavior.
- [Client handler linkage can break dedicated-server startup] -> Keep all physical-client references in a `Dist.CLIENT` subscriber and cover startup with a real dedicated-server smoke.
- [A profile snapshot may become large after many techniques] -> v1 sends only one player's immutable map on bounded lifecycle/mutation events, not every tick; add paging/deltas only if profiling later proves necessary.
- [Dynamic datapack reload can invalidate ids already stored in profiles] -> Resolve availability on every command/use and never assume a previously valid id remains registered.
- [Debug commands can accidentally become gameplay APIs] -> Keep permission level 2, deterministic behavior, and registry validation; add player-facing actions only in later explicit changes.
- [The owner exception diverges from the generic task rule] -> Record it here and in tasks, touch no release file, and leave the generic rule unchanged for future work.

## Migration Plan

1. Register definition codecs/registries and ship the minimal valid datapack resources/translations.
2. Add immutable v1 profile values and tests before registering the attachment.
3. Register the codec-backed `copyOnDeath` attachment and the service mutation boundary.
4. Add lifecycle synchronization, payload registration, the side-safe client cache, and command delegation.
5. Add shipped-data validation, documentation, and the complete automated gates.
6. Run bounded dedicated-server smoke and record manual checklist items as passed or `not_verified` based on actual evidence.
7. Archive through CRAFT/OpenSpec so the six new delta specs become baseline capabilities; do not alter release metadata.

Rollback before player data is relied upon is code/resource removal. After worlds have saved `myvillage:cultivation_profile`, rollback must tolerate the namespaced attachment data remaining unconsumed; it must not rewrite or delete unrelated player data. Reintroduction with the same attachment/registry ids restores decoding.

## Open Questions

No owner decision is blocking implementation. Exact Java helper names may follow current repository conventions, but the attachment id, registry keys/paths, v1 serialized fields, authority boundary, copy mechanism, payload direction, non-goals, and release exception are fixed by this design.
