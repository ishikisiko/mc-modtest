# 七府独立样板

北原、南疆、灵岳、西漠、东海、东荒、魔域七座城主府保留独立样板入口和原造型的院落、吊脚、叠台、跨水与高差关系。0.105.0 起的默认城市接入见[七府默认城市](default-manor-capitals.md)；本页说明独立样板和共用材质、雕构资源。

在有作弊权限的创造世界中使用：

```mcfunction
/myvillage manor list
/myvillage manor visit beiyuan
/myvillage manor visit nanjiang
/myvillage manor visit lingyue
/myvillage manor visit ximo
/myvillage manor visit donghai
/myvillage manor visit donghuang
/myvillage manor visit moyu
/myvillage manor status
/myvillage manor leave
```

首次访问时，服务器在独立空白维度 `myvillage:manor_gallery` 中分批构造相应样板，完成后自动进入并启用创造飞行。再次访问直接进入。样板坐标只用于展示，不代表城市选址。`leave` 在同次服务运行中返回进入前的位置；重启后使用主世界出生点。

创造栏「七府营造」提供各府专用墙石、瓦、木、玉、旧铜和装饰材质。建筑的细柱、檐口、楼阶和雕饰使用有实际模型与碰撞的雕构方块，中键取样保留造型；它们不逐个挤入创造栏。整幅匾额与刻纹跨格连续，不把文字逐格重复。魔域两根魔契柱复用独立的 `myvillage:demon_pact_pillar`。

这轮侧重建筑构造：装饰材料从创造栏或取样获得，没有追加生存配方或平衡。水面/灵泉使用不流动的静态玻璃材质；剑气、悬潮、法箓光效、粒子与发光效果继续单独制作，不把预览特效算作本轮已实现内容。玩家外观、行走手感、FPS 与多人验收另行记录。

## 资源归属

- `blueprints/lord_manors/cities/*.json` 是从已审阅几何正式接纳的 authored 构造输入，保留预览来源摘要；`*.design.json` 保留建筑用途与设计说明。人物尺度模型、分区覆盖和灵岳外部城市参照背景不进入建筑资源。
- `blueprints/lord_manors/config.json` 维护材质分类、尺寸分格与样板施工参数；`inscriptions/`、`motifs/` 是整幅固定题字/纹样蒙版，不依赖运行机器的字体版本。
- `tools/lord_manors/generate.py` 生成材质、单格雕构模型、服务器碰撞目录和 16 格宽的 NBT 切片。仍归现有 `civic/lord_manor` 家族，不新增城市生成家族。
- 精雕模型的可见面经 `surfaces.py` 裁切：隐藏在实心体积内的面不输出，同向共面区域只保留一个表面，局部饰面优先于大面积基底。UV 按原面方向裁切，不重新拉伸题字或纹理。原始 profile、注册状态、碰撞、建筑和城市切片不因面裁切改变。
- `ManorContent` 读取生成目录注册方块/物品；`ManorCommands` 只在专用样板维度施工，不调用省城生成器或选址器。
- 普通 Gradle 打包在通用模板清理后运行七府自己的生成入口，再检查资源，避免七府 NBT 被其他生成族清空。

修改 authored 输入后运行：

```bash
python3 tools/lord_manors/generate.py
python3 tools/lord_manors/generate.py --check
python3 -m unittest tools.lord_manors.test_surfaces
python3 -m tools.lord_manors.check_models
python3 tools/validate_mod_items.py
```

本轮以构造和代表性原生加载画面为主，不追加全量玩法、长距离行走或多人测试。

0.105.0-fix1 修复七府精雕表面共面闪烁。`check_models` 独立回读全部生成模型，使用带空边的 1/16 格体积位图检查重叠面和实心外壳完整性；同时检查 UV 与邻块剔除面的边界。此项是模型资源检查，不能替代玩家外观与 FPS 验收。
