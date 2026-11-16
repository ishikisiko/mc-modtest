# 木格双开大门设计

状态：`design_preview`。用户指出住宅模板中的单格原版门偏小，要求单独设计大门。本轮提供可旋转、开合及主屋局部试装的独立模型，未注册游戏方块，也未替换住宅蓝图。

[三维设计](https://assets.ishiki333hhh.win/previews/player-house-door-study-20261117/index.html)包含独立门体、同尺度原版门对照、主屋局部试装。可切换闭合、半开、全开，或者拖动开度滑条。

## 尺寸与样式

- 门框占5×5×1格，净开口3×4格，门槛高1/8格；主屋原门为1×2格，前方已有三格台阶，墙面高六格。
- 双扇向内开启90°，转轴放在门框后侧的退口处。门后预留3格深的开合空间，完全开启时门扇与门环均让出三格净宽。
- 上部使用木棂，下部使用实心木板；门框与住宅木柱、粉墙使用同源像素材质。暗铜门环放在较低的位置，便于与1.8格人物参照比较。
- 保持完整硬边门框和门板，细节以1/16格对齐。原版深色橡木门对照读取实际原版模型；灰色方块人仅为比例参照。

主屋试装从当前小院住宅的作者输入生成，只取门口局部；五格门框范围内仅移除原门和普通墙格，不切割既有完整窗、柱、梁或屋顶构件。试装用于判断开口与柱列、台阶的比例，未回写原住宅输入或导出文件。

## 生成与检查

```bash
python3 tools/player_homes/door_study.py --out reports/player-house-door-study/site
node tools/player_homes/door_capture.mjs reports/player-house-door-study/captures \
  https://assets.ishiki333hhh.win/previews/player-house-door-study-20261117/index.html
```

作者输入为 `blueprints/player_homes/door_study.json`，需要当前项目构件资源、Pillow及原版1.21.1客户端资源。`door-design.json`保存门框、单扇部件、转轴和尺寸；`source.zip`提供本轮作者文件，复现仍需同提交的住宅与构件代码。

离线检查逐度采样0～90°，核对两扇门的包围范围不相交、全开时净通道无构件侵入，以及最大回转深度位于三格预留区内。这些检查只对应设计几何，游戏碰撞、服务器开关同步、放置拆除与玩家操作尚未实现或验证。本轮不构建JAR、不启动Minecraft、不修改存档。
