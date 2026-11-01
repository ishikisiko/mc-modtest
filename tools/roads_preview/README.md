# 驿道外观选样

仅生成离线三维样段，用于选择道路外观方向；不修改 `config/roads/prototype.json`、运行时铺路器、游戏资源或玩家世界，不启动 Minecraft，不升级 mod。

```bash
python3 tools/roads_preview/build.py --out reports/road-style-samples
```

`styles.json` 是四个候选及样段尺寸的 authored 输入，`build.py` 生成同一弯段与缓坡上的完整通行面、路肩和边坡几何。导出时检查全路幅覆盖及相邻通行列不超过半格高差。材质从现有生成资源读取，不手工修改纹理；`manifest.json` 记录来源摘要。

`preview.html` 为 T3 内嵌版本，`index.html` 为独立页面；二者使用同一几何、内嵌纹理和 Canvas 纹理面渲染器，可旋转／缩放，不依赖 WebGL 或新服务器。所有对外链接使用 `https://assets.ishiki333hhh.win/`。导出内容置于忽略目录，不提交生成 HTML。

这些检查仅证明预览内部几何一致，不代表原生方块碰撞、材质注册或玩家可行走验收。C 明确为三格山地支路，不能沿用此前五格原型的验收结论。样式选定后再接入拥有正式输出的驿道配置和铺路器，并检查相应游戏行为。
