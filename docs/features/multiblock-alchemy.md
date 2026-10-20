# 青铜丹炉阵：首个多方块机器

首版为独立注册的 3×3×3 青铜丹炉阵，保留原单方块丹炉和已学丹方。
参考外部多方块的控制器／组件／专用接口分工；不引入外部模组依赖或复制其资源。

## 结构和操作

以正面炉心控制器为原点，横向 x=-1..1，向炉后 z=0..2，高度 y=-1..1。
中层中心 `(0,0,1)` 必须空气；控制器 `(0,0,0)`；进药口 `(-1,0,1)`；
出丹口 `(1,-1,1)`；灵石口 `(0,0,2)`；排气盖 `(0,1,1)`；其余 21 格青铜炉壁。
整体可水平旋转。所有组件手动逐块放置，控制器界面提供三层蓝图与缺件坐标。
界面是从炉前俯看的布局，正面在图下方；玩家面向炉体时进药口在右侧中层，
出丹口在左侧底层。局部坐标以炉心朝外的方向建立，蓝图显示时转换到玩家视角。

注册 ID：`alchemy_array_controller`、`alchemy_array_casing`、
`alchemy_array_input`、`alchemy_array_output`、`alchemy_array_fuel`、`alchemy_array_vent`。

沿用既有丹方、消耗和品质规则。学过丹方且已觉醒的玩家在附近菜单选择丹方，
明确启动连续炼制；服务器保存授权者、丹方与资质上限，每批单独冻结结果。
控制器独占库存和存档。进药口仅插入当前配方的两种药材，灵石口仅插入下品灵石标签，
出丹口仅提取成品。端口无独立物品副本；未成型、区块不齐或绑定不唯一时拒绝传输。
四个输出槽按实际物品和品质组件合并，容纳不同品质的连续产物。
出丹口放在底层，允许下方漏斗提取；中层侧口底下会被炉壁挡住，不能假设侧面漏斗会横向抽取。

活动批次保留所需材料与品质决定，输出满暂停；停止仅阻止下一批，不取消当前批次。
拆坏结构暂停当前批次，修复后继续；拆掉控制器只掉落实存物品，放弃未完成产物。
机器区块卸载或世界停止时不推进、不补算生产；授权玩家离线而机器仍加载时可以继续。
端口在每次操作重新核验服务器结构，旧缓存不能绕过拆坏。
原单方块炉仍为逐次手动生产。

## 表现与检查范围

外观沿用青铜／铜绿，炉心状态灯、顶部旋转气环和烟火由服务器运行状态驱动；
中文界面显示结构、材料、产物、进度、授权品质上限和三层蓝图。
首版不增加液体、电力、配方升级或外部管道模组硬依赖。

必要验证：四向蓝图、缺件／空气堵塞／区块边界、端口模拟与物品守恒、
停止／堵口／拆装／存档品质稳定、实际漏斗输入输出、映射输入菜单与运行画面、
同世界重启。重型工作仅在 Modal。玩家外观、手感、音效、FPS 与广泛多人仍需单独验收。

## 外部实现研究

核对的是官方仓库 `1.21` 分支固定快照，均面向 Minecraft 1.21.1，
不是将新版接口直接套到本仓库 NeoForge 21.1.233。

- **GregTech Modern**：提交 `36a6b38c580e54445e055e3ca05eecf675f2cb96`。
  [结构切片及谓词](https://github.com/GregTechCEu/GregTech-Modern/blob/36a6b38c580e54445e055e3ca05eecf675f2cb96/src/main/java/com/gregtechceu/gtceu/api/multiblock/pattern/MultiblockPatternBuilder.java#L65-L94)
  与[蓝图展示](https://github.com/GregTechCEu/GregTech-Modern/blob/36a6b38c580e54445e055e3ca05eecf675f2cb96/src/main/java/com/gregtechceu/gtceu/api/mui/MultiblockSchemaInfo.java#L65-L114)
  说明结构定义可以同时服务检测和说明。部件有加入／移除生命周期，世界方块状态变更会使相关结构复查。
  本实现采用同源蓝图，但不引入其大型机器框架或默认可共享部件规则。
- **Oritech**：提交 `225b182d0f8d2f9eb56a61220569dc32c4461f8e`。
  [核心代理](https://github.com/Rearth/Oritech/blob/225b182d0f8d2f9eb56a61220569dc32c4461f8e/common/src/main/java/rearth/oritech/block/entity/MachineCoreEntity.java#L72-L169)
  展示部件转发控制器库存的方式；
  [动画状态](https://github.com/Rearth/Oritech/blob/225b182d0f8d2f9eb56a61220569dc32c4461f8e/common/src/main/java/rearth/oritech/block/base/entity/MachineBlockEntity.java#L305-L335)
  区分组装、待机和工作。首版借鉴分工，保持全部散件可见，不复制其模型、动画或 GeckoLib 依赖。
- **NeoForge**：[1.21.1 能力失效约束](https://docs.neoforged.net/docs/1.21.1/inventories/capabilities/#block-capability-invalidation)
  要求能力变化时主动通知缓存。原版漏斗通过 ItemHandler 能力访问新端口；
  `simulate` 必须无副作用，已发出的 handler 也必须复查当前结构，不能仅依赖缓存通知。

这些是设计依据，并不替代本项目对拆炉、卸载、缓存、重复模拟和结算守恒的实际检查。

固定源、失败与实际观察范围见[交付验证记录](../testing/2026-10-21-alchemy-array.md)。
