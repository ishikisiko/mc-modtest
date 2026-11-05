# 传送符定向验证

在独立 Colab 源码目录运行，复用已有 Gradle 缓存。源码须固定提交并记录归档、根目录 PAL JAR、Waystones 和 Balm 身份；额外输入 `exmod/mod_block_catalog.json` 同样核对后复制。

先运行制符和 Waystones 相关单元测试、制符生成器检查，再正常打包受影响资源。通用建筑资源仅可在其生成输入一致时复用，不把缓存里的旧测试结果当作本次结果。

```bash
python3 tools/gen_talisman_assets.py --check
python3 -m unittest tools.tests.test_talisman_assets
./gradlew -I tools/waystones_smoke/unit.init.gradle test \
  --tests 'com.example.myvillage.talisman.*' \
  --tests 'com.example.myvillage.compat.waystones.*' -x processResources
MYVILLAGE_CLOUD_CHECK=travel-talismans python3 tools/travel_talisman_smoke/run.py \
  /content/travel-native-new-run
```

原生检查包含无 Waystones 的两侧启动和闪回操作、带依赖的跨维度闪回、回城列表翻页、取消和失败不扣费、两个制符选项、两名真实客户端的标记隔离，以及服务器重启后恢复标记。每轮使用新的输出目录，保留失败与 `selected/completed/failed/unrun`。死亡后复制使用 attachment `copyOnDeath`；此脚本尚未覆盖实际死亡操作。

`TravelTalismanProbe` 只用于夹具和读回，`WaystonesTravelProbe` 的单次事件取消用于制造失败。两个类仅在 `-PtravelTalismanEvidence` 时编译，带此标志打包会被拒绝。普通施符、选择菜单、标记和持续制符均由映射键鼠触发；`mapped-inputs.json` 记录操作，RCON 日志记录准备和读回。

脚本退出时关闭自己的服务器、客户端和 Xvfb。保留 Colab 会话，是否回收由用户决定。截图仅为 `captured_for_review`；玩家外观、手感和真实电脑 FPS 保持 `not_verified`。
