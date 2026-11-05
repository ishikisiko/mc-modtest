# 八城传送亭台

`blueprints/capital_waystones/sites.json` 是已选定的 25 处点位、占地和接路输入；`materials.json` 管理实体材料、屋顶高度及地形处理范围。网页预览也读取这份点位，不另存坐标。

正常 Gradle 的 `generateProvinceCapitalV11Assets` 在原八城资源导出后调用本生成器，并再次聚合 V11 冻结规则。所有装饰都进入所属城市的稀疏 NBT；石碑元数据 `waystones` 记录原生方块类型、中文地点名、位置和朝向，与模板一起参与资源身份。施工拒绝覆盖原建筑或非天然方块，保留原路铺装与同切片的原方块实体 NBT；中州南门接路按明确输入下挖一格天然地坪，以衔接已有低半砖。

单独检查使用新的输出目录，以保留原资源与失败证据：

```bash
PYTHONPATH=tools python3 -m unittest tools.capital_waystones.test_generate -v
python3 -m tools.capital_waystones.generate \
  --resources <未增加亭台的正式八城资源目录> \
  --out reports/capital-waystones-candidate
```

运行时，安装 Waystones 与 Balm 后，`CapitalGamePlacement` 在生成当前区块时添加上下两块石碑和延迟 BE。Waystones 自己负责首次加载初始化、UUID 和个人激活存档；中文名称使用官方事件提供。没有安装依赖时不加载其实现类，亭台建筑仍然存在。玩家拆除后不重建，旧区块不回填。

必要的云端检查：

```bash
./gradlew test \
  --tests 'com.example.myvillage.worldplan.capital.CapitalWaystone*Test' \
  --tests 'com.example.myvillage.worldplan.DefaultManorCapitalTest' \
  jar --no-daemon --console=plain --max-workers=2

MYVILLAGE_CLOUD_CHECK=capital_waystones \
  python3 -m tools.capital_waystones.smoke /content/capital-waystones-evidence
```

原生检查先在独立的新修仙世界验证无可选依赖启动。带 Waystones 的隔离世界只复制本轮保存的 `level.dat` 以复用同源生成器缓存，不复制任何区块、玩家或 SavedData；生成器仍核对世界、路网和矿口身份。随后从空白区块逐点生成并回读 25 处原生石碑及到达空间，保存后重启核对 UUID，并检查已拆除石碑不被重建。探针仅在 `-PwaystonesEvidence` 下编译，不进入交付 JAR。结果记录 `selected/completed/failed/unrun`；这些是开发者检查，玩家外观与使用感受由用户实际查看。
