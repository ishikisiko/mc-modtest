# 同源立体炼器模型证据（2026-10-27）

用户确认采用同源立体模型方向后，实现剑、枪、四种工具、四件玄铁甲的六阶段模型。工具由有体积的金属头、连接件和木柄组成；器坯来自成品主体，热处理改变材质，精磨恢复主体表面，装配加入柄、护件或衬垫。背包、持物与台面共享部件几何。

## 固定身份

- 构建与原生运行源码：`7ec58cd376004dd6d5c3e33a05244f41a44dbc5d`；固定 7068 个受控文件。
- jar：`myvillage-0.98.0.jar`；SHA256 `de9a7c9cd4ab82ad5ca0f6c284cddd18df340db1043f47fa31f07e9752603922`。
- Colab `armor-forging-20261027`，按用户要求保留 VM。重型任务未在本机运行。
- 依赖包 SHA256 `f2748fd663647e170fef0f722043eeaf3696dac58119e835a1e695c713a7b9c6`；逐成员校验实际依赖。

## 检查与证据复用

- 相关 Python 检查共覆盖 87 项：首轮 82 项，追加 2 项 OBJ 资源校验，再追加 3 项图集／日志回归；最终对受修复影响的模型和物品校验模块重跑 37 项，全部通过。日志分别为 `tests-2.log`、`obj-validator-tests-2.log`、`tests-atlas-4.log`。没有将此前失败的运行计作通过。
- 装备、剑、枪、炼器生成器检查与物品资源检查通过。OBJ 的坐标、UV、材质往返一致；GUI 边界、台面尺寸、装配构件、热态非金属保护均有针对性检查。剑枪的非 GUI 变换与玄铁甲穿戴模型／贴图指纹保持原值。
- 云端 `cleanProcessResources jar -x generateAllStructures` 通过，用时 13 秒。474 项相关 jar 资源与固定源码逐字节相同，54 项退役贴图确认不存在。
- 新旧 jar 的全部 Java 类和 `data/` 共 3389 项内容逐字节一致。因此沿用 [护甲炼器原始 r7](2026-10-27-armor-forging.md) 对相同服务器实现的功能证据；不声称在新模型源码上重跑了该轮 66 项 Java 测试或 71 项玩法检查。
- `validate_sword_combat_foundation.py` 全量仍有 6 项既有失败：三个 C2S 字段检查、三个旧 AGENTS 文档命令检查。对 `9894dda7` 基线与本次结果逐项比较完全一致，详见 `foundation-baseline.json`；不称全量检查通过。

## 原生局部检查

- selected：`inventory_models, held_models, workshop_models, reload_models`；completed：同上；unrun：`[]`；15 条检查通过。
- 23 张截图和一段持物视频覆盖两页背包、六种武器／工具的第一与第三人称、枪／镐／胸甲的锻造菜单和资源重载。背包、菜单、切换持物、挥动、入火与重载使用映射鼠标／按键；RCON 仅准备物品和世界、读取状态。
- 原生截图确认紫黑缺图已消除，第一人称镐头有实体厚度。关菜单后的三张台面图只显示空锻台，不能作为台上器坯的外观证据；该部分有同源几何／尺寸检查与原生模型加载日志，但本轮没有独立视角的台上器坯近照。
- 归档前再次验证全部固定源文件。游戏 phase 为 `stopped`，所属 `xvfb/server/client` 均不存活；Colab VM 按用户要求保留。
- 最终归档 `reports/forging-models/r2-artifacts.tar.gz`，SHA256 `c61986aed0f76368e8468d7bf787e81b99255ea206ecbd949d1f56a46738d5c0`。

## 保留的失败与修复

首轮 `6aba9e0d` 的 OBJ 几何能加载，但装备贴图不在默认 item/block 图集目录，实机显示紫黑材质。旧日志匹配器漏掉 `Missing textures in model`，该轮 15 条自动检查虽记录通过，人工截图检查明确判为失败，不能用于最终显示通过结论。

修复由 `tools/forging/models.py` 从实际材质引用生成 `assets/minecraft/atlases/blocks.json`，校验器检查非默认目录的材质已登记，采集器检查实际缺图警告；新增回归用例覆盖这次原始故障。首轮原图、日志、世界、jar 和失败判定保存在 `reports/forging-models/r1-artifacts.tar.gz`。

本地还保留了测试 fixture 未复制新图集文件，以及误用缺少 NumPy 的 Python 环境时的失败日志。修正 fixture 后，在已有预览虚拟环境运行原断言并通过；没有降低模型／UV／资源断言。

## 产物与边界

- 生成器预览：`reports/forging-models/candidate/index.html`、`sheet.png`，十类器物共 60 个阶段画面。
- 最终原生证据、jar、日志与独立世界：`reports/forging-models/r2/`。
- 采集入口：`python3 tools/forging_models_evidence.py --out <空证据目录>`；只能在准备好固定源码和依赖的独立云端世界运行。
- 玩家外观、手感、音效、FPS、多人和长期平衡仍为 `not_verified`。本次局部检查不能替代这些验收。
