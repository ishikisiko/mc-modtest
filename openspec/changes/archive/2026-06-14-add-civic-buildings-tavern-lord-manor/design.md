## Context

The Western building library has six archetypes (`small_house`,
`medium_house`, `blacksmith`, `small_shop`, `medium_shop`, `big_house`)
emitted by the medieval library loop, plus four Chinese sub-buildings
emitted by the compound loop. The multi-story capability
(`stories` + `story_wall_h` + `_reserve_stairwell` +
`floor_slab_pass` + `stair_pass`) is proven by `big_house` (2–3 stories),
`medium_shop` (2 stories) and `main_hall` (2 stories). The current
`floor_slab` op lays a slab across the entire interior footprint minus
the stair opening. The interior zone op has explicit branches per `kind`
in `ops.py:interior_zone()`. The `forbidden_blocks` list in
`medieval_village.json` blocks modern/nether/material blocks and also
bed/chest/banner/sign/jukebox/beacon.

The civic family introduces two new archetypes (`tavern`, `lord_manor`)
with massing shapes the current model cannot express: a partial-height
floor (tavern mezzanine) and a taller-than-main attached volume
(lord manor tower). Both archetypes also need civic role blocks
(`brewing_stand`, `bell`, `lectern`, `bed`, `banner`, `sign`) that the
current palette does not allow.

## Goals / Non-Goals

**Goals:**

- Introduce `tavern` and `lord_manor` as a procedurally generated civic
  family with the same deterministic-seed quality bar as the existing
  families (validators, gallery export, place functions).
- Add two reusable massing primitives that will generalize to future
  civic pieces: partial-floor mezzanine, and an attached tower that
  rises above the main roof.
- Unblock the four furniture/signage blocks (`bed`, `chest`, `banner`,
  `sign`) for use in civic interiors and facades while keeping the
  medieval palette otherwise intact.
- Keep the change additive: no breaking changes to existing archetypes,
  generated NBTs, or commands.

**Non-Goals:**

- No town-square compound composition in this change. Tavern and
  lord_manor ship as standalone library structures; a future
  `civic-square-compound` change can compose them with shops and a
  plaza using the compound graph.
- No symmetry enforcement. The facade planner and door picker remain
  intentionally asymmetric. The lord_manor's front door MAY be centered
  via metadata, but no per-wing mirrored facade work is in scope.
- No NPC/villager behavior, no loot tables, no block-entity NBT beyond
  the default empty state (empty `chest`, unlit `brewing_stand`, no
  `bell` direction NBT).
- No worldgen, no jigsaw, no template pool. Civics remain debug-placeable
  via `/myvillage place` only, matching v0.4 posture.
- No new style profile. Civic family reuses `medieval_village` with
  narrowed forbidden list and additional slots. A future `medieval_civic`
  profile is possible but not required for v1.

## Decisions

### D1: Civic family as a third library loop

Civic structures are emitted by a dedicated
`generate_civic_library.py` loop, parallel to
`generate_all_structures.py` (medieval) and
`generate_compound_library.py` (Chinese). Variant counts: 5 taverns,
3 lord manors.

**Why not append to the medieval loop?** The medieval library is already
45 structures; adding 8 more dilutes the per-archetype gallery rhythm
and couples civic generation to housing-style generation parameters.
Keeping the loops separate mirrors the existing Chinese compound
separation and keeps each generator file focused.

**Why not gate civics to a compound?** A town-square compound is the
natural end-state, but ships later. Releasing standalone civics first
lets visual review focus on each archetype individually before they
are composed.

### D2: Mezzanine as a partial slab + new pass

Tavern's great hall needs a tall ground story with the inn floor above
covering only part of the footprint. Implementation:

- New volume meta: `mezzanine = {"covers": "west"|"east", "depth": int,
  "y_offset": 0}` on the main volume.
- New pass `mezzanine_floor_pass` inserted between `structure_pass` and
  `floor_slab_pass`. It lays slab blocks over the mezzanine half-plane
  only, and carves a matching half-opening on the upper story so the
  tall great-hall ceiling is exposed.
- `floor_slab_pass` is unchanged for `mezzanine` volumes: it still lays
  the full story boundary slab for the *non-mezzanine* stories. The
  mezzanine story is marked as `mezzanine_story = True` so the regular
  slab pass skips it (the mezzanine pass already placed its floor).
- The stairwell continues to use the standard reserved footprint; the
  mezzanine half-plane is chosen during massing to avoid the stairwell
  column.

**Alternative considered:** extend `floor_slab` to take a coverage
polygon. Rejected: the mezzanine pass then has to coordinate with
`floor_slab_pass`'s story iteration, and the partial-vs-full distinction
is clearer as its own pass.

