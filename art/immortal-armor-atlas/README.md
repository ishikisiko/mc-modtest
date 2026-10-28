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
node art/immortal-armor-atlas/core/build.js --output reports/immortal-armor-atlas/ornament-v2/delivery-source.html
node art/immortal-armor-atlas/capture.js --file reports/immortal-armor-atlas/ornament-v2/delivery-source.html --out reports/immortal-armor-atlas/ornament-v2/delivery-capture --expected 15
node art/immortal-armor-atlas/delivery.js --out out/preview/immortal-armor-atlas-mc-ornaments-20261028 --thumbnails reports/immortal-armor-atlas/ornament-v2/delivery-capture/thumbnails-inline.json
node art/immortal-armor-atlas/checks/interaction.js out/preview/immortal-armor-atlas-mc-ornaments-20261028/atlas-inline.html reports/immortal-armor-atlas/ornament-v2/interaction
```

index.html可直接离线打开；atlas-inline.html为同内容压缩封装。每套导出整套、四件、锭共6份OBJ，15套合计90份；MTL与16/32像素 PNG贴图完整打包。source目录保留可复跑作者和内核。实际交互和采集只证明所记录来源，不将历史不同来源拼接成通过。

本轮纹样与星河/九色精修由作者输入 mc_ornaments.py 统一组合，不手改levels。星河改为连续披衣；九色采用菱花镶纹与切面冠。其余背纹及袖腰收口各有主辅图案。

太虚无相的后续精修由 `authors/mc_taixu.py` 经同一组合入口生成：缺角厚壁玉璧、真实内孔与穿璧云衣，配低位玉冠、寂青内襟及细银织边。仅修改该套穿戴端，其余十四套和十五种锭保持原样。局部再生成与检查使用 `python3 art/immortal-armor-atlas/authors/minecraft.py --levels 5 --engine-check`。

紫霄雷宸由 `authors/mc_zixiao.py` 重作整套穿戴端，保留雷府异袖理念与原锭。低伏折山冠、统一墨紫色域、实体收腕护臂、分襟和宽折披衣取代旧闪电冠、拼色袖和直条背牌。

十五套动态配置位于 `effects/catalogue.js`。预览提供动效开关与暂停，系统减少动态时默认关闭；灵材锭保持静态，OBJ始终导出静态原形。启动默认选中紫霄，也可用 `?design=huashen_taixu` 等参数指定款式。动态逻辑检查为 `node art/immortal-armor-atlas/core/test/test_effects.js`；实际页面检查为 `node art/immortal-armor-atlas/checks/dynamic.js HTML OUT LEVEL [--lifecycle]`，每个境界单独记录来源与完成数。`checks/record_effects.js HTML OUT` 录制紫霄及后两境的真实浏览器动态片段，不代表游戏实装或硬件性能。
