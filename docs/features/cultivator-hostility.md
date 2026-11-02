# 修士与怪物的目标选择（0.109.1）

18 款行尸与骸弓会在感知范围内主动选择可见的修士；原版敌对生物在原有追逐村民的规则中，也把修士作为候选。友好修士仍在受袭后进入现有自卫、追击或撤退行为。

## 新亡灵

`UndeadEntity` 保留 `BeastEntity` 的受袭反击与玩家目标，在其后增加修士目标。优先级来自 `tools/undead_roster/catalogue.json` 的 `runtime.cultivator_target_priority`，由原生成器导出；搜索距离使用实体的既有 FOLLOW_RANGE，搜索节奏沿用原版最近目标 Goal，有视线要求。现有玩家优先级和受袭反击优先级不变。

修士的男性、女性与各服装外观都属于同一个 `CultivatorEntity`。目标筛选继续经过 `CombatActors.mayHarm`，不攻击同队、盟友或其他被共享权限拒绝的对象。普通妖兽的主动目标范围没有随这次修改扩大。

## 原版村民目标

`VillagerLikeTargetMixin` 仅扩展 `minecraft` 命名空间敌对实体已有的 `NearestAttackableTargetGoal<AbstractVillager>`（或其村民子类目标）：候选集合同时包含原目标类和修士，再交给原 Goal 的 `TargetingConditions` 选最近目标。

因此原 Goal 的调度间隔、优先级、范围、视线选项、附加谓词、追踪记忆与失效处理都保留。僵尸、尸壳、溺尸、僵尸村民、卫道士、掠夺者、唤魔者、幻术师和劫掠兽通过其现有村民目标获得该行为；劫掠兽原有的年龄过滤仍生效。其他怪物按它们各自的原版规则行动，例如骷髅原本没有主动追逐村民的目标，因此这次不会额外获得修士目标。

实际村民仍在同一个查询中，修士和村民按原规则竞争目标。这里仅适配选敌，修士继续使用原实体、命簿身份和生活系统；死亡、掉落、村民感染与交易等仍按各自类型处理。

## 执行与验证

两条选敌路线都不直接结算伤害。亡灵复用已有招式与服务器伤害管线；修士受击后由 `NpcCombatRuntime.attacked` 设置施害者，再通过 `NpcCombatGoal` 提交公共战斗意图。没有新增客户端消息或持久化敌对状态。

专用探针通过 `-PcultivatorHostilityEvidence` 加入 `src/hostilityEvidence/java`，使用独立 `run-cultivator-hostility` 目录；带该开关禁止打包。场景初始攻击目标均为空，夹具不调用 `setTarget` 或注入伤害，观察自然取得目标、实际攻击、修士自卫、原村民目标、最近目标竞争、同队排除、亡灵视线与距离限制，以及旧目标移除后重新选敌。此证据不代表玩家手感、多人或 FPS 验收。

固定来源、36 个原生场景与交付 JAR 见[验证记录](../testing/2026-11-02-cultivator-hostility.md)。
