# Boss 血条候选

2026-10-20 用户选定 A「朱砂横锋」实装；B「宣纸古卷」、C「玄墨雷纹」保留为设计稿，未注册运行时皮肤。

在浏览器打开 `candidates.html` 查看三款原始交互预览（同目录 `reference.png` 为雷角山君已有实机截图，来自 `20261019T182923Z-119c0522` 的 `idle_q_front.png`；HTML 血条是概念叠加，不是已运行游戏证据）。候选保留原貌，实际游戏的 Minecraft 字体、GUI 缩放与灵力 HUD 避让以运行时画面为准。

A 实现入口：`CinnabarBossBar`；布局与受伤残影分别为 `BossBarLayout`、`BossBarTrail`。它们属于代码绘制 UI，不依赖贴图生成器。后续若选择 B/C，应先比较原始候选和当前实际画面再实施。
