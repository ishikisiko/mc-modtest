## ADDED Requirements

### Requirement: Remote test evidence identifies provenance and measured phases
Remote test evidence SHALL record the tested commit, source archive hash, explicit dependency hashes, executor identity, requested resources, observed environment, cache context, UTC timing boundaries, per-phase durations and final exit/timeout/cleanup status. Setup, upload, execution, download and termination SHALL remain distinguishable where observable. Failed attempts SHALL be retained without automatic retries. Local contract tests, real cloud smoke and owner gameplay acceptance SHALL be separate verdicts.

#### Scenario: A cached remote build completes
- **WHEN** a remote build uses a persistent dependency cache and produces test reports
- **THEN** the handoff SHALL disclose the cache and identify the exact source/executor
- **AND** it SHALL NOT present total runner duration as pure test execution time or call the run a cold benchmark

#### Scenario: Authentication prevents real cloud execution
- **WHEN** local tests pass but the real cloud smoke cannot authenticate
- **THEN** the acceptance ledger SHALL record local pass and cloud `not_verified`
- **AND** the change SHALL remain active rather than claim a completed cloud integration
