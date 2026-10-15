# Modal Test Runner (0.52.0 workflow)

`tools/modal_runner/` is the default controller for full release/build,
acceptance-server, real-client and two-client checks. This isolates heavy memory
use from the local host. It tests a fixed committed source tree and downloads
evidence before terminating its Sandbox. Local focused checks and owner gameplay
acceptance remain separate. A cloud failure is reported explicitly; it never
automatically starts local Gradle or Minecraft jobs.

## Entry points and inputs

Install the pinned SDK in a separate environment:

```bash
python3 -m venv .venv-modal
.venv-modal/bin/python -m pip install -r tools/modal_runner/requirements.txt
# First authentication, or renewal after the token expires:
.venv-modal/bin/modal token new --profile myvillage --expires-in 7d
.venv-modal/bin/python -m tools.modal_runner doctor
test_commit=$(git rev-parse HEAD)
.venv-modal/bin/python -m tools.modal_runner plan --preset release --ref "$test_commit" --timeout 3600
.venv-modal/bin/python -m tools.modal_runner run --preset release --ref "$test_commit" --timeout 3600
.venv-modal/bin/python -m tools.modal_runner run --preset alchemy-quality --ref "$test_commit" --timeout 3600
```

`plan` resolves the ref, creates the archive and request manifest locally, and
allocates no Sandbox. `doctor` checks the SDK/authentication environment without
printing credentials. Authentication remains in the user's Modal environment;
it is never a repository resource or uploaded test input. Follow the SDK's
interactive account authentication outside the source tree when required.
The example token expires after seven days; renew it when expired. Secrets stay
in the SDK environment, outside the repository and uploaded inputs.

The runner pins Modal 1.6.1. `--timeout` defaults to 1800 seconds and accepts
180 through 7200 seconds. It bounds the Sandbox lifetime after creation, not
image-build time before allocation or total billable work. Its resource request is `cpu=(4, 4)` and
`memory=(16384, 16384)`; these are requested SDK values, not measured remote
hardware. No GPU or public port is requested. The base image is
`eclipse-temurin:21-jdk-noble`, with Java 21, Python 3.12 and headless
Xvfb/input/software-rendering tools.

| Input | Provenance |
|---|---|
| `source.tar` | `git archive` of the resolved full commit; local tracked edits and generic untracked files are absent |
| Exact root `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` | Explicit dependency exception; frozen and hashed separately |
| Exact `exmod/mod_block_catalog.json` | Structure-generator catalogue required by the real build; frozen and hashed, never the whole `exmod` tree |
| `remote.py` | Current runner bootstrap, uploaded and hashed separately from the tested commit |
| `request.json` | Commit, selected preset, timeout, requested resources and input hashes |

`--ref HEAD` means the last commit, not the current dirty tree. Commit intended
changes before testing them. An older commit can use the current executor, but
the manifest must disclose both identities. The archive guard rejects common private paths before allocation; it is not
a general content-based secret scanner. Credentials/private configuration,
local worlds and unrelated files must not enter the upload. Dependency and
bootstrap files are frozen into the run directory before their hashes are
recorded, so later local edits cannot change that run's uploaded inputs.

## Presets and isolation

The `pass` rows below describe the **historical 0.46.1** runs, not validation of
the current commit. New presets remain pending until their authenticated runs,
artifacts and confirmed cleanup are recorded.

| Preset | Command / role | Real cloud status |
|---|---|---|
| `smoke` | Java version, Python dependency imports, Xvfb `glxinfo` | `pass` (authenticated second attempt; downloaded evidence and confirmed termination) |
| `python` | `python3 -m unittest discover -s tools/tests` | `pass` (1053 tests) |
| `build` | `./gradlew test build --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=4` | `pass` (913 tests; fresh 0.46.1 jar downloaded) |
| `release` | `python3 tools/release_gate.py` (complete release checks, fresh jar) | Current source: pending |
| `alchemy-quality` | `python3 tools/run_chunky_acceptance.py --stage 1`, then `python3 tools/alchemy_quality_evidence.py` | Current source: pending; includes mapped one-client brewing/use/restart/bilingual layouts and a two-client attribution check |
| `server` | `python3 tools/run_chunky_acceptance.py --stage 1` | `pass` (bounded stage-1 commands/save/stop; downloaded report and confirmed termination) |
| `client` | Sword-qi `tap_hit,charged_hit` mapped-input capture | `pass` for these two trials, videos/logs/manifest and confirmed termination; no DevBridge. Other client scenarios remain `not_verified`. |

