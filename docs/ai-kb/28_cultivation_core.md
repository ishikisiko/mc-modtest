# Cultivation Core Foundation

The first cultivation runtime slice established the infrastructure. It provides
data-driven definitions, an immutable player profile, persistence, server-to-client
snapshot synchronization, permission-level-2 administrator commands, and focused
validation. It also provides a read-only diagnostic profile screen opened by the
configurable `H` key. The subsequent initiation slice now adds deterministic
spiritual-root awakening, rules-based `basic_breathing` inheritance, two steles,
and their administrator routes without changing the v1 profile shape. The runtime
still does not provide meditation, cultivation gain, breakthroughs, technique
execution, power recovery, cooldowns, combat attributes, equipment slots, or a
mutable cultivation UI. Region qi, sect/worldgen placement, and flying-sword
restrictions are not connected.

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
executor-free `myvillage:basic_breathing` technique with its initiation realm/stage
requirements. Spiritual-element definitions now expose optional/defaulted
`awakening_weight`; the initiation generator consumes the current positive-weight
set rather than a Java element table. Definitions contain no technique executor.
Runtime lookups and command suggestions use the current `RegistryAccess`, not Java
tables or id-prefix inference.

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

## Diagnostic Profile Screen

The configurable `Open Cultivation Profile` key defaults to `H`. It opens a
non-pausing, read-only screen for the owning player's latest synchronized
snapshot. The screen shows player identity, translated realm and stage,
cultivation progress, stability, current spiritual power, spiritual-root
affinities, learned techniques, categories, grades, mastery values, and profile
schema version. It uses the synchronized registries for labels, ordering, and
element colors and preserves raw ids with an unavailable marker when a
definition cannot be resolved.

The screen has no mutation controls and sends no payload. Pressing `H` again or
Escape closes it. An absent snapshot is rendered as an explicit waiting state
rather than a fabricated default profile. The screen owns its translucent
backdrop and does not run the vanilla background-blur pass after drawing profile
content, so the face, text, bars, and dividers remain sharp. This is a testing
surface, not the future meditation, breakthrough, technique-execution, or combat HUD.

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
/myvillage cultivation awaken [target]
/myvillage cultivation initiate [target]
```

`/myvillage xiulian` is a complete pinyin alias of the `cultivation` root.
Both roots expose both names in every pair: `info` / `chakan`, `reset` /
`chongzhi`, `setrealm` / `shezhijingjie`, `setprogress` / `shezhixiuwei`,
`setstability` / `shezhiwendingdu`, `setpower` / `shezhilingli`, `setroot` /
`shezhilinggen`, `clearroot` / `qingchulinggen`, `learn` / `xuexi`, `forget` /
`yiwang`, `setmastery` / `shezhishuliandu`, rules-based `awaken` / `juexing`, and
rules-based `initiate` / `rumen`. English and pinyin routes share the same argument
types, registry suggestions, permission boundary, handlers, diagnostics, atomic
mutation behavior, and synchronization effects. Each of the two initiation pairs
is available under both roots, yielding four awakening and four inheritance routes.

Progress, power, and mastery are non-negative. `setrealm` requires the stage to
belong to the selected registered realm. The five `setroot` values are basis
points in `0..10000` and must total exactly `10000`; this convenience command
does not narrow the generic profile model. Technique mutation requires a current
registered technique, and `setmastery` does not implicitly learn one. `info`
prints all v1 fields, `unawakened` or the root affinities, and every learned id
with mastery; it preserves and marks unavailable raw ids. `awaken`/`juexing`
calls the ordinary deterministic awakening service, while `initiate`/`rumen`
calls normal-rules basic-breathing inheritance. Neither route accepts seed,
element, affinity, count, technique-id, reroll, force, or bypass input.

## Validation And Manual Acceptance

Run the automated gates in this order. The final command owns the bounded
acceptance-server lifecycle and clean stop:

```bash
openspec validate --specs --strict
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 -m unittest tools.tests.test_validate_cultivation_core
python3 -m unittest tools.tests.test_validate_cultivation_initiation
python3 tools/validate_mod_items.py
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

The two-step ritual has a separate, fully `not_verified` manual ledger covering
both steles, all eight initiation command routes, H-screen phases, repeat
invariants, lifecycle, and continued non-execution; see
[Cultivation Initiation Ritual](29_cultivation_initiation_ritual.md).

## Allowed Next Changes

The owner-directed initiation change deliberately combined awakening and
inheritance while retaining two independent service/facility boundaries. That
narrow exception is complete and does not authorize later bundles. The next
cultivation change must again choose one boundary: actual `basic_breathing`
execution; spiritual-power cap/recovery; a meditation state machine;
cultivation-gain rules; or advancement through qi-refining levels 1-3. Each must
consume `CultivationService`, current registry lookups, and snapshots instead of
bypassing them.

## See Also

- [cultivation-player-profile](../../openspec/specs/cultivation-player-profile/spec.md)
- [cultivation-definition-registries](../../openspec/specs/cultivation-definition-registries/spec.md)
- [cultivation-persistence-lifecycle](../../openspec/specs/cultivation-persistence-lifecycle/spec.md)
- [cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md)
- [cultivation-debug-commands](../../openspec/specs/cultivation-debug-commands/spec.md)
- [cultivation-core-validation](../../openspec/specs/cultivation-core-validation/spec.md)
- [Cultivation Initiation Ritual](29_cultivation_initiation_ritual.md)
- [cultivation-initiation-ritual](../../openspec/specs/cultivation-initiation-ritual/spec.md)
- [Archived initiation change](../../openspec/changes/archive/2026-07-13-add-cultivation-initiation-ritual/proposal.md)
- [Validation Checklist](09_validation_checklist.md)
