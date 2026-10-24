# 阡镇 Colab 首轮运行失败修复

固定基线：`29d4b4fd63ede4c4492046f892f85996ac634344`。
首轮证据根目录：
`/home/ubuntu/code/mc/mc-modtest/reports/colab-runs/qianzhen-29d4b4f-20261023T203843Z/`。
已核对验收总结、acceptance-result.json、证据索引及下列原始日志；保留原失败，
不更改首轮 157/158、第一章 0/10、调查 0/9 的结论。

## 路径夹具根因

`remote/acceptance/python-tests.log:170–176` 明确失败于
`test_provider_guard_uses_real_root_and_explicit_colab_marker`，
`root='/content/qianzhen', provider='Colab'` 未抛 RuntimeError。
测试将实际 ROOT 放进必拒绝元组，隐含了“运行测试的 checkout 在本机”的前提；
git archive 在 Colab 的合法 `/content/qianzhen` 解包后，该前提失效。
`assert_cloud` 的显式 marker 和解析后路径规则工作正确，候选解析没有失败证据。

修复将同一政策断言移入无生成前置的 StartupDispatchTests，使用明确的本机路径
与 Colab 路径：保留所有原拒绝情形的断言，并增加 `/content/qianzhen` 接受、
`/content` 本身及近似前缀拒绝。没有删除拒绝政策、放宽产品 guard 或跳过测试。

## 部署超时根因与证据限度

- `remote/acceptance/story-attempt-1.log:21–40`：setup 同步部署经
  Session.run → Rcon.cmd → socket.recv，在默认 10 秒读取预算超时，尚未进入
  原来的 90 秒角色状态轮询。
- `remote/server-game-logs/latest.log:296–302`：21:34:49.112 新部署连接开始，
  21:35:02.201 该连接关闭；同期服务器记录 13475ms / 269 ticks behind。
  部署窗口约 13.1 秒，服务端没有保持阻塞。
- 第一章 evidence.json 的最后一条 `save-all flush` 回复同时包含迟到的
  “阡镇已部署”及正常保存成功；不能只依据此文字判阶段通过。
- `remote/acceptance/post-failure-saved-scene-readback.json`：实际世界 SavedData
  为 COMPLETE / `1-56da335c3dd1262f` / overworld / `0 -61 0`。
- Java 部署路径同步完成六模板、道路、目标检查、chunk save 和持久 checkpoint
  后才返回，再确保剧情人物。日志支持大场景有界完成与采集预算不足；没有证据
  支持死锁或部署失败，也没有足够的逐步骤耗时数据把 13 秒归因于某个子步骤。
  本修复不宣称通用部署性能已达标，不修改运行时、资源或旧版本保护。

采集器新增独立部署等待：默认单条 30 秒（上限 60）、总计 90 秒（上限 180），
均须有限正数且单条不超过总计。连接/认证/每次分片读取共享单条绝对 deadline；
所有命令共享总 deadline。部署只发送一次；超时后的状态重读使用独立连接，
每次预算不超过剩余总时间。连接在认证失败或超时后也关闭；其他采集器仍用原默认值。

必须在 deadline 内收到唯一有效的 QIANZHEN_STORY_STATUS，核对全部角色的
有效唯一身份与位置，并只读检查持久 SavedData 的 COMPLETE、schema、ID、
版本、维度与原点。拒绝、损坏/不匹配状态立即失败；缺失或未完成状态只能继续
有界等待。超时后文字回复和旧版本存档均不能触发通过，也不写任务进度。
报告保存命令预算、耗时、timeout、最终读回和失败类型；失败保持阶段 unrun。

## 本地轻量验证与复验交接

仅运行离线 Python 测试与 CLI 检查，不启动本机/新云游戏，不构建 jar，不升版本。
聚焦测试命令：

```bash
python3 -m unittest tools.tests.test_qianzhen_story_deployment \
  tools.tests.test_qianzhen_evidence_candidate.StartupDispatchTests \
  tools.tests.test_qianzhen_story_evidence
python3 tools/qianzhen/story_evidence.py --help
git diff --check
```

