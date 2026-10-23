# 阡镇 seeded 原生采集交接（执行说明，无运行结果）

此说明交给另一验收子代理。按用户指定新建 **Colab CPU high-mem**，不得改用
Modal 或本机 Minecraft。本实现只改工具、测试与说明，没有启动云端、游戏、
浏览器、Gradle 或构建 jar。工作流见 [WORKFLOW](../WORKFLOW.md)。

## 固定源与依赖

使用包含本适配提交的完整 Git checkout，实际目录例如 `/content/qianzhen`。
验收不能只 checkout 原生成器基线 `de8a8eba80d7348b33e3ad05f350446c4bb024c6`。
父代理可传输 `git bundle create /tmp/qianzhen-acceptance.bundle HEAD` 的 bundle，
验收代理在 Colab clone 后 `git checkout --detach <本适配完整提交SHA>`；不依赖推送。
验收期间不得改 tracked 文件；需要修复就保留失败、提交修复、重新固定请求。

Colab 安装 Java **21 JDK**（`build.gradle` 的 toolchain），以及现有原生采集依赖：

```bash
apt-get update
apt-get install -y openjdk-21-jdk-headless python3 python3-yaml python3-numpy python3-pil \
  git curl unzip xvfb xauth xdotool ffmpeg imagemagick fonts-dejavu-core mesa-utils \
  libgl1-mesa-dri libglx-mesa0 libopenal1 libxrandr2 libxinerama1 libxcursor1 libxi6 libxxf86vm1 procps
```

如所选 Colab 镜像没有该 JDK 软件包，验收代理需安装可信 Java 21，记录实际
`java -version` 与 JAVA_HOME；不要更改项目 Gradle/NeoForge 版本。Python 使用
`/usr/bin/python3`，确认能 import `yaml, numpy, PIL`。首次原生运行会经仓库
Gradle wrapper 下载 NeoForge/Minecraft 依赖；需要可用网络和足够磁盘。

```bash
cd /content/qianzhen
export MYVILLAGE_CLOUD_PROVIDER=Colab
export LIBGL_ALWAYS_SOFTWARE=1
export PYTHON=/usr/bin/python3
export MC_PREVIEW_PYTHON=/usr/bin/python3
export GRADLE_USER_HOME=/content/qianzhen-gradle-cache
export MC_HEAVY_LOCK=/content/qianzhen-heavy.lock
export PYTHONUNBUFFERED=1
java -version
/usr/bin/python3 -c 'import yaml, numpy, PIL'
git rev-parse HEAD
git status --short
```

这里运行的仍是仓库 `runAcceptanceServer` / `runClient` 开发 classpath，采集器
会自行启动它们；**不需要预先构建 jar**。默认 Gradle authored 资源保持原样，
seeded 的七个资源从高优先级世界数据包加载。若另行构建默认 jar，它仍是
基线 authored 资源，不能称为 seeded 分发 jar，也不能单靠该 jar hash 证明候选。

## 生成、固定请求与离线计划

在上述干净固定 checkout 内：

```bash
/usr/bin/python3 -m tools.qianzhen.storygen --seed 20261024 --out out/qianzhen-seeded/20261024
/usr/bin/python3 tools/qianzhen/build.py --source out/qianzhen-seeded/20261024/source --out out/preview/qianzhen-seeded-20261024 --source-id seeded-20261024
/usr/bin/python3 -m tools.qianzhen.evidence_candidate --candidate out/qianzhen-seeded/20261024 --provider Colab --request out/requests/seeded-20261024.json
/usr/bin/python3 tools/qianzhen/game_evidence.py --candidate out/qianzhen-seeded/20261024 --provider Colab --request out/requests/seeded-20261024.json --plan --out out/acceptance/seeded-20261024-buildings-plan
/usr/bin/python3 tools/qianzhen/story_evidence.py --candidate out/qianzhen-seeded/20261024 --provider Colab --request out/requests/seeded-20261024.json --plan --out out/acceptance/seeded-20261024-story-plan
```

请求固定 full commit、实际 checkout 路径、provider、root seed、scene version、
完整 generation-report SHA256、八份物化 source 和七份资源 hash/指纹。
另以 `export_source_sha256` 固定包含 README 与生成报告的完整导出来源，必须与
`export-provenance.json.source_inputs` 逐项相同；`source_sha256` 则须与生成报告的
`generation.materialized_inputs` 相同。聚合指纹统一使用生成器规范 JSON SHA-256，
不采信手填 `--source-id`。合并或修改生成器后须在最新干净固定提交重新生成候选，
不得沿用合并前 generation-report 的 generator 源码哈希。
首次创建候选对象、写入固定请求前，先拒绝 root seed、派生种子、复现命令、
算法/契约/seed 算法身份和 authored scene version 的内部矛盾，再用固定 checkout 的
`blueprints/qianzhen` 与报告 root seed 调用现有 `pipeline.generate`，真正重放全部
六地点到临时目录。逐字节比较八份物化 source、README、六份生产 NBT 与 scene JSON；
同时核对重放所得全部确定性 generation 字段（含完整源码/输入清单和地点语义）。
报告中的 Python 版本只作环境记录，报告文件本身仍以完整 SHA256 固定。
仅重导出候选 source 或相信报告自报 hash 均不能建立首次 seed 绑定。
同一对象后续验证仍重新读取并核对 authored、generator、报告、source 和资源 hash，
仅全部身份未变时复用已通过的重放结果；新进程/新候选对象重新重放，不使用持久化
“已验证”标签绕过首次校验。没有 `--candidate` 时仍采用 authored；seeded 请求
配默认 authored 或候选发生漂移都会拒绝。`--plan` 只验证来源和路线，
不会启动游戏、验证 Colab 身份或把任何阶段写入 completed。

