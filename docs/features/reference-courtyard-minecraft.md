# 月洞水院：Minecraft 细体素复刻

0.68.0 将用户三张庭院参考及已建立的独立体素样稿转成实际 Minecraft 方块。
保留临水木桥、台阶门亭、主屋、曲线山墙、灰瓦、白墙月洞门、格窗和连续圆门回廊；省略植被。
参考未展示的背面和连接处作了补全，不宣称精确测绘整个原场景。

## 游戏中使用

- 需要匹配的 MyVillage 0.68.0 及原有 NeoForge 1.21.1 依赖。
- 创造栏“月洞水院 · 建筑构件”列出所有构件；默认朝向玩家放置，创造中键取块保留镜像形状。
- `/place template myvillage:reference_courtyard ~ ~ ~` 在命令坐标的正 X/Y/Z 方向放置独立样板，包围盒 48 × 24 × 36 格。
- 模板含空气，使用独立空地/测试世界；本次代码不安装到用户电脑，不自动放置，也不改旧世界。
- 构件作为普通 BlockItem 可放置、拆除并掉落自身；镜像状态随掉落保留。不需要指定工具，无生存合成配方，也没有自然获取链。
- 水池是原版水源，水中的局部木柱/石沿使用 `waterlogged`。每一格内的模型与碰撞都不超出该格。

## 所有权与稳定 ID

`tools/reference_courtyard/build.py` 和 `model.json` 拥有原始体素几何；
`minecraft.json` 拥有 MC 适配材质、单位与采集原点；`piece_library.json` 是只追加的构件库存。
普通运行 `python3 tools/reference_courtyard/minecraft.py` 重建模型、贴图、语言、掉落、碰撞数据和模板，
`--check` 检查这些打包资源。几何新增形状时显式运行 `--extend-library`；不能删除旧 ID。

构件形状在旋转/镜像等价下去重，以规范化体素与材质的内容哈希生成 registry ID；
新构件不会重排旧注册 ID。分类独立体现在 ID 中，不使用一个 `family` blockstate 混装无关类别。
`CourtyardContent` 从生成目录注册普通方块与 BlockItem；没有每格 BlockEntity 或动态同步网格。
`facing`、`mirrored`、`waterlogged` 是原版保存的方块状态。
模型 JSON 和缓存的旋转/镜像 `VoxelShape` 由相同体素占用生成，不以完整方块代替圆门边的碰撞。

这是独立 authored structure，不是新的 settlement family；不接入城镇或宗门生成。
`generate_all_structures.py` 在清理后调用其专用生成器，保证全量生成不会丢掉模板。
其他方块、武器、兽/NPC 和假山的生成归属不变。

## 检查与证据

局部检查包括模型/碰撞体素等价、旋转和镜像逆变换、稳定 ID、NBT 回读、双语/模型/掉落闭合。
Java 聚焦测试检查真实 VoxelShape、保存状态和镜像组合；通过 Modal `courtyard-build` 执行编译与 jar 打包。
`courtyard-runtime` 增加真实服务器/客户端，执行模板放置、物理 W 键通行、右键放置、游戏内截图、
结构方块 SAVE、重启读回以及独立样板世界导出。RCON 仅用于 fixture/读回，不能代替映射输入。

当前云端结果待执行；玩家外观、手感、声音、FPS 与广泛多人验收保持 `not_verified`。
原 `.vox` / `.glb` 是离线模型；本次 `.nbt` / `.schem` 才引用实际 MC 注册方块。
