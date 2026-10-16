# 修炼、功法、天命与炼丹

<a id="cultivation-player-profile"></a>
<a id="cultivation-definition-registries"></a>
<a id="cultivation-debug-commands"></a>
<a id="cultivation-initiation-ritual"></a>
<a id="cultivation-persistence-lifecycle"></a>
<a id="cultivation-state-synchronization"></a>
<a id="cultivation-lifespan-calendar"></a>
<a id="cultivation-meditation"></a>
<a id="cultivation-gain"></a>
<a id="cultivation-advancement"></a>
<a id="innate-fates"></a>
<a id="spiritual-power-pool"></a>
<a id="sword-qi-technique"></a>
<a id="crafting-aptitudes"></a>
<a id="alchemy-foundation"></a>
<a id="alchemy-quality"></a>

## 档案、权威和生命周期

- `CultivationService` 是完整不可变 profile 的唯一替换入口；失败不半写、不发变更快照。ResourceLocation 持久化不依赖当前定义存在，不存 enum ordinal、registry 数字 ID 或解析对象。
- 当前字段与版本以 `CultivationProfile` CODEC 为准（本次基线为 v4）；保留支持的旧版本显式迁移、未知合法 ID 和已有字段。旧规范中的 v2/v3 默认、固定 stability≤100 等描述不再作为现行约束；稳定度上限取当前 stage。
- 根属性为不可变整数 basis-point map，合计精确 10000；学习进度、灵力、修为等非负值在构造/codec/替换处同样验证，运算不溢出为负。
- Attachment 的 copyOnDeath 是唯一档案拷贝机制；不另写培养档案 Clone 复制。登录/重生/跨维度只在权威状态就绪后同步；退出清客户端缓存。暂态修炼/施法计数不塞进持久化 profile。
- 时间以原始 ticks 存储：共享日历在有玩家在线时前进，个人寿命只计在线存活的生存/冒险状态。时间尺度变化重解释原始值而非重写存档；寿命批量和生命周期 flush 仍经服务提交，失败保留待写值。

## 定义、入门与交互

- realm/element/technique 使用同步 datapack registry；当前注册访问来自 RegistryAccess，不能用 id 前缀/Java 枚举猜定义。类别效果是声明，不是脚本执行器。
- 灵根按世界种子、玩家 UUID、固定 salt/version 和排序后的可选元素权重确定性生成；不依赖当前位置、系统时间、JDK 随机实现或 registry 迭代顺序，不泄露世界种子。
- 选元素无放回，整数分配精确守恒；既有灵根不因配置或生成器更新自动重抽。觉醒与基础功法传承是两个独立服务、两块石碑，各一次原子替换。
- 普通入门遵守当前定义要求；重复操作无副作用。管理员直接 setroot/learn 等诊断入口与普通仪式分开。所有 cultivation/xiulian 的英文/拼音别名结构、参数、权限和处理器等价。
- 共用注册器保持网络方向与物理 side 安全。客户端只发送有界动作、必要 ID 或服务端会话 token，绝不携带修为、成本、时长、目标境界或结果。换线格式必须调整兼容版本并检查两端 codec。

## 修炼、突破和功法

- 每玩家一个服务器暂态会话，修炼、准备、研读、突破互斥；按当前规则验证地面、状态、受伤间隔等资格。移动/攻击/使用/生命周期中断幂等，视角旋转可允许；管理性关闭不冒充玩家失误罚稳定度。
- 普通修为按灵性结算；灵石模式按 stage 定价完整支付。缺款整批不扣并降为普通修炼；提交失败归还成本，提交成功后网络发送失败不能退款或重放收益。
- 达到当前修为 cap 的这一批不同时涨稳定度；后续满修为结算才固本，固本不扣灵石。法诀熟练度是独立时钟，灵力与旧 meditation reserve 也互不替代。
- 突破从当前数据读取唯一下一阶段，结束再验证，一次意图只走一级；成功修为归零、实际稳定度向下取整减半，不再另扣兼容 cost 字段。保留其他档案字段，扩容不补满灵力、寿命上限增加不重置已耗寿命。
- 当前玩家链截止金丹后期；缺少 cap/transition 的终点不推算元婴。原有 stage IDs 和四个早期过渡由回归基线保护，后续数值只写 realm JSON。
- 功法、流派和传承目录由 `tools/technique_catalogue/` authored 数据生成；除明确手写基础条目外不改输出。核心切换只走 CultivationService；grade/元素倍率与损失来自 world_sim rules。
- 秘籍以 category/grade 物品加 technique/comprehension 组件区分，不每门功法注册一个物品。研读仍经会话与服务提交，提交后才更新秘籍；有效手册客户端 use 返回 consume，避免挥手中断。
- 灵力花费原子、恢复服务器计时且有上限，无离线收益。剑气通过已学功法和武器族验证，充能/释放/取消只发空意图；服务器负责方向、耗费、碰撞、去重命中和伤害，视觉不能越墙替代命中。

## H 面板与天命

