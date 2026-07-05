# Visual Reference Structure Pipeline

This note is the **factual technical memory** for the visual-reference
decomposition workflow. The normative contract lives in the
`visual-reference-structure-pipeline` capability spec added by change
[`add-visual-reference-structure-pipeline`](../../openspec/changes/add-visual-reference-structure-pipeline/proposal.md).
Read this page to learn *how the workflow operates*; read the spec for *what
the workflow must guarantee*.

See also: [19_genops.md](19_genops.md), [genops spec](../../openspec/specs/genops/spec.md),
[huipai-tianjing-mansion spec](../../openspec/specs/huipai-tianjing-mansion/spec.md),
[research/source_structures/README.md](../../research/source_structures/README.md).

## Purpose

`research/source_structures/` is a **local research library**, not an
import-ready structure library. It preserves source facts (license, source URL,
attribution, usage decision) but contains **no** original `.nbt`, `.schem`,
`.litematic`, or world-save assets. The useful operation is therefore
**architectural translation**: observe a reference, decide what kind of project
artifact it can inform, and route that result into OpenSpec/CRAFT work — without
copying third-party geometry.

The visual-reference pipeline is the **middle layer** between "this looks
useful" and "edit the generator". It produces a human-readable
**Reference Breakdown Contract** before any generator, NBT, Java runtime, or
version-metadata edit starts.

## Non-goals

- **No** single-image-to-NBT automation.
- **No** direct copying of third-party structures into shipped resources.
- **No** generator implementation of Hui-style mansions, pagodas, moon gates,
  or other reference-derived forms in this change.
- **No** replacement for OpenSpec specs, style slots, form registries,
  validators, preview generation, or manual visual acceptance.
- **No** new distributed service, daemon, queue, or external vision dependency.

`local_research` candidates are source facts, not permission to copy or
redistribute. The breakdown preserves the source fact layer separately from the
decomposition decisions.

## The Four Buckets

Every Reference Breakdown Contract uses **exactly four** typed buckets. Each
bucket routes to a different downstream implementation path:

| Bucket | Meaning | Typical downstream route |
|---|---|---|
| `direct_component` | A reusable bounded prefab or NBT candidate | Prefab library / structure export |
| `atomic_component` | A small reusable form/motif/roof/wall/bridge/gate operation | `form-registry`, `chinese-vernacular-roof-vocabulary`, `mod-decor-motif` |
| `generative_grammar` | A planner/layout/routing/proportion rule | Compound planner, `chinese-mansion-compound`, future `rebuild-huipai-mansion` |
| `calibration_only` | A visual judgment reference, not an asset or rule | Rubric tuning, `genops/rubrics/`, `genops/style_bibles/` |

One observed feature may appear in multiple buckets **only when** each entry has
a distinct route and rationale. A single "useful reference" label is rejected
because it does not say *how* the sample enters the generator.

## Reference Breakdown Contract shape

A breakdown card is a JSON artifact under
`research/source_structures/<candidate>/breakdown.json` with the following
shape (full schema documents the contract; this is the readable summary):

```text
{
  "candidate_id":     "candidate_003",
  "source_facts":     { id, title, source_url, license, usage_decision, attribution_path },
  "observations":     [ { cue, evidence_pointer, note } ],
  "direct_component": [ { cue, rationale, downstream_route, review_needed } ],
  "atomic_component": [ { cue, rationale, downstream_route, review_needed } ],
  "generative_grammar":[ { cue, rationale, downstream_route, review_needed } ],
  "calibration_only": [ { cue, visual_question, review_needed } ],
  "verdict_state":    "pending" | "accepted" | "rejected" | "accepted_with_changes"
}
```

Required invariants (enforced by the lightweight fixture in
`tools/buildgen/probes/check_reference_breakdown.py`):

- `source_facts.usage_decision` is preserved verbatim from the candidate
  manifest; the breakdown SHALL NOT upgrade or downgrade it.
- Every non-empty `direct_component`, `atomic_component`, or
  `generative_grammar` entry SHALL carry a non-empty `downstream_route`.
- `calibration_only` entries carry a `visual_question` (the question it
  calibrates) and SHALL NOT declare a prefab/form/planner route.
- `verdict_state` is recorded separately from task pass/fail status.

## CRAFT routing

The owner-facing path is **Commander conversation**, not CLI typing:

```text
用 CRAFT 拆解 candidate_003 这个徽派参考建筑。
继续上次 reference-decomposition run，把 breakdown 卡做出来。
这张分解我不接受，马头墙那一条理由太弱；记录 verdict 后再改。
```

The Commander infers the `reference-decomposition` pipeline from natural-language
cues such as `视觉参考`, `参考建筑`, `拆解`, `source_structures`, `candidate_NNN`,
and `reference decomposition`. The owner should not need to choose pipeline YAML
paths or task ids.

The pipeline lives at `genops/pipelines/reference-decomposition.full.yaml` with
a seven-task DAG (intake-reference → draft-observations → classify-buckets →
check-openspec-routes → write-breakdown-card → visual-evidence-pointers →
record-verdict-state). The write task is scoped to `research/source_structures/**`
and explicitly forbids generator, NBT, Java, and version files. Its gate runs
`tools/check_reference_breakdown.py`. The pipeline's `human_review.required: true`
reflects that decomposition is planning evidence, not visual acceptance — a run
ends as `human_review_pending` until the owner records a verdict.

## Lifecycle

```text
Candidate inspected
  -> observations recorded (cue + evidence pointer)
  -> bucket classification with rationale + downstream route
  -> breakdown card written under research/source_structures/<candidate>/
  -> lightweight fixture validates required fields and source-fact preservation
  -> human verdict recorded separately from task status
  -> accepted cards feed future OpenSpec changes (e.g. rebuild-huipai-mansion)
```

Decomposition outputs **route** downstream work; they do not **implement** it.
A breakdown card may recommend a future OpenSpec change, but actual generator
edits still require their own proposal/tasks, validators, previews, and visual
acceptance. This is design decision D5 in the change's `design.md`.

## Worked example: candidate_003 (Hui-style Chinese Village House)

The first worked example lives at
`research/source_structures/candidate_003/breakdown.json` and is the canonical
demonstration of how a single reference splits across all four buckets. See
that file for the full Hui-style decomposition (马头墙, 堂—井—堂 sequence,
inward-facing closed facade, calibration criteria) and for the downstream
routes it identifies. The example is normative for what a "complete" breakdown
looks like; future candidates should match its standard.

## Acceptance boundary

Decomposition is **planning evidence**, not visual acceptance. Automated
classification, preview artifacts, or CRAFT run status SHALL NOT be treated as
final visual acceptance. A breakdown remains `verdict_state: pending` until the
owner gives an explicit human verdict, and any downstream generator work
remains separately gated by validators, preview generation, and human visual
review per the existing acceptance automation specs.
