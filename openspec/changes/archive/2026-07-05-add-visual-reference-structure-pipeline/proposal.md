## Why

The project now has a local reference library under `research/source_structures/`,
but there is no contract for turning visual references into project-consumable
building work. Without that contract, future reference use will repeatedly
re-litigate whether a sample should become a direct prefab, a reusable atomic
form, a generator grammar rule, or visual calibration only.

This change adds a narrow visual-reference decomposition pipeline before any
generator implementation. It lets CRAFT/GenOps plan reference-driven work with
bounded artifacts and human judgment, without pretending that one image can
automatically become a finished Minecraft structure.

## What Changes

- Introduce a `visual-reference-structure-pipeline` capability for decomposing
  visual references and local research candidates into a typed breakdown
  contract.
- Define the required Reference Breakdown Contract buckets:
  `direct_component`, `atomic_component`, `generative_grammar`, and
  `calibration_only`.
- Add CRAFT/GenOps routing for visual-reference decomposition, including a new
  reference-decomposition planning pipeline and commander intent cues.
- Add a first worked example for `research/source_structures/candidate_003`
  (Hui-style Chinese Village House), routing its features into the four buckets
  without implementing the building yet.
- Add documentation for how visual references move from observation to OpenSpec
  proposals, generator tasks, validation expectations, and human visual review.
- Explicitly declare non-goals: no direct copying of third-party structures, no
  promise of single-image automatic NBT generation, no bypass of existing style
  slots, form registries, validators, preview evidence, or human verdicts.

## Capabilities

### New Capabilities

- `visual-reference-structure-pipeline`: Defines how visual references and local
  research candidates are decomposed into direct components, atomic components,
  generative grammar, and calibration-only evidence before implementation work.

### Modified Capabilities

- `genops`: Adds CRAFT/GenOps routing and pipeline requirements for
  visual-reference decomposition runs.

## Impact

- Affected docs: `docs/ai-kb/INDEX.md`, a new visual-reference pipeline KB note,
  and possibly `CRAFT.md` / `genops/README.md`.
- Affected OpenSpec specs: new `visual-reference-structure-pipeline` spec and a
  delta to `genops`.
- Affected GenOps/CRAFT files: `genops/commander.yaml`,
  `genops/pipelines/reference-decomposition.full.yaml`, and supporting schema or
  task contract documentation if needed.
- Affected research docs: `research/source_structures/README.md` may point to
  the new decomposition workflow, but existing source metadata remains factual.
- No Java runtime behavior, shipped NBT resources, block registries, or version
  metadata are changed by the proposal itself.
