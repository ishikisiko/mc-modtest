# 0.67.0 素纸淡墨舆图

用户确认 SVG 素纸淡墨方向后接入原生界面。入口为 H → 天下 → 舆图，读取当前存档冻结规划，显示州名、州界、海陆和自身位置，支持缩放、拖动、归位和普通世界空态。

## 最终证据

- 产品与采集源：`edfc165445b1cb58fec467cd4049d9fbc07cf130`。
- Modal：`reports/modal-runs/20261018T172902Z-55cbee04/`，`atlas-runtime`，`status=passed`、`termination=confirmed`。
- Java **26/26**：WorldAtlasTest 4、WorldAtlasCodecTest 1、WorldSimPayloadCodecTest 9、WorldSimPayloadThrottleTest 3、CombatPayloadTest 9。包括冻结 V1/V3/V4 同源采样、数据边界、codec、视口变换与字体打包/绘制。
- 实机 selected/completed：`planned_map, zoom_pan_locate, ordinary_no_plan`，**3/3**，unrun 空。真实 960×540 客户端、中文、GUI scale 2；RCON 仅设置独立世界与旁观 fixture，H、点击、拖动均为映射输入。
- `ATLAS_READY` 与真实 V4 世界 plan hash 同为 `1cf664d70d2d6260ae9665d14d81c3c86093e39e941fb9a42541d81bbd991dca`，包含 5 个核心州域。
- 1.3× 后实际拖动由 `dx=-0.15, dy=-0.15` 变为 `dx=-0.0611727966689799, dy=-0.0789382373351839`；归位到 1.6×，览全图返回 1.0× / 0 / 0。采集器断言实际位移发生。
- 8 张稳定原始截图位于该 run 的 `artifacts/reports/worldplan-atlas/`。已人工式画面复审全图、缩放拖动及普通世界提示：透明混合后的图缘晕染、纸面字形和无规划提示均实际出现。
- 两个服务器正常停止，两个客户端及 Xvfb 均确认结束；Sandbox 已确认销毁。
- 本地工具检查：`tools.tests.test_modal_runner` 25 项、`tools.tests.test_world_atlas_capture` 1 项通过。后者确认菜单截图使用稳定原始帧，不禁用正常游戏输入检查。

交付 `myvillage-0.67.0.jar`，SHA-256：

```text
2a84073f707a4a1f9cc719f9acd67efda9e0c86b36cf6a1d88bb273b645e4e39
```

已核对舆图类、两套字体及其 OFL 许可证均在 jar 内。公开交付页为 `http://43.156.135.198:8765/worldplan-atlas/`，包括原始截图、jar、校验与采集摘要；不是网页模拟游戏效果。

## 保留的失败和画面复审

| Modal run | 结果与处置 |
|---|---|
| `20261018T165752Z-cf0f6bc6` | `e53e7a50` 编译发现 Button 默认旁白项访问级别问题，修正构造回调。 |
| `20261018T170355Z-d8b74a17` | `a5430ae1` 的 26 项 Java 与 jar 通过；采集器缺少报告目录，在游戏启动前退出，补目录初始化。 |
| `20261018T170827Z-49af87ec` | `f441f1a7` 真实客户端进入世界并打开 H；旧截图函数拒绝菜单画面，改用针对菜单的稳定原始帧采集。 |
| `20261018T171712Z-5771d15d` | `dbc14664` 采集器报告 3/3，但复审发现原生贴图未正确混合、比例尺字形不一致，且拖动日志无位移，因此未作为最终验收。修正混合/字形，延长映射拖动并加入位移断言。 |

上述 Sandbox 均 `termination=confirmed`。工具修复期间比对过旧两轮 jar 的 2,904 个文件条目，内容完全相同，因此当时复用局部产品检查；最终修正产品后重新运行了上述 26 项 Java 及原生场景，没有沿用旧画面通过结论。

## 验收边界

玩家审美、操作手感、长时间/硬件 FPS、双客户端、其他 GUI 尺寸和英文实机显示均为 `not_verified`。V1/V3/V4 是纯采样一致性检查，旧存档实际重开未选择；V2 未单独采样复验。异维度与超出图幅分支已实现，本轮未做对应客户端采集。软件 OpenGL 的稳定帧和开发者复审不代替玩家验收；SVG 样式确认也不等于原生画面最终认可。
