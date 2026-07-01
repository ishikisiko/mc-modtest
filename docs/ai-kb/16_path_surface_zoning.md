# Path Surface Zoning

Implemented in 0.18.0 by change `path-surface-zoning`, with the final
gallery/layout and waterside visual repairs documented in 0.18.1.

The courtyard/mansion ground + path layer is a **two-axis surface-zoning
model**. Before this change the whole path was one flat `GROUND_PATH` gravel
stripe: the formal axis, the garden approach, and the waterside step all read
the same, and the tour route could not "wind" because a single-source shortest
path is straight by definition.

## Two orthogonal axes

- **Axis 1 — material is owned by the zone (a space property).** Each ground
  /path cell is classified into one of six zones, and the zone looks up a style
  slot. The three routes do **not** carry material.
- **Axis 2 — shape is owned by the route (a geometry property).** Formal and
  service routes are straight (single-source BFS); the tour route winds (a
  waypoint polyline). Material does not participate.

Dissolving material from the route removes the unresolvable conflict the old
framing ("a slot per route") hit: is the formal-axis cell crossing a 天井 made
of 青石 (formal) or 灰砖 (it is a 天井)? Zone-owned, the answer is 灰砖 because
the cell is a 天井; the formal route only decided to pass through it.

## The six surface zones

| surface | zone | layer | slot | block (full) |
|---|---|---|---|---|
| 规整青石路 | 中轴通途 | path | `PATH_FORMAL` | `smooth_stone` |
| 天井灰砖铺地 | 天井/院心 | ground | `GROUND_YARD_HEART` | `stone_bricks` |
| 廊下木石地面 | 廊道 | ground | `PATH_GALLERY` | `oak_planks` |
| 白墙夹道 | 夹道 | ground | `PATH_ALLEY` | `stone_bricks` |
| 苔石曲径 | 游园 | path | `PATH_TOUR` | `mossy_stone_bricks` |
| 水边石阶与小桥 | 水岸 | path | `PATH_WATERSIDE` | `stone_brick_stairs` + slab bridge |

Each family realizes only the zones it has space for (per design D9):

| zone \ family | mansion | courtyard (1-进) | small_courtyard |
|---|---|---|---|
| 中轴通途 (PATH_FORMAL) | ✅ | ✅ | ✅ |
| 天井/院心 (HEART) | ✅ | ✅ | ✅ (has parcel) |
| 廊道 (GALLERY) | ✅ | ✅ | ❌ |
| 夹道 (ALLEY) | ✅ | ⚠️ (if 倒座) | ❌ |
| 游园 (TOUR) | ✅ | ❌ | ❌ |
| 水岸 (WATERSIDE) | ✅ | ❌ | ❌ |

`chinese_mansion.json` carries all six slots; `chinese_courtyard.json` carries
formal + heart + gallery + alley; `cultivation_town.json` / `cultivation_sect.json`
carry none. `_ground_zone_slot` **falls back to `GROUND_YARD_UNDER_EAVE` when a
style lacks the finer slot**, so a style that did not adopt the surface-zoning
slots regenerates byte-identical to the pre-zoning behaviour (the byte-stability
guard keeps `cultivation_sect_*` and `medieval_*` unchanged).

## Three routes

- **Formal axis** (`_route_complete_path`) — a single-source shortest-path tree
  from the street-gate entry, each door/water/planting/moon-platform endpoint
  traced back to the gate. The backbone resolves through `PATH_FORMAL`. The
  predecessor tree MUST be single-source; a multi-source tree degenerates into
  disconnected endpoint clumps that never cross the plinth (see `0.16.0-fix2`).
  `_place_band_transition_stairs` writes one `stone_brick_stairs` at each
  plinth-boundary pair on the backbone (only when `plinth_h >= 2`; Δy=1 is a
  free Minecraft autostep).
