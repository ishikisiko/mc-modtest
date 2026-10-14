## Why

Realm growth scales health and outgoing damage together, but vanilla armor pressure still uses the full enlarged damage. Consequently the same equipment loses disproportionately more effectiveness at higher realms. Normalize only the armor-pressure input by the defender's existing realm growth so same-realm equipment retains its baseline behavior.

## What Changes

- Use defender realm scale for the damage-pressure term inside vanilla armor calculation, retaining full damage for the final multiplication.
- Preserve vanilla armor/toughness attributes, caps, bypass tags, equipment durability, shields, invulnerability frames, enchantments, resistance, absorption, and all existing outgoing growth.
- Fall back to vanilla behavior for unassigned or invalid realm assignments; never infer realm from maximum health or the attacker.
- Extend operator damage traces and automated evidence to expose the actual armor-stage input and normalization.
- Verify same-realm, cross-realm, heavy-hit, bypass and layered mitigation cases, including a diagnostic 30 armor / 12 toughness fixture. This does not add equipment or grant innate armor.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `realm-combat-stats`: Defender-normalized armor pressure and inspectable vanilla-compatible damage stages.
- `validation`: Layered regression and real-client evidence for normalized armor pressure.

## Impact

Server damage handling under `stats/`, a narrowly scoped Minecraft armor hook and its packaging, focused Java/Python tests, operator diagnostics, README/KB/specs, release artifact and bounded acceptance evidence. Existing realm growth data, profile wire format, item catalogue and equipment values remain unchanged.
