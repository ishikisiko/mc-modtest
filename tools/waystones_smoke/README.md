# Waystones 灵石袋定向检查

重型命令在 Colab 的固定源副本执行。源码归档之外，需要同时上传根目录的精确 PAL JAR，以及被 Git 忽略的 `exmod/mod_block_catalog.json`；宗门资源导出会读取该文件，不能只上传 `git archive`。Waystones 和 Balm 的版本由 `gradle.properties` 固定。

支付单元测试直接读取当前 authored JSON，可以先运行而不等待城市资源生成：

```bash
./gradlew -I tools/waystones_smoke/unit.init.gradle test \
  --tests 'com.example.myvillage.compat.waystones.*' \
  --tests 'com.example.myvillage.spiritstone.*' \
  --tests 'com.example.myvillage.cultivation.meditation.SpiritStoneCompatibilityTest' \
  --tests 'com.example.myvillage.cultivation.meditation.InventoryBatchRemovalTest' \
  -x processResources --no-daemon --max-workers=2
```

交付仍需通过正常资源打包和 jar 构建，单元测试的直接资源入口不替代打包检查。

原生检查要求先完成资源处理并安装 Xvfb、xdotool、Mesa 和 ImageMagick（`import`、`convert`）；使用新的证据目录，不覆盖失败记录：

```bash
MYVILLAGE_CLOUD_CHECK=waystones python3 tools/waystones_smoke/run.py \
  /content/waystones-native-evidence
```

仅重验带依赖的收费场景时，可在新证据目录后添加 `--with-only`；复用无依赖启动结果时，应同时记录原证据路径及相同运行时代码。

仅重验同步接口的失败与退款探针时使用 `--failure-only`，结果中的实际菜单支付保持 `unrun`，需单独引用已有的同源菜单操作证据。失败夹具在费用校验后将目标设为未加载维度，要求原生接口返回 `InvalidDimension`、留下已扣款的一次性回执，并完整恢复原袋；拆除方块不足以让这个版本的同步接口失效。

脚本先在没有 Waystones 的新世界检查启动，再显式加入 `-Pwaystones_test -PwaystonesEvidence`，用真实客户端右键和选择目的地，回读大整数袋余额。管理员探针只负责准备夹具、读取状态，以及验证同步接口失败退款和重复结果通知，不将探针命令算作玩家输入。两个服务器、客户端和虚拟显示均在退出路径关闭；Colab 会话按用户授权单独处理。

0.114.2 的收费夹具使用筑基初期、相隔 5000 格的两块传送石和暂停收纳的大整数灵石袋。`pricing` 探针检查 50 个原生报价，覆盖大境界、同境界早晚阶段、1000/1001 格边界、远距离封顶、Y 坐标、斜线距离与跨维度系数。完整报价表写入服务器日志。菜单探针只观察原生 `WaystoneButton` 的实际费用对象，要求客户端显示 15 枚、可支付且按钮可用；随后用映射鼠标选择目的地，要求真实位置变化和余额精确减少 15。探针未打包进交付 JAR。

`src/waystonesEvidence` 中的命令只能进入显式探针构建。使用 `-PwaystonesEvidence` 打 jar 会被构建脚本拒绝，交付包不包含这些命令。
