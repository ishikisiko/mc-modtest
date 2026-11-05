# 灵石袋与随身灵石来源

`myvillage:spirit_stone_bag` 是不可堆叠物品，创造栏与皮革/线/下品灵石合成可得。物品栏和副手生效；原版工作台合成先潜行右键取出一叠。对空右键切换自动收纳；瞄着工作台时普通右键按原版规则交互工作台。自动收纳切换只影响入袋，暂停的袋仍可支付。

数量没有固定容量上限；扫描间隔唯一来源为 `data/myvillage/spirit_stones/bag.json`。取出时才按实际物品堆叠上限生成一叠。`tools/gen_spirit_stone_bag.py` 维护模型、配方、发现进度、袋标签和双语名称/提示；沿用原版 bundle 图标，不新增容器 GUI。

## 存储与事务

`SpiritStoneStorage` 使用持久、同步的 `myvillage:spirit_stone_balance` 组件：每种精确物品/组件身份仅存一份 count=1 原型和十进制字符串数量。`SpiritStoneLedger` 使用 `BigInteger`，不以 int/long/浮点保存余额；组件不可变，读取原型返回副本。自动开关保存在同物品 `CUSTOM_DATA` 的独立键中。不做人物附件或全局 UUID 余额。死亡、丢弃、放箱与存档均归原版物品生命周期；不额外 Clone，不额外散落一次内容。

袋标签 `myvillage:spirit_stone_bags` 默认只收录本物品。仅单件袋、无内嵌 container/ledger 的已配置品级灵石可自动收纳，不嵌套袋子。标签重载失去灵石资格后，原内容保留、停止自动支付，仍可取出。

- `SpiritStoneStorage.count/remove/insertBags` 共用原物品身份；短款整批不扣。
- `Expanded` 每种身份只向既有完整材料分配器投影一项，数量上界为本次总需求；写回仅从真实大整数余额减去消耗。不会展开成数万/亿叠物品；炼器需求仍是本次有界 int 成本，重叠标签不重复计数。
- 修炼沿用 `InventoryBatchRemoval.transact`，失败恢复原槽位的整袋组件，提交后异常不返款。
- 交易保持普通具体货币约束，按副本规划背包与袋；完整计划成功后提交命簿报价，再提交同一库存快照。命名或非同货币灵石不会变为普通货币。
- 丹炉补料仍只占一个燃料槽，不混合不同物品或组件；取袋内燃料也只取一炉。
- 自动收纳在服务器玩家 tick 与合法拾取 Pre 发生；拾取尊重其他插件拒绝、原版拾取延迟和目标。暂停或不合法的入袋请求保留原物品。

## 迁移、数量与品级

旧版 `minecraft:container` 的实际物品堆在首次服务器扫描、入袋或扣款时合并为数量条目，随后移除旧组件；暂停袋也扫描迁移。读取兼容两种表示，迁移幂等，不重复入账。失去标签资格或意外遗留的物品不会丢弃，仍能潜行取出。数量为严格正整数字符串，零条目移除，拒绝负数、小数和指数表示。

计算和存储随物品身份数及数量位数增长，不随灵石颗数线性增长；没有游戏设定的数量/槽位上限，但仍受内存、存档与网络包的物理限制。取出最多一叠；满背包失败保持整个袋子不变。修炼、供奉、交易、炼器和炼丹的余额比较均避免 long 溢出，现有单次消费仍按各系统有界成本执行。

`data/myvillage/spirit_stones/denominations.json` 是品级、普通物品 ID、标签和整数量级的唯一来源，现有下品、中品、上品三级。`SpiritStoneDenominations` 提供大整数价值合计、拆分和余数精确算术；量级必须为前一级的正整数倍。

### 高阶灵石与主动兑换（0.73.1）

中品 `medium_grade_spirit_stone`、上品 `high_grade_spirit_stone` 是普通可堆叠物品，创造栏或消耗下一级灵石兑换获取。没有新增矿脉、冶炼或工作台压缩配方。分级公共标签独立；高阶石不进入下品一比一支付标签，现有修炼、供奉、炼丹、炼器和交易仍消耗原先的下品。

