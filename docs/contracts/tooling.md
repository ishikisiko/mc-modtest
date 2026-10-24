# 采集、预览与可选工具的边界

<a id="genops"></a>
<a id="craft-frontdoor-governance"></a>
<a id="docs-knowledge-base"></a>
<a id="spec-baseline-governance"></a>
<a id="cultivation-core-validation"></a>
<a id="validation"></a>
<a id="combat-capture-tooling"></a>
<a id="chunky-acceptance-automation"></a>
<a id="modal-test-runner"></a>
<a id="interactive-preview"></a>
<a id="visual-reference-structure-pipeline"></a>

## 真实运行证据

- 需要重型检查时使用 Modal，不自动回退到本机 Gradle/Minecraft。固定提交、依赖与执行器分别记 hash；只上传明确允许输入，不上传通用未跟踪文件、凭据或用户世界。
- 云端 workspace/world 临时隔离，持久卷只存依赖缓存；同一固定源运行可复用编译产物。超时有界，零自动重试，成功/失败/取消均收集可用证据并确认销毁；终止请求与已确认终止是不同状态。
- 报告分开记录准备、构建、服务器、客户端、下载和清理；RSS sum 含共享页重复计数，不冒充 cgroup 峰值。软件渲染不能证明玩家 GPU FPS/声音。
- 游戏行为由实际映射按键/鼠标触发；RCON 仅做管理员准备与权威读回。角色站位、按键来源、菜单状态和资格先检查；姿态 probe、命令直调和启动成功不能冒充实际交互证据。
- 阡镇自然开局不能用管理员平坦场景部署、传送或发物冒充自由出生/发现/行走；旧 version1 采集器不证明 version2。服务器 fact 自动阶段须由真实碑/天命界面/吐纳/播种及权威读回验证，不能点击旧 confirm/report 链代替。离线计划保持 ready_not_run，原子操作采集完成或 trace_valid 不等于场景原生完成；旧档修复只在独立副本验证 preview/apply、幂等与非目标保留，原件和失败产物保留。
- 场景独立准备前提。只运行选中场景，报告 selected/completed/unrun；局部通过不等于完整运行验收。存档检查保留全部 batch/组件/品质与在线计时，不因重新进入世界重抽。
- 采集默认清新世界；显式 reuse 要求已有隔离世界。仅清理自己创建的进程树并持有共享锁。自然怪默认禁用，采自然出生证据需明确打开服务器选项并记录/恢复配置。

## Chunky 与预览

- Chunky 是可选的结构/世界生成验收工具，不是普通玩法必经前置，也不是运行时依赖。当前命令具体包含哪些步骤以 preset 列表为准；不运行某 preset 就不宣称它通过。
- 真正运行 Chunky 时仍检查任务启动后的本世界完成日志、deadline 和干净保存/停服；初始 idle、旧日志和其他世界完成不能提前判成功。locate/pregen 范围有界，不扫整个区域图。
- 离线 viewer 自包含本地数据/渲染库，保留内层体素以支持切片；粗体素预览不能证明方块朝向、完整自定义渲染或玩家体验。匾额按实际模型 UV 显示整幅题字。
- 改外观前看当前参考/预览并给设计判断；预览、自动校验与玩家视觉验收分开。只有准备人工评审时才生成对应页面并提供可访问地址，不要求普通工具修复启动预览服务器。
- 有来源结构/图像时保留来源、许可和使用决定；直接部件、原子构件、生成语法、仅校准是可用分析词汇，不要求每次填写强制合同或走审批流水线。

## 可选 GenOps

- GenOps/CRAFT 仅在用户明确选择时启用；可复用任务范围、patch guard、命令日志、manifest 和视觉证据能力，但不作为普通编辑/提交的前置。
- 不自动派生子代理或要求专职角色审批。开启可选 pipeline 时遵守它声明的写入范围与停止条件；退出该工具后不把其生命周期套到整个仓库。
- OpenSpec CLI、proposal/design/tasks、同步和归档均已退出日常流程。已有技术约束迁入本目录，历史决策从 Git 基线检索，不再维护重复流程树。

入口：[Modal](../ai-kb/46_modal_test_runner.md)、[采集](../ai-kb/34_combat_data_and_capture.md)、[Chunky](../ai-kb/17_chunky_acceptance.md)、[工作流程](../WORKFLOW.md)。
