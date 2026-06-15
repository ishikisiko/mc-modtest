## Context

Today the stack splits as: **Python (offline)** authors block-by-block buildings and tiles them into a `courtyard_street_block` `CompoundGraph` (`tools/buildgen/`), exports NBT, and the **NeoForge mod (thin)** stamps that NBT with `/place template ... ~ ~-1 ~` (one Java file, *"No worldgen is registered."*). The earlier "datapack-first / deterministic-Python / thin-Java" posture was a *prior constraint, not a goal* — the user has explicitly opened the door to a high-level functional rebuild.

The pain is not "which command places the NBT." It is that a stamped town has **no brain and cannot read the ground**: it does not know the slope beneath it, what else is nearby, or how many households it should have. The qualities the user actually wants — **层次 / 人烟味 / 真实市井** — were shown during exploration to live in the **meso/micro connective tissue** (the furnished street-room, the active shopfront, the props in the gaps, the roofscape gradient + dominant landmark), *not* in the macro layout engine. A perfectly WFC-tiled grid with dead gaps and blank frontages still reads as "a pile of houses."

Constraints: MC 1.21.1, NeoForge, vanilla blocks only. The per-building Python craft (`archetypes.py`, `ops.py`, `facade.py`, `massing.py`) is mature and must be preserved. The per-building preview/gallery flow is for single-building QA and is **orthogonal** — this change neither depends on it nor breaks it.

## Goals / Non-Goals

**Goals:**
- Rebuild the town **brain** as a runtime subsystem that plans and realizes a town in the live world, terrain-aware, summoned by `/myvillage town`.
- Make **层次 / 人烟味 / 市井** first-class, produced by dedicated meso (`street-room`) and micro (`lived-in-tissue`) layers over a legible macro skeleton.
- Meet terrain gracefully ("不突嗗") via a bounded `site-fit` layer.
- Preserve the Python **building vocabulary** as an interchangeable parts library; add only **frontage metadata** to it.
- End this change as a **verifiable, complete mod**: a built jar where `/myvillage town` produces an inspectable living town, town-level validation passes, and docs list the command.

**Non-Goals (this change):**
- Pluggable macro engines (WFC / shape grammar / organic agent streets) — the macro layer ships as a single hand-written heuristic planner; engines are added later, one at a time.
- A hard functional-brief constraint **solver** — the brief is soft guidance only.
- Full organic terrain terracing — site-fit is plinth/steps/retaining, not regrading hillsides.
- Sect↔town spatial relationship — `cultivation_sect` is untouched and unplaced by the town system.
- Natural worldgen spawning during chunk generation — `/myvillage town` is the on-demand entry; passive biome spawning is a later add.
- Porting the building vocabulary to Java — buildings stay Python-authored, shipped as NBT parts.

## Decisions

### D1: The brain runs at runtime in the mod, not offline as a mega-NBT
Site-fit, frontage-attachment, and gap-filling all need to know the *actual* ground and the *actual* street, so the **realize** layer must run where the world is known. Once realize is runtime, pulling plan/parcels into runtime is the natural seam. *Alternatives:* (a) one offline mega-NBT — rejected: no terrain, size limits, static; (b) decomposed offline multi-place via generated mcfunction — rejected: removes size limit but still terrain-blind and static; (c) Minecraft jigsaw template pools — viable and terrain-aware, but cedes layout control to emergent pools and makes meso/micro tissue (the actual goal) hard to author. Runtime brain keeps full authorship of the tissue.

### D2: Keep the Python building vocabulary; rebuild only assembly + realization
`archetypes.py` / `ops.py` / `facade.py` / `massing.py` (the roof/facade/motif/massing craft) are **retained** and ship as a parts library (NBT). What is rebuilt is `compound.py`'s flat tiling (assembly) and the degenerate `~ ~-1 ~` stamp (realization), plus the wholly-missing Program/Plan and terrain layers. The boundary is "vocabulary (keep) vs. brain (rebuild)."

