# External Mod Integration Plan (pre-propose)

Staging plan for integrating external decoration mods (市井 + 宗门修仙) into the
buildgen pipeline. This is a **pre-propose** document: it sequences the work so it
can be turned into one or more OpenSpec changes and executed one phase at a time.

Read together with `docs/building_rules.md` and `docs/blockstate_notes.md`.
Do not start coding from this doc alone — each phase becomes its own change/PR.

## Status

- Phase 0: done (change `add-external-mod-catalog-slots`)
- Phase 1: done (change `add-external-mod-catalog-slots`)
- Phase 2: done (change `add-external-mod-decor`)
- Phase 3: done (change `add-external-mod-decor`)
- Phase 4: done (change `add-modset-aware-validation`) — generators + validators take
  `--profile {vanilla,full}`, resolving mod-id legality from `tools/buildgen/modset.py`
  against `exmod/mod_block_catalog.json`; `vanilla` forbids all mod ids, `full` allows
  only confirmed-namespace catalog ids; both profiles validate clean.
- Phase 5: done (change `add-mod-runtime-fallback`) — generated runtime fallback
  data, Java pre-load palette patching for `/myvillage town` and `/myvillage place`,
  and optional dependency declarations are in place.
- Phase 6: automated prep done for change `add-mod-runtime-fallback`; mods-on /
  mods-off in-game screenshot acceptance is still pending manual review.

Update the checkboxes above as phases land.

## Context

- Target: Minecraft `1.21.1` / NeoForge `21.1.233`, mod_version `0.6.0`.
- Goal aesthetics: 市井 (lived-in market/坊市) + 宗门修仙 (cultivation sect) atmosphere.
- Vanilla gives enough *material/colour* (bamboo, copper, dark oak, spruce, stone
  brick, terracotta, amethyst, nether/end palettes) but lacks three *forms*:
  curved/tiled East-Asian roofs, real furniture, and atmosphere props
  (paper lanterns, incense burners, weapon racks, ritual circles, statues).
- External mods to fill the gaps are staged in `exmod/`:
  - `exmod/deep-research-report.md` — mod selection + design intent
  - `exmod/mod_jars.zip` — runtime jars
  - `exmod/mod_assets.zip` — assets (blockstates/textures) for inventory extraction
- Contents of `exmod/` are intentionally **not** opened until Phase 0.

## Architecture decisions (already settled)

1. **Semantic material slots, not concrete blocks.** Already implemented in
   `tools/buildgen/style.py` (`material_slots`, `primary()`, `alternates()`,
   `variation_rate`) and the per-style JSONs in `tools/buildgen/styles/`
   (`chinese_courtyard`, `cultivation_sect`, `cultivation_town`, `medieval_village`).
   Existing slots: `BASE_STONE / WALL_MAIN / FRAME_WOOD / ROOF_DARK / DETAIL_WOOD /
   LIGHTING / GROUND_PATH / INTERIOR_WORK / INTERIOR_STORAGE / INTERIOR_CIVIC /
   FURNITURE / SIGNAGE`. New slots are *added*, never replacing this model.

2. **Registry-id strings, no external class imports.** The Python pipeline already
   emits block ids as strings (`minecraft:lectern[...]`). External mod blocks are
   just `modid:block[...]` strings — no Java/Python class dependency at generation
   time.

3. **All external mods optional + fallback.** Every slot list keeps a guaranteed
   vanilla id as its last entry. Missing mod ⇒ degrade to vanilla, never crash or
   place air.