Prefer running `release` and `alchemy-quality` against the **same exact full source commit**.
If only evidence scripts/tests or delivery docs change afterwards, record both
commits and an empty diff for `src/`, Gradle build inputs and generated-resource
sources before reusing the release result. Run the changed evidence/parser tests;
never reuse a jar across changed product/build inputs.
The latter starts a clean acceptance world, not the previous preset's world. Its
second client has an isolated game directory/username. Check fetched jar hashes,
source identity, report results and termination before claiming delivery.

For release generator-drift checks, the executor creates a synthetic local Git
baseline from the extracted archive before adding dependencies or outputs. This
is disclosed in the result and never replaces the original `request.commit`.

The app is `myvillage-tests`, with Sandbox name `myvillage-test-runner`.
`reports/modal-runs/runner.lock` serializes local launches. All source and worlds
live under the ephemeral `/workspace`; the dedicated volume
`myvillage-gradle-cache-v1` mounts at `/cache`, with `GRADLE_USER_HOME=/cache/gradle`.
Only dependency-cache state persists. A configured cache does not establish an
observed cache hit or a warm benchmark.

A run never automatically retries a failed test. Cleanup requests termination
on success, test failure, timeout, cancellation and download failure. The
provider timeout is the backstop if the local controller cannot run cleanup.
A failed termination request is recorded as unconfirmed; inspect/recover it
using the recorded run directory:

```bash
.venv-modal/bin/python -m tools.modal_runner status --run reports/modal-runs/<run-id>
.venv-modal/bin/python -m tools.modal_runner terminate --run reports/modal-runs/<run-id>
```

Do not treat controller exit, a request to terminate, and confirmed remote
termination as the same event. The terminated `sleep` entrypoint can report
`137` even when the remote test exited `0` and termination is confirmed; inspect
the job result and cleanup state separately.

## Evidence and limitations

Each local run directory contains its request/state, stdout/stderr logs and,
after successful collection, a verified artifact archive and extracted tree.
Artifacts include selected build outputs/test reports, repository reports,
previews and acceptance/capture logs; their archive is bounded at 2 GiB.
Paths and archive members are validated before extraction.

The state records creation (including image preparation), upload, remote job,
download and termination durations; remote evidence records individual command
results, environment, resource observations and cache configuration. Creation
can include image build work and must not be labelled test time. The tested
commit, executor/dependency hashes, requested resources and observed resource
facts accompany any comparison with the [local sword-qi measurements](../testing/2026-10-14-spiritual-power.md).

Local unit tests can prove archive selection and cleanup control flow without
paid compute. They cannot prove account authentication, image startup,
network/dependency downloads, actual resource allocation, real graphics, cloud
artifact transfer or confirmed provider cleanup. Historical 0.46.1 authenticated environment smoke passed, including artifact transfer
and confirmed termination; the historical build preset also passed 913 tests and produced a fetched jar. The Python preset passed 1053 tests, and the bounded stage-1 server report passed commands/save/stop. The client preset also passed the two named mapped-input trials with server cost/hit evidence and fetched videos. These historical results establish those bounded presets only: natural acquisition, persistence and multiplayer were not exercised remotely. Unobserved surfaces remain `not_verified`. Owner PC sound, FPS, look/feel and the existing sword-qi acceptance are independent.

Current release/quality tests, wider multiplayer, owner appearance/feel/balance
and any unobserved lifecycle surfaces remain pending or `not_verified` until
separate evidence is recorded. GPU graphics are not enabled: this image uses
software rendering, which does not establish owner PC FPS.

## See also

- [Modal runner implementation and verification record](../testing/2026-10-14-modal-test-runner.md)
- [modal-test-runner](../../openspec/specs/modal-test-runner/spec.md), [validation](../../openspec/specs/validation/spec.md), [combat-capture-tooling](../../openspec/specs/combat-capture-tooling/spec.md)
- [Alchemy](49_alchemy.md), [Crafting Aptitudes](50_crafting_aptitudes.md), [alchemy-quality](../../openspec/specs/alchemy-quality/spec.md)
- [34_combat_data_and_capture.md](34_combat_data_and_capture.md), [45_spiritual_power_sword_qi.md](45_spiritual_power_sword_qi.md), [INDEX.md](INDEX.md)

The `release` preset first runs focused alchemy/protocol Gradle tests, then the full release gate in the same Sandbox; this reuses compiled classes and catches Java errors before the full Python discovery run.
