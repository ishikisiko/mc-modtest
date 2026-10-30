# 魔契柱

独立可摆放的魔域建筑构件，ID 为 `myvillage:demon_pact_pillar`。从 MyVillage 创造栏获取，或使用：

```mcfunction
/give @s myvillage:demon_pact_pillar
```

底座 4×4 格，实际高 9.25 格；上方需留出 10 格高度。手持物品右键坚实地面的顶面，点击位置是底座的一个角点，刻字面朝向放置者。整根柱子一次放置，消耗一件物品；四种水平朝向可通过站位选择。所有底座格都必须有坚实支撑，所有占用格都需可替换、无液体、无方块实体且在可交互与已加载范围内。

柱身、柱冠与底座都有对应实际造型的碰撞。拆除任意部位会移除整根；生存拆除掉落一件，创造拆除无掉落。活塞不能推动。没有合成配方，也不自动放入城主府或世界生成。

外观采用完整的紫黑石身、旧铜柱冠、正面整幅「魔契」毛笔字与背面契纹。分格仅用于 MC 的方块占用与碰撞，视觉石身连续。无发光、粒子、法阵、伤害或修炼功能。

资源入口为 `tools/demon_pact_pillar/appearance.json` 和 `inscription.pgm`（256×640 的完整题字蒙版，最初由仓库毛笔字体排字）。生成器使用标准库读取固定画布，不依赖运行机器的字体或 FreeType 版本；模型、材质和服务器碰撞目录同源：

```bash
python3 tools/demon_pact_pillar/generate.py
python3 tools/demon_pact_pillar/generate.py --check
python3 -m unittest tools.demon_pact_pillar.test_generate
python3 tools/validate_mod_items.py
```

实际 MC 画面用于外观审阅；开发者检查不代替玩家外观、手感、FPS 与多人验收。
