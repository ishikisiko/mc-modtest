# 五境十五套锭与防具设计

独立的修仙/玄幻视觉设计册，不进入游戏资源或注册。
规则为：整套不能全部以分片堆砌，小部分分片可提高层次感；横向纹理接缝允许。

- `levels/level1.json` 至 `level5.json`：十五套真实几何与完整中文设计。
- `authors/level1.py` 至 `level5.py`：唯一作者来源及可复跑生成器。
- `core/`：曲面建模、真实人体、材质、离线 HTML 和 OBJ 导出。
- `delivery.js`：整合十五套、四件及锭的90份 OBJ、MTL、说明和离线设计册。
- `capture.js`：从实际页面采集同光照前侧、背侧、锭和缩略图，记录来源。
- `checks/fit.js`：修复肩部／髋部露点与背裳层序的固定位置回归检查。
- `reviews/`：独立审美复核。技术通过不替代审美认可。

总纲见 [BRIEF.md](BRIEF.md)，几何接口见 [CONTRACT.md](CONTRACT.md)。本任务只提交设计分支，不合并 main。

## 构建与交付

```bash
node art/immortal-armor-atlas/core/build.js --output reports/immortal-armor-atlas/atlas.html
node art/immortal-armor-atlas/capture.js --file reports/immortal-armor-atlas/atlas.html --out reports/immortal-armor-atlas/capture --expected 15
node art/immortal-armor-atlas/delivery.js --out out/preview/immortal-armor-atlas --thumbnails reports/immortal-armor-atlas/capture/thumbnails-inline.json
```

截图使用本机已有的预览浏览器，不启动服务器。设计册完全离线；最终内联预览仍经 html_preview / html_render。
`index.html` 可直接打开；`atlas-inline.html` 是同内容的压缩包，适合内联交付。模型 JSON 保存完整物理材质参数，OBJ/MTL保存真实几何和通用材质。

未验证：用户审美认可、Minecraft适配、实际设备FPS和游戏行为。这是设计交付，不是可安装mod。
