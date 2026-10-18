# 灵石袋与随身灵石来源

`myvillage:spirit_stone_bag` 是不可堆叠物品，创造栏与皮革/线/下品灵石合成可得。物品栏和副手生效；原版工作台合成先潜行右键取出一叠。自动收纳切换只影响入袋，暂停的袋仍可支付。

容量和扫描间隔唯一来源为 `data/myvillage/spirit_stones/bag.json`；每格仍遵守实际物品堆叠上限。`tools/gen_spirit_stone_bag.py` 维护模型、配方、发现进度、袋标签和双语名称/提示；沿用原版 bundle 图标，不新增容器 GUI。

## 存储与事务

`SpiritStoneStorage` 使用原版 `DataComponents.CONTAINER` / `ItemContainerContents` 保存原物品及完整组件，自动开关保存在同物品 `CUSTOM_DATA` 的独立键中。不做人物附件或全局 UUID 余额。死亡、丢弃、放箱与存档均归原版物品生命周期；不额外 Clone，不额外散落一次内容。

袋标签 `myvillage:spirit_stone_bags` 默认只收录本物品。仅单件袋、无内嵌 container 的下品灵石可自动收纳，不嵌套袋子。标签重载失去灵石资格后，原内容保留、停止自动支付，仍可取出。

- `SpiritStoneStorage.count/remove/insertBags` 共用原物品身份；短款整批不扣。
- `Expanded` 保留外层槽位和袋内位置，让既有炼器完整分配器处理重叠标签，避免重复计数。
- 修炼沿用 `InventoryBatchRemoval.transact`，失败恢复原槽位的整袋组件，提交后异常不返款。
- 交易保持普通具体货币约束，按副本规划背包与袋；完整计划成功后提交命簿报价，再提交同一库存快照。命名或非同货币灵石不会变为普通货币。
- 丹炉补料仍只占一个燃料槽，不混合不同物品或组件；取袋内燃料也只取一炉。
- 自动收纳在服务器玩家 tick 与合法拾取 Pre 发生；拾取尊重其他插件拒绝、原版拾取延迟和目标。袋满则余量留在原位置。

## 饰品适配接口

`SpiritStoneSources.register(ResourceLocation, Provider)` 接入可选额外来源。提供器接受 `Player`，返回具有稳定槽键、实时 `ItemStack` 读取和可替换写入的 `Slot`。默认来源为物品栏，额外来源只接受单件袋；相同槽键/相同堆叠实例去重。饰品适配在两侧共同注册，由饰品模组同步自己的槽位；公共代码不依赖其 client-only 类型。

写入器必须是同服务器线程的本地槽位替换，不运行异步操作、外部事件或可能失败的外部提交；槽键必须对物理位置唯一，不能把同一位置复制成两个来源。`Access` 对完整快照做修改前校验；普通产物只进入 36 个背包槽，不放入装备或饰品槽。

当前提供接口，尚未安装或验收具体饰品模组。真实交易窗口、广泛多人、外观/手感/FPS 与玩家验收按专项测试记录分别报告。

实际检查、失败保留与交付 jar 身份见 [0.64.4 验证记录](../testing/2026-10-18-spirit-stone-bag.md)。
