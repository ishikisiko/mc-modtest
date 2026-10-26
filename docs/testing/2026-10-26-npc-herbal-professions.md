# 0.93.0 NPC 药业职业验证

实现入口和使用步骤见 [README](../../README.md) 的“NPC 药业职业”和 [KB68](../ai-kb/68_npc_herbal_professions.md)。范围为三职业生产分工、独立技艺、分类原料、有限药圃、原药铺伙计取消生产，以及对应存档和交谈检查；不运行全量 release。

## 检查边界

本次使用用户指定的 Colab 专用 High-RAM CPU 会话 `npc-professions-20261026`。固定提交归档和 PAL 原始根 jar、方块 catalogue 分别记录 SHA-256；Java 定向检查与构建在同一归档目录执行，不混用其他提交的测试通过结论。重型测试和服务器均不在本机运行。

纯内核测试覆盖采集、播种、成熟收获、养护、配方扣料、有限补货、缺料/满仓/受阻拒绝、经验独立与换岗保存、旧未分类库存、旧档迁移和非法存档。相邻回归覆盖生活执行、商业报价/收据、近远场执行租约和剧情人物；运行时测试检查中文近况与现有对话授权。

服务器探针只用 RCON 建立全新测试场所、登记现有人物并读取结果，核对三职业和原药铺伙计、分类采集点、空药圃、幂等登记及同一世界保存重启。探针不替玩家交谈或交易，不以命令代替键鼠输入。

药圃是命簿批次，不是逐格真实作物操作；NPC 炼制沿用基础丹方材料/产量/时长，本版不模拟玩家丹炉燃料槽。当前没有新增高阶稀有灵草或自动职业晋升。

真实玩家交谈、交易操作、近场寻路表现、外观、手感、音效、FPS 与多人玩家验收保持 `not_verified`。构建或服务器 fixture 通过不改变这些边界。

## 结果

交付固定源为 `38c4b12dfd37e1b3d661559a46891c57c7b45b3b`，`git archive` SHA-256 为 `c383c971ac3aefe3092cec79e4a97ed0d19d9d3f2df5f261c0eb94f381eef4da`。PAL 根 jar SHA-256 为 `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`；原始方块 catalogue SHA-256 为 `56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24`。

- Java：61 个 suite、416 项测试，失败、错误和跳过均为 0；所选范围为 `sim.*`、`npc.*` 与 `AlchemyRulesTest`。
- 服务器：`selected=completed=[server_registration, profession_assignments, typed_stations, registration_idempotence, saved_world_restart]`，`unrun=[]`；23 条 `checks[*].pass` 全部为 true。
- 使用同一个新测试世界 `npc-professions-fixed-source`、种子 `20261026` 保存并重启，未补造人物或修补 NBT。该 fixture 保持命簿暂停，验证登记、身份、配置和零收益保存；非零经验、播种后保存再成熟收获及生产防重由 Java 测试覆盖，不能当作原生玩家操作证据。
- 运行结束后，6,532 个固定源文件哈希一致；运行时 1,653 个 class、4,443 个资源与交付 jar 逐字节相符。
- jar：`myvillage-0.93.0.jar`，SHA-256 为 `50f12ae208506de900645dfa17d91c7562416440bf3b94139aaf3d0f47e89752`。
- 两次服务器启动均正常退出，退出码 `[0, 0]`。按用户明确要求，High-RAM CPU 会话 `npc-professions-20261026` 保留，最后状态为 `IDLE`，没有执行回收。

原始日志、JUnit XML、失败包、测试世界与交付包已下载到忽略目录 `reports/npc-professions-colab-20261026/`。本篇记录在运行后追加，未改变 jar 的打包源码。

## 首轮失败与资源复用

首轮固定源 `b4e08d594d47a27914257a350d92a59207955ad5` 完成 416 项测试，其中 2 项文本键覆盖失败：`main` 已有的 `world_sim.event.near_death.1` 中英文文本使用未编号的 `%s`，与原有位置参数断言不符。其余 414 项通过；该轮没有运行服务器探针。随后只把这两个现有词条改成 `%1$s`、`%2$s`、`%3$s`，保持断言后重跑全部所选检查。失败原包为 `failed-b4e08d59.tar.gz`，没有被新结果覆盖。

首轮完整生成器还重排了两份语言文件中既有庭院词条的顺序：逐字节源文件审计因此标记这两份文件变化，JSON 键和值均相同；该轮审计仍按失败保留。交付运行从新归档独立解压，保留修正后的原始语言文件。

两轮之间仅上述文本占位符变化，693 个生成器输入文件逐一比对一致。交付构建只复用上一轮的 540 个生成资源，逐文件记录并复查哈希；没有额外缺失的源资源，也没有复用 Java 编译产物、测试或服务器通过结论。交付运行的源文件、class、资源和 jar 检查均通过。

[下载 jar](https://assets.ishiki333hhh.win/npc-professions-0.93.0/38c4b12d/myvillage-0.93.0.jar) · [使用说明](https://assets.ishiki333hhh.win/npc-professions-0.93.0/38c4b12d/README.txt) · [证据摘要](https://assets.ishiki333hhh.win/npc-professions-0.93.0/38c4b12d/evidence-summary.json) · [原始证据和世界](https://assets.ishiki333hhh.win/npc-professions-0.93.0/38c4b12d/evidence.tar.gz) · [SHA-256 清单](https://assets.ishiki333hhh.win/npc-professions-0.93.0/38c4b12d/SHA256SUMS.txt)。
