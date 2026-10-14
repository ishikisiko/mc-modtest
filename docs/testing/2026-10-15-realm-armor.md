# Defender-normalized armor verification — 0.48.1

Scope: normalize only vanilla armor pressure using the defender's existing realm
growth, retaining full damage and native damage layers. The 30 armor / 12
toughness case is an administrator equipment/attribute fixture, not a new set.
See [the numerical contract](../../openspec/specs/realm-combat-stats/spec.md) and
[implementation notes](../ai-kb/47_realm_stats_wilderness.md).

## Environment and method

Local Linux host, Minecraft 1.21.1 / NeoForge 21.1.233, Java 21. Existing Gradle
and asset caches are warm; no cache purge is represented as a cold measurement.
Gradle and Minecraft jobs run serially under the shared heavy-work lock.
GNU time CPU/RSS describe the measured command, not complete simultaneous
process-tree consumption. Server/client setup and scenario execution are
reported separately. No Modal resources or DevBridge are used.

Fixtures use administrator realm/equipment/damage commands. Mapped client mouse
input separately tests shield use, melee and sword qi. Invalid-assignment
fixtures deliberately set entity persistent data and use fixed high health to
prove scale is not inferred from health. They do not prove normal equipment
acquisition. The dedicated acceptance and capture servers use the development
source set; jar packaging is inspected separately.

## Results

| Phase | Result | Wall time | CPU user/system | Peak RSS |
|---|---|---:|---:|---:|
| Full release gate (aggregate; includes rows below and build) | 56/56 checks; no failed or skipped gates | 357.52 s | 337.77 / 28.14 s | 460604 KiB |
| Fresh jar build within release gate | Pass; previously tested outputs reused | 41.6 s | Included in release-gate total | Included in release-gate total |
| Full Python suite | 1163 tests; 13 NumPy-dependent skips | 208.1 s (test runner 207.418 s) | Included in release-gate total | Included in release-gate total |
| Dedicated preview interpreter | All 13 skipped preview tests pass | 10.6 s (runner 10.386 s) | Included in release-gate total | Included in release-gate total |
| Focused validator mutations | 40/40 passed (21 new armor cases) | 0.71 s | 0.56 / 0.07 s | 19968 KiB |
| Full Gradle test invocation, including compile/resources | 984 tests; 0 failures/errors/skips | 112.78 s | 200.38 / 6.92 s | 786408 KiB |
| JUnit test methods (sum from 157 XML files) | Includes 10 new armor cases | 57.160 s | Not isolated | Not isolated |
| Clean stage-1 server lifecycle | Pass; no error markers | 79.75 s | 149.47 / 8.65 s | 1187148 KiB |
| Physical-client capture attempt 1 | 27/27 checks passed; no scenario failures | 125.16 s | 1.28 / 0.53 s (controller only) | 20736 KiB (controller only) |

Stage-1 setup to RCON readiness took approximately 71 s; Chunky processed 81
chunks in 3 s. This is a bounded startup/lifecycle smoke, not stages 2–4 or a
full external modset. All 56 release gates passed; generated resources remained current.
Raw logs and timings are retained under `reports/realm-armor-20261015/`.

## Observed armor results

The first capture attempt passed all 27 checks. Setup took 88.043 s (server ready
32.0 s; client join 42.2 s and in-game readiness 54.2 s are nested capture timing
fields, not additive independent phases). Scenario execution took 23.391 s;
shutdown took 13.522 s. Detached Minecraft/server supervisors are not included
in the controller's GNU-time CPU/RSS totals. The actual client renderer was
Mesa llvmpipe (LLVM 20.1.2), not a hardware-GPU performance run. The client logged
a SoundSystem startup error and disabled sound; audio remains unverified.

| Fixture | Observed damage / behavior |
|---|---|
| Equal-stage scaling, armor 20 / toughness 8 | Incoming 10/15/30/66 produced approximately 3/4.5/9/19.8 at scales 1/1.5/3/6.6 |
| Golden Core late, armor 30 / toughness 12 | 66 → 13.2; 198 → 39.6; 330 → 66; 331 → 66.6012 |
| Golden Core late, old armor 20 / toughness 8 | 198 → 99 |
| Golden Core late, armor 30 / toughness 0 | 198 → 79.2 |
| Cross-scale requested damage fixtures | 66 against scale 1.5 with 20/8 → 42.24; 15 against scale 6.6 with 30/12 → 3 |
| Breach IV, Golden Core late 30/12 | 66 → 52.8; enchantment reduces effectiveness once |
| Unassigned and four malformed assignments | Vanilla 60 → 16.8 at 30/12, scale one |
| Unmapped but armor-eligible explosion | 66 → 13.2; typed resistance remains untouched |
| Armor-bypassing magic | 66 → 66; armor trace is null |
| Frozen-tick hits of 40 then 60 | Second armor input is 20; combined loss ≈15.0303 |
| Player equipment durability | Incoming 66/198 wears each armor piece by 16/49 respectively |
| Protection IV ×4, Resistance I, typed resistance 50%, absorption 4 | Incoming 198 gives post-vanilla 11.4048, post-typed 5.7024, health loss ≈1.7024 |
| Mapped full shield block | Health unchanged; shield wears by 67; armor remains unworn |
| Mapped Qingfeng melee | Requested ≈41.58, actual loss ≈8.316 against scale 6.6, 30/12 |
| Mapped sword qi | Requested ≈48.8361, actual loss ≈9.7672 against scale 6.6, 30/12 |

Every fixture assertion checks server readback; ordinary armor probes also check
actual armor input, defender scale, pressure, normalized effective armor and
armor output. The cross-scale rows use explicitly supplied incoming damage,
not naturally acquired attacker equipment. Player equipment cases use the
actual Golden Core late profile and full netherite plus a temporary armor-base
offset. Commands, replies, traces and both logs are retained in
`out/preview/realm_armor/attempt-1/`. There were no capture retries. The validator had two successful development
runs (39 then 40 cases); the second added a missing parameter-flow mutation.
Neither run failed, and both timing records are retained in the task evidence.

## Artifact

`build/libs/myvillage-0.48.1.jar` is 3,660,699 bytes; SHA-256:
`91cb11df120e320bbe01e45c58b7c21b6467d3e8cdc747ce1140d54aef556cba`.
The jar validator passed with no errors. Required common hook configuration,
mod registration and hook classes are present. All 27 packaged statistics/mixin
classes match the development class output used for the successful runtime
checks byte-for-byte. This links the inspected artifact to the exercised code;
it does not claim a separate standalone installed-jar server run.

## Acceptance boundaries

Owner combat feel, naturally acquired equipment balance, multiplayer and
compatibility with third-party damage-overhaul mods remain `not_verified`.
The native full shield is exercised with mapped input; a third-party partial
shield is a separate unobserved runtime surface, even when residual-input
arithmetic is tested. Nested-hit container identity has unit coverage; an actual
nested-hit mod callback, beast-move collision and attack enchantments beyond the
explicit Breach fixture are not newly observed in this run. Existing gameplay
regressions remain separate from those runtime acceptance surfaces.
No armor/toughness values, realm coefficients, item catalogue or profile wire
format are retuned by this change. The baseline armor cap is distinct from
combined reduction across potion, enchantment, typed resistance and absorption.
