## Design

### Goal

Turn ad-hoc documentation hygiene into a governed expectation, and fix the three concrete gaps the new rules expose (RM1, AG2, AG3). The change is documentation-only — no code, no generated artifacts.

### Where the entry map lives

Options for the knowledge-base entry/map:

- **A — `docs/ai-kb/INDEX.md` + pointers from README/AGENTS (chosen).** The map lives next to the docs it indexes, so adding a doc and updating its neighbor is one directory's worth of work. `README.md` and `AGENTS.md` each carry a one-line pointer to it rather than duplicating the list. This keeps the canonical list in one place and matches the existing `00–10` numbering convention.
- B — a section inside `README.md`. Rejected: README is already 650+ lines and user-facing; an agent-onboarding index does not belong in the user manual, and it would split the ai-kb listing from the ai-kb folder.

### Cross-linking scope

Full bidirectional linking of all 13 ai-kb files to all 39 specs is busywork and rots. Instead the requirement is **same-topic** see-also links, and this change seeds the three clearest pairs as the pattern to follow:

- `07_neoforge_worldgen.md` ↔ `sect-worldgen-structure` (+ `sect-mountain-derivation`)
- `09_validation_checklist.md` ↔ `validation`
- `02_blueprint_schema.md` ↔ `blueprint-v1` (+ `structure-json-dsl`)

The write-in guideline then makes future docs carry their own see-also so coverage grows incrementally instead of via a big-bang sweep.

### Single source of truth for the version rule

`openspec/config.yaml` `rules.tasks` is the authoritative, machine-read location (it gates every change). `AGENTS.md` keeps the human-facing convention but **references** that rule rather than restating the `0.x.y`/`-fix` mechanics, so the two cannot drift. The `spec-baseline-governance` spec already states the synchronization requirement at the spec level; `docs-knowledge-base` adds the general "shared rule has one home" rule that this instance satisfies.

### AGENTS.md readability

The settlement-composition paragraph is split into `sect` / `town` / `street-life` sub-bullets preserving every fact. The acceptance-prep command list (currently a single ~2KB line) moves into `docs/ai-kb/` as a scannable checklist; `AGENTS.md` keeps a one-line pointer to it. No facts are dropped — they relocate to a place built to hold them.

### README scope correction (RM1)

The "Not included" block predates v0.11.0. Sects now generate via a custom `myvillage:sect` `Structure` with biome gating (`tags/worldgen/biome/has_sect`) and a `structure_set/sect` placement, so `passive/natural worldgen` and `biome placement` are no longer accurate as blanket exclusions. They are removed (or narrowed to "town worldgen"). `jigsaw/template pool/structure_set generation` stays only insofar as it is still true — sects use a hand-written `Structure`, not jigsaw template pools — so that line is reworded to "jigsaw/template-pool generation" and the structure_set claim dropped.

### Verification

`openspec validate add-docs-kb-governance --strict`; every link in `INDEX.md` resolves to a real file/spec; `grep` confirms the README scope no longer excludes shipped worldgen, AGENTS no longer restates the version mechanics, and the giant paragraphs are gone.
