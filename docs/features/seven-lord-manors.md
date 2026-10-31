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

0.105.3 起，构件物品按实际造型显示「玉格窗」「木柱」「瓦屋面」「石阶」等中文名。跨用途复用或混合构造保守显示「木作构件」「木石构件」「复合建筑构件」；注册分组编号只在调试信息中出现。物品按住 Shift 查看实际材质与营造流派，中键取样和原造型状态保持同源。

可选安装 Jade 15.10.5（NeoForge 1.21.1）后，指向提示使用同一份部件名称；按 Jade 的详细信息键（默认 Shift）展开材质。默认城市的归属由服务器读取当前世界的实际城市原点与施工三维范围，独立样板使用其真实展示地块。移到府外、超出楼体高度、进入其他维度或使用异城构件时不附加城主府名称；「某某式营造」仅说明流派。切换准星后，旧服务器提示必须同时匹配维度、位置和方块状态才可使用。Jade 设置中可独立关闭「建筑部件名称」与「建筑归属」，无关功能方块继续使用原提示。未安装 Jade 时仍有物品名称和无编号的通用方块名称，无强制依赖。

这轮侧重建筑构造：装饰材料从创造栏或取样获得，没有追加生存配方或平衡。水面/灵泉使用不流动的静态玻璃材质；剑气、悬潮、法箓光效、粒子与发光效果继续单独制作，不把预览特效算作本轮已实现内容。玩家外观、行走手感、FPS 与多人验收另行记录。

## 资源归属

- `blueprints/lord_manors/cities/*.json` 是从已审阅几何正式接纳的 authored 构造输入，保留预览来源摘要；`*.design.json` 保留建筑用途与设计说明。人物尺度模型、分区覆盖和灵岳外部城市参照背景不进入建筑资源。
- `blueprints/lord_manors/config.json` 维护材质分类、尺寸分格与样板施工参数；`inscriptions/`、`motifs/` 是整幅固定题字/纹样蒙版，不依赖运行机器的字体版本。
- `structure_rules.json` 明确每次屋盖构造的归属、源盒索引、屋面收口尺寸与挂幡外法线/承点。源构造摘要不符、屋瓦遗漏或重复引用均失败；不把独立上下重檐拼成一层，也不覆盖设计开口。
- `native_trees.json` 维护34个成树种植点、原版树种、种植土尺寸、固定随机种子与地块边界。原来的树干、树冠和树上积雪几何已从 authored 源中删除，其余建筑盒保持原值。不得重新添加精雕假树材质。
- 真实树形由独立 `manorTreegen` 数据工具直接运行 Minecraft 1.21.1 的 `ConfiguredFeature` 生成，读取原版方块标签并检查树苗、土壤和掉落来源。建筑格不可被树覆盖，未成功生成的树不能跳过。工具不进入模组 JAR。
- `tools/lord_manors/generated/native_trees.json` 是该工具拥有的生成输出，记录源码摘要、游戏引擎版本和内容摘要；修改种植输入后须重新生成，不能手改树形。泥土、原木和 `persistent=false` 的真实树叶进入实际 NBT，生长、掉落、树叶消退及再种植沿用原版。
- 城市小院2～3格宽的树池种原版树苗，深色橡木保持2×2种植阵列；不再塞入缩小的原木/树冠模型。树苗会按游戏实际净空条件生长。
- `tools/lord_manors/generate.py` 生成材质、单格雕构模型、服务器碰撞目录和 16 格宽的 NBT 切片。仍归现有 `civic/lord_manor` 家族，不新增城市生成家族。
- `blueprints/lord_manors/presentation.json` 维护部件分类、组合名与保守回退；`tools/lord_manors/presentation.py` 从 authored 材质、用途标签和原盒体方向生成独立显示目录。语义不参与几何归一化、排序或造型编号；显示信息不建立逐格方块实体。
- 精雕模型的可见面经 `surfaces.py` 裁切：隐藏在实心体积内的面不输出，同向共面区域只保留一个表面，局部饰面优先于大面积基底。UV 按原面方向裁切，不重新拉伸题字或纹理。原始 profile、注册状态、碰撞、建筑和城市切片不因面裁切改变。
- `construction.py` 在分格前按施工合同补齐相邻薄屋面间的收口，并让挂幡避开非挂接构件、通过吊杆和立承接到实际建筑。收口投影不得越出高侧屋瓦，不改变屋顶高度与平面轮廓。
- 全部题匾的文字正面不得被构造盒遮住；指定调整的题匾须继续贴在合同承梁上。真实树木的原木与树叶不得覆盖屋瓦或墙体。
- 混合材质模型使用 NeoForge 内置 `neoforge:composite`，木框/墙与透明窗玉按各自渲染层提交，物品同样按实心到透明排序。原版树叶使用原版模型与渲染。整格玻璃类材料使用原版 `TransparentBlock`，相邻同类面不重复绘制；不靠修改窗色或不透明化掩盖排序问题。
- 精雕和整格透明面由客户端 `ManorGlassModel` 在区块烘焙时读取相邻遮挡：实心部分与同种玻璃的覆盖区域分别来自生成目录，按实际露出面积裁切并同步裁切UV。透明面不使用木框的整面 `cullface`，不参与实心环境遮蔽；物品保留完整模型。无需方块实体。
- `ManorContent` 读取生成目录注册方块/物品；`ManorCommands` 只在专用样板维度施工，不调用省城生成器或选址器。
- 普通 Gradle 打包在通用模板清理后运行七府自己的生成入口，再检查资源，避免七府 NBT 被其他生成族清空。

