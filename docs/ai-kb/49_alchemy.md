# 0.99.7 竹轴药笺

14 种丹方采用竹轴、素绢环、短绢结及药草小印记，中央使用 `tools/pill_art/render.py` 的真实丹药与托座；不另画丹体或改变原丹药图标。`tools/pill_art/formulas.py` 根据 `alchemy/rules.json` 的实际 `output` 选择图案，按 `fixed_quality` 决定边饰；无固定品质则使用素绢，不按物品 ID 后缀猜品质。

共用纸卷和组合器在 `tools/scroll_art.py`／`tools/scroll_art.json`。`alchemy` 样式使用竹轴和短绢结，`forging` 样式保留金属箍；`quality_levels` 只做配方品质到边饰的映射。中央丹药用最近邻缩放，保持像素纹路清楚。正式纹理为 64×64，基于 32 像素网格；现有 10 本器谱输出字节不变。共享依赖见 `tools/scroll_art_requirements.txt`，器谱原 requirements 入口仍可用。

最终资源归属不变：基础／根基八张丹方经 `gen_alchemy_assets.py`，战备六张丹方经 `gen_wilderness_roster.py`。检查包括两个生成器 `--check`、`test_formula_scrolls`、丹药／炼丹及野外资源测试，另外检查器谱生成器及其图标测试以防共享组件回归。人工预览为 `python tools/preview_formula_scrolls.py --out reports/formula-scrolls/preview`，含与器谱的并排参照和 16／32 像素切换。交付与验证范围见 [竹轴丹方记录](../testing/2026-10-28-formula-scrolls.md)。

# 炼丹（0.75.0）

## 丹药图标共享部件（0.84.3）

现有 11 种丹药的外观入口为 `tools/pill_art/catalogue.json`。每项仅定义中文名、
`support` 与 `body`；叶托 `leaf`、浅碟 `dish`、丹匣 `casket` 表示种类的珍稀层级，
不映射下／中／上／极品。同一种丹药仍共用贴图，炼制品质沿用名称与槽框提示。

`tools/pill_art/render.py` 将托座后层、丹体、托座前沿依次组合。托座不读取物品 ID、
丹体配色或药效；丹体使用统一 32×32 透明画布、中心与半径。新增外观只需定义主体
配色／有机纹路并选择已有 `support`；新纹路只在 `MOTIFS` 添加路径，不复制托座。
`body_layer()` 与 `compose(body, support)` 分离，也允许调用方提供遵守画布约定的
独立丹体像素。新增游戏物品的注册／药效／资源归属仍按原模块闭合。

原生成器继续拥有最终文件：基础及根基五丹经 `tools/gen_alchemy_assets.py`，
战备六丹经 `tools/gen_wilderness_roster.py`。两者均调用共享组合器；运行时继续使用
原 `pill_*.png` 与 generated 物品模型，无额外贴图图层或 Java 状态。

必要检查：两个生成器的 `--check`、`tools.tests.test_pill_art` 及受影响生成器测试。
`python3 tools/preview_pill_art.py` 导出最终贴图与拆解层，先验证它们与打包源一致；
预览含 16／32 像素缩略图。资源预览不能代替游戏内手持、掉落和玩家观感验收。

新增筑基丹、补基丹、凝元果及固定品级丹方见[当前玩法与兼容](../features/foundation-pills.md)。单炉新增槽 5、炉阵新增槽 7；以下旧五槽与三丹描述仅针对历史基础配方。


新增独立多方块丹炉阵见[多方块结构、授权与当前外观](../features/multiblock-alchemy.md)。
它提供逐块装配、同源三层蓝图、四向检测、专口自动化与明确授权的连续炼制；
下文五槽、未注册物品能力与每次手动开始仍描述原 `alchemy_furnace`，不适用于新炉阵。

