# Converter API

The converter tools should share the same high-level flow:

1. Load blueprint JSON.
2. Validate schema and block references.
3. Normalize palette and blockstate data.
4. Emit target format.

Keep conversion functions pure where practical so they are easy to test.

