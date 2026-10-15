# Fate choice pool — 0.52.2

Scope: equal tier weights; eight working traits plus 17 selectable high-tier
placeholders with explicit bilingual no-effect labels, empty effects and no
provisional root restrictions. Existing drafts and confirmed choices persist.

- Local fate validator and 29 focused Python tests passed.
- First Modal run `20261015T190749Z-eeb5c920` on `cbd825f` produced the jar but
  failed before tests: standalone FateFoundationTest lacked LoadingModList setup.
  Retained logs identify a test-fixture failure, not a completed test run.
- Fix `71cfe7e` changes only test initialization. Run
  `20261015T191102Z-8a8d94d8` passed all 12 fate tests and the build; Gradle
  84.165s, controller 105.061s, downloaded artifacts, confirmed termination.
- Deployed the first run's jar after resource validation while retrying the test.
  Every ZIP entry's content matches the successful retry jar; archive metadata
  makes their whole-file hashes differ. Installed SHA256:
  `7efc5d35d245c6f43742890579b7df2c0e063673eaabc407d0b4355dd0691c2a`.
- DevHost saved/stopped without forcing, backed up 0.52.1, installed 0.52.2,
  reopened the prior `agent-test` world. DevBridge confirms loaded mod version
  0.52.2 and 25 entries in `myvillage:innate_fate`.
- No full release, broad gameplay suite or automatic candidate reset. Owner
  appearance/selection feel and placebo balance remain `not_verified`.

Current behavior: [KB48](../ai-kb/48_innate_fates.md).
