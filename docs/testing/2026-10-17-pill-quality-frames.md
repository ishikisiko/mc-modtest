# Pill quality frames — 0.58.3

## Delivered

Huiling, Yangyuan and Ningshen pills now share the weapon quality palette:
Lower green, Middle blue, Upper purple and Perfect gold. Item names, icon frames,
tooltip borders and the alchemy ceiling label use the same colors. Renamed pills
retain their saved names and gain a quality line. Missing pill-quality components
still mean Lower; no Mundane pill tier or saved-component migration was added.

`item.QualityDisplay` is the shared palette/type resolver. Client-only
`client.item.QualityFrames` replaces the weapon-specific renderer and registers
both pill and basic combat-weapon types. Icon frames stay outside the 16px image
and below vanilla count text. Medicine rules, effect strength, quality rolling,
stack equality and protocol remain unchanged.

## Validation

Final source: `8f7d63b9371dd167c75fc9646d5d890ee9054e67`.

Modal run: `20261016T200600Z-127f3aef`, preset `pill-quality-preview`.

- All 27 local Python checks passed (`test_gen_alchemy_assets`, `test_modal_runner`).
- Generated alchemy resources matched all 43 files; collector syntax and diff checks passed.
- All three `PillQualityTest` Java tests passed, covering codecs, legacy stacking,
  quality separation and localized names. Jar build and real client/server startup passed.
- All **48 display checks** and all seven selected scenarios passed: hotbar,
  all three pill types at all four qualities, tooltip borders, legacy/explicit
  Lower and named pills, chest, adjacent weapon frames, and count visibility.
- Every 16-pill stack retained all 26 sampled glyph pixels from the vanilla
  stone-stack count reference. Viewing left complete inventory components/counts
  unchanged. Raw screenshots were inspected for names, colors and readability.

The collector uses administrator-created quality fixtures, mapped menu/hover
input, raw window capture and server readback. It does not claim new brewing or
medicine-effect acceptance. The older full alchemy collectors were not run.

## Preserved first-run failure

Run `20261016T200217Z-9ca2256a` on `3b7a5ae7c477221cf408ad2b0763f2ae56d38593`
passed Java/build and initial frame checks, then stopped at the count comparator.
The comparator incorrectly selected literal-white slot-rim pixels as the count
reference; the actual font is near-white (252) and the frame intentionally colors
the rim. Restricting samples inside the icon and reading the actual font shade
made all 12 recorded count masks match, then the complete runtime passed.
This was a collector fix; every uncompressed final jar entry is byte-identical
to the first run's jar. Both runs retained logs, downloaded verified artifacts
and confirmed Sandbox termination. No local Minecraft fallback was used.

The final run took 143.857 s wall time: create 2.378 s, upload 13.530 s, remote
122.230 s, download 4.682 s, terminate 1.037 s. Within the remote phase,
test/build took 44.598 s and display collection 75.457 s; these are nested times.

## Delivery

Jar: `build/libs/myvillage-0.58.3.jar`.

SHA-256: `8d38a8f8344ed534d9e82c19381d19ea983087cdc48bdea47f9817def70c2114`.

The shared display classes are packaged, the obsolete weapon-only renderer is
absent, and both language files and alchemy/forging rules match repository bytes.
Screenshots are in `out/preview/pill_quality_frames/runtime/`, with an interactive
viewer at `out/preview/pill_quality_frames/index.html`. Subsequent commits only
document delivery. T3's HTML preview browser remains blocked by host AppArmor;
the installed alternate headless browser checks the screenshot viewer.

Owner appearance/feel/FPS and compatibility with arbitrary third-party UI mods
remain `not_verified`; gameplay and broad multiplayer were not selected.
