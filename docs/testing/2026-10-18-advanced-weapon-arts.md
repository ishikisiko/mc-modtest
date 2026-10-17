# 0.62.0 进阶武技分支合并测试

范围：`origin/feat/advanced-weapon-arts-20261017`（`4193b79`）与当前
`main`（`55dd66b`）的集成版本。原分支落后 main 17 个提交，不能用原分支结果替代合并结果。

## 集成与本地检查

- 两份语言文件从 main 恢复后由 `tools/gen_advanced_arts.py` 重新生成，保留近期天命文案。
- CombatDamageService 保留 main 的派生命中归因与天命隔离，武技额外传入径向击退朝向。
  普通攻击和派生波仍使用 session 朝向；没有增加第二条伤害路径。
- 版本同步为 0.62.0，原有未跟踪文件保持原样。
- 两个生成器 `--check` 通过；轻量 JDK 纯类检查 14965 个断言、19 份 Java 语法解析通过。
  语法检查不是 NeoForge 类型检查。
- 本地 `test_advanced_arts / test_combat_data / test_gen_technique_catalogue / test_modal_runner`
  共 102 项通过。首次失败来自过时测试基线：main 已加入两种天命派生剑气，合计三种；
  将“只有一种剑气”改为三个精确 ID，并逐项断言八条错误明细，未放宽生产校验。

## Modal 构建

- 固定源：`bb966762b16c31cb79d673697c09fa670eb21ca9`。
- run：`20261017T160042Z-4c9e1cda`，preset `advanced-arts-build`。
- 33 份 JUnit XML，共 **200 tests，0 failures，0 errors，0 skipped**。
- 覆盖武技、战斗 session/network/runtime、天命、AdvancedArtClientStateTest 与 ClientCombatStateTest。
- NeoForge Java 编译、jar 打包成功。Gradle 38.838 秒；远端命令共 41.216 秒；
  创建 2.455 秒、上传 13.559 秒、下载 3.523 秒、终止 0.503 秒、控制器总计 61.255 秒。
- artifacts `downloaded_and_verified`，Sandbox termination `confirmed`。
- 仅有 EventBusSubscriber 既有用法相关的弃用警告，不是构建失败。

## 实机运行与重跑

1. `20261017T160250Z-52aeda1a`，固定源 `9bf143c`：服务器/客户端启动、未学习拒绝通过。
   测试脚本把二品书写成不存在的 `manual_active_2`，导致研读等待超时。
   这是夹具错误；修复为注册表中的 `manual_active_xuan`，且立即检查放入命令是否成功。
   日志与失败报告保留，artifacts 校验完成，termination `confirmed`。
   构建 30.888 秒，实机脚本 173.821 秒，控制器远端阶段 206.662 秒。

2. `20261017T160652Z-ef265ceb`，固定源 `200f1126aa3a3a1a33a4df3b9157bfc712c5d7cb`：
   **56/56 检查通过**，四组 selected 全部 completed，unrun 为空。
   六本技能书从零研读、六招 Z/C 命中/完成/单次扣费/冷却、未学/低境界/低灵力拒绝、
   墙后和友军保护、正面守势减伤且不中断、换武器中断且保留冷却、双客户端友伤保护、
   重启保留六招学习状态、武技后普通攻击均有真实输入和服务器读回证据。
   守势入站伤害是 RCON 边界探针：4 点伤害实际损失 1.4 点；该项未独立量化反击倍率。
   构建 30.891 秒、实机 390.874 秒，其中首次服务器/客户端准备 56.074 秒，
   第二客户端准备 28.058 秒，重启准备 52.016 秒；这些嵌套阶段不能与总时长相加。
   控制器总计 443.123 秒，创建/上传/远端/下载/终止分别为
   2.444 / 13.169 / 423.412 / 3.091 / 1.006 秒。
   artifacts `downloaded_and_verified`，termination `confirmed`。
   与构建测试源 `bb96676` 的产品/构建/资源输入 diff 为空，沿用 200 项 JUnit 结果。

3. 人工检查上述截图发现快捷栏与中央动作提示重叠，`2178684` 只将 HUD 顶部从
   `guiHeight - 78` 上移到 `guiHeight - 120`，与动作提示留出间距；没有修改样式、战斗参数或权威逻辑。
   增加 `advanced-arts-hud` 短探针，使用管理员学习夹具与映射 Z/C 重新截图。
   本地相关调度/数据测试 32 项通过。
   HUD run `20261017T161602Z-9d93285e` 固定源
   `2178684f2c6b1b2aa1e485ed279a980eef6f1ce3`，7/7 检查通过。
   已人工对照修复前后的 `huifeng_jianzhen.png`：960×540、GUI scale 2 下两行快捷栏
   与中央动作提示分离；这是局部布局确认，不是整体视觉接受。
   编译 31.369 秒、实机短探针 72.659 秒；控制器总计 125.536 秒。
   artifacts `downloaded_and_verified`，termination `confirmed`。
   与 56 项玩法通过版本的编译类哈希比较，仅 `ClientAdvancedArts.class` 改变；
   对应产品源码 diff 只有 HUD y 坐标一行，故不重复已通过的权威/生命周期测试。

## 交付与重现

最终 jar：`build/libs/myvillage-0.62.0.jar`。
SHA-256：`8f75123f0d619ef335b66624ac384531698c3cd0249a14d9d812ade2290f8bf1`。
最终说明提交仅涉及 CHANGELOG 和非打包文档，使用上面的已验证 jar。

所有运行的固定源归档、request/state、日志、JUnit XML、截图及实机 evidence.json 保留在
`reports/modal-runs/<run-id>/`（忽略产物，不进入 Git）；四个 Sandbox 均已确认销毁。

```bash
python -m tools.modal_runner run --preset advanced-arts-build --ref 2178684 --timeout 1800
python -m tools.modal_runner run --preset advanced-arts-runtime --ref 2178684 --timeout 2400
python -m tools.modal_runner run --preset advanced-arts-hud --ref 2178684 --timeout 1200
```

以上为各自范围的重现命令，不要求每次全部运行。

## 证据与接受边界

所有重型工作在 Modal；实际客户端通过 Xvfb/xdotool 输入，无 DevBridge、无本机游戏启动。
软件渲染、命令夹具和有限双客户端检查不等于玩家画面、手感、平衡、音效、FPS 或广泛多人认可。

未单独观察项保持 `not_verified`：多目标逐段去重、环形击退朝向、突进崖边、
背面/过期格挡、死亡/重连/跨维度的非零冷却、多人视效同步及玩家体验。
技能书尚未接入掉落/商店/宗门奖励；本次书由命令准备，研读与施放由真实输入完成。
