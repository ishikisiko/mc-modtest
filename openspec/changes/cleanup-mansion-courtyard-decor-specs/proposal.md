# cleanup-mansion-courtyard-decor-specs

## Why

A review of the 徽派/江南大宅/庭院/装饰 specs against the shipped code surfaced
several places where the spec text had drifted from the implementation, was
internally inconsistent, or duplicated content that another spec owned. None of
these affected shipped behavior — the code and assets are correct — but each one
risks misleading a future maintainer who reads the spec as the contract.

The audit compared 14 specs in this cluster against `tools/buildgen/compound.py`,
`tools/buildgen/styles/chinese_mansion.json`, the `rockery_block.json`
blockstate, and `docs/ai-kb/14_deferred_roadmap.md`. The findings fall into four
classes (outdated wording, code-vs-spec conflicts, consolidable specs, and
redundant restatements); this change fixes all of them as a spec-only revision.

## What Changes

### A. Outdated / incorrect wording

1. **`courtyard-compound` — multi-进 out of scope.** The single-进 requirement
   claimed `multi-进 layouts are out of scope … the jin_count master axis is
   deferred per 14_deferred_roadmap.md §E`. 3-进 `chinese_mansion` has been
   shipped since `rebuild-jiangnan-mansion`; the deferral is stale. Reworded to
   "the single-进 `chinese_courtyard` family has exactly one 垂花门; multi-进
   forms are a separate compound family (`chinese-mansion-compound`), not a
   `jin_count` axis on this family."

2. **`courtyard-compound` — `one-진` typo.** The small-courtyard requirement used
   the Korean Hangul character `진` (should be `进`). Fixed to `一进` (two
   occurrences).

3. **`validation` — `(unchanged this turn)`.** The mansion invariant requirement
   ended with `… SHALL remain in validate_compound for the chinese_courtyard
   family (unchanged this turn).` "this turn" is change-draft phrasing with no
   meaning once archived. Dropped the parenthetical.

### B. Code-vs-spec conflicts

4. **`mod-decor-block-family` — `waterlogged`.** The contract stated "every decor
   blockstate exposes three properties: variant × facing × moss_level". The
   shipped `rockery_block.json` actually exposes four — `facing × moss_level ×
   variant × waterlogged` — because 假山 reads as 山脚入水 / 汀步 across a pond.
   Contract amended: the three base properties are mandatory; a class MAY add
   `waterlogged` when its placement contacts water. Added a scenario covering
   the rockery `waterlogged=true` case and a non-water class that omits it.

5. **`building-orientation-variants` + `chinese-mansion-compound` — 楼阁 facing.**
   The form-rule table listed `楼阁 (tower_house) → toward its enclosing yard`
   (which reads as south), but `_plan_mansion_enclosure` forces `facing="north"`
   with a comment explaining why a south door would throw the porch colonnade
   back across the 二门 into the 主院 plinth. The table now reads
   `楼阁 → north (楼阁坐南朝北，门开后院北侧院落空间)` with an explanatory
   paragraph and a "楼阁 facing is north, not south" scenario. The facing list in
   `chinese-mansion-compound`'s "Every building faces its yard" requirement and
   the 后院 楼阁 requirement were synced, and `validation`'s mansion-invariant
   facing list gained the `楼阁→north` entry.

6. **`compound-enclosure-planning` — `NOT from z-band tuple`.** Two requirements
   asserted the layout is produced "NOT from pre-cut z-band tuples" / validated
   "NOT by z-band tuple comparison". The planner does derive inner-gate z-rows
   from yard-depth parameters internally; what is true is that the *validator*
   asserts the 进 sequence via derived-yard adjacency, not by reading a stored
   z-band tuple. Softened both: the manifest requirement now forbids only
   hard-coded band-relative coordinates (`oy0 + N`) and allows yard-depth-derived
   z; the 进-sequence requirement clarifies the planner MAY use z-rows internally
   while the validator must use derived adjacency. The same pass corrected the
   楼阁 anchor wall in the anchor-wall requirement: it read "楼阁 anchors north"
   but the tower sits at the south edge of the 后院 (code `anchor="south"`), so
   the anchor wall and the (north) door facing had drifted apart.

### C. Consolidation

