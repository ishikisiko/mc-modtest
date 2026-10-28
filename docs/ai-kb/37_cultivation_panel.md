> 0.100.2：H 增加“行迹”页，当前共八页；O 直接打开此页。行迹独立于修炼档案快照，支持固定出入按钮和正文滚动，其余页面继续等待修炼档案。

# Cultivation Panel

## 0.95.2：旧内景贴图体积

四张境界内景通过 `tools/cultivation_ink/export.json` 指定 512×512、单张 200 KiB 与合计 640 KiB 预算。`gen_cultivation_ink_assets.py` 验证原画 SHA-256 后以预乘 alpha 缩放并输出 RGBA；原画仍保留 1254×1254，不替换绘画内容。检查改为原画身份、导出一致性、实际尺寸／透明度和体积预算，不再要求运行时文件与原画逐字节相同。

## 0.95.1：四门功法活卷

- `TechniqueGrowthPage` 默认展示活卷，完整文字规则移入可滚动的“功法详解”；返回功法、主修切换及技能分配入口保留。详情通过 `PanelPage.dockHeight` 归还目录预留的底部空间，其他页面保持初始化 dock 高度。
- `TechniqueScrollLayout` 按实际 H 正文尺寸安排原画、参悟签、闭关按钮及卷底进度；字体继续使用 `PanelFonts`，不追加 TTF。`TechniqueScrollStyle` 仅按四个精确 ID 选择表现。
- `TechniqueScrollMotion.Tracker` 观察客户端收到的档案：首次打开只建立基线，只有修习实际增长才显示收益与入境印，参悟实际变化才反馈。行气要求服务器会话属于当前功法；准备期、收功、隐藏和切页分别处理，没有预览网页的自动修习／自动参悟。
- 功法动效是装饰，不冒充精确的服务器批次倒计时。数值、阶段门槛、成本与收益仍只取 `TechniqueGrowthRules`；原服务意图和协议 47 保持兼容。
- 原画及导出配置归 `tools/technique_scroll/`，只用 `tools/gen_technique_scroll_assets.py` 发布。运行时 576×384、128 色索引 PNG；单张 200 KiB、合计 768 KiB 上限由导出配置约束。高分辨率源图、提示词和网页预览不打包。
- 最小检查：导出器 `--check`，`TechniqueScrollTest`、既有成长／协议和相邻面板定向检查，再构建 jar。此次用户指定 Colab；无客户端视觉结论，外观与体验由用户验收。

0.80.0：H 标题的正文回退链展开为独立 TTF providers，保留字体/字号/回退顺序，避免标题与正文通过 reference 共享可变 FreeType face 后被并行预加载。原版 default 的 bitmap/unihex 回退仍引用。

## 0.79.0：H 全页水墨与功法图标格

- H 七页使用宣纸／墨绿主题，`InkPanelTheme` 仅供 H 页使用，原 `PanelTheme` 保留其他菜单兼容。
- `PanelFonts` 通过 client-only `PanelFontAccess` 读取原字体管理器的实时 FontSet 函数，创建仅 H 使用的 Font；默认字体映射到 `panel_body`，因此测宽、换行和绘制一致。中文正文用 Droid Sans Fallback，数字／拉丁字符复用 book.ttf，标题仍为毛笔字；不改全局字体或 Boss 字体。新字体保留 Apache-2.0 许可。
- 功法页按心法／绝技／身法／炼体筛选，显示已学图标格；心法发送原主修切换意图，可施放条目跳转技能页，完整属性／条件／传承／效果在悬停层。未知合法 ID 仍保留可见的未知图标。
- 用户确认仅六个已有高级绝技用独立原画，其余功法只用大类共用图标。不存在为 139 门功法逐一建立小类的要求。
- 技能页只列服务器同步的已学可施放技能，选图标再选六个固定槽位，分配／清空／随武器／默认仍走原服务器协议。品阶颜色同时保留在图标边框、角标、名称和已绑定槽位色带。
- 图标原画、配色与映射归 `tools/cultivation_panel/`；`tools/gen_cultivation_panel_assets.py --check` 检查导出。六张原画保留 1254×1254 母版，运行时为 256×256 RGBA PNG；大类与导航为 128×128。只打包 resources 导出，不把母版塞入 jar。
- `PanelPage.topInset()` 和原底部 dock 分别保护固定筛选与固定操作；滚动仅作用于图标正文。布局纯逻辑在 `TechniqueGridLayout`，七页仍通过独立 page hooks 处理交互。
- 定向云端入口：`ink-hub-build` / `ink-hub`，覆盖面板单测、完整资源 jar、映射 H 导航／分类／主修切换／绑定／修炼，以及 GUI 2 窄屏、GUI 4 字体截图。不自动执行全量 release。

