# Sword Obsession 0.60.2 verification

Scope: implement `sword_obsession` and an independent server-created sword-wave
channel. Protocol 30; no player-root, budget, rarity, existing active-skill range,
weapon-model or animation retuning. The selected fate ID remains compatible with
old saves; the new streak is online-only and is never saved.

## Automated checks

- Python: 56 focused fate-validator and Modal-runner tests passed; combat data
  loaded through `tools.combat_data.load_strict()` with both skill definitions.
- `sword-fate-build`, source `b16eaa7`, run
  `20261017T134652Z-6f509e6d`: 250 Java tests across 44 classes passed, plus jar.
  Coverage includes fate definition/codec, stack cap/expiry/per-move deduplication,
  finisher recognition, independent hit ledgers, zero-payment/refund behavior,
  every packet phase codec, active sword-qi behavior and client wave geometry.
- Later commits through `a8754d0` change only the runtime harness; every jar entry
  was compared byte-for-byte with the successful Java test build and matches.

## Retained failures and scope

Initial run `20261017T134347Z-e620e256` at `ce80876` compiled successfully but
reported 5 failures out of 394 tests. Two tests still pinned the previous protocol
and Sword Obsession's placeholder state; the updated behavior is now asserted.
The other three failures are unrelated existing arm-IK/off-hand tests. To establish
that directly, `20261017T134531Z-64749278` tested the original main commit
`2c1b08c`: the same three tests failed (23 total), at the same source lines.
The focused preset excludes those unchanged pose suites; their defect remains
open and no whole-suite pass is claimed.

The legacy `validate_sword_combat_foundation.py` also requires three retired
command/dependency strings in AGENTS.md. Current AGENTS.md and WORKFLOW.md no
longer carry those lists; neither file was modified here. Its initial default
jar check found the older local 0.60.1 jar before the remote result was copied.
The current authored combat data and packaged resources receive focused checks.

## Runtime evidence

`sword-fate-runtime`, source `a8754d0`, run `20261017T135115Z-72ddb28c`:
**17/17 checks passed**, no unrun scenarios. Evidence is under
`reports/modal-runs/20261017T135115Z-72ddb28c/artifacts/out/preview/sword-fate/`.

- Mapped testing-stele awakening and actual selection of Sword Obsession.
- Five mapped sword attacks produced stacks `[1,2,3,4,5]`; baseline damage for the
  first four moves was `[9.45,9.975,10.5,11.55]`, enhanced damage was
  `[9.45,10.773,12.18,14.32204]`, matching prior-stack factors within float error.
- One finisher-generated wave hit a distant target; no recursive stack/refund
  receipt appeared. A stone wall blocked it.
- Online timeout cleared the streak. A live-full-streak precondition was observed
  before the mapped slot switch; swapping back did not restore it.
- Two actual clients had separate fate/streak readbacks (owner 5, guest 0);
  guest process cleanup was verified.
- Mapped active sword qi still hit its targets and earned no sword stacks.
- Death kept the chosen fate and cleared the streak; a real server/client restart
  kept the chosen fate and still had zero transient stacks.

The earlier runtime run `20261017T134814Z-f4077fb2` stopped in setup: a surviving
baseline target obstructed the testing-stele interaction. It was retained, then
the harness was corrected to remove the target and observe its disappearance
before selection. No gameplay source changed in response. The swap assertion was
also moved before guest shutdown to require a live streak rather than an already
expired one.

Both initial and retry runtime jars were built in Modal. The final delivered
`build/libs/myvillage-0.60.2.jar` is copied from the successful runtime run:

`51c95a81aa0409feb290fdb9b8a6be30e3503b7ef5cb2ecd89f7ab36732b7ba5`

Container timestamps make whole-jar hashes differ between builds, but the entry
sets and all entry contents match the successful Java test build. Fate, wave,
language and unchanged active-skill resource bytes also match the workspace.
The English selected-fate screenshot was inspected; it shows Sword Obsession
without the placeholder label. This is developer UI evidence, not a verdict on
combat visuals or owner acceptance.

## Measured time and cleanup

Successful focused build: Gradle 47.084 s, controller total 70.281 s.
Successful runtime: Gradle 40.395 s; gameplay harness 220.745 s, including initial
server/client startup 68.107 s, second client startup 38.739 s and restart startup
56.021 s. Runtime controller total 283.750 s, including upload/download/cleanup.
These nested phases are not additive independent totals. The SDK reported a
transient exit-status retrieval retry; it did not restart or duplicate the job.
All five Modal runs (including failures and the original-main comparison) have
`termination: confirmed`, with downloaded, verified artifacts.

## Acceptance boundaries

Developer input/readback does not establish owner acceptance. Combat feel,
long-term balance, owner visual/sound preference and FPS remain `not_verified`.
Broad multiplayer, real old-save upgrade, and simultaneous active/passive visual
overlap were not observed in this focused run.
The existing unrelated first-person IK failures above are not fixed by this task.