7. **`mansion-gate-house` → `chinese-mansion-compound`.** The standalone
   `mansion-gate-house` spec (5 requirements, all `chinese_mansion`-only, no
   other spec depends on it as a separate capability) is folded into
   `chinese-mansion-compound` as a new "South entrance = gate_house
   through-building" requirement group. The `openspec/specs/mansion-gate-house/`
   directory is removed. Active references in `validation` (the mansion-invariant
   bullet), `docs/ai-kb/10_civic_family.md`, and `docs/ai-kb/14_deferred_roadmap.md`
   are repointed to `chinese-mansion-compound`. (References inside
   `openspec/changes/archive/…` are left as historical snapshots.)

8. **`compound-enclosure-planning` ↔ `building-orientation-variants` companion
   note.** These two are the two halves of `rebuild-mansion-enclosure-plan` and
   are tightly coupled (every placement in the former carries a facing from the
   latter's form-rule table). A "Companion spec" note is added to each spec's
   Purpose, stating that editing one SHALL prompt a consistency check of the
   other. The specs are NOT merged (per the conservative-merge decision); the
   note reduces the risk of editing one half and forgetting the other.

### D. Redundant restatements

9. **`garden-rockery` — protocol overlap with `mod-decor-block-family`.** The
   "Variant models are generated offline" requirement restated the AABB-≤-32
   merge limit and had a dedicated "AABB count within vanilla's soft limit"
   scenario, both of which `mod-decor-block-family` already owns. The merge step
   now references the family protocol; the redundant scenario is dropped (the
   model-VoxelShape-agreement scenario is kept — it is rockery-specific).

10. **`courtyard-compound` — path/ground restatement.** The "Ground path connects
    every door and landscape feature" requirement restated the endpoint set,
    backbone paving, plinth stair, and door-overlap rules that
    `courtyard-path-network` / `courtyard-ground-layer` define normatively.
    Compressed to a binding statement + one consolidated scenario that defers to
    those specs; the corridor-vs-path terminology note is kept (it is
    courtyard-compound-specific).

### E. Knowledge-base + governance sync

- `docs/ai-kb/INDEX.md` "Capability specs" section was missing 12 specs shipped
  by the `rebuild-jiangnan-mansion` / `rebuild-mansion-enclosure-plan` /
  `add-hero-rockery` / `fix-courtyard-ground-walkability` changes (the
  mansion/courtyard/decor cluster). All 12 are added to the correct layer, and
  the removed `mansion-gate-house` is noted as subsumed by
  `chinese-mansion-compound`.
- `docs/ai-kb/14_deferred_roadmap.md` §E.3's see-also for `huipai-tianjing-mansion`
  pointed at the pre-archive path; repointed to `openspec/specs/` (where the spec
  legitimately lives, as design-only with all requirements `FUTURE:`-prefixed per
  `spec-baseline-governance`'s "current limitation is documented" allowance).

### Withdrawn finding

- **`huipai-tianjing-mansion` location.** The audit initially flagged this spec's
  presence in `openspec/specs/` as a governance conflict (it is not implemented).
  Re-reading `spec-baseline-governance` §"Specs describe implemented behavior
  unless marked otherwise" + the "A current limitation is documented" scenario
  shows the spec is explicitly allowed there because every requirement is
  `FUTURE:`-prefixed and the Purpose states `Status: design retention, not
  implemented`. No change made.

## Impact

- Specs (8 capabilities edited): `courtyard-compound`, `chinese-mansion-compound`,
  `mod-decor-block-family`, `building-orientation-variants`,
  `compound-enclosure-planning`, `garden-rockery`, `validation`.
- Specs (1 capability removed): `mansion-gate-house` (folded into
  `chinese-mansion-compound`).
- Knowledge base: `docs/ai-kb/INDEX.md` (12 specs added to layering + 1 removal
  noted), `docs/ai-kb/14_deferred_roadmap.md` (1 see-also repointed),
  `docs/ai-kb/10_civic_family.md` (1 see-also repointed).
- Code: none. The shipped code (`compound.py`, `rockery_block.json`,
  `chinese_mansion.json`) was already correct; this change makes the specs match
  it. The two `compound.py` comments that mention `mansion-gate-house` are left
  as historical source pointers (the archived spec copy still resolves them).
- Artifacts: none regenerated. This is a spec-only revision with no NBT, jar, or
  version bump.

## Out of scope

- No code changes; no NBT regeneration; no jar build; no version bump.
- No merge of `compound-enclosure-planning` + `building-orientation-variants`
  into one spec (conservative-merge decision; only the companion note is added).
- No change to `huipai-tianjing-mansion` (withdrawn finding).