实际结果：聚焦 37/37 通过（23.797 秒），CLI 帮助包含两个预算参数，
`git diff --check` 通过。另用 `--plan --deploy-timeout 120
--deploy-command-timeout 45` 实跑默认 authored 离线计划，结果为
`ready_not_run`，预算正确写入报告，completed 为空、unrun=10、
termination=not_started；临时输出已清理，没有启动游戏。

覆盖约 13 秒成功、部署/读回超时后确认、总预算耗尽、迟到回复、失败/未完成/错误
版本及原点、缺失人物、重复身份、setup 失败传播、分片读取 deadline、认证失败
关闭连接和新连接隔离；完整生成器套件与原生验收未在本次执行。

另一验收代理应在最新集成后的干净固定提交重新 git archive、生成候选与固定请求，
从 fresh CPU High-RAM 新世界按原交接执行。默认预算已覆盖首轮观测的约 13 秒；
若仍超时，保留 deployment 诊断、同一窗口服务器日志和失败世界，不自动反复
调高预算。若再见明显长阻塞，需要对该固定源追加部署各步骤耗时诊断，再判断
是存储/环境耗时还是产品性能问题。不能凭本次离线通过补记第一章或九调查完成。
两个视觉问题留给对应代理；本次没有改动其资源、生成器或镜头。

<a id="round2-flush"></a>

## 第二轮：flush 生命周期遗漏（本轮工具修复）

固定基线 `abc3a8cf5146a2733b9599ea8c85dbfe8f86fe0c`，第二轮原证据：
`reports/colab-runs/qianzhen-abc3a8c-round2-20261023T222648Z/`，保持原样。
已读验收总结、acceptance-result、两次 collector 日志、evidence.json 和原始服务器日志。
第二轮第一章仍为 9/10，210 个已执行检查通过，九调查 9/9；saved_world 为 unrun，
不能用 finally 导出的诊断 ZIP 补记完成。

### 实际根因及耗时判断

- `remote/acceptance/story-attempt-1.log` 失败于候选部署校验的普通 flush；
  `story-attempt-2.log` 失败于最终 flush，均经 Session.run → 默认 10 秒 RCON。
  314d942 只修了部署命令与只读状态确认，遗漏所有保存调用。
- `remote/server-game-logs/latest.log:45919–45928`：最终保存连接始于
  23:58:06.893；23:58:25.268 出现三个维度及 All dimensions are saved，连接关闭；
  服务器同时报告 18330ms behind。窗口约 18.375 秒，随后停服保存也完成。
  这是明确超过旧预算、但有界完成的保存窗口，无持续阻塞/死锁证据。
  缺少分步骤耗时，不能把它进一步定性为某项产品性能缺陷或承诺保存性能达标。
- 失败处理又发了一次 flush；evidence.json 最后一条回复为
  `Saved the game + Saving the game + Saved the game`，服务器也有两次完成标记。
  新连接成功不能证明前一条完成；共享 RCON 输出的迟到混杂必须拒绝。

### 统一预算与调用审计

`tools/qianzhen/story_lifecycle.py` 是第一章长命令规则、上限校验和绝对 deadline
传输的唯一来源。部署保留单条 30/最大 60 秒、总计 90/最大 180 秒，以及只发送
一次部署、剩余预算内只读确认。新增 `--flush-timeout` 默认 60、最大 120 秒，
有限正数；连接、认证、响应分片与日志/保存确认共享预算。普通查询仍用原短预算。
`StoryEvidence.rc` 统一识别长命令：flush 自动进入保存路径，deploy 必须经已有
部署确认路径；不允许普通 save-all 绕开 flush 语义。新增长操作须在该规则入口登记。

