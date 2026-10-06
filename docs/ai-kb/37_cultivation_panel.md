# Cultivation Panel

The H screen since 0.31.0: a hub with a page rail, built to take one page per
gameplay system. It replaces the two-tab screen of notes
[28](28_cultivation_core.md) and [30](30_cultivation_playable_loop.md); what
the panel may read and send is unchanged and stays specified in
[cultivation-meditation](../../openspec/specs/cultivation-meditation/spec.md),
[cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md),
[cultivation-lifespan-calendar](../../openspec/specs/cultivation-lifespan-calendar/spec.md),
and [cultivation-core-validation](../../openspec/specs/cultivation-core-validation/spec.md).
Since 0.36.0 it also has a 天下 page that reads the world ledger (命簿) of
[40_world_sim.md](40_world_sim.md) through its own read-only query.

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
Everything is drawn with fills except the meridian diagram on the Meditation
page (0.32.0), which has one generated texture and its own vector drawing; see
"Meridian diagram" below.

## Pages

| Tab (zh / en) | Class | Content |
|---|---|---|
| 内视 / Profile | `OverviewPage` | Stage ladder of the current realm, progress and stability bars, power, affinity; calendar and lifespan; root shares; the next advancement's target and conditions. |
| 修炼 / Meditation | `MeditationPage` | The meridian diagram (seated figure, small circuit, acupoints, dantian) lit and animated for the session state; beside it (below it when narrow) progress and stability, what normal and spirit meditation yield and cost, advancement target, conditions, duration, stability cost, interruption loss; and the four action buttons with their bound keys. |
| 功法 / Techniques | `TechniquesPage` | Each learned technique's category, grade, elements, mastery, and stated requirements. |
| 天下 / World | `WorldPage` | The world ledger, read-only, in five sub-views chosen from the dock: 总览 (era date, settlement running or paused with pending days, tier, living against target, dead, sects active and destroyed, event count; living per realm as bars; the five foremost people), 宗门 (every sect, active first, with master, members, top realm, prestige, gate built or not), 人物 (live search by name or Daoist title, living first), 纪事 (latest notable and major events, newest first, each with the line it answers), 此地 (the player's region, the sects seated there with gate distance, the strongest people present, recent events). Rows drill down into a sect (founding, parent, master, resources, prestige, signature technique, gate, relations, members at the sect, recent events) or a person (realm and progress, root grade and five-element shares, age, master, sect and rank, whereabouts, technique, injury or death, relations by kind, recent events); a back row returns. |

A ladder node is solid for a passed stage, a gem for the current one, an
outline for a later one, and a small faint outline for a stage with neither a
cultivation cap nor an advancement into it (Qi Refining V to IX today).

## Code

| File | Role |
|---|---|
| `client/cultivation/CultivationProfileScreen.java` | The hub: frame, header, footer, rail, page switching (`View`, `setView`), body scissor and scrolling. Reopening H returns to the last page. Each frame it passes the pointer to the open page before drawing it, forwards clicks inside the body viewport to it, and resets its scroll when the page asks. While a text field has focus, keys go to the field before the H close key (Escape still closes). |
| `client/cultivation/panel/PanelPage.java` | What a page implements: `init` (dock widgets, returns the dock height), `setVisible`, `refresh`, `render` (returns the body height). Optional hooks, no-ops by default (0.36.0): `pointer(mouseX, mouseY)` (screen coordinates before each frame, -1, -1 outside the body viewport), `mouseClicked` (a click inside the viewport; true when used), `takeScrollToTop` (true once to scroll the body back to the top). |
| `panel/PanelContext.java` | One frame's read of `ClientCultivationState` and the synchronized registries, with the shared readouts (progress, stability, calendar, lifespan, session). |
| `panel/PanelReadouts.java` | Display arithmetic without Minecraft rendering; covered by `PanelReadoutsTest`. |
| `panel/PanelTheme.java`, `panel/PanelButton.java` | Colors, card/bar/chip/ladder primitives, and the themed vanilla `Button`. |
| `panel/MeridianChart.java` | The diagram as data: acupoints and channels in the figure texture's normalised square. Holds no player state. |
| `panel/MeridianPath.java` | A channel route smoothed into a polyline measured by arc length. |
| `panel/MeridianLook.java` | The look for each `MeditationState` (colour, levels, mote count and period, gathering, halo, barrier). |
| `panel/MeridianView.java`, `panel/VectorBrush.java` | Drawing: the figure texture, channels, motes, acupoints, dantian gauge, labels; feathered strokes, discs, and arcs at fractional GUI coordinates. |
| `panel/WorldPage.java` | The 天下 page: dock (five sub-view buttons and the 人物 search box), navigation state (sub-view, open detail, back stack; static so it survives the hub rebuilding pages on resize), the query for the open view, and the cards of each view. |
| `panel/WorldCanvas.java` | One frame of drawing for the 天下 page: cards measured by the same code that fills them, wrapped text, clickable rows recorded as screen-space hit boxes with a hover tint. |
| `panel/WorldReadouts.java` | The 天下 page's arithmetic without Minecraft rendering (ages, era dates, shares, root bar widths, relation grouping, newest-first order, colours); covered by `WorldReadoutsTest`. |
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
- The only serverbound cultivation traffic is still the existing bounded
  meditation intent. Each of the four actions is bound to exactly one button,
  in `MeditationPage`; switching pages sends nothing. The world query is a
  separate read-only payload (`WorldSimQueryPayload`, registered by
  `sim/runtime/net/WorldSimPayloads`, not under `cultivation/`) that the cache
  sends on the page's behalf.
- Pages may take hover and clicks through the `pointer` and `mouseClicked`
  hooks; the 天下 page uses them only to navigate between its own views.
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

The owner's verdict on the panel, the meridian diagram, and the 天下 page, and
hover, focus, and click feel on a physical mouse, are `not_verified` (README
ledger).
