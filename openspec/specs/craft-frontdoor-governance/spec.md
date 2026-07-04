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
task id, worker role, changed artifacts, and verdict state when human review is
required.

Protected paths SHALL include OpenSpec changes/specs, GenOps configuration and
pipelines, KB docs, generator code, Java runtime code, generated structure
resources, release metadata, and user-facing command/build docs.

#### Scenario: OpenSpec artifact changed without run evidence
- **WHEN** a local diff changes `openspec/changes/**`
- **AND** no matching GenOps run evidence is supplied
- **THEN** the front-door checker SHALL report a missing-provenance finding.

#### Scenario: Generator code changed with matching task evidence
- **WHEN** a local diff changes `tools/buildgen/**`
- **AND** a matching GenOps run records a generator-engineer task that owns that
  path
- **THEN** the front-door checker SHALL accept the provenance for that path.

### Requirement: Commander summaries expose front-door evidence
For CRAFT-required work, the Commander SHALL summarize the front-door evidence
instead of reporting only raw command status. The summary SHALL include the run
id, pipeline, worker/task ownership, artifacts produced or changed, gate status,
human verdict state, and next decision.

#### Scenario: CRAFT-required summary includes run evidence
- **WHEN** a CRAFT-required run completes or pauses
- **THEN** the Commander SHALL report the run id and pipeline
- **AND** it SHALL list the relevant worker/task ownership and artifacts
- **AND** it SHALL state whether a human verdict is pending, accepted, rejected,
  or not required.

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
