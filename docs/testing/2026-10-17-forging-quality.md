# Weapon quality colors and frames — 0.58.2

## Delivered behavior

Unmarked basic combat weapons display white 凡品 / Mundane. Forged qualities
1–4 display green, blue, purple and gold. Names, quality labels, icon frames,
tooltip borders and the forging quality ceiling use one presentation palette.
The display-only zero is not a new stored quality or forging outcome.

The item decoration is registered for existing `CombatWeaponItem`s: Qingfeng
Sword, Lingxiao Spear and the mundane Xuantie Gauntlet. Only the first two retain
their existing forging recipes. The 1px icon outline sits outside the 16px image
and below the count layer; durability bars and enchantment glint remain visible.
Anvil names and item components are not rewritten. Existing stacks display their
current marks without migration; enchantments do not promote forging quality.

## Checks and evidence

- 31 focused local Python checks passed (`test_forging`, `test_modal_runner`).
  Generator `--check`, collector syntax and whitespace checks passed.
- Final source: `2fedabf50da8ce25b11d31ec02de19fe10055fc0`.
- Modal run: `20261016T195151Z-183be7a5`, preset `forging-quality-preview`.
- Generated-resource check, Java compilation and jar build passed. The real
  client/server started successfully; all **38 display checks** and all six
  selected scenarios completed. No separate Java unit-test suite was selected
  for this presentation-only change.
- Verified all five colored icon outlines in the hotbar, forging inventory,
  ordinary survival inventory and chest; both sword/spear and the mundane
  gauntlet; all five tooltip borders; named, enchanted and damaged variants;
  ordinary weapons have no forge mark and viewing does not mutate inventory.
- Pixel checks operate on raw window captures after mapped menu/hover input.
  Stacks are administrator fixtures representing saved qualities, not evidence
  of new crafting outcomes or combat attributes. Manual inspection confirmed
  readable names/quality lines, intact icons, glint, durability bars and counts.

The initial source `0edcd2fee6a249aef4715609bf462bbc7a7d78b1` also passed 37 checks
in run `20261016T194912Z-e8a2865b`. The final run includes the added gauntlet frame
registration/check. Both runs downloaded verified artifacts and confirmed
Sandbox termination; no failed game run or local Minecraft fallback occurred.

Final-run wall time: 120.807 s. Phases: create 2.386 s, upload 13.743 s, remote
100.645 s, download 3.053 s, terminate 0.981 s. Within the remote phase, jar build
took 31.336 s and the presentation collector 67.635 s. These are nested durations.

## Artifacts and limits

Jar: `build/libs/myvillage-0.58.2.jar`.

SHA-256: `d6fe2a0577c7c32d26c25af225ce40739bbb85131a9a20225d53d0733cc3391a`.

Packaged display classes and both generated language resources were checked.
Screenshots: `out/preview/forging_quality/runtime/`; interactive screenshot
viewer: `out/preview/forging_quality/index.html`. Later commits only document
delivery and do not change the packaged content.

The T3 HTML preview browser was blocked by host AppArmor; an installed alternate
headless browser checked the viewer and its image switches at desktop/mobile
widths. This presentation-page limitation did not affect Modal game checks.

Gameplay, quality probabilities, saved data and protocol remain unchanged.
Owner visual/feel/FPS acceptance and broad compatibility with other UI mods
remain `not_verified`.