已逐处审计候选初始校验、fresh-entry/救援/领奖 restart、最终保存及异常处理。
每个保存检查点只发一次 flush，超时终止本轮并拒绝后续 flush；异常截图/cleanup
不再重发。重进后的候选版本读取不再为了读取而额外 flush。高频位置/状态、
维度 fixture 和普通查询不放宽。Session 的 stop 是短确认后已有的 60 秒退出等待，
外层 stop 仍有既有 180 秒边界；本轮未扩张共享启停预算、部署规则或玩家输入。

### saved_world 确认链

1. 本次独立连接收到唯一完整的保存开始/结束响应，且本次日志起点之后出现服务器
   All dimensions are saved；日志异步落盘仅在同一剩余预算内等，不重连、不重发。
   超时、重复/缺少响应、旧日志、日志截断/轮换均不能通过。记录逐次预算、请求数、
   耗时、响应、日志 byte offsets/原片段/SHA256、保存 scene 或失败类型。
2. 读取实际 SavedData，核对 COMPLETE/schema/ID/scene version/维度/原点；
   确认真实停服并打包含 playerdata 的检查点世界。
3. 最终检查点增加真实服务器/主客户端重进，同一数据包保留；核对磁盘 scene、
   个人章节/步骤/版本/调查/奖励收据、背包、共享事实、位置和四角色稳定身份。
   第二实际客户端再次登录，核对其独立进度/奖励/背包/共享事实及客户端快照。
   新日志使用 `final-save-second-client/`，不覆盖第一次第二客户端证据。
4. 再次确认停服，验证最终 ZIP CRC、level.dat、scene/命簿 SavedData 和两个
   UUID 对应的非空 playerdata；记录 ZIP SHA256 后才完成 saved_world。
   cleanup 只验证这个已确认包的 hash，不再覆盖它。任何状态不符仍失败并保持 unrun。

这只是采集器确认链修复，不修改 Java、候选身份、资源、几何、默认 authored 或
旧路径政策；不升版本、不打 jar、不启动任何本机/云游戏。古阵镜头问题与旧驿
失盖/断架/积沙的第二轮观察保留，旧驿偏规整及玩家审美仍为 not_verified。

### 本轮轻量检查与第三轮重点

相关回归命令：

```bash
python3 -m unittest \
  tools.tests.test_qianzhen_story_lifecycle \
  tools.tests.test_qianzhen_story_deployment \
  tools.tests.test_qianzhen_evidence_candidate.StartupDispatchTests \
  tools.tests.test_qianzhen_story_evidence \
  tools.tests.test_qianzhen_story_resume \
  tools.tests.test_combat_capture \
  tools.tests.test_alchemy_quality_evidence.QualityCaptureContractTest.test_second_client_cleanup_targets_owned_trees_only \
  tools.tests.test_alchemy_quality_evidence.QualityCaptureContractTest.test_second_client_reentry_uses_distinct_logs_without_overwriting_first
```

结果 **133/133**（18.726 秒）；最后请求数记录顺序微调后单独重跑 lifecycle
**12/12**（0.169 秒）。反例包括 18.4 秒单次成功、120 秒超时、超过上限的迟到
成功、旧/异步日志、混杂响应、错误磁盘 scene、真实重进检查链的步骤/调查/版本/
收据/背包/共享事实/人物身份不符，以及第二玩家重进收据丢失。超时错误处理不重发，
普通短查询预算、旧部署超时确认、云端路径夹具、authored 和 resume 断言保留。
CLI --help、四个受影响生产 Python 文件的 py_compile、git diff --check 通过。

以 seed 20261024 新生成候选（版本仍 `1-f795e60913419e05`），实际执行：

```bash
python3 -m tools.qianzhen.storygen --seed 20261024 --out out/qianzhen-flush-fix-local/seeded
python3 tools/qianzhen/game_evidence.py --candidate out/qianzhen-flush-fix-local/seeded --provider Colab --plan --out out/qianzhen-flush-fix-local/buildings-plan
python3 tools/qianzhen/story_evidence.py --candidate out/qianzhen-flush-fix-local/seeded --provider Colab --flush-timeout 60 --plan --out out/qianzhen-flush-fix-local/story-plan
```

