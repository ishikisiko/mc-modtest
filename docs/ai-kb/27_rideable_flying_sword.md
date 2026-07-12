# Rideable Flying Sword

`myvillage:rideable_flying_sword` is both a stack-size-one functional item and
the id of a transient base-`Entity` vehicle. The first version provides one
owner, one passenger, six-direction flight, normal block collision, and a
static vanilla item-model renderer. It adds no GeckoLib, combat, progression,
energy, special moves, multiple passengers, or cross-dimension retention.

## Use And Controls

```mcfunction
/give @s myvillage:rideable_flying_sword
```

With the item held, the first right-click creates the sword immediately below
the player and mounts that player. A later right-click recalls and removes the
owned sword; the recall use does not create a replacement. Creation checks a
short vertical range for a block-clear hitbox and fails cleanly in a fully
confined space.

```text
W / S       forward / backward
A / D       left / right
Space       ascend
Shift       descend
```

Shift is reserved for descent while riding this entity. Reusing the item is the
reliable recall and dismount action.

## Server Authority

The client sends one byte with six known input bits. It sends no coordinates,
rotation, velocity, acceleration, speed, owner UUID, or target entity id. The
server derives the controlled sword from the sender's current vehicle and
accepts the bitset only when the sender is alive, in the same level, and is both
the bound owner and current passenger. Unknown bits are rejected and input
older than five ticks becomes neutral.

The server derives horizontal movement from its current player yaw, normalizes
diagonal input, applies fixed horizontal and vertical speed caps, and uses
axis-specific acceleration and drag. Normal `Entity#move` collision resolution
keeps `noPhysics` disabled; gravity stays disabled. With no accepted input, drag
brings velocity toward zero so the sword hovers and gradually slows. While the
owner is mounted, the server also resets the player's fall distance and keeps
the sword pitch at zero. A descending base `Entity` also accumulates fall
distance on the vehicle itself, so the sword blocks that vehicle-to-passenger
fall-damage propagation on touchdown. Normal block collision remains active;
only the mounted touchdown damage is suppressed.

## Binding And Cleanup

The entity stores its owner UUID. The owner's persistent player data stores the
current sword UUID as a server-side index; it is not supplied by the network
payload. Ordinary entity removal clears the index only when it still names that
sword, so an older removal cannot clear a newer binding. Explicit recall clears
the index and discards every loaded owner match. Recall and lifecycle events
scan loaded entities only at those discrete boundaries; the entity's tick
resolves its owner directly rather than performing a broad world scan.

The sword is discarded when the owner dies, logs out, changes dimension, or is
more than 64 blocks away. The entity type uses `noSave()`, cannot change
dimensions, and is not retained across chunk unload, world reload, or server
restart.

## Rendering And Side Safety

The client renderer draws an `ItemStack` of the flying-sword item horizontally
through the vanilla item renderer. It avoids the item model's extra `FIXED`
display transform, aligns the diagonal placeholder texture from hilt to blade
tip with local forward, and then follows the interpolated server yaw. The base
entity also smooths received server position/yaw snapshots over a bounded client
tick window instead of snapping between them. This does not predict movement or
send a transform back to the server. Input hooks and renderer registration stay
under the client package; the common item, entity, payload, and registration
classes do not import `net.minecraft.client` types.

## Validation And Handoff

```bash
python3 tools/validate_rideable_flying_sword.py
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

The dedicated-server gate proves side-safe loading and registration, not flight
quality. In-game acceptance still covers all six controls, Shift descent without
dismount, hover/drag, block collision, damage-free touchdown while mounted,
horizontal orientation, fall safety,
singleton recall, all cleanup paths, multiplayer authority, and item-model
scale/readability. It must also confirm that riding has no repeated snapshot
jitter and that the blade tip, rather than the hilt, points along the player's
horizontal view direction.

## See Also

- Change spec: [rideable-flying-sword](../../openspec/changes/add-rideable-flying-sword/specs/rideable-flying-sword/spec.md)
- Item workflow: [Mod Item Creation](22_mod_item_creation.md)
- Entity boundary precedent: [Custom Entities](26_custom_entities.md)
- Acceptance commands: [Validation Checklist](09_validation_checklist.md)
