# 灵岳整城蓝图试验

这是独立工具，不进入 Gradle、JAR 或世界生成。沿用在用的城市和环境 authored 输入，增加明确的院落归属、接路及门洞方案；没有重排原有住宅。

```bash
python3 tools/province_capitals/blueprint_plan/generate.py
PYTHONPATH=tools python3 -m unittest province_capitals.blueprint_plan.test_plan
```

入口为 `blueprints/province_capitals/blueprint_plan/lingyue.json`。它引用城市、环境和地标源；修改关系在该入口完成，修改原建筑形态仍由原建筑生成器负责，不另存一套房屋几何。

输出默认在 `out/preview/lingyue-blueprint-plan-20261030/`：

- `index.html`：自包含的总平面与三维对照，支持街坊选择、图层和对象归属查看；无需服务。
- `plan.json`：逐格地块、台地、道路、房屋、庭院、门洞、设施及输入哈希。
- `construction.json`：同一编译结果的施工清单，包括现有建筑/道路任务、局部整地、拟增门洞、庭院路径与设施；尚无游戏资源导出消费者。
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
