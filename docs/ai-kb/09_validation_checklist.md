# Validation Checklist

## Blueprint validation

- Blueprint JSON parses successfully.
- `schema_version` is supported.
- `id` is present and namespaced.
- `size` has three positive integers.
- Every block position is within bounds.
- Every palette reference exists.
- Every block id is namespaced.
- Blockstate values are strings.
- Exported structures can be loaded by the target tool or game version.

## Generated report artifacts

Files under `reports/` are deterministic generator/validator outputs and are
ignored by default. Regenerate them locally with the validation commands below.
The two tracked exceptions are hand-curated inputs or historical snapshots:
`reports/town_distinctness_calibration.json` and
`reports/cultivation_style_baseline_hashes.txt`.

The `reports/*_library_report.json` files store compound/building graphs in
summary form. Per-cell lists such as `parcel_nodes[].cells` and
`building_slots[].footprint` are folded into `*_count` plus `bbox`; non-volume
massing nodes and node `meta` are dropped. Validator-read report fields are
preserved: `building_slots[].massing_graph.meta.frontage` for cultivation town,
`meta.terrace_levels` for cultivation sect, and the volume node's `origin` /
`size`.

This summary shape does not alter in-memory `to_dict()` data or generated `.nbt`
output. If a task needs the full per-cell graph, inspect `to_dict()` in code or
read the `.nbt` instead of expecting generated reports to carry the full graph.

## Acceptance / preview command checklist

Run before asking for staged manual (visual) review. Prepare both the buildable
artifact and up-to-date command docs. Generate and validate:

```text
python3 tools/generate_all_structures.py
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_mod_items.py
python3 tools/validate_custom_entities.py
python3 tools/validate_rideable_flying_sword.py
python3 tools/validate_cultivation_core.py
python3 tools/validate_guideme_cultivation_guide.py
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/buildgen/tests/test_huipai_reference_slice.py
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
python3 tools/buildgen/tests/test_ganlan_stilted_house.py
python3 tools/buildgen/tests/test_pagoda_landmark.py
python3 tools/validate_civic_library.py
python3 tools/validate_town_generation.py
python3 tools/validate_runtime_town_plan.py
python3 tools/validate_sect_generation.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
```

Then build previews and the mod jar:

```text
python3 tools/preview_structure.py --all          # offline PNGs + per-structure viewer.html
python3 tools/generate_town_plan_preview.py --count 6   # top-down town-plan PNG/HTML under out/preview/town_plan_s* (default covers all 6 wall families)
python3 tools/generate_sect_plan_preview.py --count 6   # top-down sect-plan PNG/HTML under out/preview/sect_plan_s* (default covers all 3 detached-spire variants + absent)
./gradlew build
```

For the cultivation core, run the deterministic gates and a bounded dedicated
server smoke:

```text
openspec validate --specs --strict
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_guideme_cultivation_guide.py
python3 -m unittest discover -s tools/tests -p 'test_validate_*.py'
./gradlew test
./gradlew build
python3 tools/run_chunky_acceptance.py --stage 1
```

Stage 1 provisions an isolated acceptance profile, waits for RCON, runs a
bounded server lifecycle smoke, sends `save-all` and `stop`, and waits for a
clean process exit. Inspect `run-acceptance/logs/latest.log` for cultivation
registry/attachment/command registration, the clientbound cultivation snapshot,
the existing serverbound flying-sword payload, and the absence of
registry-freeze, codec, datapack-path, payload-direction, duplicate-handler,
and client-only classloading errors. The startup smoke is
registration/side-safety evidence, not manual lifecycle evidence.

Record every cultivation manual item as `pass`, `fail`, or `not_verified`.
Leave unobserved items as `not_verified` even when the validator, tests, build,
and server smoke pass:

1. New-player `info` shows schema 3 and the exact default profile, including affinity 10, zero lifespan consumption, and preserved zero legacy reserve.
2. A legal five-element `setroot` totaling 10000 succeeds.
3. A `setroot` total other than 10000 is rejected and the old profile is unchanged.
4. A registered realm-stage pair succeeds through `setrealm`.
5. A stage outside the selected realm is rejected without mutation.
6. Registered `myvillage:basic_breathing` can be learned at zero mastery.
7. An unregistered technique is rejected without mutation.
8. A non-default profile survives save and server restart.
9. A non-default profile survives true death without field loss.
10. End return preserves the profile once, without a duplicate copy or merge.
11. Dimension change delivers the latest snapshot to the owning client.

