# 炼器系统交互原型

这是独立的 HTML 设计原型，不进入模组 JAR，也不修改 Minecraft 存档。系统关系来自当前设计讨论，所有数值、工艺与地点仍为试玩样例。

页面提供装备详情、淬炼、升阶、器性改造、修补与分解、打造、委托、探索获取、器谱研读、炼器经验、工位升级、材料交易、操作记录和浏览器进度保存。三种演示起点覆盖入门与后期流程。

规则集中在 `core.js`，页面消费同一份报价与结算结果。`app.js` 和 `style.css` 负责交互与布局。修改 authored 文件后重新打包，不手改交付的单文件 HTML。

```bash
node tools/forging_system_preview/test_core.cjs
python3 tools/forging_system_preview/build.py --out reports/forging-system/index.html
```

页面无需依赖包或外部图片，可直接打开单文件。T3 展示使用同一份完整 HTML，经 `html_preview` 检查后交给 `html_render`。在线交付路径为 `https://assets.ishiki333hhh.win/forging-system/`，复用现有服务。

浏览器检查可使用 `check_browser.py --html <绝对HTML路径> --out <证据目录>`。需要已安装 Playwright 与 Chromium；检查用本地文件，不启动服务器。自动检查不代替用户对设计、美术或实际游戏手感的判断。
