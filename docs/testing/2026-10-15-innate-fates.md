# Innate fates first batch — 2026-10-15

Version: **0.49.0**, protocol 18. Eight implemented fates, 28 retained designs.
Scope: server-owned draft/confirmation, separate copy-on-death state, configurable
point budget, H page, first-batch effects. Existing cultivation roots and profile
v4 remain intact. This is developer validation, not owner acceptance.

## Results

- Focused Java tests passed after updating the old command-alias list and the
  client-cache fixture for the extended snapshot. Nine new tests exercise real
  packaged definitions, all budgets/locks, root eligibility, stale/malformed
  intent, confirmed immutability, codecs, recovery boundaries and damage math.
- Full Java suite: **999 tests, 0 failures/errors/skips** in the first successful
  release build. XML suite time: 56.579 seconds. The final build is recorded below.
- First release gate: **57/57 PASS**, no skipped gate steps. Python: **1187 tests**,
  13 numpy-dependent skips covered by the separate **13 passing preview tests**.
- Ten new fate-validator mutation tests passed; the combined fate/initiation/
  meditation/advancement validator suites passed 50 tests in 1.450 seconds.
- Clean stage-1 acceptance: PASS, fresh ephemeral server world; performed before
  capture. The two steles remain separate; capture awakening/inheritance uses
  administrator fixtures and is not a new physical-stele interaction claim.
- Actual Minecraft client: **23/23 checks PASS**, no DevBridge, same-world restart.
  Real H-page mouse input performs reroll, selects three candidates, and confirms.
  Real left-click melee exercises the hunter damage bonus. Other numerical cases
  use explicit RCON fixtures and readback, not client-authored outcomes.
- Twelve PNGs: draft and confirmed pages at GUI 480x270, 427x240 and 320x240 in
  English and Chinese. Representative PNGs and both six-image sheets inspected;
  text wraps within cards and the smaller viewport uses normal scrolling.

## Observed gameplay values

| Check | Observation |
|---|---|
| Robust health | 30 → 34.5 maximum; current health stayed 30 |
| Lightfoot speed | 0.11; repeated refresh did not stack |
| Robust + Burning Star health | 28.5 from baseline 30; additive fate health contributions |
| Burning Star direct damage | Requested fixture 10 → actual loss 13 |
| Fire affinity | Owned fire fixture 10 → 11.5; environmental 4 → 2 |
| Unyielding | Low-health incoming 2 → 1.6; bypass damage remained 2 |
| Hunter hostile damage | Fixture 10 → 11.5; mapped melee attack attribute 46.2 → loss 53.13 |
| Hunter personal kill | At qi capacity 600, kill contribution is 48; observed 49 includes one passive point |
| Hunter neutral / indirect summon kill | No cooldown update and only bounded passive recovery; no kill grant |
| Alchemist recovery | Maximum 151.8; damage to 25%, recovery finished at 83.48986 (~55%) |
| Alchemist repeat damage | Within cooldown, no second recovery trigger |
| Death | Selection and cooldown deadline preserved |
| Restart | Identical selected IDs, roll count, recovery/hunter deadlines and maximum 151.8 |

