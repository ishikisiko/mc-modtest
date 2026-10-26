# 功法活卷原画

四张源图来自用户临时认可的活卷预览；`prompts.txt` 保留内置 imagegen 的生成提示词。青木与乙木有意共享根干轮廓，花脉与动效区分后篇。

修改源图或 `style.json` 后执行：

```bash
python tools/gen_technique_scroll_assets.py
python tools/gen_technique_scroll_assets.py --check
```

需要 Pillow。仅 `src/main/resources/assets/myvillage/textures/gui/cultivation/technique_scroll/` 的导出结果进入 jar；不得手改导出图。导出尺寸、调色板与字节上限以 `style.json` 为准，Java 只消费纹理，不复制这些导出参数。

原画以 3:2 显示并在 H 正文内居中，标题、参悟签、进度和按钮均由原生界面绘制。服务器规则不由图案、颜色或动效时间推断。
