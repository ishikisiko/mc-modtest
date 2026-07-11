## Why

`candidate_005` is now decomposed into a reference contract, but it is not yet
represented by original project output. The next useful deliverable is a narrow,
verifiable Ganlan / 干栏式 stilt-house sample family that proves the reference
pipeline can create terrain-aware architecture without copying upstream NBT.

## What Changes

- Add an original `ganlan_stilted_house` generated sample family derived from
  the `candidate_005` breakdown grammar.
- Realize the minimum recognizable cues: raised timber living floor, stilt-post
  supports reaching terrain/water, raised veranda edge, deep rain-shelter gable
  roof, warm bamboo/wood palette, and wet-ground context.
- Keep the first slice deliberately narrow: no direct third-party NBT import, no
  full jigsaw village copy, no villagers/entities, and no runtime worldgen
  placement.
- Add validation/reporting that distinguishes the slice from generic wooden
  houses, Huipai white-wall compounds, and Jiangnan garden mansions.
- Generate small reviewable structure resources, placement functions, docs, and
  preview/acceptance evidence.

## Capabilities

### New Capabilities

- `ganlan-stilted-house`: Defines the original Ganlan stilt-house reference
  slice, its required raised-floor cues, terrain/water-adaptive support grammar,
  and partial-implementation acceptance boundary.

### Modified Capabilities

- `building-generation`: Add a raised-floor stilt-house archetype path with
  support-post, veranda, stair, and deep-eave metadata.
- `settlement-group`: Add a documented `ganlan_stilted_house` group binding
  with a dedicated style profile and roster.
- `form-registry`: Add registry-backed raised-floor support, veranda-edge, and
  deep-eave motifs rather than style-prefix dispatch.
- `resource-export`: Include the Ganlan sample family in generated resources,
  place functions, gallery function, and canonical generation.
- `validation`: Add checks for raised floor, support posts, access stair,
  underside openness, source provenance, and original-generated status.

## Impact

- Affected generator/docs: `tools/buildgen/**`, `tools/generate_compound_library.py`,
  `tools/generate_all_structures.py`, `docs/ai-kb/**`, `README.md`.
- Affected resources: generated `src/main/resources/data/myvillage/structure/`
  outputs and related place/gallery functions for the Ganlan sample family.
- Affected validation: focused buildgen tests, compound/library validation,
  preview generation, and visual acceptance reports.
- No direct reuse or redistribution of third-party Ganlan structure assets from
  `research/source_structures/` or the upstream datapack.
