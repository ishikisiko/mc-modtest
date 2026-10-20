# Agent Notes

先思考和核对，不轻易肯定用户观点。最后用中文汇报。

## 默认工作方式

- 从 `docs/ai-kb/INDEX.md` 找模块；工作流与最小检查规则只看 `docs/WORKFLOW.md`。
- 后续新增内容、界面与预览以中文为准，不再专门制作英文版本；保留已有兼容翻译。
- 用户已授权的工作直接完成。普通任务不创建 OpenSpec proposal/design/tasks，不运行 OpenSpec CLI，不要求 CRAFT 前门、角色审批或归档。
- 先选择本次改动必要的检查；通过就交付。失败先定位，只有具体证据才扩大范围。旧 KB/报告的全量命令清单不覆盖此规则。
- 纯工具、测试、非打包文档改动不升 mod 版本、不构建 jar、不启动 Minecraft。发版规则只在 `docs/WORKFLOW.md` 的“版本和交付”维护。
- 必需的重型 Gradle/服务器/客户端工作使用 Modal，无自动本机回退。固定源、保留失败、确认销毁；命令直调和姿态 probe 不是映射输入证据。
- 默认分支从 main 创建，完成提交后 fast-forward 合并 main 并推送，除非用户另有要求。无关未提交文件保持原样。
- `reports/` 为忽略的生成输出；不提交，也不删除两个已跟踪基线 `reports/town_distinctness_calibration.json` 和 `reports/cultivation_style_baseline_hashes.txt`。
- GenOps/CRAFT 是用户明确选择时才启用的可选工具；不自动派生子代理。开启可选工具时仍遵守真实写入范围和实际证据。

## 不可丢失的技术边界

- 当前 namespace 为 `myvillage`。生成资源只改 authored inputs 后再生成，不能手改输出。数据、阈值、概率和调参值以其配置为唯一来源。
- 新 settlement family 经 `tools/buildgen/groups.py`；模组合法性经 `modset.py` 与 catalogue；roof/motif 经注册表，不按字符串前缀分派。
- 服务器拥有输入校验、命中、成本、状态和结果。公共代码不导入 client-only 类型；客户端只提交有界意图。
- 修炼不可变替换只经 CultivationService，copyOnDeath 不叠加手工 Clone 复制；世界模拟命簿只经 WorldSim facade 修改，化身为非持久投影。
- 保持原版伤害减免仅执行一次、境界修饰幂等、旧存档/registry ID 兼容、地区出生状态不重抽。
- PAL 使用精确根 jar；武器模型/动画与兽/NPC 资源保留专用生成器归属，第一人称解算变更保持 Java/Python parity。
- 外观变更先查看当前参考和预览并给设计判断；遵守已约定的视觉方向确认。开发者检查不能代替玩家外观/手感/音效/FPS/多人验收，未观察项保持 `not_verified`。
- 只在实际人工预览交付时准备对应产物与服务。此主机公共地址有效时使用 `http://43.156.135.198:8765/`，复用现有正确服务，不干扰同端口的其他接口。

- 天命局部构建可用 Modal `--preset fates`：仅 FateFoundationTest 与 jar build，不运行游戏或全量 release。

- 炼丹学习/补料局部构建可用 Modal `--preset alchemy-build`；旧实体丹方槽采集器需迁移后才能用于新版完整验收。

- 灵土/灵植：KB51；仅三阶段，六味药材兼作播种和收获。资源只改 `tools/gen_spirit_crops.py`，局部云端测试/构建用 `--preset crops-build`。

- 仅资源表现修复可选 Modal `--preset jar` 打包，配合对应生成器检查，不自动重复玩法测试。

## 按需查阅

| 范围 | 技术约束 | 主要实现说明 |
|---|---|---|
| 资源、物品、匾额、指南 | `docs/contracts/assets.md` | KB02–06、22、31、41、49 |
| 建筑和风格 | `docs/contracts/buildings.md` | KB10、21、23、24 |
| 院落、花园、连通性 | `docs/contracts/compounds.md` | KB15、16 |
| 城镇 | `docs/contracts/towns.md` | KB11、12 |
| 区域、宗门、命簿、境界 | `docs/contracts/world.md` | KB07、13、40、43、47 |
| 修炼、功法、天命、炼丹 | `docs/contracts/cultivation.md` | KB28–30、37、41、45、48–50 |
| 战斗、武器、实体 | `docs/contracts/combat-entities.md` | KB26、27、32–39、42 |
| 采集、预览、云端、可选 GenOps | `docs/contracts/tooling.md` | KB09、17–20、34、46 |

上述文档是技术约束，不是每次必须执行全部命令的清单。专项入口、数据字段、原有接受基线与未实现范围按同主题 KB 和代码查阅。
