# 灵力与剑气：测试环境和耗时记录（2026-10-14）

状态：本轮自动测试、实际客户端采集和直接同世界重启已完成；用户的观感、声音和 PC 帧率验收仍未完成。
本报告保存进 Git；原始逐项 JSON、日志和采样曲线位于忽略目录
`reports/qi-sword-wave-tests/`。基础提交 `f061835`，目标版本 0.46.0。
日期采用亚洲/新加坡时间（UTC+8）；下面的命令表使用 UTC，因此本地 10 月 14 日凌晨对应 UTC 10 月 13 日晚上。

## 可用于规划的已完成测量

| 阶段 | 结果 | 命令墙钟时间 | 测量边界 |
|---|---|---:|---|
| 首轮 Java 完整测试和实用 jar 构建 | 912 tests 通过，构建通过 | 103.702 s | 已有依赖/项目缓存；含 Gradle 启动、构建和生成资源，不能称为纯测试时间 |
| 最终 Java 全套测试和构建 | 913 tests，0 failure/error/skip，构建通过 | 114.295 s | 含菜单取消和实体离开时客户端清理修复；已有缓存 |
| 受影响 Python 回归 | 384 tests 通过 | 139.306 s | unittest 自报 139.060 s；墙钟额外包含解释器和包装开销 |
| 修复后编译 | 通过 | 18.075 s | 依赖缓存已存在；此前失败的 19.619 s 另列 |
| 隔离 stage-1 验收服务器 | 通过 | 77.707 s | 启动/注册/有界关闭 smoke；不代表玩家完整操作 |
| 新 jar 内容检查 | 通过 | 0.071 s | 技能、功法、灵力规则、阶段 cap、双语和取消/HUD 类 |
| 新 jar 战斗校验 | 通过 | 6.177 s | 解除了早期旧 jar 的 stale 失败 |
| 版本一致性 | 通过 | 0.126 s | 发布元数据一致性 |
| 三种空技能意图的权限字段注入回归 | 通过 | 1.155 s | charge/release/cancel 中加入权威字段会被验证器拒绝 |
| 最终采集工具回归 | 97 tests 通过 | 1.414 s | unittest 自报 0.186 s；含缺失 reuse-world 快速失败和安全范围检查 |
| 最终客户端/观察者连续窗口 | 通过 | 143.254 s | 内部采集窗口 143.154 s；GUI取消、零灵力、中文HUD、重载、两人观察 |
| 直接同世界持久化重启和关闭 | 通过 | 79.317 s | restart 74.027 s，clean stop 5.003 s；独立管理夹具 |
| 最终 OpenSpec 变更 / 主规范 | 变更及 78 主规范通过 | 1.412 / 1.155 s | 最终文档/行为契约同步后校验；此前 1.104 s 记录保留在逐项表 |
| 首次采集服务端就绪 | 启动成功 | 32.1 s | 会话记录的 readiness；不是整个采集时长 |
| 首次采集客户端进入游戏 | 进入成功 | 45.3 s | client_ingame；其中 join 观测 33.3 s，与其他客户端阶段重叠，不可相加 |
| 首次采集完整会话启动 | 就绪 | 78.2 s | 服务端、虚拟显示器、客户端启动准备；后续玩法采集另计 |

这次没有清空依赖或系统页缓存，也没有做全新机器首次下载测试。
不能据此推断新环境冷启动耗时，或把所有命令时间直接相加当作项目经过时间。
首次 `focused-java-tests` 与 Python 校验有重叠，部分早期编译也与轻量检查同时发生；这些记录不是隔离性能基准。

## 主机与软件

