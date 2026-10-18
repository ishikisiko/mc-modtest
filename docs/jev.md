# GPT-6 Astra × Minecraft：实验背景与可借鉴架构 Handoff

## 1. 背景：Vals AI 的 GPT-6 Astra Minecraft 实验

近期 Vals AI 使用 GPT-6 Astra 做了一次 Minecraft 长时程 Computer Use 实验。

最初公开的约 3 小时测试中，Astra 已经能够：

- 自主探索 Minecraft 世界
- 与多个敌对生物战斗
- 通过垫方块等方式处理僵尸
- 挖坑限制骷髅移动、躲避箭矢
- 制作并驾驶船只
- 找回自己的基地
- 最终建造 Nether Portal

Vals AI 对该实验的关键描述是：

> without any specialized harness: pure computer use

也就是说，这不是给模型提供 `mineBlock()`、`craftItem()`、`findDiamond()` 等 Minecraft 专用 Agent API，而是测试 Astra 的通用 Computer Use 能力。公开报道描述其使用普通人可以看到/操作的 Minecraft 界面以及键鼠输入。

随后实验继续运行到了约 **141 小时**。

Astra 最终取得过：

- 半自动 Blaze farm
- 6 个 Blaze Rod
- 3 个 Ender Pearl
- 已经接近进入 Endgame 阶段

但之后 Creeper 爆炸摧毁了存储大量重要资源的箱子和床，导致进度遭到严重破坏。之后模型一度长时间进行比较低价值的活动，例如种植/收获土豆，没有最终击败 Ender Dragon。

### 原实验最值得关注的地方

核心不是“Astra 一次模型调用操作 Minecraft 几小时”。

而是持续循环：

```text
Minecraft 当前画面
        ↓
GPT-6 Astra 观察
        ↓
判断当前状态
        ↓
维持长期目标 / 更新子目标
        ↓
决定下一批键盘鼠标操作
        ↓
执行操作
        ↓
得到新画面
        ↓
再次调用 Astra
        ↺
```

因此，一个模型同时承担：

```text
长期任务：
击败 Ender Dragon

↓

阶段规划：
木头
铁
钻石
Nether
Fortress
Blaze
Ender Pearl
Stronghold
Dragon

↓

局部计划：
我要先拿到铁镐

↓

即时视觉判断：
铁矿在哪里？
有没有怪？
血量如何？
我是不是迷路了？

↓

Computer Use 动作：
移动
转视角
点击
攻击
挖掘
打开 GUI
合成
```

重点是 **Observe → Reason → Act → Observe** 的长期闭环。

这对正在开发 Minecraft Agent 的项目非常值得参考，因为它说明足够强的 Computer Use 模型已经可以自己完成相当多层级的任务分解，而不一定需要为 Minecraft 写大量专用 Planner。

---

# 2. 不要与 MineLog 混淆

另一个相关项目叫 **MineLog**。

MineLog 是社区项目/开源 harness，不是 Vals AI 上面那次纯 Astra 实验。

其 README 对自己的架构总结为：

> GPT-6 Astra plans. Jev decides. One Minecraft body.

它使用：

```text
             GPT-6 Astra
             高层 Planner
                  │
             async plan
                  ↓
              主循环
                  ↑
            typed choice
                  │
                 Jev
          快速 Controller
                  ↓
            Mineflayer
             Minecraft
```

这里 GPT-6 Astra **并不负责每一步动作**。

---

# 3. MineLog 的实际控制链

MineLog 每一个 tick 大致执行：

### Step 1：代码读取 Minecraft 世界

转换成结构化 JSON，例如：

```json
{
  "health": 16,
  "hunger": 18,
  "inventory": ["stone_pickaxe", "bread"],
  "hostiles": ["zombie"],
  "light": 7,
  "nearbyBlocks": ["stone", "iron_ore"]
}
```

因此模型不必完全依赖屏幕像素理解游戏。

### Step 2：代码层 Reflex

部分紧急情况直接由普通程序处理：

- Lava
- Drowning
- Creeper 贴脸
- Death

这里甚至不调用模型。

即：

```text
危险
 ↓
hard-coded reflex
 ↓
立即处理
```

---

### Step 3：GPT-6 Astra 做慢速规划

Astra 是 Planner。

负责：

