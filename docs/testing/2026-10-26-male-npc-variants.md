# 男修组合生成与运行时接入

## 0.91.4-fix1 墨衣劲装裤型修正

用户通过加宽候选后，将同一裤型接入 B 套全部 25 个组合。
固定构建源：`66446a38107885554add2a50c3a7f68c08ac1751`；仍保留在本功能分支，未合并 main。

- 只有 25 组模型、贴图和角色图共 75 份正式外观资源改变；全部动画、其他男修衣装、女修和旧 Look 资源未变。
- 保留鞋底支撑形状与内侧腿缝，仅增加裤腿宽度／厚度并调整靴筒；正式代表组合的四份输出与通过的候选逐字节一致。
- 5 项本地检查通过，覆盖 100 个男修组合的加载／换色／骨架与动作、20 个历史原型回放及新增候选 SHA 回放。
- Modal `npc-variants-build` 的生成检查和 370 项 Java 测试通过，0 失败／错误／跳过；两步退出码均为 0。
- jar 中 704 份 NPC 资源和共享目录与正式输出一致，1627 个类文件与该轮构建一致，也与 0.91.4 相同；启动必需的阡镇场景清单存在。
- Modal 运行 `20261025T183924Z-83bec137` 已完成并确认销毁。SDK 曾重试退出状态查询，两条构建命令均正常完成，未重新执行游戏或拼接其他运行结果。