| 项目 | 实测值 |
|---|---|
| 主机 | KVM Linux x86_64，内核 6.8.0-49，glibc 2.39 |
| CPU | 4 个可见 vCPU，AMD EPYC 9754；虚拟机显示 2 核 × 2 线程，不能按宿主型号推断独享 128 核 |
| 内存 | 7559 MiB；开始时 available 5498 MiB |
| Swap | 1987 MiB；开始时已用 1280 MiB，说明不是干净的全空闲内存基线 |
| 磁盘 | 工作目录所在文件系统 178 GiB，开始时可用约 80 GiB |
| Java / Gradle | Temurin 21.0.11+10 / wrapper 8.10.2，daemon=false |
| JVM 参数 | 仓库构建默认 `-Xmx2G`；实测完整 build 和采集 Gradle 使用 `-Dorg.gradle.jvmargs=-Xmx1g`；这不是总进程内存上限 |
| Python | 系统 `/usr/bin/python3` 3.12.3 跑回归；默认 Miniforge `python3` 3.13.13 跑部分工具 |
| Minecraft / NeoForge | 1.21.1 / 21.1.233 |
| 图形 | Xvfb + Mesa 25.2.8 llvmpipe（LLVM 20.1.2，软件渲染），OpenGL 4.5；不代表真实 GPU 帧率 |
| 音频 | OpenAL 设备打开失败；声音效果 `not_verified` |
| 已有缓存 | Gradle dependencies 1.6 GiB、wrapper 290 MiB、项目 `.gradle` 32 MiB、`build` 161 MiB |

环境快照采于 UTC `2026-10-13T19:11:07`；图形/音频来自本次客户端启动日志，
不混用历史追加日志中的旧会话。重任务共用 `/home/ubuntu/code/mc/.mc-heavy.lock`；
Minecraft 会话自身有服务端和客户端两进程组，锁防止另一个构建/采集任务并行挤占。

## 如何读时间与内存

- `wall_s` 是单条命令从启动到退出的墙钟时间；`lock_wait_s` 另计。本次已完成表中锁等待为零。会话 setup、readiness 和 trial 各自记录，不能混算。
- CPU user/sys 是工具记录到的等待子进程消耗；多核执行可大于墙钟时间。它不自动包含已脱离前台进程树的 Minecraft supervisor。
- GNU `time` 的 `max_rss_kib` 是观测到的最大单进程 RSS，**不是客户端＋服务端＋Gradle 的总峰值**；下表换算 MiB 展示。
- 前台工具的同 session RSS 采样会漏掉新 session 的 JVM。前两次 `capture-monitor` 也漏掉了 Gradle 子树，其约 400/320 MiB 结果无效，不能作为完整 Minecraft 峰值引用。
- 后续监视器改为递归 PPID 收集后代，再记录总 RSS。总 RSS 会重复计算共享页，0.5 s 采样也可能漏掉瞬时峰值；不等于物理内存独占量。修正后的第一轮采集实际峰值为 3405.9 MiB，墙钟 170.928 s；后续重启/双客户端另记。
- 已有 swap 和软件渲染都会影响结果。不要把这台主机上的一次成功当作最低内存配置保证；也没有据此给出购买建议。

## 失败、修复与重跑

| 尝试 | 时间 | 失败原因与处理 |
|---|---:|---|
| `catalogue-python` | 15.064 s | 新剑气诀在目录导入分类中被识别为 core；修正 active 分类后重新运行 |
| `catalogue-combat-core-python` | 17.671 s | 新 skill 检查增加了错误诊断，旧测试仍固定预期 2 条；修正预期后 105 项相关测试通过（16.905 s） |
| `initial-compile` | 19.619 s | HUD 跨包访问非 public 类型，且 Codec 无 `longRange`；修复 API/可见性后编译通过（18.075 s） |
| `gate-sword_combat_foundation` | 9.534 s | 新源码对旧 0.45.1 jar 的 freshness 检查报 `COMBAT_JAR_STALE`；最终新构建后重跑通过（6.177 s），失败尝试仍保留 |
| `focused-java-tests` | 64.594 s | 756 项中旧世界模拟传承 fixture 的固定种子不再出现预期事件；重新选取明确发生遗失/重燃的确定性 fixture，再在全套 912 项中通过 |
| `initial-minecraft-capture` | 190.473 s | 会话启动成功，但自然流程未先取得基础吐纳，研读未能进行，人工中断后补齐继承碑步骤；不是成功闭环 |
| `resume-minecraft-journey` | 3.710 s | 第一次继承碑真实点击未生效，采集主动失败；调整夹具并重新执行，不把失败时长藏入成功时间 |

补充失败：`final-zh-persistence-observer` 墙钟 **70.279 s**（退出码 1；
内部会话启动 70.026 s）在重启后读到空档案。根因是测试编排：capture 的服务器目录实际是
`run-acceptance`，保存后运行的 stage-1 默认 `--clean` 删除了整个目录，包括
`combat_capture_world`。`world_existed=false` 表明没有重启原世界；这不是产品存档丢失证据。
工具随后增加 reuse-world 世界缺失时立即失败及回归测试。重启证据已从新的、明确标注的最小管理夹具重新取得，
原自然获取闭环证据单独保留，不能声称二者是同一世界的连续旅程。

