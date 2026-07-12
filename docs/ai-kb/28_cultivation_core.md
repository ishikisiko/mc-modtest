# Cultivation Core Foundation

The first cultivation runtime slice is infrastructure only. It provides
data-driven definitions, an immutable player profile, persistence, server-to-client
snapshot synchronization, permission-level-2 administrator commands, and focused
validation. It does not provide meditation, awakening generation, cultivation
gain, breakthroughs, technique execution, power recovery, cooldowns, combat
attributes, equipment slots, UI, or new items, blocks, and entities. Region qi,
sect facilities, worldgen, and flying-sword restrictions are not connected.

## Authority And Profile

The logical server owns the profile. `CultivationService` is the only mutation
boundary: a successful update validates a newly constructed immutable value,
replaces the attachment with `ServerPlayer#setData`, and sends one new snapshot.
A failed update leaves the old value installed. Commands, events, and future
gameplay systems must not mutate attachment values or their nested maps in place.

The v1 value graph is:

```text
CultivationProfile
  schemaVersion: int = 1
  realmId: ResourceLocation = myvillage:mortal
  stageId: ResourceLocation = myvillage:mortal_unawakened
  cultivationProgress: long = 0, minimum 0
  stability: int = 0, range 0..100
  currentSpiritualPower: long = 0, minimum 0
  spiritualRoot: optional SpiritualRoot = empty
  learnedTechniques: ResourceLocation -> TechniqueProgress = empty

SpiritualRoot
  affinitiesBasisPoints: ResourceLocation -> int
  each value: 0..10000; total for a present root: exactly 10000

TechniqueProgress
  masteryPoints: long, minimum 0
```

Awakening is derived from whether `spiritualRoot` is present; there is no stored
`awakened` flag. The affinity model is not limited to the shipped five elements.
All maps are defensively copied and all persisted identifiers are
`ResourceLocation` strings, never enum ordinals or registry numeric ids.

The v1 codec accepts syntactically valid identifiers even when their definitions
have been removed. Such ids remain unchanged in saves and snapshots and are shown
as `unavailable`; login does not delete or repair them. Runtime mutations still
resolve against the current registries, so an administrator cannot install an
unknown realm/stage/root element or technique. `reset` is the explicit recovery
route. A schema version other than `1` is a controlled codec error, not an
implicit reset. Future schema changes must keep version-specific decoders and
apply explicit, validated `vN -> vN+1` migrations before writing the new shape.

## Definition Registries

`DataPackRegistryEvent.NewRegistry` registers three registries with persistent
and network codecs:

| Definition | Registry key | Shipped resource root |
|---|---|---|
| Realm | `myvillage:realm` | `src/main/resources/data/myvillage/myvillage/realm/` |
| Spiritual element | `myvillage:spiritual_element` | `src/main/resources/data/myvillage/myvillage/spiritual_element/` |
| Technique | `myvillage:technique` | `src/main/resources/data/myvillage/myvillage/technique/` |

The doubled namespace is intentional. Minecraft 1.21.1 resolves custom registry
entries as `data/<entry_namespace>/<registry_namespace>/<registry_path>/`; an
entry `<pack_namespace>:example` in `myvillage:technique` therefore lives at
`data/<pack_namespace>/myvillage/technique/example.json`.

The shipped data is deliberately small: metal, wood, water, fire, and earth;
the mortal, qi-refining, and foundation-establishment realms; mortal-unawakened,
mortal-qi-sensed, qi-refining stages 1 through 9, and foundation-early; and the
metadata-only `myvillage:basic_breathing` technique. Definitions contain no
balance thresholds or technique executor. Runtime lookups and command suggestions
use the current `RegistryAccess`, not Java tables or id-prefix inference.

## Persistence And Synchronization

The codec-backed attachment id is `myvillage:cultivation_profile`. Its
`copyOnDeath()` configuration is the sole copy owner. There is no cultivation
copy in `PlayerEvent.Clone`: true death preserves the complete profile without a
penalty, and normal NeoForge End-return replacement preserves it without a second
copy, merge, or reset. Login, respawn, and dimension-change listeners only read
the authoritative attachment and synchronize it.