- 当前 stage
- 当前 goal
- waypoint
- 下一阶段目标

例如：

```text
Stage: Iron

Goal:
获得足够铁矿并制作 iron pickaxe

Priority:
1. 找 iron ore
2. 保证食物
3. 夜晚降低风险
```

Astra 在后台异步执行。

**它不会阻塞每一个游戏 tick。**

---

### Step 4：Jev 做快速动作决策

Jev / TypeSafe System One 是 Controller。

每次拿到：

```text
当前 Minecraft 状态
+
Astra 当前计划
```

然后从一个**有限动作集合**里面选动作。

概念上类似：

```text
walk
turn
mine
attack
eat
place
craft
wait
...
```

因此控制循环是：

```text
Tick 1
状态
↓
Jev
↓
walk

Tick 2
新状态
↓
Jev
↓
turn

Tick 3
新状态
↓
Jev
↓
mine

Tick 4
发现怪
↓
Jev
↓
attack
```

所以：

**Astra = 战略脑**

**Jev = 快速战术脑**

**Mineflayer = 身体**

**普通代码 = 神经反射 + 安全门**

---

# 4. MineLog 的代码层还会检查模型动作

Jev 做出选择以后并非立即执行。

代码还有 Gate：

```text
Jev action
   ↓
是否合法？
置信度是否足够？
是否安全？
   ↓
Yes → execute
No  → wait / flee
```

MineLog README 对整个架构的总结很重要：

> Code owns the tick. Models are guests.

因此它本质上是一个：

```text
Deterministic Runtime
      +
Slow LLM Planner
      +
Fast AI Controller
      +
Minecraft Protocol
```

而不是一个完全自由运行的 LLM Agent。

---

# 5. Vals Astra 与 MineLog 的本质区别

| 层级 | Vals AI Astra 实验 | MineLog |
|---|---|---|
| 长期规划 | Astra | Astra |
| 中期规划 | Astra | Astra |
| 即时决策 | Astra | Jev |
| 世界观察 | 主要视觉/Computer Use | Structured JSON |
| 动作输出 | 通用键鼠 Computer Use | 有限动作 catalog |
| Minecraft 控制 | 人类式界面 | Mineflayer |
| 紧急 reflex | Astra / Computer Use loop | 普通代码 |
| 专用 Minecraft harness | Vals 称没有 | 有 |
| 模型结构 | 基本单模型闭环 | Planner + Controller |

因此，如果项目目标是研究 **“一个强模型到底能多自主地玩游戏”**，Vals 方案更值得模仿。

如果目标是：

> 尽可能稳定、高速地让 Agent 通关 Minecraft

那么 MineLog 架构明显更工程化。

---

# 6. 对当前 Minecraft Agent 项目的启发

建议重点比较三种架构。

## A. Pure Computer Use

```text
Screenshot
   ↓
GPT-6 Astra
   ↓
Keyboard / Mouse
   ↓
Minecraft
```

优点：

- 最通用
- 几乎没有游戏专用先验
- 能真正测试 Computer Use intelligence
- 同样的 Agent 理论上可以迁移到别的软件/游戏

缺点：

- 慢
- Token / inference cost 高
- 像素定位容易漂移
- 长时间任务容易积累错误
- Combat / parkour 等高频控制困难

---

## B. MineLog 式 Planner + Controller

```text
Minecraft State
      ↓
Structured Snapshot
      ↓
┌─────────────┐
│ Astra       │
│ Slow Planner│
└──────┬──────┘
       ↓
    Plan
       ↓
┌─────────────┐
│ Jev         │
│ Controller  │
└──────┬──────┘
       ↓
Action Catalog
       ↓
Mineflayer
```

优点：

- 快
- 稳定
- 可以有很高 tick rate
- Planner 调用频率可以很低
- 很容易加入安全策略和恢复策略

缺点：

- 系统本身注入很多 Minecraft domain knowledge
- 难以判断能力到底来自模型还是 harness
- 泛化到其他游戏需要重新造接口

---

## C. 推荐研究的 Hybrid

值得尝试：

```text
                    ┌→ Fast Controller
Minecraft ─→ State ─┤
                    │
                    └→ GPT-6 Astra
                       Event-driven Planner
```

Astra 不需要固定每 N 秒调用。

只在发生：

