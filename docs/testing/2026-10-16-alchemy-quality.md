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
- Full release/build/jar: pending.
- Clean server and mapped one-/two-client quality evidence: pending.
- Six bilingual furnace layouts and quality name/effect tooltips: pending.

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
