# Boss 血条字体修正 · 0.69.1-fix1

用户否定 0.69.1 的默认像素字体。本次沿用 A「朱砂横锋」，改为马善政毛笔标题、站酷小薇宋体信息行／数字；增大标题并增加上下留白，原版硬像素字影改为轻微偏移字影。仅 Boss HUD 选择新字体，不覆盖其他界面的默认字体；B/C 设计稿保留。

字体与舆图共用，TTF 及 OFL 文件内容未修改。资产移至 `assets/myvillage/font/`，说明／许可证名称使用小写以符合资源路径规则，`AtlasInkPainter` 同步读取新目录。两个 TTF provider 使用三倍采样，缺字仍有原版回退；测宽与截断使用对应字体 Style。

## 云端证据

| 轮次 | 固定源 | 结果 |
|---|---|---|
| `20261019T192254Z-2784bfe2` | `e0fe473ef28d53bdc12ae853941f787b20d3451b` | 构建与 6 Java 检查通过；采集准备失败，无字体截图；`termination=confirmed` |
| `20261019T193241Z-7b5a20f9` | `83efaac8cda75b359d2991fe8ca3d2e8eb5641d2` | 字体专项重采通过，4 项准备／显示检查、5 张 `stable=true` 截图；`selected=completed=[typography]`、`unrun=[]`；`termination=confirmed` |
| `20261019T194015Z-d956514c` | `c053eb630866f842d877a1f5953c07b3324d7f6e` | 最终完整资源 jar 与 6 Java 检查通过；字体许可证路径改为小写并增加打包检查；`termination=confirmed` |

第一轮固定等待两秒后铺设平台，服务器返回 `That position is not loaded`；随后立即解析 Boss 查询时返回 `No entity was found`。失败日志保留，没有将它当成字体渲染失败。修复为强制加载独立平台的 25 个区块、逐块等待就绪、断言铺地成功，再等待 Boss 可查询。清理时移除强制加载；本地新增两项回归，连同已有像素判定和 runner 用例共 30 项通过。

实机检查了普通气血、狂怒／断角、长中文名、长英文名和灵力 HUD 共存。检查员查看原始截图确认字形已切换、文字未覆盖血条／印章、信息行及百分比可辨。当前长名称样本仍可容纳，没有用这些截图宣称所有超长名称、所有语言或 GUI 尺寸都已验收。灵力共存帧保留长英文自定义名称。

## 交付身份

- [实机新旧对比与下载](http://43.156.135.198:8765/bossbar-fonts/)
- `myvillage-0.69.1-fix1.jar`，14,506,717 字节。
- SHA-256：`2b472b6b80e7f0aab35fc33135de1af7a6dd3b88fbf5286ce4b188f662062e76`。
- 最终 jar 的 1221 个生产类均与重采运行时哈希相同；TTF 与 provider JSON 相同。重采后仅调整许可证／说明文件的路径及检查工具，未改变字体显示代码。
- 与 0.69.1 对比，生产类仅 `CinnabarBossBar`、`BossBarLayout`、`AtlasInkPainter` 改变。服务器血量／阶段／观察者逻辑不变，本次未重复战斗与生命周期验收。
- jar 内仅两份 TTF，保留原始字体许可；273 个 worldplan NBT tile、V1–V3 冻结基线逐项哈希检查通过。
- 原始日志与报告位于对应 `reports/modal-runs/<run>/artifacts/`，不提交；交付副本在 `build/libs/` 和 `out/preview/bossbar-fonts/`。

以上是云端真实客户端的静态展示检查。玩家字体审美、正常战斗时的可读性、所有 GUI 缩放与 FPS 仍为 `not_verified`。没有启动所有者电脑的游戏或更换其存档。
