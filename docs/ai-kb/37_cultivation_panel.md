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
Everything is drawn with fills, so there is no texture to maintain.

## Pages

| Tab (zh / en) | Class | Content |
|---|---|---|
| 内视 / Profile | `OverviewPage` | Stage ladder of the current realm, progress and stability bars, power, affinity; calendar and lifespan; root shares; the next advancement's target and conditions. |
| 修炼 / Meditation | `MeditationPage` | Session state, what normal and spirit meditation yield and cost, advancement target, conditions, duration, stability cost, interruption loss, and the four action buttons with their bound keys. |
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

To add a system: write a `PanelPage`, add a `View` constant with its tab
translation key, register the page in `CultivationProfileScreen.init`, and add
the keys to both language files.

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

The owner's verdict on the panel, and hover, focus, and click feel on a
physical mouse, are `not_verified` (README ledger).
