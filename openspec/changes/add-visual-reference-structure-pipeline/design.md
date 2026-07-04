## Context

`research/source_structures/` is a local research library, not an import-ready
structure library. Its candidates preserve source facts and priority notes, but
the directory intentionally contains no original `.nbt`, `.schem`, `.litematic`,
or world-save assets. The useful operation is therefore architectural
translation: observe a reference, decide what kind of project artifact it can
inform, and route that result into OpenSpec/CRAFT work.

The repo already has three pieces this change should reuse:

- OpenSpec for capability contracts and implementation tasks.
- CRAFT/GenOps for Commander-routed, artifact-first generator workflows.
- Visual preview and human-verdict conventions for appearance-sensitive work.

The gap is the middle layer: a stable decomposition contract between "this
looks useful" and "edit the generator".

## Goals / Non-Goals

**Goals:**

- Define a Reference Breakdown Contract that classifies each useful reference
  into `direct_component`, `atomic_component`, `generative_grammar`, and
  `calibration_only` outputs.
- Add CRAFT/GenOps routing so a user can ask for visual-reference decomposition
  in natural language and receive a bounded planning run with artifacts.
- Add a first worked example for `candidate_003` so future decompositions have a
  concrete standard.
- Keep the pipeline preview-first and human-verdict-gated before generator
  edits, especially for visual/aesthetic structure changes.
- Preserve source facts without treating local research metadata as permission
  to copy or redistribute third-party structures.

**Non-Goals:**

- No single-image-to-NBT automation.
- No direct copying of third-party structures into shipped resources.
- No generator implementation of Hui-style mansions, pagodas, moon gates, or
  other reference-derived forms in this change.
- No replacement for existing OpenSpec specs, style slots, form registries,
  validators, preview generation, or manual visual acceptance.
- No new distributed service, daemon, queue, or external vision dependency.

## Decisions

### D1: Decomposition uses four typed buckets

Every reference breakdown uses exactly four buckets:

```text
direct_component     reusable bounded prefab or NBT candidate
atomic_component     small reusable form/motif/roof/wall/bridge/gate operation
generative_grammar   planner/layout/routing/proportion rule
calibration_only     visual judgment reference, not an asset or rule
```

This is better than a single "useful reference" label because each bucket routes
to a different implementation path. A moon gate segment may become an atomic
motif; a pagoda silhouette may become both a direct component and a tower
grammar; a whole Hui-style mansion should mostly become grammar, not a copied
prefab.

Alternative considered: classify references only by source type or license.
Rejected because source status does not say how the sample should enter the
generator. It remains source metadata, not the decomposition decision.

### D2: The pipeline is artifact-first, not automation-first

The first implementation should add a CRAFT planning pipeline and a schema-like
contract for breakdown cards. The pipeline may materialize run evidence and task
prompts before any code changes, mirroring existing GenOps planning passes.

Alternative considered: build a vision tool that extracts geometry from images.
Rejected as too early. The project first needs a stable human-readable
intermediate representation and route decisions.

### D3: Worked examples are mandatory

The pipeline should ship with at least one worked example, starting with
`candidate_003` (Hui-style Chinese Village House). That example should show:

- what can be considered a direct component, if any;
- which parts are atomic components such as 马头墙 or door/wall forms;
- which parts are generative grammar such as 堂--井--堂 sequence and closed
  inward-facing facade behavior;
- which parts are calibration-only visual criteria.

Alternative considered: define only the abstract schema. Rejected because the
bucket boundaries are visual and easy to misapply without a grounded example.

### D4: CRAFT routing should stay Commander-facing

The owner should be able to say "用 CRAFT 拆解这个参考建筑" and the Commander
should pick the reference-decomposition pipeline. The owner should not need to
choose pipeline YAML paths or task ids.

Alternative considered: expose the backend command as the primary workflow.
Rejected because it violates the existing GenOps user-facing model.

### D5: Decomposition outputs route downstream work; they do not implement it

A breakdown card may recommend a future OpenSpec change, such as
`rebuild-huipai-mansion`, but it must not count as implementation. Actual
generator edits still require their own proposal/tasks, validators, previews,
and visual acceptance.

Alternative considered: let the decomposition pipeline directly edit generator
code when it sees a strong reference. Rejected because visual alignment must
precede generator edits in this repo.

## Risks / Trade-offs

- **Risk: The pipeline becomes paperwork.** Mitigation: require one worked
  example and downstream route fields for every non-empty bucket.
- **Risk: Users assume local research means permission to copy.** Mitigation:
  preserve source facts, separate local observation from redistribution, and
  make "copy external structure" a non-goal.
- **Risk: Four buckets overlap.** Mitigation: allow one observed feature to
  appear in multiple buckets only when each entry has a distinct route and
  rationale.
- **Risk: CRAFT planning feels like implementation progress.** Mitigation:
  status and task language must say whether outputs are planning evidence,
  docs, schemas, or implementation patches.
- **Risk: The first example overfits Hui-style mansions.** Mitigation: design
  the contract generically and list follow-up examples for pagoda, temple, moon
  gate, bridge, and residential variation candidates.