- H 是 hub + PanelPage，系统页通过 PanelContext 读取缓存/同步定义，使用共享主题；每页交互走 page hooks，不把专用逻辑塞进 hub。四个修炼动作只在 MeditationPage 绑定一次。
- 经脉图只显示服务器已同步的会话/进度，不捏造穴位数量；批次绘制自持 buffer。只修改某页/语言时检查受影响页面，不能把全尺寸截图变成所有改动的默认义务。
- 天命为独立不可变持久化状态，服务替换并 copyOnDeath；六选三、一个锁、点数预算与互斥由服务器验证，无额外高品数量上限。已确认不可普通重置。
- 普通天命变更需要真实测灵碑建立的存活、同维度、近距离 session 与新鲜 token/revision；旧 close token 不关掉新会话。H 只读结果；重开觉醒页不重抽已有候选。管理员显式目标诊断不等于普通流程证据。
- 只有实现的天命进入普通池，未实现设计保留在既有 catalogue；救急/击杀恢复由服务与持久化冷却约束，死亡不刷新冷却。

## 技艺、丹炉与丹药

- 炼丹/炼器资质独立、一次初始化，使用独立确定性来源，不消耗灵根 RNG；旧已觉醒存档仅补缺失值。炼器生产见下方炼器雏形约束。
- 资质限制同一配方成品品质，不是黄玄地天配方品阶；每炉手动开始，服务器检查有效近距菜单、觉醒与材料、空输出，冻结炼制者 UUID/资质/上限/结果。
- 丹方学入玩家持久知识，死亡保留；丹炉同步各自已学列表，服务器复查知识后选择并从本人背包补齐一炉。换方退料与补料先完整规划再一次提交，空间不足不改库存。保留五槽存档索引与旧丹方取回槽，药材次序可交换；活动输入锁定，产物与扣料一次完成；堵输出暂停不耗材不重抽，损坏旧批次保留物品待重开，完成不自动连炉。区块卸载不暗中计时生产。
- 下中上极品组件有界持久化/同步；旧无组件及新下品保持旧堆叠身份和强度。高品质只改变强度，不改共享间隔、持续时间、资格或中断规则；持续药效保存原品质。
- 回灵不超容量、养元受伤中断、凝神仅影响满修为固本；拒绝使用不耗物品，不叠加活动效果。所有修炼数值变更仍走 CultivationService。

技术细节：[核心](../ai-kb/28_cultivation_core.md)、[入门](../ai-kb/29_cultivation_initiation_ritual.md)、[循环](../ai-kb/30_cultivation_playable_loop.md)、[面板](../ai-kb/37_cultivation_panel.md)、[功法](../ai-kb/41_technique_system.md)、[灵力](../ai-kb/45_spiritual_power_sword_qi.md)、[天命](../ai-kb/48_innate_fates.md)、[炼丹](../ai-kb/49_alchemy.md)、[资质](../ai-kb/50_crafting_aptitudes.md)。

天命快速开发占位：17 个紫/金/红候选可选、可保存，双语明确标注暂无效果，effects 为空；不伪装成已实现能力。六品阶权重相等，保留普通候选保底和预算。

灵植种植：灵土只支持灵植，不含干涸/踩回泥土路径；六味现有药材兼作播种物与收获物，不新增种子。三阶段 age=0..2，服务端自然生长/骨粉生长，模型和掉落由 gen_spirit_crops.py 生成。

<a id="forging-foundation"></a>

## 炼器雏形

- 炼器数值、配方、消耗、可用专长与品质权重唯一来源为 `data/myvillage/forging/rules.json`；资质品质上限仍由 crafting rules 决定。器谱使用后独立持久保存，copyOnDeath，服务器是知识唯一写入者。
- 炼器台采用玩家私有临时菜单，无共享方块库存；关闭、死亡或断线由原版容器返还/掉落暂存物品。交互复查菜单本人、存活、旁观者状态、距离与方块，客户端只发送有界按钮编号。
- 每次手动即刻炼制一件；先在副本规划完整扣料并构造结果，再在单次服务器事务扣料与设置输出。缺料、未知器谱、未初始化资质、输出占用或不支持的专长拒绝且不扣料；不自动连制。
- 原有武器 registry ID 与动作保持兼容；有界持久 `forge_mark` 记录稳定专长 ID 和品质。锋锐使用唯一主手属性修饰，坚韧写入原版最大耐久，导灵在服务器剑气扣费前应用一次，正成本向下取整且至少为一；不重复处理伤害减免。
- 维修只接受配置支持的受损武器，复制整个物品后只减损伤，不重抽品质、不清除附魔或命名。资源由 `tools/gen_forging_assets.py` 生成；武器模型继续归属原有专用生成器。

实现及限制：[炼器](../ai-kb/54_forging.md)。

- 炼器鼎保留 `forging_table` registry ID；四向/亮炉状态是表现，完成事务后才发光与粒子，拒绝请求不点火。熄灭由原版计划方块 tick 处理，形体碰撞与模型同源，配色和反馈参数在 `tools/forging/ding.json`；不因外观加入生产队列或共享库存。

- 专长辅材由炼器 rules 引用真实注册物品，获取配方由 `tools/forging/materials.json` 生成。玄铁锭/赤铜锭/淬锋砂为普通材料，炼制扣除仍由原有服务器事务完成；不在客户端按材质名猜专长，也不改变旧武器的已存属性。
