# 宗门蓝图与景观

`blueprints/sects/catalog.json` 管理共用选址规则及六套布局：高崖主峰、谷地群院、湖岸岛屿，各有紧凑版与较大版。建筑仍使用注册的 `cultivation_sect` 组件；整体高差、院落序列、桥廊和建筑群体量由各自 authored 蓝图决定，不按名称推断形态。

正式资源由 `runtime.py` 导出，预览由同一 `SectModel` 生成。需要工作区已有的 `exmod/mod_block_catalog.json`，方块合法性仍通过原 `modset.py` 检查。

```bash
python3 tools/sect_blueprints/runtime.py
PYTHONPATH=tools python3 -m unittest sect_blueprints.test_generate sect_blueprints.test_runtime
PYTHONPATH=tools python3 -m sect_blueprints.gallery --out reports/sect-gallery --base-url https://assets.ishiki333hhh.win/sect-blueprints/current
```

正式目录包含清单、地形场和显式建筑/道路/设施/植被的稀疏 NBT。地形场每格为小端 int16 高度、uint8 施工权重、uint8 地表索引。完整矩形及缓冲继续用于结构避让；实际整地按权重接回自然高度，零权重区域保留地表和植被。表土厚度、草地/林下土/裸岩、地区材料映射、树林与溪湖参数只在 authored 输入维护。水岸必须封闭。

`runtime` 定义真实入口、执事、经架及读者和活动区；`road_port` 是位于蓝图边缘的正式出口，包含位置、方向和完整净宽。三格支路只接其外侧，内部路线由蓝图拥有。道路、设施、门槛与整个声明路宽均做免跳跃拓扑检查；未声明拆除原建筑、孤立桥片、断开的经架、床头尾错误或漏水都会拒绝导出。NBT 每格回读，源摘要随清单保存。

世界规划冻结每个 `SectSite` 的蓝图 ID 和摘要，Java 共用 `SectTerrain` 计算地面和施工。FEATURES 只营造当前区块并保留此前完成的外部支路，FULL 区块不回填。命簿仅认领预留地；靠近后只绑定已有实体。化身、送信范围、执事与经架必须读取实际布局。

`generate.py --preview-only` 只生成模型网页；`gallery.py` 生成六套可切换模型。预览自然接坡统一示意到入口高度，游戏接到实际种子地形；方块配色示意不替代原生材质、碰撞、FPS 或玩家接受度。网页不进入 JAR，不新增特效。
