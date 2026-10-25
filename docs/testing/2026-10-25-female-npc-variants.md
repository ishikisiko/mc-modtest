# 女修组合生成与运行时接入

最终代码源：`5b6c487ae604c86de37caf6769cef711ed289ce2`，版本 `0.91.2`。
工作保留在 `feature/female-npc-skin-rebuild`，未合并 main。

## 已接入范围

- 三张保留脸型、四组发型与配套刘海、A 裤装／B 长衣，共 24 个正式生成组合。
- 同一生成目录用于 NPC 外观登记、客户端模型层与材质资源加载；保留三个旧 Look ID。
- 入门女修命簿化身按永久身份稳定选择；普通实体仍使用 Look 同步和 NBT 存档。
- 发色、瞳色继续由人物肖像换色驱动；男性与进阶女修定义保留。
- A 增加内藏裤腰搭接，B 隐藏长衣内部腿面；四个静止角度与修正前逐像素一致。

## 检查结果

| 检查 | 实际结果 |
|---|---|
| Python 组合、角色材质、战斗资源和实体资源验证 | 71 项通过，日志 `reports/female-npc-variants-tests-r3.log` |
| 采集与 Modal 工具检查 | 37 项通过，日志 `reports/female-npc-variants-tool-tests-r3.log` |
| 旧外观资源 | 三套旧外观共 12 份运行时文件相对 `0ade3ec4` 未变 |
| 组合生成一致性 | 24 组、96 份资源及共享目录通过 |
| Modal Java 检查 | 73 项通过，0 失败／错误／跳过；含全部 27 个模型、稳定选择、换色样本 |
| 原生保存重进 | 27 个 Look 在重启前后均匹配，实体 UUID 保持一致 |
| 原生代表性画面 | A `f_novice_02_a_pants`、B `f_novice_03_c_robe`，各 13 张静帧和 2 段行走录像 |
| 画面复核 | 最终 A/B 全身与脸部清晰；录像抽帧确认两套均有实际位移；未见缺贴图 |
| 测试包资源核对 | jar 中 96 份新 NPC 资源与正式生成输出逐字节一致 |
| 云端清理 | 最终 Sandbox termination 为 `confirmed` |

Python 71 项结果来自 `8cb297a7`；到最终代码源仅有清场等待及云端 preset 两处工具变化，
NPC authored inputs、生成资源与 Java 完全相同。最终 Modal 的生成一致性、Java、原生采集均在
同一个固定源 `5b6c487a` 执行，不拼接此前运行的原生通过项。

最终运行：`reports/modal-runs/20261025T150001Z-000bc4fc`。
原生报告 `artifacts/out/preview/npc_variants_native/report.json`：
`selected/completed = [look_load, save_reload, appearance_a, appearance_b]`，`unrun = []`，五项 checks 均为 true。

前两次证据保留：`20261025T144037Z-2f26782e` 在采集启动时缺少锁路径，未启动游戏；
`20261025T144719Z-fa6a3809` 自动采集完成，但视觉复核发现清场死亡粒子遮挡，因此不作为最终静帧证据。
修复清场等待后重跑最终固定源，未放宽断言。

## 产物和边界

[组合预览、原生画面与录像](https://assets.ishiki333hhh.win/female-npc-delivery-5b6c487a/)

[测试包 myvillage-0.91.2.jar](https://assets.ishiki333hhh.win/female-npc-delivery-5b6c487a/myvillage-0.91.2.jar)

SHA-256：`71757ef9e807b874cd38e61fe32371c6168c33d90553d1254509c2b1696dc4ee`。

这是局部开发者验证。原生采集使用普通召唤实体；命簿选择由纯逻辑测试覆盖，命簿原生投影／重投影、
刷怪蛋真实点击、全部组合的完整战斗动作、玩家审美、手感、FPS、音效与多人体验保持 `not_verified`。
相机命令和 NPC 采集不视为玩家映射输入证据。
