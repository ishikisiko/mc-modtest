# Deferred Roadmap (Index of Designed-But-Unrealized Work)

Status: **living index** — updated whenever a deferred item is realized (mark ✅
with the realizing change) or a new deferral is added by an accepted proposal.
This note exists so the project's "written but not yet built" work is
discoverable in one place, rather than scattered across the 28 folders under
`openspec/changes/archive/`.

Each entry cites the **source** (proposal / design / tasks of an archived
change) and the **current state** verified against shipped code/resources. The
normative contract for any realized behavior remains in `openspec/specs/`; this
note does not change specs — it indexes the gaps between them and the code.

Scope rule: an item belongs here only if (a) a proposal/design/tasks artifact
explicitly named it as in-scope-of-the-project but deferred, **and** (b) it is
not realized in `src/main/` or `src/main/resources/` today. Confirmed
Non-Goals (deliberately rejected alternatives) live in §H and are kept for
reference, not as work items.

See-also (per section) — this note cross-links the specs each deferred item
would extend if picked up.

## At-a-glance map

```
                  wrote API hook / data field           wrote only design/vision
                      
   Region    ─────────────────►  🔴 no consumer    ◄── §A  runtime-binding
   Region    ─────────────────►  🔴 no terrain     ◄── §B  region-topology
   Town      ─────────────────►  🔴 no worldgen    ◄── §C  living-town-generation
   Subjects  ◄────────────────  🟡 admitted_subj   ◄── §A2 (sects ignore regions)
   Cult-form ─────────────────►  🔴 魔修 flavor     ◄── §D  cultivation-style-system
   Courtyard ─────────────────►  🔴 二进/三进       ◄── §E  chinese-courtyard-compound
```

Three "half-finished nets" carry the most leverage if picked up: §A (region
consumers), §B (region terrain), §C (town worldgen). §A2 is the smallest
bridge between §A and §B/C and probably the highest-value next change.

---

## A. Region layer downstream consumers (API live, no caller)

**Source:** `2026-06-21-add-region-runtime-binding/proposal.md` "What Changes"
+ `design.md` "Non-Goals (deferred to future changes)".

The runtime-binding change landed the graph in-world (`中州` at origin,
~4000-block radius), bound spawn to the lowest-tier eligible region, and
exposed `region_at` / `current_rung` / `next_rung_regions` via
`RegionRuntimeService` + `RegionQueries`. **No downstream system reads them
yet.** `grep -r "DeferredRegister.Items" src/main/java/` returns nothing.

| Deferred item | Source quote | Current state |
|---|---|---|
| 罗盘 / 灵脉罗盘 / map / "next region" indicator | "Compass / map / 'next region' indicator form is explicitly deferred" | No item registered; vanilla compass still points to world spawn (now at periphery, so it no longer points at `中州` — see runtime-binding design "Compass semantics drift") |
| 正道/魔道 alignment system | design: "A future 正道/魔道 alignment system resolves which member of the set is 'the' next destination" | `next_rung_regions` **intentionally** returns a set at tier ties (灵岳 + 西漠 both = 18) waiting for this system to resolve; downstream UI has no tie-breaker |
| Mobility / fast-travel / flight gating (缩地 / 御剑 / 跨洲 teleport) | "Mobility / fast-travel / flight gating is explicitly deferred" | `current_rung` exposed; nothing gates movement |
| 魔域 (walled regions) reachability via 魔道 path | design: "Reaching 魔域 (via 关隘) is a future alignment-system concern" | `next_rung_regions` excludes `walled`; no path in the API |
| Region extents (precise coord→region) | design Open Risk: "extents deferred to the terrain-realization change" | `region_at` uses nearest-center as a known approximation |

See-also specs: `region-runtime-binding`.

## A2. Subjects are not placed by region (the bridge gap)

