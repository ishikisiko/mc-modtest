# CRAFT Front-door Governance

## Purpose

This spec defines when work must enter through CRAFT/GenOps before protected
project artifacts are authored or modified. OpenSpec remains the capability
contract; CRAFT is the process layer that supplies run evidence, worker
ownership, gates, and verdict state.

See also (narrative): [docs/ai-kb/19_genops.md](../../../docs/ai-kb/19_genops.md).

## Requirements

### Requirement: CRAFT-required intents enter through GenOps
The project SHALL classify high-impact owner requests as CRAFT-required. A
CRAFT-required request SHALL be handled through a GenOps run manifest and task
graph before protected artifacts are authored or modified.

CRAFT-required intents SHALL include:

- explicit CRAFT or GenOps requests;
- new OpenSpec change or proposal authoring;
- OpenSpec apply or implementation work;
- visual or aesthetic structure changes;
- multi-worker, subagent, or parallel work;
- release, version, jar, build, acceptance, or visual-review handoff.

#### Scenario: Explicit CRAFT request uses a run
- **WHEN** the owner asks "用 CRAFT 规划拆解示例建筑"
- **THEN** the Commander SHALL create or continue a GenOps run
- **AND** the Commander SHALL NOT directly write OpenSpec artifacts as an
  unscoped action.

#### Scenario: Trivial read-only query is exempt
- **WHEN** the owner asks only for a read-only status check such as current git
  status or current OpenSpec list
- **THEN** the Commander MAY answer without creating a GenOps run
- **AND** the Commander SHALL NOT modify protected files for that request.

### Requirement: Protected paths require front-door provenance
Changes to protected project paths SHALL be associated with front-door
provenance. Front-door provenance SHALL include a GenOps `run_id`, pipeline id,
task id, worker role, changed artifacts, gate evidence, raw-log locations,
manifest path, and verdict state when human review is required.

Protected paths SHALL include OpenSpec changes/specs, GenOps configuration and
pipelines, KB docs, generator code, Java runtime code, generated structure
resources, release metadata, and user-facing command/build docs.

Protected path categories SHALL remain granular enough to route ownership:
Java runtime, client resources, data resources, generated NBT, release
metadata, generator code, GenOps files, docs, and OpenSpec artifacts are
separate categories. The checker SHALL NOT use `src/main/**` as a single
catch-all ownership category.

#### Scenario: OpenSpec artifact changed without run evidence
- **WHEN** a local diff changes `openspec/changes/**`
- **AND** no matching GenOps run evidence is supplied
- **THEN** the front-door checker SHALL report a missing-provenance finding.

#### Scenario: Generator code changed with matching task evidence
- **WHEN** a local diff changes `tools/buildgen/**`
- **AND** a matching GenOps run records a generator-engineer task that owns that
  path
- **THEN** the front-door checker SHALL accept the provenance for that path.

#### Scenario: Front-door result is part of run evidence
- **WHEN** a CRAFT-required run reaches final manifest writing
- **THEN** the manifest SHALL include the front-door checker result
- **AND** protected changed files SHALL require a matching manifest artifact,
  allowed worker role, and task/patch/git changed-file consistency when those
  records exist.

### Requirement: Commander summaries separate owner surface from audit detail
For CRAFT-required work, the Commander SHALL default to an owner-facing summary
that contains `goal_status`, `scope_or_direction`, `validation_state`,
`risk_or_blocker`, `human_decision_needed`, and `next_decision`.

The Commander SHALL NOT treat run ids, pipeline names, task ids, worker roles,
raw logs, or manifest paths as default owner operation entry points. Those
fields SHALL remain complete audit detail, available when the owner asks for
audit detail or when backend evidence is the blocker.

Audit detail SHALL include `run_id`, `pipeline`, `task_id`, worker ownership,
artifacts, gates, raw logs, and manifest path.

#### Scenario: CRAFT-required summary stays decision-oriented
- **WHEN** a CRAFT-required run completes or pauses
- **THEN** the Commander SHALL report `goal_status`, `scope_or_direction`,
  `validation_state`, `risk_or_blocker`, `human_decision_needed`, and
  `next_decision`
- **AND** it SHALL NOT present the run id, pipeline, task id, or manifest path
  as the next owner action.

#### Scenario: Audit detail remains traceable
- **WHEN** the owner asks for audit detail
- **THEN** the Commander SHALL be able to report the run id, pipeline, task id,
  worker ownership, artifacts, gates, raw logs, and manifest path
- **AND** those audit fields SHALL connect protected-path changes to GenOps run
  evidence.

### Requirement: Bootstrap exception is limited and closes after implementation
The only accepted bootstrap exception SHALL be the planning artifacts for
`enforce-craft-frontdoor-governance`, because this change defines the missing
front door. The exception SHALL NOT authorize implementation code, generated
resources, release metadata, or any later OpenSpec changes without CRAFT
provenance. After the OpenSpec-change pipeline and front-door checker are
implemented, new OpenSpec proposal work SHALL use CRAFT.

#### Scenario: Bootstrap exception is accepted for this change only
- **WHEN** the front-door checker sees planning artifacts under
  `openspec/changes/enforce-craft-frontdoor-governance/**`
- **THEN** it MAY accept the documented bootstrap exception
- **AND** it SHALL still reject unrelated protected-path changes that lack
  GenOps run evidence.

#### Scenario: Later proposal bypass is rejected
- **WHEN** a later change creates or modifies `openspec/changes/**`
- **AND** the change is not the documented bootstrap exception
- **AND** no matching `openspec-change.full` run evidence is supplied
- **THEN** the front-door checker SHALL report a missing-provenance finding.
