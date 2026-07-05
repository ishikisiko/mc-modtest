## Context

`research/source_structures/candidate_003/breakdown.json` is the first worked
external-reference breakdown. It rejects direct prefab/NBT import and routes
the useful cues into original generator vocabulary: 马头墙, closed street facade,
堂—井—堂 sequence, flanking 厢房 enclosure, inward roof-water logic, and
white-wall / dark-roof palette.

The current `huipai-tianjing-mansion` spec is intentionally FUTURE-only. That
was useful while the generator had no implementation, but it now leaves the
reference pipeline without an end-to-end sample. This change makes a narrow
implementation slice rather than attempting the full future family at once.

## Goals / Non-Goals

**Goals:**

- Produce original generated `chinese_huipai_mansion_NNN` structures from the
  reference grammar.
- Make the slice visually distinct from the existing 江南 `chinese_mansion`
  garden compound: closed exterior, vertical/hall sequence, paired side wings
  around the sky-wells, no flower garden.
- Add deterministic validation so the slice cannot regress into a generic
  white-roofed courtyard.
- Keep external-reference usage clean: source facts stay in research metadata;
  shipped structures are original generated output.

**Non-Goals:**

- No import, conversion, or redistribution of third-party `.nbt`, `.schem`, or
  world data.
- No bulk decomposition or implementation of the other 29 reference candidates.
- No claim that every FUTURE requirement in `huipai-tianjing-mansion` is fully
  complete; this is a first vertical slice.
- No redesign of the existing 江南 mansion planner.

## Decisions

### D1: Separate Hui-style group and style, not a mansion variant

The slice uses a dedicated `chinese_huipai_mansion` group/style binding. This
keeps the closed/vertical 天井 language separate from the existing open/horizontal
江南 mansion, and follows the existing settlement-group extension rule instead
of branching on style-id prefixes.

### D2: Implement as a compound-library sample path

The first slice plugs into the compound library generator, parallel to other
reviewable structure families. That gives us NBT export, place functions,
gallery functions, reports, preview generation, and visual acceptance without
requiring runtime town integration first.

### D3: Use original motifs from the breakdown, not copied geometry

The 马头墙 and closed facade are generator operations derived from the cue, not
source-asset geometry. The full `candidate_003` structure remains rejected as a
direct component.

### D4: Validate recognizability before broadening scope

The validator checks the minimum load-bearing form: sequence markers for
门堂/天井/享堂/天井/寝堂, small sky-wells flanked by side-wing pairs, closed facade,
stepped gable wall, no garden parcel, and reference-source metadata in the
report. Visual acceptance still requires owner verdict after preview.

## Risks / Trade-offs

- **Risk: The slice looks like a white 江南 mansion.** Mitigation: validator and
  visual review must reject garden parcels and require closed facade / sky-well
  cues.
- **Risk: The slice reads as three detached halls.** Mitigation: require paired
  side wings and a cloister ring around each sky-well before visual acceptance.
- **Risk: The enclosure becomes visually overcrowded.** Mitigation: cap side-wing
  width, use an expanded review lot, and leave breathing room between the side
  wings and outer perimeter.
- **Risk: The halls and sky-wells are still packed too tightly along the axis.**
  Mitigation: require a minimum clear gap between each adjacent sequence element
  and use the added depth for inter-building spacing, not just a larger wall.
- **Risk: The expanded lot makes the halls read as undersized pavilions.**
  Mitigation: require minimum hall footprint area, side-wing width, and
  structure height so the building mass scales with the review lot.
- **Risk: Full 天井 roof-water physics is too large for this pass.** Mitigation:
  model the drain and inward-roof intent in metadata/geometry first; keep full
  hydrology refinements for later.
- **Risk: Adding a new family churns generated resources.** Mitigation: generate
  only a small sample count for this slice and keep existing families unchanged.
- **Risk: Source licensing is misunderstood as permission to copy.** Mitigation:
  keep `usage_decision: local_research` and require original generated output.

## Migration Plan

1. Add `chinese_huipai_mansion` group/style registration and generator entry.
2. Add the Hui-style compound generator, validation, and tests.
3. Generate the sample structures, place/gallery functions, and reports.
4. Update docs and command usage.
5. Run validation, preview, build, and stop for human visual verdict.
