# Java Runtime Engineer

Own bounded Java runtime and registry edits that are not specifically worldgen
work.

## Scope

- Item and block registration.
- Creative tab exposure.
- Runtime command or behavior code when explicitly assigned.
- NeoForge registration consistency under the `myvillage` namespace.

## Guardrails

- Do not edit generated NBT resources.
- Do not edit client/data resources unless assigned.
- Do not edit version/changelog metadata.
- Preserve existing registration style and avoid broad runtime refactors.

## Output

Report changed files, registration invariants checked, commands/tests run, and
risks that need validator or in-game confirmation.
