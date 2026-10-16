# Sponge Schematic

Sponge schematic files use the `.schem` extension and are commonly used by external editors.

## Required concepts

- Dimensions: width, height, and length.
- Palette: blockstate strings mapped to numeric ids.
- Block data: packed block ids in coordinate order.

The implemented Sponge v2 writer is in `tools/voxel_dump_to_structures.py`;
`tools/export_schem.py` remains a placeholder. Its block index is
`(y * Length + z) * Width + x`, and exports are deterministic.

The standalone Yunshan building candidates use this writer through
`tools/yunshan/build_functional.py`, with authored sign text, separate default
and site-facing variants, and full export readback checks. See the
[Yunshan building README](../../tools/yunshan/README.md) for sources, placement
boundaries, preserved originals, and the explicit offline-only acceptance status.