第二轮审查修复的离线反例覆盖：只把两份报告 root seed 从 20261024 改为 17、
单改派生种子/复现命令/算法身份，以及同步改齐 seed 标签但保留原内容，均须在
请求写入前失败；七份资源逐项篡改并更新自报 hash 仍须失败。正确候选首次绑定
实际重放，未变对象后续校验不重复生成。这些是工具回归，原生阶段仍全部 unrun。

本轮离线回归命令（不启动游戏、云端或浏览器）：

```bash
python3 -m unittest tools.tests.test_qianzhen_evidence_candidate
python3 -m unittest \
  tools.tests.test_qianzhen_storygen \
  tools.tests.test_qianzhen_storygen_apothecary \
  tools.tests.test_qianzhen_storygen_old_relay \
  tools.tests.test_qianzhen_storygen_relay_inn \
  tools.tests.test_qianzhen_storygen_canal_gardens \
  tools.tests.test_qianzhen_storygen_spring_array \
  tools.tests.test_qianzhen_storygen_initiation_court \
  tools.tests.test_qianzhen_storygen_pipeline \
  tools.tests.test_qianzhen_build_capture \
  tools.tests.test_qianzhen \
  tools.tests.test_qianzhen_story_assets \
  tools.tests.test_qianzhen_story_evidence \
  tools.tests.test_qianzhen_runtime \
  tools.tests.test_qianzhen_package \
  tools.tests.test_qianzhen_story_resume
```

本轮上述两组分别通过 18 和 136 项 unittest（含预期拒绝 fixture），共 154 项。
另以 seed 20261024 实际运行两套 `--plan`，均为 `ready_not_run`，candidate 身份
完全一致、scene version 为 `1-56da335c3dd1262f`；建筑 selected/unrun=7、
第一章 selected/unrun=10，两个 completed 均为空。没有原生游戏运行结果。

## 串行原生采集

不要把两个采集器并发启动；它们使用相同隔离游戏目录。每次使用新的输出目录，
保留失败与各自 world ZIP（下一次新场景会清理本工具的旧 fixture 世界）。

```bash
/usr/bin/python3 tools/qianzhen/game_evidence.py --candidate out/qianzhen-seeded/20261024 --provider Colab --request out/requests/seeded-20261024.json --out out/acceptance/seeded-20261024-buildings-attempt-1
/usr/bin/python3 tools/qianzhen/story_evidence.py --candidate out/qianzhen-seeded/20261024 --provider Colab --request out/requests/seeded-20261024.json --out out/acceptance/seeded-20261024-story-attempt-1
```

采集器在 supervisor 持锁后、服务器启动前安装 `qianzhen_candidate` 数据包。
初次启动验证 pack 启用和每个文件 hash；保存/重启必须保留同一数据包，
缺失/篡改会拒绝，不能在重启时偷偷替换。第一章部署和每次重启还读回实际
`myvillage_qianzhen_scene.dat`，核对 COMPLETE、scene version、维度与原点。
Java 自身会核对六 NBT hash，并拒绝旧存档 scene version 不一致。

建筑采集使用候选快照规划路线，用同一候选 NBT 的原有别名放置，然后复用
结构方块 GUI SAVE/回读、真实右键开门、W/A/S/D、设施菜单与日夜拍摄。
第一章复用生产 deploy、人物物理对话、调查右键、连续路线、满包领奖、
救援/领奖重启、第二真实客户端的另一分支和死亡/维度 fixture。
不会用 RCON 传送替代连续行走或直接写章节进度。

审阅必须读取两个 `evidence.json` 的 `candidate`、`source_commit`、
`candidate_pack_hashes`、checks、selected/completed/unrun、视频、原始日志与
保存世界；**进程退出码和 status=pass 不能替代检查全部 checks.pass**。
第一章应有 `candidate_scene_version_*` 的实际保存记录。建筑是分离地块验证，
不能替代第一章整场景验证。旧 `package_game.py` 的 authored 组合规则未扩展，
请保留原始报告与 ZIP，不用它拼接旧 authored 证据形成 seeded 结论。

游戏关闭后确认 `python3 -m tools.combat_capture session status` 和实际记录进程
均已停止、共享锁已释放；保留 session 状态/日志中的终止证据。下载独立世界、
两次报告、录像、完整 `out/qianzhen-seeded/20261024/` 候选快照及 request 后，
按父任务要求处理 Colab VM
生命周期。本次实现代理没有创建/操作任何 Colab 会话。

## 必须保留的限制

- 本次仅离线工具测试，不构成任何 seeded 原生通过项；实际阶段均为 unrun。
- 原第一章两条路线是否合计覆盖九个不同调查点，以该次 actions/章节记录核对；
  采集器没有新增九点专属覆盖阶段，也不把未执行动作补成通过。
- 原采集器的管理员起点、开门/设施起点传送，以及死亡/维度 fixture 仍有明确
  用途；它们不证明自然探索、自然死亡或自然跨维度。
- 原 standalone client-only relog、恶意 C2S/nonce 重放、广泛多人、玩家审美、
  手感、声音、真实硬件 FPS 与长期平衡仍为 not_verified。
- 固定 authored `--resume-manifest` 不支持 seeded；必须从新 fixture 世界开始。
- 新增启动数据包在真实 Colab 的 pack 自动启用、Java 启动缓存与实机碰撞/输入
  仍待该验收代理观察；若失败保留证据修工具，不能关闭环境保护、伪造路径或
  通过改标签宣称 seeded 已加载。
