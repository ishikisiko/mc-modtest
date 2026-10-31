# 七府默认城市

0.105.0 将北原、南疆、灵岳、西漠、东海、东荒、魔域的正式蓝图接入默认修仙大陆。新建世界使用当前 V11 预设和 v4 主城资源；中州沿用现有布局。已有世界和已生成城市区块不迁移。

七府保留已经确认的完整尺寸及已注册 MC 构件。地标向城中内收，住宅由 437 栋减至 386 栋，51 栋原功能建筑保留；私院继续归原主人，公共井场、卸货平台、廊坪等保留公共用途。灵岳东炉石作院保持原位，东海保留岸线海床和公共双埠，魔域保留裂隙与三座跨桥。特效仍是独立任务。

`/myvillage worldplan city <州名>` 查询城市入口；H → 天下 → 舆图沿用现有城标传送流程。入口和服务岗位由正式主城 manifest 提供；服务器仍检查实际安全落点。生活站点在对应服务区块加载后由 `NpcLifeSites` 登记，继续使用 WorldSim 的现有人口和库存。

## 资源归属

- 正式输入：`blueprints/province_capitals/blueprint_plan/<region>.json` 与 `layouts/<region>.json`。
- 实际构造：`ManorPlanner`，与独立蓝图的建筑、府邸、道路、门洞和庭院共用。
- 默认导出：`tools/province_capitals/blueprint_plan/runtime.py`；布局组通过 `tools/buildgen/groups.py` 注册。
- 聚合：`tools/province_capitals/game/compile_v11.py`，七府使用上述导出，中州继续经现有 `environment_v4` 导出。
- 打包入口：Gradle 的 `generateProvinceCapitalV11Assets`；显式等待原府邸生成/检查，避免从不完整的注册构件切片构造城市。
- 最终输出：`data/myvillage/capitals/v4/capital_<region>/city.json`、top-solid 地形和 `structure/province_capitals/v4` 稀疏切片。

地形先由默认生成器按 heightfield 逐列构造，NBT 仅覆盖当前区块的实际建筑、水、道路、支墩和空气。完整床、晒架和魔契柱先做组件检查，再按所属区块写入；不通过跨区块模板或命令在邻块补建。独立试放包仍保留其分批放置机制，两种入口使用同一个实际构造源。

## 检查范围

生成时检查实际门路、全路幅、生活岗位、入口、注册方块、完整构件和 NBT 回读，并保持原有 192 岗位与单块 palette 预算。Python 定向检查验证默认组身份、完整府邸和新服务坐标；Java 定向检查验证默认预设、冻结资源、放大后的城市规划及安全落点逻辑。

匹配 JAR 及原生服务器探针使用 Colab 固定源构建。默认区块生成与读取、NPC 站点登记、实际玩家输入、外观、FPS 和多人分别记录，不能由资源导出或单元测试替代。当前候选的执行记录随交付包提供。
