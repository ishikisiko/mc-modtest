## Context

Phases 0–1 left the pipeline able to *load* mod ids (namespace-aware `load_style`, vanilla-fallback convention, four new empty slots) but place *none* of them — `full` and `vanilla` generate identically. The catalog (`exmod/mod_block_catalog.json`) is the source of truth for what's available. Two settled architecture constraints from `docs/external_mod_integration_plan.md` bind this design: **(5) a slot is id + orientation grammar, not just an id** — modded families with novel blockstate props need an adapter, not bare string substitution; and **(6) slot = material family, motif = composition** — single-purpose props are motifs that resolve a few slots, they do not each get a slot.

The staged assets are `fetzisdisplays`, not the `fetzisasiandeco` curved-roof/lantern mod the design report assumed (catalog `notes` records this). Surveying the catalog: there is **no** `roof`/`lantern`/`pagoda` block; the only present family with genuinely non-vanilla *roof-shaped* grammar is `supplementaries:awning` (`facing` + `bottom` + `slanted`). The richest present, vanilla-grammar families are Macaw's Furniture (`FURNITURE`), `ars_nouveau` ritual blocks (`RITUAL_ANCHOR`), and `farmersdelight`/`supplementaries`/`fetzisdisplays` props (`MARKET_FITTINGS`). This is why Phase 2 anchors on awnings rather than tiled roofs.

The existing role-based primitive is `style.slot_entry(slot, contains)` plus `stair_state`/`slab_state` in `ops.py`, which hardcode vanilla stair/slab grammar (`facing/half/shape/waterlogged`, `type`). That primitive is exactly what Phase 2 generalizes.

## Goals / Non-Goals

**Goals:**
- An orientation adapter resolving `(family, cell role, orientation) → blockstate`, with vanilla stair/slab as registered families and `supplementaries:awning` as the first non-vanilla family.
- Populate slots with confirmed-namespace mod ids at the front, fallback preserved last, so `full` places mod blocks and `vanilla` stays byte-identical.
- New `market_stall` motif; route existing `incense_altar`/`spirit_array` and a sect-gate/牌坊 motif through `RITUAL_ANCHOR` / `MARKET_FITTINGS`.

**Non-Goals:**
- Modset-aware validation forbid/allow lists (Phase 4).
- Java runtime resolver and `neoforge.mods.toml` optional deps (Phase 5).
- Regenerate/preview/iterate the libraries (Phase 6).
- Staging or referencing `fetzisasiandeco` (curved roofs / paper lanterns) — deferred until those assets exist.

## Decisions

**1. The adapter is a family registry, and vanilla stairs/slabs are just families in it.**
Define each family by its property grammar and a function `(cell role, orientation) → props`. `stair_state`/`slab_state` become the `vanilla_stairs`/`vanilla_slab` families so existing roof code keeps identical output. Rationale: this honors plan constraint 5 without a parallel code path, and folding the existing helpers in means the new families share one tested seam. Alternative — a per-mod `if` ladder at each call site — rejected: it scatters grammar knowledge and re-introduces the substring-guessing the plan calls out.

**2. The awning family models a slanted eave, not a flat roof.**
`supplementaries:awning` has `facing/bottom/slanted` (no `half`/`shape`). The adapter maps an eave/canopy cell to `facing = outward`, `slanted = true` for the sloped edge, and `bottom` per the awning's vertical position. Rationale: that is the only present family that reads as an East-Asian eave or a market canopy, so it carries both the `ROOF_TILE` eave intent and the `market_stall` canopy. Alternative — treat `supplementaries:*_tile_stairs` as `ROOF_TILE` — kept as an *additional* flat-tile option but not the adapter's proving family, since it already fits vanilla stair grammar and proves nothing new.

