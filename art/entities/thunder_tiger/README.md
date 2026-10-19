# 雷角山君美术源

用户确认方向：灰白底、黑色虎纹、厚重虎形肩胸与前爪、宽额短吻、额心单角，青蓝色仅用于有限雷痕。此处为可编辑输入和 UV 真相；运行时文件由 `tools/beastgen/defs/thunder_tiger.py` 生成，不手改。

- **可编辑几何与原创动画源**：`tools/beastgen/defs/thunder_tiger.py`，原版 ModelPart cuboid 管线；没有伪造 Blockbench 文件或新增 GeckoLib 依赖。
- **已采用原生材质参考**：`material_tones.png` 和对应的 `material_faces.json`。前者来源于内置 imagegen 材质候选，经二值 mask 回贴、最近邻降采样、调色板限制和原生面细节修正。按 cube.face 的归一化局部 UV 转移材质色阶，几何调整后不会沿用过时 atlas 坐标。
- **确定性原生细节**：生成器中的 model-space 虎纹和 `_detail()`；负责条纹跨面连续、眼鼻、眉、嘴、耳、爪趾、雷角。生成图把脸画在躯干 UV 的区域已拒绝，不直接作为 atlas。
- **当前 UV 真相**：`uv_truth.json`、`texture_faces.json`、`texture_regions.json`、填充型 `uv_islands_mask.png`；由实际 cuboid 逐面导出。
- **生成的原生纹理副本**：`source_texture.png`，与运行时基础纹理逐像素一致，纳入 build --check。
- **可追溯信息**：`provenance.json`、概念与 atlas prompt、`material_adoption.json`。临时概念、raw 候选和开发预览保留于忽略的 `out/preview/thunder-tiger/`；真实模型六视图/动作页位于 `out/preview/thunder_tiger/`。

```bash
.venv-entity-art/bin/python -m tools.beastgen.defs.thunder_tiger
python3 -m tools.beastgen build thunder_tiger
python3 -m tools.beastgen build thunder_tiger --check
/usr/bin/python3 -m unittest tools.tests.test_thunder_tiger_art tools.tests.test_beastgen
.venv-preview/bin/python -m tools.beastgen preview thunder_tiger --only views,sheets
```

骨骼 `horn` 是结构根；`horn_base` 保留断角残根，`horn_tip` 根据服务器持久状态隐藏。`horn_exposed` 的头部降低量从 boss 数据的真实 head PartEntity 中心差计算。四招时间严格读取服务器 move 数据，扑击落地时间从共享运动模型推导，不把实体位移重复写入动画。第二阶段只叠加有限躯干裂纹发光层。

真实模型是约 6 格长、肩高 3 格，另有分节尾巴；原版基础碰撞箱 2.8×3.0 与真实 body/head PartEntity 共用同一运行时数据。四肢、角尖和尾巴有常规视觉外伸，不伪称每个表面都拥有独立碰撞体。

开发侧候选审查已完成；玩家最终外观、手感、音效、FPS 和多人观感仍为 `human_review_pending` / `not_verified`。离线渲染不替代游戏内验证。
