# 宗门蓝图与景观

`blueprints/sects/catalog.json` 管理六类、三级共十八套正式布局。目录为世界规划提供等级、地块尺寸、山门和外部支路出口。每套形制在 `blueprints/sects/forms/<id>/form.json` 声明原预览身份、材质、操作站位及压缩输入的摘要。

`terrain.bin.gz` 逐列记录高度、施工权重、地表索引和建筑保护位。`form.bin.gz` 依次记录局部 x、z、起始高度、跨度和材质索引。前四项为小端无符号 16 位整数，最后一项为无符号字节。导出器检查尺寸、顺序、重叠、支路标高、经架与琳琅阁的方块和站位，并将同一摘要交给世界规划和施工。大体积山石由高度场施工，压缩分段记录局部挖填、水体和建筑。树木由生物群系生成。

```bash
python3 tools/sect_blueprints/runtime.py
PYTHONPATH=tools python3 -m unittest sect_blueprints.test_generate sect_blueprints.test_runtime
python3 tools/validate_sect_generation.py
```

局部地貌在生物群系装饰前施工，桥、廊、玻璃、照明和设施在当前区块装饰阶段放置。生成器保护建筑列，外侧自然坡面按施工权重接入地形。对于照月湖心主宗，玻璃斜廊和大殿以同一形制保存，水线以上和以下的构件使用同一局部高度零点。

`import_forms.py` 将已确认的体素研究导入作者输入，需要 NumPy、体素网格及路线记录。既有参数式蓝图工具仍可用于单独设计与测试，当前世界预设按正式目录导出。
