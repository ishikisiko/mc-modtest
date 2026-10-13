## 1. Planning and contracts

- [x] 1.1 Record CRAFT scope, source/dependency upload boundaries, resource/timeout limits and separate local/cloud evidence.
- [x] 1.2 Author the proposal, design and capability deltas with explicit unverified cloud gates.
- [x] 1.3 Confirm the pinned SDK API and resource-unit semantics against primary documentation/runtime signatures.

## 2. Local runner

- [x] 2.1 Implement fixed-ref archive planning, secret/path exclusions, exact PAL/catalogue hashing and separately identified bootstrap upload.
- [x] 2.2 Add optional pinned SDK installation and doctor/plan/run/status/terminate commands.
- [x] 2.3 Implement single local lock, named Sandbox identity, bounded requests/timeout, no automatic retries and cleanup/TTL recovery.
- [x] 2.4 Add source-test/build presets, experimental server/client presets, ephemeral workspaces/worlds and dependency-only cache mounting.
- [x] 2.5 Fetch bounded artifacts and write source/environment/cache/phase timing and failure evidence.
- [x] 2.6 Test archive safety, fixed-ref behavior, lifecycle failure/cleanup, command validation and artifact handling locally without paid compute.

## 3. Authenticated cloud verification

- [x] 3.1 Authenticate through the user's Modal environment and run a bounded real smoke, recording setup/upload/execution/download/termination.
- [x] 3.2 Verify downloaded reports, observed resource/cache facts and confirmed remote termination for the smoke.
- [x] 3.3 Exercise the intended Python and Java/build presets remotely and distinguish cold setup from dependency-cache reuse.
- [x] 3.4 Exercise server/client capture remotely before promoting those presets from experimental; retain unobserved capture/owner surfaces as not_verified.

## 4. Documentation and release handoff

- [x] 4.1 Document exact commands, fixed-commit semantics, dependency cache isolation, timeout/recovery and authentication requirements in README/KB/AGENTS.
- [x] 4.2 Record local checks and any real cloud attempts with durations in a persistent test report; preserve failed attempts and never substitute mocked pass for cloud pass.
- [x] 4.3 Apply openspec/config.yaml rules.tasks for version 0.46.1 and verify a practical jar/release metadata without claiming it validates the cloud runner.
- [x] 4.4 Validate OpenSpec strictly and finish CRAFT provenance; retain this change active while required real-cloud gates remain unverified.
