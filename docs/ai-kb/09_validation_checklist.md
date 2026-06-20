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