**Source:** `2026-06-21-add-region-topology/proposal.md` "Enables (future,
deferred): runtime placement of subjects into regions" + the
`admitted_subjects` field defined by `region-profile`.

`region_profile/*.json` declares `admitted_subjects` per region, and the field
round-trips through `RegionProfile` → `GenRegion` → graph JSON. But it is
**only consumed by `RegionSpawnSelector` to filter spawn candidates**. The sect
worldgen itself ignores it:

```
src/main/resources/data/myvillage/worldgen/structure/sect.json:
  "biomes": "#myvillage:has_sect"      ← biome-only gate, no region check
```

So today "which 洲 admits a sect" is data on the graph but does not influence
where sects generate. Closing this loop is the smallest single change that
makes the region layer *do something visible* in generated worlds.

See-also specs: `region-profile`, `sect-worldgen-structure`.

## B. 隔 edges have no terrain realization

**Source:** `2026-06-21-add-region-topology/design.md` D5 + proposal
"Enables (future, deferred)".

The topology generator emits typed edges (`连` passable / `隔` separated) with
a separator palette `{特殊山脉, 特殊海洋}`. Today these are **data only** —
no chunk-gen, no biome override, no built ridge or water body realizes them.
The proposal explicitly named this as the natural follow-on to the sect
`反推山形` (extending single-peak derivation to a ridge range):

| Edge data | Intended realization | Status |
|---|---|---|
| `隔=山脉` | real ridge terrain between two regions | unrealized |
| `隔=海洋` | real water separating regions | unrealized |
| `连=官道/关隘/渡口` | connector buildables on passable edges | unrealized (D5 named these as connector buildables, type only for now) |

See-also specs: `region-topology`, `sect-mountain-derivation` (the existing
single-peak derivation that a ridge-range change would extend).

## C. Town worldgen + macro-engine modularity + sect↔town link

**Source:** `2026-06-15-add-living-town-generation/design.md` "Current
Shortcomings & Deferred Future Needs" + `2026-06-17-rebuild-cultivation-town-districts/design.md`
("City-scale (256+) … explicitly deferred").

Five interlocking deferrals from the original town-generation change, plus one
from the districts rebuild. None have been touched.

| Deferred item | Source | Current state |
|---|---|---|
| Pluggable macro engines (WFC / shape grammar / organic streets) | living-town D4 + Shortcomings §1 | Only the hand-written heuristic planner exists; no engine interface extracted (`grep` for `wfc\|shape_grammar` in `tools/buildgen/` returns nothing) |
| Hard brief solver ("must have exactly these functions") | living-town Shortcomings §2 | Only `soft_functional_brief` guidance |
| True terrain integration (retaining-terrace / 柱/桥 / 水边 docks) | living-town Shortcomings §3 | Bounded site-fit only; steep parcels are skipped, not terraced |
| **Town as worldgen Structure** (passive spawn, `/locate` discovery) | living-town Shortcomings §4 | `worldgen/structure/` contains only `sect.json`; towns remain command-only via `/myvillage town` |
| **Sect ↔ Town relationship** (神道 link, sect on adjacent high ground) | living-town Shortcomings §5 | Sect and town generators do not reference each other |
| City-scale (256+) cross-tick / cross-chunk streaming | districts rebuild design "Non-Goal" | Hard cap remains `MAX_FOOTPRINT_AXIS = 160` |

See-also specs: `town-plan`, `town-realization`, `settlement-group`.

## D. Cultivation vocabulary residuals

**Source:** `2026-06-14-add-cultivation-style-system/proposal.md` "Out of
scope (deferred)" + `2026-06-15-add-external-mod-decor/design.md`.

