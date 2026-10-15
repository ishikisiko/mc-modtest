# Fate rarity-count cap removal — 2026-10-15

Version **0.49.2**, protocol 19 unchanged. The shared server/client fate-selection
validator now permits multiple high-tier choices. Exactly three choices, current
point budget, unique groups, eligible definitions, stale-request and live-stele
session checks remain. Default budget is still 8 (configurable 3–18).

## Evidence

- New pure production-transition regression confirms two high-tier entries plus
  an ordinary entry at budget 10, and three high-tier entries at budget 15.
  The same test rejects budget 14 for the latter, duplicate groups, and only two
  selections. Definitions are test fixtures: the shipped first batch currently
  contains only one purple-or-higher fate, and no new gameplay entries were added.
- Full Java suite: **1009 tests**, zero failures/errors/skips. Summed XML suite
  execution time **61.976 s**.
- Focused Python fate/initiation validation: **46 tests**, zero failures.
- Selected release checks: **14/14 passed**, including existing cultivation and
  combat validators, a freshly built jar and README jar-entry checks.
- Final fate jar validator passed. All **831** compiled classes match the jar;
  both language resources match source and the old high-tier error key is absent.
- Clean bounded stage-1 dedicated server startup, registration, save and stop
  passed. There were no failed gate attempts or retries in this patch.

## Measured phases

Existing dependency/Gradle caches were reused on the 4-core host. Heavy jobs
were serialized. Structure data/generators were unchanged; build used
`./gradlew build -x generateAllStructures --console=plain`.

| Phase | Wall seconds | User/system CPU seconds | Max RSS KiB |
|---|---:|---:|---:|
| Focused Python | 2.54 | 1.94 / 0.52 | 23808 |
| Build and selected release gates | 91.26 | 184.12 / 6.37 | 820544 |
| Clean stage-1 setup/server/smoke/save/stop | 78.98 | 150.41 / 7.88 | 1057788 |

The Gradle build step took 82.7 s within the 91.26-second gate invocation.
Java XML execution time is nested inside that build; do not add it again.
Logs and measured records: `reports/agent_runs/20261015-fate-tier-cap-impl/verification/`.

## Artifact and limits

`build/libs/myvillage-0.49.2.jar`, SHA-256
`8261b199631cbe746d56003d9d2db613aeff499608e9904d870383b9ace95c77`.

The language copy and capture helper were aligned with the new rule. No new
client screenshot or multi-high-fate physical-client trial was run for this
bounded policy change. The [0.49.1 awakening captures](2026-10-15-stele-awakening.md)
remain layout/interaction evidence for that version; the public preview labels
its older screenshots and notes the updated rule. Owner balance/feel and
multiplayer remain unverified. Two 4-point fates and one 1-point fate require a
budget of at least 9 even after the rarity-count cap is removed.

See [KB 48](../ai-kb/48_innate_fates.md) and
[innate-fates](../../openspec/specs/innate-fates/spec.md).
