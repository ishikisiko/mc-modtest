# Modal runner：实现与测试记录（2026-10-14）

状态：本地实现与实际云端 smoke 正在验证。账户认证已完成；首轮真实 Sandbox 在执行测试前因 bootstrap hash 不一致失败，已获取诊断工件并确认终止。修复后的 smoke 结果待补。

测试源基线为 `47525d3`（0.46.0）；本轮工具发布目标 0.46.1。
`--ref` 在归档时解析到完整提交，未提交的本轮代码不会混入测试源。
远端 executor、精确 PAL 依赖分别冻结复制到每次运行目录并计算 SHA-256，允许测试旧提交，同时明确测试源与执行器不是同一份来源。

## 请求与隔离

- SDK 固定为 Modal 1.6.1；资源请求 `cpu=(4, 4)`、`memory=(16384, 16384)`，单位按 SDK 的 core / MiB 参数。请求不代表独享宿主硬件或实测吞吐；实际结果应读取环境/cgroup证据。
- 默认超时 1800 秒，范围 180–7200 秒；不自动重试。失败后人工发起的新尝试单独记录。
- 固定提交的 `git archive`，额外仅上传精确 PAL jar、冻结的 runner bootstrap 和请求清单。常见私密路径在云端分配前拒绝；这不是任意文件内容的通用秘密扫描器。
- source、world 和 test state 位于临时 workspace。专用持久卷仅存 Gradle 依赖缓存；首次 cache 是否为空、后续是否命中均应实测。
- 无 GPU、公开端口或 DevBridge。client preset 当前为实验入口，真实云端键鼠/录像未验证；本地剑气证据与用户 PC 观感验收独立。

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
只有实际下载到的环境/cgroup字段才能用于远端资源比较。已配置依赖卷不等于暖缓存命中。

## 当前验收状态

| 表面 | 结果 |
|---|---|
| OpenSpec 初始严格校验 | `pass`，1.161 秒，本地、无云分配 |
| SDK/账户认证 | `pass`（主实现方核对），不代表 remote smoke |
| 本地归档与生命周期回归 | `not_verified`，待最终结果 |
| 首轮真实 smoke | `fail`（测试前 `remote.py` hash 不一致） |
| 首轮失败后的工件获取 / 确认终止 | `pass`（下载并校验成功，termination confirmed） |
| 云端 Python 与 Java/build | `not_verified` |
| 云端 server/client 与实际键鼠采集 | Experimental，`not_verified` |
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

还没有可比较的云端测试耗时或价格结论。失败和后续重跑会各自保留；未完成真实云端验证前不归档此变更。

## 关联文档

- [Modal runner 使用与边界](../ai-kb/46_modal_test_runner.md)
- [本地灵力/剑气测试基线](2026-10-14-spiritual-power.md)
- [README](../../README.md#on-demand-modal-test-runner-0461)
