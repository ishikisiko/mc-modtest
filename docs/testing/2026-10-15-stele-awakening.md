# Testing-stele awakening — 2026-10-15

Version **0.49.1**, protocol **19**. Scope: right-click the testing stele for an
independent root-first awakening screen, then select three of six innate fates;
H Fates becomes a read-only result view. Existing roots, candidate drafts and
confirmed choices are preserved. Technique inheritance remains a separate
stele. This report records developer evidence, not owner acceptance.

## Automated results

- Strict OpenSpec baseline: **82 passed, zero failed**; strict change validation
  passed separately.
- Focused Java suite: **263 passed**, zero failures/errors/skips.
- Initial full release gate: **52 passed, one failed, four skipped**. The build
  failed because `CombatPayloadTest` still asserted protocol 18 after the
  intentional protocol-19 extension. The assertion was updated; the five-step
  build/jar-dependent retry passed **5/5**, including **1006 Java tests**.
- Global Python suite: **1205 tests run**, **13 skipped**, no failures/errors.
  Those numpy-dependent skips were covered by the separate passing
  `combat-preview-tests` release step. After the capture readback correction,
  the focused Python suite passed **46 tests** in **2.874 s**.
- The first image review found a candidate tooltip clipping at the top edge.
  A bounded tooltip layout and two tests were added. The final five-step
  build/jar checks passed **5/5**; final Java XML records **1008 passed**, zero
  failures/errors/skips, with summed suite time **59.066 s**.
- Clean stage-1 dedicated acceptance passed, including save/stop. This run
  preceded the client-only tooltip correction. The subsequent capture uses
  the final rebuilt classes and starts fresh dedicated server/client sessions.

The release evidence is a retained initial failure followed by focused retries,
not a claim that one final invocation passed all 57 steps. No generator or
server gameplay code changed after those earlier passing steps; the final build
skipped regeneration with `-x generateAllStructures` after the tooltip fix.

## Runtime capture results

Final capture: **29/29 checks passed**, `pass: true`, finished at
**2026-10-15 12:57:26 UTC**. The timed capture subprocess exited **0**. Both
server/client sessions stopped, and the shared heavy-work lock was released.

| Surface | Observed developer evidence |
|---|---|
| Fresh root awakening and first candidate pool | Actual mapped stele right-click after an explicit administrator rootless fixture |
| Close and reopen | Session becomes zero; the same root, candidate IDs, revision and lock resume |
| Lock, reroll and unlock | Mapped input; lock retained through explicit reroll, root unchanged |
| Three-choice confirmation and repeated use | Mapped confirmation; reopening preserves the stored choices and root |
| H result page | Actual navigation; no draft/selection mutation and no active session |
| Invalid sessions | After out-of-range teleport, stele removal, death or closure, a mapped self-roll command is rejected with the draft unchanged |
| Save and same-world restart | Root and complete confirmed draft unchanged; prior active session 11 becomes zero |
| Layout and hover | 18 root/draft/confirmed images plus six actual mapped-hover tooltip images, English/Chinese at 480×270, 427×240 and 320×240 |

The distance/death/block-removal scenarios use explicit administrator fixture
changes followed by mapped ordinary self-command attempts and server readback.
They are developer authority checks, not claims of naturally encountered owner
gameplay. Draft resumption was observed across close/reopen; restart preservation
was specifically tested with a confirmed draft, not an unfinished one.

The final representative images and the previously problematic English/Chinese
tooltips were inspected. The bounded tooltip stays inside the viewport; six
candidates and fixed action controls remain visible at supported sizes. The
owner's appearance/usability verdict remains pending despite this developer
layout review.

The selected command is:

```bash
python3 tools/innate_fates_evidence.py --ui-only --out out/preview/stele_awakening
```

The script uses actual Minecraft mapped stele right-clicks, candidate input and
H navigation, with RCON only for explicit fixture preparation and readback; it
uses no DevBridge. `--ui-only` covers the new interaction/session and restart
path and intentionally omits the unchanged first-batch numerical effects suite.
That suite's earlier observations remain historical
[0.49.0 evidence](2026-10-15-innate-fates.md); the default command without
`--ui-only` still runs it.

## Timing by phase

Existing Gradle/Minecraft dependency and artifact caches were reused. These are
measured warm-cache runs on the shared 4-core host, with one heavy Gradle or
Minecraft job at a time. Stage-1 and capture worlds are temporary acceptance
fixtures. Times below are separate invocations; Java XML suite durations and
individual release-step timings are nested measurements, not additional wall
phases to sum into their parent build/release runs.

