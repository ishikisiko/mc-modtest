## Why

The local Minecraft build and client-capture workload competes for a small host's CPU and memory, and its shared acceptance directory has already invalidated a persistence trial. An on-demand remote test runner provides a fixed-revision, isolated execution path with measured resource and timing evidence without replacing local or owner gameplay acceptance.

## What Changes

- Add a Modal Sandbox runner for offline planning, authentication diagnostics, bounded remote runs, status and explicit termination.
- Upload a fixed Git commit archive plus only the exact required root PAL jar and `exmod/mod_block_catalog.json`, recording content hashes; keep generic untracked files, credentials and local worlds out of the upload.
- Separately identify and hash the runner bootstrap so it can test an older source commit without concealing which executor was used.
- Serialize local runner launches, use one named Sandbox identity, request bounded CPU/memory, disable automatic retries, and terminate after success, failure or cancellation with a provider TTL as a crash backstop.
- Preserve only a dedicated Gradle dependency cache; source, worlds and test state remain ephemeral.
- Fetch logs and test artifacts, and report setup/upload/test/download/termination phases with environment and cache context.
- Distinguish local contract tests from authenticated cloud execution. Client capture remains experimental until observed; no DevBridge is used.

## Capabilities

### New Capabilities

- `modal-test-runner`: Fixed-commit remote test execution, bounded lifecycle, artifact collection, and explicit authentication/evidence state.

### Modified Capabilities

- `validation`: Remote evidence includes source/executor identity, per-phase timing, cache conditions and truthful unverified states.
- `combat-capture-tooling`: Remote client capture preserves existing input and observation boundaries and never persists shared worlds in the dependency cache.

## Impact

Adds the optional `tools/modal_runner` Python tooling and a pinned optional SDK environment, local regression tests, ignore rules, commands and documentation. The mod's gameplay and network authority are unchanged. Version metadata follows `openspec/config.yaml` `rules.tasks`, targeting 0.46.1. Real cloud smoke requires an authenticated Modal account; absent authentication is a documented unverified gate, not a passed test or an archive-ready change.
