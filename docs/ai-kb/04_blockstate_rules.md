# Blockstate Rules

Represent blockstates as JSON objects with string values.

```json
{"facing": "north", "half": "bottom", "shape": "straight"}
```

## Notes

- Validate state keys against the target Minecraft version where possible.
- Directional blocks should use Minecraft's canonical directions: `north`, `south`, `east`, `west`, `up`, `down`.
- Stairs usually need `facing`, `half`, `shape`, and `waterlogged`.

