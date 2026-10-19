# 雷角山君

`myvillage:thunder_tiger` 是灰白底色、黑色虎纹、额生雷角的守巢首领。实体继承 `BeastEntity`，复用原版 `ModelPart`、生成骨骼与动画管线，不增加 GeckoLib 依赖。约六格的视觉身长与三格肩高由模型表达；服务器使用独立身体／头部 `PartEntity` 受伤几何。技术边界见 [战斗与实体契约](../contracts/combat-entities.md#hostile-beast-runtime)，机器可读契约为 `genops/contracts/entities/thunder_tiger.yaml`。

## 游玩入口

主世界山地的新生成区块中会出现 `myvillage:thunder_tiger_lair` 洞府。可用群系、间距及地形均由 `data/myvillage/boss/thunder_tiger_lair.json` 决定。旧区块不会补放洞府；保留原有世界和玩家建筑。洞府锚点处于已加载区块、地面与生成空间安全、附近有存活的生存／冒险玩家且难度允许时，才绑定并生成一只首领。

玩家可用 `/exploration guide tiger` 阅读当前遭遇境界、位置线索、招式、断角、余震、撤退和零奖励说明。普通探索向导与城镇向导对话提供可点击的“山君传闻”。`ThunderTigerGuide` 从作者配置读取境界，仅发送提示，不查找世界结构、不生成实体、不修改进度。

首领具有可见血条、近战与雷击前摇、落点提示和收招窗口。玩家继续使用现有战斗操作；本功能没有新增战斗键位。四个招式分别为裂山爪、撼岳扑、踏地惊雷、山君雷啸。后两招具备有界目标范围和视线检查；雷啸提供对空中／远处目标的应对。离开巢穴范围或失去有效目标后，首领停止追击并返回。较低血量进入第二阶段，提高压力并增加落雷余震。

诱导扑击撞上洞府原有石柱后，首领会短暂低头露出雷角。此时玩家必须命中真实头部／角的受伤部位；仅打身体不会推进断角。断角后保留可见角根，主要雷击招式停用，爪击和扑击仍可使用。断角及阶段状态会保存；重载取消进行中的攻击与临时露角窗口。

首领使用作者配置的固定筑基初期遭遇境界，通过 `StatsRuntime.applyVanillaRealm` 接入共享境界属性和伤害管线，遭遇身份写入存档并在重载恢复。基础属性、境界与阶段阈值从对应数据读取。它不随搬迁、区块重载或地区变化重抽。

本次明确不提供奖励：掉落表为空、经验为零，不产生雷角物品、宝箱或击败解锁。

## 管理员与开发者入口

以下命令需要相应管理员权限。`locate` 只查询结构位置；不会创建首领或重置洞府。

```mcfunction
/locate structure myvillage:thunder_tiger_lair
/myvillage beast status @e[type=myvillage:thunder_tiger,sort=nearest,limit=1]
```

独立诊断实体与生成蛋入口：

```mcfunction
/summon myvillage:thunder_tiger ~ ~ ~
/give @s myvillage:thunder_tiger_spawn_egg
```

诊断出生没有洞府柱绑定，不能通过普通墙面获得露角窗口。生成蛋可在现有创造物品栏找到。强制动作命令会绕过一般起手距离和冷却，属于开发者准备工具，不能作为真实战斗输入证明；断角后依然拒绝强制主要雷击。

```mcfunction
/myvillage beast move @e[type=myvillage:thunder_tiger,sort=nearest,limit=1] myvillage:thunder_tiger_pounce
/myvillage beast debug on
/myvillage beast debug off
```

玩家只读入口 `/exploration guide tiger` 无需管理员权限。通用探索建议也可通过 `/exploration guide`、`/exploration guide tips` 与 `/exploration guide side` 阅读；这些命令不会生成洞府、复活首领或发放本首领奖励。

## 文件与作者归属

| 内容 | 真相源／实现 |
|---|---|
| 基础属性、四招时序、范围和冷却 | `data/myvillage/beast/thunder_tiger.json`，保留通用 beast schema 1 |
| 固定遭遇境界、两阶段、角、追击、粒子、表现时长与反馈参数 | `data/myvillage/beast/thunder_tiger_boss.json`；`ThunderTigerRules` 严格加载 |
| 服务器招式与真实部位、血条、同步和存档 | `entity/beast/ThunderTigerEntity.java`、`ThunderTigerPart.java` |
| 洞府布局、柱脚中心、触发和自然分布 | `data/myvillage/boss/thunder_tiger_lair.json` |
| 洞府生成资源 | `tools/gen_thunder_tiger_lair.py`；native structure、structure_set、template_pool、biome tag 和 NBT |
| 单一首领槽及回执 | `boss/lair/ThunderTigerLairBlockEntity.java`、`LairProgress.java`、既有 `BattleReceipts` |
| 灰白黑纹模型、UV、贴图和动画 | `tools/beastgen/defs/thunder_tiger.py`、`art/entities/thunder_tiger/`；`tools.beastgen` 生成运行时资源 |
| 蛋模型、空掉落表、名称和声音字幕别名 | `tools/gen_thunder_tiger_support.py` |
| 声音注册 | `entity/beast/ThunderTigerSounds.java`；录音复用原版，Boss 专用中英文字幕 |
| 采集与证据判定工具 | `tools/thunder_tiger_evidence.py`、对应工具测试；实际运行证据另行记录 |

模型与动画位于 `assets/myvillage/beast/thunder_tiger_{model,animations}.json`。基础图和局部发光图位于 `assets/myvillage/textures/entity/thunder_tiger/`，最终尺寸由模型文件决定。`horn_base` 保留断茬，断角隐藏 `horn_tip`。所有输出由各自生成器拥有，不手改生成文件。

声音事件为 `myvillage:thunder_tiger.<cue>`，包括 ambient、hurt、death、attack、charge、thunder、roar、horn_break 和 step。`sounds.json` 将事件指向原版录音，字幕使用 `subtitles.myvillage.thunder_tiger.<cue>`。这些是新事件与字幕别名，不是新录制音频。

## 持久状态与安全边界

洞府锚点为隐藏、不可破坏、无物品及无奖励的方块实体。生成模板含 READY 状态；手工放置或无法识别的状态保持 DORMANT。READY 仅在安全条件成立时绑定一个 UUID，进入 ACTIVE。已绑定首领卸载、离远或暂时无法查询时仍占用此槽，不能据此生成替代者。死亡回执进入 CLEARED，保存后不再刷新；只有明确的生成失败或和平模式移除回执可回到 READY。

`bindLair(home, pillars)` 只在服务器首次绑定，传入有限的原始柱脚中心列表。扑击仅核对该列表及仍存在的允许石柱方块，不扫描远处地形。生成前检查脚下地板、空间、液体与已加载区块；不修地、不强制加载未知地形。

身体与头部共享父实体生命值，子部位不独立保存；服务器按真实接触部位提交伤害，沿用原版伤害减免一次。命中去重、目标合法性、视线、阶段、战斗结果和持久状态均由服务器决定；客户端只显示姿态、角、粒子与血条。公共实体代码不导入客户端类。

## 检查与证据状态

本条目的静态检查包括实体资源／契约一致性、生成器 `--check` 和定向 Python 测试。`tools/validate_custom_entities.py` 对 `structure_anchor` 显式路线核对 native 结构链、锚点及规则实体，同时继续禁止环境随机刷怪的 placement、biome modifier 和 tag；妖狼原有自然生成检查保持生效。通用 skill schema 需要数值，仓库契约要求引用唯一数据源，因此只在内存中从实际属性和模型解析具体值后做 schema 校验，不将调参值复制进 authored contract。

实际 Modal 构建、专用服务器、映射输入、断角战斗、双客户端观察与重启证据：**pending**，由本次最终执行报告补充。工具 fixture 和强制动作不等于自然游玩通过。玩家审美、手感、音效质量、FPS、自然密度和广泛多人验收维持 `not_verified`；生成图与预览维持 `human_review_pending`，直到实际人工确认。
