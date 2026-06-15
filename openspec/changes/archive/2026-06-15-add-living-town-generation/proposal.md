## Why

The town pipeline can produce a `cultivation_town` **street block** (`courtyard_street_block`), but a block placed as a static NBT via `/place template` is still a flattened stamp: it has no town-scale legibility, no lived-in detail, and no relationship to the ground it lands on. The result reads as "a pile of houses," not 一座有人烟味的城镇.

The qualities that make a settlement read as a *complete town at a glance* — **层次 (legible spatial hierarchy)**, **人烟味 (lived-in human presence)**, and **真实市井 (authentic market-street life)** — do not live in the macro layout engine. They live in the **meso/micro connective tissue**: the street as a furnished room, the active shopfronts, and the gaps between buildings filled with the traces of daily life, all meeting the terrain without floating or burial. That tissue is exactly what the current system lacks. This change rebuilds the town's "brain" around producing those qualities.

## What Changes

- **Rebuild the town brain as a runtime subsystem.** The town is no longer a pre-baked mega-NBT; it is planned and realized in the live world (terrain-aware) and summoned by a new `/myvillage town` command. The mature Python **building vocabulary** (archetypes/roofs/facades/motifs) is retained and shipped as a parts library; only the *assembly + realization* layers are rebuilt.
- **Macro — heuristic town planner first.** A hand-written planner produces a legible skeleton: enclosure (城墙 + 城门), a main-street **spine**, one **dominant landmark** that tops the skyline, and an **importance/density/height/roof-grade gradient** from center to edge. Pluggable engines (WFC / shape grammar / organic streets) are explicitly deferred — added one at a time later.
- **Meso — street-as-room.** Streets and squares become furnished rooms: **continuous active frontage** (shopfronts, counters, 招幌, awnings — not blank walls) on both sides, with a width/paving gradient (青石主街 vs 夯土巷) and street furniture.
- **Micro — lived-in tissue.** A scatter pass fills *intentionally-shaped negative space* with domestic and market props (water wells, 晾衣, 柴垛, 菜畦, stalls, lanterns), **smoke and light** (chimney 炊烟, lit windows, 香炉), and **wear/imperfection**.
- **Ground — site-fit.** Each parcel samples the heightmap and grows a **plinth / steps / retaining course** so the town meets the land gracefully ("不突兀"); streets follow slope with steps/ramps. Bounded — not full organic terracing.
- **Building frontage metadata.** Buildings expose which side faces the street and where the shopfront opens, plus importance-driven massing/roof-grade hooks, so the meso/micro passes can attach to them.
- **Soft functional brief.** `cultivation_town` may carry a soft program (housing / market / civic / defense counts) as *guidance*, not a hard constraint to satisfy.
- **End state is a verifiable, complete mod.** This change concludes with a buildable jar where `/myvillage town` generates an inspectable living town, town-level validation passes, and command docs are updated.

## Capabilities

### New Capabilities
- `town-plan`: Macro heuristic planner that outputs a terrain-aware town model — enclosure/gates, a main-street spine, one dominant landmark, an importance/density/height/roof-grade gradient, district zoning, and intentionally-shaped negative space. Accepts an optional soft brief.
- `street-room`: Meso layer that treats streets and squares as furnished rooms — continuous active street frontage plus street furniture, with a width/paving hierarchy.
- `lived-in-tissue`: Micro scatter layer that dresses designed negative space with domestic & market props, chimney smoke, lantern/lit-window light, and wear, producing 人烟味.
- `town-realization`: Runtime subsystem that assembles a planned town in the live world against terrain — including **site-fit** (heightmap sampling, plinth/steps/retaining, "不突兀") — exposes the `/myvillage town` command, and owns the town-level acceptance loop (generate → validate → preview → jar → docs).

### Modified Capabilities
- `building-generation`: Buildings expose **frontage metadata** (street-facing side, shopfront opening) and **importance-driven** massing/roof-grade selection hooks that the meso/micro passes consume.
- `settlement-group`: `cultivation_town` rebinds from the standalone block library to the new town-generation system, and MAY declare a soft functional brief as guidance.

## Impact

- **New runtime mod subsystem** under `src/main/java/com/example/myvillage/` (town planner + realizer + `/myvillage town` command). The mod is no longer "no worldgen registered."
- `tools/buildgen/archetypes.py` (+ `facade.py`/`massing.py`): emit frontage metadata and importance hooks on building outputs.
- `tools/buildgen/groups.py`: rebind `cultivation_town`; add optional soft brief.
- Building NBT library shipped as **town parts** under `src/main/resources/data/myvillage/structure/` (existing per-building craft reused).
- New town-level validators / preview affordances (top-down plan view) alongside the existing per-building preview/gallery flow, which stays as-is for single-building QA.
- Docs: `README.md` + `AGENTS.md` add `/myvillage town`; `design.md` records current shortcomings and deferred future needs (pluggable engines, organic streets, hard brief solver, sect↔town relationship).
- No change to `medieval_village`, `chinese_courtyard`, `civic`, or `cultivation_sect` building outputs. MC 1.21.1, NeoForge, vanilla blocks only.
