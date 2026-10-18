# Modal Test Runner (0.52.1 workflow)

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
.venv-modal/bin/python -m tools.modal_runner plan --preset release-alchemy --ref "$test_commit" --timeout 3600
.venv-modal/bin/python -m tools.modal_runner run --preset release-alchemy --ref "$test_commit" --timeout 3600
# Focused diagnostic retry; not full delivery evidence:
.venv-modal/bin/python -m tools.modal_runner run --preset alchemy-quality --cases medicine --ref "$test_commit" --timeout 1800
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
Xvfb/input/software-rendering tools. OpenSpec and its Node/npm bootstrap have
been removed; image construction is covered by offline tests, not a new cloud
bootstrap result. Select checks using [WORKFLOW](../WORKFLOW.md); complete presets
are optional full-validation paths, not required for every change. OpenSpec and its Node/npm bootstrap have
been removed; image construction is covered by offline tests, not a new cloud
bootstrap result. Select checks using [WORKFLOW](../WORKFLOW.md); complete presets
are optional full-validation paths, not required for every change.

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
| `exploration-v2-build` | 第二版聚焦 combat／stats／wilderness／exploration／fate Java、资源与 jar；不自动运行全量 release | [第二版记录](../testing/2026-10-18-vanilla-exploration-v2.md)，实际结果待记录 |
| `exploration-v2-native` | build 后两次真实服务器 boot：末地、四首领原生方法／事件 fixture、门开关、有限箱子及同世界重启读回；无映射输入 | 同一第二版记录；fixture 不替代玩家验收 |
| `exploration-v2-runtime` / `exploration-v2-play` | runtime 同一固定源先 native 后映射；play 仅映射局部复验。`--cases progression,bosses,portal,dragon,end_chest,two_clients,restart` 选择范围 | 同一第二版记录；play 必须声明复用源和 jar 内容身份 |
| `exploration-build` / `exploration-native` / `exploration-runtime` | 探索配置／指南／炼器资源、聚焦 Java 和 jar；native 使用真实 V4／下界及重启；runtime 使用实际客户端交谈、箱子、合成、炼器、战斗与双人／重启 | [第一版逐项验证记录](../testing/2026-10-18-vanilla-exploration-v1.md) |
| `exploration-play` | 已有同产品 Java/native 证据后的采集器局部复验；重建 jar 和实际客户端场景，不重复 Java 选择 | 必须记录生产源与最终 jar 内容身份；同一专项记录 |
| `exploration-chest-restart` | 仅实际客户端箱子取物、真实服务器重启后重新开箱与背包保留；不布置城镇或重跑战斗／双人 | 复用已通过且产品输入相同的 Java/native 与向导证据；同一专项记录 |
| `npc-combat-build` / `npc-combat-runtime` | 相关战斗／命簿 Java 检查和 jar；runtime 支持独立 `--cases`，完整集合先运行这些检查，再以真实客户端验证 NPC 战斗与生命周期 | [运行、失败与结果](../testing/2026-10-18-npc-sword-combat.md) |
| `smoke` | Java version, Python dependency imports, Xvfb `glxinfo` | `pass` (authenticated second attempt; downloaded evidence and confirmed termination) |
| `advanced-arts-build` | 生成资源检查、武技/战斗/天命相关 Java 测试和 jar | [0.62.0 合并测试](../testing/2026-10-18-advanced-weapon-arts.md) |
| `advanced-arts-hud` | jar 与映射 Z/C 的快捷栏布局短探针 | 仅代表性 HUD 检查，不是完整玩法复验 |
| `advanced-arts-runtime` | committed-resource jar、六本书真实研读、Z/C 施放、拒绝/碰撞/友伤、守势、换武器、双客户端与重启 | 具体完成项和限制见同一专项记录；不启动 Chunky |
| `fates-purple-build` | Fate validator, focused fate/qi/medicine/forging Java tests, jar | [0.60.1 evidence](../testing/2026-10-17-purple-fates.md): 106 focused tests and actual NeoForge compilation passed |
| `fates-purple-runtime` | Committed-resource jar plus mapped purple-fate scenarios | Supports independent `--cases meridians,pill_aegis,tempered_bones,lifecycle`; same-source main cases plus lifecycle retry passed; isolated fixture explicitly enables PvP |
| `npc-life-build` | Life resource check, focused generator tests, 10 simulation Java classes plus furnace navigation regression and jar | [NPC life evidence](../testing/2026-10-17-npc-life.md): original feature commit compiled and 98 simulation tests passed; furnace regression added during runtime fixes |
| `npc-life-runtime` | Life resource check, furnace navigation regression, committed-resource jar, authored showcase, real NPC Goals and mapped conversations/lifecycle checks | Supports `--cases`; always includes registration/identity prerequisites. Per-run cases and limitations in [NPC life evidence](../testing/2026-10-17-npc-life.md) |
| `python` | `python3 -m unittest discover -s tools/tests` | `pass` (1053 tests) |
| `crops-build` | SpiritCropsTest and jar build only | Per-run result required |
| `worldplan-build` | Frozen V1/V2 resource checks, worldplan/region and direct simulation tests, jar only | Per-run result required; no game launch |
| `yunshan-buildings` | Expanded-building checks and authored exports, committed-resource jar, actual client placement/traversal/facility checks and mapped structure-block SAVE exports | Per-run artifacts and cleanup required; no owner-PC launch |
| `yunshan-district` | Full D1 city generation and tile exports, focused tests, committed-resource jar, real mapped roads/rotated entries/facilities and saved world ZIP; flat preview translation is explicit | [D1 city evidence and separate owner delivery](../testing/2026-10-18-yunshan-d1-city.md); software-rendered oblique view reviewed, vertical view limited by distance fog |
| `yunshan-natural` | Actual NORMAL-world Anvil survey, three distinct sites, protected-core terrain fitting, matched before/after client frames, mapped paths and separate natural-world ZIP | [Yunshan tooling](../../tools/yunshan/README.md); the run's evidence distinguishes candidate planning from actual placement |
| `yunshan-natural-site` | Re-survey and verify the authored refined site only, with actual placement, paths and complete ZIP | Reuses documented comparison evidence; reports `single_site_actual_survey`, not a repeated three-site search |
| `yunshan-auto-sites` | Bounded multi-seed NORMAL-world selection and explicit refusals; first accepted site is placed, walked and exported | [Automatic siting evidence](../testing/2026-10-18-yunshan-auto-sites.md); results in `reports/yunshan-auto-sites`, no runtime worldgen registration |
| `yunshan-flat-fallback` | Require a genuine primary refusal, then select/place/walk the flat variant on seed 20261002 | [Flat fallback evidence](../testing/2026-10-18-yunshan-flat-fallback.md); separate saved world, unchanged structure/hazard vetoes |
| `combat-ruins-build` | Combat ruin generation/path checks, ruins Java tests and committed-resource jar | Per-run result required |
| `combat-ruins-runtime` | Above checks plus natural worldgen, mapped combat/cache, multiplayer and restart evidence | Per-run result required |
| `danxia-build` | Ruin generator/path and affected tooling tests, ruins Java tests and jar build | Per-run result required |
| `danxia-runtime` | Generated-resource check, committed-resource jar, natural-world ruins and mapped interaction/restart evidence | Per-run result required |
| `forging-tags` | Jar plus disposable datapack aliases, mapped tag costs/infusion, reload and restart; Java tests use forging-build | Per-run result required |
| `forging-quality-preview` | Generated-resource check, jar and mapped UI captures of quality names, icon frames and tooltip borders across hotbar/inventory/chest | Per-run result required |
| `pill-quality-preview` | PillQualityTest, jar and mapped UI captures of pill frames, tooltip borders, stack counts and adjacent weapon regression | Per-run result required |
| `quality-picker-build` | Jar only using committed resources; no tests or game launch | Per-run result required |
| `forging-acquisition-preview` | Jar and settled staged material screenshots only; no gameplay verdict | Per-run artifacts required |
| `forging-acquisition` | Forging build, natural deposit readback, mapped mining and infusion, partial restart and two-client private batches | Per-run result required |
| `forging-ding` | Ding geometry/state/protocol tests, jar, mapped placement and pulse/restart visual evidence | Per-run result required |
| `forging-build` / `forging-runtime` | Forging assets and affected Java tests/jar; runtime additionally runs mapped learning, forging, repair, cost, two-player and persistence checks | Per-run result required |
| `spirit-bag-build` / `spirit-bag-runtime` / `spirit-bag-preview` | 灵石袋及消费端 Java 测试和 jar；runtime 做实际袋操作/拾取/修炼/炼器/重启/死亡，preview 局部复核材料提示 | [0.64.4：131 项 Java 和分场景实际客户端证据](../testing/2026-10-18-spirit-stone-bag.md) |
| `spirit-stone-build` | 灵石标签与混合库存回滚、修炼、炼丹、炼器、宗门供奉相关 Java 测试和 committed-resource jar；不启动游戏 | [0.64.3：106 项通过与交付证据](../testing/2026-10-18-spirit-stone-tags.md) |
| `alchemy-build` | Alchemy Java tests and jar build only | Per-run result required; no full game evidence |
| `fates` | FateFoundationTest and jar build only; no game/full release | Per-run result required |
| `jar` | `./gradlew jar` only, for resource/presentation-only delivery | Per-run result required; no tests/game |
| `build` | `./gradlew test build --no-daemon -Dorg.gradle.jvmargs=-Xmx2g --max-workers=4` | `pass` (913 tests; fresh 0.46.1 jar downloaded) |
| `release` | `python3 tools/release_gate.py` once (complete release checks, fresh jar) | See current workflow record |
| `release-alchemy` | release, clean stage1, all alchemy cases in one Sandbox/workspace | See current workflow record |
| `alchemy-quality` | clean stage1, then selected cases (`--cases`, default all) | Subsets are partial diagnostic evidence; see current workflow record |
| `server` | `python3 tools/run_chunky_acceptance.py --stage 1` | `pass` (bounded stage-1 commands/save/stop; downloaded report and confirmed termination) |
| `client` | Sword-qi `tap_hit,charged_hit` mapped-input capture | `pass` for these two trials, videos/logs/manifest and confirmed termination; no DevBridge. Other client scenarios remain `not_verified`. |

