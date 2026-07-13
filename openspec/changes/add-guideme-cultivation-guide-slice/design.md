## Context

MyVillage 0.25.0 already ships the first complete cultivation loop: spiritual-root awakening, Basic Breathing inheritance, normal or spirit-stone meditation, progress followed by stability consolidation, deterministic advancement, lifespan/calendar constraints, and a Qi Refining IV release ceiling. The current README acceptance section and H screen expose that truth, but there is no in-game guide that connects the steps for an ordinary player.

GuideME 21.1.17 provides a data-driven guide format, item/block indexing, live key-binding components, and a public open-guide API for Minecraft 1.21.1. Integrating it crosses Gradle dependency resolution, NeoForge load metadata, client resources, one functional item, development run configuration, packaging, dedicated-server compatibility, and user-facing validation. The root-level `guideme-21.1.17.jar` is the user-provided, untracked official release shadow jar; it is useful for inspection or ad hoc testing but is not a reproducible dependency source for this repository.

The first slice must prove compatibility without creating a second documentation system. Chinese is the authored default, English is a complete paired translation, and the shipped guide remains deliberately small enough to audit against the current 0.25.0 behavior.

## Goals / Non-Goals

**Goals:**

- Require GuideME on both physical sides and resolve its API/full runtime artifacts reproducibly from Maven Central.
- Ship the data-driven `myvillage:cultivation` guide from one root `guidebook/` source with exactly three concise Chinese pages and three path-matched English pages.
- Cover the complete released cultivation loop and its Qi Refining IV ceiling without documenting planned mechanics as available.
- Display the player's configured MyVillage controls, index the relevant initiation and spirit-stone entries, and render representative item/block links.
- Leave GuideME's default `G` item-index hotkey available by default without adding integration-specific input interception or automatic binding migration.
- Add a one-stack `myvillage:cultivation_handbook` that opens the guide through GuideME without adding a MyVillage payload.
- Make `runGuide` live-preview the root Markdown truth that `processResources` packages in the jar.
- Add deterministic source/package validation plus bounded client and dedicated-server compatibility handoffs.

**Non-Goals:**

- A full long-form manual, MkDocs site, Wiki publication, or a common-source generator for external and in-game documentation.
- More than the paired index, initiation, and combined cultivation-loop pages in this slice.
- Qi Refining V or later progression, Foundation Establishment gameplay, pills, facilities, reincarnation, or any other unimplemented cultivation system.
- A separate handbook texture or a new visual identity for the book.
- Changes to cultivation authority, payloads, progression arithmetic, generated structures, worldgen behavior, or existing resource ids.
- Automatic rewriting of an existing player's saved key bindings.

## Decisions

### Resolve GuideME from Maven Central and declare it required

`mavenCentral()` remains the Gradle repository of record. A single `guideme_version=21.1.17` property feeds a `compileOnly` dependency on the published `api` classifier and a `runtimeOnly` dependency on the published full ProGuard mod artifact. NeoForge metadata declares `guideme` as a required, `BOTH`-side dependency with a compatible range beginning at 21.1.17.

The root-level jar will not be referenced through `files(...)`, `flatDir`, copied into a repository library directory, nested into the MyVillage jar, or committed. This keeps command-line builds and CI independent of one workstation's untracked file while still allowing that file to serve as supplementary inspection evidence. A soft dependency was rejected because direct GuideME API use would otherwise require class-loading isolation and a degraded no-guide mode that this compatibility slice does not need.

### Keep the guide data-driven and bounded to three paired pages

The guide definition lives at `src/main/resources/assets/myvillage/guideme_guides/cultivation.json`, which creates `myvillage:cultivation` and selects `zh_cn` as the default language. The only authored Markdown truth lives at repository-root `guidebook/`: Chinese pages use their ordinary relative paths, and English mirrors those paths under `_en_us/`. `processResources.from('guidebook').into('assets/myvillage/guides/myvillage/cultivation')` copies that source only into Gradle's generated resource output for packaging; it does not create a second checked-in page tree.

The only authored page paths in either language are:

```text
index.md
getting_started/initiation.md
getting_started/cultivation_loop.md
```

`index` states the playable order, points to the two detail pages, summarizes the version boundary, and distinguishes released systems from deferred ones. `getting_started/initiation` owns the testing-stele and inheritance-stele sequence. `getting_started/cultivation_loop` deliberately combines the H-screen controls, ordinary/spirit meditation, spirit-stone acquisition and costs, progress/stability ordering, lifespan eligibility, and deterministic advancement. Splitting those topics into a larger tree was rejected because the immediate goal is a compatibility proof, not the later full handbook.

The first guide definition does not declare `custom_colors`, and its pages do not reference a custom color id. GuideME 21.1.17 has an upstream parameter-order defect in the custom-color codec/`ConstantColor` path, so custom palette styling would add a known compatibility hazard without helping prove the guide flow. Built-in presentation remains sufficient for this slice; custom colors can be reconsidered after an upstream-fixed version is adopted and tested.

### Treat current runtime truth as the content contract

The pages are written against the implemented 0.25.0 registries, resources, and server rules rather than the older initiation-only narrative. They identify the independent awakening and inheritance steps, Basic Breathing prerequisite, Profile/Meditation tabs, ordinary versus direct-stone meditation, progress-before-stability ordering, stage-owned costs and limits, deterministic one-stage advancement, stability halving, lifespan gating, and the Qi Refining IV ceiling. They do not imply recipes or world placement for the steles, retroactive ore generation, random advancement, or post-ceiling gameplay.

