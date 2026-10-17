# Advanced fates 0.61.0 verification

Scope: Innate Sword Heart, Wind Step, Fire Spirit Body, shared derived-damage
attribution, and optional saved fate states. Protocol 31. Three GPT-6.1-Sol
workers ran at high reasoning effort, with disjoint implementation ownership and
cross-review; the parent integrated common files and ran serialized Modal gates.

## Build and focused tests

`advanced-fates-build`, source `f4f6a86`, run `20261017T144950Z-f31e1ecb`:
331 Java tests across 59 classes passed, plus jar build. Coverage includes the
new fate pure transitions/codecs, preservation across unrelated state writers,
death/reset cooldown retention, ground/air collision and commit rollback,
ordinary combat/dodge regression, stats mitigation, packet codecs and client UI.
The known unrelated first-person IK failures documented in the preceding
Sword Obsession report are outside this focused preset.

Python checks: 40 fate-validator tests, 22 Modal-runner tests and 5 focused
advanced-fate collector tests passed. Combat
resources load through `tools.combat_data.load_strict()` with all three sword
wave definitions. `c7ddad7` adds the runtime scenarios and a read-only
`FIRE_SPIRIT_HIT` log; this is the only game-source difference from the successful
Java test build.

## Review fixes made before cloud runtime

- Replaced side-effecting air-movement probing with swept-volume planning and
  a payment/cancel/move transaction. `Entity.move` can invoke fire contact inside
  the attempted transaction, before an invulnerability window exists.
- Kept genuine thin-edge support, and restore an air charge on an observed
  airborne-to-ground transition without forcing an extra standing tick.
- Reclaim completed empty fire ledgers, preventing a player's 64 target slots
  from staying occupied by targets with no remaining work or stack.
- Moved burn installment timing into authored data and preserve the final
  fractional remainder; prevent derived DOT knockback using the damage tag.
- Freeze the actual primary-health receipt before enchantment post-effects and
  retain the primary fraction when Tempered Bones adds damage.
- Clear short sword openings at login and clear stale synchronous hit receipts
  at each server tick, while preserving saved cooldowns.

These were source/test findings, not claims of already observed game behavior.

## Runtime evidence

Run `20261017T150323Z-11320a64`, source `b10fb8d`, completed Sword Heart and Wind
Step successfully; 65 checks passed before Fire Spirit's next-target fixture stopped.

- Sword Heart: actual two-client precise dodge, no reward from a lone dodge key,
  one opening consumed by a mapped sword hit, and a single piercing wave. Baseline
  melee damage 9.45 became 14.175; both targets took the same separate 15.75 wave.
  Cooldown, environmental/poison/thorns exclusions, private player state and death
  clearing the opening while preserving cooldown all passed.
- Wind Step: real Space/Alt input, ground-free speed bonus, configured air payment,
  no second air charge, landing reset/shared cooldown, insufficient-qi and wall
  rejection without payment/charge loss, same-flight retry, and thin-pane stopping.
  At 1 HP, real fire-block contact was canceled during the committed air dodge;
  no death occurred before payment/window setup.
- Fire Spirit: unresisted installment amount/timing, damage type/resistance path,
  no vanilla ignition and no burn recursion passed. Its following attack had no
  primary receipt while the just-killed previous target could still intercept
  the same aim point during the vanilla 20-tick death animation. The collector
  now observes entity removal before reusing that aim point. The next run selects
  only `fire_spirit`; the two passed cases are reused unchanged.

The intermediate fire-only run `20261017T151042Z-e1b21b89` at `cb9886d`
showed that waiting on `@e[tag=...]` was insufficient: that selector adds an
`Entity::isAlive` predicate and stops matching before the corpse is removed.
The final collector uses exact UUID lookup, whose server selector retains the
entity until actual removal. This correction changes only the fixture.

Final selected run `20261017T151445Z-31615f62`, source `73f228d`, passed all
24 checks for `fire_spirit` and the shared airborne-restart probe. Sword Heart
and Wind Step were deliberately unselected in this final run; their successful
results are reused from `b10fb8d`, with identical gameplay sources.

