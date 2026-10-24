# 阡镇 seeded 原生采集与 fresh High-RAM 复验交接

此说明交给另一验收子代理。按用户指定新建 **Colab CPU high-mem**，不得改用
Modal 或本机 Minecraft。本实现只改工具、测试与说明，没有启动云端、游戏、
浏览器、Gradle 或构建 jar。工作流见 [WORKFLOW](../WORKFLOW.md)。

首轮 `29d4b4fd63ede4c4492046f892f85996ac634344` 验收失败，原证据完整保留在
`reports/colab-runs/qianzhen-29d4b4f-20261023T203843Z/`；不得覆盖或补记通过。
本次整合三个修复分支后的复验要求见
[首轮修复集成交接](2026-10-24-qianzhen-integration-handoff.md)。
第二轮 `abc3a8cf5146a2733b9599ea8c85dbfe8f86fe0c` 的第一章为 9/10，210 个已执行
检查通过、九调查 9/9，最终普通 10 秒 flush 超时，saved_world 仍 unrun。
第三轮使用包含保存修复的新固定提交和 fresh CPU High-RAM；保留两轮原始失败目录。
耗时证据、统一预算与 saved_world 新确认链见
[第二轮 flush 修复记录](2026-10-24-qianzhen-colab-failure-fixes.md#round2-flush)。

下文历史离线计数和 scene version 只属于适配时的固定源，不能作为最终候选身份。

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
  tools.tests.test_qianzhen_story_deployment \
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

适配阶段历史两组分别通过 18 和 136 项 unittest（含预期拒绝 fixture），共 154 项；
当时不含本次新增的部署等待套件，最终集成结果以首轮修复集成交接为准。
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

第一章部署默认总预算为 90 秒、单条 RCON 预算为 30 秒；可用
`--deploy-timeout`（最大 180 秒）和 `--deploy-command-timeout`（最大 60 秒，
且不得超过总预算）显式调整。连接、认证、分片读取共用单条绝对 deadline，
部署命令与后续只读状态确认共用总 deadline。部署只发一次；超时后只允许
在剩余预算内以新连接重读状态，不重放部署。最终同时核对持久 SavedData 的
COMPLETE、ID、版本、维度、原点和四名唯一角色的服务器状态；文字成功回复
不构成通过。`evidence.json.deployment` 保留预算、逐次命令耗时/超时和最终状态。
首轮失败根因、离线修复证据与 fresh High-RAM 复验注意见
[首轮失败修复记录](2026-10-24-qianzhen-colab-failure-fixes.md)。

第一章所有 flush 现经统一生命周期入口，`--flush-timeout` 默认 60 秒、硬上限 120 秒。
每个检查点只发送一次；连接/认证/响应和本次服务器日志确认共用 deadline。
普通查询仍保留短预算；超时不重发，也不以新连接成功补记。审阅 `flushes` 的
requests/status/elapsed_seconds/reply/server_log_artifact/saved_scene；只接受完整唯一
响应和新日志中的全维度保存完成记录，失败阶段必须保持 unrun。
最终 `saved_world_verification` 还须证明真实停服、主客户端和第二客户端重进，
场景版本、双方个人任务/奖励收据/背包、共享事实及人物身份一致，并核对最终 ZIP
中的两份 playerdata、scene/命簿数据及 hash。只见 ZIP 文件不算 saved_world 通过。
最终第二客户端日志在 `final-save-second-client/`，不能覆盖 `second-client/`。
第三轮不得沿用 210 的旧计数，应按新增实际检查核对完整 selected/completed/unrun。

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

- 本次保存修复仅做离线工具测试；新固定源的原生阶段需第三轮实际执行，不能补记旧失败。
- 原第一章两条路线是否合计覆盖九个不同调查点，以该次 actions/章节记录核对；
  采集器没有新增九点专属覆盖阶段，也不把未执行动作补成通过。
- 原采集器的管理员起点、开门/设施起点传送，以及死亡/维度 fixture 仍有明确
  用途；它们不证明自然探索、自然死亡或自然跨维度。
- 原 standalone client-only relog、恶意 C2S/nonce 重放、广泛多人、玩家审美、
  手感、声音、真实硬件 FPS 与长期平衡仍为 not_verified。
- 固定 authored `--resume-manifest` 不支持 seeded；必须从新 fixture 世界开始。
- 第二轮已观察其固定候选的数据包启用及原生输入；不能外推为新保存确认链通过。
  第三轮仍须核对自己的完整候选与 pack 身份；若失败保留证据修工具，不能关闭
  环境保护、伪造路径或通过改标签宣称 seeded 已加载。

## 第四轮输入/焦点交接

第三轮基线 `2fcc8f97` 第一章 7/10：第二玩家 `return_zhou_he` 已生效且对话已关，
重新抓取门失败；原证据不覆盖，最终 saved_world 仍未验收。新固定源包含第一章
目标窗口/PID/focus 校验、有界真实中键/一次 Escape 恢复，以及对应玩家的双向真实
鼠标移动 + Rotation 只读回执。默认 `--input-timeout 8`，有限正数且上限 30；
不能把增大预算作为唯一修复。原部署与单次 flush 规则保留。

离线回归补充 `tools.tests.test_qianzhen_story_input` 和
`tools.tests.test_qianzhen_story_lifecycle`；Modal story 的 discover 已包含新测试。
两套 plan 仅为 ready_not_run，不证明上述输入在软件渲染实机成立。
第四轮仍由另一代理使用 fresh CPU High-RAM、新候选/请求/世界/输出目录完成原流程。
须查看 actions 的 `input_state`（目标 display/window/PID/focus/grab、恢复次数与
耗时）、`input_rotation_readback` 和 guest-failure 截图/录像，逐步核对第二玩家新
应用快照；客端切回、死亡/维度、最终主/次客端重进同样必须核对。
底层触发原因的证据限度、恢复规则和 final-save 全链要求见
[第三轮故障记录](2026-10-24-qianzhen-colab-failure-fixes.md#round3-input)。

### 第三轮输入复核交接（原生第四轮仍未执行）

不要以 `0b2a30072dad9a52c19d0ff1f74e378550ecb475` 或
`ffec7187b64701c102853d69652d6df0854572ed` 开始验收。后者虽修复了 grab=True
状态合并及运行期 Xlib 阻塞，仍漏掉探测内部 42→99→42、释放 grab 后未重抓的
菜单假阳性，以及 Popen 构造返回前已创建子进程时的取消/回收边界。
必须固定包含本节新修复的 feature 提交，重新生成候选和请求，保留此前失败。

菜单与游戏门现在使用同一隔离 X11 连接的 `x11-focus-events-v1` 证据：
对真实目标窗口订阅 FocusChangeMask，XSync 并仅在边界开始前清除旧事件；随后
依次读 focus、探测 grab、读 focus、XSync 并收齐 FocusIn/FocusOut。
`input_state/event=grab` 保留 boundary、focused、focused_after、原始 grabbed 和
focus_events（type/window/mode/detail/synthetic）。前后均为 actor 窗口、边界内无
焦点事件才算 actor_focused；任何 FocusOut 均拒绝，保守地也拒绝 FocusIn。
因此 42→99→42 即使最终得到 (42,False,42) 也失败，不能记录 menu_confirmed
或继续发 left_mouse。真正稳定菜单仍要求 grab=False；游戏仍要求 grab=True
及同一玩家双向真实鼠标 Rotation 只读回执。事件只证明这个明确的采样边界，
不声称覆盖订阅前或采样完成后至点击间的持续焦点，也不把 left_mouse 发送记录
当成界面/剧情生效证据。点击结果继续由既有新页面/权威状态/最新 revision 核对。

父进程不再同步构造 Popen。Linux fork 期间屏蔽 SIGINT，取得 PID 并建立专用
进程组后才恢复信号；子进程自行 exec 新解释器、连接 actor display。
父进程不等待 exec 成功管道，启动卡住也受同一 select/绝对 work deadline 管理。
启动后取消、异常或超时均只 SIGKILL 本次 PID/进程组并 waitpid reap、关闭管道；
创建失败无 PID 时不发信号。work 阶段用 waitid(WNOWAIT) 观察退出，保留 PID
直到 finally 回收，避免收到取消时向已回收且可能复用的 PID 发信号。

默认输入逻辑预算仍 8 秒，上限 30；从剩余预算预留 min(.25 秒,20%) 给回收，
不接受迟到工作结果或回收后超过逻辑 deadline 的成功。取消或预算已耗尽时仍给
最多 .25 秒的独立回收宽限，不能 wait(timeout=0) 后遗留子进程，也不会无界等待。
若内核在该宽限内仍无法 reap，明确报 `Pointer cleanup blocker` 和 owned PID，
保留为失败/阻断，不能声称已回收或开始原生验收。此机制不承诺不可中断内核
fork/调度的硬实时上限。共享 Game.pointer_grabbed、默认 xdo 与炼丹/战斗 API 不改。

第三轮输入复核应重跑真实短预算进程与事件序列测试，并检查日志中的事件模型、
PID/组消失、已 reap、创建失败不误杀及回收宽限；轻量 Xvfb 测试只验证真实 X11
事件传输，不是 Minecraft 菜单通过。反例、完整命令与结果见
[第二轮审查故障补充](2026-10-24-qianzhen-colab-failure-fixes.md#round3-input-p2-deep)。
最终仍由另一 fresh CPU High-RAM 代理完成原生第四轮，特别检查第二玩家
return_zhou_he 后所有步骤、每步最新应用 revision、切回、死亡/维度、最终主/次
客端重进和 saved_world 完整链。部署/单次 flush 规则不变，不能跳过原生项或将
两个 ready_not_run 计划计为通过。

### 第四轮根因与第五轮输入交接

第四轮 `91f006316c2bdb4c82a08b30628301de3cb88aec` 仍在 `primary_start` 失败：
窗口/PID/focus/grab 采样符合，但第一条真实 `+12px` 没有 Rotation `+1.8°`。
保留 VM 上的受控对照确认根因是客户端 `MouseHandler.ignoreFirstMove` 丢弃
抓取后的第一条光标回调；`StoryInput.mouse_receipt` 已改为在测量前先发一次
真实 `+12px` 预热，再执行原双向回执。8 秒预算与 `.35°` 误差不变。详见
[第四轮输入根因](2026-10-24-qianzhen-colab-failure-fixes.md#round4-primary-input)。

下一轮必须另固定包含该修复的提交、重新生成候选/请求，在独立新世界核对
`input_state/mouse_prime`、两次 `mouse_input`、双向 `rotation` 与最终
`confirmed`；保留第四轮原始失败目录，不把前一轮未完成前缀拼成新候选通过。
第二玩家、切回、死亡/维度、最终保存和双客户端重进仍须完整执行。
