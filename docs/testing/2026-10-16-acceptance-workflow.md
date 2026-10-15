# Acceptance workflow — 2026-10-16

0.52.1 changes Python acceptance tooling and release metadata only. Gameplay and
save formats remain unchanged. Owner approval covers independent cases and
one-Sandbox combined delivery. Clean stage1 remains required; Chunky decoupling,
automatic impact selection and cross-run build/world caching are deferred.

## Validation

- Local focused scenario/controller tests: 45 passed; failure-injection output is expected.
- Modal isolated medicine, isolated persistence and combined delivery: all passed.
- Full release, fresh jar, stage1 and complete game evidence ran through the
  new combined preset on one immutable source. Partial cases cannot establish full acceptance.

## Boundaries

Fixtures use RCON setup/readback; actual gameplay uses mapped client input. No
DevBridge and no local heavy fallback. Preserve failures, per-command timing,
source hashes and confirmed Sandbox cleanup. Owner feel/balance and broader
multiplayer are not reclassified by this tooling change. GUI/tooltip PNGs are
developer evidence. This report is the current execution record; historical
0.52.0 counts remain in their original report.

## Isolated medicine case

Run `20261015T173818Z-6d24bf6c` on `2c0c5b4` passed 9 medicine checks on a
fresh workspace. The report records only medicine completed; all four other
cases are `not_selected`, `coverage=partial`, `full_acceptance=false`.
Clean stage1 took 190.556s and the medicine command 123.668s. Total controller
wall time was 339.113s, including creation/upload/download/termination. Artifacts
were downloaded and Sandbox termination confirmed. Sampled RSS sum peaked at
5489060 KiB, with per-process shared-page duplication; this is not a cgroup peak.

## Persistence prerequisite correction

While combined validation on `2c0c5b4` was running, a source audit found that an
independent Ningshen persistence fixture must explicitly fill cultivation progress
and reset stability before mapped use. The full path previously inherited that
eligibility from the medicine case. Commit `3df1a4a` adds both setup commands and
checks their ordering in the regression test (21 evidence tests passed). Only
evidence setup/tests changed; product source, resources and build inputs have
an empty diff. The running cloud source was not patched. A separate fresh-world
persistence run validated the corrected independent entry (below).

## Remaining repeated work

`build.gradle` deliberately runs `generateAllStructures` for every resource
processing invocation, and `processResources` is never up-to-date. Sharing a
workspace can reuse Java compilation; it does not eliminate all resource
regeneration. This change does not alter that resource-freshness contract.
Timings are observations, not a controlled before/after performance benchmark.

## Combined delivery

Run `20261015T174442Z-abd188ca` on `2c0c5b4` passed all three commands in one
Sandbox and extracted workspace: complete release 494.321s, clean stage1 69.517s,
and all five game cases 350.417s. Total controller wall time was 940.716s
(15m40.716s). Release passed 62/62 gates, 1315 Python tests and 1062 Java tests
without failure/error/skip. Runtime passed 43 checks with every case completed
and `full_acceptance=true`. This flag means all runtime cases; the release result
is recorded separately. Artifacts downloaded successfully and termination was confirmed.

Stage1 and both capture launches record `compileJava UP-TO-DATE`. Their main-class
manifest contains 869 classes, all matching the release jar byte-for-byte.
Downloaded `build/libs/myvillage-0.52.1.jar` SHA256:
`12a13c2917d8e22d4bc4af930b22d2b35012d899c258c640656da50aaaf78254`.
Local read-only alchemy/aptitude jar validators passed. Sampled Sandbox RSS sum
peaked at 8078940 KiB (shared pages counted per process), within the separately
requested 16 GiB environment; the RSS sum is not a measured unique-memory limit.

Case phases: brewing 45.286s, multiplayer 60.108s (guest startup included),
medicine 58.614s, Chinese UI 25.313s, persistence preparation 5.365s, restored
batch checks/completion 4.792s, English UI 18.901s. Initial session setup took
64.580s, restart setup 58.088s; these timings must not be double-counted with
nested guest/tooltip phases. Only two main capture sessions were used.

Fifteen PNGs were downloaded. Representative Chinese/English 320x240 layouts
and long Chinese/English medicine tooltips were inspected with no clipping.
The visual-handoff generator passed and the public preview returned HTTP 200:
http://43.156.135.198:8765/alchemy/quality-0.52.1/index.html . Cobblestone is an
output-blocking administrator fixture; quality4 tooltip items are administrator
fixtures rather than claimed natural rolls. Owner visual/feel verdict stays unverified.

## Isolated persistence and closeout

Run `20261015T180045Z-d1cf6b54` on `3df1a4a` passed all five persistence checks
from a fresh player/world, with all other cases `not_selected`, partial coverage
and no full-acceptance claim. Whole batch, component inventory, sustained medicine
quality/counters and exact saved-quality output survived restart. Sandbox cleanup
is confirmed. Its 869 main classes also match the release jar. The only difference
from combined source `2c0c5b4` is persistence prerequisite setup and its focused test;
no product/build input changed, so the full release was not repeated.

The three cloud runs totaled 1506.416 seconds (25m06s) of controller wall time.
This implementation's validation was still expensive: two isolated-case proofs
plus one complete delivery, each with clean stage1. The feature reduces future
scenario replay, but does not by itself make tooling-only changes cheap to release.
No cloud run failed; the persistence prerequisite was corrected by source review
before its isolated cloud run. This record preserves that correction explicitly.

Persistence command phases: 88.309s, 116.263s. Controller phases: {"create_including_image": 2.704, "upload": 14.007, "remote_job": 206.363, "download": 2.539, "terminate": 0.974}.