修改 authored 输入后运行：

```bash
# 涉及树木输入或生成引擎时，先在云端运行：./gradlew generateManorNativeTrees
python3 tools/lord_manors/generate.py
python3 tools/lord_manors/generate.py --check
python3 -m unittest tools.lord_manors.test_construction tools.lord_manors.test_surfaces tools.lord_manors.test_native_trees tools.lord_manors.test_presentation
python3 -m tools.lord_manors.check_models
python3 -m tools.lord_manors.check_native_trees
python3 -m tools.lord_manors.check_structures
python3 tools/validate_mod_items.py
```

默认城市资源生成后，用 `python3 -m tools.lord_manors.check_structures --runtime build/generated/province_capitals_v11/resources` 逐格核对施工目标从原生模板落入默认城市后的状态与旋转。放置掩膜与独立样板共用 `construction.py` 的修正构造。

本轮以构造和代表性原生加载画面为主，不追加全量玩法、长距离行走或多人测试。

0.105.0-fix1 修复七府精雕表面共面闪烁。`check_models` 独立回读全部生成模型，使用带空边的 1/16 格体积位图检查重叠面和实心外壳完整性；同时检查 UV 与邻块剔除面的边界。此项是模型资源检查，不能替代玩家外观与 FPS 验收。

0.105.1 修复实际施工与材质渲染层。`check_structures` 从合同追到原屋瓦、收口与挂件，检查邻接/遮挡/锚固，再回读实际 NBT、构件状态及旋转后的体积覆盖；`check_models` 同时要求每个面的渲染层与对应独立材质一致。另用 NeoForge 官方 CompositeModel 读取打包后的全部混合模型。这轮按逻辑核查，不作视觉验收；施工数据及款式目录重新生成，须新建世界。

0.105.2 将34棵府内成树替换为原版生成树，城市小树池改种真实树苗；清理精雕假树资源。树形工具实际调用原版 `ConfiguredFeature`，资源检查核对原木、叶距、土壤及默认城市NBT。玩家种植、催熟、砍伐与等待树叶消退尚未实机验收，保持 `not_verified`。

0.105.2-fix1 修复精雕窗玻璃借用木框遮挡范围而被整面裁掉、相邻玻璃保留内部面的缺陷。新增真实灵岳窗块组合检查、六向部分遮挡与UV裁切检查；单块模型及加载器检查不能代表相邻块显隐。原构造、碰撞、款式号与模板方块状态保持原值，现有建筑无需重建；玩家画面仍待验收。
