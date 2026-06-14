## Context

`tools/buildgen/compound.py` already provides a `CompoundGraph` parcel layer with two layout strategies: the one-진 `chinese_courtyard` (`generate_compound`) and the monumental `sect_terraced_axial_compound` (`generate_sect_compound`). Both own parcel nodes (perimeter wall, water, planting, corridors, path) and building slots, where each slot is realized by the per-building pass pipeline via `generate_subbuilding`.

`cultivation_town` (`tools/buildgen/groups.py`) currently uses `layout_strategy="standalone_library"` with a flat roster (`cultivation_house`, `cultivation_shop`, `cultivation_inn`, `cultivation_market`, `town_shrine`). Each building is an independent NBT placed by the game's village logic — no shared frontage, no lanes. The deferred 模型B (recorded in the archived `add-cultivation-style-system` design and the project memory) calls for a **小合院拼街 坊市**: small walled courtyards tiled wall-to-wall along streets.

Constraints: MC 1.21.1, vanilla blocks only. Existing `medieval_village`, `chinese_courtyard`, and `cultivation_sect` outputs must stay byte-stable.

## Goals / Non-Goals

**Goals:**
- A reusable **small-courtyard unit**: a scaled-down walled courtyard (a few `cultivation_town`-roster buildings around a tiny 天井 + 院墙), built on the existing `CompoundGraph` machinery.
- A **street-tiling layout strategy** that arranges multiple small courtyards into a town block with continuous 院墙 street frontage and traversable 巷道 between rows.
- Rebind the `cultivation_town` group to the new layout and ship a generated + validated + exported town-block library with `/myvillage` docs.
- No change to existing medieval / courtyard / sect outputs.

**Non-Goals:**
- New roof/motif **forms** — the town reuses existing forms and the `cultivation_town` palette; this is layout work only.
- A whole-town generator. The export unit is a **street block** (a tiled cluster), not an entire settlement; the game still places multiple blocks.
- Procedural road-network / terrain adaptation beyond a flat parcel — courtyards tile on a flat lot, matching how the courtyard/sect libraries already export.
- 灵材 / spirit materials in town (stays sect-exclusive per the prior change).

## Decisions

### D1: Small courtyard = scaled-down `CompoundGraph`, not a new graph type
Add a `generate_small_courtyard(seed, style, roster, size)` that produces a `CompoundGraph` reusing `ParcelNode` / `BuildingSlot` / perimeter / landscape / circulation helpers, but with a compact lot, a reduced building count (2–4 town-roster buildings around one small 天井), and a single 院墙 with one street-facing gate. Rationale: the courtyard primitives (`_add_perimeter`, `_place_landscape`, `_route_circulation`, `_translate_context`) are exactly what a small courtyard needs; a parallel graph type would duplicate them. Add a `courtyard_size` entry (e.g. `town_small`) to `COURTYARD_SIZE` rather than a new dataclass. *Alternative — generate each courtyard as a bare building cluster without `CompoundGraph`:* rejected; loses the 院墙/landscape/circulation guarantees that define the unit.

### D2: Street tiling is a town-scale `CompoundGraph` whose slots are courtyards
Introduce `generate_town_block(seed)` producing a top-level `CompoundGraph` (layout strategy `courtyard_street_block`) whose parcel nodes include **street** and **lane** cells, and whose building footprints are the *merged* footprints of tiled small courtyards. Each small courtyard is generated, then translated (like `_translate_context` does for slots) so its 院墙 abuts the street edge and neighboring courtyards share wall lines. Rationale: keeping one flattened `CompoundGraph` (rather than nesting graphs) means export, validation, and `to_dict` work unchanged — the existing exporter already consumes a single graph + grid. The block grid is composed by translating and merging each courtyard's `BlockGrid`. *Alternative — a new `TownBlockGraph` nesting child `CompoundGraph`s:* rejected as more invasive to export/validate for no layout benefit.

### D3: Frontage adjacency is grid-driven, with a shared-wall rule
Lay courtyards on a row/column grid sized in courtyard-units; place each courtyard so its perimeter wall sits on the shared lot line, then **de-duplicate the doubled wall** into a single party wall between neighbors (continuous frontage) while keeping the outer block edge a full wall. Streets run between rows; lanes (narrower) may split long rows. Gates face the nearest street. Rationale: a continuous frontage is the whole point of 坊市; explicit party-wall merging is what visually distinguishes this from "houses with gaps." Reuse the existing non-overlap discipline: building/landscape cells must not overlap street/lane cells (mirrors the courtyard water/path rules).

### D4: Rebind the group; roster stays, layout changes
Change `cultivation_town`'s `layout_strategy` from `standalone_library` to `courtyard_street_block` in `groups.py`. The roster (`cultivation_house`/`shop`/`inn`/`market`/`town_shrine`) is unchanged — courtyards draw from it, mixing housing + functional + a civic `town_shrine` as a focal courtyard. Rationale: the group descriptor is the documented hook for exactly this layout swap; no archetype/palette change needed. *Alternative — keep standalone and add a separate street group:* rejected; the project decision is that the town *is* the 坊市, not a second variant.

### D5: Variant axes for town blocks, combinatorial like courtyards
Town-block variation comes from independent axes combined per seed: block shape (row count × courtyard count), street width, lane presence, corner-shop frontage, and per-courtyard variant reuse of the existing courtyard axes. Default library emits a small set (target ~6, matching the courtyard library convention) differing in ≥1 axis. Rationale: consistent with the established `select_variant` / six-instance library pattern, so validation and gallery tooling extend naturally.

## Risks / Trade-offs

- **Footprint/grid overlap when merging translated courtyards** → Reuse and extend the existing non-overlap assertions; add validation that no two courtyards' building cells overlap and that streets/lanes stay clear. Land the small-courtyard unit first (its own commit) so a tiling bug is bisectable from the unit.
- **Party-wall de-dup leaving gaps or doubled walls** → Add a validation scenario: shared edges between adjacent courtyards are exactly one wall thickness; outer block edge is continuous. Visual acceptance in-game per the existing courtyard acceptance pattern.
- **Town block exceeding sane structure-placement size** → Cap default block dimensions in `scale_params`; keep the export unit a *block*, not a town (D2 Non-Goal).
- **Regression in existing libraries** → Touch only `cultivation_town` group binding + new functions; regenerate medieval/courtyard/sect and diff NBT for byte-stability before/after.
- **Roster too sparse for varied courtyards** → 5 town archetypes already exist; mix and repeat with variant axes; no new forms required (D1 Non-Goal upheld).

## Migration Plan

1. Add small-courtyard unit (`generate_small_courtyard` + `town_small` size) — own commit, with unit validation.
2. Add `generate_town_block` + `courtyard_street_block` strategy and party-wall merge — own commit.
3. Rebind `cultivation_town` group; wire library generation, validation, export, and `/myvillage` docs.
4. Regenerate and diff medieval/courtyard/sect libraries to confirm no regression.

Rollback: revert the group binding to `standalone_library` (the standalone roster and library code remain intact), and drop the new town-block NBT.

## Open Questions

- **Block topology default**: single-row strip vs. 2-row street (courtyards facing each other across a street) for the default library — leaning 2-row for a truer 坊市 read; confirm during step 2 from in-game inspection.
- **Standalone library retirement**: keep the standalone town library generatable as a fallback, or remove it once the block library is accepted? Defer to after visual acceptance.
