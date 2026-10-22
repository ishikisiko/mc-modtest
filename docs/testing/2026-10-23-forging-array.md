# 0.83.0 玄枢炼器鼎交付证据

## 实现与固定源

新增独立多方块炼器鼎，保留旧鼎、锻台和既有物品 ID。服务器持有共享炉次、原器物、冻结成品和精确材料暂存；手动开炉，材料来源及品质规则复用现有炼灵。玩法说明见 [多方块炼器鼎](../features/multiblock-forging.md)。

最终 Java / 打包资源源提交：`14fc8e55b8eb7d0a03f20779abfdd9e2e861b36e`。随后 `d8e1032d` 与 `6db3ea45` 只改采集等待、镜头、HUD 与展示用器物；交付说明另补普通放置提示，不影响 jar。同步清单逐文件哈希校验，复用原 `/content/locked-sword` 工作目录和 Gradle 缓存。

本次按用户明确要求复用 Colab `sword-arts-highmem`（CPU / High-RAM），未启动本机 Minecraft。所有云端工作完成、产物下载后，按用户追加要求执行 `colab stop -s sword-arts-highmem`，服务端 `colab sessions` 返回无活动会话；VM 已释放，游戏进程也均已确认退出。

## 必要检查

- 本地资源、结构与采集器：29 项 Python 检查通过，生成器 `--check` 通过。结构碰撞、四向坐标、生成漂移、精确物品/材料解析均有针对性覆盖。
- Colab 最终构建：10 套、52 项炼器相关 Java 测试通过，零失败、错误、跳过；`jar -x generateAllStructures` 完成。
- 运行时使用的 1,441 个类与 3,377 个资源逐一对照交付 jar，一致，零差异。
- 未运行无关世界生成、战斗全回归或完整 release gate。

## 映射输入场景

`tools/forging_array_evidence.py` 使用真实客户端鼠标/键盘完成插入、开炉、撤炉、取物、拆除和修补。命令仅用于独立测试世界、资质/材料 fixture、测试时钟及权威读回。以下为多次独立运行的合并结果，不将失败尝试改写为通过：

| 场景 | 完成证据 | 观察结果 |
|---|---|---|
| structure | runtime-1 | 四朝向识别、缺格拒绝、修复 fixture 成形；结构规则未在后续改动 |
| infusion | runtime-2 | 真实材料预留、一次出炉、品质 4、名字/损伤/淬炼保留、已炼灵拒绝、缺料原子拒绝 |
| lifecycle | runtime-3 | 映射拆壁暂停；真实停服重启保留进度/快照/材料/作者；映射补壁续炼；拆枢与撤炉精确退款；实际卸载区块后恢复无离线追赶 |
| two_players | runtime-4 | 两个真实客户端，输入/专长/重复开炉锁定，非作者撤炉拒绝；旁观显示同一器物，完成物品只领取一次 |
| visual | runtime-4、runtime-5 | 冷/热、昼/夜、四朝向、中文菜单和缺格蓝图；精铁镐与青锋剑实际展示；运行短片及独立存档 |

合并 `selected = completed = [structure, infusion, lifecycle, two_players, visual]`，`unrun = []`。所有尝试均记录 `termination=confirmed`。`runtime-5` 是视觉补拍，不代替新的全玩法回归。

## 保留的问题与修复

1. 离线结构检查发现铜边、耳座粗碰撞侵入留空格；在 authored 网格中区分装饰与实体体积，重新生成后通过。
2. 静态守恒审查发现载入缺失/多余暂存材料未拒绝；补足额且无剩余的校验，进入可回收状态，新增两项测试。
3. runtime-1：采集器将中文物品名二次转换为 JSON 后送入 SNBT 解析器而失败；改为直接验证已解析栈，补中文转义回归。
4. runtime-2：部件默认交互拦住 BlockItem 的正常修补；改为手持方块优先放置。runtime-3 通过真实放置复验。
5. runtime-3：刻意降到 2 TPS 的双人 fixture 中首次菜单按钮点击未生效；补初始 DataSlots 同步等待，runtime-4 通过。
6. 首版视觉镜头过远、有聊天提示；runtime-5 仅补拍隐藏 HUD 的近景与青锋剑展示，不改玩法代码。

原始尝试、日志、脚本和清单保留于 `reports/forging-array-colab/artifacts/`，首次下载归档 `reports/forging-array-colab/delivery.tgz`。导出时 VM 尚保留；最终关闭状态独立记录于 `colab-termination.json`，最终 `delivery.json` 已更新。

## 产物与视觉检查

- [实机预览与下载](https://assets.ishiki333hhh.win/forging_array/)
- [myvillage-0.83.0.jar](https://assets.ishiki333hhh.win/forging_array/myvillage-0.83.0.jar)
- [独立展示存档](https://assets.ishiki333hhh.win/forging_array/forging_array_preview_world.zip)
- [交付清单](https://assets.ishiki333hhh.win/forging_array/delivery.json)、[哈希](https://assets.ishiki333hhh.win/forging_array/SHA256SUMS)

Jar SHA-256：`9e2c467972d675c7fd31a53ad4a85d583363b968cb1fb9f6da29de18ef08553d`。

已查看四朝向代表画面、冷/热及昼/夜画面、中文炼灵与缺格界面、第二客户端物品显示，并抽查 3.025 秒 H.264 短片首尾帧。观察到轮廓完整、状态表现切换、法环与实际物品转动；未观察到本次画面的缺件穿帮或文字越界。视频无音轨。

开发者检查不替代玩家审美、手感、音效、实机 FPS、长期平衡或广泛多人；这些仍为 `not_verified`。展示存档独立导出，未修改用户旧存档；未安装到用户电脑或自动启动其游戏。
