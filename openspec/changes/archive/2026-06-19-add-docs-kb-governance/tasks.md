## 1. Knowledge-base entry map and interlinks

- [x] 1.1 Add `docs/ai-kb/INDEX.md`: a knowledge-base map listing the `00–10` learning chain (each with a one-line purpose) and linking the `openspec/specs/` capability index; group specs by layer (building / compound / town / worldgen / integration / governance).
- [x] 1.2 Add a one-line pointer to `docs/ai-kb/INDEX.md` from `README.md` and from `AGENTS.md`.
- [x] 1.3 Cross-link same-topic doc/spec pairs with see-also references (both directions): `07_neoforge_worldgen.md` ↔ `sect-worldgen-structure` (+ `sect-mountain-derivation`), `09_validation_checklist.md` ↔ `validation`, `02_blueprint_schema.md` ↔ `blueprint-v1` (+ `structure-json-dsl`).

## 2. README scope accuracy (RM1)

- [x] 2.1 Update the `README.md` "Not included" block: remove `passive/natural worldgen` and `biome placement` (sect worldgen shipped in v0.11.0 as a custom `myvillage:sect` Structure, biome-gated by `has_sect`, spaced by `structure_set/sect`); reword the jigsaw line to "jigsaw/template-pool generation" and narrow remaining exclusions to what is still true (e.g. town worldgen not yet registered).

## 3. AGENTS.md readability (AG2)

- [x] 3.1 Split the settlement-composition convention (`AGENTS.md`, the ~2KB single-line paragraph) into `sect` / `town` / `street-life` sub-bullets, preserving every fact.
- [x] 3.2 Move the acceptance-prep command checklist (the other ~2KB single-line paragraph) into `docs/ai-kb/` (extend `09_validation_checklist.md` or add an acceptance-checklist doc); leave a one-line pointer in `AGENTS.md` and list it in `INDEX.md`.

## 4. Version rule single source (AG3)

- [x] 4.1 Make `openspec/config.yaml` `rules.tasks` the authoritative version-bump rule; change the `AGENTS.md` changelog convention to reference it instead of restating the `0.x.y`/`-fix` mechanics.

## 5. Version bump and changelog (per `openspec/config.yaml`)

- [x] 5.1 Bump `0.11.0-fix1` → `0.11.0-fix2` across `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` (documentation maintenance: KB entry map + interlinks, README scope fix, AGENTS readability/single-source).

## 6. Verify

- [x] 6.1 `openspec validate add-docs-kb-governance --strict` passes.
- [x] 6.2 Every link in `docs/ai-kb/INDEX.md` resolves to a real file/spec; entry map reachable in ≤2 hops from README and AGENTS to any ai-kb doc and its same-topic spec.
- [x] 6.3 `grep -n -i "worldgen" README.md` confirms the "Not included" block no longer excludes the shipped sect worldgen.
- [x] 6.4 `AGENTS.md` has no remaining >800-character single-line paragraph; the version mechanics appear only in `openspec/config.yaml`.
