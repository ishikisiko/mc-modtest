# Sponge Schematic

Sponge schematic files use the `.schem` extension and are commonly used by external editors.

## Required concepts

- Dimensions: width, height, and length.
- Palette: blockstate strings mapped to numeric ids.
- Block data: packed block ids in coordinate order.

The exporter in `tools/export_schem.py` should keep coordinate ordering documented and deterministic.

