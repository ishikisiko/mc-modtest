## Context

MyVillage already has deferred item/entity registration, a client-only renderer entry point, and a dedicated-server acceptance run, but it has no custom payload layer or controllable vehicle. The requested slice crosses common runtime, client input/rendering, resources, lifecycle events, and validation. It must remain compatible with Minecraft 1.21.1, NeoForge 21.1.233, Java 21, and a dedicated server with no client classes on the common class path.

The flying sword is a vehicle, not a living mob. Its first version therefore does not need attributes, goals, persistence, spawning rules, GeckoLib, or animation state. The held item is also the recall control, which gives the player a deterministic way to dismount without assigning Shift to vanilla dismount while Shift is reserved for descent.

## Goals / Non-Goals

**Goals:**

- Provide one functional item that toggles a single owner-bound sword: create and mount when none exists, recall and remove when one exists.
- Keep movement server-authoritative while using compact, bounded client key-state messages.
- Support six-direction flight, block collision, horizontal look-following orientation, hover drag, and fall-distance reset.
- Remove the transient entity when its owner dies, logs out, changes dimension, or becomes too far away.
- Render the entity with the registered item model from client-only code and keep dedicated-server startup side-safe.
- Add focused contract/resource/protocol checks plus Gradle test, build, and acceptance-server evidence.

**Non-Goals:**

- Combat damage, targeting, energy consumption, levels, special moves, multiple passengers, autonomous flight, or cross-dimension retention.
- Persistent flying-sword entities across world saves or server restarts.
- GeckoLib, Blockbench geometry, custom skeletal animation, custom audio, or a production-quality final texture.
- Client-supplied coordinates, velocity, acceleration, yaw, pitch, owner identity, or target entity identity.

## Decisions

### Use a transient base `Entity` vehicle

`RideableFlyingSwordEntity` subclasses the mapped base `Entity`, registers under `MobCategory.MISC`, has no gravity, accepts only its bound player as its sole passenger, and opts out of world saving. A narrow, low hitbox represents the sword while normal `Entity#move` collision resolution remains enabled.

This is preferred over a `Mob` because the sword has no health, attributes, goals, navigation, loot, or natural spawning. It is preferred over an invisible vanilla carrier because a dedicated entity identity makes tracking, rendering, cleanup, and protocol validation explicit.

### Toggle summon and recall through the same item

The server owns the item interaction. If the player already has a live bound sword, using the item discards it and clears the binding. Otherwise the server searches a short vertical range from immediately below the player's mounted feet for a block-clear sword hitbox, records both directions of the owner/entity association only after finding one, adds it to the level, and force-mounts the owner. A fully blocked search fails without a stale binding. The item is not consumed.

The owner UUID lives on the entity for authorization, while server-side player persistent data stores the current sword UUID as a lookup index. Item reuse and lifecycle events also scan the server's loaded entities only at those discrete moments and discard every matching owned sword before creation can proceed. The entity's normal tick resolves its owner directly from the server player list, so no broad per-tick scan or client-maintained binding is needed. Removal clears only a matching index, and the entity is not saved, preventing an unloaded stale sword from returning later.

### Send only a validated key bitset

`FlyingSwordInputPayload` carries one byte containing only forward, backward, left, right, ascend, and descend bits. It carries no entity id, owner id, position, rotation, velocity, or speed. The server derives the target from the sending player's current vehicle and accepts input only when the sender is the living, same-level owner/passenger of that sword and the bitset contains no unknown flags.

The handler runs on the server thread, stores only the validated key state, and refreshes a short input timeout. Missing/stale input becomes zero input. This bounds spoofing to the same actions a legitimate mounted client can request; all acceleration, speed caps, collision, yaw, and final position remain server decisions.

### Compute movement in the entity server tick

The server converts W/S and A/D bits into normalized horizontal vectors based on the owner's horizontal yaw. Space and Shift select bounded vertical targets. Input accelerates toward fixed horizontal/vertical limits; absent axes apply drag toward zero so the sword hovers rather than falling and gradually slows. Diagonal input is normalized before applying the speed limit.

Each server tick sets pitch to zero, copies only the owner's horizontal yaw, calls normal collision-aware movement, and resets the mounted player's fall distance. The client does not run authoritative movement and receives ordinary entity tracking updates.

The base `Entity` implementation snaps ordinary tracking packets directly to their target transform, which is visibly unstable for a mounted vehicle. The sword therefore follows the vanilla minecart-style client interpolation pattern: it stores only the latest server-provided position/yaw target and eases toward it over a bounded tick window. This is presentation-only interpolation. The client still performs no flight integration or prediction and the entity deliberately does not expose a controlling passenger, so vanilla vehicle-coordinate packets remain disabled.

### Reserve Shift for descent while mounted

A client-only movement-input hook suppresses the vanilla dismount interpretation of Shift only while the local player rides this entity, while the raw Shift key still contributes the descend bit. Reusing the held flying-sword item recalls the entity and cleanly dismounts the player.

This is preferred over remounting after every vanilla dismount because forced remount loops cause visible jitter and ambiguous server state.

### Clean up from both entity checks and lifecycle events

The entity checks its bound owner on the server tick and discards itself when the owner is absent, dead, in another dimension, or beyond the configured maximum distance. Common-side player logout, death, and dimension-change listeners also recall immediately, reducing the window where an orphan remains. Stale player indexes are cleared during any later lookup.

### Render through the item model on the client

`RideableFlyingSwordRenderer` delegates to the vanilla `ItemRenderer` with an `ItemStack` of `myvillage:rideable_flying_sword`, applies a horizontal transform, and does not own a separate entity texture/model format. It uses the unmodified item-model context rather than stacking the model's `FIXED` display transform, rotates the diagonal placeholder texture axis so hilt-to-tip becomes local forward, then applies the interpolated entity yaw. Renderer registration and input hooks remain under the client package and client distribution annotations; common registration, payload, entity, item, and lifecycle code import no `net.minecraft.client` classes.

## Risks / Trade-offs

- [Shift handling can regress with input event ordering] -> Cover the source boundary with focused checks and retain item reuse as the reliable recall/dismount path; verify descent without dismount manually in a real client.
- [Latency makes server-authoritative flight feel less immediate] -> Send compact input every client tick, use a short tracking update interval, and interpolate only received server transforms rather than predicting movement or sending coordinates client-side.
- [A stale index or duplicate could be created outside normal item use] -> Lookups verify type and owner, removal compares UUIDs before clearing, item use removes every loaded owner match, unowned summons self-discard, and no sword is saved across unload.
- [Transient entities disappear on unload/restart] -> This is intentional for the first version and prevents abandoned vehicles; persistence and cross-dimension transfer remain non-goals.
- [A flat item model has limited visual depth] -> Accept the low-cost placeholder route for this iteration and leave in-game angle/scale/readability as an explicit human review item.

## Migration Plan

1. Register the payload, entity type, item, lifecycle listeners, and client renderer/input hook without changing existing ids.
2. Add names, item model, and placeholder texture; run focused validators and unit tests.
3. Run Gradle test/build and the dedicated acceptance server, then perform multiplayer and visual/gameplay checks in a client.
4. Roll back by removing the new registrations/classes/resources and the isolated capability change; no saved entity or data migration is required because the sword entity is transient.

## Open Questions

None for the first version. Flight constants and the cleanup distance are implementation constants that may be tuned after manual gameplay review without expanding scope.
