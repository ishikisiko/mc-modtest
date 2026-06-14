# Versions

## Target versions

- Minecraft: 1.20.x and 1.21.x references are tracked in `blocks_120.json` and `blocks_121.json`.
- Mod loader: NeoForge.
- Python tools: Python 3.11 or newer.

Update this document when the exact Minecraft, NeoForge, and Java versions are finalized.

## Mod version policy

- Keep `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`,
  README jar-name examples, and `CHANGELOG.md` in sync when the mod version
  changes.
- Large feature additions bump `0.x.y` to `0.(x+1).0`.
- Small feature additions bump `0.x.y` to `0.x.(y+1)`.
- Single validated fixes use ordered suffixes such as `0.x.y-fix1` and
  `0.x.y-fix2` after the relevant validation passes.
