# Crafting aptitude foundation — 2026-10-15

The owner explicitly selected **same-recipe product-quality ceilings**, not
recipe grade or growth speed. Version0.51.0 adds independent player aptitudes,
saved once and shown on the read-only H Crafting page. Current ungraded pills,
furnace automation and forging-production absence remain unchanged.

## Developer checks

- First focused Gradle run:288 tests in50 suites, zero failure/error/skip,
  23-second Gradle wall time. Covers deterministic generation/domain separation,
  pending vs initialized-zero, no reinitialization, all101 score thresholds,
  strict data/codecs, aliases/bounds, snapshot and client readouts.
- Focused Python:77 tests (crafting, initiation read-only guards and release gate)
  passed. All seven selected cultivation/crafting validators passed.
- Full release gate:62/62 passed on the first attempt in7m11s. Full Python1272
  tests passed (13 environment skips separately passed with the13 preview tests).
  Full Java1049 tests passed with no failure/error/skip. All866 compiled main
  classes match the fresh0.51.0 jar byte-for-byte; regeneration changed no resource.
- Clean stage1 server passed,15:25:51–15:27:08 UTC (77 seconds).
- First mapped evidence recorded37/37 passed checks and completed both session
  cleanups. The command tool nevertheless reported exit143; no cause was
  established, so it is retained as an execution-wrapper anomaly rather than
  silently counted as a clean process pass. First artifacts remain under
  `out/preview/crafting_aptitudes_attempt1/`; an isolated subprocess retry records
  its actual return code separately.
- All six bilingual target-size pages plus Chinese pending state were inspected;
  no clipped aptitude values or quality labels observed. Narrow screens stack
  the two cards, and the foundation boundary remains readable.
- Isolated retry passed37/37 checks and exited0; measured wrapper wall201.540s.
  Initial setup78.031s, Chinese layouts11.087s, first stop9.018s, restart setup78.029s,
  English layouts10.973s, final stop5.004s. Remaining time contains fixture commands,
  polling and report writes; it is not labeled model thinking time.
- Actual checks include mapped awakening, all quality-threshold boundaries for
  both professions, alias equivalence, invalid score rejection, read-only page
  clicks, death, root clear/reawakening, cultivation-profile reset and save/restart.
  Administrator70/100 screenshot values are explicit fixtures, not natural rolls.

## Evidence boundary

`tools/crafting_aptitude_evidence.py` uses actual mapped testing-stele and H input,
with RCON for explicit administrator setup and authoritative readback. It tests
score boundaries with fixtures rather than claiming those values were naturally
rolled. Client screenshots target480x270,427x240,320x240 in both languages. A
second session reads the same world after restart.

Preview: <http://43.156.135.198:8765/crafting_aptitudes/index.html>.
Detailed commands, checks, measured phase timings and retained logs live beside
its `evidence.json`. Developer results do not establish owner appearance, aptitude
balance or multiplayer acceptance. Legacy-save live migration and dimension-change
capture remain not_verified; pure/source tests cover their foundations.
