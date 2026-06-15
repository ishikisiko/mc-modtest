## Why

Phases 0–3 (`add-external-mod-catalog-slots`, `add-external-mod-decor`) built the
catalog, a namespace-aware slot system, orientation adapters, and motifs, so the
shipped libraries now contain external-mod ids (`ars_nouveau`, `farmersdelight`,
`supplementaries`, `fetzisdisplays`, …) at their intended 落点. But generation and
validation no longer share one source of truth about *which* ids are legal.

Generation resolves legal ids from a **modset profile** (`vanilla` vs `full`, via
`modset_namespaces()` over `exmod/mod_block_catalog.json`). Validation does not:
`validate_structure_json.py` hard-rejects every non-`minecraft` id, and
`validate_building_library.py` flags every non-vanilla id as
`unknown_block_ids` against `blocks_121.json`, while `validate_compound_library.py`
and `validate_generated_structures.py` perform **no** id-legality check at all. The
result is contradictory: a `full`-profile structure that generation considers valid
either fails the JSON/library validators or passes the NBT validators without any id
check. There is no profile under which "every placed mod id is a real catalog id"
is actually asserted, and no profile that asserts "this output is mod-free."

Phase 4 makes validation modset-aware so both profiles validate clean against the
same catalog the generator reads.

## What Changes

- **One modset resolver for generation and validation.** Add a `modset` module that,
  for a named profile, exposes the active namespaces (already in `style.py`) and the
  set of legal mod block ids drawn from `exmod/mod_block_catalog.json`. Generators
  and validators both resolve legality here — the single source of truth.
- **`--profile {vanilla,full}` on generators.** `generate_building_library.py`,
  `generate_compound_library.py`, `generate_civic_library.py`, and the
  `generate_all_structures.py` driver gain a profile that filters slots via
  `load_style(style_id, available_namespaces=…)`. `full` keeps current output
  (every shipped id is in a confirmed namespace, so filtering is a no-op); `vanilla`
  drops mod ids to their `minecraft:` fallbacks.
- **Modset-aware id legality on validators.** `validate_building_library.py`,
  `validate_compound_library.py`, `validate_generated_structures.py`, and
  `validate_structure_json.py` gain `--profile`. Under `vanilla` any non-`minecraft`
  id fails (`forbidden_mod_blocks`); under `full` a non-`minecraft` id passes only
  if its namespace is confirmed **and** the id exists in the catalog
  (`unknown_mod_blocks` otherwise). Vanilla `minecraft` id checks are unchanged.
- **Default profile `full`.** The shipped artifacts under
  `src/main/resources/data/myvillage/structure/` already carry catalog mod ids, so
  the documented validate commands keep passing unchanged. `vanilla` is opt-in via
  the flag and is proven against freshly generated mod-free output.
- **No exmod, no Java, no regeneration of shipped artifacts.** Validation-layer +
  generator-flag only. The runtime resolver and `neoforge.mods.toml` optional deps
  remain Phase 5; preview/iterate remains Phase 6.

## Capabilities

### New Capabilities
- `modset-profile`: A profile resolver shared by generation and validation that maps
  a profile name (`vanilla` / `full`) to its active namespaces and its set of legal
  external-mod block ids, sourced from `exmod/mod_block_catalog.json`.

### Modified Capabilities
- `validation`: Block-id legality is evaluated against the active modset profile, not
  a fixed vanilla-only registry. `vanilla` forbids all mod ids; `full` allows only
  confirmed-namespace ids present in the catalog. Existing structural, signature, and
  vanilla-id heuristics are unchanged.

## Impact

- **Code:** new `tools/buildgen/modset.py`; `--profile` plumbing in
  `tools/generate_{building,compound,civic}_library.py`,
  `tools/generate_all_structures.py`, and
  `tools/validate_{building_library,compound_library,generated_structures,structure_json}.py`.
- **Artifacts:** mod ids validated against `exmod/mod_block_catalog.json`
  (read-only). Shipped NBT/function files are unchanged.
- **Profiles:** `full` (default) validates the shipped artifacts clean; `vanilla`
  validates freshly generated mod-free output clean and rejects mod ids.
- **Docs:** `AGENTS.md` validate-command list and `docs/external_mod_integration_plan.md`
  status note the profile flag and Phase 4 completion.
- **Out of scope (later phases):** Java runtime resolver + `neoforge.mods.toml`
  optional deps (Phase 5); regenerate/preview/iterate (Phase 6).
