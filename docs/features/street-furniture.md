# 符灯与晒布架（0.103.1）

两类街景设施使用独立装饰方块和对应可放置物品，不再将缩小的整方块配方取整后放入世界。

- `myvillage:rune_lamp`：两格高的细铜立灯，上格提供光照。灯面使用已有 `brush.ttf` 书法字体的“明”字，保留 32×32 像素贴图。
- `myvillage:drying_rack`：两格宽、约一格四分之三高的木架，占用两格宽、两格高、一格深的空间。双柱等粗，木纹跟随构件方向；布面很薄，碰撞由木框承担。

两者均列入 MyVillage 创造栏，有合成配方，支持手动放置、朝向和取物键。服务器先检查全部占用格、支撑和权限，再一次放置并扣除一件物品；拆除由根格拥有唯一掉落。它们没有额外菜单、耗灵或晾晒玩法。

测试世界可使用：

```mcfunction
/give @s myvillage:rune_lamp
/give @s myvillage:drying_rack
```

## 新旧城市

新建修仙大陆使用 V11，读取独立 v4 城市资源。城市仍沿用 V10 的地形、布局族和生活身份，只把原来明确登记的符灯、晒架换成新方块。晒架按院门或原朝向布置；有限的两格内微调避开货桶、原房屋和受保护通路，找不到位置则生成失败，不跳过设施。

用户已明确选择旧城保持原样。V1–V10 及 v1/v2/v3 城市资源继续打包，旧世界不迁移，不提供本轮旧城改造命令。旧世界可以由玩家自行放置新物品。

## 生成归属

- `blueprints/street_furniture/catalog.json` 是模型、材质、字体、光照、配方及碰撞标记的来源。
- `tools/street_furniture/generate.py` 输出模型、贴图、分格碰撞目录、方块状态、物品、掉落、配方、名称和工具标签；`--check` 检查这些输出。
- `blueprints/street_furniture/replacement.json` 与 `tools/province_capitals/environment_v4/export_game.py` 负责新城中的两类设施替换，原 v3 导出器保持原有产物。
- `tools/province_capitals/game/compile_v11.py` 验证全部八城、前置 V10 身份并生成 V11 冻结规则。沿用已有布局族，不新增 settlement family。
- Java `streetfurniture` 包读取生成目录，统一处理占用格、朝向、碰撞、光照、放置与拆除。没有 BlockEntity 或客户端专属公共类型。

模型按方块边界切开仅用于多格存储，连续表面的 UV 不重置，不产生新的外观接缝。功能检查与玩家外观、手感、FPS 和多人验收分开；本轮游戏内查看由用户完成。