### D3: Model the town as a scale-layered functional pipeline; invest in meso/micro
The town is `Site → ①Program → ②Plan → ③Parcels → ④Buildings → ⑤Realize`, with terrain read in ①②③⑤. The named qualities map to layers: **层次→②Plan (skeleton/gradient/landmark)**, **市井→meso street-room**, **人烟味→micro tissue**, **不突兀→⑤ site-fit**. Center of gravity is the meso/micro tissue and the realize layer — that is where "complete/alive" is won, and where the current system is empty.

### D4: Macro = one heuristic planner first; engines are a deferred, pluggable ②-internal choice
The macro planner produces a legible skeleton deterministically by hand-written rules: enclosure (城墙 + 城门), a main-street **spine**, exactly one **dominant landmark** topping the skyline, an **importance/density/height/roof-grade gradient** from center outward, coarse district zoning, and **intentionally-shaped negative space** (a yard here, a well-plaza there). Because legibility lives in spine+landmark+gradient (not in tiling sophistication), a heuristic is enough to ship; WFC / grammar / organic streets are later swaps inside ②.

### D5: Meso — the street is a furnished room, not leftover space
`street-room` gives streets/squares **continuous active frontage** on both sides (shopfronts / counters / 招幌 / awnings, never blank walls), a **width + paving gradient** (青石主街 vs 夯土巷), and **street furniture** (lanterns, stalls, benches, signboards). Main-street mouths crowd (stalls encroach); lanes quiet down.

### D6: Micro — 人烟味 is dressing in designed negative space
`lived-in-tissue` is a scatter pass over the negative space the planner intentionally left. It places **domestic & market props** (water well, 晾衣, 柴垛, 菜畦, stalls, 酱缸, 推车), **smoke & light** (chimney 炊烟 via campfire, lit windows, 香炉), and **wear/imperfection** (moss, patched walls, slight misalignment). Density rule: crowd the market mouth, scatter the back lanes.

### D7: Site-fit is bounded terrain adaptation, not regrading
Per parcel, sample the heightmap, pick a base level, and grow a **plinth / a few steps / a retaining course** so the footprint meets the land without floating or burial; streets follow slope with steps/ramps; above a max slope the parcel is skipped rather than forced. The bar is the user's "不突兀," not organic terracing.

### D8: Buildings expose frontage metadata as the meso/micro attach interface
Each building part declares its **street-facing side** and **shopfront opening** (and an **importance** tier driving massing/roof-grade), so `street-room` knows where to attach awnings/counters and `lived-in-tissue` knows which sides are "front" vs "back." This is the one required change to the otherwise-preserved vocabulary.

### D9: The functional brief is soft
`cultivation_town` may carry counts (housing / market / civic / defense) as **guidance** the planner aims at, never a hard constraint it must satisfy — a town short one shop is fine. Keeps generation robust on awkward sites.

### D10: Verifiability is the `/myvillage town` command + a town validator + the jar
"Done" = the jar builds, `/myvillage town [seed]` generates an inspectable town in-world, a **town-level validator** checks structural invariants (enclosure closed, every parcel reachable from the spine, gates in the wall, frontage continuity, no street/building overlap, no floating/buried parcels), a **top-down plan preview** aids triage, and `README.md` + `AGENTS.md` document the command.

## Risks / Trade-offs