Prefer `release-alchemy`: one fixed source upload, full release once, then clean stage1
and complete game validation in the same Sandbox. This reuses that run's compiled
outputs without persisting them across runs. Separate `release` / `alchemy-quality`
commands remain available against the **same exact full source commit**.
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

- [云山四栋放大建筑实机证据](../testing/2026-10-17-yunshan-expanded-buildings.md)：模板放置、映射通行/交互、结构方块界面 SAVE 与原始 NBT 导出。
- [Modal runner implementation and verification record](../testing/2026-10-14-modal-test-runner.md)
- [modal-test-runner](../contracts/tooling.md#modal-test-runner), [validation](../contracts/tooling.md#validation), [combat-capture-tooling](../contracts/tooling.md#combat-capture-tooling)
- [Alchemy](49_alchemy.md), [Crafting Aptitudes](50_crafting_aptitudes.md), [alchemy-quality](../contracts/cultivation.md#alchemy-quality)
- [34_combat_data_and_capture.md](34_combat_data_and_capture.md), [45_spiritual_power_sword_qi.md](45_spiritual_power_sword_qi.md), [INDEX.md](INDEX.md)

The generic `release` preset has no feature-specific preliminary Java selection.
`alchemy-quality --cases medicine` runs only the selected game scenario after clean
stage1; accepted names are `brewing,multiplayer,medicine,persistence,ui`, or `all`.
Selection is validated before upload/allocation and recorded in the request and
exact remote command. `release-alchemy` rejects `--cases`. Cases own their fixtures;
persistence prepares its own mapped quality4 medicine use and component inventory.
UI and persistence share one restart when both selected. A case failure stops the
run, and the report explicitly distinguishes partial from full acceptance.

[Current workflow evidence](../testing/2026-10-16-acceptance-workflow.md) is the
single record for this tooling revision; historical rows above are not current passes.

0.52.3 changes the formula menu: old `alchemy-quality` / `release-alchemy` game
fixtures require migration before use. `alchemy-build` is the focused current
build path; prior captures remain historical.

`courtyard-build` 检查月洞水院生成资源、局部 Python/Java 几何测试和 jar；
`courtyard-runtime` 在此基础上运行 `tools/reference_courtyard/game_evidence.py`，
采集真实模板、映射通行/放置、截图、原生 SAVE、重启和独立世界。
实际结果见[专项说明](../features/reference-courtyard-minecraft.md)，不据 preset 存在推断成功。