实机结果见 [0.79.0 H 水墨面板记录](../testing/2026-10-22-ink-hub.md)；预览原画和 HTML 不能代替游戏内清晰度与玩家视觉验收。

## 0.78.0：已学技能按键分配

H 增加「技能」页 `SkillBindingsPage`，沿用原有深色金边主题。正文滚动列出已学且有实际执行器的主动武技、波技能、身法；下方固定六键与操作区，先选槽位再分配技能。Z/C 默认随武器两招，I/J/K/M 默认空白；同一技能重绑会移走旧位置。清空、随武器、整套默认和原版按键设置各有明确入口；冲突提示读取真实 KeyMapping。

`SkillBindings` 保存在服务器 `CombatPreference` attachment，沿用 copyOnDeath，旧保存缺字段时补默认。绑定只接受已学可施放 ID，施放只发槽位与方向；技能执行仍由原武技、蓄力/松手和身法路径决定。物理键位由客户端原版选项保存，技能选择按服务器角色保存。额外已分配槽位进入同一快捷栏，快捷键改绑不会清除冷却。普通心法、被动与未实现主动不进入绑定列表。

实机与固定源记录：[0.78.0 已学技能分配](../testing/2026-10-22-skill-bindings.md)。

## 0.71.0：分境界水墨内景

用户确认的最终方向是非写实水墨底形与独立动态流转；已淘汰的人物立绘、
整图上下浮动和早期几何图案不作为实现基线。

- `MeditationPage` 仍独占四个有界操作意图，`RealmInkPage` 只读 `PanelContext`。
  修炼页采用独立的 `CultivationInkTheme`；切回其他 H 页面恢复原有主题。
  收益区悬停保留普通收益、灵石收益与消耗、固本状态、吐纳熟练度和灵石总量。
- `RealmInkStyle` 用完整 registry ID 选择纯表现样式。未知 ID 回落通用内景，
  不按前缀或境界序号推算游戏规则。`nascent_soul` 仅有预留图层，不新增 registry、
  修炼 cap 或金丹后期突破目标。
- `RealmInkMotion` 提供确定性的周天、液滴、环丹及婴体路径。`RealmInkView`
  分别绘制后景流、静止底形和前景流。时钟只有服务器活动状态才推进；
  收功、切离页面、未同步状态不会在下次打开时补跑动画时间。
- 固本由满修为且处于实际修炼会话决定；研读优先于固本；准备期不显示自造倒计时。
  冲关环的进度只取同步的 duration/remaining，粒子数和速度不代表额外数值。
- 原画 authored inputs 位于 `tools/cultivation_ink/source/`，生成提示词同目录上级；
  `python3 tools/gen_cultivation_ink_assets.py` 发布到 `textures/gui/cultivation/ink/`。
  只修改 authored 输入后再生成；0.95.2 起按上述导出配置缩小运行时贴图，`--check` 验证与导出结果一致。
- `PanelPage.renderOverlay` 在正文 scissor 解除且控件绘制后显示只读 tooltip；
  正文仍通过既有 pointer hook 判断悬停。小窗口沿用正文滚动和固定按钮区。
- 必要检查：本次受影响的修炼源约束、采集器裁切测试与
  Modal `--preset cultivation-ink`。该 preset 运行 panel 单元测试、完整资源 jar
  和真实客户端 H 导航/按钮采集；不运行全量 release 或 Chunky。