**Alternative considered:** raise `story_wall_h = [6, 3]` per-story
array for tall-ground/short-upper. Rejected: still produces a full slab
at the boundary, so the great hall is still enclosed at 6 blocks; the
mezzanine overhang is the whole point.

### D3: Tower as new `tower_volume` node

Lord manor's tower is a new attached volume type added to
`VOLUME_TYPES`. Characteristics:

- Footprint smaller than main (e.g., 5×5 or 5×7).
- `stories` higher than the main volume (typically main + 1 or main + 2).
- Attached to one side of the main volume like `side_wing`, with its own
  `gable_roof` (or a flat parapet for a more civic look — deferred to a
  variant meta `parapet: true`).
- Own stairwell reserved via the existing `_reserve_stairwell` mechanic,
  scoped to the tower volume, so the upper tower stories are reachable.
- A `bell` block is placed at the top story, hanging under the tower
  roof, by the interior furnishing pass (zone kind `town_chamber_belfry`
  or simpler: tower meta `belfry = True`).

**Why a new node type rather than a `side_wing` with `stories=3`?**
Three reasons:
1. `side_wing` currently carries the same `story_wall_h` as main, so a
   3-story side wing next to a 2-story main is visually awkward at the
   attachment edge. A dedicated `tower_volume` declares the height
   differential explicitly.
