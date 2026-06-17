## Why

The mod registers no worldgen — `MyVillageMod` states "No worldgen is registered" and every settlement is built by command. Once `build-sect-compound` makes a terraced sect compound buildable and deterministic, the natural next step is for sects to **appear in the world on their own**, sited to dramatic terrain and discovered by exploring, the way a sect should be found rather than summoned.

Sects want the relief that villages avoid (peaks, cliffs, high altitude), and suitable natural terrain is rare and uncooperative — a found peak rarely matches the compound's terrace profile, leaving terraces floating or buried. So rather than hunt for a peak, this change has the generator **derive the mountain from the compound's terrace profile (反推山形)**: the terraces are fixed first, then the mountain is filled in beneath and around them and blended into the natural terrain. The compound stops adapting to terrain; the terrain adapts to the compound.

## What Changes

- **Register a custom `Structure` type for the sect**, sited during chunk generation (no force-load), spaced as a rare per-region landmark, gated to mountainous/high-relief biomes, and reproducible per world seed. The structure bakes into the chunk so there is no build pop-in.
- **Derive the mountain from the terrace profile (反推山形).** Take the terrace skeleton + geometry parameters exported by `build-sect-compound`, treat the terrace elevations as the mountain's skeleton, fill the slopes beneath and between terraces with seed-driven noise, and resolve an outer **blend skirt** that grades the man-made relief into the surrounding natural heightmap so there is no cut-off edge. The man-made body is solid built stone (structures place after terrain/surface, so no caves/ore intrude — fitting for a 仙山).
- **Place a cliff-back face** behind the summit terrace so the principal hall backs sheer rock and looks out over the drop.
- **Place a manual cloud-sea / fog surface.** A horizontal translucent "云海面" sheet (white/tinted glass) is laid at a configured Y between the gate and disciple terraces so the upper terraces read as floating above cloud, with optional powder-snow "云絮" wisps clinging to terrace edges. This is a placed-block illusion, not volumetric fog (the chosen first-pass technique; true biome fog is out of scope).
- **Make the detached-spire flying-bridge feature a worldgen element with its solitary peak.** When the compound selects the feature, the generator raises the **solitary peak (孤峰)** under the detached volume and spans the gap with the flying bridge. The feature ships as 3 variants (inherited from `build-sect-compound`); in worldgen it appears **randomly** per site.
- **Add a force-generate command with variant selection.** Extend the sect command so an operator can force a sect at a location and specify the detached-spire variant (or none), for review and testing, independent of random worldgen rolls. Add `/locate` support so worldgen sects are findable.

## Capabilities

### New Capabilities
- `sect-worldgen-structure`: The mod SHALL register a custom sect `Structure` that sites a terraced sect compound during chunk generation as a rare, biome-gated, world-seed-reproducible landmark that bakes into terrain without build pop-in, is locatable via `/locate`, and can be force-generated at a location with a chosen detached-spire variant.
- `sect-mountain-derivation`: The generator SHALL derive the sect's mountain from the compound's terrace profile (反推山形) — terrace elevations as skeleton, noise-filled slopes, an outer blend skirt into natural terrain, a cliff-back face behind the summit, a manually-placed cloud-sea/fog surface, and a raised solitary peak under the detached-spire feature — all seed-deterministic.

### Modified Capabilities
- `sect-compound-realization`: Extend the sect command with a worldgen-style force-generate mode that selects the detached-spire variant (or none) and lets the structure be placed on derived terrain; the existing `/myvillage sect [seed]` on-the-spot behavior is preserved.

## Impact

- Code (worldgen): new `src/main/java/com/example/myvillage/sect/SectStructure.java` (+ `StructureType` registration, `findGenerationPoint`, `generatePieces`) and a `StructurePiece`/feature that writes terrain; structure-set/placement + biome-tag data under `src/main/resources/data/myvillage/worldgen/` and `tags/`.
- Code (mountain): a derivation module turning the exported terrace profile into a heightfield (skeleton + noise + blend skirt + cliff-back), reusing `SectGenerator`'s compound placement on top; cloud-sea/fog placement pass.
- Code (command): extend the `/myvillage sect` branch in `MyVillageMod` with a force-generate/variant argument; register the `/locate` entry.
- Assets: cloud-sea uses vanilla translucent blocks (white/tinted glass) + powder snow; no new authored `.nbt`.
- Specs: new `sect-worldgen-structure`, `sect-mountain-derivation`; `sect-compound-realization` (from `build-sect-compound`) extended.
- Validation/reports: worldgen-siting + mountain-blend + reproducibility checks added to `reports/sect_generation_validation.json`; a worldgen preview/seed-survey added to the preview aggregate.
- Docs: `README.md` (worldgen behavior, force-generate command, `/locate`), `AGENTS.md`, `CHANGELOG.md`, and `META-INF/neoforge.mods.toml` updated; note the mod now registers worldgen (the "No worldgen is registered" statement is removed).
- Depends on: `build-sect-compound` (consumes its terrace profile and assembler). Independent of all town work; town worldgen is a later change.
