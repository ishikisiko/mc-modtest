# Optional GenOps tools

GenOps is enabled only by an explicit CRAFT/GenOps request. Normal development follows
[WORKFLOW](../WORKFLOW.md); no run, proposal, role approval or archive is required.

`genops/commander.yaml` routes opted-in work. `tools/genops/commander.py` manages
optional runs and a rebuildable `.genops/state.sqlite` index. `run_pipeline.py`
creates scoped task bundles, artifact indexes and gate results under ignored
`reports/agent_runs/`. The frontdoor and patch guards apply inside these runs.
`validate_pipelines.py` checks pipeline role/scope consistency.

State rebuild reads local manifests and decision evidence, without an external
spec CLI. Closeout readiness requires actual run evidence, validation and an OK
verdict; it does not scan a proposal directory. Subagents require explicit user
request. Keep owner decisions separate from developer evidence.

See [GenOps reference](../../genops/README.md), [CRAFT](../../CRAFT.md), and
[tooling contracts](../contracts/tooling.md#genops).
