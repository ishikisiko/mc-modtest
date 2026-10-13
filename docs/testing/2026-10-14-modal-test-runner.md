# Modal runner：实现与测试记录（2026-10-14）

状态：本地实现及五个实际云端 preset 均已取得对应范围的通过证据。账户认证已完成；首轮真实 Sandbox 在执行测试前因 bootstrap hash 不一致失败，已获取诊断工件并确认终止。修复后的第二轮 smoke 已通过，首轮完整 Java/build preset 在固定提交 `4e3f6ee` 上因缺少结构生成 catalogue 失败，已确认清理；修复后的 build 已通过 913 项并取回 0.46.1 jar；Python preset 也已通过 1053 项，server stage 1 与 client 的 tap/charged 两项 mapped-input 试验已通过。

测试源基线为 `47525d3`（0.46.0）；本轮工具发布目标 0.46.1。
`--ref` 在归档时解析到完整提交，未提交的本轮代码不会混入测试源。
远端 executor、精确 PAL 和 block catalogue 依赖分别冻结复制到每次运行目录并计算 SHA-256，允许测试旧提交，同时明确测试源与执行器不是同一份来源。

## 请求与隔离

- SDK 固定为 Modal 1.6.1；资源请求 `cpu=(4, 4)`、`memory=(16384, 16384)`，单位按 SDK 的 core / MiB 参数。请求不代表独享宿主硬件或实测吞吐；实际结果应读取环境/cgroup证据。
- 默认超时 1800 秒，范围 180–7200 秒；从 Sandbox 创建后限制其生命周期，创建前镜像构建不在这个超时内，因此不是整轮计费硬上限。不自动重试。失败后人工发起的新尝试单独记录。
- 固定提交的 `git archive`，额外仅上传两个精确依赖文件（根 PAL jar 和 `exmod/mod_block_catalog.json`）、冻结的 runner bootstrap 和请求清单。常见私密路径在云端分配前拒绝；这不是任意文件内容的通用秘密扫描器。
- source、world 和 test state 位于临时 workspace。专用持久卷仅存 Gradle 依赖缓存；首次 cache 是否为空、后续是否命中均应实测。
- 无 GPU、公开端口或 DevBridge。client preset 已验证 tap/charged 两项实际云端键鼠/录像；其他场景未验证，本地剑气证据与用户 PC 观感验收独立。

本次使用 `modal token new --profile myvillage --expires-in 7d` 完成认证，
到期需要续认证；报告和仓库不保存令牌。
结束时主实现方通过实时 SDK list 核对 `myvillage-tests` 的 `running_sandboxes=[]`；
全部 7 次云端尝试的本地 state 都记录 termination confirmed。

