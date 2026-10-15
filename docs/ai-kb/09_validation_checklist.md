# Validation selection

Follow [WORKFLOW](../WORKFLOW.md) for the minimum checks and failure-driven expansion.
A successful focused check ends that check stage; neither committing nor building a
jar automatically requires the full release suite.

- Python/tools: affected tests and offline plan/fixture checks.
- Runtime/resources: related logic and resource checks; compilation or packaging when affected.
- Persistence/protocol: affected codec, lifecycle or authority boundary; restart/multiplayer only where necessary.
- Visual work: representative preview and separate owner verdict. Developer captures are not acceptance.

Full release tooling remains `python3 tools/release_gate.py --list` and `--only` for
selected steps. The complete gate includes generation, validators, tests, build and
jar checks; choose it only for complete release validation. Required heavy execution
uses the [Modal runner](46_modal_test_runner.md). Chunky belongs to structure checks.

Record exact selected/passed/failed/unrun checks. Unobserved owner review remains
`not_verified`. Keep generated reports ignored, preserving the two tracked baselines.
See [tooling contracts](../contracts/tooling.md) and README commands.
