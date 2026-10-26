# 0.95.0 功法成长样例验证

范围为基础吐纳诀、青木长春功、乙木生华诀、剑气诀，使用入口和边界见 [KB69](../ai-kb/69_technique_growth_sample.md)。用户指定 Colab；没有本机 Gradle、服务器或客户端运行，没有扩展到全量 release。

## 固定来源

- 模组内容、Java 检查与 jar：`ea9657366d1c9ef8926764bd835ab5276c996146`；归档 SHA-256 `30eb1d510846113d9777f9cd5813930ac0e7997242cb94dab55d6b0a61bad80e`。
- 最终采集器：`b9de1d54781613a78e2c13f6a4dcff05e192f04c`；归档 SHA-256 `91da0273de9c8c7a542976d88b5450160f6a651a27610b485c8ebdcceea5db1d`。与模组内容提交仅 `tools/technique_growth_evidence.py` 不同，修正菜单 Escape 的映射输入接口，并把夹具灵石放在独立槽位。全部打包输入逐文件一致，复用同一个已测 jar，不重新升版或打包。
- PAL 精确根 jar：`b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`；方块 catalogue：`56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24`。
- Colab 专用 High-RAM CPU 会话 `technique-growth-20261026`，实测 8 vCPU、约 51 GiB 内存。每轮独立归档与输出目录，前两轮失败产物和世界均保留。

## 结果

- 本地 4 项资源检查通过；专用生成器与功法目录 `--check` 通过，修炼定义校验通过（159 功法、17 阶段）。
- Colab 204 项 Java 检查通过，失败／错误／跳过均为 0。覆盖五阶段与上限、互斥参悟、过期请求、学习优惠不叠加、原资格与研读门槛、前篇承接、提交失败不耗书、旧存档缺省及未知状态、修习会话、网络编解码、灵力与剑气相邻回归。
- 最终原生运行 `r3/native-1`：`selected=completed=[practice, insight, related_learning, lineage, sword, save_restart]`，`unrun=[]`；17 条 `checks[*].pass` 全部为 true。该轮从头完整执行，没有使用前两轮的局部通过项补齐。
- 玩家动作来自真实客户端 H 面板鼠标、秘籍右键和剑气鼠标按下／释放；RCON 仅准备夹具和读回。观察到未完成修习不扣费、完整批次单次扣费、首次参悟扣心得、改选单次扣灵石、圆满停止后续扣费、基础吐纳带来的首次参悟优惠、前篇未圆满拒绝学习、乙木承接初始修习及参悟、剑气空放不增长且有效施放增长一次。
- 同一个最终新测试世界（种子 `20261026`）真实保存并重启，四门功法的熟练度、参悟、已用心得和参悟版本前后一致。基础吐纳 `180/steady/2/2`，青木 `360/flow/1/1`，乙木 `60/flow/0/0`，剑气 `82/reserve/2/1`；上述值依次为修习／方向／已用心得／版本。
- 最后核对 6,561 个固定源文件无变化；运行目录 1,670 个 class、4,450 个资源与交付 jar 逐字节一致，jar 在原生运行前后哈希相同。

交付 `myvillage-0.95.0.jar`，SHA-256：

`d5526173c3609a76242c828b34be9faa185758c23bbd773d932982e6ccd7e67f`

## 失败与复用说明

首个客户端采集缺少 ImageMagick 的 `import` 可执行文件，在截图时失败，六个场景均未完成。补齐 Colab 环境后，第二轮通过了修习和部分参悟检查，但采集器用只允许游戏内状态的 `Game.key` 关闭 H 面板，被输入保护拒绝。之后改为该菜单场景应使用的真实 X11 Escape，并记录输入；没有放宽断言或绕过输入映射。第二轮仅 `practice` 完成，其余保持未完成；原日志、截图及测试世界保留在证据包中。

初次资源生成用时 5 分 28 秒，冷编译与检查 3 分 16 秒；补充配方书提示后的同范围构建 39 秒。资源生成器只重排了两份语言 JSON 的键顺序，确认语义完全一致后，在编译前恢复固定归档中的原始字节并记录前后哈希。后续只复用输入未改变的 540 个结构／世界生成资源，不复用不同源码的 Java 测试结论。最终采集器修正仅涉及工具，已测模组内容和 jar 保持不变。

## 验证边界

为缩短场景，使用了明确的修习阶段边界夹具；青木、剑气秘籍从零研读，乙木秘籍以接近完成的研读进度测试前置拒绝与学成承接。没有直接改写存档 NBT 制造结果。旧格式兼容由 Java codec 检查覆盖；原生重启使用的是本次新世界，不称为玩家历史存档的完整迁移验收。

自然材料收集、工作台合成的真实操作、乙木全时长研读、长期养成节奏、玩家外观／手感／音效／FPS 与多人体验均保持 `not_verified`。这批结果也不代表其他功法或 NPC 职业技能已经获得完整成长。

原始产物已下载至忽略目录 `reports/technique-growth-colab/`；公开交付路径固定在 `https://assets.ishiki333hhh.win/technique-growth-0.95.0/ea965736/`。

下载及逐文件校验完成后已执行 `colab stop -s technique-growth-20261026`，返回 `Session terminated.`；随后服务器侧 `colab sessions` 返回 `No active sessions found on server.`，未保留计费运行时。终止回执随交付产物保存。

[下载 jar](https://assets.ishiki333hhh.win/technique-growth-0.95.0/ea965736/myvillage-0.95.0.jar) · [使用说明](https://assets.ishiki333hhh.win/technique-growth-0.95.0/ea965736/README.txt) · [证据摘要](https://assets.ishiki333hhh.win/technique-growth-0.95.0/ea965736/evidence-summary.json) · [原始证据和世界](https://assets.ishiki333hhh.win/technique-growth-0.95.0/ea965736/evidence.tar.gz) · [SHA-256 清单](https://assets.ishiki333hhh.win/technique-growth-0.95.0/ea965736/SHA256SUMS.txt)。
