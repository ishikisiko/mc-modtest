# 资质点数与六类培养的局部检查

本轮按用户要求快速迭代，仅选择资质数值、相关存档与网络编解码测试，并构建当前 JAR。功能说明见 [资质培养](../features/aptitude-points.md)。

## 固定输入

- 基于 main `86319d962edd9040a186df137f5ae31adf62f037`，版本 `0.122.0`；玩家格式6、命簿格式14、协议65。
- Colab 会话 `aptitude-points-20261108`，CPU 高内存；复用已校验依赖缓存及根目录 PAL，不使用本机 Gradle 或游戏运行。
- 构建前校验全部输入文件的 SHA256，完整清单摘要为 `6b5c34d6e78d32ef6e3454ab48a7fa87c34eadcb426e86006b11c782fa102089`。
- 外部方块目录取当前工作区 `exmod/mod_block_catalog.json`，SHA256 `56f6844e00d975f01483332590ee62bb5399b62c92a0e74618a35baf6f9f7c24`。

## 已完成的静态检查

- 资质资源生成器 `--check`：74个文件一致。
- 功法目录生成器 `--check`：输出一致。
- 技艺资质、天命与物品资源校验通过。
- 原有 `validate_cultivation_core.py` 返回38项问题；在未修改的上述 main 基线与本次候选逐项对照，新增问题为0。该旧检查仍处于失败状态，未改动无关断言。

## 已选择的 Java 检查

| 测试类 | 覆盖内容 |
|---|---|
| AptitudePointsTest | 独立元素点数、超过100的技艺资质、确定性兵器初值、培养存档、伤害曲线、边界与已确认天命 |
| SpiritualRootGeneratorTest | 稳定生成、输入顺序、独立点数范围及无效输入 |
| CraftingAptitudesTest | 三项技艺保存、单项修改和非法值拒绝 |
| CultivationSnapshotPayloadTest | 实际网络缓冲区内传递兵器资质、实践记录和授业进度 |
| NpcCultivationTest | 命簿资质保存及相邻修炼状态 |
| WorldSimPayloadCodecTest | NPC 详情中的实际资质网络往返 |

实际结果：selected=6类／38项，completed=6类／38项，failed=0，skipped=0。所有测试源码编译通过，当前交付包构建成功。标准构建用时16分43秒，其中包含已有全局资源生成；没有运行完整 release 或游戏验收。

JAR 为 `myvillage-0.122.0.jar`，55,091,163字节，SHA256：

```text
3416296d21b50f697d882dd49669752795cf33bd5291babfbf01fba5962a425e
```

包内版本与上述版本一致，逐字节核对236项资质规则、培养资源及现有功法数据，并确认正式运行类已打包。原始构建失败／成功日志、JUnit XML、输入文件清单和基线对照保存在交付证据包 `build/aptitude-evidence-0.122.0.zip`。该记录在构建完成后补写，运行代码、测试和工具与固定输入清单一致。

## 保留的失败

1. 初次编译发现当前 DFU 没有 `Codec.longRange`，改用非负校验的 `Codec.LONG`。
2. 首次组合任务的 `--tests` 位于 `jar` 之后，Gradle 拒绝参数；调整至 `test` 参数范围。
3. Java 编译通过后，资源生成缺少被 Git 忽略的外部方块目录；补入原工作区实际文件并记录哈希。
4. 失败的资源生成已清理部分派生函数文件，增量重试的固定输入核对发现缺失；从原始源码包恢复后重新应用候选增量，保留严格哈希校验。

## 验收边界

六类培养已接入正式服务器入口。本轮没有启动 Minecraft；真实交互、存档重启、玩家手感、长期数值平衡与多人体验均为 `not_verified`。课程的单位测试与编译结果不能代替这些观察。
