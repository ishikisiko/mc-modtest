## Context

Generation already resolves legal ids from a modset profile: `load_style(style_id,
available_namespaces)` filters each slot list to active namespaces, and
`style.modset_namespaces(profile)` maps `vanilla` → `{minecraft}` and `full` →
`{minecraft} ∪ catalog.confirmed_mod_namespaces`. Validation has no equivalent. Four
validators touch block-id legality and each does something different:

- `validate_structure_json.py` — DSL validator; raises on any non-`minecraft` id.
- `validate_building_library.py` — flags any palette id not in `blocks_121.json`
  (namespaced) as `unknown_block_ids`, plus per-style `style.is_forbidden` substring.
- `validate_compound_library.py` — only `style.is_forbidden`; no registry/id check.
- `validate_generated_structures.py` — no id-legality check at all.

The shipped structures already contain catalog mod ids (verified: every non-`minecraft`
id in `src/main/resources/.../structure/*.nbt` is from a confirmed namespace and exists
in the catalog). So the goal is not to remove mod ids but to assert, per profile, that
the ids present are exactly the ids that profile permits.

## Goals / Non-Goals

**Goals:**
- One resolver (`tools/buildgen/modset.py`) for profile → (namespaces, legal mod ids),
  reused by generators and validators.
- `--profile {vanilla,full}` on the four validators and the three generators + driver.
- `vanilla` forbids every mod id; `full` allows only confirmed-namespace catalog ids.
- Both profiles validate clean (full against shipped artifacts, vanilla against
  freshly generated mod-free output).

**Non-Goals:**
- Adding vanilla `blocks_121.json` checks to validators that lack them (out of scope;
  Phase 4 is about *mod* id legality). Existing vanilla checks stay where they are.
- Changing `style.is_forbidden` per-style substring policy (orthogonal).
- Regenerating or rewriting shipped artifacts; Java runtime resolver (Phase 5).

## Decisions

**1. A single `modset` module owns mod-id legality; profiles are immutable.**
`load_modset(profile)` returns a frozen `ModsetProfile(name, namespaces,
mod_block_ids)`. `namespaces` reuses `style.modset_namespaces`; `mod_block_ids` is the
set of `entry["id"]` over `catalog["namespaces"]` restricted to
`confirmed_mod_namespaces`. Rationale: validation reads the *same* catalog the
generator's namespace filter reads, so the two cannot drift. Alternative — duplicate
the id list inside each validator — rejected: that is the drift Phase 4 exists to kill.

**2. The profile classifies only non-`minecraft` ids; vanilla checks are untouched.**
`ModsetProfile.palette_block_errors(palette)` returns at most
`forbidden_mod_blocks: [...]` (namespace not in `self.namespaces`) and
`unknown_mod_blocks: [...]` (namespace allowed but id not in `self.mod_block_ids`). A
`minecraft:` id is never touched here, so each validator keeps its existing vanilla
behavior (registry check, `is_forbidden`, or none) exactly. Rationale: minimal blast
radius and no surprise failures from pre-existing vanilla-id gaps. In
`validate_building_library.py` the one needed adjustment is to scope its existing
`unknown_block_ids` check to the `minecraft` namespace so mod ids are classified by the
profile, not double-flagged.

**3. Default profile is `full`.**
Shipped artifacts carry catalog mod ids; defaulting to `full` keeps every documented
validate command green with no flag. `vanilla` is opt-in and proven against
temp-dir-generated mod-free output, not the shipped tree. Rationale: the repo already
ships full-profile output; a `vanilla` default would fail the shipped artifacts and the
AGENTS.md command list on day one. Alternative — required flag — rejected as a
gratuitous break of existing commands.

**4. Generators filter slots by profile; `full` is byte-identical to today.**
Generators currently call `load_style(style_id)` with no filter. They will call
`load_style(style_id, available_namespaces=modset_namespaces(profile))`. Because every
shipped slot id is in a confirmed namespace (Phase 3 task 3.7), filtering under `full`
removes nothing, so `full` output is byte-identical to the current no-filter output
(asserted in verification). `vanilla` filtering drops mod ids to fallbacks. Rationale:
this is what makes "generation and validation share one source of truth" literally
true — both sides call into `modset` with the same profile name.

## Risks / Trade-offs

- **A future slot id from an unconfirmed namespace** would pass generation (no filter
  catches it if it slips in) but fail `full` validation as `unknown_mod_blocks`. That is
  the intended guard, and it is exactly the contradiction Phase 4 closes; the failure
  message names the offending id.
- **Default `full` means the shipped tree is never auto-checked for mod-freeness.** That
  is correct: the shipped tree is *meant* to carry mod ids. Mod-freeness is a property
  of `vanilla` output, which is validated where it is generated.
- **Catalog drift:** if `exmod/mod_block_catalog.json` is regenerated and an id is
  dropped, previously-valid shipped structures referencing it would fail `full`
  validation. This is desirable (it surfaces a real catalog/artifact mismatch) and is
  covered by the existing Phase 0 catalog-review gate.

## Migration / Verification

- `full` validation of the shipped artifacts passes unchanged (regression guard).
- Generate each affected library under `--profile vanilla` into a temp dir; confirm no
  non-`minecraft` id appears and `--profile vanilla` validation passes clean.
- Generate under `--profile full` into a temp dir; confirm byte-identical to the
  current shipped output (no-filter equivalence).
- Inject a synthetic non-confirmed-namespace id and a non-catalog mod id into a palette
  and confirm `forbidden_mod_blocks` / `unknown_mod_blocks` respectively.
- Existing `check_style_policy.py`, `check_cultivation_forms.py`, town and runtime
  validators continue to pass.
