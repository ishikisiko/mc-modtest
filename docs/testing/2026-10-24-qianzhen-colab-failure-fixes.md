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