`myvillage:cultivation_snapshot` is play-to-client only and is sent to the owning
player on login, respawn (death or End return), dimension change, every successful
administrator mutation, and reset. It is not sent every tick and does not repeat
definition records, which arrive through registry synchronization. The handler
uses `enqueueWork`; the client stores only the latest immutable snapshot and
clears it on disconnect. There is no client-to-server cultivation mutation
payload, and common/server registration does not classload client-only types.

## Administrator Commands

All commands inherit the existing `/myvillage` permission-level-2 boundary.
Targets use the standard single-player argument.

```mcfunction
/myvillage cultivation info
/myvillage cultivation info <target>
/myvillage cultivation reset <target>
/myvillage cultivation setrealm <target> <realm_id> <stage_id>
/myvillage cultivation setprogress <target> <amount>
/myvillage cultivation setstability <target> <0..100>
/myvillage cultivation setpower <target> <amount>
/myvillage cultivation setroot <target> <metal> <wood> <water> <fire> <earth>
/myvillage cultivation clearroot <target>
/myvillage cultivation learn <target> <technique_id>
/myvillage cultivation forget <target> <technique_id>
/myvillage cultivation setmastery <target> <technique_id> <amount>
```

Progress, power, and mastery are non-negative. `setrealm` requires the stage to
belong to the selected registered realm. The five `setroot` values are basis
points in `0..10000` and must total exactly `10000`; this convenience command
does not narrow the generic profile model. Technique mutation requires a current
registered technique, and `setmastery` does not implicitly learn one. `info`
prints all v1 fields, `unawakened` or the root affinities, and every learned id
with mastery; it preserves and marks unavailable raw ids. There is no random
`awaken` command.

## Validation And Manual Acceptance

Run the automated gates in this order. The final command owns the bounded
acceptance-server lifecycle and clean stop:

```bash
openspec validate --specs --strict
python3 tools/validate_cultivation_core.py
./gradlew test
./gradlew build
python3 tools/run_chunky_acceptance.py --stage 1
```

Stage 1 provisions an isolated server, waits for RCON, performs a bounded
lifecycle smoke, sends `save-all` and `stop`, and waits for a clean process exit.
Inspect `run-acceptance/logs/latest.log`; the smoke checks registry/data loading,
attachment and command registration, the clientbound cultivation payload, the
existing serverbound flying-sword payload, and dedicated-server side safety. It
does not prove player commands, persistence, death, End-return, or client
snapshot delivery.

The following gameplay/lifecycle items are `not_verified` until each is observed
in a real client/server session and recorded as pass or fail:

| # | Manual acceptance item | Current documentation status |
|---|---|---|
| 1 | A new player's `info` shows the exact default v1 profile. | `not_verified` |
| 2 | A valid five-element `setroot` succeeds. | `not_verified` |
| 3 | A root total other than 10000 is rejected and the profile is unchanged. | `not_verified` |
| 4 | A valid registered realm-stage pair succeeds. | `not_verified` |
| 5 | A stage outside the selected realm is rejected. | `not_verified` |
| 6 | Learning registered `myvillage:basic_breathing` succeeds. | `not_verified` |
| 7 | Learning an unregistered technique is rejected. | `not_verified` |
| 8 | A non-default profile survives save and server restart. | `not_verified` |
| 9 | A non-default profile survives true death without loss. | `not_verified` |
| 10 | A non-default profile survives End return without duplicate copying. | `not_verified` |
| 11 | Dimension change delivers the latest snapshot to the owning client. | `not_verified` |

## Allowed Next Changes

The next cultivation change must choose one foundation boundary rather than
combining them: deterministic spiritual-root generation/awakening; actual
`basic_breathing` execution; spiritual-power cap/recovery; a meditation state
machine; cultivation-gain rules; or advancement through qi-refining levels 1-3.
Each consumes `CultivationService`, current registry lookups, and snapshots
instead of bypassing them.

## See Also

- [cultivation-player-profile](../../openspec/specs/cultivation-player-profile/spec.md)
- [cultivation-definition-registries](../../openspec/specs/cultivation-definition-registries/spec.md)
- [cultivation-persistence-lifecycle](../../openspec/specs/cultivation-persistence-lifecycle/spec.md)
- [cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md)
- [cultivation-debug-commands](../../openspec/specs/cultivation-debug-commands/spec.md)
- [cultivation-core-validation](../../openspec/specs/cultivation-core-validation/spec.md)
- [Validation Checklist](09_validation_checklist.md)