```text
目标完成
连续失败
死亡
迷路
资源不足
进入新 biome
进入 Nether
发现 Fortress
重大 inventory 改变
计划超时
```

时重新规划。

普通操作交给高速 Controller。

这样可以把：

**LLM 的 reasoning intelligence**

和

**传统 Game Agent 的高频动作效率**

分开。

---

# 7. 一个尤其值得研究的问题

Vals 实验表明，Astra 本身似乎已经具备比较强的：

```text
Long-horizon planning
+
Visual grounding
+
Failure recovery
+
Memory / state tracking
+
Tool manipulation
```

因此当前项目可以研究：

> 随着模型能力增强，到底哪些原来属于 harness 的逻辑，可以逐渐删除并交回模型？

例如做 ablation：

```text
Level 0
全部 specialized skills

Level 1
去掉自动 crafting

Level 2
去掉 navigation

Level 3
去掉 resource planner

Level 4
只提供结构化 state + primitive actions

Level 5
只提供 screenshot + keyboard/mouse
```

最后测：

- 通关率
- Time to Dragon
- Death count
- Tokens
- API cost
- recovery rate
- stuck rate
- manual intervention count

这可能比单纯追求最快 Minecraft speedrun 更有研究价值。

---

# 8. 关键资料

### OpenAI：GPT-6 Astra 官方介绍

OpenAI 官方明确把 Astra 定位为新一代 Computer Use 模型，强调其：

- 屏幕理解
- 多步骤任务
- 软件操作
- 长程任务
- autonomous computer use

[GPT-6 Astra 官方介绍](https://openai.com/index/gpt-6-astra/?utm_source=chatgpt.com)

---

### Vals AI Minecraft 3 小时实验

Vals 原始公开内容中最关键的信息：

> All of this was without any specialized harness: pure computer use.

Bilibili 有完整约三小时录播及原始说明转载：

[GPT-6 Astra Minecraft 三小时完整录播](https://www.bilibili.com/video/BV1VpeG61EaZ/?utm_source=chatgpt.com)

---

### 141 小时实验报道

关于后续 141 小时运行、Blaze farm、Creeper 爆炸以及后续行为：

[PurePC：GPT-6 Astra Minecraft 141h experiment](https://www.purepc.pl/gpt-6-astra-minecraft-test-creeper-ziemniaki?utm_source=chatgpt.com)

另一个英文概述：

[GPT-6 Astra Plays Minecraft for 141 Hours](https://ypyl.github.io/news/2026/09/17/gpt-6-astra-plays-minecraft/?utm_source=chatgpt.com)

---

### MineLog GitHub

这是理解 Planner / Controller 架构最重要的代码库：

[beamnxw/minelog GitHub](https://github.com/beamnxw/minelog?utm_source=chatgpt.com)

README 明确给出了完整运行循环：

1. Observe world → JSON
2. Code reflex
3. Astra sets stage / goal / waypoint
4. Jev chooses bounded action
5. Code safety gate
6. Mineflayer executes
7. Runtime logs

代码目录也值得直接阅读：

```text
src/loop.mjs
src/planner/
src/controller/
src/observe/
src/skills/
src/protocol/
src/verify/
```

---

# 9. 给接手 Agent 的任务建议

请基于以上信息继续研究当前 Minecraft Agent 项目，重点不要把：

**Vals AI 的 pure computer-use Astra experiment**

和

**MineLog 的 Astra + Jev + Mineflayer architecture**

混为同一个实验。

下一步建议：

1. 阅读 MineLog repository，重点分析 `loop.mjs`、planner、controller、observe 和 skills。
2. 研究它实际暴露给 Jev 的 action catalog。
3. 研究 Astra Planner 的 prompt、JSON schema、调用条件与调用频率。
4. 分析哪些功能由普通代码完成，而不是模型。
5. 对照 Vals pure-computer-use 方法，判断当前项目更适合：
   - Pure Astra
   - Planner + Controller
   - Hybrid
6. 如果目标是实现自己的 Minecraft Agent，优先设计：
   - Observation schema
   - Action space
   - Planner/Controller interface
   - Event-driven replanning
   - Failure recovery
   - Persistent memory
   - Evaluation metrics

尤其要避免把“模型拥有 Minecraft 内部 JSON 状态”和“模型只能看到人类屏幕”当成相同实验条件。