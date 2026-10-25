# 新修士外观入口统一与命簿原生验证

版本 `0.91.5`，最终固定源 `59a2d02ee7bf019c66abba7e571693992a1f396b`。
分支 `feature/female-npc-skin-rebuild`，未合并 main。

[游戏原生画面与测试包](https://assets.ishiki333hhh.win/npc-appearance-0.91.5/) ·
[下载 myvillage-0.91.5.jar](https://assets.ishiki333hhh.win/npc-appearance-0.91.5/myvillage-0.91.5.jar) ·
[逐项原生检查](https://assets.ishiki333hhh.win/npc-appearance-0.91.5/native-checks.json)

## 统一范围

- 新修士的普通召唤、刷怪蛋与命簿投影均使用保留的 100 个男修／72 个女修组合。
- 高境界女修不再回退 `f_adept`；女性所有境界稳定选同一组合。性别缺失时选择稳定男修组合。
- `appearance_rules.json` 是普通出生族权重来源，经 npcgen 写入同一运行时目录；保留原男女一比二比例。
- 入世事件补齐没有经过 finalizeSpawn 的创建入口；已有存档与显式 Look 不重抽，保存时包括旧 default 在内都明确写入 Look。
- 所有已通过的模型、贴图、角色图与动画保持；普通实体配色仍为原配色或显式 Colours，命簿配色仍随人物肖像。

## 最终验证

运行 `reports/modal-runs/20261025T212343Z-fb5c7cba`，preset `npc-appearance-runtime`。
生成检查、Java 测试、原生场景和产物核对均属于此固定源；未拼接前两轮通过项。

| 检查 | 实际结果 |
|---|---|
| 本地工具／规则回归 | 48 项通过，`reports/npc-appearance-unification/focused-tests-r4.log` |
| Java | 384 项通过，0 失败／错误／跳过；含 12 项 NPC 战斗账本检查 |
| 普通命令召唤 | 12 个未指定 Look 的新实体，全部属于保留组合 |
| 刷怪蛋 | 6 次真实鼠标右键，产物 Look 均来自生成池；fixture 未注入 Look 或 Colours |
| 显式外观兼容 | 三个旧 Look 与两个新组合均保持指定外观和 Colours |
| 真实命簿 | 11 个实际宗门人物，包括筑基／金丹女修及男修；Look 与人物身份选择一致 |
| 撤回重投影 | 实际站位区块保持加载，服务器日志确认撤回；新实体 UUID 改变，人物 Look／Colours 保持 |
| 保存重进 | 23 个普通／蛋生／显式外观实体的 UUID、Look、Colours 保持；命簿新实体恢复相同外观 |
| 命簿完整记录 | 35 名人物完整 JSON 一致，包括此前失败的 injurer 字段 |
| 原生画面 | 六张静帧来自本次运行，已查看普通召唤、刷怪蛋及命簿男女修 |
| jar | 704 份 NPC 资源与目录匹配正式输出，1629 个类文件匹配实际运行；阡镇启动清单存在 |
| 云端清理 | termination=`confirmed` |

原生 `selected = completed = ["command_spawn", "mapped_spawn_egg", "explicit_look_compatibility", "ledger_projection", "ledger_reprojection", "save_reload"]`，
`unrun = []`；全部 128 项 checks 为 true。

## 两轮失败与修复

1. `20261025T205353Z-545ade5a`，源 `2629bcc513fa2205e53a3d5ea2352fb56ad1083c`：前四场景完成。
   撤回重现后 UUID 未变。加载范围只覆盖山门锚点附近，而真实 NPC 站在北侧范围外；短暂查不到实体是区块卸载，服务器没有相应宗门撤回记录。
   修正为按实际化身坐标保活，逐点核对加载并等待新出现的服务器撤回日志。UUID 必须变化的断言保留。
2. `20261025T210800Z-867b5c6e`，源 `0bf35fbd`：前五场景完成，保存重进的 Look／Colours／UUID 检查通过，
   最后一项完整人物记录比对失败。人物 132 的 injurer 从 8 变成 -1，其他字段、人物数与模拟日均未变。
   根因为既有 `NpcCombatLedger.checkpoint` 在纯身体快照同步时以空 lastAttacker 清除了历史归属。
   最小修复只在生命值／最大生命值或攻击回执变化时更新致伤者；新增完整人物记录保持及真实伤害归属回归，原生完整记录比对未放宽。

两轮原始失败日志、初末命簿 JSON 和截图均保留，环境均已确认销毁。
第 3 轮重新从固定源完整执行六项场景。新增持久化修复没有改动攻击时序、伤害公式或命簿格式。

## 交付与边界

测试包 `27,408,408` 字节，SHA-256：`1d3dc3758e47c32d0cee9be562310b2798faacf81ff2544f83fdd27e0ec171e9`。
[机器核对记录](https://assets.ishiki333hhh.win/npc-appearance-0.91.5/verification.json)。

这是单客户端开发者原生验证。命令负责准备、读回与相机，刷怪蛋动作来自实际鼠标输入；命簿通过正常 facade 创建和撤回，未改写存档 NBT 制造人物。
真实投影采集使用宗门人物；城镇／野外共用入口，但本轮没有分别重跑这些完整玩法。
已有旧 Look 的兼容通过显式 Look 与重启覆盖，未额外回放极旧缺失 Look 字段的真实历史存档。
玩家审美、手感、FPS、音效及多人保持 `not_verified`；本轮没有重新验收完整 NPC 对战。
