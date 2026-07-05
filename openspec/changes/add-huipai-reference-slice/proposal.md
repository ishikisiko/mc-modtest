## Why

The external-reference library now has a validated `candidate_003` Hui-style
breakdown, but the generator has not yet produced any original structure from
that reference grammar. This change turns the reference into the first narrow,
verifiable implementation slice without copying third-party geometry.

## What Changes

- Add an original `chinese_huipai_mansion` / 徽派天井 sample family as the first
  external-reference-driven generated output.
- Realize the minimum recognizable cues from `candidate_003`: white wall / dark
  roof palette, closed street facade, stepped 马头墙 perimeter, and a
  堂—井—堂 hall / sky-well sequence flanked by paired side wings so it does not
  read as three freestanding halls.
- Keep the implementation deliberately narrow: no third-party NBT import, no
  bulk processing of the remaining `source_structures` candidates, and no claim
  that the full Hui-style future spec is complete.
- Add validation/reporting that distinguishes this slice from the existing open
  江南大宅 garden language.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `huipai-tianjing-mansion`: Promote a first implementation slice from FUTURE
  vocabulary to generated, validated sample output.
- `settlement-group`: Add a documented family binding for the new
  `chinese_huipai_mansion` generated sample group.
- `form-registry`: Add the reference-derived stepped-gable / closed-facade form
  hooks through registry-based vocabulary rather than string-prefix dispatch.
- `validation`: Add acceptance checks for the Hui-style reference slice.

## Impact

- Affected generator/docs: `tools/buildgen/**`, `tools/generate_compound_library.py`,
  `docs/ai-kb/**`, `README.md`.
- Affected resources: generated `src/main/resources/data/myvillage/structure/`
  outputs and related place/gallery functions for the new sample family.
- Affected validation: buildgen tests plus generated-structure and visual
  acceptance reports.
- No direct reuse or redistribution of third-party structure assets from
  `research/source_structures/`.
