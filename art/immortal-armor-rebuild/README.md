# 仙铸：五境十五套防具与灵材设计卷

本目录保存 2026-10-28 重新制作的十五套防具设计、建模源码、HTML 查看器模板与测试脚本。不是先前丢失原稿的恢复，也不覆盖旧的 `art/immortal-armor-atlas` 分支。

## 下载包与仓库的区别

完整离线交付已先于仓库写入提供给用户：`xianzhu-armor-atlas.html`（15,528,172 字节）和 `xianzhu-armor-complete.zip`（53,099,492 字节）。ZIP 包含已生成的 HTML、30 份 GLB、对应 OBJ/MTL、完整设计说明、同源模型渲染、源码和测试记录。交付校验值见 `DELIVERY_RECEIPT.json`。

**本分支提交可重建源码与 HTML 模板，不声称已上传大体积生成网页、模型和截图。** `src/viewer.html` 是构建模板，不是可直接单独打开的完成品。先使用下载包中的 `index.html`，或按下方命令生成本地 `index.html`。

## 五境十五套

| 境界 | 方向一 | 方向二 | 方向三 |
|---|---|---|---|
| 炼气 | 听澜：青泓灵铜 / 清逸水行 | 赤霄：丹霞灵铜 / 明艳火甲 | 照夜：玄曜精铁 / 冷峻夜衣 |
| 筑基 | 雪阙：霜魄灵银 / 霜宫礼衣 | 碧衡：琅玕玉钢 / 玉庭法袍 | 金乌：曜炎精金 / 日轮战甲 |
| 金丹 | 沧溟：海心蓝金 / 御潮仙衣 | 莲华：琉璃莲金 / 净化法衣 | 天律：紫宸雷金 / 雷霆法甲 |
| 元婴 | 太阴：月魄玄银 / 月宫仙裳 | 朱凰：涅槃赤金 / 凰仪神甲 | 玄冥：玄渊神铁 / 镇渊玄甲 |
| 化神 | 太初：混元仙金 / 庄严神衣 | 星阙：星髓天晶 / 星宫法服 | 无相：太虚道金 / 玄远道衣 |

每套的完整设计判断、材质、锭、四件部位、局部分片、法相与玩法方向保存在 `src/catalog.py`，并显示在生成后的 HTML 中。名称、材料与玩法方向是提案，不是已注册内容。

## 重建

在远程 Linux 工作环境中执行：

```bash
cd art/immortal-armor-rebuild
python -m pip install -r requirements.txt
python src/build.py
```

将生成独立离线 `index.html`、十五套 armor/ingot GLB、OBJ/MTL 和几何验证报告。首次构建无缩略图也可使用全部三维功能，导航显示名称占位，不会替换真实模型。

需要同源渲染缩略图与浏览器验证时，额外安装 Pillow、Playwright 和可用 Chromium；独立 GLB 回读另需 trimesh。脚本默认 Chromium 路径是 `/usr/bin/chromium`。

```bash
python tests/browser_check.py 0 15
python src/build.py --assemble-only
python tests/ui_check.py
python tests/portable_check.py
node --check src/viewer.js
node --check src/software.js
```

## 查看器与模型

整套、冠、胸衣、护腿、靴和锭可分别旋转、缩放、切换正侧背视角。支持材质、素模、剪影、线框、法相和人台开关、深浅棚光、同光同尺度比较，以及当前模型 GLB/OBJ 和 PNG 导出。模型是真实连续曲面，不是图片生成或静态图替代三维。导出不包含展示人台。

页面无外部脚本、字体或 CDN。优先使用 WebGL，不可用时自动使用同网格的 CPU 深度缓冲三维光栅器；软件模式可能较慢。浏览器需要支持 DecompressionStream。GLB 保留通用金属度、粗糙度、法线、UV、自发光和局部透明参数。

## 规则与边界

根 `AGENTS.md` 与 `docs/contracts/assets.md` 同步修订防具规则：主体保持完整，允许少量局部分片提高层次感；禁止全身拼甲或补丁式碎片化。锭、工具、武器规则未放宽，正式物品资源未修改。

本轮基于通过 GitHub 连接读取的 `main @ fdd3ba3e5227b7b543dfb29cfe5e8362beea6df5`，不是当前容器完整 clone。单独设计分支，不合并 main，不升模组版本，不构建 JAR。

这不是可安装模组。未完成 Minecraft 方块人比例、装备注册、配方、数值、骨骼绑定、图集烘焙、动作穿模、LOD、目标设备 FPS 验收。当前测试环境没有可用 WebGL，实际预览与浏览器测试使用 CPU 光栅器；GPU 路径未实机验证。技术检查不代表用户审美认可。完整验证边界见 `tests/VALIDATION.md`。
