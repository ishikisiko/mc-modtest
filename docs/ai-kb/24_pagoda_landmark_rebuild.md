# Pagoda Landmark Rebuild

`pagoda_001..003` are the rebuilt cultivation-town vertical landmarks delivered
by change `rebuild-pagoda-landmark`. They retain the existing resource ids and
town/sect placement roles, but their generator grammar is no longer the old
three-storey box plus doubled top roof.

## Profile Set

The three tiers are deterministic:

| resource | body | storeys | inset schedule | intent |
|---|---:|---:|---|---|
| `pagoda_001` | `15x15` | 5 | `0,0,1,1,2` | compact town landmark |
| `pagoda_002` | `19x19` | 5 | `0,0,1,2,3` | broad monumental tower |
| `pagoda_003` | `17x17` | 7 | `0,0,1,1,2,2,3` | slender high landmark |

Every profile owns its platform pad, storey height, eave projection, crown
overhang, and finial height. The current generated resource sizes are:

```text
pagoda_001  19x37x21
pagoda_002  27x46x29
pagoda_003  23x56x25
```

## Generator Grammar

- Upper wall bodies follow a non-decreasing inset schedule with at least two
  real reductions.
- Every occupied-storey boundary below the crown has a shallow two-band eave
  skirt, under-eave brackets, and four lifted corners.
- Upper storeys regain centered framed openings after the inset pass rebuilds
  their wall planes.
- The registered `pagoda` roof emits only the top-storey pyramidal crown and
  profile-controlled finial.
- The ground-storey colonnade terminates at the first-storey eave instead of
  stretching as uninterrupted posts to the crown.
- Protected stair openings and landings remain aligned through all five or
  seven storeys.
- Paired scripture-terrace flank slots intentionally select compact
  `pagoda_001`; larger profiles remain available for direct placement and
  detached-spire features where their full bounds fit.

`candidate_006` remains `local_research` and is recorded as
`calibration_only`. It informed the exterior tower rhythm; no external NBT,
datapack layout, palette, or geometry is shipped.

## Validation

The building report records profile signature, storey count, inset reductions,
eave levels/cells, lifted corners, brackets, crown type, finial cells, height,
maximum span, height-to-width ratio, and reference provenance. The pagoda
library gate requires three unique signatures and NBT hashes, at least eight
blocks of height spread, and one ratio of at least `2.0`.

Run the focused and integrated checks from the repository root:

```bash
python3 tools/buildgen/tests/test_pagoda_landmark.py
python3 tools/generate_building_library.py --group cultivation_town --count 3 --base-seed 20260613
python3 tools/validate_town_generation.py
python3 tools/validate_runtime_town_plan.py
```

Review in game with the unchanged commands:

```mcfunction
/myvillage place pagoda_001
/myvillage place pagoda_002
/myvillage place pagoda_003
/function myvillage:gallery/cultivation_town
```

Automated validation confirms geometry and integration, not final appearance.
The rebuilt family remains visually pending until the owner reviews the new
previews.

## See Also

- Change: `rebuild-pagoda-landmark`
- Spec: [vertical-landmark](../../openspec/specs/vertical-landmark/spec.md)
- Spec: [cultivation-massing-grammar](../../openspec/specs/cultivation-massing-grammar/spec.md)
- Spec: [validation](../../openspec/specs/validation/spec.md)
- KB: [Visual Reference Structure Pipeline](20_visual_reference_structure_pipeline.md)
- KB: [Validation Checklist](09_validation_checklist.md)
