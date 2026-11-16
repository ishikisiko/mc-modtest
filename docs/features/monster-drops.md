# 怪物掉落动态配置

动态掉落从0.133.0加入。0.134.1起，服务器默认使用当前模组JAR内的统一掉落表。开发者修改 `src/main/resources/data/yuanxian/drops/monster_drops.json`、重新构建并替换JAR后，重新启动游戏或服务器即可使用新规则；已有世界和新建世界采用同一加载规则。

服务器首次启动会创建 `config/yuanxian/monster_drops_settings.json`，其中 `use_external_config` 默认为 `false`。在此状态下，游戏目录中已有的 `monster_drops.json` 保留原内容，不参与规则加载。单机使用该游戏目录的 config，独立服务器使用服务器自己的 config。

统一表集中控制普通怪物、主世界群落、委托秘境、宗门考核和资质试炼的物品掉落。默认继承原版及模组战利品表；0.135.0起，兽类群落通过[妖兽皮、妖兽骨和妖丹](beast-materials.md)获得回收收益，其他东荒群落保留原规则。委托秘境与两类考核继续禁止直接物品掉落。烈焰人兽火的额外掉落也由统一表持有。玩家背包掉落不属于此配置的作用范围。

## 服务器自定义与重载

服务器需要独立调整掉落时，显式开启外置覆盖：

1. 将 `config/yuanxian/monster_drops_settings.json` 改为 `{"use_external_config": true}`。
2. 管理员执行 `/yuanxian drops reload`。外置表缺失时，会从当前JAR内的规则创建 `config/yuanxian/monster_drops.json`；已有外置表保持原内容。
3. 编辑此外置表，再执行 `/yuanxian drops reload`。

成功回复显示当前来源、规则版本和 SHA-256；从下一次死亡起使用新规则，已有怪物也适用。需要恢复随模组更新时，将开关改回 `false` 并重载。开关和规则在同一次重载中生效，无需重启；自定义表保留在原位置。明确开启的外置覆盖在服务器重启和更换JAR后继续生效。

重载先完整读取并校验开关及选中的掉落配置，通过后一次性替换服务器当前快照。语法、重复字段、未知字段、物品/实体/地区ID、数量、概率、境界或格式错误都会拒绝本次重载，当前来源、快照及其版本保持原值。开关必须使用JSON布尔值，配置对象仅包含 `use_external_config`。外置覆盖关闭时不读取外置掉落表，其内容无效也不影响包内规则。启动时开关或已启用的外置表无效，则记录错误并使用本次包内规则，`status` 显示启动错误；修正后可再次重载。

启动及重载成功后自动导出 `config/yuanxian/monster_drops_effective.json` 和 `.csv`。这两份是查询产物；默认规则通过源码表发布，外置覆盖开启后才通过服务器的 `monster_drops.json` 编辑。单独执行 `/yuanxian drops export` 也可重新导出；原版数据包 `/reload` 后会刷新导出所引用的基础战利品表。导出失败会记录错误，已经生效的掉落规则继续运行。

## 四种模式

| mode | 结算行为 |
| --- | --- |
| `inherit` | 保留实体本来的全部物品掉落，包括原生装备、特殊死亡物品及其他已有结算结果 |
| `append` | 保留原有物品，再抽取本规则的 drops |
| `replace` | 清空原有物品，再抽取本规则的 drops |
| `disabled` | 清空全部物品，并跳过 additions |

`inherit` 和 `disabled` 的 drops 数组须为空。基础战利品表继续由原生成器和数据包负责，当前选中的统一表记录继承关系及运行时覆盖。经验规则单独保留，考核和资质试炼默认仍不发经验。

## 优先级

服务器根据真实实体身份确定来源，再依次处理：

1. `overrides` 从上向下取第一条匹配规则。
2. 没有匹配覆盖时，读取 `entities` 中该实体的配置。
3. 实体未单独配置时，读取 `defaults`。
4. 选中的模式允许掉落时，再执行所有匹配的 `additions`。

默认覆盖顺序为资质试炼、宗门考核、委托秘境、兽类群落、其他东荒群落。现有群落身份、代次和真实死亡回执仍由服务器验证；无效或过期成员身份没有奖励。

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

群落中的赤甲蜥优先使用 `beast_habitat_materials` 覆盖，再按兽类追加项结算材料；其他东荒群落使用 `donghuang_habitat`。修改对应覆盖与追加项可调整奖励，不会重新生成据点、成员名单或玩家建设。

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

