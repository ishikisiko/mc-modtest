## Why

The cultivation foundation can store and synchronize a spiritual root and learned techniques, but players still have no rules-based path to obtain either. This change adds one narrow, server-authoritative initiation ritual so later meditation and technique execution can build on deterministic awakening and validated technique inheritance rather than administrator-only profile edits.

## What Changes

- Add deterministic one-time spiritual-root awakening from the Overworld seed, player UUID, a fixed mod salt, and the current eligible spiritual-element ids and awakening weights; registry iteration order, time, position, dimension, and client state do not affect the result.
- Extend spiritual-element definitions with optional `awakening_weight` (`0..1_000_000`, default `1`), ship explicit weight `1` for the five current elements, select one through five distinct eligible elements without replacement, and normalize positive integer affinities to exactly `10000` basis points.
- Add a server-side awakening service that atomically installs the generated root and advances a valid mortal profile to `myvillage:mortal_qi_sensed` through the existing `CultivationService`, without a new profile field or schema version.
- Add a reusable technique-requirement evaluator and a server-side inheritance service that teaches only `myvillage:basic_breathing` at mastery `0` after the current technique definition's realm, stage, and affinity requirements pass; repeat inheritance preserves existing mastery.
- Update `myvillage:basic_breathing` to require `myvillage:mortal` and `myvillage:mortal_qi_sensed`, while remaining element-neutral metadata with no executor.
- Add separate interactive blocks and BlockItems `myvillage:spirit_testing_stele` and `myvillage:technique_inheritance_stele`, complete client/data resources, bilingual translatable feedback, creative-tab exposure, `/give` acquisition, and no BlockEntity, recipe, natural generation, or combined automatic workflow.
- Extend both `/myvillage cultivation` and `/myvillage xiulian` with structurally equivalent `awaken`/`juexing` and `initiate`/`rumen` administrator routes that call the same services as the two steles and accept no player-selected seed, root, affinity, element, or technique id.
- Reuse the existing clientbound cultivation snapshot and read-only `H` profile screen; add no cultivation play-to-server mutation payload and no client-authoritative eligibility or generation path.
- Add focused Java tests, `tools/validate_cultivation_initiation.py` with Python tests, jar-content checks, existing cultivation regression gates, bounded dedicated-server smoke, and an evidence-based manual checklist whose unobserved items remain `not_verified`.
- Add the cultivation-initiation KB note and update command, acceptance, version, changelog, and jar-name documentation according to the single version rule in `openspec/config.yaml`.
- Keep meditation, technique execution, spiritual-power capacity/recovery, cultivation gain, mastery growth, advancement, combat effects, root quality/tiering/rerolls, worldgen, sect/region integration, NPCs, quests, crafting, and any profile schema v2 field out of scope.

## Capabilities

### New Capabilities

- `cultivation-initiation-ritual`: Deterministic spiritual-root generation and atomic awakening, definition-driven basic-technique inheritance, the two separate initiation steles, their server-authoritative interactions, and the strict gameplay/non-goal boundary.

### Modified Capabilities

- `cultivation-player-profile`: Permit the two ritual services to replace the existing immutable v1 profile atomically through `CultivationService`, without adding persistent fields or changing schema version `1`.
- `cultivation-definition-registries`: Add `awakening_weight`, make technique requirements executable through a shared evaluator, and give `basic_breathing` its minimum mortal realm/stage requirements while retaining no executor.
- `cultivation-persistence-lifecycle`: Preserve ritual-produced roots and learned techniques through the existing attachment lifecycle without adding a second copy or persistence path.
- `cultivation-state-synchronization`: Synchronize only the final successful awakening or inheritance profile through the existing clientbound snapshot, with no mutation snapshot on failure and no new C2S payload.
- `cultivation-debug-commands`: Add equivalent English/pinyin initiation routes under both roots while preserving the existing low-level administrator commands and their distinct bypass semantics.
- `cultivation-core-validation`: Extend cultivation regression evidence to cover deterministic awakening, definition-driven inheritance, stele integration, packaging, dedicated-server safety, and the expanded manual lifecycle checklist.
- `resource-export`: Document and package the two usable stele BlockItems and their complete resources as creative/`/give`-only content without implying recipe, natural generation, structure, or worldgen export.
- `validation`: Add the dedicated initiation validator/tests, strengthen block-item resource checks for both steles, and require truthful jar, server-smoke, and manual acceptance evidence.
- `docs-knowledge-base`: Add and cross-link the cultivation-initiation note and update shipped-scope statements when the two-step ritual lands.

## Impact

- Java runtime: new pure root-generation and ritual service packages, a shared technique-requirement evaluator, two simple interactive blocks, block/item registration, creative-tab exposure, and command-tree delegation through existing authority boundaries.
- Data/resources: one backward-compatible spiritual-element codec field, five updated element definitions, updated `basic_breathing` requirements, two blockstates/models/item models/loot tables/tool-tag entries, bilingual names/messages, and jar packaging.
- Networking/client: the existing `CultivationSnapshotPayload`, `ClientCultivationState`, and read-only `CultivationProfileScreen` remain the only cultivation client surface; snapshot shape and profile schema remain unchanged.
- Validation/docs/release: focused Java and Python tests, a new deterministic validator, existing cultivation and mod-item regressions, bounded acceptance-server smoke, `docs/ai-kb/29_cultivation_initiation_ritual.md`, command/usage documentation, manual pass/fail/`not_verified` records, and the synchronized version/changelog updates required by `openspec/config.yaml`.
- Compatibility: Minecraft `1.21.1`, NeoForge `21.1.233`, Java `21`, mod id `myvillage`; existing saves and datapacks that omit `awakening_weight` remain loadable through the default value, and existing roots are never automatically regenerated.
