# Agent Notes

This repository is scaffolded for a Minecraft town mod and related blueprint conversion tools.

## Working conventions

- Keep docs in `docs/ai-kb/` concise and factual.
- Keep example files small enough to inspect by hand.
- Prefer deterministic converters and validators in `tools/`.
- Treat `src/main/resources/data/myvillage/` as the current implemented mod data namespace. `minecraft_town_mod` is only a historical placeholder unless a dedicated rename change updates code, resources, specs, and docs together.
- Treat implementation as incomplete until it is reflected in the actual mod resources and the user-facing usage docs. For generated structures, commands, galleries, styles, or gameplay-facing behavior, this means regenerating or updating `src/main/resources/data/myvillage/`, building the mod jar when practical, and updating `README.md` command/usage instructions in the same change.
- Add new settlement families through `tools/buildgen/groups.py`: each group binds a style profile, archetype roster, layout strategy, and scale parameters. Avoid detecting families by matching `style_id` prefixes.
- Add new roof or motif forms through the registries in `tools/buildgen/ops.py`, then list them in style `allowed_roof_types` or `allowed_motifs`. Do not add new form dispatch by string-matching inside passes.
- Keep `CHANGELOG.md` current when preparing a release or accepted fix. Large feature additions bump `0.x.y` to `0.(x+1).0`; small feature additions bump `y`; a single validated fix keeps the base version and adds `-fix1`, `-fix2`, and so on. Version changes must update `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.
- For staged manual acceptance, prepare both the buildable mod artifact and the command documentation before asking for visual review. The minimum prep is `python3 tools/generate_all_structures.py`, `python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure`, `python3 tools/validate_compound_library.py --count 6`, `python3 tools/validate_civic_library.py`, `python3 tools/check_style_policy.py`, `python3 tools/check_cultivation_forms.py`, `./gradlew build`, current generated/validated resources, and an up-to-date README command list for `/myvillage list`, `/myvillage place <structure_id>`, `/myvillage place tavern_001`, `/myvillage place lord_manor_001`, `/myvillage place cultivation_house_001`, `/myvillage place cultivation_sect_001`, `/myvillage gallery`, `/myvillage gallery original`, and `/myvillage gallery cultivation`.
- When commands or acceptance prep steps change, update `README.md`, this `AGENTS.md`, and the relevant `openspec/specs/` documents in the same change.
