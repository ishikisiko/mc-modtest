## 1. Phase 1 — Form registry migration (behavior-preserving)

- [x] 1.1 Capture a baseline: regenerate `medieval_village` and `chinese_courtyard` libraries and record NBT hashes for regression comparison
- [x] 1.2 Add `ROOF_REGISTRY` (name→handler) in `tools/buildgen/ops.py` and register `gable_roof`, `cross_gable_roof`, `lean_to_roof` verbatim
- [x] 1.3 Add `MOTIF_REGISTRY` (name→handler) in `tools/buildgen/ops.py` and migrate every existing motif branch into registered handlers
- [x] 1.4 Replace the roof-type and motif `if`/branch dispatch in `tools/buildgen/passes.py` with registry lookups
- [x] 1.5 Raise a clear error on unregistered roof/motif names (no silent fallback)
- [x] 1.6 Add style-vocabulary validation: `allowed_roof_types`/`allowed_motifs` must exist in the registries
- [x] 1.7 Regenerate legacy libraries and assert byte-identical output vs 1.1 (or review+document any diff)

## 2. Phase 1 — Settlement-group layer

- [x] 2.1 Add a group descriptor in `tools/buildgen/` binding `style_id` + archetype roster + layout strategy + scale params
- [x] 2.2 Define the `cultivation_town` and `cultivation_sect` group descriptors (rosters may be stubs until phases 2–3)
- [x] 2.3 Route archetype selection through the active group's roster; reject out-of-roster archetypes
- [x] 2.4 Replace `export.py` `startswith("chinese_courtyard")` style detection with group-aware resolution
- [x] 2.5 Document the group descriptor as the extension hook in `AGENTS.md` / module docstring

## 3. Phase 1 — Style schema: spirit slots & per-style forbidden

- [x] 3.1 Extend `tools/buildgen/style.py` to recognize `SPIRIT_CRYSTAL` and `RITUAL_METAL` slots (omittable, skip-if-missing)
- [x] 3.2 Confirm the quality forbidden-gate reads the active style's `forbidden_blocks`; add a test for per-style behavior
- [x] 3.3 Add a test: a spirit block passes in sect, fails in town

## 4. Phase 2 — Cultivation town group (mortal)

- [x] 4.1 Author `tools/buildgen/styles/cultivation_town.json` (timber/stone/clay-tile palette, spirit materials forbidden, no spirit slots)
- [x] 4.2 Build the town archetype roster (古风 houses/shops/inn/etc.) reusing the standalone massing model
- [x] 4.3 Wire the town group to the standalone library generator
- [x] 4.4 Generate the town library and pass quality gates
- [x] 4.5 Add town entries to the in-game command/gallery listing

## 5. Phase 3 — Cultivation forms (new vocabulary)

- [x] 5.1 Implement and register `tiered_eave_roof` (重檐), with small-footprint fallback to single eave
- [x] 5.2 Implement and register the `moon_gate` round opening
- [x] 5.3 Implement and register the `spirit_array` ground motif (crystal / formation blocks)
- [x] 5.4 Implement and register `incense_altar` and `cloud_rail` motifs
- [x] 5.5 Add quality checks/gates for the new forms; assert medieval/chinese never invoke them

## 6. Phase 3 — Cultivation sect group (immortal)

- [x] 6.1 Author `tools/buildgen/styles/cultivation_sect.json` (mortal base + 灵材; unlock quartz/copper/gold; `SPIRIT_CRYSTAL`/`RITUAL_METAL`; spirit-glow `LIGHTING`; `tiered_eave_roof` in `allowed_roof_types`; 仙宫 sub-flavor default)
- [x] 6.2 Build the sect archetype roster (山门/大殿/藏经阁/炼丹房/弟子居 …) with sect massing builders
- [x] 6.3 Extend `tools/buildgen/compound.py` with the sect terraced/axial layout strategy (monumental scale, hierarchical slots, optional terraces with level circulation)
- [x] 6.4 Wire the sect group to the compound layout
- [x] 6.5 Generate the sect library/compound and pass quality gates
- [x] 6.6 Add sect entries to the in-game command/gallery listing

## 7. Validation & docs

- [x] 7.1 Update affected reports under `reports/` for the new libraries
- [x] 7.2 Regression: confirm legacy medieval/chinese output still byte-stable end-to-end
- [x] 7.3 Update `AGENTS.md`/`README.md` with the group concept, cultivation styles, and how to add a new form/group
- [x] 7.4 Resolve or re-file the open questions (sub-flavor, town spirit-bleed, town layout granularity)
