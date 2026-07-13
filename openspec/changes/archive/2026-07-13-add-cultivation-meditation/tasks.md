## 1. Protocol And Session Core

- [x] 1.1 Add a three-value action codec for normal/spirit/stop, reject unknown values, and prove it contains no identity/transform/profile/result/breakthrough fields.
- [x] 1.2 Add the UUID-keyed transient manager with idle, two 40-tick preparation states, two active states, idempotent transitions, and no persistence.
- [x] 1.3 Route intent through the existing registrar/server-thread contract with sender-derived identity, duplicate-start rate limiting, and no direct attachment write.

## 2. Eligibility And Interruptions

- [x] 2.1 Implement root/Basic-Breathing, survival/adventure, alive, lifespan, supporting-ground, mount/swim/flight/sleep/use, conflict, and 100-tick damage eligibility.
- [x] 2.2 Track authoritative position/dimension with 0.01-block tolerance while excluding yaw and pitch.
- [x] 2.3 Add idempotent interruption hooks for movement/jump, damage, attack/swing, mining, use, mount, swim/flight/sleep/mode conflict, dimension, death, logout, stop, and shutdown.
- [x] 2.4 Prove sessions grant no profile value, reserve/item use, pose, HUD, breakthrough action, or persistent data.

## 3. Client Feedback And Controls

- [x] 3.1 Add clientbound transition/rejection status and an immutable disconnect-cleared cache without per-tick traffic.
- [x] 3.2 Register configurable V normal, B spirit, and G stop keys; do not register N or a breakthrough action.
- [x] 3.3 Add bilingual status/reason messages and keep H non-pausing, sharp, read-only, and free of controls.

## 4. Validation And Handoff

- [x] 4.1 Add tests and focused validation for payload direction/shape, all states/timings/gates/interruptions, side safety, no persistence, and flying-sword regression.
- [x] 4.2 Update README, KB/index, AGENTS probes, key/usage guidance, and the manual pass/fail/`not_verified` interaction ledger.
- [x] 4.3 Run strict change/baseline specs, cultivation core/initiation/meditation validators and tests, Gradle tests/build, jar inspection, and bounded server smoke.

## 5. Shared Feature Release

- [x] 5.1 Participate in the owner-approved five-change `0.24.0` feature release: the final serial integration task SHALL update `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together exactly once, while this change records its meditation notes and SHALL NOT perform a duplicate bump.