两套均 ready_not_run，建筑 selected/unrun=7、第一章=10，completed 均为空；
第一章 termination=not_started，生命周期预算正确记录。没有伪造 Colab marker 或
启动游戏；离线 plan 不验证云身份。输出和日志仅在忽略目录，不提交生成物/reports。

顺带完整炼丹工具测试发现两项基线失败：
`test_persistence_sets_own_medicine_and_component_inventory_without_ui` 预期 3 条而
实际 14 条物品夹具；`test_pill_tooltips_keep_hover_and_never_send_use_or_click`
在 hotbar slot>8 时失败。用 `git show HEAD:tools/alchemy_quality_evidence.py`
加载未修改基线再次执行这两项，复现同样的 1 failure/1 error（0.009 秒），与本次
SecondClient 日志目录参数无关；保留日志，不改炼丹夹具、断言或产品内容求过。

第三轮 fresh High-RAM 应从本 feature 新提交重新固定源/候选/请求，在新世界串行
执行建筑与第一章。重点审查每个 flush requests=1、新日志窗口及 deadline；最终
final-save 的真实主客户端/第二客户端重进读回和 ZIP hash 必须齐全，才能报 10/10。
若失败保留当次失败、世界、逐命令耗时和服务器窗口，不能回写第二轮 9/10、把
诊断 ZIP 当通过或循环调高上限。按原任务完成下载核 hash 后确认游戏、锁、具名
High-RAM 会话均释放；本实现代理没有操作任何云会话。

补充组合检查也已完整结束：将上述 StartupDispatchTests 扩为完整
`tools.tests.test_qianzhen_evidence_candidate`，并包含完整
`tools.tests.test_alchemy_quality_evidence`，其余模块相同；当时采样共 167 项，
165 项通过、1 failure/1 error（870.061 秒），仅为上述两项已复现的炼丹基线失败。
完整候选身份 21 项均通过，包含首次真实 seed 重放、逐资源篡改、默认 authored、
候选缓存/请求与重启数据包身份。此补充组合不宣称全绿；本轮最终生命周期新增
反例以独立 12/12 和相关 133/133 为准。本地日志位于忽略目录
`reports/qianzhen-flush-fix-local/`，不改写两轮原始验收证据。

<a id="round3-input"></a>

## 第三轮：第二客户端界面关闭与鼠标抓取

基线 `2fcc8f9701b60aedfd8f26dcb7ad29b22e86f2e8`；原证据目录
`reports/colab-runs/qianzhen-2fcc8f9-round3-20261024T015358Z/` 保持原样。
已核对中文报告、acceptance-result、story-failure-triage、第一章 evidence/actions、
同窗口服务器及双客户端日志，以及 chapter-05 的 429.083 / 434.083 秒原始尾帧。
第三轮仍是第一章 7/10、177 个已执行检查通过、九调查 9/9；第二玩家完整分支、
死亡/维度、saved_world 均未完成。四次局部 flush 不覆盖 final-save 接受门。

### 可以确定的根因与不能倒推的细节

- actions 最后一次左键为 `return_zhou_he`（单调时钟 8355.5747），
  8355.9445 发送 Escape。客端 04:10:11.005 已应用 revision 8；
  434.083 与 439.083 秒录像为第一人称、无对话，目标已改为向陆衡说明发现；
  失败保全 playerdata 也是 warden/revision 8。选项输入生效、界面已关闭，
  阻塞发生在重新抓取门，不能诊断为选项没有提交或直接跳过抓取检查。
- 失败前已在第二客户端独立 Xvfb、独立 Game/display/client log 上连续行走、
  对话与调查；`as_guest` 在整个 yield 内保持 actor，失败调用没有中途切回主客端。
  没有源码或日志证据支持“轮询到了主客户端 Game”这一具体解释。
