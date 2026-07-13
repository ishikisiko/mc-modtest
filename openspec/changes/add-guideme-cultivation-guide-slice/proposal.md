## Why

MyVillage now ships a complete first cultivation loop, but players must leave the game or read a long acceptance section to learn its order, controls, and release ceiling. GuideME can provide that information in context, but the dependency, data-driven page format, dedicated-server compatibility, and custom handbook entry point must first be proven against the current NeoForge runtime before building a larger documentation pipeline.

## What Changes

- **BREAKING** Require GuideME in the compatible range `[21.1.17,22)` on both client and server, using the published 21.1.17 API for compilation and full mod at development runtime.
- Add the data-driven `myvillage:cultivation` guide with Chinese as its default language, three concise Chinese pages covering the complete released loop, and matching English translations.
- Index the initiation steles and spirit-stone resources from the relevant pages, render representative block/item links, and show the real configurable MyVillage key bindings where applicable.
- Add `myvillage:cultivation_handbook` as a one-stack functional item in `myvillage:main`; using it opens the cultivation guide without adding another MyVillage network payload.
- Add a `runGuide` client configuration that live-reloads the packaged guide source directory rather than introducing a second copy of the Markdown pages.
- Add focused guide/resource validation, practical jar inspection, client and dedicated-server smoke coverage, player-facing usage/acceptance documentation, and the required feature version/changelog synchronization.
- Incorporate the first real-client verdict: retain the accepted guide UI, release GuideME's default `G` item-index hotkey by moving MyVillage's default stop-meditation key to `X`, and add no GuideME-specific interception or binding migration.
- Keep a shared Wiki/MkDocs generator, a complete long-form manual, and post-Qi-IV or otherwise unimplemented cultivation systems out of this first compatibility slice.

## Capabilities

### New Capabilities
- `guideme-cultivation-guide`: GuideME dependency compatibility, the bilingual data-driven cultivation guide, the custom handbook item, live preview, and the exact first-slice content boundary.

### Modified Capabilities
- `cultivation-meditation`: Change the configurable stop-meditation default from `G` to `X` while leaving GuideME's mapping and ordinary key-processing behavior untouched.
- `resource-export`: Package the GuideME definition, Markdown pages, handbook model/language resources, and registered item in the practical MyVillage jar without changing structure generation.
- `validation`: Add deterministic guide integrity and packaging checks plus bounded client/dedicated-server compatibility handoff requirements.

## Impact

The change affects Gradle dependency/run configuration, NeoForge dependency metadata, one item class and item registration, one default cultivation key mapping, client assets and bilingual Markdown resources, focused Python validation/tests, README/KB/agent guidance, and release metadata. Existing cultivation authority, profile persistence, network payloads, generators, generated NBT, and worldgen behavior remain unchanged; installations that omit GuideME will no longer load MyVillage.
