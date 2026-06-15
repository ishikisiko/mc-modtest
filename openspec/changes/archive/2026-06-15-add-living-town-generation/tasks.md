## 1. Building vocabulary: frontage metadata (Python, no behavior change to existing libraries)

- [x] 1.1 Add `frontage` metadata to town building part outputs in `tools/buildgen/archetypes.py` (street-facing side + shopfront/entry opening cells); default to the largest open side when undeclared
- [x] 1.2 Add an `importance` tier input to town building selection that biases massing height + roof grade; reserve the top tier for the dominant landmark
- [x] 1.3 Regenerate town parts; diff `medieval_village` / `chinese_courtyard` / `cultivation_sect` NBT to confirm byte-stability (no regression)
- [x] 1.4 Extend a validator (`tools/validate_*`) to assert town parts carry frontage metadata and a resolvable front side

## 2. Macro planner (`town-plan`)

- [x] 2.1 Define the town-plan data model: perimeter, gates, spine, parcels (importance tier + ground ref), reserved negative-space regions, optional soft brief
- [x] 2.2 Generate a closed perimeter (城墙) with ≥1 gate (城门), every gate on the perimeter
- [x] 2.3 Generate a main-street spine connecting gates through the core; place exactly one dominant landmark (top tier) anchoring the spine
- [x] 2.4 Assign a center-to-edge importance gradient (non-increasing outward) mapped to height/roof-grade hints
- [x] 2.5 Reserve intentionally-shaped negative space (院落 / 井台) disjoint from parcels and streets
- [x] 2.6 Fit the plan to site bounds, record a per-parcel ground reference, and bias toward an optional soft brief without requiring it
- [x] 2.7 Offline plan dump + top-down plan preview (PNG/HTML) for triage
- [x] 2.8 Plan-level validation: perimeter closed, gates on wall, gradient monotonic, negative space disjoint, plan within site bounds

## 3. Runtime realizer + site-fit (`town-realization`, NeoForge Java)

- [x] 3.1 Add a `town` subsystem package under `src/main/java/com/example/myvillage/` and register the `/myvillage town [seed]` command
- [x] 3.2 Load a plan + the shipped building parts and place buildings, walls, and roads into the live world from loaded chunks; clamp or refuse an oversize footprint with a reported extent
- [x] 3.3 Site-fit: sample the heightmap per parcel and join to ground via plinth/steps/retaining; skip + report parcels above the max-slope limit
- [x] 3.4 Make streets follow slope with steps/ramps; keep the network traversable
- [x] 3.5 Make generation deterministic for a given seed + site
- [x] 3.6 Implement the town-level structural validator: enclosure closed, every parcel reachable from the spine, gates on the wall, no building footprint overlapping a street

## 4. Meso layer (`street-room`)

- [x] 4.1 Resolve frontage against the realized street graph and attach active shopfronts/openings/counters to street-facing parcel edges; flag any frontage facing a wall/void
- [x] 4.2 Enforce continuous main-street frontage (no blank-wall run beyond the configured threshold)
- [x] 4.3 Apply the width + paving hierarchy (青石主街 wider/higher-grade than 夯土巷)
- [x] 4.4 Furnish streets/squares by rank (lanterns/stalls/benches/signboards), crowd the mouth/square, keep the traversable path clear

## 5. Micro layer (`lived-in-tissue`)

- [x] 5.1 Scatter domestic + market props (water well, 晾衣, 柴垛, 菜畦, stalls) into reserved negative space without blocking circulation
- [x] 5.2 Add smoke + human-scale light: chimney 炊烟 (campfire), lit windows, temple 香炉
- [x] 5.3 Apply market-to-lane density falloff and surface wear/imperfection

## 6. Acceptance close-out — verifiable, complete mod

- [x] 6.1 Run the town validator on several seeded towns (incl. a sloped site); all pass, and a deliberately broken town fails with the offending invariant
- [x] 6.2 Confirm the size/perf budget: default town within the bounded footprint/block budget; oversize refused or clamped with reported extent
- [x] 6.3 Generate top-down plan previews, ensure the aggregate `out/preview/index.html`, and serve locally per the AGENTS.md acceptance handoff
- [x] 6.4 `./gradlew build` produces the mod jar; in a flat/repro world `/myvillage town` builds an inspectable living town
- [x] 6.5 Update `README.md` + `AGENTS.md` command lists with `/myvillage town`; bump `CHANGELOG.md` and version in `gradle.properties` + `neoforge.mods.toml` together
- [ ] 6.6 Staged manual acceptance: place a town in-game and inspect 层次 (spine + dominant landmark + gradient), 人烟味 (tissue/smoke/light), 真实市井 (active frontage + furnished street), closed enclosure, and site-fit on a slope
