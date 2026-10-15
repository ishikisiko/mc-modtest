# Acceptance workflow — 2026-10-16

0.52.1 changes Python acceptance tooling and release metadata only. Gameplay and
save formats remain unchanged. Owner approval covers independent cases and
one-Sandbox combined delivery. Clean stage1 remains required; Chunky decoupling,
automatic impact selection and cross-run build/world caching are deferred.

## Validation

- Local focused scenario/controller tests: 41 passed; failure-injection output is expected.
- Modal isolated case and combined delivery: pending recorded runs.
- Full release, fresh jar, stage1 and complete game evidence will run through the
  new combined preset on one immutable source. Partial cases cannot establish full acceptance.

## Boundaries

Fixtures use RCON setup/readback; actual gameplay uses mapped client input. No
DevBridge and no local heavy fallback. Preserve failures, per-command timing,
source hashes and confirmed Sandbox cleanup. Owner feel/balance and broader
multiplayer are not reclassified by this tooling change. GUI/tooltip PNGs are
developer evidence. This report is the current execution record; historical
0.52.0 counts remain in their original report.
