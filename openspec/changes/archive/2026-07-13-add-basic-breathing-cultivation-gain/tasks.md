## 1. Stage Caps And Definitions

- [x] 1.1 Add optional positive `cultivation_cap` to stage codec/data and preserve lifespan/foundation metadata and registry-driven lookup.
- [x] 1.2 Ship exact caps `300/500/800/1200` for qi-sensed through Qi Refining III; leave all later stages uncapped and unavailable for gain.
- [x] 1.3 Add codec, shipped-data, missing/invalid cap, datapack reload, and legacy over-cap preservation tests.

## 2. Basic Breathing Settlement

- [x] 2.1 Add a fixed Basic Breathing settlement service with 100-tick batches, live configured-year denominator, and transient integer remainders.
- [x] 2.2 Implement normal rates `100` progress, `10` stability, and `10` mastery per year and spirit total progress `400` without lifespan acceleration.
- [x] 2.3 Apply current-stage cap clamping with no overflow while allowing applicable stability and mastery to continue at the cap.
- [x] 2.4 Interrupt active sessions on time-scale reload and controlled definition/profile invalidation without adding a generic executor.

## 3. Reserve And Inventory Transaction

- [x] 3.1 Spend one persistent reserve per applied bonus progress and consume one low-grade spirit stone for exactly 100 reserve only at a due settlement.
- [x] 3.2 Coordinate ordinary-inventory removal and one final immutable `CultivationService` replacement with failed-removal no-op and failed-commit item restoration.
- [x] 3.3 Downgrade unavailable spirit acceleration to normal meditation with one status, no current-spiritual-power use, and no per-tick inventory scan.

## 4. Synchronization UI And Validation

- [x] 4.1 Synchronize one final changed profile per successful settlement, keep remainders transient, and add real status/snapshot traffic tests.
- [x] 4.2 Extend read-only H presentation with current/cap, stability, Basic Breathing mastery, reserve, and unsupported-stage states.
- [x] 4.3 Add fixed-point/rate/cap/reserve/rollback/downgrade/lifespan-invariance tests and focused gain validation with negative fixtures.
- [x] 4.4 Update README, KB/index, AGENTS acceptance guidance, controls/status text, and manual pass/fail/`not_verified` evidence.
- [x] 4.5 Run strict change/baseline specs, core/initiation/lifespan/meditation/gain validators, tests, Gradle tests/build, jar inspection, and bounded server smoke.

## 5. Shared Feature Release

- [x] 5.1 Participate in the owner-approved five-change `0.24.0` feature release: the final serial integration task SHALL update `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together exactly once, while this change records its gain notes and SHALL NOT perform a duplicate bump.
