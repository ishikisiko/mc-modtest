# Blueprint Schema

A blueprint is a JSON document describing a structure using coordinates, block ids, optional blockstates, and metadata.

## Minimal shape

```json
{
  "schema_version": 1,
  "id": "example:small_house",
  "size": [5, 4, 5],
  "origin": [0, 0, 0],
  "palette": {
    "wall": "minecraft:oak_planks",
    "floor": "minecraft:cobblestone"
  },
  "blocks": [
    {"pos": [0, 0, 0], "palette": "floor"}
  ]
}
```

## Rules

- `size` is `[x, y, z]` and all block positions must be inside it.
- `palette` keys are local aliases.
- A block entry uses either `palette` or `block`.
- Blockstates are stored as an object in `state`.

## See also

- Spec: [blueprint-v1](../../openspec/specs/blueprint-v1/spec.md) — the normative blueprint contract.
- Spec: [structure-json-dsl](../../openspec/specs/structure-json-dsl/spec.md) — the structure JSON DSL.
- Index: [Knowledge Base Map](INDEX.md).

