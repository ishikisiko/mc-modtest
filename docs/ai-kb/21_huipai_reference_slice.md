# Hui-Style Reference Slice

`chinese_huipai_mansion` is the first implemented output from the external
building-reference pipeline. It uses
`research/source_structures/candidate_003/breakdown.json` as visual grammar only:
the source remains `local_research`, and shipped `.nbt` resources are generated
original output.

Generated resources:

```text
src/main/resources/data/myvillage/structure/chinese_huipai_mansion_001.nbt
src/main/resources/data/myvillage/structure/chinese_huipai_mansion_002.nbt
src/main/resources/data/myvillage/function/gallery/chinese_huipai_mansion.mcfunction
src/main/resources/data/myvillage/function/place/chinese_huipai_mansion_001.mcfunction
src/main/resources/data/myvillage/function/place/chinese_huipai_mansion_002.mcfunction
reports/chinese_huipai_mansion_compound_library_report.json
```

The implemented slice is intentionally narrow. It validates the cues that make
the family distinct from the Jiangnan `chinese_mansion` family:

- closed white street-facing facade with one primary entry;
- dark roof palette;
- stepped 马头墙 / fire-wall cue above the roof edge, with two-cell visual
  thickness, dark coping, and short return-wall hints so it does not read as a
  flat white plate;
- 门堂 → 天井一 → 享堂 → 天井二 → 寝堂 sequence;
- two small sky-wells, each no larger than six cells in either horizontal
  dimension;
- paired side-wing / 厢房 massing and cloister rings flanking both sky-wells,
  so the slice reads as an enclosed mansion rather than three detached halls;
- restrained side-wing width that leaves side-yard breathing room inside the
  outer wall;
- expanded review-lot footprint, now `47x76` / `43x72`, keeping the 天井 small
  while giving the mansion enough side and depth clearance to avoid a cramped
  roof field;
- minimum clear gaps between adjacent hall / sky-well sequence elements, so the
  three-in plan breathes instead of becoming tightly stacked rows;
- minimum hall footprint area, side-wing width, and 16-block structure height,
  so the expanded lot does not make the buildings read as undersized pavilions;
- no 花园, garden pavilion, pond, or rockery garden parcel.

Generate it directly:

```bash
python3 tools/generate_compound_library.py --group chinese_huipai_mansion --count 2 --base-seed 20260619
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/buildgen/tests/test_huipai_reference_slice.py
```

Review it in-game:

```mcfunction
/myvillage place chinese_huipai_mansion_001
/myvillage place chinese_huipai_mansion_002
/function myvillage:gallery/chinese_huipai_mansion
```

This slice does not complete the broader FUTURE Hui-style vocabulary. It remains
partial until automated validators, preview evidence, and owner visual verdict
all pass.

## See also

- Spec: [huipai-tianjing-mansion](../../openspec/specs/huipai-tianjing-mansion/spec.md)
- Spec: [settlement-group](../../openspec/specs/settlement-group/spec.md)
- Spec: [form-registry](../../openspec/specs/form-registry/spec.md)
- Spec: [validation](../../openspec/specs/validation/spec.md)
- KB: [Visual Reference Structure Pipeline](20_visual_reference_structure_pipeline.md)
- KB: [Validation Checklist](09_validation_checklist.md)