The playable-loop acceptance ledger in
[README.md](../../README.md#in-game-acceptance) additionally covers spirit-stone
appearance, Silk Touch/Fortune/worldgen, V/B/X/N and H-button parity,
interruption behavior, calendar/lifespan, affinity gain, direct layer-priced
stone batches, pre-cap stability locking, post-cap affinity consolidation,
stage-derived stability caps, success halving, deterministic advancement, and
both H-screen tabs. Automated
gates do not turn any unobserved real-client item into `pass`.

For the GuideME cultivation guide, also run:

```text
openspec validate add-guideme-cultivation-guide-slice --type change --strict
python3 tools/validate_guideme_cultivation_guide.py
python3 -m unittest tools.tests.test_validate_guideme_cultivation_guide
./gradlew test
./gradlew build
./gradlew runGuide
./gradlew runAcceptanceServer
```

`runGuide` must watch root `guidebook/`, validate and open
`myvillage:cultivation`, and remain a bounded startup smoke unless a reviewer
actually interacts with the client. The dedicated-server run checks the required
GuideME dependency and side safety. Neither run can infer a pass for default
Chinese fallback, English switching, navigation, search, item-index jumps,
`ItemLink`/`BlockImage`, configured key rendering, live reload, handbook
open/reopen, the post-fix GuideME `G` item-index hotkey, or existing H/gameplay regression. Record
all unobserved surfaces as `not_verified` in the README ledger.

For the rideable flying sword, also run the dedicated-server gate:

```text
python3 tools/validate_rideable_flying_sword.py
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

`runAcceptanceServer` proves registration and common/client separation only.
Before accepting the gameplay or appearance, verify in a real client/server
session:

- `/give @s myvillage:rideable_flying_sword`, first-use spawn/mount, and
  second-use recall without a replacement or duplicate.
- W/S forward/backward, A/D strafe, Space ascent, and Shift descent without
  vanilla dismount.
- Neutral hover and gradual slowdown, solid-block collision, horizontal yaw
  following, rider fall-distance reset, and no damage when the mounted sword
  descends onto a solid block.
- Owner-only single-passenger control and multiplayer observation of
  server-driven motion.
- Cleanup after owner death, logout, dimension change, and more than 64 blocks
  of separation, plus `noSave()` removal across unload/reload.
- Smooth riding without repeated position/yaw snaps; blade-tip-forward horizontal
  item-model scale, orientation, texture readability, and absence of
  missing-model fallback.

Optional in-game automation with Chunky:

```text
python3 tools/run_chunky_acceptance.py --stage 1   # isolated server + Chunky + RCON lifecycle
python3 tools/run_chunky_acceptance.py --stage 2   # Stage 1 plus RCON myvillage ...at smoke
python3 tools/run_chunky_acceptance.py --stage 3   # Stage 2 plus full optional-mod preflight/cases
python3 tools/run_chunky_acceptance.py --stage 4   # Stage 3 plus locate natural sect + bounded Chunky
```

Stage 1 must pass before later Chunky stages are trusted. Stage 2 records
`myvillage list`, `placeat`, `galleryat cultivation`, `townat`, `sectat`, and
`sectat worldgen` command responses in `reports/chunky_acceptance_report.json`.
Stage 3 extracts `exmod/mod_jars.zip`, verifies the expected full optional-mod
ids and mandatory jar dependencies, then runs gallery/town/worldgen-sect cases
only if that preflight passes. Missing dependency jars, such as `architectury`
for Fetzi's Displays, fail before server startup and are recorded in the report.
Stage 4 runs only after Stage 3 passes; it records the `/locate structure
myvillage:sect` response, the selected Chunky center/radius, and completion
state, or records `sect_not_located` when no sect is found.
This report supplements the offline validators and preview server; it does not
replace visual review.

Chunky path-traced renderer PNGs, when produced separately via
`tools/render_structure.py`, are currently limited to ordinary block appearance
checks. Do not use them as visual acceptance evidence for custom `myvillage:`
blocks such as `myvillage:rockery_block`: as of 2026-07-01 Chunky 2.4.6 renders
that block as an unknown-block placeholder even when the MyVillage jar is passed
as a texture pack. Custom-block appearance still requires Minecraft client
inspection or a future dedicated renderer compatibility path. For ordinary
layout/framing review, `tools/render_structure.py` defaults to the multi-camera
`--view-plan survey` (mid-height cardinal views plus high diagonal views); use
`--view-plan height-sweep` when the review depends on comparing low, mid, and
high camera elevations. Multi-view renderer runs write a manifest-linked
`contact_sheet.png` by default for quick angle/height comparison.

Visual review handoff after previews and any Chunky run:

```text
python3 tools/write_visual_acceptance_report.py
```

This writes `reports/visual_acceptance_report.json` and
`reports/visual_acceptance_report.md`. The report verifies that
`out/preview/index.html`, representative isometric/contact-sheet PNGs, plan
previews, and the latest Chunky command targets are present. It is an inspection
checklist, not an image classifier: before reporting that visual verification was
performed, the agent must open representative PNGs from the report and summarize
what was checked. In-game final appearance review still belongs to the reviewer.

- When more than one viewer is generated, ensure the aggregate `out/preview/index.html`
  exists — it is the review entry point.
- Serve the previews for review:
  `python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview`.
  Keep the server running until the user says it can be closed, or until the related
  OpenSpec change is being archived. (The review host/IP is environment-specific; report
  the host's own address, not a hardcoded one.)
- Keep the documented command list (`README.md`, `/myvillage list`, etc.) current in the
  same change when commands or acceptance steps change.

## See also

- Spec: [validation](../../openspec/specs/validation/spec.md) — the normative validation requirements.
- Cultivation core: [Cultivation Core Foundation](28_cultivation_core.md) and its [validation spec](../../openspec/specs/cultivation-core-validation/spec.md).
- GuideME cultivation guide: [GuideME Cultivation Guide](31_guideme_cultivation_guide.md) and its [change spec](../../openspec/changes/add-guideme-cultivation-guide-slice/specs/guideme-cultivation-guide/spec.md).
- Flying sword: [Rideable Flying Sword](27_rideable_flying_sword.md) and its [change spec](../../openspec/changes/add-rideable-flying-sword/specs/rideable-flying-sword/spec.md).
- Index: [Knowledge Base Map](INDEX.md).
