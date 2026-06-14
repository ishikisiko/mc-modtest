## Context

The buildgen pipeline (`tools/buildgen/`) is layered: Style Profile (JSON data) → Archetype (functional massing) → Build Ops / Passes (block placement) → Compound (parcel layout) → Export/Quality. Today "style" is a single flat dimension (`medieval_village`, `chinese_courtyard`) and the *form* vocabulary it references is not actually data-driven: `ops.py` dispatches roofs through a fixed set (`gable_roof`, `cross_gable_roof`, `lean_to_roof`) and motifs through an `if motif == "..."` chain. The Chinese `歇山/悬山/硬山` are aliases that all resolve to `gable_roof` with different overhang. So adding a style JSON buys a palette swap but no new shape.

The project wants a 修仙 identity built around new shapes (multi-eave roofs, moon gates, spirit-arrays, terraced sect compounds) and wants to keep adding *bigger* forms over time. Two macro families are required — 城镇 (mortal town) and 宗门 (immortal sect) — and exploration concluded they diverge at the archetype, palette, and layout layers but must share the one engine (grid/massing/passes/ops/export/quality).

Constraints: MC 1.21.1, vanilla blocks only. Medieval and Chinese-courtyard outputs are already shipped and validated; the registry migration must not change them.

## Goals / Non-Goals

**Goals:**
- A **settlement-group** layer above style that bundles (style profile, archetype roster, layout strategy) into a named family, and is the documented hook for future families.
- A **form registry** (name→handler) for roof types and motifs so style vocabularies become pluggable and new forms are added without touching dispatch logic.
- A 修仙 **form vocabulary** (`tiered_eave_roof`, `moon_gate`, `spirit_array`, `incense_altar`, `cloud_rail`) registered as engine forms.
- Two flat style profiles (`cultivation_town`, `cultivation_sect`) with per-style `forbidden_blocks` and new spirit slots.
- Byte-stable medieval/Chinese output after migration (regression guard).

**Non-Goals:**
- Final sub-flavor split (仙宫 vs 魔修) — default 仙宫; 魔修 is a future profile.
- Full sect archetype roster polish — ship the layout + at least the hall/gate/tower forms; deeper rosters iterate later.
- Any non-vanilla blocks, custom block entities, or loot.
- Replacing the existing `medieval_village` / `chinese_courtyard` styles — they stay.

## Decisions

### D1: A "group" descriptor, not a third magic style id
A settlement group is a small descriptor binding `style_id` + archetype roster + layout strategy + scale params. Rationale: archetypes (民居 vs 大殿) and layouts (street vs terraced) genuinely differ between families, so overloading `style` (which is palette/vocabulary) would conflate two axes. Alternative considered — encode group via naming convention on `style_id` (like the current `export.py` `startswith("chinese_courtyard")` check): rejected as the brittle pattern this change is trying to retire. The descriptor is the explicit extension hook for "以后会增加大的".

### D2: Form registry replaces if-chains, migration is behavior-preserving
Introduce `ROOF_REGISTRY` and `MOTIF_REGISTRY` mapping a name → handler `(grid, style, rng, node, ...) -> info`. `passes.py` looks up `roof["type"]` / `node.meta["motif"]` in the registry instead of branching. Migrate existing `gable_roof`/`cross_gable_roof`/`lean_to_roof` and every current motif into the registry verbatim. Rationale: the registry is the core enabler of extensibility and makes `allowed_roof_types`/`allowed_motifs` in style JSON actually mean something. Alternative — keep the if-chain and just append 修仙 branches: rejected because the user explicitly wants ongoing extensibility, and growing the chain is the smell being removed. Trade-off: a registry indirection for a small dispatch surface, justified by the stated growth.

### D3: Two flat style JSONs, no inheritance
`cultivation_town.json` and `cultivation_sect.json` are independent and self-contained. Exploration estimated ~40% palette overlap — not enough to justify an `extends` mechanism in `style.py`. Rationale: style profiles are cheap data; duplicating a few timber/stone slots is clearer than an inheritance feature and avoids cross-style drift. The sect profile = mortal base + 灵材, expressed directly.

### D4: 灵材 = unlock + new slots, scoped to sect
`forbidden_blocks` becomes genuinely per-style. `cultivation_sect` removes `quartz`/`copper`/`gold_block` (and similar) from its forbidden list and adds spirit slots `SPIRIT_CRYSTAL` (amethyst family) and `RITUAL_METAL` (oxidized copper family). `cultivation_town` keeps them forbidden. New slots are omittable; generators referencing a missing slot skip placement (consistent with existing civic-slot behavior in `style-profile`). Rationale: keeps mortal town austere while letting sect forms pull jade/crystal/glow without leaking into town.

### D5: Sect layout extends compound.py; town reuses standalone model
宗门 is a parcel-level cohesive complex → extend `CompoundGraph` with a terraced/axial sect strategy (monumental scale, hierarchical slots). 城镇 is individual buildings placed by the game's structure logic → reuse the existing standalone archetype/library model. Rationale: these two layout shapes already exist latent in the codebase (compound vs standalone); the group descriptor just names which one a family uses.

## Risks / Trade-offs

- **Registry migration changes existing output** → Add a regression guard: regenerate `medieval_village` and `chinese_courtyard` libraries before/after and diff the NBT; require byte-stability (or an explicitly reviewed diff). Land the registry as its own phase 1 commit so a regression is bisectable.
- **`tiered_eave_roof` geometry is genuinely new and hard** → Build it as a composition of existing eave/slab primitives where possible; gate it behind quality checks; allow a fallback to `歇山`-style single eave if a footprint is too small.
- **Scope creep (full sect)** → Phase the tasks: P1 registry (no behavior change), P2 town group, P3 sect group + new forms. Each phase ships independently.
- **灵材 blocks failing the quality forbidden-gate** → Make the gate read the *active* style's forbidden list (it already does per the style-profile spec); add a test that a sect-only block passes in sect and fails in town.
- **Spirit-glow / froglight availability** → All chosen blocks verified present in MC 1.21.1; no 1.21.4 blocks (pale oak/moss) used.

## Resolved Questions

- **Sub-flavor**: ship only the bright `cultivation_sect` profile now. Do not stub a dark/evil-cultivation profile until a dedicated change defines its palette, forms, and quality gates.
- **Spirit-material bleed into town**: keep spirit materials strictly sect-exclusive in this change. `cultivation_town` keeps quartz, copper, gold-block, and spirit materials forbidden; any small shrine/shop exceptions require a later town-roster change.
- **Town layout granularity**: keep `cultivation_town` on the standalone building-library model for this change. Compound-style town streets or courtyard clusters are future layout work, not part of this implementation.
