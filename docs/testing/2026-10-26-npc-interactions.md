# 0.92.0 修士通用交谈基础验证

本次范围为统一交谈、身份事务组合、临时散修有界闲逛及相关授权；不运行全量 release。源码从 main 创建的独立分支构建，原检出未提交的阡镇入口改动不进入本次固定源。

## 使用

- 普通主手右键修士打开“闲聊、询问近况、事务、告辞”。事务内按身份选择交易、剧情、向导或宗门内容；Esc 可以从任意页告辞。
- 普通弟子可以介绍自己的宗门；守山执事/长老沿用原有办事权限。店主只有真实到岗才提供交易服务。
- 刷怪蛋修士为临时散修，基础交谈不增加命簿人物或关系；默认在出生点附近闲逛，范围见 `npc_interaction/rules.json`，旧实体从首次加载位置初始化。
- 客户端与服务器均需更新到协议 46。

## 验证与边界

Colab High-RAM 专用会话 `npc-interactions-20261026`，独立源码归档和输出目录。定向检查覆盖选项组合、事务分页、普通弟子权限、临时散修无事务、会话重放/期限/换身/维度/租约、协议边界与相邻社会关系、宗门、商业和剧情回归。最终编译、打包和服务器结果如下。

实际键鼠交谈、界面观感、手感、音效、FPS 和多人玩家验收保持 `not_verified`；纯逻辑、编译和 NPC fixture 探针不能代替这些结论。

专项交互采集器若仍假设右键直接打开旧剧情/商业/宗门页，需先进入本次的“事务”并选择对应入口后再运行其原场景；本次不宣称这些旧采集流程已迁移或通过玩家操作验收。

## 固定源与失败保留

交付运行时固定源为 `2a17115a8d3c85b24a018b905dcedd6d4cb98863`，独立 `git archive` SHA-256 为 `4ab1557bd10c4169de96c4841ae11ebc1a25b82b1ab5fb084b894cd2455834cd`。PAL 根 jar SHA-256 为 `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`，原始方块目录 SHA-256 为 `56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24`。

早期 `cfffb707` 编译因客户端监听器与父类 tick 同名而失败；`1de3710a` 首次打包因忽略目录中的方块 catalogue 缺失而失败，补齐原始依赖后通过，后来被向导冷却与中文词条修正取代。`ea37c4ef` 的服务器探针在锚点检查处停止：RCON 实际返回 `81L` 加换行，采集器误用字符串结尾判断。保留该轮 `completed=[server_registration]`、其余四项 `unrun` 与原始回复；新采集器按整数值精确检查锚点为 81，没有改变位置或半径条件。

交付源码单独重跑全部选中的测试与服务器场景。仅复用生成器输入完全未变的 `build/generated` 资源，并记录输入比较与逐文件输出哈希；不复用早期提交的 Java 测试或服务器通过结论。交付归档的所有 5,789 个源码文件均在运行前后重新核对；运行时 class/resources 与最终 jar 另作逐文件比较。早期失败和最终结果分别保存。

`2a17115a` 第一轮服务器运行完成前四项，在重启后立即读取 UUID 时收到 `No entity was found`；该轮保留为失败，原世界与日志单独归档。第二轮使用同源 jar 与新世界 `npc-basic-fixed-source-r2`，重启后最多等待 30 秒让原实体载入，随后仍精确比较原 UUID、锚点和命簿身份；没有补召 NPC 或写 NBT 来制造通过。

## 最终结果

- Colab Java：12 个 suite、103 项测试，失败/错误/跳过均为 0；涵盖对话、权限与分页、会话、中文词条、协议、社会关系、宗门、商业、向导与纸页布局。
- 服务器：`selected=completed=[server_registration, temporary_identity, bounded_wander, walk_back_after_displacement, anchor_restart]`，`unrun=[]`；41 条 `checks[*].pass` 全部为 true。新世界种子 `20261026`，重启前后 UUID 与锚点精确一致，未补召或修补存档。
- 完整源码哈希、生成资源输入/输出复用哈希和 jar 必需内容检查通过；运行时的 1635 个 class、3753 个资源文件与交付 jar 逐字节相符。
- jar：`myvillage-0.92.0.jar`，SHA-256 `4b23fb0d42bb0027d3498fbeb5f034980879b79fef91dff6255bb05bd5d237ea`。
- 游戏与采集进程已经退出，服务器退出码为 0；High-RAM 会话保留供后续复用。早期失败日志、失败世界、最终世界和 JUnit XML 均已下载，保存在忽略目录 `reports/npc-interactions-colab-20261026/`。
- [下载 jar](https://assets.ishiki333hhh.win/npc-interactions-0.92.0/2a17115a/myvillage-0.92.0.jar)、[使用说明](https://assets.ishiki333hhh.win/npc-interactions-0.92.0/2a17115a/README.txt)、[证据摘要](https://assets.ishiki333hhh.win/npc-interactions-0.92.0/2a17115a/evidence-summary.json)、[原始证据与世界](https://assets.ishiki333hhh.win/npc-interactions-0.92.0/2a17115a/evidence.tar.gz)、[SHA-256 清单](https://assets.ishiki333hhh.win/npc-interactions-0.92.0/2a17115a/SHA256SUMS.txt)。

交付之后仅追加本篇非打包记录；实际运行时仍对应上述固定提交。RCON 仅用于 fixture/readback，本次没有用命令模拟玩家的交谈、交易或剧情动作；真实键鼠、玩家外观/手感/音效/FPS 和多人仍为 `not_verified`。
