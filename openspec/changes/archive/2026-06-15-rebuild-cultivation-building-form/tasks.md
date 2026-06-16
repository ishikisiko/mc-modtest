## 1. Roof silhouette forms (form registry)

- [x] 1.1 Implement `sweeping_eave_roof` (翘角 corners stepped out-and-up from stairs/slabs, deep overhang) and register it
- [x] 1.2 Implement `hip_roof` (庑殿, four-sided slopes) and register it
- [x] 1.3 Implement `pyramidal_roof` (攒尖, converging to a finialed apex) and register it
- [x] 1.4 Redefine `tiered_eave_roof` to stack sweeping eaves (curved 重檐) with small-footprint fallback to a single sweeping eave
- [x] 1.5 Add ridge/crown ornament forms (宝顶 finial, 鸱吻 ridge-ends) as roof detail resolving through `RIDGE_ORNAMENT`
- [x] 1.6 Add `dougong` (斗拱) bracket-set detail under deep eaves, replacing the fence-under-eave rhythm
- [x] 1.7 Style-vocabulary validation accepts the new forms; assert medieval/Chinese/civic never invoke them

## 2. Style schema & profiles

- [x] 2.1 Add `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, `BALUSTRADE` slots to `style.py` (optional, trailing vanilla fallback)
- [x] 2.2 Populate the new slots in `cultivation_town.json` / `cultivation_sect.json` (confirmed mod ids front, `minecraft:` fallback last)
- [x] 2.3 Update cultivation `allowed_roof_types` (add sweeping/hip/pyramidal) and drop Western motifs from `allowed_motifs`
- [x] 2.4 Retune cultivation `proportions` (hall overhang ≥ 2, platform/foundation ≥ 2, roof ratio ~0.5)

## 3. Cultivation massing grammar (archetypes)

- [x] 3.1 Add the platform (台基) massing element with an entry stair
- [x] 3.2 Add the colonnade (檐廊) standoff-column element under a deep eave
- [x] 3.3 Add the galleried pavilion (楼阁) massing with a balustraded balcony
- [x] 3.4 Add the tapering pagoda (塔) massing (per-story inset + pyramidal crown)
- [x] 3.5 Add the three-bay mountain gate (山门牌坊) massing volume
- [x] 3.6 Rewrite `cultivation_house` / `cultivation_shop` / `cultivation_inn` / `cultivation_market` off the Western aliases onto the grammar
- [x] 3.7 Rebuild `town_shrine` as 神庙/道观 massing (platform + colonnade + tiered eave; no corner tower)
- [x] 3.8 Rebuild `sect_gate` (gate), `sect_main_hall` (platform + colonnade + 重檐), `scripture_pavilion` (pagoda), `disciple_quarters`
- [x] 3.9 Replace the `alchemy_room` chimney with a 丹炉 furnace feature
- [x] 3.10 Remove every chimney / porch / woodpile / barrel / fence call from cultivation builders

## 4. Quality, validation, acceptance

- [x] 4.1 Quality gate: reject chimney / woodpile / porch-post in cultivation builds
- [x] 4.2 Quality gate: hall-class volumes carry a platform + colonnade; pagoda insets per story
- [x] 4.3 Regression: medieval / Chinese / civic libraries byte-stable (pre/post NBT hashes)
- [x] 4.4 Vanilla-profile cultivation output resolves every new slot to its fallback (no air, no mod-only id)
- [x] 4.5 Regenerate cultivation libraries + `reports/`; regenerate previews (`preview_structure.py --all`)
- [x] 4.6 Update `README.md` / `AGENTS.md` with the new forms and the geometry-vs-mod curve note
