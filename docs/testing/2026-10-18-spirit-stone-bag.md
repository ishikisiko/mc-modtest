# 灵石袋交付检查（0.64.4）

产品提交：`b4d1b4a7c1fff73304a0285e1a3c398b6d03ab3d`；测试环境修正：`34838f6b0bdbf76ab7d479bf7d0c281a25c58a9d`；最终材料提示修正与交付 jar：`f749d9553e47ba8b58c0bf35a77b86105d841568`。后续只改交付文档。

## 范围

灵石袋可合成、可从创造栏获得，放在物品栏/副手自动收纳原灵石及组件；右键切换自动收纳，潜行右键暂停此袋并取出一叠。参数以 `spirit_stones/bag.json` 为准。普通工作台合成先取出原料。

修炼、宗门供奉、炼器/维修、丹炉补料、交易买卖及客栈支付读取随身袋；交易仍只接受配置中的普通具体货币。修炼/交易余额和炼器材料提示也包含袋。饰品模组通过 [KB59 的来源接口](../ai-kb/59_spirit_stone_bag.md) 接入，当前没有安装具体饰品模组。

## 本地检查

87 项相关 Python 检查通过：

```bash
python3 -m unittest tools.tests.test_spirit_stone_bag tools.tests.test_validate_spirit_stone_resources tools.tests.test_validate_cultivation_gain tools.tests.test_validate_alchemy tools.tests.test_modal_runner
```

材料提示修正后，39 项直接相关 Python 回归通过：`test_spirit_stone_bag`、`test_forging`、`test_modal_runner`。这些集合有重叠，不合并为唯一测试总数。

`gen_spirit_stone_bag.py --check`、mod items、灵石资源、cultivation gain 与炼丹检查通过。最终 jar 再通过灵石资源 `--require-current-jar` 和炼丹打包检查。

## Modal 与失败保留

1. `reports/modal-runs/20261018T073209Z-1f0e5037` / `b4d1b4a7`：130 项 Java 中 129 项通过，唯一失败为独立测试 bootstrap 没有 Forge 注册表 ID 同步上下文，直接调用原版 ItemStack.STREAM_CODEC 被保护检查拒绝；存档往返此前已通过。没有启动客户端。原日志、jar 和测试 XML 均保留，Sandbox `sb-01M56YSRRNDGXNKECN7QC4RCQG` 销毁确认。
2. `reports/modal-runs/20261018T073721Z-85dd718a` / `34838f6b`：改为独立测试可用的注册表名称流编码，保留完整内容/开关往返断言；真实连接覆盖 ID 同步。26 个 suite、130 项 Java 全通过，7 个实际游戏场景完成，13 项检查全部通过。Sandbox `sb-01M56Z39CPN3FAHPVGBKDG18YF` 产物已下载校验、销毁确认。
3. 最后看图发现炼器的扣料已读袋，但数量颜色还只数散装物品。修正客户端来源和共用显示/数量方法，新增对应回归，运行 `reports/modal-runs/20261018T074646Z-c3008ee2` / `f749d955`：26 个 suite、131 项 Java 全通过；针对材料提示的 4 项真实客户端补充检查通过，画面人工查看确认四项材料均显示充足。Sandbox `sb-01M56ZMGWXY5076MCWHG849MZP` 产物已下载校验、销毁确认。

初次失败没有改变游戏代码或降低断言；独立单元测试仍验证保存/恢复和名称流编码，真实客户端验证原版同步和实际使用。SDK 曾报告状态查询连接重试，最终各运行状态与销毁结果均已读取确认；没有自动重跑测试。

## 实际游戏证据

主运行使用真实客户端映射右键、Shift+右键、B/X 和炼器按钮；RCON 仅做物品/位置/修炼资格等 fixture 与服务器回读，没有 DevBridge 或命令代替物品使用。

- 自动收纳后没有散装灵石；暂停后散装保留，恢复后再入袋；潜行取出守恒。
- 36 格背包装满石头，副手袋从 30 到 37，接收地上 7 枚灵石。
- 按 B 开始灵石修炼，副手袋从 37 到 36，散装灵石为零；按 X 停止。
- 真实炼器菜单从袋中准确支付 2 枚灵石，袋从 30 到 28，返回一把青锋剑。
- 沿用同一个实际玩家文件重启，整份物品栏及袋内 28 枚灵石一致。
- `keepInventory=false` 的真实玩家死亡后，掉落的灵石袋仍包含 28 枚灵石。

主运行输出：`artifacts/out/preview/spirit_stone_bag/runtime/evidence.json` 和两张截图。最终材料提示输出：`artifacts/out/preview/spirit_stone_bag/display/evidence.json`、`forging-bag-costs-ready.png`。

最后运行不重复未变化的生命周期场景：两份 jar 的 class 逐项比较，只有 `ForgingScreen.class` 与 `ForgingIngredients.class` 不同，差异为材料显示/可用数量；存取、支付、拾取、序列化、交易与生命周期处理 class 一致。

## 交付与限制

最终 jar：`build/libs/myvillage-0.64.4.jar`。

大小：5,639,318 bytes；SHA-256：`feb2a6cbd06a111b0c7a0b824305da89cf3fc3dc6c9ba4d60c2439e9e00f71d0`。

8 个变更资源逐字节与源文件一致，袋物品、事件、存储、来源和规则的 5 个主 class 均存在。普通物品存储沿用原版组件，不额外拷贝 Clone 或重复散落内容。

交易买卖、满背包/缺款拒绝、普通货币限制、炼器重叠分配、丹炉取一炉与原组件恢复由 Java 测试覆盖；实际 NPC 交易窗口、实际宗门供奉/丹炉交互、具体饰品模组、广泛多人、自然获取体验和玩家外观/手感/FPS 验收仍为 `not_verified`。本次未安装到玩家设备，也未启动玩家游戏。