以上包含产品代码缺陷、测试 fixture 更新和采集准备错误三类；全部保留，
但不应把修复过程当作稳定 CI 每次都需要的固定成本。

## 运行证据边界

采集不使用 DevBridge；通过 Xvfb 上真实 Minecraft 的键鼠操作、RCON 夹具/只读血量查询、
服务端日志和录像互相核对。已观察到真实执事 JOIN、经架 BORROW、继承碑取得基础吐纳、
持有秘籍研读；本地 `03:33:28` 日志显示剑气诀学会，随后真实施放命中并观察到普通回复。
恢复后阶段实测：继承 3.834 s、研读 62.086 s（tick rate 80，加速实验，不代表正常学习等待）、
施放 10.699 s、回复观察 5.527 s。该流程起点是管理夹具准备的合格入宗者，
不是从全新凡人角色开始的整个成长过程。真实施放和第一轮 11 个边界试验已通过；英文 6 张 HUD 证据已采集，开发方已检查最小 GUI 布局和命中画面。
灵力不足试验在输入前的实测灵力是 1（低于基础花费 8），并非 0；中文 6 张 HUD 已补齐（12.882 s），与英文合计 12 张；开发方检查了中文 320 charge 和 427 idle，无截断或遮挡。
新版本 tap/fullcharge/menu-cancel 3/3 通过（27.163 s）。精确零灵力近战和闪避通过（11.027 s）：
操作前后灵力均为 0，目标 HP 93.07 → 86.77，位移 4.4803 格，服务端记录 DODGE started。
F3+T 自动等待完成 5.572 s；用户最终表现仍未验收。第二真实客户端观察 74.100 s 完成，其中加入 56.448 s、进入游戏 61.455 s（重叠阶段，不相加）。
两人在线，满蓄消耗 12，目标 HP 100 → 90.55；开发方抽查视频 2.6 s 帧观察到旁观端挥剑及弧形飞行剑气。
蓄力粒子被清靶死亡白粒子污染，清晰度仍为 `not_verified`，不能把整项表现都判通过。
本次连续采集窗口内部计时 143.154 s（外层命令墙钟 143.254 s），完整递归 PPID 峰值 5455948 KiB = 5328.1 MiB（约 5.20 GiB），
CPU 增量 412.84 s。系统 available 最低观测约 539 MiB、swap 满；主机开始时已有大量 swap，
不能全归因于此次采集，也不宜把一次通过当作稳定并发容量基线。新管理夹具直接同世界重启通过：重启阶段 74.027 s，正常停止 5.003 s，
外层命令总墙钟 79.317 s。离线 NBT 中灵力 17；登录后查询为 19/30，等待客户端就绪期间
已恢复灵力，因此不要求逐值相等。剑气诀、基础吐纳、身法和明心宗外门弟子身份保留。
`world_existed_before_start=true`，本次启动部分 73.4 s；完整递归进程峰值 3367512 KiB = 3288.6 MiB，
CPU 增量 187.15 s。已确认主客户端和服务器正常停止，重任务锁空闲。
重启夹具与原自然获取闭环属于不同世界，见上述编排失败说明。
声音、手感、用户电脑 FPS 和最终视觉接受仍是 `not_verified`。

