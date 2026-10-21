# 三档聚灵阵：0.74.0 交付验证

新增中品青玉莲座与上品紫金悬心，地基扩大至 9×9／11×11，中央生效区为 5×5／7×7。
原阵 7×7／3×3 和原 registry ID 保留。加成、燃料余能与使用方法见 [KB62](../ai-kb/62_spirit_gathering_array.md)。
重型工作依用户明确要求继续使用既有 Colab `alchemy-furnace-0722`；未运行本机 Gradle 或 Minecraft。

## 固定源与检查

- 产品及 Java 测试源：`93bd4a6cd57686167d24e5f355ba62d2c531759f`。
- 最终生产源：`6f4a72e19fbd4bba16b36a691d4a7280fa337edd`。
  后续产品变化仅为 `GatheringRenderer` 将上品八卦内移，避免四个正方向被阵枢底座遮挡。
  重新打包后逐 entry 比较，唯一变化为 `GatheringRenderer.class`；玩法测试结果复用。
- 采集器首轮 `17aaffb3`、双客户端与画面补采 `1078e2ce`、服务器补齐展示世界 `774b2cda`。
  这些提交只改变 `tools/gathering_tiers_evidence.py`，不改变生产源码或资源。
- 远程工程复用 `/content/spirit-stones-0731`。最终源清单核对 4,115 个文件，0 差异。
  Gradle 缓存和与输入匹配的世界资源保持可复用，未省略实际打包依赖。
- 本地 `tools.tests.test_gathering` 与 `tools.tests.test_spirit_stone_resources` 共 19 项通过；
  聚灵阵生成器、灵石资源和物品资源检查通过。采集器语法、帮助入口与 21 处 check 调用参数数目检查通过。
- Colab Gradle 范围为 `gathering.*`、`cultivation.meditation.*`、`spiritstone.*`：
  13 套件、97 项测试通过，0 failures/errors/skipped；首轮编译测试及 jar 为 29.31 秒，最终表现修正打包 15.35 秒。
- 最终 jar 的 2,853 项资源（含生成世界资源）和全部生产 class 与实际运行目录逐字节一致。
  下载后再次核对本地 2,570 项 authored 资源，0 差异。

## 实机场景与复用范围

| 范围 | 固定采集源 | 结果与证据 |
|---|---|---|
| structure、matrix、switch、legacy_restart | `17aaffb3` | 四项 completed；结构、六种合法燃料组合、三种非法组合、收益区边界、换料余能、旧档迁移与重启均通过。随后第二客户端登录失败，整轮保持 fail；含清理共 52 项已执行断言通过。 |
| two_players、三档实际运转画面 | `1078e2ce`，最终生产源 `6f4a72e1` | 双客户端 completed；三档运转、近景、菜单、总览和短片均已完成，含客端清理共 5 项断言通过。末尾展示物资准备的断言调用参数遗漏，整轮保持 fail，原 visual 未标记 completed。 |
| 展示世界准备与导出 | `774b2cda --finalize-preview` | 只启动服务器，复用上述完整画面，未重做或冒充映射输入；确认三座阵心、两座入门石碑，补齐并逐槽读取验证 11 格物资，保存、停服并导出，status=pass，41.27 秒。 |

汇总完成六类范围，57 项已执行实机断言全部通过，另有服务器展示准备核验。
`delivery.json` 明确记为 `pass_with_documented_scope_reuse`，不把任一失败轮改写成整轮成功。
首轮、补采、补齐记录独立保留，原 selected/completed/unrun 和失败信息保持原样。

关键观察：

- 真实键鼠放置最后一座阵枢；通过实际右键菜单、Shift 点击、启阵按钮和 V／B／X 完成输入。
  RCON 只负责 fixture、取景与状态读回，不用管理员命令代替修炼操作。
- 原阵下品 +25%；中品阵下品／中品 +30%／+40%；上品阵下品／中品／上品 +35%／+50%／+60%。
  六种组合均核对实际修为、阵能和 B 模式个人下品灵石费用。
- 各档扩大后的中央角落生效，外侧一格不扣阵能；地基、净空与同品级阵枢约束有效。
- 上品阵内下品旧余能仍按 +35%，待用上品显示 +60%；旧余能耗尽后才切换。
  反向换成下品也保留已购上品余能；同世界停服重启后，余能、品级和待用燃料一致。
- Version 1 旧阵 17 次余能迁移为下品，重启后仍为 17 次，无离线补扣。
- 两名真实客户端各获得 16 点修为，阵心恰好扣 2 次；扩大生效区中的第二玩家也受益。
- 三档实景、两档新阵枢近景和中文菜单已审阅；青玉莲座、紫金框架与悬心可辨，卦纹露出，菜单没有裁切。
  三段短片均为 960×540、4 秒、80 帧。编码帧率不代表玩家硬件 FPS。

## 保留的工具失败

1. 初次 Colab exec 出现 Connection lost，重试出现 HTTP read timeout；检查确认尚未启动构建后，
   改用 skill 支持的管道式 console 启动独立进程，复用同一会话和缓存。
2. 首轮第二客户端名 `GatheringTierGuest` 超过 Minecraft 的 16 字符上限；客户端日志为
   `String too big (was 18 characters, max 16)`。修正为 `GatheringGuest`，只补跑双客户端与画面。
3. 补采已完成全部截图／短片，但展示石碑检查遗漏 `detail` 参数，抛出 TypeError。
   修正调用并增加显式 `--finalize-preview`，只恢复展示准备与导出尾段；原失败记录不覆盖。

完整输入、XML、日志、两次失败证据、补齐及清理审计保存在忽略目录
`reports/gathering-tiers-colab/`。没有运行全量 release，也没有将历史其他场景视为本轮新增实测。

## 交付

- [实景、视频、jar、独立世界与验证文件](http://43.156.135.198:8765/gathering_tiers/)。
- `myvillage-0.74.0.jar`：18,269,842 字节。
- SHA-256：`8f79850066d11693f837c0033b90d9d7f11241a86c339887fb7217abd047ceec`。
- 独立世界有三座完整阵法、觉醒／传承石碑和补给箱；压缩包完整性通过，不覆盖旧存档。
- 所有游戏、Gradle、Xvfb 进程已退出；原采集器和第二客户端分别记录清理，最终服务器进程按 PID／启动时间复核，
  `termination=confirmed`。用户要求复用的 Colab 会话保留。
- 公共下载页、jar、世界、图片和 manifest 均验证 HTTP 200，复用现有正确服务。
- 玩家审美、长期平衡、手感、音效、原生硬件 FPS、自然获取完整流程和广泛多人体验保持 `not_verified`。

