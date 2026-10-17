# 战斗、武器与实体

<a id="combat-style-data"></a>
<a id="combat-data-validation"></a>
<a id="hostile-beast-runtime"></a>
<a id="humanoid-npc-runtime"></a>
<a id="custom-entity-runtime"></a>
<a id="rideable-flying-sword"></a>
<a id="lingxiao-spear-weapon"></a>

## 战斗权威与数据

- move/weapon/skill 文件由 `data/myvillage/combat/index.json` 索引，双方读同一资源。所有 timing、命中采样、位移、反应、反馈和镜头数值在数据中，不在 Java 复制。
- 两个攻击 C2S 为空；闪避仅一字节八向意图，剑气充能/释放/取消也为空。时间、命中、伤害、连段、取消、位移、冷却和无敌窗口均属服务器。
- 武器条目通过 style/family 选择行为；combat 包不得硬编码物品或妖兽名称。CombatWeaponItem 保留原版 re-equip 规则但去除耐久变化触发的重复装备动画。
- move 满足 active/buffer/chain/total 的时序约束；样本按 tick 非递减排序，多样本活动帧数量一致，绘制轨迹的远端达到武器 tip radius。仅 presentation 的 trail.samples 不参与服务器命中。
- 服务器 step 决定冲量和计划命中原点，客户端物理执行；击停、轨迹、声音、镜头和手臂是展示，不能改写结果。抗硬直通过通用 StaggerResistant 钩子，不按实体类分支。
- PAL 精确根依赖与 client-only 边界保留；客户端战斗计时仅 ClientCombatClock。已有青锋/枪/拳回归基线不可被无意 retune，故意调值同步对应数据和基线。

## 模型和动画

- 武器模型/纹理/图标/geometry 由各自生成器拥有；PAL 动画由 `gen_sword_pal_anims.py` 的独立 pose tables 生成，不手写输出。
- 几何格式 2 使用 butt/handle/collar/head/head_base/head_tip 和 axes.length；拒绝格式 1 兼容回退。抓握由契约和撤销 baked display 变换计算，不在 Java 写武器偏移。
- 两手武器有 off_hand_grip_center 与 rig.off_hand；空副手槽才展示辅助手。free off-hand 不握武器，paired 装备按通用配置镜像，不按物品 ID 判断。worn 表用 fist_key，保持拳套贴手。
- 动画长度/活动窗口/strike 覆盖与服务端数据一致；双手上杆关键帧校验握点、身体穿插与地面净距。改第一人称解算/rig/几何时更新 Java/Python parity golden。
- 客户端资源缺失或非法时可回退原版持握，不能污染其他武器。视觉姿态 probe 不发送动作包，不是实战输入证据。

## 妖兽和 NPC

- 妖兽属性、追击和每个招式数值在 beast JSON/index 中；contract 只引用字段，不复制调参数字。起手资格、锁定朝向、突进、去重命中、冷却均服务器决定。
- beastgen/npcgen 是模型、动画、贴图的唯一作者。招式长度与服务端 ticks 同步；正在招式中的一步位置更新与 clip delay 必须成对修改。公共 entity 代码不导入客户端类。
- NPC 基础 body 不自行决定敌友；宗门对话、化身等由明确后续 runtime 提供。不要把早期“无交互”条款覆盖到现有命簿化身功能上。
- NPC 服装用真实分层几何和逐像素体积/阴影/褶皱；shell cutout 的 alpha 为 0/255，避免共面。可选 model.scale 统一作用于模型和步态。
- 一个 look 对应定义、三份生成资源、contract looks、Java LOOKS；共享骨架/碰撞不变，只可增骨。实体身份、属性、渲染、语言、蛋、掉落和自然生成的真实支持范围须一致。
- 自然生成同时需要数据注入和 Java placement predicate；召唤、蛋生与自然出生的状态不能混为一谈。化身不存档，由命簿投射和撤回，不套用普通 NPC 所有生命周期假设。

## 乘骑飞剑

- 每玩家至多一把 owner-bound 暂态飞剑；使用在有界无碰撞位置创建并乘上，再用为召回。创建失败不留绑定。
- 网络仅传前后左右升降位；服务器从当前 vehicle 推导身份，检查 owner/passenger/维度/存活，决定位置速度朝向，限制斜向速度并保留碰撞。
- 乘骑期间与落地时处理玩家/载具坠落距离；死亡、离线、跨维度、远离和召回清理实体及绑定，不随世界存档。
- 水平 item renderer 与平滑插值只在客户端；不恢复原版客户端载具坐标权威，不加预测性移动包。

入口：[战斗集成](../ai-kb/32_pal_combat_integration.md)、[数据与采集](../ai-kb/34_combat_data_and_capture.md)、[双手枪](../ai-kb/35_lingxiao_spear.md)、[新武器](../ai-kb/36_new_combat_weapon.md)、[妖兽](../ai-kb/38_hostile_beasts.md)、[NPC](../ai-kb/39_humanoid_npcs.md)、[飞剑](../ai-kb/27_rideable_flying_sword.md)。检查依改动选择，旧全量清单不是每次必跑。

- 派生剑气由服务器创建，独立记录每波的有界目标去重和飞行位置；沿用扫掠碰撞与合法目标/PvP/墙体规则。客户端实例键区分主动动作与派生波 ID，派生消息不改主动充能姿态或连招 revision。
