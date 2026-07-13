# Cultivation Initiation Ritual

The implemented initiation slice is a two-step, server-authoritative flow:

```text
mortal_unawakened
  -> spirit_testing_stele: deterministic spiritual-root awakening
  -> mortal_qi_sensed
  -> technique_inheritance_stele: definition-gated basic-breathing inheritance
  -> myvillage:basic_breathing at mastery 0
```

The owner explicitly directed one change to contain both boundaries despite the
normal one-boundary guidance in the foundation note. This is a narrow exception,
not a precedent: awakening and inheritance remain independent services, blocks,
commands, result types, tests, and future facility boundaries. The exception does
not include meditation or technique execution.

## Deterministic Spiritual Roots

`SpiritualRootGenerator` is pure. Its result depends only on the Overworld seed,
player UUID, the sorted positive-weight spiritual-element id/weight set, and the
fixed algorithm implementation. It does not use the current dimension, position,
tick, wall-clock time, biome, weather, client state, `RandomSource`, or registry
iteration order. The world seed is never persisted, snapshotted, chatted, or
written to ordinary logs.

The compatibility constants are:

```text
ALGORITHM_VERSION = 1
ROOT_AWAKENING_SALT = 0x4D5956494C4C4147L
SPLITMIX_GAMMA = 0x9E3779B97F4A7C15L
```

Candidate ids are folded as UTF-8 with fixed 64-bit FNV-1a and mixed with a
local SplitMix64-style state machine. Bounded draws use rejection sampling rather
than Minecraft or JDK PRNG behavior. The algorithm version is a code constant,
not a profile field.

`SpiritualElementDefinition.awakening_weight` is optional in datapacks, defaults
to `1`, and accepts `0..1_000_000`. Weight `0` excludes an element from ordinary
awakening; positive weights participate in weighted selection. The complete
`ResourceLocation` strings are sorted before hashing and selection, selection is
without replacement, and checked `long` arithmetic turns empty/overflow cases
into controlled failures. Java does not hard-code the shipped five element ids.

The requested element-count distribution is fixed:

| Elements | Weight |
|---:|---:|
| 1 | 10 |
| 2 | 25 |
| 3 | 35 |
| 4 | 20 |
| 5 | 10 |

The effective count is the smaller of the roll and eligible count, capped at
five. Element count has no quality, power, progression, damage, or technique
bonus.

Affinity allocation is integer-only. A single element receives `10000` basis
points. For multiple elements, each receives `1000`; the remaining points are
apportioned from stable positive weights using integer floor shares and the
largest-remainder method. Remainder ties use full-id ascending order. Every
selected affinity is positive, unselected/zero entries are omitted, and the
total is exactly `10000`.

## Awakening Transaction And Datapacks

`SpiritualRootAwakeningService` accepts a rootless `myvillage:mortal` profile at
`myvillage:mortal_unawakened`, plus the narrow administrator-repair state where
`clearroot` left a mortal player at `myvillage:mortal_qi_sensed`. It rejects an
existing root and rootless profiles in other realm/stage states.

Success installs the generated root and `mortal_qi_sensed` in one immutable
profile replacement through `CultivationService`, preserving the current schema,
realm, progress, stability, spiritual affinity, spiritual power, lifespan
consumption, inert meditation reserve, and learned techniques. One commit sends
one final client snapshot. Failures commit nothing, send no mutation snapshot,
and do not play the complete success effect. Repeating awakening never rerolls
or overwrites an existing root, including one installed by `setroot`.

Existing saved roots are never recalculated by login, datapack reload, registry
changes, or mod updates. `reset` followed by awakening reproduces the exact root
only when the Overworld seed, UUID, positive-weight id/weight set, and algorithm
version are all unchanged. A changed datapack affects future awakening and can
therefore change a post-reset result; seed plus UUID alone is not the contract.

## Definition-Driven Inheritance

`TechniqueRequirementEvaluator` resolves the current technique requirements
against current realm/stage ordering and element registries. Missing references,
invalid stage membership, and ambiguous equal-order realms fail closed. Explicit
minimum-affinity requirements require a present root and sufficient basis points;
the technique's descriptive `elements` list is not an eligibility rule.

The shipped `myvillage:basic_breathing` definition requires minimum realm
`myvillage:mortal` and minimum stage `myvillage:mortal_qi_sensed`, with no element
or affinity restriction. All valid one-through-five-element roots can qualify.

`TechniqueInheritanceService` resolves that definition first. A missing current
definition returns `TECHNIQUE_NOT_REGISTERED` while preserving saved unknown
progress. When the definition exists, `ALREADY_LEARNED` takes precedence over
later root/stage checks, so repeat inheritance never resets mastery. A new learner
must then have a root and satisfy the shared evaluator. Success adds only
`myvillage:basic_breathing -> mastery 0` in one `CultivationService` replacement;
all other profile fields and learned techniques remain unchanged.

## Steles, Commands, And Authority

The independent facilities are:

```mcfunction
/give @s myvillage:spirit_testing_stele
/give @s myvillage:technique_inheritance_stele
```

Both are in `myvillage:main`. Creative inventory and `/give` are the only current
acquisition paths: neither has a recipe, natural generation, sect/worldgen
placement, BlockEntity, menu, or block-local player state. Their vanilla block
interactions call their corresponding service only on the logical server, and
full sounds/particles occur only after a successful commit.

All eight permission-level-2 administrator routes use the same ordinary rules as
the corresponding block service:

```mcfunction
/myvillage cultivation awaken [target]
/myvillage cultivation juexing [target]
/myvillage xiulian awaken [target]
/myvillage xiulian juexing [target]
/myvillage cultivation initiate [target]
/myvillage cultivation rumen [target]
/myvillage xiulian initiate [target]
/myvillage xiulian rumen [target]
```

Omitting `target` uses the executing player. These routes accept no seed,
element, affinity, count, technique id, reroll, force, or bypass argument. The
existing low-level `setroot`, `clearroot`, and `learn` administrator tools remain
separate. Blocks and command handlers never write the attachment directly.

Successful actions reuse the existing clientbound-only
`myvillage:cultivation_snapshot`. The `H` profile screen remains a non-pausing,
read-only view of the latest snapshot: it generates no root, evaluates no
requirements, exposes no mutation control, and sends no cultivation payload.

## Validation And Manual Evidence

Run the automated gates before handoff:

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

The Python validator checks integration, resources, authority, documentation,
scope, release synchronization, and jar packaging. Java tests own golden-vector,
affinity-math, atomic-transition, repeat, and mastery-preservation proof. Stage 1
is bounded dedicated-server lifecycle/registration/side-safety evidence only.

No real client/server interaction was observed for this documentation update.
Every final manual item therefore remains `not_verified`:

The Java service tests exercise the atomic `ProfileCommitter` seam, not a live
Attachment plus `PacketDistributor` path. Static validation likewise checks
block routing and sided-result source contracts, not physical main/off-hand
dispatch. Those distinctions are why snapshot delivery and one-use behavior
remain manual evidence rather than automated acceptance claims.

| Manual acceptance item | Status |
|---|---|
| Default H profile before initiation | `not_verified` |
| Testing-stele awakening; H shows `mortal_qi_sensed` and affinities total `10000` | `not_verified` |
| Awakening does not automatically teach basic breathing | `not_verified` |
| Repeat testing-stele use does not reroll or replay the complete success effect | `not_verified` |
| Inheritance-stele learning appears in H at mastery `0` | `not_verified` |
| Repeat inheritance preserves nonzero mastery | `not_verified` |
| Relog, save/restart, true death, and dimension-change lifecycle | `not_verified` |
| Reset and exact basis-point reawakening under unchanged deterministic inputs | `not_verified` |
| All four `awaken`/`juexing` and four `initiate`/`rumen` routes | `not_verified` |
| Both stele drops, mining, creative-tab presence, messages, effects, and appearance | `not_verified` |
| H-screen sharpness and existing cultivation-command/flying-sword regression | `not_verified` |
| The initiation actions themselves grant no execution, cultivation/power gain, mastery growth, or advancement | `not_verified` |

## Strict Non-Goals

At its original release boundary, this slice added no meditation or sitting state,
basic-breathing executor,
spiritual-power cap/recovery, cultivation gain, efficiency, mastery growth,
realm/stage advancement, breakthrough, stability growth, equipment/primary
technique slot, combat art, element bonus/matchup, root quality/tier/rarity,
washing/reroll, player-selected root input, profile schema v2 field, new
cultivation C2S payload, recipe, natural/worldgen/sect/region integration, NPC,
quest, alchemy, crafting, or flying-sword rule change. Learning is not equipping,
executing, gaining cultivation, or gaining spiritual power. Profile v3,
meditation, gain, advancement, affinity, and H-tab controls were later added as
separate changes; see
[Cultivation Playable Loop](30_cultivation_playable_loop.md).

## See Also

- [Cultivation Core Foundation](28_cultivation_core.md)
- [cultivation-initiation-ritual](../../openspec/specs/cultivation-initiation-ritual/spec.md)
- [Archived initiation change](../../openspec/changes/archive/2026-07-13-add-cultivation-initiation-ritual/proposal.md)
- [cultivation-player-profile](../../openspec/specs/cultivation-player-profile/spec.md)
- [cultivation-definition-registries](../../openspec/specs/cultivation-definition-registries/spec.md)
- [cultivation-persistence-lifecycle](../../openspec/specs/cultivation-persistence-lifecycle/spec.md)
- [cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md)
- [cultivation-debug-commands](../../openspec/specs/cultivation-debug-commands/spec.md)
- [cultivation-core-validation](../../openspec/specs/cultivation-core-validation/spec.md)
- [resource-export](../../openspec/specs/resource-export/spec.md)
- [validation](../../openspec/specs/validation/spec.md)
