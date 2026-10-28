# 五境十五套 Minecraft 风格锭与防具

方头、宽臂、硬边衣甲与真实像素材质。允许局部分片、鳞片、叠甲和护肩增强层次，整套不能全部由分片组成。旧的非MC稿已被用户否决，旧画面和旧包覆检查不作为本版证据。

作者和接口见 [MC_BRIEF.md](MC_BRIEF.md)、[CONTRACT.md](CONTRACT.md)。当前只交付HTML设计与模型源，不实装游戏，不合并main。

## 生成与最小检查

```bash
python3 art/immortal-armor-atlas/authors/minecraft.py
python3 art/immortal-armor-atlas/authors/minecraft.py --check --engine-check
node art/immortal-armor-atlas/core/test/test_engine.js
```

可以使用 --levels 1,2 只生成指定境界。旧levelN.py入口仍可使用。

## 预览与导出

```bash
node art/immortal-armor-atlas/core/build.js --output reports/immortal-armor-atlas/minecraft/all15.html
node art/immortal-armor-atlas/capture.js --file reports/immortal-armor-atlas/minecraft/all15.html --out reports/immortal-armor-atlas/minecraft/all15-capture --expected 15
node art/immortal-armor-atlas/delivery.js --out out/preview/immortal-armor-atlas-mc-20261028 --thumbnails reports/immortal-armor-atlas/minecraft/all15-capture/thumbnails-inline.json
node art/immortal-armor-atlas/checks/interaction.js out/preview/immortal-armor-atlas-mc-20261028/atlas-inline.html reports/immortal-armor-atlas/minecraft/interaction
```

index.html可直接离线打开；atlas-inline.html为同内容压缩封装。每套导出整套、四件、锭共6份OBJ，15套合计90份；MTL与16×16 PNG贴图完整打包。source目录保留可复跑作者和内核。实际交互和采集只证明所记录来源，不将历史不同来源拼接成通过。
