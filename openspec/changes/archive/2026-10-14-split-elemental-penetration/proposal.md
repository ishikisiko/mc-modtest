## Why

One shared resistance-penetration attribute currently affects every classified hit. Element-specific equipment and future skills need independent metal, wood, water, fire and earth penetration so changing one channel cannot weaken another channel's resistance.

## What Changes

- Register five independent synchronized elemental penetration attributes, each defaulting to zero and using the existing configured penetration bounds.
- Select only the attacker's penetration attribute for the classified damage channel; retain the existing percentage-point subtraction and zero effective-resistance floor.
- Preserve the old `myvillage:resistance_penetration` registry ID as physical-only penetration, retaining saved physical modifiers without copying them into elemental channels. Its language label and documentation become explicitly physical.
- Expose the five values through existing attribute/statistics diagnostics and both language files; verify channel isolation, packaging and physical-client/fire/physical runtime behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `realm-combat-stats`: Independent typed penetration and explicit legacy physical-only semantics.
- `validation`: Channel isolation and attribute/readback coverage with unchanged armor layers.

## Impact

Statistics attribute registration and damage routing, tests/validator, language resources, command documentation and the release artifact. This change adds no elemental attacks, no source reclassification and no equipment; metal/wood/water/earth attack routes remain pending their own feature work.
