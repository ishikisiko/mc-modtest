# Cultivation Panel

The H screen since 0.31.0: a hub with a page rail, built to take one page per
gameplay system. It replaces the two-tab screen of notes
[28](28_cultivation_core.md) and [30](30_cultivation_playable_loop.md); what
the panel may read and send is unchanged and stays specified in
[cultivation-meditation](../../openspec/specs/cultivation-meditation/spec.md),
[cultivation-state-synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md),
[cultivation-lifespan-calendar](../../openspec/specs/cultivation-lifespan-calendar/spec.md),
and [cultivation-core-validation](../../openspec/specs/cultivation-core-validation/spec.md).

## Layout

```text
header   face, name, realm · stage            calendar, remaining lifespan
rail     one tab per page   | body: the open page, scrolls when it overflows
                            | dock: the page's fixed widgets (optional)
footer   session state                        close key, profile schema
```

The header and footer show on every page. The panel is at most 480x246 GUI
pixels and shrinks with the window. From 427x240 up (854x480 at scale 2,
2560x1440 at auto scale) every page fits without scrolling in Chinese; below
that width a page stacks its cards in one column and the body scrolls.
Everything is drawn with fills except the meridian diagram on the Meditation
page (0.32.0), which has one generated texture and its own vector drawing; see
"Meridian diagram" below.

## Pages

| Tab (zh / en) | Class | Content |
|---|---|---|
| 内视 / Profile | `OverviewPage` | Stage ladder of the current realm, progress and stability bars, power, affinity; calendar and lifespan; root shares; the next advancement's target and conditions. |
| 修炼 / Meditation | `MeditationPage` | The meridian diagram (seated figure, small circuit, acupoints, dantian) lit and animated for the session state; beside it (below it when narrow) progress and stability, what normal and spirit meditation yield and cost, advancement target, conditions, duration, stability cost, interruption loss; and the four action buttons with their bound keys. |
| 功法 / Techniques | `TechniquesPage` | Each learned technique's category, grade, elements, mastery, and stated requirements. |

A ladder node is solid for a passed stage, a gem for the current one, an
outline for a later one, and a small faint outline for a stage with neither a
cultivation cap nor an advancement into it (Qi Refining V to IX today).

## Code

| File | Role |
|---|---|
| `client/cultivation/CultivationProfileScreen.java` | The hub: frame, header, footer, rail, page switching (`View`, `setView`), body scissor and scrolling. Reopening H returns to the last page. |
| `client/cultivation/panel/PanelPage.java` | What a page implements: `init` (dock widgets, returns the dock height), `setVisible`, `refresh`, `render` (returns the body height). |
| `panel/PanelContext.java` | One frame's read of `ClientCultivationState` and the synchronized registries, with the shared readouts (progress, stability, calendar, lifespan, session). |
| `panel/PanelReadouts.java` | Display arithmetic without Minecraft rendering; covered by `PanelReadoutsTest`. |
| `panel/PanelTheme.java`, `panel/PanelButton.java` | Colors, card/bar/chip/ladder primitives, and the themed vanilla `Button`. |
| `panel/MeridianChart.java` | The diagram as data: acupoints and channels in the figure texture's normalised square. Holds no player state. |
| `panel/MeridianPath.java` | A channel route smoothed into a polyline measured by arc length. |
| `panel/MeridianLook.java` | The look for each `MeditationState` (colour, levels, mote count and period, gathering, halo, barrier). |
| `panel/MeridianView.java`, `panel/VectorBrush.java` | Drawing: the figure texture, channels, motes, acupoints, dantian gauge, labels; feathered strokes, discs, and arcs at fractional GUI coordinates. |
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

## Rules That Did Not Change

- The panel reads only the three clientbound caches and synchronized
  registries. It writes no profile data.
- The only serverbound traffic is the existing bounded meditation intent. Each
  of the four actions is bound to exactly one button, in `MeditationPage`;
  switching pages sends nothing.
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

The owner's verdict on the panel and on the meridian diagram, and hover, focus,
and click feel on a physical mouse, are `not_verified` (README ledger).