The server's ordinary qi regeneration continues during the diagnostic `/tick
freeze` because its lifecycle callback still runs. Kill evidence therefore
separately checks the cooldown transition and bounds the small passive gain;
negative cases run beyond the prior cooldown and do not mistake passive recovery
for hunter recovery. The indirect-source fixture explicitly names a wolf as the
direct source and the player as its attribution owner.

Cooldown timestamps remained byte-for-byte equal across restart. The server
continued world ticks while the new client loaded, so the recorded alchemist
deadline had naturally expired by the post-restart readback; this is not a claim
that its cooldown was still active then. Pure tests cover the exact cooldown
boundary. Death during an unfinished healing sequence, End-return, dimension
changes, datapack removal/reload, and non-default in-game server budgets were
not separately observed in this capture.

## Timing by phase

All times are measured, not performance targets. Existing Gradle/Minecraft
artifact caches were reused; worlds were ephemeral. One heavy-work session at a
time on this 4-core host. Client renderer: Mesa llvmpipe software rendering.

| Phase | Wall time | User / system CPU seconds | GNU time max RSS KiB |
|---|---:|---:|---:|
| First focused attempt, old fixture failures | 56.81 s | 89.44 / 4.99 | 679572 |
| Corrected focused Java run | 55.47 s | 86.33 / 4.71 | 598724 |
| First full Java run, old protocol assertion failed | 111.64 s | 199.34 / 6.50 | 802796 |
| First successful release gate | 424.31 s | 480.52 / 31.42 | 821076 |
| Clean stage-1 setup/server/smoke/stop | 83.55 s | 161.83 / 9.05 | 1094276 |
| Capture attempt 1, screenshot guard failure | 109.87 s | 2.73 / 0.45 | 30840 |
| Capture attempt 2, NBT-long parser failure | 137.02 s | 3.31 / 0.64 | 30880 |
| Capture attempt 3, complete two-session run | 263.30 s | 385.44 / 29.29 | 1332952 |

Capture attempt 3 phase wall times: initial setup **84.032 s**, mapped UI
**12.936 s**, English layouts **14.251 s**, effect cases **37.347 s**, first stop
**7.015 s**, restart setup **86.075 s**, Chinese layouts **15.430 s**, final stop
**5.015 s**. Within initial setup, server readiness was 32.0 s and client readiness
50.2 s; restart server/client readiness was 36.0/48.3 s. These nested values are
not additional phases to add to the total. Both heavy-lock waits were 0.

GNU time values cover each launched wrapper and whatever descendants it waited
for; capture supervision is partly detached. In particular the first two capture
CPU/RSS rows do not include the full Minecraft process tree. They must not be
compared as aggregate peak memory or CPU across attempts.

## Retained failures and limitations

1. The old alias-tree and cache-reflection tests failed after the intentional
   API extension; fixtures were updated and the focused suite passed.
2. The old protocol-17 assertion failed in the first full run; it was updated
   to protocol 18 and the complete suite passed in the release build.
3. Capture attempt 1 passed H selection but called a gameplay-only screenshot
   helper while the menu was open. The helper correctly refused. Window capture
   is now used for menus; the failed attempt remains preserved.
4. Capture attempt 2 passed 13 gameplay checks and the English layouts, then its
   readback parser rejected the valid NBT long suffix `3L`. The parser was fixed,
   passive qi recovery was accounted for explicitly, and the full attempt reran.
5. Both successful client sessions report OpenAL startup failure on this host;
   sound was disabled. Existing blockstate/model warnings remain in the logs.
   There were no new fate-runtime exceptions in the successful capture.

Owner battle feel, balance, GUI usability acceptance, real-PC FPS/audio,
multiplayer, and the unobserved lifecycle/reload surfaces above remain
**not_verified**. Screenshots and automation are evidence, not owner approval.
The other 28 traits and root rerolling are **not_implemented**.

## Evidence and packaging

- `reports/innate-fates-20261015/`: per-phase time files, logs, stage-1 report,
  first/final release logs and step copies, packaging and front-door evidence.
- `out/preview/innate_fates/attempt-1`, `attempt-2`, `attempt-3`: failure records,
  commands/readbacks, preserved server/client logs and successful PNGs.
- [Live evidence page](http://43.156.135.198:8765/innate_fates/index.html).
  Port 8765 on loopback belongs to an unrelated DevBench service, so this preview
  binds the host's Ethernet address 10.3.0.5 on the same port; both local Ethernet
  and public-IP requests returned HTTP 200 without stopping the other service.
- [First-batch guide](../ai-kb/48_innate_fates.md), [retained designs](../innate-fates-design.json).

The capture uses Gradle development classpaths, not an installed release jar.
Final post-capture gate: **57/57 PASS**, no failed/skipped gate steps; wall
**422.16 s**, CPU user/system **471.69 / 31.43 s**, GNU time max RSS
**817420 KiB**. Fresh Gradle build step: **107.7 s**. Final Java: **999 PASS**,
zero failures/errors/skips, XML suite time **57.882 s**. Python: **1187 tests**,
13 numpy skips covered by the separate 13 passing preview cases.

Jar: `build/libs/myvillage-0.49.0.jar`, **3,708,414 bytes**.
SHA-256: `b6081e9276e6df8a577bda58eeea03d74df211f306b54fcb35ef25fa123d9d01`.
The jar contains all eight fate definitions and rules; `--jar` validation passes.
All **817** compiled mod classes match the development classes used for the
successful physical-client run byte-for-byte (no mismatches). This packaging
comparison does not relabel the capture as an installed-jar run.

The post-first-build changes hardened direct kill attribution, displayed
unavailable definitions, and preserved healing across End-return; the successful
capture and the final build use those current classes. Owner acceptance remains
as explicitly scoped above.
