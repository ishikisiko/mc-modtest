## Why

The repository now has CRAFT/GenOps governance, but mod item work still lacks a
first-class route. Without a framework, item requests can collapse Java
registration, creative-tab exposure, model JSON, textures, lang entries,
recipes/tags, validation, and docs into one ambiguous patch.

## What Changes

- Add a repo-local `mod-item-creation` skill that classifies item requests and
  routes them through CRAFT instead of generating files directly.
- Add `genops/pipelines/mod-item.full.yaml` with atomic role tasks for item
  contracts, Java registration, resources, visual review, validation, docs, and
  regression.
- Add dedicated GenOps roles for Java runtime registry work and client/data
  resource assets.
- Add `myvillage:test_item_block` as a deliberately plain, placeable smoke item
  block that exercises the route without adding gameplay behavior.
- Document the item workflow in the KB and route natural-language item requests
  through Commander intent routing.

## Capabilities

### New Capabilities

- `mod-item-pipeline`: CRAFT-backed workflow for creating or revising
  `myvillage:` mod items.

### Modified Capabilities

- `genops`: Route mod item work through a dedicated pipeline and mapped custom
  subagents.

## Impact

- Affected governance/config: `.codex/skills/**`, `.codex/agents/**`,
  `genops/**`, `tools/genops/**`.
- Affected docs/specs: `docs/ai-kb/**`, `openspec/changes/**`,
  `openspec/specs/genops/spec.md`, README/AGENTS/CRAFT navigation.
- Adds a small test block item and focused validation for item/resource
  completeness. No gameplay item behavior is added.
