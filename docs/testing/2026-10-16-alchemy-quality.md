# Alchemy quality — 2026-10-16

Version 0.52.0 connects the existing innate alchemy ceiling to manually started
single batches and quality-bearing medicines. Input is the vanilla menu start
intent; the server freezes the brewer UUID, aptitude, ceiling and rolled outcome.
The hidden outcome survives pause/save/restart. Existing pills without a component
retain lower quality and baseline strength. Forging production is not implemented.

## Validation plan and status

- Local lightweight checks: 141 focused Python tests passed in 4.885 seconds; 12 selected source gates
  passed in 3 seconds. Initial source-gate pass caught the old medicine-multiplier
  pattern and the still-empty changelog body; both were corrected before upload.
  Negative mutations now guard active-only saved-quality bonus scaling.
- Cloud environment: authenticated Modal runner, fixed source archives and explicit
  PAL/catalogue hashes; 4 requested CPU cores, 16 GiB memory request/hard limit.
  All Gradle/full release/server/client/two-client execution is remote. No local
  heavy fallback. Environment smoke uses the prior source commit solely to prepare
  and verify the new Node/OpenSpec image; it is not feature validation.
- Cloud full release/build/jar: pass: 62/62 gates without skips; 1062 Java tests
  without failure/error/skip and 1297 Python tests passed. Focused Java command
  131.850s, full release command 450.935s; creation 2.671s, upload 14.276s,
  remote job 586.826s, download 3.797s, termination 0.969s. Confirmed terminated.
  Whole-Sandbox sampled RSS sum peak 2104224 KiB (shared pages counted repeatedly).
  Downloaded jar SHA256 `05940e4df446da907b46e6807c5c980b3df355018f4adba179019e0ffcdbaf46`;
  local read-only alchemy/crafting jar-resource checks also passed.
- Clean server and mapped one-/two-client quality evidence: pass, 42/42 checks.
- Six bilingual furnace layouts and six quality name/effect tooltips: captured and
  inspected; readable without clipped text. Three additional state views inspected.

## Evidence boundaries

Administrator commands set material/aptitude/medicine fixtures; furnace start,
slot-transfer attempts and pill use are actual mapped input. The second player
is a separate real client with a separate directory and username. Full frozen
Batch NBT and component-bearing inventories are compared across restart.
Disabled-button clicks prove UI behavior; crafted malformed vanilla packets are
not claimed as a physical-client test. Legacy-save cold migration, natural
survival acquisition, owner appearance/feel/balance and broader multiplayer remain
not_verified. Declared probability and strength values are provisional balance.

Failures and retry reasons, remote timing/memory observations, source commits,
artifact hashes and termination confirmation are retained in reports/modal-runs.
Preview after evidence collection: http://43.156.135.198:8765/alchemy/quality/index.html

## Fixed-source provenance

The cloud release/build used `9728489`; the completed runtime run used `2055bf6`.
Intermediate commits changed evidence scripts and acceptance preparation only.
Product Java, resources, Gradle inputs and the alchemy asset generator stayed
identical. The downloaded runtime-class manifest independently matches all 869
main classes in the release jar. Delivery documentation follows those results.

The first runtime attempt used `57e5655`, adding three actual inventory-hover
tooltip PNGs per language; its 16 focused parser/geometry tests passed locally.

## Retained first runtime attempt

Run `20261015T164706Z-e92cb5d5` (`57e5655`) passed clean stage1 in 93.265s.
Capture entered the actual game, then failed its precondition because a fresh
options.txt omitted custom default bindings. No gameplay check ran. The known
V/X defaults were registered in code; the corrected evidence script resolves
missing entries from that registration and still rejects a real remapping or
unknown default. 35 focused tests passed before the explicit retry on `d7b25c5`.
The failed capture lasted 67.379s; whole-Sandbox RSS sum peak 5226348 KiB. Evidence
was downloaded and termination confirmed. This was not an OOM or a product bug.
The retry preserves identical Java/resources/build inputs and adds a remote
compiled-class hash manifest for comparison with the released jar.

## Interrupted second runtime attempt

