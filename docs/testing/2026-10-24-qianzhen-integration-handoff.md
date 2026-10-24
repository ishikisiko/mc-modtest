# 阡镇首轮 Colab 修复集成交接

本轮只在 `feature/qianzhen-story-generator` 合并与离线检查，不 push、不合 main，
不启动本机游戏、Gradle、浏览器或云端；最终验收交给独立代理新建 CPU High-RAM。

## 来源与证据边界

共同基线为 `29d4b4fd63ede4c4492046f892f85996ac634344`，三个原始修复提交均保留：

- `314d942029a904f9460348d6ccfa758528cfb4c1`：可移植路径夹具与一次部署、有界状态确认。
- `c559dd40ca39cebde439dbb5eb7c1fae58d92b11`：古阵 presentation/review metadata。
- `7ea504fce20ea3e6d3ba2247bf6e07e41f378d24`：只改变 seeded 旧驿几何。

首轮目录 `reports/colab-runs/qianzhen-29d4b4f-20261023T203843Z/` 保持原样。
首轮结论仍为失败：Python 157/158，第一章 0/10，调查 0/9。
首轮旧候选建筑 7/7 不覆盖新候选。禁止改写原报告，禁止把离线检查写成原生通过。

## 固定源、生成与等待

传输最终 feature tip 的完整 Git bundle；在新 Colab checkout 后核对 full SHA、
干净 tracked 状态和真实 High-RAM 分配。使用该固定提交重新生成候选、预览和请求，
不要复用合并前 generation-report/source/provenance/request。完整依赖、环境变量、
两套 `--plan` 和串行原生命令见 [主交接](qianzhen-seeded-colab-handoff.md)。
请求在 Colab 的实际 checkout 内创建；本机生成的 repo_root 不能冒充 Colab 路径。

部署默认单条 30 秒、总计 90 秒；只发送一次 deploy，超时后只重读状态。
成功必须同时满足持久 SavedData 的 schema/ID/版本/维度/原点/COMPLETE 和
四名唯一角色及位置，文字回复不能证明完成。报告中的 `deployment_budgets`、
`deployment.attempts/final_saved_state/final_story_state` 必须保留。
如需改预算，使用显式 `--deploy-timeout` 与 `--deploy-command-timeout`，
总计不超过 180 秒、单条不超过 60 秒；再次超时先留诊断，不能循环调大或重发部署。
根因与聚焦检查见 [部署修复记录](2026-10-24-qianzhen-colab-failure-fixes.md)。

## 必拍与复验清单

主 seed `20261024` 执行完整建筑与第一章采集。两个采集器串行，使用全新输出目录。
对照 seed `-42`、`17` 分别重新生成自己的六地点候选和请求，运行建筑采集器的
`--walk-locations old_relay`；该选项只缩小连续步行，放置、保存与画面仍覆盖六处。
不能把这两次局部运行称为第一章或全部地点步行通过。

- 古阵：原生 `spring_array/entry.png` 应从阵庭南岸新眼位拍完整结构；
  新增 `spring_array/maintenance_interior_uncut.png` 从工作台 approach 朝后方工作区。
  `evidence.json.day_frame_plan.spring_array` 列出相机脚部位置与焦点。
  旧 `spring_array/interior.png` 仍保留，是旧固定镜头，报告计划与页面已明确标注，
  **不得把它当成维护室新视角通过证据**。新补拍只证明画面，不能证明映射行走。
- 古阵离线：用同源 `capture.py` 拍 `cut`、`top`、`maintenance_interior`、
  `maintenance_plan_cut`、`underground_side_cut`。这些剖面不进入原生世界；
  `maintenance_interior.png` 为离线去顶画面，不能替代原生 `_uncut.png`。
  详细眼位、剖切含义与六 seed 方块不变基线见
  [古阵专项说明](../../tools/qianzhen/storygen/spring_array_review.md)。
- 旧驿：对 `20261024/-42/17` 按原 `exterior/rear/entry` 镜头正背对照，
  核对西楼后跨真实失盖、残墙、楼层，以及东仓失冠/折梁/侧倾三方案。
  额外保留东仓断架、`collapse_view`、侧口与墙根积沙的原生近景，逐图记录 seed、
  固定候选身份和实际眼位；不能移动原正背镜头替代同镜头比较。
  按原路线经过账房、讲台、前后平台与双梯，核对藏身室楼板、药箱实际点击、
  阵纹视线、三格侧口净空、沙稳定与断木支承。详见
  [旧驿专项说明](../../tools/qianzhen/storygen/old_relay.md)。

额外近景若由独立相机夹具采集，应单独命名并保留相机位置和原始图片，
不重命名既有截图、不修改剧情状态、不计作 mapped action。
全部实际结果继续按 `selected/completed/unrun/checks`、保存世界和原始录像核对；
第一章九个不同调查点须以真实 actions/章节记录逐项核对，缺项保持 unrun。
结束下载证据并确认游戏进程、锁与 High-RAM 会话均已释放；不删除首轮失败。

## 第四轮输入复核前的追加修复

以 `e0aa7ebd5e39a5add30e617f7d45054196051558` 为基线修复第三轮输入复核的
两项 P2，详见 [grab/派发门与完整 SIGINT 临界区](2026-10-24-qianzhen-colab-failure-fixes.md#round3-input-p2-final)。
182 项离线检查及真实轻量 Xvfb/信号反例通过；两套 plan 仍仅 ready_not_run。
新的 input_state grab 行包含 grabbed_after 和 phase=menu_sample/pre_dispatch，
边界协议为 x11-focus-events-grab-v2；初始 menu_confirmed 不能替代最终派发门。
Ctrl-C 从可能创建 worker 前到回收/关管道后全程延后递送，最多 .25 秒回收宽限
与无法 reap 明确 blocker 保持。复核者须独立复验真实反例，不能据通过数推断。

之后另一 fresh High-RAM 代理按包含修复的 feature 最终 full SHA 重新固定源，
完成第二玩家全部剩余分支/最新 revision、死亡/维度、主次客端重进与 saved_world。
本修复未启动游戏或云；原生项目仍 unrun，体验、音效、FPS、广泛多人 not_verified。

## 离线集成检查入口

部署/路径夹具、古阵六 seed 几何不变、旧驿八 seed/保护格故障注入、公共契约、
candidate 重放/缓存身份/混源拒绝、pipeline 逐格双路回读、默认 authored 七资源
字节基线，以及两套实际 `--plan` 是本次集成检查范围。
计划只能得到 `ready_not_run`、completed 为空；原生外观、碰撞、点击、手感、
音效、FPS 与多人在最终独立 Colab 观察前仍为 `not_verified`。
