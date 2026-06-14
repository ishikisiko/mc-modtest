# Changelog

All notable project changes should be recorded here when a version is prepared.

## Versioning Rules

- Large feature additions bump the middle version component and reset the patch
  component: `0.x.y` -> `0.(x+1).0`.
- Small feature additions bump the patch component: `0.x.y` -> `0.x.(y+1)`.
- Single verified fixes keep the base version and add an ordered fix suffix:
  `0.x.y-fix1`, `0.x.y-fix2`, and so on.
- A fix suffix should only be added after the fix passes the relevant
  validation or build step.
- Any version change must update `gradle.properties`,
  `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples,
  and this changelog in the same change.

## Unreleased

### Added

- Split `/myvillage gallery` into the full gallery plus
  `/myvillage gallery original` and `/myvillage gallery cultivation`.

### Documentation

- Added project changelog and versioning rules.

## 0.5.0

### Added

- Generated civic structures, cultivation town structures, standalone
  cultivation sect structures, and cultivation sect compound structures.
- Grouped gallery support for civic, cultivation town, and cultivation sect
  columns.
