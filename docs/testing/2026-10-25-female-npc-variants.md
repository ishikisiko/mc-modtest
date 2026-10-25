# 女修组合生成与运行时接入

## 2026-10-26 补齐发色预览

[四种发色与六套衣装对照](https://assets.ishiki333hhh.win/female-npc-hair-colours-0.91.3/)
补回已保留的墨蓝原配色、栗棕、银灰、灰紫；支持三脸、四组发型、六衣装和四视角。
这是预览工具与文档更新，运行时资源仍为下文 `ef07e2f2` 的 0.91.3，不升版、不重打包或启动游戏。

- 72 × 4 份贴图通过正式换色器与 painter 重绘对照；非头发纹素全部保持。
- 1152 张视图完整，其中 288 张原配色视图与上一版逐字节一致。
- 已检查桌面／手机页面；发色、衣装两种对照模式及脸型／发型／视角切换正常，无页面脚本错误。
- 核对运行时链路：命簿化身从人物肖像取得发色；普通召唤／刷怪蛋未设置 `Colours` 时仍用原配色。
  本次未增加普通 NPC 的随机发色，也未将离线图视为原生验收。

产物与逐组合检查：`reports/female-npc-hair-colours/{manifest,checks}.json`；
生成日志：`reports/female-npc-hair-colours-render.log`。

## 0.91.3 补齐遗漏衣装

代码源 `ef07e2f2340606a6f24525fbf9ae08f606cafa9d`；补回 01 靛蓝交领、02 月白青裙、03 朱砂直领、05 藕紫对襟，
与 A/B 共六套衣装、72 组合、75 个含旧 ID 的外观。先前仅接 A/B 属于范围遗漏。

- 恢复款原型回放的四类产物 SHA 均与旧预览一致；内藏腿面处理后，16 个静止视角逐像素一致。
- 既有 A/B 的 24 组合、96 份资源逐字节不变；新增 48 组合。
- 27 项 Python 检查通过；Modal `npc-variants-build` 的 169 项 Java 检查通过，零失败、错误与跳过。
- 测试包内 288 份组合资源和共享目录与 npcgen 输出一致；Modal 已确认销毁。
- 六款离线多角度与行走序列已查看。本次没有启动游戏，新增四款原生动态与玩家验收为 `not_verified`，不沿用下文 A/B 的原生通过结论。

运行目录：`reports/modal-runs/20261025T153621Z-51d37307`。

[六套预览](https://assets.ishiki333hhh.win/female-npc-six-outfits-0.91.3/) · [0.91.3 测试包](https://assets.ishiki333hhh.win/female-npc-six-outfits-0.91.3/myvillage-0.91.3.jar)

测试包 SHA-256：`f149434c22c5d15ed351f0051defaab7ea371ce66594043895ddbe8f0a23fe31`。

## 0.91.2 历史接入与原生采集

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
