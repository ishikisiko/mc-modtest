# 御剑飞行

基础 registry ID `myvillage:rideable_flying_sword`（物品、实体）保持兼容，新增物品「青霄飞剑」`myvillage:azure_cloud_flying_sword`。完整入口、诊断 schema、生命周期、配置、预览与实际证据边界见[御剑 demo](../features/flying-sword-demo.md)。本次源码实现尚未执行云端编译或游戏验收。

## 实现归属

| 范围 | 唯一入口 |
|---|---|
| 资格、速度、耗灵、输入与应急时限 | `data/myvillage/flying_sword/rules.json`；`entity/flight/FlyingSwordRules` 在每次起飞读取冻结 |
| 物品与境界/渡劫门槛 | `RideableFlyingSwordItem`；调用约定的 `TribulationManager.isActive(ServerPlayer)`，由渡劫模块实现 |
| 纯飞行算术、身份与回执 | `entity/flight/FlyingSwordFlight`、`FlyingSwordAuthority`、`FlyingSwordEscrow` |
| 服务器移动与实体同步 | `RideableFlyingSwordEntity`；六阶段、服务器位置、碰撞、档次、无保存载具 |
| 真实物品暂存、登录恢复、只读诊断 | `entity/flight/FlyingSwordInventory`、`FlyingSwordEvents` |
| 输入与召回意图 | `FlyingSwordInputFlags`、`FlyingSwordInputPayload`、`FlyingSwordRecallPayload`；协议兼容版本在 `ModPayloads` |
| 自然回灵暂停 | `SpiritualPowerRuntime` 的骑乘检测；不改丹药回灵 |
| 表现 | `FlyingSwordClientInput`、`RideableFlyingSwordRenderer`；不用 PAL 覆盖持剑动作 |
| 两档模型、色板、配方 | `tools/gen_flying_swords.py`；不得手改生成输出 |

## 保持的边界

客户端只传七位按键与空召回请求，不传坐标、速度、所有者或目标实体。服务器由当前 vehicle 推导身份，双层复查 owner/passenger/维度/存活；未新鲜输入中立。普通 `Entity.move` 保留墙面、剑底和玩家头部碰撞，不恢复原版客户端载具坐标权威。未知区块/世界边界前转缓降。

来源物品飞行时进入玩家持久 escrow；载具不保存，物品完整组件保留。落地、死亡、退出及跨维度通过同一回执一次归还，旧实体不能清理新绑定。高空召回先缓降，不能直接删除摔落；断线/重启后仅恢复无水平操控的应急下降。没有永久免摔属性，离剑后原版坠落规则恢复。

飞行与自然回灵互斥，丹药保持原恢复服务；低灵先提示并禁催剑，耗尽后只允许短时低速修正，随后零水平下降。创造模式方便模型检查，仍执行 owner/环境/渡劫边界。

## 检查层级

离线资源、Python 定向测试与 Java 语法树检查只能证明各自范围。Java JUnit 源码由父代理在统一 Colab 执行；渡劫接口合并前不独立编译。本模块不启动本地 Gradle、服务器或客户端。站姿、声音、FPS、多人权威和存档/断线行为均需真实输入实机证据，保持 `not_verified`。

相关技术约束：[战斗与实体](../contracts/combat-entities.md#rideable-flying-sword)、[资源](../contracts/assets.md)、[灵力](45_spiritual_power_sword_qi.md)。
