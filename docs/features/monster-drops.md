# 怪物掉落动态配置

0.133.0 起，服务端首次启动创建 `config/yuanxian/monster_drops.json`。单机游戏使用该游戏实例的 config 目录，独立服务器使用服务器自己的 config 目录。现有文件保持原内容，更新模组不会覆盖自定义规则。

该文件集中控制普通怪物、主世界群落、委托秘境、宗门考核和资质试炼的物品掉落。默认继承原版及模组战利品表；东荒群落继续掉落1–3枚下品灵石，委托秘境与两类考核继续禁止直接物品掉落。烈焰人兽火的3.5%额外掉落也迁入本文件。玩家背包掉落不属于此配置的作用范围。

## 修改与重载

1. 编辑 `config/yuanxian/monster_drops.json`。
2. 管理员执行 `/yuanxian drops reload`。
3. 成功回复显示本次规则版本和 SHA-256；从下一次死亡起使用新规则，已有怪物也适用。

重载先完整读取并校验全部配置，通过后一次性替换服务器当前快照。语法、重复字段、未知字段、物品/实体/地区ID、数量、概率、境界或格式错误都会拒绝本次重载，当前快照及其版本保持原值。启动时已有文件无效则记录错误并使用本次包内默认规则，`status` 显示启动错误；可修正文件后再次重载。

重载成功后自动导出 `config/yuanxian/monster_drops_effective.json` 和 `.csv`。这两份是查询产物，编辑入口始终是 `monster_drops.json`。单独执行 `/yuanxian drops export` 也可重新导出；原版数据包 `/reload` 后会刷新导出所引用的基础战利品表。导出失败会记录错误，已经生效的掉落规则继续运行。

## 四种模式

| mode | 结算行为 |
| --- | --- |
| `inherit` | 保留实体本来的全部物品掉落，包括原生装备、特殊死亡物品及其他已有结算结果 |
| `append` | 保留原有物品，再抽取本规则的 drops |
| `replace` | 清空原有物品，再抽取本规则的 drops |
| `disabled` | 清空全部物品，并跳过 additions |

`inherit` 和 `disabled` 的 drops 数组须为空。基础战利品表继续由原生成器和数据包负责，外置文件记录继承关系及运行时覆盖；已配置的替换和追加规则只由此外置文件生效。经验规则单独保留，考核和资质试炼默认仍不发经验。

## 优先级

服务器根据真实实体身份确定来源，再依次处理：

1. `overrides` 从上向下取第一条匹配规则。
2. 没有匹配覆盖时，读取 `entities` 中该实体的配置。
3. 实体未单独配置时，读取 `defaults`。
4. 选中的模式允许掉落时，再执行所有匹配的 `additions`。

默认覆盖顺序为资质试炼、宗门考核、委托秘境、东荒群落。现有群落身份、代次和真实死亡回执仍由服务器验证；无效或过期成员身份没有奖励。

## 普通赤甲蜥示例

在 `entities` 中将 `yuanxian:redplate_lizard` 改成以下内容，可使普通赤甲蜥每次掉2–4片赤甲片：

```json
{
  "mode": "replace",
  "drops": [
    {
      "item": "yuanxian:redplate_scale",
      "min": 2,
      "max": 4,
      "chance": 1,
      "looting": {
        "min_per_level": 0,
        "max_per_level": 1,
        "limit": 6
      }
    }
  ]
}
```

`chance` 为0–1的小数，各条 drops 独立抽取，可以同时产出。min/max 是包含两端的基础整数数量，范围0–64。抢夺增加量为“抢夺等级×每级区间内的随机小数”四舍五入，随后按 limit 限制总数；未填写 looting 时不增加数量。每个输出堆叠按物品自身堆叠上限拆分。

东荒群落中的赤甲蜥仍优先使用 `donghuang_habitat` 覆盖；修改该覆盖里的 drops 可调整群落奖励。修改掉落不会重新生成据点、成员名单或玩家建设。

## 条件与特殊物品

`overrides` 与 `additions` 的 when 支持可选的 `entities`、`scenes`、`regions`、`min_realm`、`max_realm`、`player_kill`、`fire_captured`。

- 数组条件中的任一值可以匹配，不同字段之间须同时满足；填写的数组不能为空。
- 场景为 `normal`、`habitat`、`quest_realm`、`sect_trial`、`aptitude_trial`、`battle_ruin`。多重身份按此模块的服务器来源判定处理，试炼与考核先于其他身份。
- 地区条件只使用主世界地区归属；其他维度没有主世界地区。
- 境界使用 `{"realm":"yuanxian:qi_refining","stage":8}`，stage从0开始，炼气九层为8。上下限均包含端点，未分配境界的目标不匹配境界限定。
- `player_kill` 检查最终伤害来源是否为 ServerPlayer，包括玩家发射的投射物。
- 兽火使用 `factory: "beast_fire"`，同时要求 item 为 `yuanxian:beast_fire_seed`；它生成带有兽火种类、成熟度和来源信息的正式物品。默认烈焰人规则还要求尚未被捕火。普通物品可省略 factory，或填写 `plain`。

## 查询

这些命令均要求管理员权限2：

```text
/yuanxian drops status
/yuanxian drops inspect @e[type=yuanxian:redplate_lizard,sort=nearest,limit=1]
/yuanxian drops show yuanxian:redplate_lizard normal donghuang yuanxian:qi_refining 8
/yuanxian drops show yuanxian:redplate_lizard habitat donghuang
/yuanxian drops export
```

`inspect` 使用目标真实的来源、地区、已分配境界及捕火状态；它按玩家击杀来预览。`show` 可以指定实体、场景、地区、境界及零基阶段；后面的参数可以依次省略。返回值包含匹配规则、模式、配置掉落、追加项、继承的战利品表及快照身份。

JSON导出保留完整配置、各实体各场景的规则预览和继承表。CSV也包含继承战利品表的完整JSON表达式，方便在一份表中查阅原生概率和数量。habitat 使用本世界实际群落地区，其他场景不预设地区和境界；具体条件用 show/inspect 查询。继承模式下，原生装备、搬运物和特殊死亡物品仍由实体状态结算，查询不提前执行随机抽取。

## 作者入口

包内默认文件为 `src/main/resources/data/yuanxian/drops/monster_drops.json`。它与外置文件使用同一格式；发布时修改默认文件，运行时编辑服务器外置文件。掉落参数已从冻结的生态模板及炼器兽火配置中移出。

仅生态资源引用发生变化时，拥有者生成器可执行 `tools/province_capitals/game/compile_v11.py --refresh-habitat-only --out <资源根目录>`。该入口核对当前作者输出，只更新生态摘要、预设内的冻结规则及索引摘要，保留其他世界规划输入。

本次构建、服务器检查和下载记录见 [0.133.0交付记录](../testing/2026-11-15-dynamic-drops.md)。