| Deferred item | Source quote | Current state |
|---|---|---|
| 仙宫 vs 魔修 sub-flavor split | "final sub-flavor split (仙宫 bright vs 魔修 dark — default to 仙宫)" | Only `cultivation_sect` 仙宫 profile exists; no 魔修 profile authored |
| Spirit materials bleeding into town (小道观 / 坊市 法器铺) | "whether spirit materials may bleed into town — left as an open question" | `cultivation_town` `forbidden_blocks` still excludes 灵材 |
| Real `fetzisasiandeco` curved roof + paper lantern family | `add-external-mod-decor` design: "swapping in real `fetzisasiandeco` roof/lantern families is deferred until those assets are staged" | Mod is still not in `exmod/`; eave curve is generated geometry, lanterns use `supplementaries` / `ars_nouveau` substitutes |

See-also specs: `cultivation-form-vocabulary`, `style-profile`,
`mod-decor-motif`.

## E. Courtyard-form expansions

**Source:** `2026-06-14-add-chinese-courtyard-compound/proposal.md` "Out of
scope" + exploration captured during the `rebuild-chinese-courtyard` change
(2026-06).

The original courtyard-compound change shipped only a single-yard "one-courtyard"
grammar. The follow-up work splits into two arcs:

### E.1 — One-进 rebuild (realized)

**Status:** ✅ realized by change `rebuild-chinese-courtyard`. Covers: real
硬山/悬山/歇山/卷棚 roof forms; 台基 + 檐廊
massing grammar fed back from `cultivation-massing-grammar`; outer-yard /
main-yard split with 影壁 + 垂花门 + 抄手游廊 + 月台; new plan-level variant
axes (`layout_type` / `main_orientation` / `main_bays` / `platform_tier` /
`gate_type`). See the change's proposal + design for full scope.

### E.2 — Multi-进 compounds (still deferred)

**Source:** original proposal + `rebuild-chinese-courtyard` exploration.

The `rebuild-chinese-courtyard` design intentionally scopes the `CompoundGraph`
z-band split (`outer_yard_band` / `main_yard_band`) so a follow-up can extend it
to `jin_count ∈ {1, 2, 3}` via `(jin_count, lot_d) → list of z-bands` without
redesigning the data model. Sketches captured during that exploration:

```
一进 (E.1)        二进 (deferred)               三进 (deferred)
                                                                
外院 + 主院       前院 + 主院 + 后院            前院 + 主院 + 后院 + 花园
1 垂花门          1 垂花门 + 后门               2 垂花门 + 后门
倒座 / 厢 / 正    + 后罩房 (女眷/储物)           + 假山/亭/曲折游廊/水
lot ~35×47        lot ~50×75                    lot ~60×100
```