所有玩家可输入 `/灵石` 查看用法；`/灵石 余额` 显示随身各级数量；`/灵石 兑换 中品|上品 [份数]` 每份换出一枚；`/灵石 拆分 中品|上品 [份数]` 每份消耗一枚，省略份数时为一份。仅操作执行者自己的物品栏和已接入的灵石袋，无他人目标参数，无 OP 要求。

`SpiritStoneExchange` 先在副本计算成本、扣除、装袋或放入最多 36 个普通槽，最后 `SpiritStoneSources.Access.commit` 整体提交。余额不足、背包放不下、快照变动均不扣款；大额输出优先直接加进袋内大整数账本，不展开为堆叠。主动兑换允许存入已暂停的袋，保留暂停状态。命名、附带特殊组件和外部同级物品不会洗成普通货币；入袋仍保留其原始身份。余额显示包含同级物品，兑换明确仅接受普通本模组物品。

高阶外观原画归 `tools/art/spirit_stones/`，通过 `tools/gen_spirit_stone_grades.py` 缩放打包；当前静态内流光和光泽不是动态光源。中文标签和提示随生成器维护，现有英文兼容翻译保留。


## Waystones 传送费用（0.114.2）

可选安装 NeoForge 版 Waystones 21.1.46 及其 Balm 依赖；本次固定依赖为 Balm 21.0.67。第三方 JAR 不进入 MyVillage 包。开发时通过 `-Pwaystones_test` 显式加入运行环境，普通构建只用它们编译可选适配类。`WaystonesBootstrap` 与 `WaystonesClientBootstrap` 不导入可选接口，检测到模组后才分别调用实际费用和绘制实现。绘制接口与匿名渲染器放在没有自动订阅注解的 `WaystonesClientCompatibility` 中；只在同一个自动订阅类的方法里添加存在判断，仍可能在客户端注册订阅者时提前解析缺失接口。

`WaystonesCompatibility` 使用官方费用类型注册接口，为 `waystones:item` 创建 `BagAwareItemRequirement`。原 `add_item_cost`、`scaled_add_item_cost`、乘法及上下限规则继续工作；灵石费用通过 `SpiritStoneSources` 和 `SpiritStoneStorage` 查询、规划和提交，普通物品费用仍使用 Waystones 原有逻辑。客户端复用原物品费用图标与数字，是否可支付会计入同步的袋余额。价格仍以 `config/waystones-common.toml` 为唯一来源；适配本身不把 XP 自动换成灵石，不覆盖已有配置。

可复制仓库的 [配置示例](../../config-examples/waystones-common.toml)，或只替换已有 `[teleports]` 下的 `warpRequirements`：

```toml
warpRequirements = [
    "[source_is_waystone] scaled_add_item_cost(myvillage:waystone_spirit_stone_cost, myvillage:low_grade_spirit_stone, 1)",
    "[source_is_inventory_button] add_cooldown(inventory_button, 300)"
]
```

示例仅从传送石出发收费，保留背包按钮冷却；其他传送方式未在示例中收取灵石。若要所有传送方式收费，可去掉第一条的条件。移除原 XP 费用项才能避免同时收费；修改后重启游戏或服务器。已有 0.114.1 的固定一枚规则不会自动替换，需采用上述新变量规则。

`WaystonesPricingRules` 计算 `base_cost × realm_multiplier × distance_coefficient`，所有数值以世界的 `serverconfig/myvillage-waystones-server.toml` 为准；初始文件由 `WaystonesPricingConfig` 创建，可参考[参数示例](../../config-examples/myvillage-waystones-server.toml)。默认凡人、炼气倍率为 1，筑基为 3，金丹为 10，只读取大境界 ID。路程系数按同维度水平直线距离除以 1000 向上取整，并限制在 1～10；传送石以源石与目标石中心为准，其他方式以玩家当前位置为起点，Y 坐标不参与。跨维度固定使用系数 10。单次下品费用分别为 1～10、3～30、10～100 枚。未配置境界和缺失的客户端档案使用显式配置的备用倍率，默认 10。

