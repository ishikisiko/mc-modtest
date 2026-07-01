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

## Acceptance / preview command checklist

Run before asking for staged manual (visual) review. Prepare both the buildable
artifact and up-to-date command docs. Generate and validate:

```text
python3 tools/generate_all_structures.py
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
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
high camera elevations.

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
- Index: [Knowledge Base Map](INDEX.md).