- **Scope: a runtime generative system is a large build** → Stage so the *end of this change* is a verifiable complete mod producing a basic-but-alive town (macro skeleton + meso frontage + micro tissue + site-fit); push all sophistication (engines, organic streets, hard brief, worldgen, sect link) to explicit future work. Each layer lands and is inspectable on its own.
- **Loss of offline determinism for the town** → Accepted: the *town* is now world-dependent by design. Mitigate with a seedable planner (same seed + same site → same town) and a flat-world repro for inspection. The per-building offline pipeline is unaffected.
- **Frontage metadata churn across the vocabulary** → Add metadata as optional with a sane default (largest open side = front); buildings missing it still place, just with weaker frontage logic. Backfill incrementally.
- **Meso/micro passes attaching to the wrong sides on terrain** → Frontage is resolved against the *realized* street graph, not assumed; validator flags frontages that face a wall/void.
- **Java realizer placing into ungenerated/odd chunks** → Command operates only on loaded chunks around the player; refuse or clamp footprints that exceed loaded area; report extent before building.
- **Performance of a large runtime build** → Bound default town size; build in bounded passes; surface a size cap in config. Treat block-count/time budget as an acceptance check.

## Current Shortcomings & Deferred Future Needs

*(Recorded here per request — what this change does NOT yet do, and what later changes will need.)*

- **Single hand-written macro engine.** No WFC / shape-grammar / organic-agent street networks yet — towns will be legible but somewhat regular. *Future:* introduce a pluggable ② engine interface and add organic/winding street generation for 山城/沿河 character.
- **Soft brief only; no solver.** Programmatic requirements are aimed-at, not guaranteed. *Future:* an optional constraint solver for "must have exactly these functions."
- **Bounded site-fit, not true terrain integration.** Steep/awkward sites get skipped parcels rather than terraced platforms or bridges. *Future:* retaining-terrace and stilt/桥 strategies; water's-edge docks.
- **On-demand command only.** No passive worldgen spawning, no structure locating, no spacing/biome rules. *Future:* register as a NeoForge structure for natural discovery.
- **No sect↔town relationship.** The town ignores `cultivation_sect`. *Future:* place a sect on adjacent high ground with a 神道 link (the deferred relationship from earlier exploration).
- **No growth/age model.** Towns are generated whole, not as a dense old core + newer sprawl. *Future:* age-layered generation for organic density falloff.
- **Vocabulary still flat-authored.** Buildings are authored on flat parcels; site-fit reconciles ground externally. *Future:* slope-aware building variants (split-level, stilted).
- **Town-level preview is plan-only.** No offline 3D of a whole town (the per-building 3D viewer does not compose at town scale). *Future:* a town-scale preview/export for review without launching the game.

## Migration Plan

Staged so each step is independently inspectable and the final step yields a verifiable complete mod:

1. **Frontage metadata** on the Python vocabulary (optional + default) — regenerate parts, no behavior change to existing libraries.
2. **Macro planner** (`town-plan`): skeleton + gradient + landmark + zoning + negative space; offline plan dump + top-down preview.
3. **Runtime realizer skeleton** (`town-realization`): place parts + walls + roads against terrain with **site-fit**; `/myvillage town` builds a structurally-valid bare town; town validator green.
4. **Meso `street-room`**: continuous active frontage + street furniture over the realized streets.
5. **Micro `lived-in-tissue`**: prop/smoke/light/wear scatter into negative space.
6. **Acceptance close-out**: town validator + size/perf budget + top-down preview + `./gradlew build` jar + `README.md`/`AGENTS.md` updated with `/myvillage town`.

Rollback: `/myvillage town` is additive; existing `/myvillage place`/`gallery` and all current libraries are untouched, so the feature can be disabled by not shipping the command without affecting prior outputs.

## Open Questions

- **Default town size / shape** — smallest convincing town (one spine, ~2 blocks, 1–2 gates, 1 square, 1 landmark) vs. a larger default. Lean smallest-convincing for first acceptance.
- **Seeded determinism vs. site reactivity** — how much should the same seed reproduce across *different* sites? Propose: seed fixes choices, site fixes fit.
- **Which macro engine comes next** (WFC vs. organic streets) once the heuristic is accepted — driven by whether the desired character is 坊市-regular or 山城-organic.
- **Landmark vocabulary** — reuse an existing tall archetype (e.g. a pagoda/shrine massing) as the dominant, or author a dedicated town landmark part.
