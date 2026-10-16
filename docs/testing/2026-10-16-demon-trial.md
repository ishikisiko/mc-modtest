# Demon lure pearl — 0.56.0

The consumable pearl starts an in-place, three-wave encounter. Server admission checks
flat supported clear space, loaded chunks, build permission, difficulty and distance
from another trial/cache before creating one core and consuming one item. The existing
battle progress/receipts runtime owns deaths, persistence and the shared reward inventory.
The core disappears after its rewards are taken; another pearl can start a new encounter.

## Evidence

- Runtime source: `566d2cce9f8cfcb524fe388c590d739a3730534a`.
- Modal run: `reports/modal-runs/20261016T115320Z-bc22eb5d/`.
- 13 Java tests passed: DemonTrialRulesTest (3), BattleProgressTest (5), RuinRulesTest (5).
- 30 actual-game checks passed in `artifacts/out/preview/demon_trial_runtime/evidence.json`.
  All nine selected scenarios completed: mapped summon, rejection, shared admission,
  three combat waves, partial restart, reward extraction, core cleanup, another pearl,
  and persistence after looting.
- Mapped right-click creates the core and consumes exactly one pearl. Obstructed space,
  Peaceful and an overlapping attempt preserve the pearl. A second real client cannot
  spend a pearl on an overlapping trial, open early or reset the current guards.
- Mapped mouse attacks defeat all eight guardians across three waves. The fixture uses
  frozen AI and enhanced player strength to isolate interactions, not prove balance.
- Partial restart retains defeated slots and the surviving guard's health. Rewards are
  exactly 12 low-grade spirit stones, 2 huiling pills and 2 yangyuan pills; actual menu
  shift-clicks transfer them. Empty core cleanup permits a new pearl at the same site.
  A subsequent normal restart keeps the new encounter and the previously looted items.
- No DevBridge or owner's computer was used for this test. RCON only prepared fixtures
  and read state; item activation, attacks and inventory transfer used client input.
- The run passed, artifacts were downloaded/hash-verified and termination was confirmed.
  One transient Modal exit-status connection retry recovered; no command/test failed.
- Local resource + Modal controller tests: 23 passed. Item relationships validator and
  generated-resource consistency passed.

## Final resource-only adjustment

`8dd0906` extends the red/cyan status fault to all four sides of the item/core model,
with no Java or encounter-data changes. The runtime results above remain applicable to
behavior; they do not claim that the first screenshot shows this final model. The focused
`demon-trial-build` preset checks resources and repackages that fixed commit.

Owner appearance, balance, feel, audio/FPS and multiplayer experience remain
`not_verified`. The fixture tests are developer evidence. Normal saved restarts are
covered; cross-file crash atomicity and external world-editor modifications are not.

Final packaging run: `reports/modal-runs/20261016T115909Z-961ba542` — passed, artifacts verified, termination confirmed.
Jar: `myvillage-0.56.0.jar`, SHA256 `adde5706665cb68a1da448acfad055ce6495567c8b31a3c4b731f55950258968`. Jar contents and served download
were independently checked. Final asset build: `8dd09060911e00f62bf58a7072d3ef66409c8af7`.