| Deferred item | Current state |
|---|---|
| `jin_count` master axis abstraction | Not implemented; `generate_compound` is hard-coded to one outer + one main yard (E.1 baseline) |
| 二进 compound (前院 + 主院 + 后院 + 后罩房) | Not implemented; the follow-up adds a `back_chamber` parcel node to the `jin_count` z-band sequence |
| 三进 compound + 花园 (打破正交轴) | Not implemented; first non-rect `CompoundGraph` region — needs `garden_rockery` / `garden_pavilion` parcel nodes and a free-curve pond |
| 假山 (3D blob rockery) as a first-class capability | Not implemented; may warrant a new `garden-rockery` spec rather than a parcel-node add |
| Side 跨院 paths | Not implemented (original proposal's deferral) |
| NPC systems | Not implemented; `README.md` still lists "possible NPC/villager-related behavior once runtime and data support exist" under Future direction |
| Cross-family: small-courtyard town unit inherits from the rebuilt 一进 | Not decided; the small-courtyard may stay simplified on purpose (街面建筑 less formal than a 府邸) |

See-also specs: `courtyard-compound`, `chinese-vernacular-roof-vocabulary`
(after E.1 archives).

## F. Tooling / visual small deferrals

Low-leverage but tracked. Each came from a design doc's "Open Questions" or
"follow-up if needed" note.

| Item | Source | Note |
|---|---|---|
| `viewer.html` greedy meshing / base64-packed payload | `add-interactive-3d-preview/design.md` | Largest compound viewers are multi-MB inline JSON; acceptable today, optimization held in reserve |
| Hanging-plaque wind animation (招幌 / 酒幌 swing) | `add-custom-plaque-blocks/design.md` | v1 is static; blockstate space reserved for a future `wind_phase` |
| True volumetric cloud-sea / biome fog | `add-sect-worldgen/proposal.md` | Current cloud-sea is a manual glass + powder-snow illusion; volumetric fog was out of scope |
| Per-world tunable region scale (gamerule / serverconfig) | `add-region-runtime-binding/design.md` | Scale is a single Java constant today; promoting to data deferred until multiplayer tuning demands it |
| `civic_core` north/back precinct wall | `add-civic-precinct/design.md` Open Questions | South + lateral walls shipped; north edge behind the shrine left optional |
| Wharf (码头) / 演武场 fringe sub-districts | `rebuild-cultivation-town-districts/design.md` Open Questions | "needs water siting — deferred to follow-up"; `fringe` today is `spirit_field` / `药圃` only |

See-also specs: `interactive-preview`, `plaque-block-family`,
`sect-mountain-derivation`, `region-runtime-binding`, `civic-precinct-framing`,
`town-districts`.

## G. Unchecked staged-acceptance tasks

These are not vision-level gaps — code is written, only the **manual in-game
acceptance step** was never signed off. Listed for completeness; closing them
is a review pass, not a code change.

| Source change | Task | What it asks |
|---|---|---|
| `add-living-town-generation` | 6.6 | Place a town in-game and inspect 层次 / 人烟味 / 真实市井 / enclosure / site-fit on a slope |
| `add-mod-runtime-fallback` | 4.6 | Run `/myvillage town` and `/place` with mods installed and uninstalled; confirm fallback |
| `add-mod-runtime-fallback` | 5.3 | Mark Phase 5 / Phase 6 done in `docs/external_mod_integration_plan.md` |
| `densify-cultivation-town` | 1.4 / 4.3 / 5.3 | In-world verification of floor-flush, courtyard enclosure, spine traversability |
| `fix-sect-worldgen-chunk-stall` | 4.2 / 5.2 | Force-generate vs worldgen same-seed compare; release-build smoke test |

## H. Confirmed Non-Goals (deliberate, not deferred)

Recorded so they are not re-litigated. Picking one up requires a new proposal
that explicitly supersedes the Non-Goal.

| Non-Goal | Source | Reason it was rejected |
|---|---|---|
| Asymmetric west/east district rosters | `town-shape-irregularity/design.md` | "deliberately deferred — complicates the skyline-relief rule" |
| Slanted or curved street grids (KB note 11 lever B2) | `town-shape-vocabulary/design.md` | "high-risk low-yield, deferred again" |
| Jigsaw / template-pool generation for sects | `add-sect-worldgen/design.md` D1 | Custom `Structure` chosen instead; jigsaw rejected |
| Runtime chunk-gen / biome takeover by the region layer | `add-region-topology/proposal.md` | Offline-first OTG §19; runtime is passive |
| Loot tables / complex authored block-entity NBT | `README.md` "Not included" | Out of scope for the library layer |

---

## How to use this index

- **Picking the next change:** §A2 is the smallest high-leverage step (makes
  the region layer visible by gating sect spawning on `admitted_subjects`).
  §A consumers and §B terrain realization are the natural next arcs; §C
  (town worldgen) is the largest single unlock.
- **When realizing an item:** mark it ✅ with the realizing change name, move
  it under a "Realized" sub-section, and remove any obsolete Non-Goal entries.
  Per `docs-knowledge-base`, update this note + any same-topic spec in one
  change.
- **When adding a new deferral:** cite the source artifact precisely (change
  name + file + section) and verify it is not already realized in code before
  listing — this note must not drift into aspirational wishlist.
