# 洲域与城镇入场报幕候选

本轮只制作样式预览，等待用户选择；未增加游戏事件、网络、触发范围或HUD运行代码，未启动Minecraft测试，也不升级mod版本。

四种方向为电影居中、左下角地区标签、右侧竖排题签、电影黑边字幕。洲域与城镇可以分别选择。预览支持中州/云山城切换、静帧、一次入场动画和HUD占位。时长为当前设计示意，尚未形成游戏规则。

背景来自已有中州千机阁实机截图，样例地名来自当前`capital_waystones/sites.json`中的“中州·云山城”。字幕只是候选叠加，不能作为区域报幕已经运行的证据。标题使用游戏现有马善政与站酷小薇宋体的字形轮廓；预览通过SVG图片保持字形，正式游戏接入时仍应使用现有字体渲染。

作者文件为`candidates.html`与`prepare_assets.py`。生成资源放在忽略的`reports/`，字体来源与原图SHA-256记录在`sources.json`，保留字体许可证。

```bash
uv run --with fonttools==4.60.1 python art/ui/region-entry/prepare_assets.py --out reports/region-entry-styles-20261114/public
```

预览资源发布到`https://assets.ishiki333hhh.win/previews/region-entry-styles-20261114/`，沿用现有资源服务。