`status` 的 `source` 为 `bundled` 时表示正在使用模组包内规则，为 `external` 时表示正在使用外置覆盖；`config` 是当前规则路径，`settings` 与 `external_config` 分别是开关和外置表路径。启动或重载失败时，`source` 仍表示实际生效的规则来源。

`inspect` 使用目标真实的来源、地区、已分配境界及捕火状态；它按玩家击杀来预览。`show` 可以指定实体、场景、地区、境界及零基阶段；后面的参数可以依次省略。返回值包含匹配规则、模式、配置掉落、追加项、继承的战利品表、规则来源及快照身份。

JSON导出保留完整配置、各实体各场景的规则预览和继承表。CSV也包含继承战利品表的完整JSON表达式，方便在一份表中查阅原生概率和数量。habitat 使用本世界实际群落地区，其他场景不预设地区和境界；具体条件用 show/inspect 查询。继承模式下，原生装备、搬运物和特殊死亡物品仍由实体状态结算，查询不提前执行随机抽取。

## 作者入口

包内默认文件为 `src/main/resources/data/yuanxian/drops/monster_drops.json`。它与外置表使用同一格式，是默认发版的唯一掉落规则入口；服务器明确启用外置覆盖后才读取其自定义表。掉落参数已从冻结的生态模板及炼器兽火配置中移出。

仅生态资源引用发生变化时，拥有者生成器可执行 `tools/province_capitals/game/compile_v11.py --refresh-habitat-only --out <资源根目录>`。该入口核对当前作者输出，只更新生态摘要、预设内的冻结规则及索引摘要，保留其他世界规划输入。

0.133.0的原始实现见[动态掉落交付记录](../testing/2026-11-15-dynamic-drops.md)；0.134.1的默认来源切换、重启检查与下载见[随模组更新交付记录](../testing/2026-11-16-drops-follow-mod.md)。

## 在线工作台

[打开掉落工作台](https://assets.ishiki333hhh.win/drop-workbench/)，选择生物或场景规则，再从右侧分类物品库添加掉落。解锁编辑后，数量、概率及匹配条件会自动保存到正式源码中的统一表；下一次编译使用更新后的文件。页面顶部显示写回结果，发生版本冲突时保留草稿并提示刷新。

生物列表和图鉴使用当前模型、贴图生成的离线三视图；原有掉落直接读取原生战利品表，展示抽取次数、数量、无抢夺概率、条件和抢夺加成。装备、特殊死亡回调及额外数据包的游戏内改动需要结合实际实体状态判断。修改统一表不要求改动原生战利品生成器。

网页支持原版／源仙筛选、材料／丹药／武器等用途分类、名称或 ID 搜索、历史恢复和 JSON 下载。生成、服务路径、私密密码文件及独立接口部署见[工作台说明](../../tools/drop_workbench/README.md)。

## 指定掉落物品质与稀有度

0.134.2 起，每项 drops 可指定 `quality` 或 `rarity`，省略时保留物品默认值。它们可以用于生物规则、场景覆盖及额外追加。

- `quality`：源仙丹药支持1／2／3／4，即下品、中品、上品、极品。已接入成器品质的武器、防具、工具还支持0，即凡品；正品质写入未炼灵的成器记录，不额外赠送专长。实际可用品类由服务端的物品类型判断。
- `rarity`：其他物品使用 Minecraft 的 `common`／`uncommon`／`rare`／`epic`，对应普通、少见、稀有、史诗。该组件控制物品名称颜色，基础属性保持原值。当前修仙衣甲等沿用原版护甲机制的物品使用此选项。
- 一项物品只设置其中一个字段。越界、错误类型、不适用的品质或两个字段同时出现都会拒绝整次保存／重载，当前快照保持原值。

例如 `{"item":"yuanxian:pill_huiling","min":1,"max":2,"chance":0.5,"quality":4}` 掉落极品回灵丹；`{"item":"minecraft:diamond_sword","min":1,"max":1,"chance":0.1,"rarity":"epic"}` 掉落史诗稀有度的原版钻石剑。同一物品可以用多项分别配置不同品质及概率；各项仍独立抽取。

网页点击物品时选择档位，加入后也可在卡片上修改。网页自动保存的是源码统一表；游戏需要使用0.134.2或之后的模组，按原有包内规则／外置覆盖方式加载。

0.134.2的构建、实际掉落组件与浏览器保存检查见[品质与稀有度交付记录](../testing/2026-11-16-drop-quality.md)。
