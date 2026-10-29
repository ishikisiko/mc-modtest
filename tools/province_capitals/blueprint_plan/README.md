# 灵岳整城蓝图试验

这是独立工具，不进入 Gradle、JAR 或默认世界生成。沿用在用的城市和环境 authored 输入，增加明确的院落归属、接路及门洞方案；没有重排原有住宅。网页方案与实际 MC 结构导出是两个入口。

```bash
python3 tools/province_capitals/blueprint_plan/generate.py
PYTHONPATH=tools python3 -m unittest province_capitals.blueprint_plan.test_plan
```

入口为 `blueprints/province_capitals/blueprint_plan/lingyue.json`。它引用城市、环境和地标源；修改关系在该入口完成，修改原建筑形态仍由原建筑生成器负责，不另存一套房屋几何。

输出默认在 `out/preview/lingyue-blueprint-plan-20261030/`：

- `index.html`：自包含的总平面与三维对照，支持街坊选择、图层和对象归属查看；无需服务。
- `plan.json`：逐格地块、台地、道路、房屋、庭院、门洞、设施及输入哈希。
- `construction.json`：同一编译结果的施工清单，包括现有建筑/道路任务、局部整地、拟增门洞、庭院路径与设施。实际 MC 导出器使用同一 authored 输入，并读取实际方块重新检查净空。
- `checks.json`：实际空间检查结果。有未解决项时生成器退出非零，同时保留带标记的方案。

宏观山势与五片台地读取原生成器。局部整地只允许在声明的庭院、接路内，变化上限读取入口配置。私家院必须连接所属建筑的可达室内，不能跨公共道路或侵占公共前场；公共庭院直接连接道路。新增门洞以方案记录表达，正式建筑输入未被覆盖。

每栋建筑的地块记录包含原 footprint、明确归属的院落和接路；它不是把整城剩余空地自动划为私宅的 Voronoi 分区。未分配空地、坡面和沟谷继续保留。公共石井坪的药圃保留公共归属，井体及现有陈设参与避障。

三维中的原房屋来自现有方块生成器；新庭院、门洞和设施为方案几何，城墙为边界体量，不能视作最终游戏方块资源或碰撞验证。道路接通、逐格高差和原几何净空是离线检查，游戏运行、寻路与玩家审美验收保持 `not_verified`。

可选界面检查直接打开本地文件，不启服务：

```bash
CHROME_BIN=/path/to/chrome PLAYWRIGHT_MODULE=/path/to/playwright \
  node tools/province_capitals/blueprint_plan/capture.cjs
```

它核对压缩预览的源身份、平面/三维切换、庭院归属信息、手机布局及控制台错误，并保存截图和 `capture-report.json`。

## 实际 MC 方块试验

先用原建筑生成器导出当前灵岳源，再生成独立数据包：

```bash
python3 tools/province_capitals/regional/v2/export_game.py --regions lingyue \
  --out reports/lingyue-trial/base-v2 --preview-out out/preview/lingyue-trial/base-v2
python3 tools/province_capitals/blueprint_plan/export_minecraft.py \
  --base-v2 reports/lingyue-trial/base-v2 --out reports/lingyue-trial/export-r1
PYTHONPATH=tools python3 -m unittest province_capitals.blueprint_plan.test_plan province_capitals.blueprint_plan.test_minecraft
```

实际导出使用原环境施工器保留地标、道路、城防、门前陈设与原生符灯，再读取最终方块重新求解庭院接路及设施避障。`minecraft` 配置决定具体方块、数据包版本和试验原点。地形填充与建筑方块合并为每个区块的 NBT；新门洞使用完整上下半门，双向坡道在高端生成朝上坡的楼梯，晒架使用注册的四个部件。

输出包括 `lingyue-blueprint-trial.zip`、实际求解后的 `plan.json`、逐模板 SHA 与放置批次 `inventory.json`、数据包身份 `artifact.json`。每个模板立即回读比对施工数据；检查原房屋只能在声明门洞内改动，整地只能发生在声明区域，并保持原有岗位的支撑与净空。

将数据包放进**新建独立测试世界**的 `datapacks/`，使用匹配的 MyVillage JAR。开启作弊，按清单中的批次加载对应区块并调用 `myvillage:blueprint_trial/lingyue/batch_XX`。模板含地基，试验原点来自配置；只能在预留的空白区域放置。`myvillage:blueprint_trial/lingyue/audit` 检查门、路及设施的原生方块，目标计数在清单中。

该入口验证原生结构放置，不改自然世界生成预设，也不把测试世界中的建筑自动注册为 NPC 城镇。原生采集仍需记录匹配 JAR、数据包、世界和采集器身份；网页检查、原生方块检查、映射输入与玩家外观验收分别报告。

实际结果与已知的局部植物、床部件问题见[原生试验记录](../../../docs/testing/2026-10-30-lingyue-blueprint.md)。
