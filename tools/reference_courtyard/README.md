# 月洞水院：独立体素模型

按用户提供的三张庭院参考图，手工编排细体素结构。此目录不属于
`buildgen` settlement family，不写入模组资源，不替换游戏存档。

包含临水木桥、带连续扶手的台阶、抬高门亭、后方主屋、白色曲线山墙、
灰瓦与凸起瓦垄、月洞门、扇形墙面浮雕、八角格窗、连续圆门回廊和侧室。
无植被。背面与房间连接为参考未展示部分的补全，不宣称逐尺寸测绘。

## 生成

在仓库根目录（或源文件 ZIP 解压根目录）执行：

```sh
python3 tools/reference_courtyard/build.py
```

仅使用 Python 标准库。生成到 `out/preview/reference-courtyard/`：

- `courtyard.vox`：MagicaVoxel 150，真实占用体素和调色板，可编辑。
- `courtyard.glb`：glTF 2.0 二进制模型，按构件/材质拆分的表面网格。
- `index.html`：自包含的 Three.js 离线查看器；六个视角、去顶与隐藏水面、PNG 导出。
- `source.zip`：建模脚本、唯一配置、查看器与已有 Three.js/OrbitControls 依赖。
- `manifest.json`：模型数量、导出哈希和结构检查结果。

`.vox` 使用 Z 向上；`.glb` 使用 Y 向上，单位为米。两种导出共享体素占用数据，
每体素 0.25 米。作者坐标 X 在导出时反射，以使前方视角中的门亭在左、月洞门在右。
这不是一体素对应一米方块的 Minecraft NBT/SCHEM，不能直接用结构方块导入。
VOX 水材质保存为调色板颜色，GLB/查看器另有透明水面表现。

`model.json` 是尺寸、布局、材质与预设相机的配置来源；`build.py` 实现体素构件。
不要手改生成文件来修正模型。模型尺度为 48 × 36 米的场地，包围盒高度上限 24 米。

## 检查和截图

生成时进行 VOX 全体素回读、GLB buffer/index/法线朝向验证、门洞净空检查，
并以 1.75 米头部净空、0.25 米高差检查桥、门亭、主屋、月洞门、回廊和侧室的地面连通。
这是离线几何检查，未验证游戏碰撞或玩家手感。

可选截图：先将输出目录通过已有 HTTP 服务发布，再执行：

```sh
PLAYWRIGHT_CORE=/path/to/playwright-core \
CHROMIUM_EXECUTABLE=/path/to/chrome \
node tools/reference_courtyard/capture.mjs \
  out/preview/reference-courtyard http://127.0.0.1:8766/reference-courtyard/
```

该命令读取实际模型，在六个预设视角截图，检查页面错误、屋顶/水面开关和手机横向溢出。
不启动 Minecraft。外观最终验收、游戏集成、FPS 与玩家游玩仍为 `not_verified`。
