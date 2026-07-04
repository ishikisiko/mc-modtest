## Context

CRAFT/GenOps already defines a natural-language Commander, scoped worker roles,
pipelines, patch guards, gates, run manifests, and human verdicts. The current
problem is not missing process vocabulary; it is that the vocabulary is
advisory. An agent can still treat OpenSpec as the user-facing workflow, write a
proposal directly, and only later describe what happened. That bypass loses the
central CRAFT properties: worker ownership, task contracts, run evidence, and
explicit verdict state.

This change makes CRAFT a real front door for high-impact work. OpenSpec stays
the capability contract, but CRAFT becomes the required process layer for
authoring new OpenSpec changes, applying changes, making visual/aesthetic
structure edits, coordinating workers, and preparing release or acceptance
handoffs.

There is one unavoidable bootstrap issue: the OpenSpec-change CRAFT pipeline
does not exist yet. This change itself is therefore authored through a bounded
bootstrap exception. Once this change lands, that exception closes and future
OpenSpec proposal work must go through `openspec-change.full`.

## Goals / Non-Goals

**Goals:**

- Define a machine-checkable and human-readable list of CRAFT-required intent
  classes.
- Add `openspec-change.full` so OpenSpec proposal/design/spec/tasks artifacts
  are produced through CRAFT worker tasks, not directly by the Commander.
- Add provenance checks for protected paths so bypasses are visible in local
  validation and review.
- Require Commander summaries to include run id, pipeline, worker/task,
  artifacts, gates, verdict state, and next decision for CRAFT-required work.
- Document the one-time bootstrap exception and its closure condition.
- Make the existing `add-visual-reference-structure-pipeline` change a later
  downstream item that should be re-entered through the new front door.

**Non-Goals:**

- No distributed scheduler, daemon, server, queue, or external service.
- No change to Minecraft runtime behavior or generated structure content.
- No requirement that trivial read-only repo checks use CRAFT.
- No automatic spawning of subagents for every task; worker roles may be
  represented by scoped task contracts unless the owner authorizes subagents.
- No replacement for OpenSpec. OpenSpec remains the normative spec system;
  CRAFT governs how work reaches it.

## Decisions

### D1: CRAFT-required is an explicit intent class, not a vibe

The Commander will classify work as CRAFT-required when the owner explicitly
invokes CRAFT/GenOps or when the requested action is high-impact:

```text
explicit CRAFT/GenOps request
new OpenSpec change or proposal
OpenSpec apply / implementation work
visual or aesthetic structure change
multi-worker, subagent, or parallel work
release, version, jar, build handoff
acceptance or visual-review handoff
```

When an intent is CRAFT-required, the Commander must start from a GenOps run or
an existing run manifest. It must not directly write OpenSpec artifacts, code,
generated NBTs, release metadata, or docs as an unscoped action.

Alternative considered: leave this as prose guidance in `CRAFT.md`. Rejected
because advisory language already failed. The rule needs spec coverage and a
local checker.

### D2: OpenSpec authoring gets its own CRAFT pipeline

Add `genops/pipelines/openspec-change.full.yaml` with role-scoped tasks:

```text
intake-goal
  -> map-current
  -> check-existing-changes
  -> plan-capabilities
  -> write-openspec-artifacts
  -> review-frontdoor-evidence
```

Suggested ownership:

- `context-cartographer`: current active changes, docs/spec map, relevant
  files.
- `spec-guardian`: whether to create a new change, modify an existing change,
  or pause due to conflict.
- `pipeline-architect`: scope, capabilities, non-goals, and acceptance gates.
- `docs-steward`: proposal/design/spec/tasks artifact writing.
- `manager` or `regression-steward`: front-door evidence and checks.

Alternative considered: reuse `building-library.full` or another existing
pipeline for OpenSpec work. Rejected because that misrepresents ownership and
allowed paths.

### D3: Provenance checking is a local guardrail, not a perfect proof

Add a lightweight checker, likely `tools/genops/check_frontdoor.py`, that
examines changed files and fails or warns when protected paths were modified
without a matching GenOps run manifest and task evidence.

Protected paths include:

```text
openspec/changes/**
openspec/specs/**
docs/ai-kb/**
genops/**
tools/buildgen/**
src/main/**
src/main/resources/data/myvillage/structure/*.nbt
gradle.properties
CHANGELOG.md
README.md
```

The checker should accept an explicit `--run-id` or read an evidence index. It
does not need to prove authorship cryptographically; it needs to make bypasses
visible and reviewable.

Alternative considered: rely only on patch guard inside each GenOps run.
Rejected because bypassed work never reaches patch guard.

### D4: Commander reporting becomes structured for CRAFT-required work

For CRAFT-required work, final user-facing summaries must include:

```text
run_id
pipeline
worker/task ownership
artifacts produced or changed
gates run/skipped
human verdict state
next decision
```

This prevents summaries that only report `openspec status` or raw command
success while hiding the process route.

Alternative considered: keep summaries flexible. Rejected for governed work;
casual tasks can stay flexible, but CRAFT-required work needs a minimum audit
shape.

### D5: Bootstrap exception is narrow and self-extinguishing

This change is allowed to be created without the future `openspec-change.full`
pipeline because it creates that pipeline. The exception is limited to:

- `openspec/changes/enforce-craft-frontdoor-governance/**`
- proposal/design/spec/tasks artifacts for this governance change
- no implementation code, no generated resources, no release metadata

After `openspec-change.full` and the front-door checker land, future new
OpenSpec changes must go through CRAFT. Existing bypass-created changes, such
as `add-visual-reference-structure-pipeline`, should be re-entered through
`openspec-change.full` before implementation continues.

Alternative considered: pause and first create the CRAFT pipeline by hand.
Rejected because that would still be an ungoverned implementation. A bounded
planning bootstrap is clearer and easier to audit.

## Risks / Trade-offs

- **Risk: Governance slows small work.** Mitigation: explicitly exempt trivial
  read-only checks, status queries, and direct answers.
- **Risk: Agents game the checker by creating empty run manifests.**
  Mitigation: require task evidence, touched artifacts, and Commander summary
  consistency, not only directory existence.
- **Risk: The first bootstrap exception becomes precedent.** Mitigation:
  document the closure condition and include a spec scenario that rejects later
  unscoped OpenSpec proposal creation.
- **Risk: Too many paths are protected and the checker becomes noisy.**
  Mitigation: start with warning mode for docs-only paths if needed, but keep
  OpenSpec, genops, source, resources, and release metadata hard-gated.
- **Risk: Worker roles are simulated by one agent rather than spawned.**
  Mitigation: the pipeline still records role/task ownership; actual subagent
  spawning remains governed by owner authorization and disjoint file ownership.

## Migration Plan

1. Implement the front-door docs/spec updates.
2. Add `openspec-change.full` and Commander routing.
3. Add and test the front-door checker.
4. Run a no-op CRAFT pass for this governance change after the pipeline exists.
5. Re-enter `add-visual-reference-structure-pipeline` through the new pipeline
   before implementation continues.

## Open Questions

- Should the front-door checker be a hard failure for all protected paths from
  day one, or warning-only for docs paths during the first migration?
- Should `openspec new change` itself be wrapped by a Commander helper, or is a
  pipeline task contract plus checker enough?
- Should run evidence be referenced in OpenSpec artifacts directly, or only in
  GenOps manifests and final summaries?
