# 装备制作源

本目录只放 authored 输入。实现与命令入口为 `tools/equipment`，产物写入
`src/main/resources`；不要直接修改生成的模型、贴图、图标或目录。

```bash
python3 -m tools.equipment list
python3 -m tools.equipment preview xuantie_armor --out reports/equipment-preview
python3 -m tools.equipment build
python3 -m tools.equipment check
```

预览和图标渲染需要 numpy/Pillow。复用 `.venv-preview`，或设置
`MC_PREVIEW_PYTHON`；依赖清单为 `tools/combat_preview/requirements.txt`。

## 制作下一套防具

```bash
python3 -m tools.equipment new-armor --from xuantie_armor --id jade_armor --name 青玉
python3 -m tools.equipment preview jade_armor --out reports/jade-armor
```

新目录默认 `enabled: false`，可预览但不注册、不打包对应资源。先改轮廓、部件和材质，
完成外观确认后设为 `true`，执行 `build` 和 `check`。同类防具无需修改 Java 注册器或渲染器。
已导出的 registry ID 不允许通过改名或禁用删除；`build`/`check` 会拒绝这种改变。

每个 `equipment.json` 有独立 id、中文名及四个可选槽位（同槽最多一件）：

- `head`：只允许挂到 `head`。
- `chest`：允许 `body`、`right_arm`、`left_arm`。
- `legs`：允许 `body`、`right_leg`、`left_leg`。
- `feet`：允许 `right_leg`、`left_leg`。

`boxes` 是骨骼局部坐标的立方体；单位为 Minecraft 模型像素，Y 向下，Z 负向为正面。
`from` 与 `size` 给出范围；`pivot`、`rotation` 可选，旋转为度，顺序与 ModelPart 的 ZYX 一致。
名称在单件内唯一，用于定位问题。胸甲挂在手臂上的部件可指定 `fit: "arm"`，
工具会保持手臂内侧边缘，将宽臂的 4 像素宽适配为细臂的 3 像素宽，另分配对应 UV。
非手臂部件必须用默认的 `fixed`。

`materials.json` 提供可复用的金属、布、皮革和玉石材质。每个部件只选材质名；
导出器按 Minecraft 的立方体展开法分配 UV，图集放不下时明确报错。
`texel_density` 控制每模型像素的纹素数，`texture_size` 控制图集边长。
不允许越界 UV、空体积、不合法骨骼或重复身份。

当前基础材质仅支持 `minecraft:iron`，耐久倍率来自输入。它是样本的原版属性基线，
不是已经完成的玄铁防具平衡体系。没有额外伤害监听器，减伤仍由原版执行一次。
装备导出器不负责获取配方；玄铁甲的生存制作与炼灵由 [炼器工坊](../docs/features/xuantie-armor-forging.md) 负责。

## 武器复用与预览

`weapon_reference` 只声明已有武器的专用生成器、实际模型、几何合同和战斗 id，
不接管武器美术、PAL 动画或第一人称解算。

```bash
python3 -m tools.equipment preview qingfeng_sword --out reports/qingfeng.png \
  --vanilla-jar build/moddev/artifacts/neoforge-21.1.233-client-extra-aka-minecraft-resources.jar
python3 -m tools.equipment preview xuantie_armor --weapon qingfeng_sword --out reports/fitting
```

完整武器第一人称、动作和差异对比继续使用 `python3 -m tools.combat_preview`。
共享基础函数位于 `tools/equipment/geometry.py`，旧生成器保留兼容导出。

## 预览的含义

`preview` 输出自包含 `index.html`、多视角 `sheet.png` 和说明 JSON。
浏览器可旋转、缩放、切换单件／整套、宽臂／细臂、四种姿态和配持武器。
几何、UV 和图标来自与游戏相同的编译结果；姿态快照由 `poses.json` 提供。
浏览器光照是预览光照，不声称与游戏逐像素一致，也不模拟 PAL 的全部实时动作。
实时摆动、其他模组姿态、资源重载和玩家观感仍需单独核对。

当前不含 Blockbench 双向导入、布料物理、裙甲独立骨骼动画、第一人称护臂绘制或原版纹饰适配。
槽位标签会被原版纹饰标签间接包含，因此导出器用 NeoForge 的 `remove` 标签条目仅排除本目录防具的纹饰资格，
避免把原版 UV 直接套在自定义图集上；原版附魔资格与光效沿现有盔甲渲染链处理。

对外预览仅发布到现有 `https://assets.ishiki333hhh.win/` 资源服务，不启动第二套服务。

## 同源立体物品与器坯（0.98.0）

剑、枪在背包和工坊中直接使用各自专用生成器的立体本体，不再切换独立二维图标。
防具的物品模型从穿戴模型的标准身形、骨骼坐标和 UV 导出为 OBJ；穿戴资源保持原有归属。
旧剑枪图标及防具自动渲染图只保留为粒子／资源兼容文件。

- `display.json`：共用 GUI 取景、工具握持与普通物品展示参数。
- `workshop/tools.json`：镐、斧、铲、锄的实体部件、角色、材质色表与握点。
- `workshop/stages.json`：剑枪锻造主体、防具甲片选择、粗坯参数和阶段表面。
- `tools/equipment/mesh.py`：保持原坐标和 UV 的 quad/OBJ/MTL 导出；使用 NeoForge 自带 OBJ loader，不增加自定义 Java 渲染器。
- `tools/forging/models.py`：同一部件来源派生工件、成品工具及锻台／水槽模型；只用整体旋转、缩放和居中适配台面。

```bash
python3 tools/gen_qingfeng_sword_model.py
python3 tools/gen_lingxiao_spear_model.py
python3 -m tools.equipment build
python3 tools/gen_forging_assets.py
python3 -m tools.forging.preview_models --out reports/forging-models/preview
```

成形、淬火、回火的几何一致，表面状态不同；精磨恢复原成品主体材质，装配加入柄、护手、饰件或衬垫。
GUI 自动取景不改变武器的非 GUI 变换或战斗几何合同。图标、锻台与水槽不再各维护一套简笔轮廓。
OBJ、MTL、模型 JSON 及其所有贴图引用都必须打包并通过检查；禁止回落到缺失材质或二维占位图冒充完成。
