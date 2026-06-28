## A. Outdated / incorrect wording

- [x] A.1 `courtyard-compound`: reword the stale "multi-进 out of scope / deferred
      per §E" clause in the Chinese one-courtyard requirement to point at
      `chinese-mansion-compound` as the separate multi-进 family.
- [x] A.2 `courtyard-compound`: fix the `one-진` → `一进` Hangul typo (two
      occurrences in the small-courtyard requirement + its scenario).
- [x] A.3 `validation`: drop the `(unchanged this turn)` parenthetical from the
      mansion-invariant requirement.

## B. Code-vs-spec conflicts

- [x] B.1 `mod-decor-block-family`: amend the "three blockstate properties"
      contract to allow an optional `waterlogged` for water-contact classes
      (rockery); add the rockery `waterlogged=true` + non-water-class scenarios.
- [x] B.2 `building-orientation-variants`: change the form-rule table's 楼阁 row
      from "toward its enclosing yard" to "north" with an explanatory paragraph
      and a "楼阁 facing is north" scenario.
- [x] B.3 `chinese-mansion-compound`: sync the 后院 楼阁 requirement + the
      "Every building faces its yard" requirement to the north facing.
- [x] B.4 `validation`: add `楼阁→north` to the mansion-invariant facing list.
- [x] B.5 `compound-enclosure-planning`: soften the two "NOT from z-band tuple"
      assertions — manifest forbids only hard-coded band-relative coords; 进
      sequence clarifies planner MAY use z-rows internally while the validator
      uses derived adjacency. Also fix the 楼阁 anchor wall from "north" to
      "south" in the anchor-wall requirement (caught in F.3 — the tower sits at
      the south edge of the 后院, code has `anchor="south"`).

## C. Consolidation

- [x] C.1 Fold `mansion-gate-house`'s 5 requirements into `chinese-mansion-compound`
      as a new "South entrance = gate_house through-building" requirement group
      (with a note recording the consolidation source).
- [x] C.2 `git rm` the `openspec/specs/mansion-gate-house/` directory.
- [x] C.3 Repoint the active `mansion-gate-house` references: `validation`
      (mansion-invariant bullet), `docs/ai-kb/10_civic_family.md` (see-also),
      `docs/ai-kb/14_deferred_roadmap.md` (see-also). Leave
      `openspec/changes/archive/…` references as historical snapshots.
- [x] C.4 Add a "Companion spec" note to `compound-enclosure-planning` Purpose
      and `building-orientation-variants` Purpose, stating the two are tightly
      coupled halves of `rebuild-mansion-enclosure-plan`.

## D. Redundant restatements

- [x] D.1 `garden-rockery`: in "Variant models are generated offline", reference
      the `mod-decor-block-family` protocol for the AABB-≤-32 merge instead of
      restating it; drop the redundant "AABB count within vanilla's soft limit"
      scenario (keep the model-VoxelShape-agreement scenario).
- [x] D.2 `courtyard-compound`: compress the "Ground path connects every door"
      requirement to a binding statement that defers to `courtyard-path-network`
      / `courtyard-ground-layer`; collapse its 4 restated scenarios into 1;
      keep the corridor-vs-path terminology note.

## E. Knowledge-base + governance sync

- [x] E.1 `docs/ai-kb/INDEX.md`: add the 12 missing specs
      (`chinese-vernacular-roof-vocabulary`, `building-orientation-variants`,
      `chinese-mansion-compound`, `huipai-tianjing-mansion`,
      `compound-enclosure-planning`, `courtyard-voxel-walkability`,
      `courtyard-ground-layer`, `courtyard-path-network`, `garden-rockery`,
      `mod-decor-block-family`) to the correct layer; note `mansion-gate-house`
      is subsumed by `chinese-mansion-compound`.
- [x] E.2 `docs/ai-kb/14_deferred_roadmap.md` §E.3: repoint the
      `huipai-tianjing-mansion` see-also from the pre-archive path to
      `openspec/specs/`.
- [x] E.3 Confirm `AGENTS.md`'s `chinese_mansion` paragraph has no stale
      `mansion-gate-house` path reference and already lists `tower_house north`
      (consistent with B.2/B.3). — confirmed, no edit needed.

## F. Verification

- [x] F.1 `grep -r "mansion-gate-house"` over the repo: only matches inside
      `openspec/changes/archive/…` (historical), the two `compound.py` comments
      (historical source pointers), this change's own proposal/tasks, the
      consolidation note in `chinese-mansion-compound`, and the "subsumes the
      former mansion-gate-house" note in INDEX.md. No active spec/doc reference
      to the removed directory.
- [x] F.2 `grep -r "one-진\|unchanged this turn"` over `openspec/specs/` and
      `docs/ai-kb/`: no matches (remaining hits are inside
      `openspec/changes/archive/…` and this change's own proposal/tasks).
- [x] F.3 Read the edited `chinese-mansion-compound` + `building-orientation-
      variants` + `compound-enclosure-planning` + `validation` end-to-end: 楼阁
      facing is "north" consistently across the form-rule table, the 后院
      requirement, the "Every building faces its yard" requirement, the
      mansion-invariant facing list, AND the anchor-wall requirement (caught a
      stale "楼阁 anchors north" → fixed to "anchors south").
- [x] F.4 Read the edited `mod-decor-block-family`: the contract reads "three
      base properties + optional waterlogged" and the rockery scenario covers
      `waterlogged=true`.
