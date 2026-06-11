# Vanilla Structure NBT

Vanilla structure files use the `.nbt` format produced by structure blocks.

## Required concepts

- `size`: structure dimensions.
- `palette`: block state list.
- `blocks`: block positions with palette indexes.
- `entities`: usually empty for static buildings.

The exporter in `tools/export_nbt.py` should map blueprint palette entries to vanilla structure palette entries.