| Phase | Wall seconds | User/system CPU seconds | GNU time max RSS KiB |
|---|---:|---:|---:|
| Focused Java | 30.49 | 59.78 / 3.87 | 573400 |
| Initial 57-step release attempt, protocol assertion failure | 412.86 | 466.59 / 31.06 | 832176 |
| Five-step build/jar retry | 112.09 | 197.01 / 6.58 | 801548 |
| Final tooltip-corrected five-step build/jar run | 86.42 | 179.13 / 6.01 | 816220 |
| Clean stage-1 setup/server/smoke/save/stop | 76.11 | 149.73 / 8.11 | 1173092 |
| Capture attempt 1, false readback assertion | 273.15 | 424.76 / 29.72 | 1321796 |
| Capture attempt 2, final successful two-session run | 273.12 | 413.20 / 29.42 | 1329436 |
| Final focused Python after parser correction | 3.00 | 2.10 / 0.53 | 23808 |

Within the initial release run, the global Python step took **209.9 s**
(unittest's own interval **209.197 s**), and the separate preview-test step took
**10.3 s**. The initial Gradle build step took **103.2 s** and failed; the corrected
retry build took **104.7 s**. The final build step took **79.0 s** within its
86.42-second wrapper. Java XML suite sums were **56.042 s** for the first
successful full build and **59.066 s** for the final build. These values overlap
their parent invocations and are not additive.

Final capture phase wall times: initial setup **78.028 s**, mapped stele UI
**43.229 s**, English layouts **30.169 s**, first stop **7.011 s**, restart setup
**76.047 s**, Chinese layouts **30.634 s**, final stop **5.005 s**. Initial server
readiness was **28.0 s**, client join/fully in-game readiness **36.1/48.1 s**;
restart server readiness was **28.0 s**, client join/fully
in-game readiness **36.1/47.2 s**. Both lock waits were zero. Readiness values sit inside the
setup phases and must not be added again.

GNU time records the invoked wrapper and waited-for descendants. Capture also
has sampled process-tree records: attempt 1 reached **3412768 KiB** summed Java
RSS, sampled every three seconds. That sum double-counts shared pages and is
not GNU time max RSS. Attempt-1 process roles could not be classified through
Java argument files and are honestly recorded as unclassified; the final
sampler uses process working directories to distinguish client/server/launcher roles. Final
capture peak summed Java RSS was **3104836 KiB**. The first server/client pair
had sampled peaks **871096/1326876 KiB** and observed CPU **97.83/283.38 s**;
the restart pair **823844/1314384 KiB** and **64.98/173.08 s**. Sampled CPU can
miss the final interval; concurrent RSS peaks and wrapper max RSS are different
measurements.

## Retained failures and review corrections

1. The initial release failed the obsolete protocol-18 test assertion. The
   corrected protocol-19 suite and all four deferred jar checks passed on retry.
2. Capture attempt 1 completed **29 checks: 28 passed, one failed**. The sole
   failure, `mapped_unlock`, was a parser assertion: an absent optional `locked`
   field represents an unlocked state, but the old check expected an explicit
   value. Readback normalization and a regression test were fixed. The failed
   JSON, logs and PNGs remain under `out/preview/stele_awakening/attempt-1/`.
3. The first screenshot inspection found tooltip clipping. The tooltip bounds
   and two regression cases were added before the final build/capture retry.
4. The outer tool session later reported status 143 while the GNU time record
   for the actual final capture subprocess records exit 0. The complete final
   JSON has `pass: true`, all 29 checks pass, sessions stopped and the lock was
   released. This outer-session status difference is retained for audit; it is
   not relabeled as a failed capture test.

## Packaging

Jar: `build/libs/myvillage-0.49.1.jar`.

SHA-256: `c459bf5cd09ff89c24e231629feb1535d5e26101445c1f67de6b17be64a250ec`.

Final inspection compared **831 compiled classes**, with **no class or resource
mismatches**, against the development outputs used by the final capture. The
capture launches Gradle development classpaths; this byte comparison does not
turn it into an installed-jar physical-PC test.

## Evidence and acceptance boundary

- `reports/agent_runs/20261015-stele-awakening-impl/verification/`: phase `.time`
  files, release/build/spec/Python logs, Java XML counts, stage-1 report, jar
  inspection and sampled capture process resources.
- `out/preview/stele_awakening/`: final capture JSON, session logs and PNGs;
  `attempt-1/` preserves the false-failure run and images.
- [Current awakening preview](http://43.156.135.198:8765/stele_awakening/index.html):
  the public page and representative PNG returned HTTP 200. The earlier
  `/innate_fates/index.html` redirects here; its 0.49.0 page remains at
  `/innate_fates/historical_0.49.0.html`.
- [Innate fates guide](../ai-kb/48_innate_fates.md) and
  [initiation services](../ai-kb/29_cultivation_initiation_ritual.md).

Owner appearance, usability, balance/combat feel, real-PC FPS/audio and
multiplayer remain **not_verified**. A mapped click on the separate inheritance
stele was not observed in this capture and remains **not_verified**; stele
separation in code, tests or old administrator fixtures is not that interaction.
Other unobserved lifecycle/reload surfaces remain unverified rather than being
inferred from build or startup success.
