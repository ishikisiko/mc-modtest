# 云山活城与 V4：实施和检查记录

开发分支 `feat/yunshan-living-city-worldplan-v4-20261018`，基于 `d53cbca5` 的 0.63.1 继续开发；不合并 main。交付版本 0.64.0，协议 33，命簿 payload 6。用户明确允许减少环境受限测试，主要完成实现。

## 已执行

在隔离 Linux 工作区完成源码修改、整城资源编译和纯 JDK/Gson 测试，没有启动玩家电脑的 Minecraft。GitHub 域名无法从工作区解析，源码经新分支的临时 GitHub Actions 导出、校验 bundle 后取得；纯核依赖同样固定版本并校验。没有凭下载工具存在就声称已构建或实机通过。

- D1 原蓝图 70 所民居和四栋建筑；90 项原有组合几何检查通过。224×82×272、238 个完整区块列模板，共 4,996,096 个方块记录逐模板编码回读一致，七处题字保留。
- `check_v4.py`：38 项旧冻结资源哈希检查，238 个新模板哈希，压缩默认预设与冻结 JSON 一致，17 个 NPC 站位和设施在最终模板上的 58 项探针通过。
- 61 项 focused 纯 Java 检查通过，覆盖商业 CAS/玩家绑定/旧请求/店主死亡/有限配药/付费恢复冷却/正常序列化与 v5 迁移、原生活和战斗档案、V4 压缩与全部 60,928 城市地表列、V1/V2/V3 保留以及原 510 种子规划回归。纯 JDK 运行不是完整 NeoForge 编译或游戏启动。
- 16 项 Python 检查通过：D1 适配、压缩边界、旧世界显式启用、原 NPC 生活元数据和验收器有界解压。

第一轮纯核检查暴露 V4 高地沿用旧半高上限和新文件 import 的 pure-guard 不兼容，已修复后复验；旧版本高度限制未放宽。一个运行缺少 `src/test/resources`、一次 Python 命令拼错已有模块名，属于准备错误，纠正后重新执行，原失败保留在工作区日志中。

## 隔离 NeoForge 编译与原生类型测试

本会话没有可调用的 Modal 连接或凭据，沿用仓库已有的隔离 GitHub Actions 方式完成一次局部构建，没有本机重型回退。实际实现提交为 `e52125a5876eb8929e54559dfe3a90e5a317d230`；成功构建固定源为 `54c1f3cf64538e88c3d4cf20753b6a97c74641ae`，二者仅临时构建工作流不同。

成功运行：[39082517591](https://github.com/ishikisiko/mc-modtest/actions/runs/39082517591)，artifact `yunshan-v4-build-4`（ID `12118793862`）。Java 21 / Minecraft 1.21.1 / NeoForge 21.1.233，PAL 根依赖的 SHA256 经原项目固定值校验。

`compileJava`、`compileTestJava`、资源生成和 `jar` 全部成功；只运行下列 29 项 Java 测试，零失败、零错误、零跳过，不称为全仓测试：

| 测试类 | 数量 |
|---|---:|
| `WorldSimCommerceTest` | 12 |
| `CommerceInventoryTest` | 5 |
| `CommercePayloadTest` | 2 |
| `WorldPlanV4Test` | 5 |
| `WorldPlanTest` | 5 |

原生 ItemStack 测试检查组件匹配、满包原子拒绝、收益装袋、特殊组件物品不被降格收购；载荷测试覆盖实际 RegistryFriendlyByteBuf 往返和输入边界。其余所选测试与前述纯核检查有重合，不能相加声称 90 项独立测试。

Gradle 命令为：

```bash
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx3g \
  -x generateAllStructures test \
  --tests 'com.example.myvillage.sim.WorldSimCommerceTest' \
  --tests 'com.example.myvillage.sim.runtime.commerce.*' \
  --tests 'com.example.myvillage.worldplan.WorldPlanV4Test' \
  --tests 'com.example.myvillage.worldplan.WorldPlanTest' jar
```

本次跳过无关全建筑重生器，沿用已跟踪的旧结构；V4 `generateWorldPlanAssets` 和 `generateNpcLifePlaces` 实际执行。16 项 Python、V4 资源检查和生活元数据一致性在云端也通过。Gradle 实测 5m 5s，包括首次下载/生成映射依赖，不是游戏运行时间。保留了现有风格的 EventBusSubscriber 弃用警告，没有错误。

前一次运行 [39082423555](https://github.com/ishikisiko/mc-modtest/actions/runs/39082423555) 的校验源码传输与分支推送成功、16 项 Python 通过，但 `test jar --tests ...` 把选项绑定到了 jar，报 `Unknown command-line option '--tests'`。该次未进行 Java 编译；修正选项所属任务后重跑，没有修改测试断言掩盖失败。原失败 artifact `12119506896` 保留。

## 交付产物

- `myvillage-0.64.0.jar`，5,598,935 字节。
- SHA256：`67f3eb473f44ee48660e1388bbb1b18b5a47b66088888dcfa7cf59d030ef165e`。
- 下载的构建 ZIP SHA256：`30e1b008a096e8b22ceb4c78e8642639e1038110e75ba1094fddaa2c1d2ad62b`；ZIP CRC 与 jar SHA256 均重新核对。
- 对实际 jar 执行 `tools.worldplan.acceptance.check_jar()` 通过：238 个 V4 模板、35 个旧模板、四代冻结规则、V4 默认预设、原世界保护 mixins、商业菜单类和配置均存在。
- 61 个推送源码文件逐一与隔离开发工作区的 SHA256 一致。临时源传输片段已删除；收尾仅记录证据并移除临时工作流，不改变已构建的 mod 内容。

## 尚未执行 / 验收边界

未执行新 V4 实际生成与真实客户端买卖，不运行全量 release、自动 Chunky 或用户电脑游戏。待实测：自然城观感、门/楼梯与 NPC 寻路、真实双客户端争购、死亡/撤离时菜单行为、收费恢复、世界实际重启和旧 V1/V2/V3 实际重新进入。玩家外观、手感、音效、经济平衡、FPS 与广泛多人均 `not_verified`。

商业账本与玩家库存同线程提交，支持正常保存/重启，不声明异常断电下多个存档文件之间的原子事务。完整功能范围和接手操作见 [KB58](../ai-kb/58_yunshan_living_city.md)。
