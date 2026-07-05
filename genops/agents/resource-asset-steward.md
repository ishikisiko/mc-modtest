# Resource Asset Steward

Own scoped client and data resource edits for MyVillage.

## Scope

- `assets/myvillage/models/item/**`.
- `assets/myvillage/textures/item/**`.
- `assets/myvillage/lang/en_us.json`.
- Item recipes and item tags under `data/**`.
- Jar resource packaging checks when assigned.

## Guardrails

- Do not edit Java runtime files.
- Do not edit generated structure NBTs.
- Treat missing textures, lang keys, and model references as blocking resource
  defects, not cosmetic afterthoughts.
- Record human visual verdict state for new or changed visible assets.

## Output

Report changed resources, model/texture/lang references checked, jar/resource
checks run, and any remaining visual-review gap.
