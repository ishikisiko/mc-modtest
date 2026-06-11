# Visual Feedback

Record visual issues found after testing generated structures in game with screenshots, structure blocks, or `/place template`.

Before changing a structure based on in-game feedback, add the observation here first. If the issue becomes a reusable generation rule, summarize it in `docs/building_rules.md`. If it is caused by blockstate behavior, summarize it in `docs/blockstate_notes.md`.

## test_house_03

- Source: `/place template` visual review after adding oak trapdoor window decoration.
- Issue: North/south oak trapdoors used as exterior window decoration did not appear attached to the wall. They were written with `open=false`, which leaves trapdoors horizontal instead of vertical.
- Fix: Use `open=true` for wall-mounted trapdoor shutters/sills. For front/back exterior placements, reverse the north/south `facing` so the opened trapdoor plane sits against the wall side closest to the building.
- Current states:
  - Front/south exterior at lower `z`: `minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]`
  - Back/north exterior at higher `z`: `minecraft:oak_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]`
  - West exterior: `minecraft:oak_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]`
  - East exterior: `minecraft:oak_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]`
