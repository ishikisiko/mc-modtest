## 1. Governance Contract and Documentation

- [x] 1.1 Update `CRAFT.md` to define CRAFT-required intent classes, the mandatory front-door rule, the bootstrap exception, and the required Commander summary shape.
- [x] 1.2 Update `docs/ai-kb/19_genops.md` with the same front-door governance model and the distinction between trivial direct work and CRAFT-required work.
- [x] 1.3 Update `docs/ai-kb/INDEX.md` to list the new `craft-frontdoor-governance` capability under Pipeline / integration.
- [x] 1.4 Update `genops/README.md` so backend GenOps usage describes `openspec-change.full` and front-door provenance checks.
- [x] 1.5 Add an explicit note that the pre-existing `add-visual-reference-structure-pipeline` change must be re-entered through the new front door before implementation continues.

## 2. OpenSpec-change Pipeline

- [x] 2.1 Add `genops/pipelines/openspec-change.full.yaml` with tasks for intake/context, existing-change inspection, capability/scope planning, artifact writing, and front-door evidence review.
- [x] 2.2 Ensure the new pipeline scopes OpenSpec artifact writes to `openspec/changes/**`, spec deltas, KB/docs as needed, and blocks generator/runtime/release paths unless a later implementation pipeline owns them.
- [x] 2.3 Update `genops/commander.yaml` so new OpenSpec proposal/change/apply cues route to `openspec-change.full` rather than direct artifact writing.
- [x] 2.4 Update `genops/agents/commander.md` to require front-door routing and structured reporting for CRAFT-required work.
- [x] 2.5 Add or update worker role guidance for `spec-guardian`, `pipeline-architect`, and `docs-steward` so OpenSpec authoring is role-scoped.

## 3. Front-door Provenance Checker

- [x] 3.1 Add `tools/genops/check_frontdoor.py` to inspect changed protected paths and validate supplied GenOps run evidence.
- [x] 3.2 Implement protected-path matching for `openspec/changes/**`, `openspec/specs/**`, `docs/ai-kb/**`, `genops/**`, `tools/buildgen/**`, `src/main/**`, generated structure NBT resources, version files, `CHANGELOG.md`, and user-facing docs.
- [x] 3.3 Support an explicit `--run-id` or equivalent run evidence argument and verify that the run manifest/task evidence identifies the relevant pipeline, task id, worker role, and changed artifacts.
- [x] 3.4 Encode the bootstrap exception for `openspec/changes/enforce-craft-frontdoor-governance/**` only, and reject unrelated protected changes without provenance.
- [x] 3.5 Add tests/fixtures for accepted provenance, missing provenance, mismatched worker ownership, release-steward ownership, and the bootstrap exception.

## 4. Run Evidence and Summary Shape

- [x] 4.1 Extend GenOps task/run evidence if needed so changed artifacts are indexed per task and exposed in the final manifest or summary.
- [x] 4.2 Ensure `tools/genops/run_pipeline.py` or report writing includes enough artifact ownership data for `check_frontdoor.py` to validate protected changes.
- [x] 4.3 Update Commander-facing docs/examples to require summaries with `run_id`, `pipeline`, `worker/task`, `artifacts`, `gates`, `human_verdict`, and `next_decision`.
- [x] 4.4 Run a no-op `openspec-change.full` planning pass for this governance change after the pipeline exists and record the manifest path in the handoff.

## 5. Validation, Build, and Version Sync

- [x] 5.1 Run OpenSpec status/validation for `enforce-craft-frontdoor-governance` and confirm all planning artifacts remain complete.
- [x] 5.2 Run GenOps pipeline loading checks, including the new `openspec-change.full` pipeline.
- [x] 5.3 Run the `check_frontdoor.py` test suite/fixtures.
- [x] 5.4 Run the front-door checker against the current governance change with the documented bootstrap exception and then with a generated no-op run once available.
- [x] 5.5 Run `./gradlew build` as the practical jar build gate.
- [x] 5.6 Bump the mod/tooling version and update `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together per `openspec/config.yaml` `rules.tasks`.

## 6. Handoff and Re-entry

- [x] 6.1 Report that this change closes the bootstrap exception after `openspec-change.full` and `check_frontdoor.py` land.
- [x] 6.2 Re-enter the existing `add-visual-reference-structure-pipeline` change through `openspec-change.full` before continuing its implementation.
- [x] 6.3 Summarize which work remains exempt from CRAFT (trivial read-only checks) and which work is now CRAFT-required.