- Both unresisted and 50%-resisted burns paid exactly the scheduled three-part
  raw budget. The fire-resistance path applied once; no vanilla ignition occurred.
- Three mapped hits queued exactly one weapon-based burst. Further hits during
  the same target's cooldown did not burst; a second target had an independent
  cooldown. Burn totals contained no extra contribution from the derived burst.
- Two actual clients established source UUID attribution/private state and showed
  that a victim's fate did not transfer to an unawakened attacker.
- For lifecycle, a real mapped air dodge consumed the saved charge in an explicitly
  prepared high-altitude/slow-falling survival fixture. After a real server/client
  restart the player was still airborne at Y=61.02, the saved charge was still
  spent and a further mapped dodge was rejected as AIRBORNE. Landing restored it.
  Wind Step and Fire Spirit's nonzero saved deadlines and the selected fate IDs
  were preserved. Those short deadlines had naturally elapsed by readback; this
  is preservation evidence, not a claim that startup was faster than the cooldown.
- A nonzero Sword Heart cooldown was not present in that selected restart fixture;
  its death retention and full-state codec round-trip are covered separately.
  Its nonzero-deadline restart surface remains `not_verified`.

Each scenario records
selected/completed/unrun state, failures, mapped inputs and authoritative readbacks.
Server commands prepare fixtures and explicitly labelled negative boundaries;
they do not substitute for player attack/dodge input in positive gameplay checks.

## Retained initial runtime failure

Run `20261017T145714Z-06323624` at `c7ddad7` passed 52 checks before all three
scenarios stopped on collector issues: Sword Heart expected `minecraft:magic`
but actual poison is `neoforge:poison`; Wind Step's `HurtTime` and Fire Spirit's
`Fire` return SNBT short values such as `0s`, which the inherited float-only reader
rejected. The scope-specific reader now parses numeric SNBT and scoreboard values,
and the poison boundary matches the observed source. Five parser/regression tests
cover those corrections. No gameplay logic changed for this retry. The failure's
artifacts are retained and its sandbox termination is confirmed.

## Delivery and measured time

Delivered artifact: `build/libs/myvillage-0.61.0.jar`, copied from the final
successful selected runtime run. SHA-256:

`b77e2070df63b021d60bf5dbd378c7da2f8480c9cba23caa62a0577be2740e78`

All three fate definitions, the new wave, fire damage type/tags, bilingual
language files and mod metadata match the authored workspace bytes in the jar;
`validate_innate_fates.py --jar` passed. The selected Fire Spirit screenshot was
inspected and no longer has a placeholder label. Gameplay visuals remain an
owner-acceptance surface.

The successful Java build took 65.304 s in Gradle and 86.894 s controller total.
The final selected runtime used 32.932 s for Gradle and 204.875 s for its harness,
including 56.022 s initial setup, 28.448 s second-client startup and 46.017 s
restart setup. Controller total was 259.630 s. These are nested measurements,
not independent additive totals or model thinking time.

All five run records have `termination: confirmed` and downloaded/verified
artifacts. The three failed runtime runs remain available, with total controller
times 261.448 s, 303.228 s and 139.535 s. Occasional Modal exit-status transport
retries did not rerun the job. No Minecraft client ran on the owner's computer.

## Player-facing interpretation

Ground dodges retain their previous free cost. Wind Step's new air dodge alone
pays the chosen movement technique's configured qi cost and shares its cooldown.
Its instantaneous server air step preserves fall distance and vertical velocity;
this does not establish that its feel matches the old ground slide.

Sword Heart consumes its opening on the first accepted sword-melee target,
including real shield absorption. Vanilla natural criticals are not multiplied
a second time. Fire Spirit's burst is target-only: the authored design contains
no explosion radius. All derivatives exclude recursive offensive fate triggers.

Owner feel, balance, visual/sound acceptance, FPS, broad multiplayer and real
legacy-save upgrade remain `not_verified` unless separately observed. Existing
unrelated IK failures and old validator documentation pins are not fixed here.