实机接入与验证：[0.71.0 水墨修炼页](../testing/2026-10-21-cultivation-ink.md)。
最终宽屏三境与窄屏读数已复验；`cultivation-ink-layout` 用于已有玩法证据后的布局重采。

以下 0.31–0.38 章节保留为历史实现说明；其中原经脉人物图已由上述修炼页替代，
`Meridian*` 类型继续保留为既有路线与渲染参考。玩家审美、手感、FPS 和多人验收
必须按实际观察另行标记，云端截图不能代替玩家结论。

The H screen since 0.31.0: a hub with a page rail, built to take one page per
gameplay system. It replaces the two-tab screen of notes
[28](28_cultivation_core.md) and [30](30_cultivation_playable_loop.md); what
the panel may read and send is unchanged and stays specified in
[cultivation-meditation](../contracts/cultivation.md#cultivation-meditation),
[cultivation-state-synchronization](../contracts/cultivation.md#cultivation-state-synchronization),
[cultivation-lifespan-calendar](../contracts/cultivation.md#cultivation-lifespan-calendar),
and [cultivation-core-validation](../contracts/tooling.md#cultivation-core-validation).
Since 0.36.0 it also has a 天下 page that reads the world ledger (命簿) of
[40_world_sim.md](40_world_sim.md) through its own read-only query. Since
0.37.0 the 功法 page groups techniques and can switch the running core
technique, and the meridian diagram follows that technique's route
([41_technique_system.md](41_technique_system.md)). Since 0.38.0 the 修炼 page
shows a study card while a technique manual is read (研读; "Study card" below).

## Layout

```text
header   face, name, realm · stage            calendar, remaining lifespan
rail     one tab per page   | body: the open page, scrolls when it overflows
                            | dock: the page's fixed widgets (optional)
footer   session state                        close key, profile schema
```

The header and footer show on every page. The panel is at most 480x246 GUI
pixels and shrinks with the window. From 427x240 up (854x480 at scale 2,
2560x1440 at auto scale) every cultivation page fits without scrolling in
Chinese; below that width a page stacks its cards in one column and the body
scrolls. The 天下 page's lists can be longer than the body at any size and
then scroll.
Dates read year, week and day (0.36.1: `1 年 第 1 周 第 1 日` in the header and
the 内视 calendar card, `启元N年 第W周第D日` on the 天下 总览 row, the week length
from the time snapshot's `daysPerWeek`); the header drops its 宗历 label when
the name and standing would keep less than 130 px (English at GUI 320).
Everything is drawn with fills except the meridian diagram on the Meditation
page (0.32.0), which has one generated texture and its own vector drawing; see
"Meridian diagram" below.

## Pages

| Tab (zh / en) | Class | Content |
|---|---|---|
| 内视 / Profile | `OverviewPage` | Stage ladder of the current realm, progress and stability bars, power, affinity; calendar and lifespan; root shares; the next advancement's target and conditions. |
| 修炼 / Meditation | `MeditationPage` | The meridian diagram (seated figure, small circuit, acupoints, dantian) lit and animated for the session state; beside it (below it when narrow) progress and stability, what normal and spirit meditation yield and cost, advancement target, conditions, duration, stability cost, interruption loss; and the four action buttons with their bound keys. While a study session runs (0.38.0) a study card replaces the normal and spirit cards. |
| 功法 / Techniques | `TechniquesPage` | Since 0.37.0 one card per category, 心法 / 绝技 / 身法 / 炼体 (Core Methods / Battle Arts / Movement Arts / Body Tempering), empty ones left out, plus a last 未知功法 card for learned ids the synchronized registry does not know; inside a card highest grade first, then by name. Each entry: name, mastery, chips (category, grade name 凡阶..天阶, school, each heritage with the technique's position such as `太白剑脉 2/4`, elements; up to three chip rows), and its stated requirement. The running core technique carries a jade 运转中 (Running) chip; every other learned core technique a 运转此心法 (Run this method) button. With nothing learned, an empty card with a hint. |
| 天下 / World | `WorldPage` | The world ledger, read-only, in five sub-views chosen from the dock: 总览 (era date, settlement running or paused with pending days, tier, living against target, dead, sects active and destroyed, event count; living per realm as bars; the five foremost people), 宗门 (every sect, active first, with master, members, top realm, prestige, gate built or not), 人物 (live search by name or Daoist title, living first), 纪事 (latest notable and major events, newest first, each with the line it answers), 此地 (the player's region, the sects seated there with gate distance, the strongest people present, recent events). Rows drill down into a sect (founding, parent, master, resources, prestige, signature technique, its 传承 when it holds one (0.37.0), gate, relations, members at the sect, recent events) or a person (realm and progress, root grade and five-element shares, age, master, sect and rank, whereabouts, technique, injury or death, relations by kind, recent events); a back row returns. |

A ladder node is solid for a passed stage, a gem for the current one, an
outline for a later one, and a small faint outline for a stage with neither a
cultivation cap nor an advancement into it (Qi Refining V to IX today).

## Code

| File | Role |
|---|---|
| `client/cultivation/CultivationProfileScreen.java` | The hub: frame, header, footer, rail, page switching (`View`, `setView`), body scissor and scrolling. Reopening H returns to the last page. Each frame it passes the pointer to the open page before drawing it, forwards clicks inside the body viewport to it, and resets its scroll when the page asks. While a text field has focus, keys go to the field before the H close key (Escape still closes). |
| `client/cultivation/panel/PanelPage.java` | What a page implements: `init` (dock widgets, returns the dock height), `setVisible`, `refresh`, `render` (returns the body height). Optional hooks, no-ops by default (0.36.0): `pointer(mouseX, mouseY)` (screen coordinates before each frame, -1, -1 outside the body viewport), `mouseClicked` (a click inside the viewport; true when used), `takeScrollToTop` (true once to scroll the body back to the top). |
| `panel/PanelContext.java` | One frame's read of `ClientCultivationState` and the synchronized registries, with the shared readouts (progress, stability, calendar, lifespan, session). |
| `panel/PanelReadouts.java` | Display arithmetic without Minecraft rendering (since 0.38.0 also the session-text key, study percent and bar fraction, gate key, and whether stability is short of the next gate); covered by `PanelReadoutsTest`. |
| `panel/PanelTheme.java`, `panel/PanelButton.java` | Colors, card/bar/chip/ladder primitives, and the themed vanilla `Button`. |
| `panel/MeridianChart.java` | The diagram as data: acupoints and channels in the figure texture's normalised square. Holds no player state. |
| `panel/MeridianPath.java` | A channel route smoothed into a polyline measured by arc length. |
| `panel/MeridianLook.java` | The look for each `MeditationState` (colour, levels, mote count and period, gathering, halo, barrier). |
| `panel/MeridianView.java`, `panel/VectorBrush.java` | Drawing: the figure texture, channels, motes, acupoints, dantian gauge, labels; feathered strokes, discs, and arcs at fractional GUI coordinates. |
| `panel/WorldPage.java` | The 天下 page: dock (five sub-view buttons and the 人物 search box), navigation state (sub-view, open detail, back stack; static so it survives the hub rebuilding pages on resize), the query for the open view, and the cards of each view. |
| `panel/WorldCanvas.java` | One frame of drawing for the 天下 page: cards measured by the same code that fills them, wrapped text, clickable rows recorded as screen-space hit boxes with a hover tint. |
| `panel/WorldReadouts.java` | The 天下 page's arithmetic without Minecraft rendering (ages, era dates, shares, root bar widths, relation grouping, newest-first order, colours); covered by `WorldReadoutsTest`. |
| `panel/TechniquesPage.java` | The 功法 page (0.37.0): group cards, entries, chips, and the per-technique 运转此心法 buttons, drawn in the body (not the dock) at the entry's position each frame; only buttons drawn this frame take clicks. |
| `panel/TechniqueShelf.java` | The 功法 page's arithmetic without Minecraft rendering: grouping and order, grade-name keys, chain position `n/m`, chip rows; covered by `TechniqueShelfTest`. |
| `panel/MeridianRoute.java` | Which circuit and channels the diagram lights for a running core technique's `meditation_route` (0.37.0); covered by `MeridianRouteTest`. |
| `tools/gen_meridian_figure.py` | Generates `textures/gui/cultivation/meridian_figure.png` (standard library only; `--check` fails when the file on disk differs, `--preview` overlays the chart). |

To add a system: write a `PanelPage`, add a `View` constant with its tab
translation key, register the page in `CultivationProfileScreen.init`, and add
the keys to both language files.

## Meridian diagram

The Meditation page is built around a figure sitting cross-legged in profile,
facing right, so the governing vessel (督脉) rises along the back and the
conception vessel (任脉) falls along the front. Together they are the small
circuit (小周天) through the lower dantian. Labelled points: 会阴, 命门, 大椎,
百会, 膻中, 丹田; 尾闾, 夹脊, 玉枕, 印堂, 承浆, 劳宫, 涌泉 are unlabelled nodes. A
hand channel runs from 膻中 to the palm and a foot channel from 会阴 to the sole.

The diagram is a view of the session, not a gameplay system. What it shows:

| Element | Driven by |
|---|---|
| Colour, brightness, and motion of figure and channels | `MeditationState` from the synced status, and whether the synced profile has an awakened root and Basic Breathing (neither: dormant grey) |
| Dantian fill | cultivation progress over the stage cap |
| Ring around the dantian | stability over its cap |
| Halo behind the head while advancing | elapsed share of the advancement (duration minus remaining ticks) |
| Caption under the figure | session state; remaining advancement ticks; or the missing prerequisite |

Per state: at rest the channels are dim and still; preparing draws qi in toward
the dantian with no countdown; normal meditation sends jade motes round the
circuit; spirit-stone meditation is blue, faster, and adds motes running inward
from palm and sole; an ordinary advancement is gold with the halo; a bottleneck
advancement is red-orange and the motes bunch at 玉枕. Mote counts and speeds
are decoration and stand for no value.

Since 0.37.0 the lit route follows the running core technique:
`MeditationPage` reads its `effects.core.meditation_route` from the synced
profile and registry, and `MeridianRoute.of` maps it to a circuit (the loop
the motes run on, where a bottleneck holds them back) and the set of lit
channels; an unlit channel would stay a faint line. Only `xiaozhoutian` (the
small circuit with every channel lit, the look above) exists, and an unknown
route, a technique without one (Basic Breathing), or no running technique
falls back to it, so the diagram looks as it did in 0.32.0.

No meridian state exists on the server. There is deliberately no "channels
opened n/m" readout. A later meridian system can light channels one by one by
`MeridianChart.Channel.id()`; until then every channel of a kind is lit alike.

`VectorBrush` collects a batch's vertices itself and hands them to the
`Tesselator` only in `end`. Every `Tesselator.begin` writes into one shared
buffer, so two batches built there at once mix their vertices.

To move a point or reshape a channel, edit `MeridianChart` and look at
`python3 tools/gen_meridian_figure.py --preview /tmp/figure.png`, which overlays
the chart read from the Java source on the texture. To change the figure, edit
the script and regenerate; the chart's coordinates are in the texture's square.

## Study card

0.38.0. A study (研读) session is meditation's third mode
([41_technique_system.md](41_technique_system.md), "Manuals and study"); its
states are the normal ones (`PREPARING_NORMAL`, `MEDITATING_NORMAL`), and
`MeditationStatus.study()` carries a `StudyProgress`. While that is present
(`PanelContext.study()`), `MeditationPage` draws one more card:

- title `研读《功法名》` (`screen.myvillage.cultivation.study.title`; the name
  from the synchronized technique registry, the raw id with the unavailable
  marker when it is missing), accented in the diagram's colour, with the
  whole percent comprehended (rounded down, as the manual's tooltip) as its
  chip;
- a 参悟 meter, `points / totalPoints`;
- `下一关 n 点 · 耗稳定度 m` (n is the gate's point value), amber when the
  synced stability is below m, or `无关卡` when `nextGatePoints` is -1;
- `按 X 停止` with the bound stop key.

It takes the place of the normal and spirit cards for the length of the
session (they describe modes that are not running) and sits at the top of the
readout column beside the figure, above the progress and stability meters and
the advancement card. Below the wide threshold (330 px of body) it goes above
the figure, so the progress shows without scrolling at 320x240, and the figure
and the other cards follow and scroll. With the two mode cards gone the column
is shorter than in normal meditation, so the page still fits 480x270 and
427x240 without scrolling (by arithmetic; not looked at yet).

The session text in the footer, the 内视 session row, and the diagram's caption
reads 准备研读 during the preparation and 研读中 afterwards
(`screen.myvillage.cultivation.study.preparing|reading`, chosen by
`PanelReadouts.sessionKey`); the colour stays amber while preparing and jade
while reading. The diagram looks as in normal meditation. The buttons behave
as in any session: stop enabled, the three starts disabled. Nothing new is
sent: a study starts by using the manual (item use, not a panel payload) and
the stop button sends the existing STOP intent.

## World page (天下)

0.36.0. The page is a reader of the world ledger, which lives only on the
server ([40_world_sim.md](40_world_sim.md), "In-game panel"). It holds no
ledger and no ledger logic:

- Its ledger data comes only from `client/sim/ClientWorldSimState` (from
  `PanelContext` it takes only the font and element colours): `refresh` calls
  `request(query)` for the open view every frame while the page is visible,
  and `render` draws `latest(query)`. The cache sends the same
  `WorldSimQuery` at most once per 2.5 s (`MIN_REPEAT_NANOS`), so the open
  view follows the ledger at that pace; the server answers at most one query
  per player every 4 ticks and drops a faster one without an answer. The page
  never sends a packet itself; the sender is installed by `WorldSimPayloads`.
  The cache is cleared on logout.
- Kinds per view: 总览 `OVERVIEW`, 宗门 `SECTS`, 人物 `PERSON_SEARCH` (not
  sent while the search text is empty), 纪事 `CHRONICLE`, 此地 `HERE`; an open
  detail asks `SECT` or `PERSON` by id. A search shows its previous answer
  until the new one arrives.
- Lists are capped on the server (`WorldSimSnapshot.MAX_*`: 96 sects, 24
  members, 10 search matches, 10 people present or foremost, 40 chronicle
  lines, 10 recent events per sect, person, or region). Only the search says
  it may be cut ("only the first 10", shown when it returns exactly 10).
- States: no answer yet (loading card), ledger inactive (a card with
  `commands.myvillage.world.inactive` and the server's reason), outside every
  region on 此地, and an empty list each get their own card or line.
- Text: person, sect, technique, and region names arrive as literals (Chinese
  in both languages). Everything else is a language key: the page's own
  `screen.myvillage.cultivation.world.*`, the command keys it reuses
  (`commands.myvillage.world.*` for tier, relation and gate state, running or
  paused, inactive, outside, not found, no sects here), and the ledger's
  `world_sim.*` words through `WorldSimText` (realm, stage, rank, status,
  cause, and each event line, whose `@`-prefixed params are themselves keys).
- Below 300 GUI pixels of body width the cards stack in one column and the
  dock takes two rows (buttons, then the search box); it also takes two rows
  when the five labels do not fit beside the search box. All text is cut with
  `PanelTheme.fit` or wrapped inside its card.

## Rules

Unchanged since 0.31.0, with the 天下 page's additions:

- The cultivation pages read only the three clientbound cultivation caches
  and synchronized registries; the 天下 page's data comes only from
  `ClientWorldSimState`. No page writes profile or ledger data.
- Two bounded serverbound cultivation payloads exist (unchanged in 0.38.0:
  starting a study is a right-click on a manual, which is item use, and the
  study card adds no input). The meditation intent:
  each of the four actions is bound to exactly one button, in
  `MeditationPage`. The core-technique switch (0.37.0,
  `myvillage:core_technique_switch`, a technique id and nothing else): sent
  only from the 功法 page's 运转此心法 buttons through
  `ClientCultivationIntentSender.sendCoreSwitch`; the server checks it in
  `CultivationService.switchCoreTechnique`, answers a success with a new
  snapshot and a refusal with nothing. Switching pages sends nothing. The world query is a
  separate read-only payload (`WorldSimQueryPayload`, registered by
  `sim/runtime/net/WorldSimPayloads`, not under `cultivation/`) that the cache
  sends on the page's behalf.
- Pages may take hover and clicks through the `pointer` and `mouseClicked`
  hooks; the 天下 page uses them only to navigate between its own views, the
  功法 page only for its 运转此心法 buttons.
- Button enablement is advisory. The server decides every start, stop, cost,
  and result.
- The legacy meditation reserve is not shown.

The cultivation validators read the screen and every file under `panel/` as
one source, so these rules hold for a page added later
(`tools/validate_cultivation_{initiation,lifespan,meditation,gain,advancement}.py`).

Preparation has no countdown: the server announces it once, so a number would
stand still. Advancement shows remaining ticks at the server's feedback
interval.

## Evidence

2026-10-05, development host, headless client (llvmpipe, 960x540, GUI
480x270), English and Chinese: all three pages in the mortal, Qi Refining II,
and Qi Refining IV states; preparing, meditating, and advancing driven through
the panel buttons, ending in a completed advancement to Qi Refining III; GUI
427x240 and 320x240 in Chinese.

The same day the 0.31.0 jar was deployed to the owner's Windows client through
DevHost and the three pages were opened there through DevBridge (GUI 534x300,
Chinese, the save's mortal unawakened profile). That shows the panel opens and
draws on that client; it showed no populated data there. Screenshots of both
hosts are in `out/preview/cultivation_panel/` (untracked).

0.32.0 meridian diagram, 2026-10-05, development host, headless client
(llvmpipe, GUI 480x270, Chinese): dormant (reset profile), rest, preparing,
normal and spirit-stone meditation, an ordinary advancement (Qi Refining II to
III) and a bottleneck advancement (III to IV), each started from the page's
buttons and run to completion; rest and spirit-stone meditation at GUI 320x240
and rest at 427x240. English was looked at at 427x240 and 320x240 on a build
from before two fixes made the same day (`VectorBrush` batching, bottleneck
slow point); it was not looked at again afterwards. The 0.32.0 jar was then
deployed to the owner's Windows client through DevHost and the page opened
there through DevBridge (GUI 534x300, Chinese, the save's profile: mortal, qi
sensed, Basic Breathing learned): the rest state draws correctly on that GPU.
No session was started there. Stills and clips are in
`out/preview/meditation_meridian/` (untracked).

0.36.0 天下 page, 2026-10-06, the owner's PC (real GPU, Chinese client,
singleplayer world `agent-test`, GUI 534x300 at scale 3, then 480x270,
427x240, and 320x240 by resizing the window): all five sub-views (总览, 宗门,
人物 with live search, 纪事 with cause lines, 此地), drilling into a sect and a
person, back navigation, hover highlight, one-column stacking, and the
two-row dock below 300 wide. English at narrow widths, multiplayer, the
ledger-inactive card on a real client, and feel on a physical mouse were not
looked at. Stills are in `out/preview/world_sim_panel/pc/` (untracked).

0.37.0 功法 page and route-driven meridian diagram: not looked at on any
client yet; layout at GUI 480x270, 427x240, and 320x240 in both languages,
the switch button, and the unchanged diagram are `not_verified` (README
ledger "Technique system (0.37.0)").

0.38.0 study card: not looked at on any client yet; its layout at GUI
480x270, 427x240, and 320x240 in both languages, the session text, and the
stop button ending a study are `not_verified` (README ledger "Technique
manuals (0.38.0)").

The owner's verdict on the panel, the meridian diagram, the 天下 page, the
功法 page, and the study card, and hover, focus, and click feel on a physical
mouse, are `not_verified` (README ledger).

## Native atlas entry (0.67.0)

天下 adds a sixth dock entry, 舆图 / Atlas, opening `WorldAtlasScreen` while retaining the
previous 天下 view for return. Dock sizing uses the actual section count. This screen is a
read-only projection of the save's frozen worldplan; see [55](55_seeded_worldplan.md#in-game-ink-atlas-0670).
