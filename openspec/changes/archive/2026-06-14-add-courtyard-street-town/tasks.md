## 1. Small-courtyard unit

- [x] 1.1 Add a `town_small` entry to `COURTYARD_SIZE` (compact lot) in `tools/buildgen/compound.py`
- [x] 1.2 Implement `generate_small_courtyard(seed, style, roster, size)` returning a `CompoundGraph` that places 2–4 roster buildings around one small 天井, reusing `_add_perimeter`, `_place_landscape`, `_route_circulation`, and `_translate_context`
- [x] 1.3 Ensure the small courtyard's `perimeter_wall` has exactly one gate and that no building footprint overlaps 天井 or wall cells
- [x] 1.4 Add a unit-level validation/check that the small courtyard's lot footprint is smaller than the one-진 `chinese_courtyard` layout

## 2. Street-tiling town block

- [x] 2.1 Implement `generate_town_block(seed)` returning a top-level `CompoundGraph` with layout strategy `courtyard_street_block`
- [x] 2.2 Lay courtyards on a row/column grid; translate and merge each courtyard's `BlockGrid`, `parcel_nodes`, and `building_slots` into the block graph
- [x] 2.3 Add `street` and `lane` parcel node types; place street/lane cells between rows and confirm no building/wall/landscape cell overlaps them
- [x] 2.4 Implement party-wall de-duplication: shared lot lines collapse to one wall thickness; outer block edge stays a continuous wall
- [x] 2.5 Orient each courtyard's gate toward the nearest street and verify all gates are reachable from the street network
- [x] 2.6 Add town-block variant axes (rows × courtyards-per-row, street width, lane presence, corner-frontage) and a `select_*` helper combining them per seed

## 3. Group binding

- [x] 3.1 Change `cultivation_town` `layout_strategy` to `courtyard_street_block` in `tools/buildgen/groups.py` (roster unchanged)
- [x] 3.2 Route the `courtyard_street_block` strategy to `generate_town_block` wherever layout strategies are dispatched (export/library generation)

## 4. Library, validation, export

- [x] 4.1 Generate the `cultivation_town` town-block library (target ~6 distinct blocks) via the library generator
- [x] 4.2 Extend the compound validator(s) for the new strategy: courtyard non-overlap, party-wall thickness, street/lane clearance and traversability
- [x] 4.3 Export `cultivation_town_001.nbt` onward to `src/main/resources/data/myvillage/structure/` and pack into the v0.5 mod jar
- [x] 4.4 Add `/myvillage place cultivation_town_001` and `/myvillage gallery cultivation_town` to the command documentation

## 5. Regression & acceptance

- [x] 5.1 Regenerate `medieval_village`, `chinese_courtyard`, and `cultivation_sect` libraries and diff NBT to confirm byte-stability
- [x] 5.2 Run the full buildgen validation/report suite and confirm no new failures
- [x] 5.3 Stage at least one town block for in-game visual acceptance (continuous frontage, shared party walls, street/lane traversability, gate orientation)
