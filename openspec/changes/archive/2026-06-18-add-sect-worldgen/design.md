## Context

`build-sect-compound` produces a deterministic terraced sect compound and exports its terrace profile + geometry parameters, but only on-the-spot via `/myvillage sect`. The mod registers no worldgen at all (`MyVillageMod`: "No worldgen is registered"). This change makes sects appear during world generation, sited to dramatic terrain.

The hard problem with siting on found terrain is that a natural peak rarely matches the compound's terrace profile — terraces float or bury, and suitable peaks are rare. We invert it: fix the terraces, then **derive the mountain from them (反推山形)**. This trades the old problem (adapt buildings to terrain) for a new one (blend the man-made mountain into natural terrain) — addressed by an outer blend skirt. Earlier exploration weighed a deferred force-load hybrid (reuse the command realizer when a player approaches) against a custom `Structure`; we chose the custom `Structure` because terrain pop-in is far more jarring than building pop-in, and a man-made mountain that appears after the fact would be unacceptable.

## Goals / Non-Goals

**Goals:**
- A custom sect `Structure` (StructureType + registration) sited during chunk gen, rare, biome-gated to high relief, world-seed reproducible, locatable.
- Derive the mountain from the terrace profile: skeleton + noise slopes + outer blend skirt + cliff-back face, deterministic per seed.
- Manual cloud-sea/fog surface (placed translucent blocks + powder-snow wisps), single peak.
- Reuse the `build-sect-compound` realizer to place the compound onto derived terrain.
- Detached-spire feature gets its raised solitary peak; appears randomly per site; force-generate command selects a variant.

**Non-Goals:**
- No mountain range — single peak this change; ranges later.
- No volumetric/biome fog rendering — cloud sea is a placed-block illusion.
- No town worldgen — towns are a separate later change.
- No change to the compound's form/variants (owned by `build-sect-compound`).

## Decisions

### D1: Custom `Structure`, not jigsaw and not deferred force-load
Register a `StructureType` with `findGenerationPoint` (siting/biome/spacing) and `generatePieces`. The mountain + compound bake into chunks during generation. Rationale: terrain that pops in after approach is unacceptable; jigsaw cannot express the derived heightfield or the procedural compound.

### D2: The terrace profile is the contract; the realizer is shared
Consume exactly the terrace skeleton + parameters `build-sect-compound` exports. Reuse its compound realizer to place volumes/galleries/stairs/bridge onto the derived terrain, so worldgen and command produce the same compound — only the terrain source differs.

### D3: Derive height as skeleton + noise + skirt; build solid
Terrace elevations are the skeleton; inter-terrace and outer slopes are seed-driven noise; an outer skirt interpolates to the natural heightmap across a radius. The body is solid built stone — structures place after terrain/surface, so no caves/ore intrude, which suits a man-made 仙山 and sidesteps the feature/terrain timing problem.

### D4: Cloud sea is placed blocks at a fixed Y
A horizontal translucent sheet (white/tinted glass) at a configured Y between gate and disciple terraces, optional powder-snow wisps at terrace edges. Accepts the limitation that this is a visible surface, not drifting fog; true biome fog is a possible later enhancement.

### D5: Feature is random in worldgen, forceable by command
The detached-spire feature uses the compound's per-seed selection in natural worldgen (so it appears on some sites, not others). The force-generate command overrides the roll and selects a specific variant (or none) for review/testing, and registers `/locate` for discovery.

## Risks / Trade-offs

- **Blend skirt seams** — the man-made/natural boundary is the new core risk; a too-small skirt radius leaves a visible cut, too-large washes out the mountain. Tunable radius + a seam check in validation.
- **Cross-chunk terrain writes** — writing a multi-chunk mountain from a structure must survive chunk boundaries and the structure/terrain step ordering; prototype the heightfield write before wiring the full compound.
- **Performance/volume** — a solid mountain is many blocks; bound footprint to a single peak this change and measure generation cost in the seed survey.
- **Cloud-sea read** — a flat glass sheet can look artificial; mitigate with randomized/feathered edges and powder-snow wisps, accept it as a first pass.