- 实际工具缺陷是 `close_screen` 将 display 未抓取同时解释为“屏幕仍打开”，
  只发一次 Escape，随后不断等待；没有目标窗口/focus 核验、没有真实重新抓取恢复。
  `Game.pointer_grabbed` 用 XGrabPointer 探测 display 全局占用，无法证明占用者
  是哪个窗口。且这是有副作用的主动探测；现有 `Game.type_chat` 已明确说明与
  GLFW 关屏重抓竞争时可能使抓取失败，必须先留安静帧并以中键恢复。
- 原采集器没有记录失败处的 X focus、窗口 PID 或每次 grab 结果时间。
  因此**触发这一次丢抓的底层原因（焦点丢失或主动探测竞争）不能由旧证据唯一确定**；
  探测竞争是代码支持的具体机制，不是已观测结论。也没有证据证明产品输入 bug、
  正常渲染只需多等几秒，或 8 秒预算本身不够。本次不改 Java 或视觉，不把未知写成确定。

### 最小系统性修复与输入证据

`tools/qianzhen/story_input.py` 将窗口核验及有界恢复限定在第一章采集器；
共享 `Game.xdo` 仅新增可选 timeout，其他使用者继续默认 20 秒；
`SecondClient` 新增可选 prepare_game 回调，仅第一章传入，在原有 45 秒启动等待前
核对并对焦第二窗口；未传回调的炼丹路径保持原行为。

- 每个 Game 绑定其 display 上唯一可见 Minecraft 窗口及 PID，后续漂移/多窗口
  拒绝；记录实际窗口标题、display、client_log、focus、grab 和单调耗时。
  使用 Xvfb 支持的真实 `windowfocus --sync`，不依赖窗口管理器的 EWMH 激活。
  对话坐标使用已绑定窗口的相对坐标，截图也绑定相同窗口。
- 默认总预算仍为 **8 秒**，新增 `--input-timeout`，有限正数、最大 30 秒。
  对焦/恢复后先留 .6 秒（Escape 后 .8 秒）不做主动抓取探测；先中键尝试恢复
  已关闭但未抓取的游戏，仍失败才发最多一次 Escape，之后最多再中键一次。
  所有等待、xdotool 子进程、Rotation 查询共用绝对 deadline；迟到结果拒绝。
  超时立即失败并记录，不重复选项、不写进度、不发送 RCON 旋转、不无限恢复。
- 全局 grabbed=true 仍不足以通过：在目标玩家上真实鼠标右移 12 像素，核对
  服务器只读 Rotation 的 yaw +1.8°（固定 sensitivity=.5/raw=false，误差 <.35°），
  再真实左移 12 像素并核对回到原 yaw，最后重新检查目标 focus/grab。
  这既证明输入落到对应玩家，也使画面朝向复原；不是 RCON 设置姿态。
  RCON 查询单条至多 3 秒且不超过剩余总预算，普通部署/保存预算不变。
- 主客端启动、每次 close_screen、客端切入/切回、死亡重生、维度往返和最终
  saved_world 的主/次客端重进使用同一门。重生保留已有 20 秒预算，其他输入门
  默认 8 秒；O 自身关闭测试禁止 Escape 兜底，仍开着就必须失败。
  打开行迹/对话还核对目标窗口焦点及释放的指针，不能拿失焦冒充菜单。
- 第二玩家每一步在真实动作前取其独立日志 offset，动作后等待最新应用快照与
  服务器的 revision/accepted/completed/chapter 完全一致。对话打开时的旧快照
  不能提前满足条件；使用原 12 秒快照预算，不扩张其他超时。
  第二玩家失败时在其进程销毁及 actor 切回前保存真实客端窗口截图。

本次保留单次 flush、原部署预算、candidate 首次 seed 重放、来源身份、两真实
客户端和完整第二玩家分支。没有修改资源/几何、版本或第三轮证据。

### 第四轮 fresh High-RAM 接受重点

另一独立代理应从本 feature 新提交重新固定源、候选和请求，新建 CPU High-RAM、
新世界、新输出目录；不能复用第三轮已执行前缀补记通过。

