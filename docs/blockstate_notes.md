# Blockstate Notes

Notes for Minecraft blockstates that are easy to get visually wrong in generated structure NBT.

Before AI edits a structure, read this file together with `docs/visual_feedback.md` and `docs/building_rules.md`.

## Trapdoor

- `open=false` means the trapdoor is horizontal.
- `open=true` means the trapdoor is vertical and can be used as a wall-mounted shutter, sill, or trim.
- For exterior window trim placed outside a wall, `facing` may need to point back toward the wall so the opened trapdoor plane sits flush against the building.
- Known good `test_house_03` exterior window trim states:
  - Front/south exterior at lower `z`: `minecraft:oak_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]`
  - Back/north exterior at higher `z`: `minecraft:oak_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]`
  - West exterior: `minecraft:oak_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]`
  - East exterior: `minecraft:oak_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]`

## Stairs

- Roof stairs need explicit `facing`, `half=bottom`, `shape=straight`, and `waterlogged=false` for deterministic output.
- For a roof ridge running along x, south roof slope commonly uses `facing=south`; north roof slope commonly uses `facing=north`.

## Door

- Doors are two-block structures. Place both halves explicitly:
  - Lower: `half=lower`
  - Upper: `half=upper`
- Keep `facing`, `hinge`, `open`, and `powered` consistent between halves.

## Slab

- Use explicit `type=bottom`, `type=top`, or `type=double`.
- Include `waterlogged=false`.

## Lantern

- Hanging lanterns need `hanging=true`.
- Include `waterlogged=false`.

## Barrel

- A plain empty barrel can be represented with blockstate only, for example `minecraft:barrel[facing=up,open=false]`.
- Do not add inventory NBT until block entity writing is supported.