**3. Slot population is data-first, at the front, fallback last.**
Mod ids go at the head of each slot list in the style JSONs; the trailing `minecraft:` id (Phase 1 invariant) stays. Under `full`, `primary()` returns a mod id; under `vanilla`, namespace filtering drops the head and `primary()` returns the same fallback as today. Rationale: zero loader changes, and the `vanilla` byte-stability guard falls out for free. 落点 per role (confirmed): `ROOF_TILE` ← `supplementaries:awning*` + `supplementaries:{stone,blackstone}_tile_{stairs,slab}`; `PAPER_LANTERN` ← `ars_nouveau:{sconce,sourcestone_sconce,source_lamp}` + `supplementaries:candle_holder*`; `RITUAL_ANCHOR` ← `ars_nouveau:{brazier_relay,arcane_pedestal,arcane_core,agronomic_sourcelink,alchemical_sourcelink}`; `MARKET_FITTINGS` ← `farmersdelight` + `supplementaries` + `fetzisdisplays` display/sign props; `FURNITURE` ← `mcwfurnitures`; window/wall slots ← `mcwwindows`.

**4. Motifs compose slots; new props become motifs, not slots.**
`market_stall` is new (counter from `MARKET_FITTINGS`, canopy via awning adapter, goods display). `incense_altar`/`spirit_array` already exist and gain a `RITUAL_ANCHOR` focal block; the sect gate/牌坊 builds on the existing gate motif vocabulary with `MARKET_FITTINGS`/signage. Rationale: honors constraint 6 and avoids slot proliferation. Each motif resolves through `slot_entry`/`optional_slot_entry`, so omitted optional slots skip cleanly and the `vanilla` profile yields the fallback composition.

**5. The catalog's `role_families` heuristic is advisory, not authoritative.**
The keyword-matched `design_intent.role_families` is noisy (e.g. `archwood_leaves` and basketweave under `ROOF_TILE`). Per-role ids are hand-picked from the catalog against the confirmed mod set, not copied from the heuristic. Rationale: the heuristic over-matches; the 落点 is a curated decision (the Phase 3 user gate).

## Risks / Trade-offs

- **Awning is a canopy, not a true curved roof** → it reads as eave/market-shade, which fits 市井 but won't deliver the temple-roof silhouette; accepted, with real `fetzisasiandeco` swap deferred. The adapter interface is built so adding a curved-roof family later is one more registration.
- **A populated slot could accidentally omit the trailing vanilla fallback** → the Phase 1 load-time fallback-convention check already fails fast on this; population must keep the fallback last.
- **Awning vertical placement (`bottom`) and `slanted` can look wrong at corners/over tall walls** → start on a single eave edge with a generated preview check before applying broadly; corners can fall back to vanilla stair eaves.
- **`full` output now diverges from `vanilla`, so a naive "outputs match" test breaks** → the regression guard is reframed: `vanilla` must match pre-change `vanilla`; `full` is checked for presence of expected mod ids, not equality with `vanilla`.
- **Slot ids could drift from the catalog if mods update** → ids are validated against the confirmed namespace set at authoring time; Phase 4 will add automated modset validation.

## Migration Plan

1. Land the orientation adapter with vanilla stair/slab families; assert existing roof generation output is unchanged.
2. Add the awning family; verify eave orientation on a generated preview.
3. Populate slot lists with mod ids (front) per the 落点 above, fallback preserved.
4. Add `market_stall`; route `incense_altar`/`spirit_array`/gate motifs through the new slots.
5. Verify: `vanilla` output byte-identical to pre-change; `full` output contains the expected mod ids at the intended spots.

Rollback: revert the style JSON id insertions and the motif/adapter edits; the catalog is inert and untouched. Because mod ids are only ever at the head with the vanilla fallback retained, reverting cannot leave a slot empty.

## Open Questions

- Whether `market_stall` and the sect-gate motif belong in `cultivation_town`/`chinese_courtyard` only, or also `cultivation_sect` — resolved per-style during implementation against each style's `allowed_motifs`.
- Exact awning color per style (17 dyes available) — an implementation palette detail, not blocking.
- Whether flat `*_tile_stairs` should appear in `ROOF_DARK` too (not just `ROOF_TILE`) — deferred; out of scope unless a style needs it.
