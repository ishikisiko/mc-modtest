# 游戏内指南与炼器 JEI

游戏内《修行入门录》使用 GuideME，指南 ID 为 `myvillage:cultivation`。默认中文，说明应与当前服务器实际行为一致。共享约束见[资源契约](../contracts/assets.md)，检查范围见[工作流程](../WORKFLOW.md)。

## 指南来源与打包

根目录 `guidebook/` 是唯一 Markdown 来源。`processResources` 将整棵目录复制到 `assets/myvillage/guides/myvillage/cultivation/`，不得在 `src/main/resources` 再维护一份镜像。

- `guidebook/index.md` 按玩家需要组织入口；各章使用 `navigation.title/parent/position/icon` 加入目录。
- 已有 `_en_us/` 兼容翻译保留并修正过时事实；新章节只编写中文，不要求新增英文镜像。
- 开局、天命与技能、修炼、筑基、金丹雷劫、炼丹、种植、炼器、制符、灵石、舆图宗门、御剑、遗迹和敌人均有对应章节。玩家当前可玩上限仍是金丹后期。
- 探索页由 `tools/gen_exploration_guide.py` 生成；五行法修、野外战利品和亡灵页分别归其专用生成器。先改拥有者，再重新导出，不能手改这些生成页。
- 操作键使用 `KeyBind` 组件显示当前映射。不要把默认字母写成不可变的操作条件。GuideME 的默认 G 保持不被本模组占用，收功默认 X，不自动迁移玩家键位。

指南定义位于 `src/main/resources/assets/myvillage/guideme_guides/cultivation.json`。GuideME 通过 Maven 依赖，客户端和服务器均需要 `[21.1.17,22)`；模组不内嵌 GuideME JAR。21.1.17 的定义不使用 `custom_colors`。

## 开书与预览

`myvillage:cultivation_handbook` 在模组创造栏提供；玩家使用它时仅客户端调用 `GuidesCommon.openGuide`，没有新增开书网络协议。模型继承 `guideme:item/guide_base`。

```mcfunction
/give @s myvillage:cultivation_handbook
/guidemec myvillage:cultivation open
/guideme open @s myvillage:cultivation
/guideme give @s myvillage:cultivation
```

作者预览可用 `./gradlew runGuide`，监听根 `guidebook/`，启动时校验并打开本指南。游戏和 Gradle 运行位置按当前工作流程选择。

## 炼器 JEI

可选客户端插件位于 `client/forging/jei/`，分类 ID 为 `myvillage:forging`。安装 JEI 后可从成品配方、材料用途或锻器台／炼器鼎入口进入。

`ForgingJeiRecipe` 枚举 `WorkshopRules.crafts()`，通过对应 `ForgingRules.Recipe` 获取成品，并调用服务器同用的 `WorkshopRules.blankCosts()` 取得起坯主材。不能复制十种器物的成本表，也不能把炼灵灵石、专长辅材算入起坯成本。材料标签按客户端实际标签成员循环显示并保留数量。

页面左侧为主材，右侧为最终器物，中间使用现有炼器鼎物品图标。器谱使用 `CATALYST` 槽表示永久学习条件；无需器谱的工具不显示该条件。图标的提示说明锻器台工序和另外准备的工序辅料，成品提示说明品质按装配时资质决定。

它只提供查询，不增加合成类型、不提供自动转移按钮、不扣材料、不修改品质或服务器制作逻辑。JEI 是 `compileOnly` 可选依赖，所有引用留在客户端叶子包；普通服务器和未装 JEI 的客户端不需要加载插件。开发运行用现有 `-Pcourtyard_jei_test` 加载相同版本 JEI。

新增语言键归 `tools/gen_forging_assets.py`。只更新界面文字时使用 `--lang-only`，避免重导出无关器物几何：

```bash
python3 tools/gen_forging_assets.py --lang-only --check
python3 tools/validate_guideme_cultivation_guide.py
python3 -m unittest tools.tests.test_validate_guideme_cultivation_guide
```

## 检查含义

指南校验器保留六个基础中英文页面的存在要求，继续严格检查原有入门、修炼和当前上限，但不再把整本书错误限制为六页。它解析全部实际页面，检查导航、所有站内链接、物品／方块引用及嵌入配方；明确拒绝将已实现的金丹雷劫写成未实现。

指定 `--jar` 时，页面清单必须与整棵作者目录完全相同，且每一页与源文件逐字节一致，既不允许漏页也不允许携带过期额外页面。开发者检查不能证明玩家已认可导航、中文排版、手感或外观；未观察项保持 `not_verified`。

相关说明：[锻器工坊](54_forging.md)、[修炼闭环](30_cultivation_playable_loop.md)、[炼丹](49_alchemy.md)、[金丹雷劫](../features/tribulation.md)。
