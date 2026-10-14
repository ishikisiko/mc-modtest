# Independent elemental penetration — 0.48.2

Scope: five independent elemental penetration attributes, with the existing
`myvillage:resistance_penetration` ID retained as physical-only. No old value is
copied into the new attributes, and no elemental attacks are introduced.
See [the contract](../../openspec/specs/realm-combat-stats/spec.md) and
[implementation notes](../ai-kb/47_realm_stats_wilderness.md).

## Method and environment

Local Linux host, Minecraft 1.21.1 / NeoForge 21.1.233, Java 21. Existing Gradle
and game caches are warm. Gradle/Minecraft runs are serialized under the shared
heavy-work lock. No Modal or DevBridge is used. Administrator attribute and
numeric damage fixtures are distinct from mapped melee/sword-qi input and
normal acquisition. Pure selector tests cover all six channels; current game
source classification only connects physical and fire.

GNU-time totals describe the measured command and its waited-for children;
detached processes may be omitted, and peak RSS is not a complete simultaneous
process-tree aggregate. Setup, scenario execution,
restart and build timings are recorded separately. Dedicated servers use the
development source set; the fresh jar is separately inspected against tested
class output.

## Results

| Phase | Result | Wall time | CPU user/system | Peak RSS |
|---|---|---:|---:|---:|
| Full release gate (aggregate, including rows below and build) | 56/56 checks; no failed/skipped gates | 352.39 s | 334.25 / 27.26 s | 460724 KiB |
| Fresh jar build within release gate | Passed; tested outputs reused | 41.0 s | Included in release-gate total | Included in release-gate total |
| Full Python suite | 1177 tests; 13 NumPy-dependent skips | 205.8 s (runner 205.128 s) | Included in release-gate total | Included in release-gate total |
| Dedicated preview interpreter | All 13 skipped preview cases pass | 10.4 s | Included in release-gate total | Included in release-gate total |
| Focused validator mutations | 54/54 passed, including all 40 prior cases and 14 new cases | 1.45 s | 1.25 / 0.09 s | 20876 KiB |
| Full Gradle test invocation, including compile/resources | 990 tests; 0 failures/errors/skips | 112.84 s | 203.44 / 7.12 s | 831716 KiB |
| JUnit methods (sum from 158 XML files) | Includes six new penetration cases | 58.961 s | Not isolated | Not isolated |
| Clean stage-1 server lifecycle | Passed | 81.98 s | 152.85 / 9.14 s | 1278816 KiB |
| Physical-client capture, two sessions, attempt 1 | 27/27 checks passed | 194.70 s | 292.32 / 22.17 s | 1347744 KiB |

All 56 release checks passed; generated resources remained current. Raw logs,
attempts and phase measurements are retained under
`reports/elemental-penetration-20261015/`.

## Actual observations

The first capture attempt passed all 27 checks, with no failed scenarios or
retries. Initial setup took 82.068 s, initial checks 16.669 s and first shutdown
10.009 s. Restart setup took 80.055 s, restored checks 0.741 s and final shutdown
5.007 s. Nested capture startup fields are retained in JSON: first server ready
24.1 s, client join 45.2 s, in-game ready 56.2 s; restart server ready 32.0 s,
client join 33.4 s, in-game ready 46.4 s. These nested fields are not additive
independent phases. Clean stage-1 setup reached RCON in approximately 73 s;
Chunky processed 81 chunks in 3 s. Stages 2–4 were not requested.

Both actual clients used Mesa llvmpipe (LLVM 20.1.2), with OpenGL 4.5 / Mesa
25.2.8. Both logged SoundSystem startup failure and disabled audio. This is
software-rendered functional evidence, not hardware-GPU performance or audio
acceptance.

| Runtime fixture | Observed result |
|---|---|
| Fresh attributes | All six channels start at zero |
| Physical penetration 0.2 against physical resistance 0.5 | 10 damage → 7 |
| Same old physical value against fire resistance 0.5 | 10 fire damage → 5 |
| Independent fire penetration 0.3 | 10 fire damage → 8; physical remains 7 |
| Metal, wood, water and earth each set to 0.8 | Physical and fire results remain unchanged |
| Fire penetration 0.8 against fire resistance 0.5 | Effective resistance zero; 10 damage → 10 |
| Fire without a living attacker | Penetration zero; 10 damage → 5 |
| Armor 20 / toughness 8, then physical resistance 0.5 and penetration 0.2 | 10 → 3 after armor → 2.1 after resistance |
| Unmapped generic and bypassing out-of-world damage | Remain untouched by typed resistance/penetration |
| Authored bounds | Base above the configured maximum reads back clamped; negative base reads back zero |
| Mapped melee with physical 0.2 / resistance 0.5 | Requested ≈41.58 → actual loss ≈29.106 |
| Mapped sword qi with the same physical values | Requested ≈48.8361 → actual loss ≈34.1853 |
| Save/restart, physical/metal/wood/water/fire/earth | Distinct values 0.11/0.21/0.31/0.41/0.51/0.61 and each permanent modifier restored exactly |
| Restored physical/fire processing | 10 physical → 6.1; 10 fire → 10, using their own restored values |

Every attribute setup writes and reads its base value before a result is
accepted. Damage probes check classified type, selected penetration,
post-vanilla damage and actual health loss. Restart compares the player UUID,
each effective value, base and modifier identities. Commands, traces and both
sessions' logs are retained under `out/preview/elemental_penetration/attempt-1/`.
The validator had two passing development runs (53 then 54 cases); the second
added an explicit no-addition/compiled-symbol guard. Their timings remain in
the task evidence.

## Artifact

`build/libs/myvillage-0.48.2.jar` is 3,661,472 bytes; SHA-256:
`8cf4f8d36ffb0329c5b503dcb7bae82fd71858826c87d46f0523bd24d6b9fd27`.
The focused jar validator passed with no errors. All six penetration labels in
both languages match the authored files, and the registry/routing symbols are
present. All 27 packaged statistics/mixin classes match the development output
used by the successful runtime checks byte-for-byte. A separate standalone
installed-jar server launch is not claimed.

## Compatibility and acceptance boundaries

The old attribute ID keeps existing physical bases and permanent modifiers.
It now has explicit Physical Resistance Penetration / 物理抗性穿透 labels and
ceases to reduce fire resistance. Every new elemental attribute defaults to
zero; callers intending fire penetration must set `fire_resistance_penetration`.
Percentage-point subtraction, the zero floor, existing bounds, armor pressure
and source classification remain unchanged.

Metal/wood/water/earth attack gameplay is not implemented; registered values,
selector tests and save/readback do not establish those attacks. Owner combat
feel, visual UI, audio and multiplayer remain `not_verified`. New-attribute
behavior during death/respawn, End return and dimension transfer was not
observed in this runtime session; those lifecycle surfaces remain
`not_verified`. A cross-version installed-world upgrade was not exercised;
the retained ID and native attribute NBT tests establish the compatibility
mechanism, while this restart checks independently saved current-version data.
