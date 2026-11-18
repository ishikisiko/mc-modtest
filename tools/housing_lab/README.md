# 住宅的原生小场景检查

这个测试场使用 Minecraft 1.21.1 的 GameTest。它在平坦世界放入正式住宅方块，创建实际 NPC，让其从门外走到床边，再返回门外。六套住宅可以同时检查；场景不生成八城、驿道或矿场。

首次准备开发资源与独立探针 JAR 后，在仓库根目录运行：

```bash
./gradlew dropProbeJar -PdropProbe
python3 -m tools.housing_lab.run \
  --out /tmp/housing-lab-check \
  --probe build/libs/yuanxian-drop-probe-1.jar
```

重跑时换一个输出目录。只修改一套住宅时可以加入 `--ids stone_terrace`；可同时指定多个 NPC 模板 ID。纯检查脚本变更不要求重建主模组 JAR。重型运行沿用项目规定的 Colab 环境。

默认使用原生 `tick sprint` 加速。NPC 的实体 tick、重力、碰撞、导航和开门 Goal 都照常执行；加速改变的是等待这些 tick 的实际时间。需要在开发客户端观察时加入 `--normal-ticks`，使用本次隔离服务器的端口连接。服务器会在检查结束后正常保存并关闭，客户端需另行启动。测试场没有自动提供人工外观或操作验收。

## 检查的实际行为

- 正式方块及其旋转后的真实碰撞形状；逐 tick 核对 NPC 身体没有进入实体方块。
- NPC 自然落地后寻找路径，使用正式生活规则中的速度、到达距离和重试间隔。分段路径必须让 NPC 实际前进，终点只按实体实际到达判定。
- 从公共入口进入床边，再走回入口；切换目标时等待 NPC 落地。
- NPC 自动开启整扇院门，返回后门重新关闭；门框保留碰撞。
- 失败文件保留 NPC 位置、当前路径节点、目标及附近方块和碰撞盒，用于定位卡住的门槛、楼梯或低顶。

`report.json` 分别记录 `selected`、`completed`、`failed`、`unrun` 和启动、执行耗时。各房屋另有 `housing-gametest-<id>.json`。探针、测试模板和 GameTest 不进入主交付 JAR。

实际城镇中的地形衔接、生成位置及居民分配继续由 `tools/housing/native_evidence.py --checks npc` 检查。小场景通过后，只在相关生成输入变化时追加对应新世界检查。
