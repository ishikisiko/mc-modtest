# 灵石袋大整数存储验证（0.64.5）

## 改动和验收边界

- 旧 container 存储迁移到物品组件 `myvillage:spirit_stone_balance`：一个完整物品/组件身份对应 count=1 原型及 BigInteger 数量；无固定容量，不随颗数展开物品堆。
- `SpiritStoneStorage.remove` 接受大整数；当前消费系统仍保持本次有界成本。炼器/炼丹只投影本次需求，交易继续核验普通具体货币。短款、满背包和过期快照保持原子失败。
- 精确品级换算支持大整数乘除及余数，配置当前只定义下品灵石。测试的多级倍率为 fixture，不启用高阶物品或自动兑换。
- 新组件与原型不可变，旧 container 幂等迁移；标签资格消失后保留取出路径。无全局钱包、无 Clone 复制，沿用物品死亡/丢弃生命周期。

## 本地检查

- `python3 tools/gen_spirit_stone_bag.py --check` 与 `python3 tools/validate_spirit_stone_resources.py` 通过。
- 袋子、灵石资源、修炼增益、炼丹和 Modal runner 的 focused Python tests：88 通过。
- 袋子与炼器相关工具测试：16 通过（与上组有重叠，不累加成独立用例数）。

## 首次失败（已保留）

- Run `20261018T081159Z-8a3a5fc1`，源 `2049e544d90591c346e83ac8391414aa027bbcc1`，Sandbox `sb-01M5712PDHDMVBFN2BXY0QP0J9`。137 项测试中 19 项因同一账本编码器静态初始化循环失败；游戏未运行。
- 根因：先初始化嵌套 Entry 时读取外层 QUANTITY，外层又读取尚未初始化的 Entry.CODEC。修复把条目、账本编码器按依赖顺序集中在外层，保留原迁移与交易断言。
- 失败 XML、日志及 jar 留在对应 `reports/modal-runs/.../artifacts/`；失败 jar 不作为交付。Sandbox 终止 confirmed。

## 两次采集器失败（已保留）

- `20261018T081726Z-b1643d6c`，源 `04bb864cc4ddbd2526c4a902445991c31fd785ff`，Sandbox `sb-01M571CPHDXX2PZJY5HM945MEM`：27 套件、137 Java 测试全部通过；实际收纳/暂停/取出通过，但取出后的重新开启超时。采集器随后加入服务器端潜行标志读回，在实际 Shift 松键生效后才继续下一次映射点击，不放松余额断言。
- `20261018T082532Z-f2222778`，源 `2902ca8a0056dd9b2484ded7ba20055630682c48`，Sandbox `sb-01M571VH6XR36MZ807FQGQBT4Q`：Java 测试与 jar 通过；普通收纳/开关/取出/重新收纳、满背包副手拾取、旧数据迁移、副手修炼、巨额锻造及一次取出 64 枚通过。巨额重新收纳超时：场景仍瞄准炼器台，普通右键被工作台交互消费。修正场景先瞄向空中，并在 README/KB 明确对空右键。
- 两次产物、输入读回、日志和截图均保留在各自 `reports/modal-runs/.../artifacts/`；两 Sandbox 均已终止 confirmed。各次完成场景均按证据 JSON 记录，未执行的重启/死亡不视作通过。

## Modal 最终固定源

- 源提交：`a890bda6001fbe8fa04e53dffa2ac2ea725842fc`；与上一轮相比只修改采集场景及不打包说明，Java/资源内容不变。
- Preset：`spirit-bag-runtime`；本机不运行 Gradle 或 Minecraft。
- Run：`20261018T083535Z-6c1795ba`；Sandbox：`sb-01M572DWWY8X9ZSH7HJQTTSGB2`。
- 27 套件、137 项 Java 测试通过，无失败/跳过；覆盖 `10^100` 级计数、300 种不同组件、迁移幂等、跨袋支付、严格货币身份、重叠材料分配、补料及编解码。
- 9 个 selected 场景全部 completed，16 项实际客户端检查通过，failures 为空：收纳、映射右键/潜行取出、满背包副手拾取、旧袋迁移、副手 B/X 修炼、巨额袋余额锻造、巨额取出/重新收纳、真实客户端/服务器重启、实际死亡掉落。
- 实际巨额流程为 `10^40+17` → 锻造扣 2 → 取出 64 → 重新收纳 64 → 保存/重启 → 死亡掉落；最终数量 `10^40+15`，存储仍仅一个条目。
- 固定源任务 status `passed`、artifacts `downloaded_and_verified`、termination `confirmed`；不留运行中的 Sandbox。
- `build/libs/myvillage-0.64.5.jar`，5,655,260 字节；SHA-256 `b05c44919d71dc388cb6e5bb600cc7efd7a0c9907d9469bb54141a4814257d89`。已比对实际运行编译类与 jar 类字节全部一致，并检查内含版本、无容量槽位配置及下品单位配置。
- 日志、测试 XML、逐次输入读回和截图：`reports/modal-runs/20261018T083535Z-6c1795ba/artifacts/`。最后仅补充不打包验证说明，不重跑未受影响的游戏。

## 未验证项

具体饰品模组尚未接入；真实 NPC 交易窗口、供奉和丹炉交互、多客户端、玩家外观/手感/FPS 不因本次局部测试视为通过。相关消费计划由 Java 测试覆盖，实际映射输入覆盖袋子、修炼和炼器；开发者证据不替代玩家验收。
