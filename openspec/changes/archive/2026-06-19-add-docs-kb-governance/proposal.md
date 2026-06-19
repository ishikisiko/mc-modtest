## Why

The project's in-repo documentation is accurate and kept in sync with code, but it has **discoverability and maintenance gaps** that make the knowledge base harder to use and easier to let drift:

- The `docs/ai-kb/` learning chain (`00_project_brief` → `10_civic_family`) has **no entry point and no cross-links** — nothing in `README.md`, `AGENTS.md`, or the specs points a new contributor/agent at it, and it does not point back at the `openspec/specs/` capability index that covers the same topics.
- The **version-bump rule is written twice** — in `AGENTS.md` and in `openspec/config.yaml` `rules.tasks` — so the two can drift apart.
- `README.md`'s "Not included" scope list still claims **`passive/natural worldgen` and `biome placement` are absent**, but v0.11.0 shipped a custom `myvillage:sect` worldgen Structure (biome-gated by `has_sect`, spaced by `structure_set/sect`). The scope statement contradicts the shipped behavior.
- Two `AGENTS.md` conventions are **~2KB single-line paragraphs** (settlement composition; the acceptance-prep command checklist) that neither a human nor an agent can scan.

There is no captured standard for *where doc content lives, how it is linked, and how it stays truthful*, so each of these is a one-off rather than a governed expectation. This change captures that standard as a governance capability and fixes the three concrete instances above.

## What Changes

- **Add a `docs-knowledge-base` governance capability** (sibling to `spec-baseline-governance`) that requires: a single discoverable knowledge-base entry/map; cross-links between same-topic `docs/ai-kb/` files and `openspec/specs/`; a single authoritative location for any rule referenced in multiple docs; that scope/"Not included" statements reflect shipped behavior; and a write-in guideline for adding new doc content.
- **Add a knowledge-base entry map** at `docs/ai-kb/INDEX.md` listing the `00–10` learning chain and linking the `openspec/specs/` index, referenced from `README.md` and `AGENTS.md`.
- **Cross-link** at least the worldgen, validation, and blueprint-schema doc/spec pairs with see-also references.
- **Fix the README scope list (RM1)**: remove the now-shipped `passive/natural worldgen` and `biome placement` items from "Not included", qualifying the remaining accurate items (no jigsaw/template pool; no town worldgen yet).
- **Split the two `AGENTS.md` giant paragraphs (AG2)**: settlement composition into per-subject sub-items; the acceptance-prep command checklist moved into `docs/ai-kb/`, leaving a short pointer.
- **Make the version-bump rule single-source (AG3)**: keep `openspec/config.yaml` `rules.tasks` authoritative; change `AGENTS.md` to reference it instead of restating it.

Out of scope (explicitly deferred this round): the hardcoded acceptance-review IP in `AGENTS.md`; a sink for deleted design/investigation notes (DSPP); and adding `status` metadata to OpenSpec changes.

## Capabilities

### New Capabilities
- `docs-knowledge-base`: The repository's documentation SHALL have a single discoverable entry/map, cross-links between same-topic ai-kb docs and specs, a single authoritative location for shared rules, scope statements that reflect shipped behavior, and a write-in guideline for new doc content.

### Modified Capabilities
- None. (`spec-baseline-governance` already requires version/changelog synchronization and that specs describe implemented behavior; this capability is additive and does not contradict it.)

## Impact

- Docs (new): `docs/ai-kb/INDEX.md` (knowledge-base map).
- Docs (edited): `README.md` (RM1 scope fix + entry link), `AGENTS.md` (AG2 paragraph split + acceptance checklist moved out + entry link; AG3 version rule reduced to a reference), `docs/ai-kb/07_neoforge_worldgen.md` / `09_validation_checklist.md` / `02_blueprint_schema.md` and their sibling specs (`sect-worldgen-structure`, `validation`, `blueprint-v1`) gain see-also links.
- Specs: new `docs-knowledge-base` capability spec.
- Version: `README`/`AGENTS` are user-facing docs, so this lands as a validated `-fix` per `openspec/config.yaml` — `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` bump together.
- No code, structures, assets, or commands change; the asset-export and command-manual rules in `openspec/config.yaml` do not apply.
