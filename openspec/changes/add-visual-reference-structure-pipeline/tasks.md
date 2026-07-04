## 1. Contract and Documentation

- [ ] 1.1 Add a `docs/ai-kb/20_visual_reference_structure_pipeline.md` note that explains the visual-reference decomposition workflow, its four buckets, non-goals, and see-also links to the new capability spec and `genops`.
- [ ] 1.2 List the new KB note and `visual-reference-structure-pipeline` capability in `docs/ai-kb/INDEX.md`.
- [ ] 1.3 Update `research/source_structures/README.md` to point local-research candidates at the decomposition workflow rather than direct import or generator edits.
- [ ] 1.4 Update `CRAFT.md` and `genops/README.md` with the owner-facing reference-decomposition usage pattern.

## 2. Reference Breakdown Contract

- [ ] 2.1 Add a structured Reference Breakdown Contract schema or documented artifact format covering source facts, observations, `direct_component`, `atomic_component`, `generative_grammar`, `calibration_only`, downstream routes, evidence, and verdict state.
- [ ] 2.2 Add the first worked example for `research/source_structures/candidate_003`, explicitly routing Hui-style cues into the four buckets.
- [ ] 2.3 Add at least one lightweight validation/check fixture that confirms the worked example includes source facts, all required bucket keys, downstream routes for non-empty actionable buckets, and a pending/recorded verdict state.

## 3. CRAFT / GenOps Pipeline

- [ ] 3.1 Add `genops/pipelines/reference-decomposition.full.yaml` with a scoped task DAG for mapping a reference, drafting the breakdown card, classifying buckets, checking OpenSpec/KB routes, collecting visual evidence pointers, and recording human verdict state.
- [ ] 3.2 Update `genops/commander.yaml` so natural-language cues such as `视觉参考`, `参考建筑`, `拆解`, `source_structures`, `candidate_003`, and `reference decomposition` route to the new pipeline.
- [ ] 3.3 Add or update GenOps role prompts only where needed so the new pipeline keeps implementation edits out of scope unless a later change authorizes them.
- [ ] 3.4 Run a no-op GenOps planning pass for the new pipeline and confirm it writes a manifest/task graph under `reports/agent_runs/<run_id>/` without modifying generator code or shipped NBT resources.

## 4. Verification and Release Hygiene

- [ ] 4.1 Validate the OpenSpec change status and review the new spec scenarios for exact `#### Scenario` formatting.
- [ ] 4.2 Run the Reference Breakdown Contract validation/check fixture added in 2.3.
- [ ] 4.3 Run the new reference-decomposition GenOps no-op planning pass from 3.4 and record the manifest path in the task evidence.
- [ ] 4.4 Run existing relevant GenOps/tooling checks for pipeline loading and patch scope.
- [ ] 4.5 Run `./gradlew build` as the practical jar build gate even though this change does not add placeable structures.
- [ ] 4.6 Bump the mod/tooling version and update `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together per `openspec/config.yaml` `rules.tasks`.

## 5. Acceptance Handoff

- [ ] 5.1 Summarize what the `candidate_003` worked example routes to future work, especially the split between direct component, atomic component, generative grammar, and calibration-only criteria.
- [ ] 5.2 State explicitly that no external structure was copied, no generated NBT was added, and no visual style is accepted until the owner gives a human verdict.
- [ ] 5.3 Identify the likely next downstream changes: `rebuild-huipai-mansion`, reference atom library work, or additional worked examples for pagoda / temple / moon-gate / bridge references.
