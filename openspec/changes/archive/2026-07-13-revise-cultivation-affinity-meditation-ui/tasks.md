## 1. Profile Schema V3

- [x] 1.1 Add non-negative integer `spiritualAffinity` to immutable `CultivationProfile`, make schema `3` current, and set new/reset defaults to `10` without removing `meditationQiReserve`.
- [x] 1.2 Retain exact version-specific v1 and v2 decoders, implement validated v1-to-v2-to-v3 and v2-to-v3 migrations, preserve unknown ids/over-cap progress/lifespan/reserve, write only v3, and fail unsupported versions explicitly.
- [x] 1.3 Update every profile factory, copy helper, codec, snapshot, initiation, lifespan, command, settlement, advancement, and lifecycle replacement to preserve affinity and reserve through one `CultivationService` commit.
- [x] 1.4 Ensure no client payload, UI field, command shortcut, or direct attachment write can author spiritual affinity or bypass `CultivationService` validation.

## 2. Caps And Settlement Arithmetic

- [x] 2.1 Keep source-stage progress caps at `1000/1100/1200/1300`, derive stability caps and requirements at 50% (`500/550/600/650`), retain durations/penalties/deterministic outcomes/Qi-IV ceiling, and validate authored compatibility costs as half the requirement.
- [x] 2.2 Replace progress's 100-tick/year-scaled batching with one server-owned settlement per ten eligible active ticks; normal mode adds current affinity, discards partial-session ticks, clamps at cap, and banks no overflow.
- [x] 2.3 Implement fixed spirit output of `50` per ten eligible ticks, add positive stage-owned `spirit_stone_cost` definition data (sensed/Qi I=`1`, Qi II=`2`, Qi III=`3`, later Qi layers matching their layer), reject capped stages without a cost, clamp only output on a nonempty final batch, and apply no cost at cap or an unsupported stage.
- [x] 2.4 Retire reserve gameplay by removing every credit, spend, conversion, scan, and presentation path while preserving the stored v3 value unchanged.
- [x] 2.5 Keep stability unchanged before progress is already full; beginning with the next ten-tick settlement, add current affinity in either mode, clamp to 50% of the stage progress cap, consume no stones for stability, and keep only Basic Breathing mastery at `10` per configured year with a transient remainder.
- [x] 2.6 On deterministic advancement, reset progress and retain integer-floor half of current stability while preserving the existing interruption penalty rules.

## 3. Atomic Spirit Transactions

- [x] 3.1 Revalidate session, profile, definitions, stage, remaining cap, and full server-derived item cost before each spirit batch and inspect only ordinary player inventory.
- [x] 3.2 Make exact item removal plus one immutable profile installation logically atomic: restore partial removals or full cost on pre-install failure and never send an intermediate profile snapshot.
- [x] 3.3 Distinguish successful attachment installation from later snapshot-delivery failure so a committed batch is not refunded or replayed, and add duplicate/reentrant settlement protection.
- [x] 3.4 When the full cost is unavailable, consume nothing, downgrade the existing session to normal without preparation, apply the due affinity result, and send one transition-only status.

## 4. H Screen Meditation UI

- [x] 4.1 Refactor the H screen into stable responsive Profile and Meditation tabs while preserving non-pausing toggle behavior, missing-data states, sharp-content render ordering, and supported GUI-scale text bounds.
- [x] 4.2 Show schema v3, spiritual affinity, and the dynamic stage stability cap on Profile; preserve existing root/technique/calendar/lifespan/advancement content and remove all visible reserve labels or values.
- [x] 4.3 Show current session status, progress/cap, normal affinity result, fixed spirit result, source-stage stone cost before cap, no-stone-cost state after cap, locked/active stability rate and cap, mastery context, and release-ceiling state on Meditation with translatable labels.
- [x] 4.4 Add normal, spirit, stop, and advance buttons with stable dimensions and clear enabled/disabled/hover/focus states; route one click to exactly the existing `START_NORMAL`, `START_SPIRIT`, `STOP`, or `START_BREAKTHROUGH` action.
- [x] 4.5 Retain V/B/G/N key parity and prove opening, rendering, closing, or switching tabs sends no intent; keep all displayed costs/rates advisory and revalidated by the server.

## 5. Automated Coverage And Validators