[更新包与已通过裤型](https://assets.ishiki333hhh.win/male-npc-b-trousers-0.91.4-fix1/) ·
[下载 myvillage-0.91.4-fix1.jar](https://assets.ishiki333hhh.win/male-npc-b-trousers-0.91.4-fix1/myvillage-0.91.4-fix1.jar) ·
[机器核对记录](https://assets.ishiki333hhh.win/male-npc-b-trousers-0.91.4-fix1/verification.json)

测试包 27,405,152 字节，SHA-256：`bd18f9420fcda549b1e8226e50597b9d1ff0e2e2206ecd71654149c44d0a74b5`。
日志与核对记录位于 `reports/male-npc-b-trousers-fix/`；预览候选与动画差异排查保留在
`reports/male-npc-b-trousers-preview/`，旧批准原型 SHA 未覆盖。

本次是资源修正，仅做必要生成、换色、姿态／候选一致性和云端测试构建，没有选取或重跑原生游戏场景。
下文 0.91.4 的原生记录属于上一版，不代表本次加宽裤型的原生验收；加宽版玩家手感、FPS 和多人保持 `not_verified`。

## 0.91.4 组合接入与原生验证

版本 `0.91.4`，最终构建与原生验证源：`a5160321e0ef3ec247b68bfbef84abaec759a67f`。
工作保留在 `feature/female-npc-skin-rebuild`，未合并 main。

[组合预览](https://assets.ishiki333hhh.win/male-npc-combinations-0.91.4/) ·
[原生画面与录像](https://assets.ishiki333hhh.win/male-npc-combinations-0.91.4/native/index.html) ·
[0.91.4 测试包](https://assets.ishiki333hhh.win/male-npc-combinations-0.91.4/myvillage-0.91.4.jar)

请替换此前的 0.91.3 测试包：旧局部构建预设跳过了启动必需的阡镇场景资源，本轮已修复打包并验证启动。

## 接入内容

- 五张脸、五种发型独立组合，搭配 A 青衫长衣／B 墨衣短打／C 月白长衣／旧版衣装，共 100 个男修 Look。
- 正式输入保存在 `tools/npcgen/male_variants.json`；头部、衣装由 `male.py` 和 `male_clothing.py` 组合，生成不依赖报告目录。
- 命簿男性按人物身份稳定选款，境界变化不重抽；发色与瞳色仍随人物肖像。普通召唤／刷怪蛋未设置 `Colours` 时保持原配色。
- 原三类刷怪蛋选择比例保留，在男性类别内选择新组合。旧普通实体的 Look／Colours 继续存读；男性命簿化身首次重新投影会使用新组合。
- 72 个女修组合与三个旧 Look 保留，修士目录共 175 项。旧女修选择盐、固定向量及旧外观资源不变。
- A/C 衣片接入原有摆动骨骼，并加内藏裤装覆盖。原型回放保护 20 个同号脸／发型组合的 80 份资源 SHA。

## 检查结果

| 检查 | 实际结果 |
|---|---|
| Python 组合／换色／旧女修目录／实体资源验证 | 27 项通过；`reports/male-npc-integration/focused-tests.log` |
| 采集与 Modal 打包工具 | 114 项通过；`capture-package-tests.log` |
| 正式生成一致性 | 最终固定源的 `npcgen variants --check` 通过 |
| Java | 370 项通过，0 失败／错误／跳过 |
| 原生保存重进 | 175 个 Look，分配四种 Colours 值；前后 Look、Colours、UUID 全部一致 |
| 四套原生外观 | 每套 13 张静帧、2 段 40 秒行走录像，共 52 张／8 段 |
| 图像复核 | 全身、正脸、后侧及下摆清晰；每段录像在 2／10／20／30／38 秒抽帧，确认实际位移和动作；所看样本未见缺贴图或明显衣摆露空 |
| 测试包核对 | 704 份 NPC 资源及共享目录与正式输出逐字节一致；1627 个类文件与本轮运行时哈希一致 |
| 启动资源 | jar 包含阡镇场景清单与 118 份阡镇 NBT；实际服务器、客户端启动和保存重进完成 |
| 发布链路 | 浏览器读取 12 张原生展示图、8 段录像元数据正常；HTTPS 下载 jar 的 SHA 与本地产物一致 |
| 云端清理 | 最终 Sandbox termination 为 `confirmed` |

四张 `scale_player` 同框比例图的稳定判定均为 false，原始 manifest 保留；不将其视为逐像素静态 golden。
其余 48 张静帧的稳定判定为 true。代表性比例图已查看，人物未被遮挡。

A/C 加动作覆盖后的静态对照：正面、侧面、后侧没有变化；左前视角各有 1 个边缘像素变化，
最大通道差为 26。没有把这一结果写成全视角逐像素一致。

27 项 Python 结果来自 `036a443f`；至最终固定源只改了采集、打包工具和文档，NPC 输入、输出及 Java 完全相同。
114 项工具检查覆盖最终打包修复。离线预览也来自 `036a443f`，最终 jar 中对应资源已逐字节核对一致。
最终的生成检查、370 项 Java、保存重进及四套原生采集全部来自同一个 `a5160321` 运行，未拼接早期结果。

## 固定运行与失败保留

最终运行：`reports/modal-runs/20261025T175301Z-d8309779`。
`artifacts/out/preview/npc_variants_native/report.json` 中：

```text
selected = completed = [look_load, save_reload, appearance_male_a,
                       appearance_male_b, appearance_male_c, appearance_male_legacy]
unrun = []
checks[*].pass = true （共 7 项）
```

原生四个样本分别为 `m_01_01_a / -1`、`m_02_04_b / 50`、`m_03_02_c / 66`、
`m_05_03_legacy / 82`（Look／Colours）。完整 100 × 4 发色 × 4 视角共 1600 张离线预览另存，不能代替全组合原生画面。

前两轮均保留原始日志和产物，且已确认销毁：

- `20261025T172921Z-6d146c2d`，源 `036a443f`：原生启动额外生成资源，之后因对进度的滞后判断而被主动中止；实际已完成 Look、保存重进和 A，B/C/旧版未完成，最终为 failed。
- `20261025T174359Z-b28c8d28`，源 `7787dd41`：复用构建资源后暴露旧预设漏打包问题，服务器因 `FileNotFoundException: myvillage:story/qianzhen_scene.json` 失败；六项全部未完成。修复后用最终新固定源完整重跑。

同文件检查确认已交付的 0.91.3 jar 也缺少该清单；旧测试记录现已标明此限制。
最终预设只跳过建筑全量生成，保留启动必需资源任务；采集只复用该构建，不在重进时补生成。

## 产物与验收边界

`myvillage-0.91.4.jar`：27,404,126 字节。

SHA-256：`605f711ed3dafd00c4c12a8135b0df14b4670c1c509a7feb107cd02127aeb53d`。

机器可读核对记录：[verification.json](https://assets.ishiki333hhh.win/male-npc-combinations-0.91.4/verification.json)。
本地复核与原始证据位于 `reports/male-npc-integration/` 和上述 Modal 运行目录，均不提交到 Git。

这是局部开发者验证，采集使用普通召唤实体、命令相机和软件渲染。命簿实际投影／重投影、刷怪蛋真实点击、
全部组合的完整战斗动作、玩家审美与手感、FPS、音效和多人体验保持 `not_verified`。
身份选择有逻辑测试，但不把它或命令采集称为玩家映射输入验收。