SDK 语义由主实现方核对 [Sandbox API](https://modal.com/docs/reference/modal.Sandbox) 与安装版本；本记录不将尚未运行的配置推断为云端硬件事实。

## 记录位置与口径

| 内容 | 路径 |
|---|---|
| 本地校验的 UTC/墙钟/退出码/CPU/RSS | `reports/modal-test-runner-tests/timings.jsonl` |
| 每轮固定提交、请求与输入 hash | `reports/modal-runs/<run-id>/request.json` |
| Sandbox id、阶段耗时、错误和终止结果 | 同目录 `state.json` |
| 远端标准输出/错误 | 同目录 `remote.stdout.log` / `remote.stderr.log` |
| 下载的校验后工件及远端环境/逐命令结果 | 同目录 `artifacts/`，环境/命令位于 `artifacts/evidence/result.json` |

镜像创建、上传、执行、下载和终止分开记录。镜像首次准备可能占据主要时间，不应称作测试执行时间。
本地 GNU 最大 RSS 只表示被观测单进程峰值，不能代替远端容器总内存。
只有实际下载到的环境字段才能用于远端资源比较。gVisor 内 cgroup 文件可能不可见，首轮为 null，不能写成已测到资源限额。后续监视器记录 Sandbox `/proc` 进程 RSS 总和与 `/proc/meminfo`；RSS 共享页面按进程重复计数，不等于独占物理内存。已配置依赖卷不等于暖缓存命中。

## 当前验收状态

| 表面 | 结果 |
|---|---|
| OpenSpec 严格校验 | `pass`，初始 1.161 秒；最终 change 1.430 秒、79 项 baseline 1.527 秒，本地无云分配 |
| SDK/账户认证 | `pass`（主实现方核对），不代表 remote smoke |
| 本地归档与生命周期回归 | `pass`，15 项，最新 0.380 秒；此前各轮保留 |
| 首轮真实 smoke | `fail`（测试前 `remote.py` hash 不一致） |
| 修复后真实 smoke | `pass`，17.626 秒，工件获取与确认终止均成功 |
| 首轮失败后的工件获取 / 确认终止 | `pass`（下载并校验成功，termination confirmed） |
| 云端 Java/build | `pass`，913 tests，0 failure/error/skip；总墙钟 190.077 秒 |
| 云端 Python | `pass`，1053 tests；runner 总墙钟 156.217 秒 |
| 云端 server stage 1 | `pass`，commands/save/stop；总墙钟 143.870 秒 |
| 云端 client 与实际键鼠采集 | `pass`，仅 `tap_hit` / `charged_hit` 两项；视频/日志/manifest 已取回，终止 confirmed |
| 用户声音、手感、PC帧率、视觉接受 | 本工具不替代这些验收 |

## 首轮真实失败（保留，不覆盖）

运行 `20261013T202658Z-f3ed294a` 测试提交 `47525d3d2048a37f1fb2873085f48eaff1db33e8`，
preset `smoke`，timeout 600 秒。控制器总墙钟 132.463 秒：

| 阶段 | 秒 |
|---|---:|
| 创建（含首次镜像准备） | 117.911 |
| 上传 | 12.610 |
| 远端作业 | 0.481 |
| 下载并校验诊断工件 | 0.960 |
| 确认终止 | 0.501 |

远端在输入完整性校验中发现 `remote.py` SHA-256 与请求不一致，`steps=[]`，所以没有 Java/Python/图形测试通过。
原因是首次启动时执行器文件仍可能在本地准备与上传之间变化；修复为将 PAL 和 bootstrap 先冻结复制到每轮输入目录再计算并上传 hash。
下载到的环境是 Python 3.12.3、4 个可见 CPU、gVisor Linux；本轮未观察到的 cgroup 值为 null，不推断 16 GiB 已被完整利用。
初始 Gradle cache 目录不存在；该失败尚未开始依赖下载，因此不是 Java 冷构建基准。

## 修复后真实 smoke

运行 `20261013T202927Z-a730a50f` 仍测试同一基线提交，控制器总墙钟 **17.626 秒**。

| 阶段 | 秒 |
|---|---:|
| 创建 | 2.376 |
| 上传 | 11.576 |
| 远端作业 | 2.235 |
| 下载并校验 | 0.898 |
| 确认终止 | 0.542 |

Java 版本、Python 的 yaml/numpy/Pillow 依赖和 Xvfb `glxinfo` 均通过。
观测渲染器为 Mesa 25.2.8 llvmpipe（LLVM 20.1.2）、OpenGL 4.5，没有 GPU。
终止后的 Sandbox 入口是被清理杀掉的 `sleep`，`status` 返回 137；实际远端测试退出码是 0，两者不是同一个失败信号。
这证明已能分配 Sandbox、上传固定输入、执行小任务、拉回证据并确认终止；不证明 Minecraft 客户端或整套 Gradle 已能工作。
第二轮明显减少的是创建阶段；尚未执行 Gradle 的两次 smoke 不能证明依赖缓存命中。

固定的 0.46.1 实现提交为 `4e3f6ee`。下一轮 build 测试它，timeout 1800 秒；
后续本地文档修订不进入这一轮源归档。首次 build 结果和修复如下。

## 首次完整 build 的外部依赖失败

运行 `20261013T203004Z-bc3b9026` 总墙钟 **233.883 秒**，创建 2.392、上传 11.996、
远端作业 213.980、下载 0.929、确认终止 4.585 秒。Gradle 完成 Java 编译后，
`generateAllStructures` 因缺少 `exmod/mod_block_catalog.json` 失败，未完成 test/build。
诊断工件获取及终止均已确认。

固定 Git 归档正确排除了未追踪文件，但构建依赖的本地 catalogue 原本不在上传契约里。
据此将依赖白名单从仅 PAL 调整为两个精确文件：根 PAL jar 和约 1.5 MB 的
`exmod/mod_block_catalog.json`，分别冻结并记录 hash。没有上传整个 `exmod` 或其他 mod jar，
也没有将用户无关的未追踪文件纳入归档。
此失败包含首次 Gradle 依赖下载/准备，不能与单纯增量构建直接比较；重跑的 cache 条件另记。

## 完整 build 重跑通过

运行 `20261013T203449Z-9776904f` 测试固定提交 `d240fb1`，913 项 Java 测试全部通过，
无 failure/error/skip；`myvillage-0.46.1.jar` 为 3,541,831 bytes，已下载并放入本地 `build/libs/`。
这是实际云端构建结果，未为重复同一检查再启动本地 Gradle。

| 计时层 / 阶段 | 秒 |
|---|---:|
| 本地控制器总墙钟 | 190.077 |
| 创建 | 2.349 |
| 上传 | 12.807 |
| 控制器观测远端作业 | 166.944 |
| 远端 bootstrap 自报总时长 | 166.371 |
| 实际 Gradle 命令 | 165.328 |
| 下载并校验 | 3.082 |
| 确认终止 | 4.894 |

观测到 4 个 CPU、Python 3.12.3、gVisor Linux。Sandbox `/proc` 的 0.5 秒采样 RSS 总和峰值
2,513,472 KiB（约 2.40 GiB），最多 7 个进程；共享页按进程重复计数。
这与上一轮本地主机 GNU 单进程最大 RSS 不是同一口径，不能直接当作两环境的内存倍数。

`cache_before` 存在 `.tmp/caches/daemon/native/notifications/wrapper`。Neoform 日志显示各阶段缓存命中，
该准备段总计 20.36 秒，初次失败 build 的对应准备段为 154.68 秒。
前一轮缺 catalogue 最终失败、两轮项目状态也不同，不能由这两数计算完整构建加速比。
目前观察到的收益是依赖/工具准备被复用；尚未做全新账户/全冷镜像与缓存的受控对比。

## 完整 Python preset 通过

运行 `20261013T203822Z-bb8aa5f2`：`unittest discover -s tools/tests` 通过 **1053 项**。
框架自报 136.778 秒，实际命令墙钟 137.520 秒，远端作业阶段 138.956 秒，控制器总计 156.217 秒。
创建 2.368、上传 12.691、下载 1.212、确认终止 0.988 秒；工件已校验下载，终止 confirmed。
0.5 秒采样的 Sandbox `/proc` RSS 总和峰值 539,204 KiB（约 526.6 MiB），最多 5 个进程。

此前本地报告跑的是受影响的 384 项子集，此次是整个发现集 1053 项，
两者测试选择、进程环境和缓存不同，不能把耗时或测试项数量的比值当作云端加速比。

已有实际阶段耗时；尚无受控吞吐或价格对比结论。失败和后续重跑各自保留。

## 有界 server stage 1 通过

运行 `20261013T204137Z-2643758c` 在同一固定提交 `d240fb1` 上执行 stage 1，
实际命令 116.757 秒，bootstrap 117.953 秒，控制器总墙钟 **143.870 秒**。
创建 2.338、上传 13.083、远端作业 118.393、下载 1.388、确认终止 8.668 秒。
下载的 `reports/chunky_acceptance_report.json` 标记整体和 stage 1 为 passed，
包含命令与保存/停止检查；这不属于客户端渲染或交互证据。
0.5 秒采样的 Sandbox `/proc` RSS 总和峰值为 2,893,192 KiB（约 2.76 GiB），最多 8 个进程。

原 report 引用了验收服务器 stdout，但 runner 最初未收集该文件。
后续提交 `f1d99e1` 将精确 `run-acceptance/acceptance-server-stdout.log` 加入工件白名单，
15 项 focused tests 通过（0.380 秒）；client preset 从这个固定提交运行。
Java/游戏资源未变，下载 jar 仍对应 `d240fb1` 的 0.46.1 构建。

## client 两项 mapped-input 试验通过

运行 `20261013T204431Z-1adf6242` 测试固定提交
`f1d99e1e2ec8f4832444ddc5f386fe8b86658e5e`，使用现有 capture 的真实右键按下/释放，
RCON 只准备边界 fixture 和读取服务器结果，没有 DevBridge 或姿态探针替代输入。
命令为 `python3 -m tools.combat_capture sword-qi --trials tap_hit,charged_hit --out out/preview/modal_sword_qi`。
两项都通过，MP4、日志、manifest 和页面已下载校验，Sandbox 终止 confirmed。

| 计时层 / 阶段 | 秒 |
|---|---:|
| 本地控制器总墙钟 | 150.015 |
| 创建 / 上传 | 2.344 / 12.999 |
| 控制器观测远端作业 | 131.382 |
| 远端 bootstrap / 实际 capture 命令 | 130.987 / 129.723 |
| 下载并校验 / 确认终止 | 2.299 / 0.989 |
| capture 服务器就绪 / 客户端加入 / 客户端入场 | 64.3 / 27.2 / 39.2 |
| capture 整体启动 | 103.8 |
| tap_hit / charged_hit 场景 | 5.540 / 7.602 |

内部就绪计时存在累计边界，不能将全部行相加当成总耗时。
0.5 秒采样的 Sandbox `/proc` RSS 总和峰值为 6,108,564 KiB（约 5.83 GiB），最多 13 个进程。
主实现方查看了 charged_hit 录像抽帧，确认出现实际世界、铁傀儡、剑气与 HUD。
软件渲染图像是开发证据，不是用户 PC 帧率或体验结论。

| 试验 | 服务器 charge / cost | 灵力变化 | 目标生命变化 | 结果 |
|---|---|---|---|---|
| tap_hit | 38 / 9 | 20 → 11 | 100 → 93.23059 | 输入、释放、命中和扣费关联通过 |
| charged_hit | 255 / 12 | 20 → 8 | 100 → 90.55 | 输入、满蓄释放、命中和扣费关联通过 |

该次用了管理员学习/充能/目标 fixture，manifest 明确将自然获取、重启持久化、双客户端和
owner verdict 保持 `not_verified`。不能把两个场景扩大为远端全套游戏验收或用户视觉接受。
client 与先前成功 build 的 PAL、catalogue 和 controller hashes 相同；变动输入为：

- source.tar：`a8a2ed5231b809a4ee02c879631c4c8af4b2ad2c479fc45f28e2f0e39d196087`
- remote bootstrap：`55b3a574b58722ac72d4a64fcc6f768cf9576ce39679380c6201996b860e2094`

## 本地检查与来源摘要

本地没有重复云端的整套 Gradle；本地检查集中在 runner 的输入/清理契约、规范和下载产物。
所有现有本地计时均保留在 `timings.jsonl`；下表的 RSS 是 GNU 单进程最大值，
与远端采样的多进程 RSS 总和不同。

| 检查 | 墙钟秒 | user/sys 秒 | 最大 RSS KiB | 结果 |
|---|---:|---:|---:|---|
| 初始 OpenSpec strict | 1.161 | 0.40 / 0.10 | 96,648 | pass |
| 初始 runner 12 项 | 0.228 | 0.15 / 0.05 | 26,368 | pass |
| 扩展 runner 15 项 | 0.334 | 0.14 / 0.06 | 26,240 | pass |
| 输入来源回归 | 0.444 | 0.17 / 0.07 | 26,624 | pass |
| catalogue 白名单回归 | 0.336 | 0.14 / 0.06 | 26,624 | pass |
| stdout 工件白名单回归 | 0.380 | 0.14 / 0.08 | 26,368 | pass |
| 无 Modal SDK 的 system Python 离线 plan | 0.381 | 0.26 / 0.09 | 25,916 | pass，无云分配 |
| 最终 OpenSpec change strict | 1.430 | 0.47 / 0.11 | 97,264 | pass |
| 最终 OpenSpec 全部 baseline strict | 1.527 | 0.49 / 0.14 | 109,676 | 79 / 79 pass |
| 下载 jar 的 TOML 版本和 README 58 项条目检查 | 0.429 | 未采集 | 未采集 | pass，0.46.1 |

jar 检查第一次的字符串断言因合法 TOML 空格而失败（0.512 秒）；
改用 TOML 解析后通过。该失败属于检查脚本，不是产物损坏，原记录保留。

成功 build 和 Python 的固定源提交均为 `d240fb144aeacaed45be3d24a76be69b56e5f8dd`。
以下 SHA-256 来自该次请求清单，可在忽略的运行目录之外核对关键来源：

| 输入 | SHA-256 |
|---|---|
| source.tar | `397445ea84a31c12066e455a02e4b4b384f7e935bda3fb78fb06c26623334c95` |
| 精确 PAL jar | `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072` |
| block catalogue | `56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24` |
| remote bootstrap | `a29a440d3916108befbfa0ad524ed07ab9e633ea98ebcaa1077a8b5f676a2705` |
| 本地 controller | `344e012eadf3ff11b6af1b36fb1c9864dfc448e85ea6446084be280863c93fd5` |

## 关联文档

- [Modal runner 使用与边界](../ai-kb/46_modal_test_runner.md)
- [本地灵力/剑气测试基线](2026-10-14-spiritual-power.md)
- [README](../../README.md#on-demand-modal-test-runner-0461)