炉阵在 `alchemy/array/` 独立注册；`array.json` 同源驱动检测与蓝图。
控制器保留菜单所需 `Container` 方法，但通过 `WorldlyContainer` 向所有方向暴露空槽位，
同时拒绝侧面插取，防止原版漏斗／投掷器在能力缺失时回退访问控制器。
只有三个专口注册 NeoForge ItemHandler；handler 创建时固定控制器身份，每次调用复核结构，
成型、卸载和替换主动失效缓存。方块配置的局部左右与玩家面对炉体的左右相反；
蓝图绘制和 hover 都转换为从炉前俯视，不能直接将局部 x 当屏幕 x。

0.64.3 起，丹炉燃料槽、开始/持续校验、快捷移动和自动补料统一接受 `c:spirit_stones/low_grade`。燃料仍占单槽，不合并不同物品或组件；配方生成器同步输出分级 tag 原料。 标签结构与兼容边界见 [资源合同](../contracts/assets.md#灵石公共标签)。


The thirteen existing items remain a modeled furnace, six infused herbs, three
learnable physical formulas and three pills. Survival acquisition uses vanilla
plants plus spirit stones. [README](../../README.md#alchemy-炼丹-0520) documents
controls, quality probabilities, effects and administrator fixtures.

## Sources of truth

- `data/myvillage/alchemy/rules.json` format2 owns recipes, costs, durations,
  output counts, shared interval, base effects, quality weights by ceiling and
  strength multipliers. `AlchemyRules` rejects malformed/out-of-bounds data;
  rebuild/restart for changes (not datapack hot reload).
- `LearnedFormulas` is immutable, persisted with an additive `learned_formulas`
  attachment and `copyOnDeath`. `AlchemyKnowledge` alone writes it on the server.
  Using a FormulaItem learns its recipe; survival consumes one copy on first
  learning only. Knowledge does not depend on cultivation-profile reset/root state.
- The furnace menu privately synchronizes each viewer's knowledge with vanilla
  DataSlots; the learned list sends only a bounded menu button index. Selection
  and start recheck the current nearby menu, living non-spectator player, recipe
  knowledge and idle state. No client-authored inventory or learned IDs are accepted.
- `AlchemyAutofill` plans transfers on copies. Incompatible existing ingredients
  return to the actor's main inventory/hotbar; full inventory rejects the whole
  switch. It fills one batch's deficit, preserves stack components, and leaves
  missing ingredients empty. One server-thread commit updates both inventories.
- `AlchemyFurnaceBlockEntity` retains five saved Items indices for compatibility:
  index0 is return-only legacy formula storage; 1/2 herbs, 3 fuel, 4 output.
  `SelectedRecipe` is additive; old saves infer it from their legacy formula.
  Valid existing Batch decisions resume without relearning or rerolling; the
  next start requires the initiating player's knowledge.
- Batch still freezes brewer UUID, recipe, aptitude, cap and outcome. Active
  inputs/selection are locked. Ingredients/fuel commit once with output; output
  blockage pauses without cost or reroll. Completion requires another manual start.
  Unload pauses; breaking preserves held items but abandons unfinished output.
  No hopper capability is advertised. This alchemy wire layout was introduced in protocol22; current builds require protocol25.
- `PillQuality` uses bounded persistent/networked `myvillage:pill_quality`, integers
  1..4 (lower/middle/upper/perfect), on the three existing pill IDs. Missing is
  lower; ordinary new lower pills omit it and stack with legacy pills. Explicit
  component1 has baseline strength but vanilla component equality still controls
  stacking. Invalid data rejects or safely falls back to lower, never higher.
- `MedicineRuntime` / `MedicineState` / `MedicineEffects` own consumption,
  eligibility and online counters in `NeoForgeData.myvillage_medicine`, including
  sustained-effect `quality`. Recovery and each healing tick multiply base
  strength by quality. Ningshen scales only the bonus above1:
  `1 + (baseStabilityMultiplier - 1) * qualityMultiplier`. Duration, interval,
  eligibility and interruption rules remain; missing saved quality is lower.
  Death clears benefits but keeps interval; non-death clone/logout/restart retain
  remaining counters/quality. Vanilla cooldown shading is presentation only.
- Power replacement stays behind `CultivationService`. Ningshen affects only
  post-cap normal/spirit meditation stability, never progress, study, mastery or
  advancement. Profile remains v4. This alchemy wire layout was introduced in protocol22; current builds require protocol25.
- `tools/gen_alchemy_assets.py` owns models, textures, bilingual keys, recipes,
  loot and tags. Never hand-edit generated outputs. `--check` is deterministic;
  `--preview` writes actual-model previews to `out/preview/alchemy/`.

## Gates and evidence

Use generator `--check`, `tools/validate_alchemy.py`, related Python tests and
Modal `--preset alchemy-build` for the affected Java tests and practical jar.
This focused path has no game or full release suite. The old physical-formula
capture fixtures/coordinates in `alchemy_evidence.py` and
`alchemy_quality_evidence.py` need migration before running their complete
presets against 0.52.3; retain their historical evidence as historical.

The 0.50.0 foundation and 0.51.0 aptitude results are historical, not evidence
for 0.52.0. Current Modal evidence passes 62 release gates and 42 mapped
quality/effect/restart/two-client checks; 15 screenshots were inspected.
See [recorded runs and retries](../testing/2026-10-16-alchemy-quality.md). Owner appearance, feel,
balance and wider multiplayer remain `not_verified`. Natural acquisition,
cold legacy-save migration and other unobserved lifecycle surfaces remain
`not_verified`. Wild plant worldgen, skill XP, toxicity,
furnace failure remain outside this slice. Forging production is covered by [KB54](54_forging.md).

See also: [alchemy-foundation](../contracts/cultivation.md#alchemy-foundation),
[alchemy-quality](../contracts/cultivation.md#alchemy-quality),
[crafting-aptitudes](../contracts/cultivation.md#crafting-aptitudes),
[resource-export](../contracts/assets.md#resource-export),
[validation](../contracts/tooling.md#validation),
[item workflow](22_mod_item_creation.md), [aptitudes](50_crafting_aptitudes.md),
[meditation](30_cultivation_playable_loop.md),
[spiritual power](45_spiritual_power_sword_qi.md).

0.52.3 focused tests, build and owner-client layout: [verification record](../testing/2026-10-16-learned-formulas.md).

Six herb items are plantable on permanent Spirit Soil since 0.53.0; see [spirit farming](51_spirit_farming.md).

## Quality frames (0.58.3)

`item.QualityDisplay` owns the shared green/blue/purple/gold palette and delegates
pill classification to `PillQuality.get`; missing components remain Lower rather
than the weapon-only Mundane display. `PillQuality.name` and `AlchemyPillItem`
use that palette for ceiling/name text, including a quality line for renamed pills.
`client.item.QualityFrames` registers all three pills alongside basic combat weapons
for icon decoration and tooltip border coloring. Stack counts remain above the
1px frame. Rendering never applies/replaces a saved quality component.

Modal `--preset pill-quality-preview` runs `PillQualityTest`, builds the jar, then
captures mapped inventory/chest/hotbar views with all pill types and qualities,
legacy/explicit lower and renamed items, count glyph preservation, and adjacent
weapon frame checks. It does not repeat brewing or medicine-effect acceptance.

Delivery evidence: [2026-10-17 pill quality frames](../testing/2026-10-17-pill-quality-frames.md).

### 野外清瘴丹品质时长（0.84.2）

清瘴丹在 roster 作者配置中声明 `quality_duration`，使用原品质倍率延长持续清毒时间，清毒强度不变。`ExpeditionRules.Medicine.durationTicks` 供实际服用、物品说明和存档读取上限共用。必须先解析有效品质再裁剪剩余时间，不能把高品质余量裁回下品时长；旧存档中的剩余时间不补满。其他丹药仍沿用原有强度品质规则。
