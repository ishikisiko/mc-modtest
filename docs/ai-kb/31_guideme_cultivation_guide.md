# GuideME Cultivation Guide

This note records the initial GuideME compatibility slice for the cultivation
loop. The guide summarizes shipped behavior; runtime truth remains in
[Cultivation Playable Loop](30_cultivation_playable_loop.md), and the normative
integration contract is the active
[`guideme-cultivation-guide` change spec](../../openspec/changes/add-guideme-cultivation-guide-slice/specs/guideme-cultivation-guide/spec.md).

## Dependency Boundary

MyVillage 0.25.1-fix1 requires GuideME on client and server with NeoForge range
`[21.1.17,22)`. Gradle compiles against the published 21.1.17 `api` classifier
and loads the full published artifact for development runs. GuideME is not
nested in the MyVillage jar. The root `guideme-21.1.17.jar` is untracked
inspection evidence and is not referenced by Gradle.

## One Authored Page Tree

The guide id is `myvillage:cultivation`. Its definition is
`src/main/resources/assets/myvillage/guideme_guides/cultivation.json`, with
`zh_cn` as the default language and no `custom_colors`. Root `guidebook/` is the
only editable Markdown source:

```text
guidebook/index.md
guidebook/getting_started/initiation.md
guidebook/getting_started/cultivation_loop.md
guidebook/_en_us/index.md
guidebook/_en_us/getting_started/initiation.md
guidebook/_en_us/getting_started/cultivation_loop.md
```

`processResources` copies that tree to
`assets/myvillage/guides/myvillage/cultivation/` in generated resources. Do not
check in a mirror under `src/main/resources`. The three page pairs cover the
playable order, the independent two-stele initiation, and the combined
meditation/progress/stability/advancement/lifespan loop through the Qi Refining
IV release ceiling. They use live `KeyBind` components instead of fixed-letter
instructions and index the two steles, low-grade stone, and both ores.

## Handbook And Preview

`myvillage:cultivation_handbook` is a one-stack functional item in
`myvillage:main`. Its use path calls `GuidesCommon.openGuide` only on the client
and creates no MyVillage guide payload. The MyVillage item model inherits
`guideme:item/guide_base`; this first slice deliberately adds no custom book
texture.

Use the player item or GuideME diagnostics with:

```mcfunction
/give @s myvillage:cultivation_handbook
/guidemec myvillage:cultivation open
/guideme open @s myvillage:cultivation
/guideme give @s myvillage:cultivation
```

Run `./gradlew runGuide` for author preview. It watches root `guidebook/`, uses
the `myvillage` source namespace, and validates/opens `myvillage:cultivation` at
startup. Owner review of 0.25.1 found GuideME's default item-tooltip `G`
unavailable while MyVillage also used `G` for stop meditation. The fix changes
MyVillage's default stop key to `X` and leaves GuideME input alone: there is no
GuideME-specific interception, remapping, or automatic migration. A client that
saved the old binding must reset or rebind `Stop Meditation`; actual post-fix
tooltip navigation remains a manual client check.

## Validation Boundary

Run:

```text
python3 tools/validate_guideme_cultivation_guide.py
python3 -m unittest tools.tests.test_validate_guideme_cultivation_guide
python3 tools/validate_mod_items.py
./gradlew test
./gradlew build
./gradlew runGuide
./gradlew runAcceptanceServer
```

Automated gates establish reproducible dependency resolution, source integrity,
compilation, resource packaging, and side-safe startup. They do not establish
Chinese/English rendering, navigation, search, item-index jumps, component/model
appearance, live reload, key remapping, handbook remembered-page behavior, or
H-screen/gameplay regression. Record every unobserved real-client surface as
`not_verified`.

## See Also

- [Validation Checklist](09_validation_checklist.md)
- [Mod Item Creation](22_mod_item_creation.md)
- [Cultivation Playable Loop](30_cultivation_playable_loop.md)
- [GuideME cultivation change spec](../../openspec/changes/add-guideme-cultivation-guide-slice/specs/guideme-cultivation-guide/spec.md)
- [Knowledge Base Map](INDEX.md)
