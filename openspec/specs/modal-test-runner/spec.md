# modal-test-runner Specification

## Purpose

Define fixed-commit on-demand remote tests, bounded Sandbox lifecycles, explicit input provenance, scoped dependency caching and separately measured cloud evidence.

See also: [Modal Test Runner](../../../docs/ai-kb/46_modal_test_runner.md).

## Requirements

### Requirement: A remote run identifies immutable source and explicit dependencies
The runner SHALL resolve the requested ref to a fixed Git commit and upload its archive rather than the worktree. It SHALL record the full commit and archive hash. Generic untracked files, credentials and local worlds SHALL NOT be uploaded. The exact root `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` and `exmod/mod_block_catalog.json` SHALL be the only additional local dependencies; each SHALL be frozen into the run input directory and independently hashed. No other `exmod` file or mod jar SHALL be implicitly uploaded. Any separately uploaded runner bootstrap SHALL have a separately recorded identity and hash.

#### Scenario: Local changes exist when a commit is planned
- **WHEN** tracked modifications and unrelated untracked files are present
- **THEN** the planned source SHALL remain the selected committed tree
- **AND** none of those local changes SHALL enter the upload except the two declared exact dependency files

#### Scenario: An upload contains forbidden private material
- **WHEN** pre-upload inspection detects credentials or forbidden private configuration
- **THEN** the runner SHALL stop before remote upload and report the path or rule without printing secret contents

### Requirement: Remote execution is bounded and explicitly terminated
The runner SHALL serialize local launches with one lock, record the remote Sandbox id, and use bounded CPU, memory and execution timeout requests. It SHALL use zero automatic retries. Success, test failure, timeout, cancellation and artifact-fetch failure SHALL all reach an attempted termination. A provider-enforced TTL SHALL bound remote lifetime if local cleanup cannot execute. A failed termination request SHALL remain visible and SHALL NOT be described as confirmed termination.

#### Scenario: A remote test fails
- **WHEN** the test process returns a nonzero exit status
- **THEN** the runner SHALL retain the failure, attempt to fetch available evidence, and request Sandbox termination without retrying the test

#### Scenario: Artifact download fails
- **WHEN** downloading artifacts raises an error
- **THEN** cleanup SHALL still attempt Sandbox termination
- **AND** the run SHALL report the collection failure instead of a successful complete handoff

### Requirement: Workspace and worlds are ephemeral while dependency caching is scoped
A run SHALL use an ephemeral source/workspace/world tree. Any persistent volume SHALL contain only the dedicated Gradle dependency cache and SHALL NOT contain source, Minecraft worlds, credentials or output artifacts. Evidence SHALL report cache configuration and observed cache state separately from the source identity.

#### Scenario: A later run reuses dependencies
- **WHEN** a second run mounts the same dependency cache
- **THEN** its source and Minecraft worlds SHALL still be freshly isolated for that run

### Requirement: Planning is offline and remote execution requires authentication
The runner SHALL expose offline planning, prerequisite diagnostics, bounded execution, status and termination. Offline planning SHALL allocate no paid remote compute. Authentication data SHALL remain outside source archives and test logs. Missing authentication SHALL leave real cloud execution `not_verified` rather than be converted into a successful smoke.

#### Scenario: The account is not authenticated
- **WHEN** a user prepares or locally tests the runner without usable cloud credentials
- **THEN** offline results SHALL remain available
- **AND** cloud smoke SHALL be explicitly unverified

### Requirement: Presets preserve their validation status
The runner SHALL expose bounded known test presets with their exact commands. A client/capture preset SHALL remain experimental and unverified until authenticated remote input, artifacts and cleanup have been observed. It SHALL NOT use DevBridge or treat local mocked tests as cloud validation.

#### Scenario: Only local lifecycle tests pass
- **WHEN** tests simulate successful Sandbox execution and termination
- **THEN** the result SHALL prove only the local orchestration contract
- **AND** remote build, graphics, capture and termination SHALL remain separately unverified

### Requirement: Heavy alchemy quality checks run remotely
Full build/release, acceptance-server, client and two-client checks SHALL default to Modal rather than local heavy execution. The runner SHALL provide fixed-commit presets for the complete release gate and alchemy-quality acceptance, including clean stage1, mapped one-client brewing/use/restart/layout evidence and a two-client attribution check. Each run SHALL retain bounded resources, command timings, artifacts and confirmed cleanup. Failure SHALL NOT trigger a local heavy-test fallback.

#### Scenario: Cloud validation completes
- **WHEN** the remote release and alchemy-quality presets pass
- **THEN** their jar and evidence SHALL be downloaded and verified against the recorded source commit before delivery

#### Scenario: Cloud execution fails
- **WHEN** a remote job fails or cannot allocate
- **THEN** the failure SHALL remain explicit and no local Gradle or Minecraft job SHALL start automatically