4. **Two-layer resolution (the part GPT's advice missed).**
   - *Generation layer (Python):* generate against a **modset profile**; the emitted
     library carries concrete ids so the validator can check them.
   - *Runtime layer (Java):* placement must still resolve
     `ResourceLocation → BuiltInRegistries.BLOCK.containsKey(rl) ? block : fallback`,
     so a world without the mod degrades gracefully. Java currently hardcodes via
     `Blocks.` + parses `ResourceLocation` in
     `src/main/java/com/example/myvillage/town/TownGenerator.java`; the fallback
     resolver lands here.

5. **A slot is id + orientation grammar, not just an id.** Modded roof families have
   different blockstate props (`facing/half/shape` or custom `variant`) than vanilla
   stairs. Swapping the string alone misplaces them, so families with novel
   blockstate grammar get a small orientation adapter. The existing role-based
   substring picking (`ROOF_DARK -> '_stairs'/'_slab'`, `slot_entry()`) is the
   primitive version of this.

6. **Slot = material family; motif = composition.** Single-purpose props (market
   counter, ritual altar, sect gate) are built as **motifs** in
   `tools/buildgen/ops.py` that resolve a few slots — they do not each get their own
   slot. Preserve this boundary.

## Modset profiles (open decision — default chosen)

Default to **two profiles**:

- `vanilla` — no external mods; every slot falls back to vanilla. Keeps the mod
  compatible with worlds that don't install the decor mods.
- `full` — the target modpack with all `exmod/` mods active.

A middle profile (e.g. Macaw's-only, no magic mod) is only added if a real use case
appears. **Needs user confirmation** before Phase 4.

## Phases

Sequencing principle: build and prove the fallback skeleton on `vanilla` first, then
slot mod content in. Phases 1, 2, 5 do **not** need `exmod/` contents and can start
immediately; Phases 0 and 3 are the only ones that open the zips.

### Phase 0 — Mod block catalog (one-time extraction)
- **Goal:** a machine-readable source of truth for every mod block.
- **Work:** `tools/extract_mod_catalog.py` reads `exmod/mod_assets.zip` (or jar
  `assets/<modid>/blockstates/`) → `exmod/mod_block_catalog.json`:
  `modid → [block id, blockstate properties, texture names]`. Merge in the design
  intent (落点) from `deep-research-report.md`.
- **Gate / output:** `mod_block_catalog.json` reviewed; mod list confirmed.
- **Needs user:** confirm the final mod set after the catalog is generated.
- **Touches exmod:** yes (the only required unzip).

### Phase 1 — Slot system: namespace filter + fallback convention (mod-agnostic)
- **Goal:** optional + fallback works end-to-end with zero mod ids present.
- **Work:**
  - `load_style(style_id, available_namespaces)` filters each slot list to active
    namespaces at load time; `primary()/alternates()` contracts unchanged so
    downstream code needs no edits.
  - Convention: every slot list ends with a guaranteed vanilla id.
  - Declare new **empty** semantic slots: `ROOF_TILE`, `PAPER_LANTERN`,
    `RITUAL_ANCHOR`, `MARKET_FITTINGS` (names provisional).
- **Gate:** generate under `vanilla` profile — all new slots resolve to vanilla
  fallback, nothing breaks.
- **Touches exmod:** no.

### Phase 2 — Orientation adapters (mod-agnostic scaffold)
- **Goal:** place block families whose blockstate grammar differs from vanilla.
- **Work:** an adapter mapping `(cell role, orientation) → blockstate` for one family
  first (**tiled roof**, highest value), then replicate the pattern.
- **Gate:** roof family orients correctly (not just string substitution).
- **Touches exmod:** no (adapter interface only; concrete ids arrive in Phase 3).

### Phase 3 — Populate slots + author motifs (uses Phase 0 catalog)
- **Goal:** mod blocks actually appear with correct design intent.
- **Work:**
  - Insert catalog ids at the **front** of the matching slot lists per style
    (`chinese_courtyard`, `cultivation_sect`, `cultivation_town`).
  - New motifs in `ops.py` for compositions: market stall, alchemy/ritual altar,
    sect gate/牌坊.
- **Gate:** `full` profile generates buildings using mod blocks at intended spots.
- **Needs user:** one-line 落点 per mod block family (street / sect gate / altar …).
- **Touches exmod:** indirectly (via `mod_block_catalog.json`).

### Phase 4 — Modset-aware validation
- **Goal:** generation and validation share one source of truth.
- **Work:** `tools/validate_*.py` + forbidden/whitelist driven by the active modset
  profile. `vanilla` profile forbids all mod ids; `full` allows them.
- **Gate:** both profiles validate clean.
- **Touches exmod:** no.

### Phase 5 — Java runtime resolver + soft dependency
- **Goal:** missing mod degrades gracefully at runtime.
- **Work:**
  - Java resolver: `ResourceLocation → containsKey ? block : fallback` in
    `TownGenerator.java` (and any other placement site).
  - Register the decor mods as **optional** dependencies in
    `src/main/resources/META-INF/neoforge.mods.toml`.
- **Gate:** with mods installed, new blocks show; without them, vanilla fallback
  places — no crash, no air.
- **Touches exmod:** no (jars consumed via the modpack, not the repo).

### Phase 6 — Regenerate, validate, preview, iterate
- **Goal:** confirm the aesthetic in-game.
- **Work:** regenerate libraries under `full`, run validators, run existing preview
  tools, screenshot, iterate on 落点 and ratios.
- **Touches exmod:** no.

## Dependency graph

```
Phase 0 (unzip → catalog) ─┐
                           ├─> Phase 3 ─> Phase 4 ─┐
Phase 1 (slots + fallback) ┤                        ├─> Phase 6
Phase 2 (orientation adp.) ┘                        │
Phase 5 (Java fallback) ────────────────────────────┘
```

- Phases 1, 2, 5 are independent of `exmod/` contents.
- Phases 0, 3 are the only ones that open the zips; Phase 3 also needs user 落点.

## Mapping aesthetics → slots (design intent reference)

| Need | Aesthetic | Likely slot / motif |
|---|---|---|
| Tiled / curved roof, eaves | both | `ROOF_TILE` (+ orientation adapter) |
| Paper / red lanterns | 市井 + 修仙 | `PAPER_LANTERN`, `LIGHTING` |
| Lattice / paper windows | 市井 | `WALL_MAIN` trim, window motif |
| Tables, chairs, screens, shelves | 市井 | `FURNITURE` |
| Market stall, food display, carts | 市井 | `MARKET_FITTINGS` motif |
| Railings, bridges, well head | 市井 | `DETAIL_WOOD`, bridge motif |
| Incense burner / 鼎 / 丹炉 | 修仙 | `RITUAL_ANCHOR` motif |
| Ritual circle / 法阵 (floor glow) | 修仙 | floor motif + `LIGHTING` |
| Jade / marble / cloud stone | 修仙 | `BASE_STONE`, `WALL_MAIN` |
| Statues, weapon racks, talismans | 修仙 | dedicated motifs |
| Sect gate / 牌坊 / 山门 | 修仙 | gate motif |

Per-dimension notes: Nether already strong for 魔道宗 (crimson/warped, blackstone,
soul fire); End is a good base for 仙界/虚空 (end stone, purpur, chorus, end rods).
Both mostly need cloud/mist and floating-island decoration, tracked under
`BASE_STONE` alternates + future motifs.

## Next action

Start with **Phase 1** (mod-agnostic: namespace filter + fallback convention + empty
new slots), and prepare the **Phase 0** extraction script to run on demand. When this
plan is approved it should be promoted into a formal OpenSpec change (suggested id:
`add-external-decor-mods`) before implementation begins.
