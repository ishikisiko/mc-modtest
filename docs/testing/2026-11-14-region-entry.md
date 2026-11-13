# 0.131.1 洲域与城镇报幕检查

本轮只检查用户选定的洲域 A 款居中书法、城镇 C 款竖排题签。此前的小BOSS测试保持暂停，没有执行战斗秘境采集。

## 固定源与交付物

- 产品提交：`23869a17a2a9ca82ae20aae7fbde65015ea0faaa`。其后的记录更新只改文档。
- 完整源归档：`arrival-source.tar.gz`，100,839,635 字节，SHA-256 `97aa1ecafcb9bde523b2a14a3d60260b562c77110f30c4911bbe96c674f55e04`。
- JAR：`myvillage-0.131.1.jar`，78,777,656 字节，SHA-256 `f9ebe1c8af267171f81c73944b15ead3000f6b03a6f16b4f56a2600da2e8439e`。
- 证据归档：`proof.tar.gz`，70,345,803 字节，SHA-256 `166a2bf6a39a2607efca448d461c0e85fad14ec47ba34dcac759d3e706f773fe`。
- PAL 精确根 JAR：`PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar`，SHA-256 `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`，与本地依赖一致。

运行于复用的 Colab CPU High-RAM 会话 `quest-realms-runtime-20261114`，使用独立 `/content/arrival-runtime` 目录。主世界复制自此前已通过的 `quest-native-5` 隔离世界，只复用地形；没有打开用户存档，也没有恢复暂停的小BOSS世界。测试世界为 `arrival_87fd8138`。

沿用已核验的 0.131.0 JAR 中未改变的生成资源，基包 SHA-256 为 `34e24337cdbb89610c1ec1cc6941004923c230ebd0f69999cf9fc8ba3743c24f`。完整 Git 差异确认生成输入没有变化；未经过转换的资源逐字节比对，NPC 打包器和官道出口生成器按当前输入复算后与基包逐字节一致。只更新报幕配置和版本元数据，Java 从完整当前源编译。原生运行的全部 2,391 个类及资源再次与最终 JAR 逐字节比对，源清单在构建和运行前后保持一致。

## 局部结果

命令：`./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx3g -x generateAllStructures -x processResources test --tests 'com.example.myvillage.arrival.*' jar`。

15 项单元检查全部通过：服务器停留确认与离界缓冲、冷却和原地不重复、依次播放与旧目的地取消、淡入淡出、有界中文载荷、八省城与湾口台地集的真实位置。JAR 的共享语言字节检查通过。

原生采集：`python3 tools/arrival_evidence.py --out reports/arrival-native-1 --previous /content/quest-runtime/reports/quest-native-5/report.json`。

| selected | completed | failed | unrun |
|---|---|---|---|
| 独立服务器启动 | 通过 | 无 | 无 |
| 洲域 A 报幕 | 通过 | 无 | 无 |
| 城镇 C 题签 | 通过 | 无 | 无 |
| 报幕期间实际 W 键移动 | 通过 | 无 | 无 |
| 同城传送不重复 | 通过 | 无 | 无 |
| 实际 F1 隐藏后等待，恢复后播放 | 通过 | 无 | 无 |
| 跨维度取消旧提示 | 通过 | 无 | 无 |

RCON 用于设置天气、准备所在地与维度、读取权威位置；移动和 F1 使用受焦点保护的客户端按键。本轮没有验证连续徒步跨洲。菜单等待、死亡和重登的玩家体验未单独采集；多人、声音、玩家 GPU FPS 和用户外观认可保持 `not_verified`。

截图 `province-A.png`、`town-C.png`、`province-A-resumed.png`、`dimension-cleared.png` 为原生客户端采集，状态为 `captured_for_review`。首组画面位于城镇入口，镜头近处有台阶；它们用于观察报幕字形、布局和遮挡，不作为建筑观感验收。

## 失败与修正

1. 资源准备时，旧固定源清单只覆盖 36 个变化文件，新清单覆盖完整 53,886 个文件，直接比较会误报全部资源变化。改用完整 Git 树差异核对，保留“只改变报幕配置和版本元数据”的资源约束。
2. 原始 NPC JSON 与打包引用、官道出口中间文件与正式生成指纹存在格式或摘要差异。分别调用所属打包器和生成器复算，核对正式运行形式；其余资源继续逐字节比较。诊断与原脚本保存在本轮 `reports/region-entry-runtime-20261114/`。
3. 首次构建缺少精确根 PAL JAR，在项目配置阶段停止；复制同一已核验依赖后继续。原日志 `logs/arrival-build-1-failed.log` 保留。
4. 第二次构建中 14 项单元检查通过，1 项因测试错误假设“当前主世界仍有阡镇”失败。当前 `WorldPlanRules` 明确不生成主世界阡镇；移除无效查询和地名配置，检查明确断言主世界无阡镇，并继续严格检查湾口和八省城的位置。原堆栈、结果 XML、HTML 报告和固定源清单保留于证据归档的 `failed-build-2/`，原构建日志为 `logs/arrival-build-2-failed.log`。

最终 15 项单元与 7 项原生场景通过，没有未运行的选中场景。服务器正常退出，`forced_stop=false`、`returncode=0`、`owned_survivors=[]`；客户端 7 个所属进程已确认终止，最终进程检查无遗留 Java、Xvfb 或启动器。Colab 会话保留，未关闭。未安装到用户电脑。

## 下载与画面

- [交付 JAR](https://assets.ishiki333hhh.win/previews/region-entry-runtime-20261114/myvillage-0.131.1.jar)
- [洲域 A 实机截图](https://assets.ishiki333hhh.win/previews/region-entry-runtime-20261114/province-A.png)
- [城镇 C 实机截图](https://assets.ishiki333hhh.win/previews/region-entry-runtime-20261114/town-C.png)
- [完整证据](https://assets.ishiki333hhh.win/previews/region-entry-runtime-20261114/proof.tar.gz)
