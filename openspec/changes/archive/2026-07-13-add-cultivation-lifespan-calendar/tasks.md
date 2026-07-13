## 1. Profile V2 Migration

- [x] 1.1 Add immutable non-negative lifespan/reserve fields, current schema version 2, complete v2 copy helpers, and exact v2 default/reset behavior.
- [x] 1.2 Retain an exact v1 DTO decoder, add a pure validated v1-to-v2 migration with zero new fields, encode only v2, and reject all other versions.
- [x] 1.3 Update attachment and snapshot codecs plus every awakening/inheritance/profile-preserving service to retain the two counters.
- [x] 1.4 Add migration, unknown-id, over-cap preservation, v2 round-trip, invalid-counter/version, reset, and initiation-regression tests.

## 2. Realm Lifespan And Configuration

- [x] 2.1 Add required positive `maximum_lifespan_years` to realm definitions and ship exact values `80/120/240` with codec/reference/translation validation.
- [x] 2.2 Add server configuration defaults `24000` ticks/day and `6` days/year with checked products and explicit dynamic-reinterpretation warnings.
- [x] 2.3 Update cultivation debug `info` and `reset` behavior for the complete v2 profile without adding clock/lifespan/reserve setter commands.

## 3. Calendar And Personal Runtime

- [x] 3.1 Add Overworld SavedData for non-negative `elapsedCalendarTicks` and one server-post-tick eligible-player gate independent of vanilla time.
- [x] 3.2 Add UUID-keyed pending personal lifespan counters with survival/adventure/alive eligibility, 600-tick service commits, checked/saturating arithmetic, and failed-commit retry.
- [x] 3.3 Force pending state through safe logout, death/respawn, dimension, save, and clean-stop paths while retaining `copyOnDeath` as the sole profile-copy owner.
- [x] 3.4 Add relative 10/5/1-year warnings, per-session de-duplication, derived exhaustion, unavailable-realm handling, and no automatic consequence.

## 4. Synchronization UI And Validation

- [x] 4.1 Add the compact clientbound time payload/cache with login/lifecycle/config and bounded 600-active-tick synchronization, real buffer tests, and dedicated-server side isolation.
- [x] 4.2 Extend the read-only H screen with year/day, consumed/remaining/maximum lifespan, reserve, exhaustion/unavailable states, and preserved sharp rendering.
- [x] 4.3 Add focused migration/time/lifecycle/config/UI validation and negative fixtures; update core/initiation validators so schema 2 is allowed only through this declared change.
- [x] 4.4 Update README, KB/index, AGENTS acceptance guidance, configuration warnings, and manual pass/fail/`not_verified` ledgers for the new time surfaces.
- [x] 4.5 Run strict change/baseline specs, core/initiation/lifespan validators and tests, Gradle tests/build, jar inspection, and bounded acceptance-server smoke.

## 5. Shared Feature Release

- [x] 5.1 Participate in the owner-approved five-change `0.24.0` feature release: the final serial integration task SHALL update `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together exactly once, while this change records its migration/calendar release notes and SHALL NOT perform a duplicate bump.
