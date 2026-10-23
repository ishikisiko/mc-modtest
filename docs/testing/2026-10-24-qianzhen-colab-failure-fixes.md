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
