## Context

`research/source_structures/candidate_005/breakdown.json` records Ganlan / 干栏式
reference cues as planning evidence. It rejects direct prefab import and routes
the useful language into original generator work: raised living floor, stilt
posts, raised veranda, deep rain-shelter roof, warm bamboo/wood palette,
wet-ground decor, and water/terrain-aware siting.

The current generator already has a group/style layer, BlockGrid export,
compound-library sampling, place/gallery functions, preview generation, and
validation patterns proven by the Hui-style reference slice. This change reuses
that delivery path for a new narrow sample family rather than introducing
worldgen, jigsaw pools, villagers, or a generic third-party importer.

## Goals / Non-Goals

**Goals:**

- Produce original generated `ganlan_stilted_house_NNN` structure resources from
  the candidate_005 grammar.
- Make the first slice visually legible as stilt architecture: raised floor,
  open underside, structural posts reaching terrain/water, access stair, raised
  veranda edge, and deep protective eaves.
- Keep reference provenance explicit while ensuring shipped structures are
  original project output.
- Add deterministic validation and focused tests before broader settlement or
  region integration.
- Prepare preview and manual visual-review evidence for owner verdict.

**Non-Goals:**

- No direct copy/import of upstream Ganlan `.nbt` files.
- No generic NBT/datapack/schematic importer.
- No full Ganlan village jigsaw replication, villagers/entities, loot, or
  biome worldgen resources.
- No final region placement or runtime town integration in the first slice.
- No claim that the visual style is accepted before owner visual verdict.

## Decisions

### D1: Implement a narrow generated sample family first

The first deliverable is `ganlan_stilted_house`, a small generated structure
family suitable for offline preview and in-game placement. This is preferable
to copying the upstream jigsaw village because the project needs reusable
generator grammar, not a third-party village transplant.

### D2: Treat stilts as terrain-aware grammar, not decoration

The raised-floor rule owns `raised_floor_y`, `post_to_ground`, `entry_stair`,
and `underside_clearance` metadata. Validation uses those values to ensure the
house does not become a normal wooden building placed on a filled pedestal.

### D3: Route motifs through existing extension points

Support posts, veranda rails, and deep eaves are authored as explicit form
operations or metadata-driven generator helpers. The implementation SHALL NOT
infer Ganlan behavior by matching `style_id` prefixes.

### D4: Use compound-library export for review

The slice plugs into the existing compound/library review path so it gets NBT
export, place functions, a gallery function, reports, previews, and validation
without requiring runtime worldgen first.

### D5: Preserve source facts separately from generated output

Reports and metadata reference `candidate_005` as `local_research`, but no
shipped structure resource is copied from `research/source_structures/` or the
upstream datapack.

## Risks / Trade-offs

- **Risk: It reads as a generic wooden hut.** Mitigation: validation requires
  raised floor, open underside, post grid, veranda/access cues, and source
  provenance.
- **Risk: It reads as floating rather than supported.** Mitigation: post
  validation checks support contact and underside openness together.
- **Risk: Deep eaves overpower small volumes.** Mitigation: generate at least
  one small and one medium review variant and inspect roof/body proportion.
- **Risk: Wet-ground context becomes clutter.** Mitigation: keep pond/plant/decor
  motifs sparse in the first slice and treat atmosphere as visual calibration.
- **Risk: The slice is mistaken for final village worldgen.** Mitigation: docs
  and reports call it a generated sample family; settlement placement remains a
  later delivery.

## Migration Plan

1. Add `ganlan_stilted_house` group/style registration and generator entry.
2. Implement the stilt-house sample generator, validation, and focused tests.
3. Generate sample structures, place/gallery functions, and reports.
4. Update README, KB, and acceptance docs for review commands.
5. Run focused tests, generated-resource validation, previews, and build; stop
   for owner visual verdict.
