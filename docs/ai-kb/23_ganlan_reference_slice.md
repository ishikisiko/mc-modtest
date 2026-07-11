# Ganlan Stilted-House Reference Slice

`ganlan_stilted_house` is the second implemented output from the external
building-reference pipeline. It uses
`research/source_structures/candidate_005/breakdown.json` as visual grammar
only: the source remains `local_research`, and shipped `.nbt` resources are
generated original output.

Generated resources:

```text
src/main/resources/data/myvillage/structure/ganlan_stilted_house_001.nbt
src/main/resources/data/myvillage/structure/ganlan_stilted_house_002.nbt
src/main/resources/data/myvillage/function/gallery/ganlan_stilted_house.mcfunction
src/main/resources/data/myvillage/function/place/ganlan_stilted_house_001.mcfunction
src/main/resources/data/myvillage/function/place/ganlan_stilted_house_002.mcfunction
reports/ganlan_stilted_house_compound_library_report.json
```

The implemented slice is intentionally narrow: it represents a humid,
fully-elevated subtype rather than every regional or ethnic Ganlan form. It
validates the cues that make this subtype distinct from ordinary wooden houses,
Huipai closed-courtyard mansions, and Jiangnan garden compounds:

- occupied bamboo/wood floor is raised above the support plane;
- visible, bay-aligned support-post grid and underfloor tie beams reach from the
  underside to ground/water contact;
- underside remains mostly open rather than becoming a filled pedestal;
- raised veranda/deck edge has rail or trapdoor cues and a lower rain canopy;
- an offset entry stair reaches the veranda before the central door, so the
  veranda reads as circulation rather than a decorative ledge;
- the main gable and lower canopy form two rain-shelter roof layers;
- dark bay framing and lighter infill make the column-and-tie rhythm legible;
- wet-ground/waterside context passes beneath part of the floor without turning
  the review lot into a full settlement;
- provenance states `candidate_005`, `local_research`, generated original
  output, and no copied source assets.

Generate it directly:

```bash
python3 tools/generate_compound_library.py --group ganlan_stilted_house --count 2 --base-seed 20260708
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
python3 tools/buildgen/tests/test_ganlan_stilted_house.py
```

Review it in-game:

```mcfunction
/myvillage place ganlan_stilted_house_001
/myvillage place ganlan_stilted_house_002
/function myvillage:gallery/ganlan_stilted_house
```

The owner accepted these two generated samples as the narrow Ganlan visual
slice on 2026-07-11 after automated validators and preview evidence passed.
That verdict does not complete a broader Ganlan village, jigsaw pool, biome
placement, or worldgen integration; those remain separate future scopes.

## See also

- Change spec: `add-ganlan-stilted-house`
- Spec: [settlement-group](../../openspec/specs/settlement-group/spec.md)
- Spec: [form-registry](../../openspec/specs/form-registry/spec.md)
- Spec: [resource-export](../../openspec/specs/resource-export/spec.md)
- Spec: [validation](../../openspec/specs/validation/spec.md)
- KB: [Visual Reference Structure Pipeline](20_visual_reference_structure_pipeline.md)
- KB: [Validation Checklist](09_validation_checklist.md)