1. 首先核对 `input_budget` 与 actions 中每次 `input_state`：display/window/PID/
   focus/grab、有限恢复动作、对应玩家双向 Rotation 回执和 confirmed。
   特别检查 AlchemyGuest `return_zhou_he` 后、后续陆衡/古阵/领奖/终章及切回主客端。
   若仍失败，保留最后状态、guest-failure 截图、对应录像和双客户端日志，不能只调大上限。
2. 完成第二玩家另一分支、独立收据/背包/共享事实及逐步应用快照；再完成死亡、
   维度往返及行迹真实开关。开发者检查不等于玩家手感或广泛多人验收。
3. 真正执行 final-save：flush requests=1、新鲜独立响应与本次保存日志、持久
   scene、真实停服后主客端重进、第二客端再次登录和独立状态/背包/快照，再停服，
   两份非空 playerdata、ZIP CRC 与 SHA256 均确认后才标记 saved_world。
   第三轮尚未覆盖此链，2fcc8f97 的最终保存修复仍不能标记已验收。
4. 全部结果继续保留 selected/completed/unrun；下载核 hash 后确认游戏、锁及
   本次具名 High-RAM 会话释放。本实现轮未创建/启动/停止任何云会话或游戏。

### 本次离线结果

最终相关组合 **149/149**（16.059 秒）通过，含输入状态 **16/16**：已关/仍开、
错误焦点、错误窗口全局 grab、延迟恢复、探测安静窗口、超过 deadline 的 grab/
Rotation、输入次数上限、O 关闭不允许 Escape、probe 不可用/窗口身份漂移、
第二客端逐步快照、启动等待前真实对焦及切回/失败截图。组合还保留 story evidence/lifecycle/deployment/
resume、StartupDispatchTests、完整 combat_capture 以及 SecondClient 两个清理/重进
聚焦测试。命令如下，不运行游戏或构建：

```bash
python3 -m unittest \
  tools.tests.test_qianzhen_story_input \
  tools.tests.test_qianzhen_story_evidence \
  tools.tests.test_qianzhen_story_lifecycle \
  tools.tests.test_qianzhen_story_deployment \
  tools.tests.test_qianzhen_story_resume \
  tools.tests.test_combat_capture \
  tools.tests.test_qianzhen_evidence_candidate.StartupDispatchTests \
  tools.tests.test_alchemy_quality_evidence.QualityCaptureContractTest.test_second_client_cleanup_targets_owned_trees_only \
  tools.tests.test_alchemy_quality_evidence.QualityCaptureContractTest.test_second_client_reentry_uses_distinct_logs_without_overwriting_first
python3 -m tools.qianzhen.storygen --seed 20261024 --out out/qianzhen-input-fix-local/seeded
python3 tools/qianzhen/game_evidence.py --candidate out/qianzhen-input-fix-local/seeded --provider Colab --plan --out out/qianzhen-input-fix-local/buildings-plan
python3 tools/qianzhen/story_evidence.py --candidate out/qianzhen-input-fix-local/seeded --provider Colab --input-timeout 8 --flush-timeout 60 --plan --out out/qianzhen-input-fix-local/story-plan-final
```

两套 plan 均 `ready_not_run`，建筑 selected/unrun=7、第一章=10，completed 为空；
第一章 termination=not_started。两计划 candidate 身份完全一致，场景版本仍为
`1-f795e60913419e05`，七份资源 SHA256 均与第三轮主 seed 完全相同，资源总指纹
`a79336baeb10b4380cc4af0363ad51b624fb2f1719f2025aa66735bb6b7cf7a8`。
这是本实现工作树的离线结果，不是第四轮固定源原生验收；完整 candidate 反例套件
与完整生成器测试本轮未重复运行。四个生产工具 py_compile、CLI --help 和
`git diff --check` 通过。日志保存在忽略目录 `reports/qianzhen-input-fix-local/`，
生成物和 plan 保存在上述 out 目录；均不提交。