- **Tour route** (`_route_tour_path`) — a winding polyline 假山南 → nearest pond
  shore → 亭, each segment a single-source shortest-path tree with an obstacle
  set forcing any segment that would cut through the rockery/pond to route
  around it. Resolves through `PATH_TOUR`. The single-source-tree contract is
  preserved *per segment*.
- **Service route** — formal backbone reaches the `service_house` (仆役房) door
  through the 夹道; its `door_info["front"]` is a mandatory path endpoint.

## The 月洞门 material boundary

The `moon_gate_passage` parcel is a voxel-walkable 穿墙通道 through the garden
screen wall (the `moon_gate` motif is applied to the surrounding wall cells).
It is the material boundary: cells before it are formal (青石), cells after are
tour (苔石). The tour's first waypoint is *inside* the 花园, on the 花园 side of
the passage, so the formal/tour cell intersection is empty by construction.

## Waterside crossing

`PATH_WATERSIDE` writes `stone_brick_stairs` descending to the waterline, then a
**slab bridge** (`oak_slab`/`spruce_slab` at the water surface y) spanning the
pond's narrowest crossing to the 亭/island. A slab is a flat, walkable,
water-surface block — it reads as a plank bridge. The deleted 汀步
`rockery_block` spike-row ("一列小尖刺") is **not** restored; the spike problem
was the block choice, not the crossing geometry. Lily pads are intentionally
sparse and cleared from the bridge/gallery clear-water lanes.

## 3D galleries and mansion layout repair

The mansion 水边廊 and 主院 抄手游廊 are real 3D galleries, not floor-only
surface cells. `_place_covered_gallery_3d()` writes a `PATH_GALLERY` floor,
2-high `COLUMN` posts, a `BALUSTRADE` fence row on the open side, and a
single-slope `ROOF_DARK` roof. The 水边廊 keeps the `covered_gallery` parcel type
but is **one short straight two-cell-deep run** on a clean pond bank, not every
freeform shoreline cell. It must not overlap pond water, the island rockery, or
the waterside bridge; the 主院 galleries tie the inner-gate flanks to the
open-hall flanks when there is enough clear width.

The mansion back-yard bug was fixed in the same final pass. `generate_mansion`
uses `_mansion_yard_depths()` to split 后院 and 花园 into separate z-bands, moves
the garden screen wall/月洞门 to `garden_band[0]`, constrains `tower_house`
(绣楼/藏书楼) to the 后院, and shrinks the 主院 platform to the building/gallery
footprints so the 主院 heart falls through to `GROUND_YARD_OPEN` grass.

## Validation

`validate_mansion` adds three checks on top of the voxel-walkability suite:

- `surface_zone_material:<zone>:<cell>` — each ground/path cell's block matches
  its zone's slot primary (the path overlays `PATH_FORMAL` / `PATH_TOUR` /
  `PATH_WATERSIDE` / `GROUND_PATH` are allowed to sit on any ground zone).
- `tour_segment_disconnected:<from>-><to>` — every tour waypoint segment is a
  connected single-source tree; no-op for compounds without a garden.
- `waterside_bridge_incomplete:<first|last>` — the slab bridge spans both
  shores / 亭-island.
- `waterside_gallery_clutter:*` and `pond_lily_clutter:<cell>` — the 水边廊 is
  one short straight strip, does not overlap water/rockery/bridge, and the
  bridge/gallery clear-water lanes stay free of lily pads.
- `back_yard_garden_overlap` and `tower_overlaps_garden` — the 后院/花园 bands
  stay separated and no `tower_house` footprint overlaps 花园 feature cells.

## See also

- Specs: [`path-surface-zoning`](../../openspec/specs/path-surface-zoning/spec.md),
  [`courtyard-ground-layer`](../../openspec/specs/courtyard-ground-layer/spec.md),
  [`courtyard-path-network`](../../openspec/specs/courtyard-path-network/spec.md),
  [`chinese-mansion-compound`](../../openspec/specs/chinese-mansion-compound/spec.md).
- Index: [Knowledge Base Map](INDEX.md).
