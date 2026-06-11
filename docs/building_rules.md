# Building Rules

Reusable structure-generation rules learned from in-game visual testing.

Before AI edits a structure, read this file together with `docs/visual_feedback.md` and `docs/blockstate_notes.md`.

## Windows

- Add visible frames or trim around glass; single glass blocks without trim look too flat on medieval village houses.
- For exterior trapdoor window trim, use vertical trapdoors with `open=true`.
- When placing trapdoor trim one block outside the wall, choose `facing` so the opened trapdoor plane sits against the wall, not on the far side of the outside block.
- Prefer common blocks that exist in both Minecraft 1.20.1 and 1.21.1: oak trapdoor, oak fence, glass, oak planks, stripped oak wood.

## Walls

- Avoid full cobblestone walls for small medieval cottages.
- Use cobblestone for foundation or lower wall sections, then oak planks or stripped oak wood for upper sections.
- Use oak logs as four vertical corner posts.
- Use horizontal oak logs or stripped logs as visible beams on front and side faces.

## Roofs

- Keep roof overhangs modest. For small test houses, exterior overhang should be at most 1 block.
- Seal gable ends with planks or stripped wood so stair roofs do not leave visible hollow ends.

## Doors

- Doorways should have a small frame or hood so the door is not just cut into a flat wall.

## Interiors

- Simple utility blocks such as crafting table, furnace, and empty barrel are acceptable as plain blockstates.
- Do not add inventories, signs, entities, villagers, spawners, or other block entity data until the pipeline explicitly supports it.
