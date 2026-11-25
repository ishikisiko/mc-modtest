# 宗门蓝图与景观

`blueprints/sects/catalog.json` 管理选址规则、六套通用布局和听泉谷中主宗。通用布局分为高崖主峰、谷地群院、湖岸岛屿，各有紧凑版与较大版。`architecture_profiles.json` 定义建筑的体量、层数、屋顶、材料及室内功能分区，由 `cultivation_sect` 的注册组件生成。整体高差、院落序列、桥廊和建筑群体量由各自的蓝图决定。

松崖和双岸使用连续折岭及台地坡脚，控制点、左右断面与坡脚宽度全部由各自蓝图维护。其余四套地形和布局保留。弟子居生成完整床铺，丹房保留炉台及工作设施，藏经阁的经架与读者站位显式预留；多层楼梯最后一级也必须通向楼面，不能以满方块跨越一格高度。

道路和室内活动面保留三格净空，明确声明的门洞与楼梯井按玩家高度检查两格净空；两者均要求真实承托和免跳跃高差。楼梯出口须预留连续落脚位，不能被家具堵住。床铺以完整头尾为一个操作设施，吊钟的操作位置允许低于悬挂高度。

正式资源由 `runtime.py` 导出，预览由同一 `SectModel` 生成。需要工作区已有的 `exmod/mod_block_catalog.json`，方块合法性仍通过原 `modset.py` 检查。

```bash
python3 tools/sect_blueprints/runtime.py
PYTHONPATH=tools python3 -m unittest sect_blueprints.test_generate sect_blueprints.test_runtime
PYTHONPATH=tools python3 -m sect_blueprints.gallery --out reports/sect-gallery --base-url https://assets.ishiki333hhh.win/sect-blueprints/current
```

正式资源包括清单、局部地形场和稀疏结构分块。地形场记录施工范围的包围矩形，每列使用小端 int16 高度、uint8 施工权重和 uint8 地表索引。完整地块及缓冲另用于结构避让。世界预设以蓝图 ID 和摘要引用同一份资源。

听泉谷中主宗只为主宗地区预留。样板的洞室、低道和天窗由蓝图参数交给游戏生成，从洞中泉庭过桥登上主殿，两侧设抬高的书廊和望泉廊。洞室在生物群系植被生成前开挖，树木由生物群系特征生成。其余山坡直接接修仙世界地形。

`runtime` 定义真实入口、执事、经架及读者和活动区；`road_port` 是位于蓝图边缘的正式出口，包含位置、方向和完整净宽。三格支路只接其外侧，内部路线由蓝图拥有。道路、设施、门槛与整个声明路宽均做免跳跃拓扑检查；未声明拆除原建筑、孤立桥片、断开的经架、床头尾错误或漏水都会拒绝导出。NBT 每格回读，源摘要随清单保存。

世界规划冻结每个 `SectSite` 的蓝图 ID 和摘要，Java 共用 `SectTerrain` 计算地面和施工。FEATURES 只营造当前区块并保留此前完成的外部支路，FULL 区块不回填。命簿仅认领预留地；靠近后只绑定已有实体。化身、送信范围、执事与经架必须读取实际布局。

`generate.py --preview-only` 生成单套模型网页，`gallery.py` 汇总蓝图。模型的接坡以入口高度示意，游戏接到实际种子地形。方块配色、碰撞和玩家观感可在客户端复查。