Run `20261015T165345Z-681b229e` (`d7b25c5`) passed clean stage1 in 72.166s
and 18 partial gameplay/layout checks. A source audit found the healing test used
an invalid `/tick freeze` assumption: vanilla excludes players from entity freeze,
and medicine runs in PlayerTickEvent.Post. The Commander explicitly interrupted
the capture before that measurement; exit -2, 116.506s. The old capture report's
partial `pass` is NOT an accepted whole-run result; the separate interruption
record and controller failure take precedence. Termination was confirmed.
All 869 compiled runtime main classes from this attempt match the release jar
byte-for-byte. Two Chinese screenshots were inspected and were legible.
The evidence-only fix `f19f4e3` uses atomic Health+medication reads and actual
elapsed medicine ticks; 21 focused tests passed. It also records interrupts as
failures rather than allowing a partial report to say pass.

## Retained third and fourth runtime attempts

Run `20261015T165854Z-3d66757c` (`f19f4e3`) passed clean stage1 in
131.216s. Both physical clients joined; the fixture placed both at the same
coordinates and the second player obstructed the main player's furnace ray.
Capture failed after 170.817s; 18 partial checks passed, no medicine evidence.
The guest client started in 40.108s with a 768 MiB heap. Explicit separated
view positions and a readback precondition were added in `3c25e21`.
Whole-Sandbox RSS sum peak was 8165256 KiB, with repeated shared-page counting.
Termination was confirmed; there was no OOM.

Run `20261015T170701Z-4ce119bd` (`3c25e21`) failed clean stage1 after
245.769s; capture did not start. An initial Chunky `No tasks running` reply was
mistaken for completion; task completion arrived while server shutdown was
already saving, and clean stop timed out. Commit `2055bf6` waits for a new
selected-world completion log after the pre-start offset. Five regression tests
cover initial idle, stale/wrong-world completion, rounded progress, fast finish
and the unchanged bounded deadline. RSS sum peak was 3222616 KiB. Artifacts
were retained and termination confirmed.

The fifth attempt uses `2055bf6`. Product source, resources, Gradle build inputs
and the alchemy asset generator remain identical to release commit `9728489`.

## Completed Modal runtime validation

Run `20261015T171602Z-8330bc24` on `2055bf6` passed clean stage1 in 70.049s
and all 42 mapped-client checks in 285.004s. Initial setup took 50.030s, the
separate guest client 31.061s, and restart setup 46.013s. Controller phases:
create 2.707s, upload 16.616s, remote job 356.704s, download 2.769s, termination
0.980s; total controller wall time 379.777s. Sandbox termination is confirmed.

The final run's sampled whole-Sandbox RSS sum peaked at 8252868 KiB (about
7.87 GiB, with shared pages counted per process). This is not unique resident
memory, a cgroup peak or local-host usage. The isolated Sandbox requested and
capped memory at 16 GiB. All heavy work stayed on Modal with the persistent
dependency cache; no local Minecraft or Gradle process was used.

Coverage includes no auto-start/auto-chain; rejected unawakened starts; frozen
brewer/score/ceiling/quality; locked inputs; output-block pause without reroll;
all three recipes and costs; actual second-client interference and separate
attribution; mapped pill use; whole Batch and component persistence; sustained
quality/counters across restart; and exact saved-quality output after restart.

The health fixture had a maximum of 30 HP: legacy Yangyuan restored about 0.6
HP per measured interval and quality4 restored about 1.2 HP. Ningshen yielded
15 versus 20 stability per settlement at fixture affinity 10. Huiling readbacks
were 9 versus 17 power, including normal recovery during observation; the
script allows this bounded recovery, while Java tests cover exact restoration.
These are administrator fixtures, not survival balance acceptance.

All 869 runtime main class hashes match the downloaded release jar byte-for-byte;
product Java/resources/build inputs and the alchemy generator are unchanged
between release `9728489` and runtime `2055bf6`. The final lightweight follow-up
checks passed 22 evidence/Chunky tests, jar-aware alchemy/aptitude validators,
version consistency, 85 strict main specs and the change validation.

All 15 downloaded PNGs were inspected: six furnace layouts at GUI 480x270,
427x240 and 320x240 in both languages, six quality4 pill tooltips, and three
state views. No clipped label, button or tooltip was seen. Cobblestone in the
output slot is an administrator pause fixture; quality4 tooltip items are
administrator fixtures, not claimed natural rolls. The unawakened start button
is visible and the server rejects the action without cost. Owner judgments and
current H-page layout revalidation remain `not_verified`.

The public focused preview is served by the existing server bound to the host's
private interface on port 8765 (loopback 8765 belongs to a separate console).
Public HTTP access is checked without replacing that console.

The visual acceptance handoff generator passed with the final cloud Chunky report;
public preview HTTP returned 200. This checks handoff readiness, not owner acceptance.
