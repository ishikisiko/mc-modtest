# Validation Checklist

- Blueprint JSON parses successfully.
- `schema_version` is supported.
- `id` is present and namespaced.
- `size` has three positive integers.
- Every block position is within bounds.
- Every palette reference exists.
- Every block id is namespaced.
- Blockstate values are strings.
- Exported structures can be loaded by the target tool or game version.

