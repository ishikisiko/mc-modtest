## Context

The user authorized an on-demand Modal Sandbox integration after reviewing the local sword-qi test measurements. The base is `47525d3` (0.46.0). This is test infrastructure, not a new gameplay capability. Existing owner acceptance remains separate. CRAFT planning and implementation runs record the source-upload and lifecycle scope before code changes.

## Goals / Non-Goals

**Goals:** Run known source revisions away from the constrained local host, collect reusable evidence, separate setup from execution cost, and bound every remote run's resource/time lifetime.

**Non-Goals:** Persistent source or worlds, generic worktree uploads, DevBridge, automatic retry, public service hosting, guaranteed physical GPU behavior, or unmeasured cloud throughput/price claims.

## Decisions

1. **Archive the resolved commit, not the checkout.** Resolve `--ref` to a full commit and use Git's archive content; record both commit and hash. A dirty or untracked local file cannot silently enter the test. The explicit dependency allow-list contains exactly the root `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` and `exmod/mod_block_catalog.json`, each frozen and hashed. The catalogue is required by structure generation during a real build; this does not authorize uploading the whole `exmod` directory or other mod jars. Credentials and suspicious private/config files must be rejected/excluded before upload. The executor bootstrap is separately hashed and disclosed, allowing an older commit to use current orchestration without pretending it came from that commit.
2. **Bound a named Sandbox.** A single local runner lock protects each launch; use the named `myvillage-test-runner` Sandbox in its app to detect conflicting runs rather than silently launch another. The implementation uses verified SDK request fields for CPU `(4, 4)` and memory `(16384, 16384)`, no GPU or exposed ports. Record requested and observed resources separately. Sandbox lifetime timeout defaults to 1800 seconds and cannot exceed 7200; image preparation before creation is outside that bound, so it is not a whole-run billing cap; automatic retries are zero. `finally` requests termination and a provider TTL bounds the orphan risk if the local process crashes; termination failures stay visible and status/terminate permit recovery.
3. **Preserve dependency downloads only.** The final implementation scope permits a dedicated persistent Gradle cache volume, replacing the initial all-ephemeral-cache draft. Never mount source, output artifacts, authentication material or Minecraft worlds there. Every run receives an ephemeral workspace/world tree. Cache presence is an observed condition, not a guarantee that a run is fully warm.
4. **Keep the SDK optional and pinned.** Install Modal 1.6.1 in `.venv-modal`; ordinary mod/runtime work does not gain a Python dependency. The runner image provides Java 21, Python 3.12 and the headless graphics/input tools. `plan` is offline and allocates no Sandbox. `doctor` reports prerequisites without printing secrets. `run`, `status` and `terminate` perform only their explicit remote lifecycle actions.
5. **Presets state their evidence level.** Initial source tests and Java/build commands use bounded presets. Server and client execution reuse repository tooling; client is experimental until real remote mapped-input evidence passes. A successful plan, mocked API test or image definition is not a cloud execution result.
6. **Separate outcomes and phases.** Measure archive, Sandbox/image setup, upload, execution, artifact download and cleanup where observable, together with UTC boundaries, exit/timeout status, source/executor/dependency hashes and remote environment/cache facts. Fetch available diagnostics after a test failure before cleanup where possible; fetch failure must not suppress termination.

## Risks / Trade-offs

- Missing authentication → local planning/tests remain usable, cloud smoke is `not_verified`, and the change remains active.
- Local process death leaves remote execution → server-enforced bounded TTL plus recorded Sandbox id and explicit status/terminate recovery.
- Secret or user-file upload → fixed Git archive, pre-upload safety validation, two exact dependency exceptions and no ambient environment forwarding.
- Cache crosses test runs → dedicate it to Gradle dependencies only, disclose cache state, and isolate all world/source state.
- Software-rendered client evidence differs from a PC → no claims about owner GPU FPS, sound or final look/feel.
- Artifact collection fails after a test → retain local phase/error evidence, attempt termination, and never turn the run into PASS.

## Migration Plan

Add optional tooling and documented environment installation; keep existing local commands intact. Apply the release rule in `openspec/config.yaml` `rules.tasks` for 0.46.1. Validate local archive/lifecycle contracts before authenticated smoke. Authenticate in the user's Modal environment without copying credentials into the source archive. Run a small real cloud preset, inspect downloaded evidence and termination, then validate heavier presets independently. Do not archive before the required authenticated cloud evidence exists.

## Open Questions

Authenticated cloud startup, dependency-cache behavior, actual resource observations, artifact transfer and termination remain to be measured. Real remote client capture is a separate unverified surface until exercised.