`WaystonesPricingVariable` 注册 `myvillage:waystone_spirit_stone_cost`。Waystones 的原生菜单在客户端计算报价，因此服务器配置由 NeoForge SERVER 配置同步，客户端境界从已有 `ClientCultivationState` 快照读取；实际服务器费用始终读取 `CultivationService` 的玩家档案，不接受客户端提供价格或境界。两侧共用整数定价规则，没有新增报价网络包。价格参数要求重启世界或服务器；境界改变后应重新打开菜单刷新报价。

Waystones 的公开变量为 float，规则先验证最坏单次价格不超过可连续精确表示整数的 16777216，随后才将有界整数作为变量交给原生物品规则；超范围配置直接报错，不静默截断。这个限制仅属于一次费用，灵石袋余额仍为无上限 `BigInteger`。

费用按具体物品及组件匹配：中品、上品及外部同级灵石不会自动替代下品，命名灵石也不用于普通未命名物品费用。余额为 `BigInteger`，单次费用沿用 Waystones 的整数；不会将大余额展开成实体堆叠。多袋、副手散装物品、副手袋及已注册的额外袋来源可共同支付，自动收纳开关不限制支出。原版收纳袋与物品容器继续按 Waystones 的嵌套组件处理器读取，遍历深度沿用该版本的 32 层限制；扣款先使用直接物品栏，再使用袋与嵌套内容。

扣款只在服务器执行，短款或快照失效不提交。每次实际扣款保留一次性回执，`WaystoneTeleportEvent.Complete` 以主传送实体是否成功为结算依据；宠物失败不退已经成功传送的玩家费用。同步接口先扣款后发现失效目标的情况同样退款。库存仍匹配扣款后快照时恢复原槽位；若其他事件已修改库存，只补回本次货币，优先入袋、再放普通槽，无法放入的实际灵石掉落。成功结算后不因重复通知退款，未扣款的失败也不退款。

## 饰品适配接口

`SpiritStoneSources.register(ResourceLocation, Provider)` 接入可选额外来源。提供器接受 `Player`，返回具有稳定槽键、实时 `ItemStack` 读取和可替换写入的 `Slot`。默认来源为物品栏，额外来源只接受单件袋；相同槽键/相同堆叠实例去重。饰品适配在两侧共同注册，由饰品模组同步自己的槽位；公共代码不依赖其 client-only 类型。

写入器必须是同服务器线程的本地槽位替换，不运行异步操作、外部事件或可能失败的外部提交；槽键必须对物理位置唯一，不能把同一位置复制成两个来源。`Access` 对完整快照做修改前校验；普通产物只进入 36 个背包槽，不放入装备或饰品槽。

当前提供接口，尚未安装或验收具体饰品模组。真实交易窗口、广泛多人、外观/手感/FPS 与玩家验收按专项测试记录分别报告。

初版检查见 [0.64.4 验证记录](../testing/2026-10-18-spirit-stone-bag.md)；大整数迁移、换算和当前交付身份见 [0.64.5 验证记录](../testing/2026-10-18-unbounded-spirit-stone-bag.md)。

中品／上品物品与主动兑换的定向检查及交付见 [0.73.1 验证记录](../testing/2026-10-22-spirit-stone-grades.md)。

Waystones 适配的依赖身份、57 项单元测试、无依赖两侧启动、实际菜单扣款与失败退款分项结果见 [0.114.1 验证记录](../testing/2026-11-05-waystones-spirit-stone-bag.md)。

大境界与路程计费的 0.114.2 源码、构建和用户要求停止后的实际检查范围见 [0.114.2 验证记录](../testing/2026-11-05-waystones-realm-distance-pricing.md)。
