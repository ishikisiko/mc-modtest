# Agent Notes

This repository is scaffolded for a Minecraft town mod and related blueprint conversion tools.

## Working conventions

- Keep docs in `docs/ai-kb/` concise and factual.
- Keep example files small enough to inspect by hand.
- Prefer deterministic converters and validators in `tools/`.
- Treat `src/main/resources/data/myvillage/` as the current implemented mod data namespace. `minecraft_town_mod` is only a historical placeholder unless a dedicated rename change updates code, resources, specs, and docs together.
- For staged manual acceptance, prepare both the buildable mod artifact and the command documentation before asking for visual review. The minimum prep is `./gradlew build`, current generated/validated resources, and an up-to-date README command list for `/myvillage list`, `/myvillage place <structure_id>`, and `/myvillage gallery`.
- When commands or acceptance prep steps change, update `README.md`, this `AGENTS.md`, and the relevant `openspec/specs/` documents in the same change.