2. Quality checks and validators can target tower nodes specifically
   (e.g., "lord_manor must contain a bell" becomes "every
   `tower_volume` with `belfry=True` must contain a bell").
3. Future keep/watchtower/windmill archetypes can reuse the node.

**Alternative considered:** make main `stories=3` with a narrower
footprint on story 3. Rejected: the current massing model assumes one
footprint per volume for all stories; per-story footprint would require
deeper changes to wall and roof generation.

### D4: Stable annex reuses the shed node

Tavern's stable is a `shed` node (`type="shed"`, `open=True`) with new
meta `stable = True` and a hay floor. The interior zone op reads
`stable=True` and lays `hay_block` floor cells plus a single
`fence_gate` opening. This avoids a new node type while still allowing
the validator to recognize the stable via meta.

### D5: Interior zone kinds extended

New `kind` branches in `ops.py:interior_zone()`:

| Kind | Placement rules |
|------|---|
| `tavern_hall` | 1–2 `brewing_stand` facing the bar wall, 2–4 `barrel` cluster near hearth, 1 `furnace` (hearth), 1 `cauldron`, hanging lantern. The bar counter is a slab+fence run placed by a small dedicated op `tavern_bar_counter`. |
| `tavern_inn` | 1–3 `bed` (head against a wall, foot facing inward), 1 `chest` (empty, no NBT) at the bed foot, 1 `crafting_table`. |
| `town_chamber` | 1 `lectern`, 2+ `bookshelf` along walls, 1 `crafting_table`. |
| `town_foyer` | 1 `bell` (if no belfry tower), 1 `cauldron`, banner on wall via the heraldry slot. |
| `stable` | `hay_block` floor cells, 1 `fence_gate`, optional `water` trough cell. |

The existing `living` / `work` / `storage` / `forge` / `smithy` branches
remain unchanged.

### D6: New material slots

Added to the style profile schema:

- `INTERIOR_CIVIC`: `brewing_stand`, `lectern`, `bell`, `bookshelf`,
  `cauldron`, `flower_pot`.
- `FURNITURE`: `bed` (multiple colors sampled per seed), `chest`.
- `SIGNAGE`: `standing_sign`, `wall_sign` (oak/spruce variants matching
  `DETAIL_WOOD`).
- `HERALDRY`: `standing_banner`, `wall_banner` (color sampled per seed
  from a curated medieval palette: red, blue, black, green, yellow; no
  modern/pink/purple).

The slots are read by the interior and facade passes. Color sampling for
`bed` and `banner` is keyed off the seed so the same seed produces the
same colors.

### D7: Narrowed `forbidden_blocks`

Remove from `medieval_village.json:forbidden_blocks`:
`bed`, `chest`, `banner`, `sign`.

Keep forbidden: `quartz`, `concrete`, `terracotta`, `warped_`,
`crimson_`, `iron_block`, `copper`, `gold_block`, `netherite`,
`spawner`, `command_block`, `shulker`, `jukebox`, `beacon`.

The four unblocked items are placement-safe (no forced NBT required for
default state). Chests and brewing stands are placed empty; signs are
placed without text; banners are placed with no pattern; beds are placed
as the foot+head blockstate pair facing the chosen wall.

### D8: Validator signature-block rules

Extend `validate_generated_structures.py`:

- If the structure name starts with `tavern_`, the NBT palette MUST
  contain `minecraft:brewing_stand` OR at least 3 `minecraft:barrel`
  blockstate entries.
- If the structure name starts with `lord_manor_`, the NBT palette MUST
  contain `minecraft:bell` OR `minecraft:lectern`.
- `lord_manor_` MUST also contain at least one `banner` blockstate
  (heraldry identity).
- `tavern_` MUST contain at least one `bed` blockstate (the inn floor
  premise).

These rules sit alongside the existing blacksmith forge and house
utility rules. Each archetype family now has a recognizable
signature-block gate.

### D9: No facade symmetry; only door centering

Lord manor's front door is centered on the front wall via a new
`door_centered = True` meta on the main volume. `_door` reads this and
picks the geometric center door x (with the existing two-cells-from-
corner rule preserved by widening the door bay if needed). The facade
planner otherwise remains asymmetric: window bands, posts, and awnings
keep their current jittered behavior. No facade refactor.

**Why not full symmetry?** It would require a new facade planner mode,
new tests, and a separate spec delta for facade planning. The visual
gain is bounded (the tower + banner already carry the civic identity).
Keep the option open for a future `symmetrical-facades` change.

### D10: Bump artifact version to 0.5.0

`gradle.properties` and `build.gradle` version bumps from `0.4.1` to
`0.5.0`. Minor bump reflects new content; no breaking API or command
changes.

## Risks / Trade-offs

- **[Mezzanine slab vs. roof interaction]** The mezzanine half-slab
  lives at the boundary between story 1 and story 2, but only on one
  half-plane. Roof generation may try to fill above it. → Mitigation:
  mezzanine slabs are tagged `STRUCTURE` + `PROTECTED`, matching the
  existing floor_slab tagging, so roof and facade passes already treat
  them as non-overwritable.

- **[Tower volume attachment seam]** A 3-story tower attached to a
  2-story main leaves a visible seam where the tower's lower story
  meets the main's roof eave. → Mitigation: tower's lower story uses
  the main wall material; the tower's own roof is generated with an
  overhang that visually ties back to the main roof. Accept some seam
  in v1; revisit if visual review flags it.

- **[Bed placement under tall windows]** Beds are 1.5 blocks tall and
  need head-wall clearance. If the facade planner places a window
  exactly where a bed head would go, the bed placement must defer.
  → Mitigation: the `tavern_inn` zone op iterates along walls and
  skips windows just like the existing interior zone `spots_along_walls`
  helper does; bed is placed only on solid-wall intervals.

- **[Validator strictness could break generation]** Requiring a `bed`
  in every tavern means the inn zone op must reliably place one even
  on small footprints. → Mitigation: tavern variants always have at
  least 2 inn rooms by design; if a seed yields zero placement spots,
  the generator raises rather than silently shipping an invalid NBT
  (matches current `min_volumes` assertion pattern).

- **[Forbidden-blocks narrowing affects all archetypes]** Removing
  `bed`/`chest`/`banner`/`sign` from forbidden applies to the whole
  `medieval_village` style, not just civics. The existing generators
  do not currently emit these blocks, so existing output is unchanged.
  → Mitigation: the change is additive; existing archetype builders
  do not opt into the new slots automatically. Only the civic builders
  reference the new slots.

- **[Banner color sampling could clash with style]** A randomly
  sampled red banner on a brown-timber building can read as "off".
  → Mitigation: banner palette is curated to medieval heraldic colors
  and matches `ROOF_DARK` (spruce) tones; saturation stays low. Visual
  review at the gallery step is the final gate.

## Migration Plan

No migration: change is additive. Existing structure NBTs are
regenerated unchanged. Existing worlds with placed v0.4 structures are
unaffected. Users regenerate via the documented commands.

Rollback: revert the change; v0.4.1 artifacts remain valid because no
existing archetype, style, or validator rule is mutated in a breaking
way (only narrowed forbidden list and added slots, both additive).

## Open Questions

- Should `lord_manor` variants include one with a `parapet=True` flat
  tower roof (more civic) vs. gabled tower roof (more residential)?
  Default: ship all three variants with gabled tower roofs; revisit
  if a civic feel is missing at review.
- Should the civic gallery column sort by archetype (`tavern` before
  `lord_manor`) or by visual size? Default: by archetype, matching the
  existing Chinese courtyard column ordering.
- Should `/myvillage gallery` get a second row, or extend the existing
  row with a civic column? Default: extend the existing row; revisit
  spacing if total column count exceeds 8.