All five configurable controls use GuideME `KeyBind` components with the existing translation ids for profile, normal meditation, spirit meditation, stop, and advancement. Prose may explain an action but does not present H/V/B/X/N as immutable instructions. Initiation indexes both stele ids; the combined cultivation-loop page indexes the low-grade stone and both ore blocks. `ItemLink` and `BlockImage` components use fully qualified shipped ids so missing or stale references can be validated.

### Release GuideME's G binding instead of layering conflict logic

The first real-client review confirmed the guide UI but found GuideME's `G`
item-index hotkey globally unavailable while MyVillage also registered stop
meditation on `G`. The corrective default is `X` for MyVillage stop meditation.
MyVillage does not inspect, consume, remap, or otherwise special-case GuideME's
mapping; its pre-existing ordinary screen guard and bounded intent path remain
unchanged. This is a default change, not a migration: an installation that has
already saved the old `G` mapping must reset or rebind `Stop Meditation` in the
Controls screen.

### Preview the packaged source tree directly

The `runGuide` client run points `guideme.myvillage.cultivation.sources` directly at repository-root `guidebook/`, sets the matching source namespace, and sets both `guideme.showOnStartup` and `guideme.validateAtStartup` to the guide id `myvillage:cultivation`. GuideME then resolves the configured default `myvillage:index.md`; the fully qualified `myvillage:cultivation!myvillage:index.md` form remains an acceptable diagnostic target but is not the required run configuration. Normal resource processing copies the same source into the jar path without writing generated pages back into `src/main/resources`. Acceptance and troubleshooting use GuideME 21.1.17's actual command order, `/guidemec myvillage:cultivation open`, rather than the inverted `/guidemec open ...` form.

A checked-in mirror under `src/main/resources/assets/myvillage/guides/` was rejected. Keeping `guidebook/` as the only editable Markdown tree and treating `build/resources` as disposable output avoids manual synchronization while preserving standard jar paths.

### Reuse GuideME's base book visual for the custom entry item

`myvillage:cultivation_handbook` is a one-stack functional item in `myvillage:main`. Client use delegates to GuideME's public open-guide API for `myvillage:cultivation`; GuideME retains the last visited page, and MyVillage adds no guide-opening network payload. The item still owns MyVillage bilingual name/tooltip entries and a MyVillage model id, but that model inherits `guideme:item/guide_base`. No `cultivation_handbook.png` is authored in this change.

This is preferred over permanently handing players GuideME's generic item because MyVillage needs a stable namespaced acquisition surface. It is preferred over a new texture because compatibility and navigation, not book art, are the first-slice risk.

### Validate source integrity separately from observed GuideME behavior

A focused validator and negative fixtures check Maven/metadata wiring, the exact paired page topology under `guidebook/`, the one-way `processResources` mapping, guide JSON/frontmatter and internal links, shipped item/block/key-binding references, handbook registration/resources, direct-source `runGuide` configuration, documentation/release synchronization, and practical-jar contents. The jar must contain MyVillage's guide and handbook resources but must not embed GuideME itself.

Gradle tests/build and a bounded dedicated-server startup establish compilation, packaging, hard-dependency resolution, registration, and side safety. A bounded `runGuide` client smoke can establish client startup. Guide discovery, default-language rendering, navigation/search, item-index hotkey behavior, model rendering, live reload, language switching, and handbook interaction remain explicit real-client observations recorded as `pass`, `fail`, or `not_verified`.

## Risks / Trade-offs

- [A required dependency prevents MyVillage from loading without GuideME] -> Make the requirement explicit in NeoForge metadata and README installation guidance; use the same full artifact in development and server smoke.
- [A future GuideME release changes API or authoring behavior] -> Compile against 21.1.17, bound the declared compatibility range, and validate/run the exact first-slice surfaces before widening support.
- [GuideME 21.1.17 custom colors have an upstream parameter-order defect] -> Omit `custom_colors` and custom-color page markup from the first definition; revisit styling only after upgrading to a verified fixed version.
- [Markdown can become stale as cultivation rules evolve] -> Validate ids, page topology, required factual anchors, and key-binding components; future cultivation changes must update the guide alongside their runtime/spec changes.
- [Three pages compress several systems] -> Keep the page split task-oriented and defer reference-level tables and long explanations to the later handbook change.
- [Reusing `guideme:guide_base` couples the item appearance to GuideME] -> Accept that dependency for the compatibility slice and keep the MyVillage model id as the future replacement point.
- [Source checks cannot prove GuideME rendering or interaction] -> Keep client-only observations separate and leave every unobserved visual or interaction item `not_verified`.
- [Existing options can preserve the old G binding after the default changes] -> Document one manual reset/rebind and avoid silently rewriting player configuration.

## Migration Plan

1. Add the Maven-resolved API/runtime dependency and required NeoForge metadata without using the root jar.
2. Add the guide definition and the three Chinese/English page pairs under root `guidebook/`, map that tree into packaged resources, and configure `runGuide` against the root source.
3. Register the handbook item, inherit the GuideME base model, add bilingual item text, and expose it in `myvillage:main`.
4. Add focused validation/tests, classify this compatibility slice as a small feature, and synchronize the 0.25.1 player-facing/release files under the repository's authoritative task rule before building and inspecting the practical jar.
5. Run bounded client and dedicated-server smokes and record real-client guide interactions separately.
6. After the first real-client verdict, move the MyVillage stop default to `X`, release `G` without GuideME-specific interception, append the validated-fix version, and require a fresh item-index hotkey observation.

Rollback removes the isolated item/resources/run configuration, GuideME Gradle coordinates, and required metadata entry together. No existing cultivation profile, world data, or generated resource needs migration; the only new saved-world concern is an item id introduced by this change.

## Open Questions

None for the initial slice. Broader handbook structure, custom art, and Wiki/MkDocs common-source generation are decisions for the follow-up change after compatibility evidence is accepted.
