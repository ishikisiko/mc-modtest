## Context

`cultivation_*` archetypes delegate to the Western domestic builders and inherit cottage/manor massing, gable roofs, chimneys, porches, woodpiles, and barrels. The recent mod-block work swapped material slots but left this skeleton intact, so the buildings read as Western. The engine already has the right seams for a fix — a name→handler roof/motif registry (`ops.py`), a graph-based massing layer (`massing.py`/`archetypes.py`), and slot-based material resolution with a vanilla fallback contract (`style.py`) — so the rebuild fits the existing architecture rather than fighting it.

## Goals / Non-Goals

**Goals**
- Each cultivation building reads as 修仙 *in isolation*: silhouette (飞檐/攒尖/curved 重檐), platform (台基), colonnade (檐廊), pavilion/pagoda (楼阁/塔), built gate (山门), ornament (宝顶/鸱吻/斗拱).
- Kill the Western tells (chimney/porch/woodpile/barrel/fence) from cultivation output.
- Preserve the slot + vanilla-fallback discipline; keep legacy libraries byte-stable.

**Non-Goals**
- How buildings sit together — terraces, bridges, courtyards, siting (→ `rebuild-cultivation-settlement-form`).
- A new required external Asian-decor mod dependency.
- Live-world terrain integration.

## Decisions

### D1 — The eave curve is generated geometry, not a mod block
The only staged decor mods (supplementaries / mcw* / ars_nouveau / farmersdelight / fetzisdisplays) have no curved-roof or 飞檐 corner block; the Asian-decor mod that would (fetzisasiandeco-class) is **not staged**. So the upturned corner (翘角) and deep eave are built from vanilla stairs/slabs that step out-and-up at the corners, which keeps the silhouette working under the `vanilla` profile and independent of any mod. New roof-tile / lantern mod blocks remain an optional skin via `ROOF_TILE` / `PAPER_LANTERN`, layered on top, never required.
*Tradeoff*: blockier than a true curved mesh. Mitigate by biasing hall/gate footprints large enough for the step to read, and falling back to a single straight eave below a size threshold.

### D2 — Cultivation gets its own massing builders; no more Western aliases
`build_cultivation_*` and the sect builders are rewritten to compose the new grammar elements (platform → colonnade → walls → sweeping/tiered eave → ornament) instead of calling `build_small_house` / `build_shop` / `build_tavern` / `build_lord_manor`. This is the structural heart of the change and what makes re-skinning unnecessary.

### D3 — New *semantic* slots for form, not just material
`COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, `BALUSTRADE` join the slot schema so the colonnade, platform, finial, and railing resolve through slots and degrade to a vanilla fallback, consistent with the existing contract. No build operation hardcodes a block for these.

### D4 — Altitude seam with the companion change
This change = building-in-isolation; `rebuild-cultivation-settlement-form` = composition/siting. The seam: this change exposes platform height and the gate / hall / pagoda / shrine forms; the companion stacks them on terraces and links them with bridges. `town_shrine`'s *form* (神庙/道观 massing) lands here; its *placement as the town's ritual-axis anchor* lands in the companion.

## Risks

- **Legacy byte-stability** — mitigate with cultivation-only code paths and pre/post NBT hash regression on medieval/Chinese/civic.
- **Vanilla-profile coverage of new slots** — mitigate with the existing trailing-`minecraft:` fallback-convention check extended to the new slots.
- **Blocky curve readability** — mitigate with footprint thresholds and graceful single-eave fallback.

## Open Questions

- Keep `tiered_eave_roof` (redefined) or deprecate it in favor of explicit `sweeping_eave_roof` + a tier count? *Proposal: redefine, keep the name to avoid churn in style profiles.*
- 仙宫 (bright) vs 魔修 (dark) sub-flavor ridge-ornament palettes — defer; default to 仙宫.
