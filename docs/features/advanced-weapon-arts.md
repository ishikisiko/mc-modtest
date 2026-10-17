# 高级武技：剑、枪、拳各两招

本功能随 0.62.0 交付；合并测试与实机证据见
[专项测试记录](../testing/2026-10-18-advanced-weapon-arts.md)。参数唯一来源是
`tools/advanced_arts/arts.json`；运行 `python tools/gen_advanced_arts.py` 生成资源。

## 玩家入口

达到**炼气四层**，使用对应的通用功法技能书，完成参悟后永久学会。
这一批试用技能书的参悟参数独立于旧功法目录：600 点、无额外突破关卡，
不改变旧二品功法的参悟成本。施放时服务器仍重新检查当前境界，管理员直接
`learn` 不会绕过境界门槛。六招无灵根属性偏好限制。

按原有 **R** 切入修炼战斗模式，手持对应武器，**Z / C** 分别施放该武器第一、第二招。
按键可以重绑定，游戏指南和右下 HUD 显示当前实际键位。**X 保持停止修炼**，
原右键《剑气诀》、左键基础连招和 Alt 闪避不变。
这些武技目前按“已学会 + 当前武器”自动装载到两个独立快捷位，不需要挤入旧右键剑气的选择规则。

| 武器 | Z | C |
|---|---|---|
| 青锋剑及 sword 家族 | 青莲剑歌：三段前进剑光，末斩青莲大弧光，清理正面敌群 | 回风剑阵：短正面格挡窗口，承受有效攻击后强化本次反击，环斩解围 |
| 凌霄枪及 spear 家族 | 苍龙出水：三次安全循地突进，双螺旋枪芒贯穿一线目标 | 横扫千军：双重环形枪罡，先牵制再向外震开敌群 |
| 玄铁拳套及 fist 家族 | 崩山劲：挑击接震地掌，三重震环、浮空与范围击退 | 金刚震：起手正面减伤，成功格挡强化震掌，清理贴身敌人 |

完整的灵力、冷却、范围、每段倍率与起手规则请看 authored JSON 或生成后的游戏指南，
避免文档形成第二套平衡数值。

### 测试用技能书

管理员以玩家身份执行以下命令获取**六本书**，不会直接学会、修改境界或重置档案：

```mcfunction
/function myvillage:advanced_arts_books
```

单本示例，沿用原有技能书系统：

```mcfunction
/myvillage cultivation manual @s myvillage:qinglian_jiange
/myvillage cultivation manual @s myvillage:huifeng_jianzhen
/myvillage cultivation manual @s myvillage:canglong_chushui
/myvillage cultivation manual @s myvillage:hengsao_qianjun
/myvillage cultivation manual @s myvillage:bengshan_jin
/myvillage cultivation manual @s myvillage:jingang_zhen
```

本批书暂未加入掉落、商店、宗门任务或世界模拟功法分配，避免未平衡技能侵入旧存档经济。
普通学习仍使用原有书本交互，不是拿着书打怪或依赖管理员施法。

## 动作与特效的实际实现

使用现有 PAL 与第一人称已建立的动作片段组成新的多段编排，并为每段独立配置
灵力攻击范围、反应与安全步进。**不是六套从零制作的骨骼动画**，也不重调旧基础动作。
第三人称和第一人称均沿用原有服务端确认动作包；客户端将新武技标为 external action，
防止技能结束后误预测基础连招中间一招。

新增青莲弧带、双螺旋枪芒、环形枪罡、震地环与正面护罡薄环，采用有深度测试的
半透明网格、亮色边缘及有限粒子，不仅是普通攻击改名字。启动、命中声音沿用
现有资源；守势有额外起手提示音。颜色、网格段数和存活时间由 authored 数据控制。

防御仅在第一段**有效帧之前**的正面窗口生效，源为攻击者或飞来的投射物。
绕后、超出窗口、穿盾/无敌穿透伤害不受该护罡减伤。
只有最终产生正伤害且对应同一 DamageSource 的格挡才强化反击，不靠环境伤害刷增幅。
攻势技能会承受正常伤害和击退，不附送无敌；守势在未格挡的受伤时中断。

## 权威、取消与兼容

客户端只提交 `slot=0/1` 的一字节意图。服务器检查战斗模式、状态、手持家族、
书本学习、当前境界、注册表定义、灵力、冷却和共享动作占用，再一次性扣费。

所有阶段由 CombatSessionManager 推进，沿用单调 revision、碰撞安全步进、
StatsRuntime/CombatDamageService 的唯一伤害路径，以及既有友军、PvP、无敌判断。
范围查询必须有视线，不穿墙、不破坏方块、不加载远方区块。每段对每个目标最多
结算一次；只有下一段才重置命中去重。环形技能击退朝目标外侧，既有抗硬直和
Boss 抗性仍适用。

换武器/同槽替换 ItemStack、切维度、死亡、登出、退出战斗模式、打开服务端容器、
失去学习或境界资格会终止后续阶段。沿用恢复段 Alt 闪避取消规则。
中断不退已扣灵力；冷却按玩家 UUID 与武技 ID 保存，不靠换槽、换武器、死亡或
重连清空。与现有临时战斗状态一致，服务器停止后清空冷却。

六招用独立 composite registry `combat/advanced_arts.json`，不伪装成旧单段投射物技能。
`tools/technique_catalogue/rules.json` 的 legacy `hand_written_techniques` 字段预留这六个 ID，
真实作者是新的生成器；旧功法生成器不删除六本书或覆盖其多语言名称。
功法档案、书本与死亡复制协议完全复用，没有新增玩家存档字段。

## 检查与未验证项

生成与本地针对性检查：

```bash
python3 tools/gen_advanced_arts.py --check
python3 tools/gen_technique_catalogue.py --check
python3 tools/check_advanced_arts.py
python3 -m unittest tools.tests.test_advanced_arts tools.tests.test_combat_data tools.tests.test_gen_technique_catalogue tools.tests.test_modal_runner
```

固定提交的云端入口：

```bash
python -m tools.modal_runner run --ref HEAD --preset advanced-arts-build
python -m tools.modal_runner run --ref HEAD --preset advanced-arts-runtime --timeout 2400
```

build 覆盖武技、战斗会话/网络/伤害、天命与客户端动作状态的相关 JUnit，并编译打包。
runtime 使用隔离存档与真实客户端映射输入，覆盖六本书从零研读、六招 Z/C、扣费与冷却、
未学/境界不足/灵力不足、墙体/友军、正面守势、换武器中断、双客户端与重启学习状态。
管理员命令只准备和读取夹具；守势的入站伤害为明确标注的边界探针。
具体完成范围与失败重跑记录以专项测试记录为准。

自动检查不替代玩家画面、手感、平衡、音效、FPS 验收。未单独观察的多目标逐段去重、
环形击退方向、枪突进崖边、背面/过期格挡、死亡/重连/跨维度的非零冷却、
多人视效同步仍为 `not_verified`。技能书世界自然获取尚未加入，测试通过不表示已实现。
