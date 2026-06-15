## Context

Generation (Phases 0–4) emits concrete external-mod ids into the shipped NBT libraries
and proves them legal under one catalog. Runtime placement, however, does not honor the
"optional + fallback, never air" contract (architecture decision 3 in
`docs/external_mod_integration_plan.md`). The placement path is
`TownGenerator.realizeParcels` → `level.getStructureManager().get(id)` →
`StructureTemplate.placeInWorld(...)` (`TownGenerator.java:230-249`). Minecraft resolves
each palette entry through `NbtUtils.readBlockState` against the live block registry at
**template-load time**; an id absent from `BuiltInRegistries.BLOCK` resolves to
`Blocks.AIR.defaultBlockState()`. By the time `placeInWorld` runs, the palette is already
resolved, so a post-load or post-placement pass cannot tell an intended air block from a
dropped mod block. The programmatic motif placement in `TownGenerator` (`placeStall`,
`placeLampPost`, street network, …) is all `Blocks.<X>.defaultBlockState()` and stays
vanilla in this change.

Constraints carried from earlier phases: ids stay **strings**, no external mod classes
are imported; Python remains the single source of truth for slot/fallback data; the
`full` tree is what ships. Current version is `0.6.0-fix3`.

## Goals / Non-Goals

**Goals:**
- Guarantee at runtime that any registry-absent block id is placed as a deterministic
  vanilla fallback — never air, never a crash — for both `/myvillage town` and
  `/myvillage place` template placement.
- Keep the fallback table generated from the same style slot data the generator reads,
  shipped as a data file, with no `modid → block` map hand-maintained in Java.
- Declare the six confirmed decor mods as optional dependencies.
- Regenerate / validate / preview end-to-end and stage a mods-on vs mods-off acceptance
  pass (Phase 6).

**Non-Goals:**
- Adding new mod families, new 落点, or new motifs (catalog frozen for this change).
- Translating mod blockstate orientation into the fallback (the fallback is a degraded
  vanilla approximation; orientation fidelity is the installed-mod path's concern, owned
  by the Phase 2 adapter).
- Regenerating the shipped tree to the `vanilla` profile — the runtime resolver is what
  makes a mod-free install safe, so the shipped `full` tree stays.
- Any change to generation-time legality (Phase 4 owns that).

## Decisions

### 1. Patch the template NBT palette before load, not after
The fallback must be applied **before** Minecraft resolves the palette to AIR. The
resolver loads the structure's `CompoundTag`, walks the `palette` (and `palettes` for
multi-palette templates), and for any entry whose `Name` is absent from the block
registry rewrites `Name` to the mapped fallback id and replaces `Properties` with the
fallback's properties, then hands the patched tag to `StructureTemplate.load(holderGetter,
tag)`. Placement then proceeds through the normal `placeInWorld`.

- *Alternative — `StructureProcessor` on `StructurePlaceSettings`:* rejected. Processors
  run during placement, after the palette is already AIR; the original id is gone.
- *Alternative — post-placement world sweep:* rejected. Cannot distinguish authored air
  from a dropped mod block, and is O(volume).
- *Alternative — custom `HolderGetter<Block>` that returns a fallback for missing ids:*
  viable in principle but `getStructureManager().get(id)` owns the holder getter and
  caches the loaded template; intercepting it is more invasive than reading the tag and
  loading it ourselves through a thin helper. The helper centralizes both `town` and
  `place` on one path.

### 2. Fallback table is generated from slot lists, shipped as data
A new generator step (in `tools/`, reusing `tools/buildgen/style.py` + `modset.py`) reads
the per-style slot lists and, for each non-`minecraft` id, takes the trailing
`minecraft:` fallback of the slot(s) that list it, emitting
`src/main/resources/data/myvillage/mod_block_fallbacks.json` as `{ "modid:block":
"minecraft:fallback[props]" }`. Java loads this once at server start into a
`Map<ResourceLocation, BlockState>` (parsing the value with `BlockStateParser`).

- Rationale: the slot's last entry already encodes the designed degraded form; deriving
  the table from it keeps runtime and generation from drifting and needs no Java edit when
  a slot gains an id.
- *Precedence for multi-slot ids:* when an id appears in slots with differing fallbacks,
  pick deterministically by (style order, slot order) and record the rule in the
  generator; log a generation-time note so collisions are visible.
- *Default-of-last-resort:* an id present in no slot list (shouldn't happen for shipped
  templates) maps to a single global fallback (e.g. `minecraft:cobblestone`) so the
  resolver is total.

### 3. Optional dependencies, not required
Add six `[[dependencies.myvillage]]` blocks with `type = "optional"`,
`ordering = "AFTER"` (mods load before `myvillage`), `side = "BOTH"`, open
`versionRange`. Optional keeps the mod loadable standalone; ordering guarantees their
blocks are registered before our placement runs.

### 4. Phase 6 is verification, not new behavior
Regenerate libraries under `--profile full` (expected byte-stable vs `main` since Phase 3
already populated ids), regenerate the fallback map, run the full validator suite under
both `--profile vanilla` and `--profile full`, refresh offline previews, build the jar,
and stage a mods-on / mods-off `/myvillage town` acceptance pass. Bump version and update
docs together.

## Risks / Trade-offs

- **Fallback loses orientation/semantics** (a slanted awning becomes a flat slab) →
  accepted; the contract is "no air, no crash," not visual parity without the mod. The
  installed-mod path keeps full fidelity.
- **Multi-palette / jigsaw templates** could carry palettes the simple walk misses →
  mitigation: handle both `palette` and `palettes`; the shipped templates are single
  structure NBTs, so this is a guard, not a common path.
- **BlockState parse failure for a fallback value** (bad props string in the data file) →
  mitigation: the generator emits canonical states; Java falls back to the block's default
  state on parse failure and logs once.
- **Registration ordering with optional deps** — if a decor mod registers blocks late →
  mitigation: `ordering = "AFTER"` plus resolving lazily at placement (registry queried
  per-id at load), not cached at our mod-construct time.
- **Regeneration drift** — Phase 6 regen could surface a non-byte-stable diff → that would
  reveal an undocumented generation change; treat any diff as a finding to explain before
  shipping, not auto-accept.

## Migration Plan

1. Land the fallback-map generator and emit `mod_block_fallbacks.json`.
2. Land the Java resolver + helper and route `realizeParcels` / `place` through it.
3. Add optional deps to `neoforge.mods.toml`.
4. Phase 6: regenerate, validate (both profiles), preview, build jar, stage acceptance.
5. Bump version, update README/CHANGELOG/plan status.

Rollback: the resolver is additive — reverting the helper restores the previous
(air-on-missing) behavior; the data file and optional deps are inert without it.

## Open Questions

- Global default-of-last-resort block id — `minecraft:cobblestone` proposed; confirm
  during implementation if any shipped id lacks a slot fallback.
- Whether `/myvillage place` of a single template should warn in chat when it substitutes
  fallbacks (useful for acceptance triage) or stay silent — lean toward a one-line summary
  count.