开发证据服务在本次环境绑定 `10.3.0.5:8765`，因为 loopback `127.0.0.1:8765`
由既有 devbench console 占用，未停止它。公网边界页
`http://43.156.135.198:8765/sword_qi/boundaries/index.html` 已实测 HTTP 200；
这仅证明证据可访问，不等于 用户认可效果。[聚合证据页](http://43.156.135.198:8765/sword_qi/index.html) 也已实测 HTTP 200。

## 下一轮环境比较应补的测量

1. 将 capture 和 acceptance 的世界目录拆开，或给每个任务分配独立运行目录，再做同世界保存/重启校验。当前共享目录已导致一次可复现的测试编排失败；清理边界比单纯加内存更先需要明确。
2. 在保留本次暖缓存结果的同时，单独测新机器依赖下载、资源准备和第一次编译。不要清掉日常开发缓存后把等待网络时间误算成单元测试性能。
3. 分别记录单客户端和双客户端的启动、操作、关闭及递归进程树内存；不要仅看前台 Python 或最大单个 JVM。本轮双客户端窗口已记录：完整递归进程树 RSS 峰值 5328.1 MiB；仍需隔离重复测量才能判断稳定容量。
4. 在 用户 PC 上另测真实 GPU 帧率、声音和操作感受。llvmpipe/OpenAL 缺设备不能回答这些问题。
5. 做稳定环境对比时固定同一提交、测试选择、世界快照、tick rate 和语言，禁止后台构建重叠。自然获取闭环、管理夹具的边界测试、另建副本的保存/恢复测试分别报告。

这些是由本次测量缺口和失败得到的实验要求，不是硬件购买或最低配置结论。

## 已完成命令逐项记录

最后纳入的命令结束 UTC：`2026-10-13T19:50:39.528344+00:00`。记录 56 条已退出命令，包含准备、测试、构建、失败及重跑；不能全部计作纯测试。

| 标签 | UTC 开始 → 结束（2026-10-13） | 墙钟 s | 退出码 | CPU user/sys s | GNU 单进程峰值 MiB |
|---|---|---:|---:|---:|---:|
| capture-host-check | 19:10:48.616 → 19:10:48.876 | 0.261 | 0 | 0.09/0.01 | 21.7 |
| environment-snapshot | 19:11:07.067 → 19:11:10.117 | 3.050 | 0 | 0.06/0.27 | 36.1 |
| baseline-capture-tests | 19:11:30.962 → 19:11:31.297 | 0.335 | 0 | 0.19/0.07 | 27.9 |
| genops-frontdoor-initial | 19:14:24.063 → 19:14:24.238 | 0.175 | 0 | 0.12/0.04 | 16.2 |
| openspec-initial-strict | 19:14:41.264 → 19:14:42.367 | 1.103 | 0 | 0.38/0.08 | 91.6 |
| catalogue-python | 19:14:47.949 → 19:15:03.013 | 15.064 | 1 | 13.42/1.60 | 25.4 |
| sword-qi-capture-tests | 19:15:09.672 → 19:15:09.932 | 0.260 | 0 | 0.18/0.07 | 27.4 |
| sword-qi-capture-cost-tests | 19:16:02.615 → 19:16:02.873 | 0.258 | 0 | 0.17/0.07 | 26.6 |
| qi-runtime-core-validator | 19:16:29.394 → 19:16:29.516 | 0.122 | 0 | 0.07/0.01 | 17.2 |
| catalogue-combat-core-python | 19:16:46.254 → 19:17:03.926 | 17.671 | 1 | 15.29/2.14 | 26.1 |
| initial-compile | 19:17:09.330 → 19:17:28.949 | 19.619 | 1 | 35.07/2.18 | 667.0 |
| catalogue-combat-core-python-final | 19:17:59.759 → 19:18:16.664 | 16.905 | 0 | 14.78/2.06 | 25.5 |
| capture-journey-import-tests | 19:18:23.368 → 19:18:23.645 | 0.277 | 0 | 0.19/0.08 | 27.6 |
| openspec-synced-strict | 19:18:26.266 → 19:18:27.421 | 1.156 | 0 | 0.39/0.10 | 91.7 |
| compile-retry-1 | 19:18:28.789 → 19:18:46.865 | 18.075 | 0 | 51.04/2.81 | 634.6 |
| catalogue-school-reference | 19:18:38.142 → 19:18:38.334 | 0.192 | 0 | 0.10/0.03 | 18.4 |
| catalogue-generation-check | 19:18:43.257 → 19:18:43.384 | 0.127 | 0 | 0.09/0.01 | 16.7 |
| openspec-baseline-all-strict | 19:18:53.745 → 19:18:54.849 | 1.104 | 0 | 0.45/0.09 | 104.3 |
| focused-java-tests | 19:19:12.854 → 19:20:17.448 | 64.594 | 1 | 126.54/4.40 | 626.9 |
| gate-cultivation_core | 19:19:50.251 → 19:19:50.428 | 0.176 | 0 | 0.11/0.01 | 17.2 |
| gate-cultivation_initiation | 19:19:50.482 → 19:19:50.969 | 0.488 | 0 | 0.38/0.07 | 19.8 |
| gate-spirit_stone_resources | 19:19:51.033 → 19:19:51.536 | 0.504 | 0 | 0.25/0.16 | 19.0 |
| gate-cultivation_lifespan | 19:19:51.596 → 19:19:51.722 | 0.126 | 0 | 0.07/0.02 | 17.0 |
| gate-cultivation_meditation | 19:19:51.773 → 19:19:51.900 | 0.128 | 0 | 0.08/0.01 | 17.3 |
| gate-cultivation_gain | 19:19:51.959 → 19:19:52.137 | 0.178 | 0 | 0.10/0.02 | 18.4 |
| gate-cultivation_advancement | 19:19:52.196 → 19:19:52.321 | 0.126 | 0 | 0.07/0.01 | 17.7 |
| gate-world_sim | 19:19:52.385 → 19:19:52.823 | 0.438 | 0 | 0.35/0.03 | 22.3 |
| gate-sword_combat_foundation | 19:19:52.881 → 19:20:02.414 | 9.534 | 1 | 8.27/0.18 | 30.9 |
| gate-mod_items | 19:20:02.460 → 19:20:02.586 | 0.125 | 0 | 0.06/0.01 | 15.5 |
| gate-custom_entities | 19:20:02.645 → 19:20:02.908 | 0.263 | 0 | 0.19/0.03 | 24.1 |
| gate-rideable_flying_sword | 19:20:02.965 → 19:20:03.040 | 0.075 | 0 | 0.02/0.02 | 13.0 |
| gate-guideme_cultivation_guide | 19:20:03.095 → 19:20:03.320 | 0.225 | 0 | 0.15/0.02 | 21.2 |
| capture-reuse-world-tests | 19:20:08.681 → 19:20:08.969 | 0.288 | 0 | 0.21/0.08 | 28.8 |
| affected-python-validator-suite | 19:20:31.951 → 19:22:51.258 | 139.306 | 0 | 119.74/18.80 | 40.3 |
| capture-final-unit-tests | 19:20:56.432 → 19:20:56.692 | 0.261 | 0 | 0.17/0.10 | 27.0 |
| manual-textures-check | 19:21:31.757 → 19:21:31.799 | 0.043 | 0 | 0.02/0.01 | 13.4 |
| heritage-fixture-seed-scan | 19:22:57.099 → 19:22:59.321 | 2.222 | 0 | 5.59/0.33 | 159.4 |
| full-tests-build | 19:23:39.669 → 19:25:23.371 | 103.702 | 0 | 192.07/6.46 | 793.4 |
| initial-minecraft-capture | 19:26:46.100 → 19:29:56.573 | 190.473 | 254 | 11.14/3.22 | 33.9 |
| resume-minecraft-journey | 19:31:31.425 → 19:31:35.136 | 3.710 | 1 | 0.44/0.14 | 29.0 |
| journey-prerequisite-retry | 19:32:27.889 → 19:35:18.817 | 170.928 | 0 | 27.26/5.97 | 130.1 |
| genops-final-scope-snapshot | 19:35:01.651 → 19:35:02.385 | 0.734 | 0 | 0.32/0.28 | 15.1 |
| genops-final-frontdoor | 19:35:07.035 → 19:35:07.436 | 0.400 | 0 | 0.29/0.05 | 16.0 |
| zero-qi-regression-save-stop | 19:36:12.992 → 19:36:34.627 | 21.635 | 0 | 0.38/0.28 | 23.4 |
| final-tests-build | 19:37:48.088 → 19:39:42.383 | 114.295 | 0 | 196.92/7.08 | 795.0 |
| acceptance-stage1 | 19:39:54.062 → 19:41:11.769 | 77.707 | 0 | 149.51/8.58 | 1201.4 |
| fresh-jar-inspection | 19:41:28.121 → 19:41:28.193 | 0.071 | 0 | 0.04/0.01 | 21.6 |
| sword-validator-fresh-jar | 19:41:28.284 → 19:41:34.461 | 6.177 | 0 | 5.97/0.14 | 30.9 |
| version-consistency | 19:41:36.840 → 19:41:36.966 | 0.126 | 0 | 0.06/0.02 | 22.0 |
| final-cancel-authority-test | 19:41:43.205 → 19:41:44.360 | 1.155 | 0 | 0.77/0.26 | 26.5 |
| final-zh-persistence-observer | 19:42:15.354 → 19:43:25.633 | 70.279 | 1 | 1.01/0.82 | 23.1 |
| final-new-fixture-client-observer | 19:45:05.409 → 19:47:28.662 | 143.254 | 0 | 124.28/11.63 | 1197.5 |
| direct-persistence-restart | 19:47:51.471 → 19:49:10.788 | 79.317 | 0 | 1.07/0.87 | 22.1 |
| final-capture-python-tests | 19:49:46.937 → 19:49:48.350 | 1.414 | 0 | 0.27/0.09 | 29.0 |
| final-openspec-change-strict | 19:50:36.917 → 19:50:38.330 | 1.412 | 0 | 0.42/0.12 | 92.8 |
| final-openspec-baseline-strict | 19:50:38.373 → 19:50:39.528 | 1.155 | 0 | 0.46/0.10 | 105.8 |

<details>
<summary>对应命令和逐项缓存说明</summary>

**capture-host-check**

```bash
python3 -m tools.combat_capture check
```

缓存/运行条件：existing checkout and dependency caches; lightweight check, no JVM started

**environment-snapshot**

```bash
python3 reports/qi-sword-wave-tests/environment.py
```

缓存/运行条件：existing dependency/project/build caches; no cold benchmark

**baseline-capture-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_combat_capture
```

缓存/运行条件：existing checkout, Python module caches may be warm; no JVM

**genops-frontdoor-initial**

```bash
/usr/bin/python3 tools/genops/check_frontdoor.py --run-id 20261014-spiritual-power-sword-qi-impl --json
```

缓存/运行条件：existing Python bytecode; initial in-progress attribution snapshot

**openspec-initial-strict**

```bash
openspec validate add-spiritual-power-sword-qi --strict
```

缓存/运行条件：initial validation; no cache clearing

**catalogue-python**

```bash
/usr/bin/python3 -m unittest tools.tests.test_gen_technique_catalogue tools.tests.test_validate_world_sim
```

缓存/运行条件：incremental

**sword-qi-capture-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_sword_qi_capture tools.tests.test_combat_capture
```

缓存/运行条件：Python modules cached; new capture source/tests

**sword-qi-capture-cost-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_sword_qi_capture tools.tests.test_combat_capture
```

缓存/运行条件：incremental capture source change

**qi-runtime-core-validator**

```bash
/usr/bin/python3 tools/validate_cultivation_core.py
```

缓存/运行条件：incremental

**catalogue-combat-core-python**

```bash
/usr/bin/python3 -m unittest tools.tests.test_gen_technique_catalogue tools.tests.test_validate_world_sim tools.tests.test_validate_cultivation_core tools.tests.test_combat_data
```

缓存/运行条件：incremental

**initial-compile**

```bash
./gradlew compileJava compileTestJava --no-daemon -Dorg.gradle.jvmargs=-Xmx1g -x generateAllStructures
```

缓存/运行条件：existing dependency caches; incremental source compile

**catalogue-combat-core-python-final**

```bash
/usr/bin/python3 -m unittest tools.tests.test_gen_technique_catalogue tools.tests.test_validate_world_sim tools.tests.test_validate_cultivation_core tools.tests.test_combat_data
```

缓存/运行条件：incremental

**capture-journey-import-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_sword_qi_capture tools.tests.test_combat_capture
```

缓存/运行条件：incremental capture implementation

**openspec-synced-strict**

```bash
openspec validate add-spiritual-power-sword-qi --strict
```

缓存/运行条件：warm CLI; source specs synchronized

**compile-retry-1**

```bash
./gradlew compileJava compileTestJava --no-daemon -Dorg.gradle.jvmargs=-Xmx1g -x generateAllStructures
```

缓存/运行条件：warm dependency cache; retry after API fixes

**catalogue-school-reference**

```bash
/usr/bin/python3 -m unittest tools.tests.test_gen_technique_catalogue.RowEffectsTest.test_active_school_must_match_skill_school
```

缓存/运行条件：incremental

**catalogue-generation-check**

```bash
/usr/bin/python3 tools/gen_technique_catalogue.py --check
```

缓存/运行条件：incremental

**openspec-baseline-all-strict**

```bash
openspec validate --specs --strict --no-interactive
```

缓存/运行条件：warm CLI; baseline specs synchronized

**focused-java-tests**

```bash
./gradlew test --tests 'com.example.myvillage.combat.*' --tests 'com.example.myvillage.cultivation.*' --tests 'com.example.myvillage.client.combat.*' --tests 'com.example.myvillage.client.cultivation.*' --tests 'com.example.myvillage.sim.*' --tests 'com.example.myvillage.item.*' --no-daemon -Dorg.gradle.jvmargs=-Xmx1g -x generateAllStructures
```

缓存/运行条件：warm dependencies; compiled changed sources

**gate-cultivation_core**

```bash
/usr/bin/python3 tools/validate_cultivation_core.py
```

缓存/运行条件：incremental

**gate-cultivation_initiation**

```bash
/usr/bin/python3 tools/validate_cultivation_initiation.py
```

缓存/运行条件：incremental

**gate-spirit_stone_resources**

```bash
/usr/bin/python3 tools/validate_spirit_stone_resources.py
```

缓存/运行条件：incremental

**gate-cultivation_lifespan**

```bash
/usr/bin/python3 tools/validate_cultivation_lifespan.py
```

缓存/运行条件：incremental

**gate-cultivation_meditation**

```bash
/usr/bin/python3 tools/validate_cultivation_meditation.py
```

缓存/运行条件：incremental

**gate-cultivation_gain**

```bash
/usr/bin/python3 tools/validate_cultivation_gain.py
```

缓存/运行条件：incremental

**gate-cultivation_advancement**

```bash
/usr/bin/python3 tools/validate_cultivation_advancement.py
```

缓存/运行条件：incremental

**gate-world_sim**

```bash
/usr/bin/python3 tools/validate_world_sim.py
```

缓存/运行条件：incremental

**gate-sword_combat_foundation**

```bash
/usr/bin/python3 tools/validate_sword_combat_foundation.py
```

缓存/运行条件：incremental

**gate-mod_items**

```bash
/usr/bin/python3 tools/validate_mod_items.py
```

缓存/运行条件：incremental

**gate-custom_entities**

```bash
/usr/bin/python3 tools/validate_custom_entities.py
```

缓存/运行条件：incremental

**gate-rideable_flying_sword**

```bash
/usr/bin/python3 tools/validate_rideable_flying_sword.py
```

缓存/运行条件：incremental

**gate-guideme_cultivation_guide**

```bash
/usr/bin/python3 tools/validate_guideme_cultivation_guide.py
```

缓存/运行条件：incremental

**capture-reuse-world-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_sword_qi_capture tools.tests.test_combat_capture
```

缓存/运行条件：incremental capture source and tests

**affected-python-validator-suite**

```bash
/usr/bin/python3 -m unittest tools.tests.test_validate_cultivation_core tools.tests.test_validate_cultivation_initiation tools.tests.test_validate_spirit_stone_resources tools.tests.test_validate_cultivation_lifespan tools.tests.test_validate_cultivation_meditation tools.tests.test_validate_cultivation_gain tools.tests.test_validate_cultivation_advancement tools.tests.test_validate_world_sim tools.tests.test_validate_sword_combat_foundation tools.tests.test_validate_mod_items tools.tests.test_validate_custom_entities tools.tests.test_validate_guideme_cultivation_guide tools.tests.test_gen_technique_catalogue tools.tests.test_gen_manual_textures tools.tests.test_combat_data tools.tests.test_world_sim_report
```

缓存/运行条件：incremental

**capture-final-unit-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_sword_qi_capture tools.tests.test_combat_capture
```

缓存/运行条件：incremental range/same-weapon slot scenario additions

**manual-textures-check**

```bash
/usr/bin/python3 tools/gen_manual_textures.py --check
```

缓存/运行条件：incremental

**heritage-fixture-seed-scan**

```bash
/usr/bin/python3 reports/qi-sword-wave-tests/heritage_seed_probe.py
```

缓存/运行条件：incremental

**full-tests-build**

```bash
./gradlew test build --no-daemon -Dorg.gradle.jvmargs=-Xmx1g
```

缓存/运行条件：warm dependencies; full test selection and practical build after fixes

**initial-minecraft-capture**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label initial-minecraft-capture -- python3 reports/qi-sword-wave-tests/run_initial_capture.py
```

缓存/运行条件：dependencies and full test/build cached; tiny latest client edit recompiles; new isolated world

**resume-minecraft-journey**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label resume-minecraft-journey -- python3 reports/qi-sword-wave-tests/resume_journey.py
```

缓存/运行条件：same live JVMs; repaired prerequisite fixture and chronicle assertion; no restart

**journey-prerequisite-retry**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label journey-prerequisite-retry -- python3 reports/qi-sword-wave-tests/resume_journey.py
```

缓存/运行条件：live JVMs; stele placed after loading chunk; full descendant memory monitoring fixed

**genops-final-scope-snapshot**

```bash
/usr/bin/python3 reports/agent_runs/20261014-spiritual-power-sword-qi-impl/snapshot_provenance.py
```

缓存/运行条件：existing Python bytecode and current implementation worktree

**genops-final-frontdoor**

```bash
/usr/bin/python3 tools/genops/check_frontdoor.py --run-id 20261014-spiritual-power-sword-qi-impl --json
```

缓存/运行条件：existing Python bytecode; attribution snapshot refreshed

**zero-qi-regression-save-stop**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label zero-qi-regression-save-stop -- python3 reports/qi-sword-wave-tests/zero_and_save_stop.py
```

缓存/运行条件：live JVMs; no rebuild; persist existing capture world for reuse

**final-tests-build**

```bash
./gradlew test build --no-daemon -Dorg.gradle.jvmargs=-Xmx1g
```

缓存/运行条件：warm dependency and build caches; full suite after charging cancellation fix; no concurrent Minecraft jobs

**acceptance-stage1**

```bash
/usr/bin/python3 tools/run_chunky_acceptance.py --stage 1 --server-ready-timeout 180 --chunky-timeout 120 --stop-timeout 60
```

缓存/运行条件：warm dependencies; isolated acceptance world; no concurrent Gradle or capture session

**fresh-jar-inspection**

```bash
/usr/bin/python3 reports/qi-sword-wave-tests/inspect_jar.py
```

缓存/运行条件：fresh final jar; no Minecraft job running

**sword-validator-fresh-jar**

```bash
/usr/bin/python3 tools/validate_sword_combat_foundation.py
```

缓存/运行条件：fresh final jar after full build

**version-consistency**

```bash
/usr/bin/python3 tools/release_gate.py --only version-consistency
```

缓存/运行条件：final release metadata

**final-cancel-authority-test**

```bash
/usr/bin/python3 -m unittest tools.tests.test_validate_sword_combat_foundation.SwordCombatFoundationValidatorTest.test_charge_release_and_cancel_remain_empty_intents
```

缓存/运行条件：warm Python bytecode; isolated after Minecraft stop

**final-zh-persistence-observer**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label final-zh-persistence-observer -- python3 reports/qi-sword-wave-tests/restart_chinese_observer.py
```

缓存/运行条件：fresh final jar/full Gradle tests and stage1 cached; reuse captured world; latest cancel intent protocol17

**final-new-fixture-client-observer**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label final-new-fixture-client-observer -- python3 reports/qi-sword-wave-tests/resume_final_new_fixture.py
```

缓存/运行条件：same live final-protocol JVMs; minimal admin lifecycle fixture after acceptance clean; previous natural journey retained separately

**direct-persistence-restart**

```bash
python3 reports/qi-sword-wave-tests/capture_monitor.py --label direct-persistence-restart -- python3 reports/qi-sword-wave-tests/direct_persistence_restart.py
```

缓存/运行条件：same saved final world; cached Gradle dependencies; no intervening stage1 clean

**final-capture-python-tests**

```bash
/usr/bin/python3 -m unittest tools.tests.test_sword_qi_capture tools.tests.test_combat_capture tools.tests.test_world_sim_entry_evidence
```

缓存/运行条件：Python stdlib local warm filesystem; final capture helper and reuse guard tests

**final-openspec-change-strict**

```bash
openspec validate add-spiritual-power-sword-qi --strict
```

缓存/运行条件：warm CLI; final docs and acceptance evidence synchronized

**final-openspec-baseline-strict**

```bash
openspec validate --specs --strict --no-interactive
```

缓存/运行条件：warm CLI; final baseline specs synchronized

</details>

## 关联文档

- [灵力与剑气技术说明](../ai-kb/45_spiritual_power_sword_qi.md)
- [README 使用与验收](../../README.md#spiritual-power-and-sword-qi-0460)
- [采集工具](../../tools/combat_capture/README.md)