- [x] 5.1 Add Java profile tests for exact v3 defaults/invariants including non-negative stability above 100, v1/v2 migrations, unsupported versions, unknown ids, non-default affinity/reserve round trips, real snapshot buffer round trip, and preservation across every replacement family.
- [x] 5.2 Add Java settlement tests for ten-tick eligibility, partial discard, default/zero/non-default affinity, every released spirit cost, fixed/clamped progress, pre-cap stability lock, next-batch activation, affinity-paced stability in both modes without stone cost, dynamic cap, inert reserve, mastery rate, and downgrade arithmetic.
- [x] 5.3 Add transaction tests for insufficient and partial removal, validation/install rollback, post-install snapshot failure without refund, external-container exclusion, and exactly-once settlement.
- [x] 5.4 Add advancement tests pinning progress/stability caps, compatible authored requirements/costs, duration/penalty behavior, success-time halving including integer floor, one-stage reset/no-chain semantics, and rejection at Qi IV without synthesized `1400` behavior.
- [x] 5.5 Add client/payload tests for both H tabs, four buttons, dynamic stability cap/rate/lock presentation, one-intent routing, V/B/G/N parity, disconnect cleanup, missing-data rendering, absence of reserve, and absence of client-authored numeric fields.
- [x] 5.6 Revise the focused Python validators and negative fixtures for schema v3, ten-tick progress/stability arithmetic, direct costs/rollback, dynamic caps, halving, H tabs/buttons, localization, docs, version, and current-jar contents; keep behavioral proof in Java tests where source inspection is insufficient.

## 6. Documentation And Release Metadata

- [x] 6.1 Update README player flow, H-tab controls, V/B/G/N parity, affinity/rates/costs/progress and stability caps, post-cap consolidation, success halving, inert-reserve compatibility note, exact manual commands, and the pass/fail/`not_verified` real-client ledger.
- [x] 6.2 Update `docs/ai-kb/30_cultivation_playable_loop.md`, `docs/ai-kb/28_cultivation_core.md`, `docs/ai-kb/INDEX.md`, the validation checklist, AGENTS cultivation-loop acceptance guidance, and same-topic OpenSpec cross-links without claiming unobserved gameplay or visuals.
- [x] 6.3 Apply the large-feature version rule from `openspec/config.yaml`: update `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together to `0.25.0`.
- [x] 6.4 Confirm bilingual labels, status/rejection messages, tab names, button names, affinity, direct costs, progress/stability caps, stability lock/rate/retention, and release-ceiling text are complete and packaged.

## 7. Strict Regression And Build Handoff

- [x] 7.1 Run strict validation for `revise-cultivation-affinity-meditation-ui`, strict baseline validation, and strict validation of `add-spirit-stone-resources`, `add-cultivation-lifespan-calendar`, `add-cultivation-meditation`, `add-basic-breathing-cultivation-gain`, and `add-qi-refining-advancement`; resolve rather than waive any semantic conflict.
- [x] 7.2 Run `validate_cultivation_core.py`, `validate_cultivation_initiation.py`, `validate_spirit_stone_resources.py`, `validate_cultivation_lifespan.py`, `validate_cultivation_meditation.py`, `validate_cultivation_gain.py`, and `validate_cultivation_advancement.py`, including all corresponding validator unit tests and current-jar checks.
- [x] 7.3 Run structure/resource generation required by the build, `./gradlew test`, and a practical `./gradlew build`; verify the fresh `myvillage-0.25.0.jar` contains every changed class, resource, translation, definition, and UI asset.
- [x] 7.4 Run the bounded stage-1 acceptance server, await clean shutdown, and inspect logs for codec/migration, registry, payload direction, client-classloading, item, save, and cultivation errors.
- [x] 7.5 Run CRAFT/front-door governance checks, record actual command evidence, run `git diff --check`, and ensure generated `reports/` output is not staged.

## 8. Visual And Real-Client Acceptance

- [x] 8.1 Generate the visual acceptance report, inspect representative Profile/Meditation screenshots at desktop and constrained GUI scales, and critically record dynamic stability text fit, hierarchy, sharpness, tabs, buttons, focus, disabled states, and visual regressions.
- [x] 8.2 Serve `out/preview/` over HTTP until owner visual review ends and keep every unobserved H-screen judgment explicitly `not_verified` rather than inferring it from build or headless smoke.
- [x] 8.3 Execute the README real-client ledger for migration/default affinity, both tabs, four buttons and key parity, normal ten-tick gain, each layer cost, downgrade, progress-cap/no-charge, pre-cap stability lock, post-cap affinity stability, dynamic stability caps, success halving, mastery, all four thresholds, Qi-IV ceiling, interruption, lifecycle preservation, initiation, and flying-sword regression.
- [x] 8.4 Record the owner verdict and remaining manual risks; only after required gates and the chosen closeout verdict, synchronize/archive and follow the repository branch/fast-forward/push workflow without converting `not_verified` entries into passes.
