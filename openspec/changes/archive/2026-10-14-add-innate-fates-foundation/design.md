## Context

The owner accepted staged implementation of the 36-fate browser design. Existing root awakening is deterministic, persisted and deliberately not repeatable; the H panel is page-based. Existing characters must not be rerolled or lose their cultivation profile.

## Goals / Non-Goals

Goals: one complete first-batch loop, bounded server-owned choice, immutable persistence, independent configurable budget, and eight actually functioning effects. Existing awakened players receive one unconfirmed choice. Non-goals: root rerolls, the other 28 effects, awakening, NPC fates, loot/ruins or new items.

## Decisions

- Use a separate copy-on-death immutable FateState attachment; all replacement is centralized in CultivationService. This avoids migrating the unrelated cultivation profile. Add the fate state and budget to its existing owning-client snapshot and bump the payload protocol.
- Register synchronized `innate_fate` definitions, with effect numbers in JSON. Store candidate IDs, a single lock, revision, roll sequence, confirmed IDs, and recovery/kill cooldown state. Data reload never rerolls characters or silently replaces confirmed selections; invalid references become unavailable rather than granting replacements.
- The H-page is available after testing/awakening. It offers six unique candidates and three final selections. The first pool contains robust, lightfoot, bright, fire affinity, alchemist descendant, beast hunter, unyielding, burning star. The other designs are not selectable placeholders.
- The server uses its own random sequence, root eligibility, point budget (3..18, default 8), one-high-tier and exclusive-group rules. Client intent contains only action, seen revision and a bounded slot/mask. No client values, seeds, clocks, selected IDs outside the current candidates, or arbitrary rerolls after confirmation are accepted. Requests are throttled.
- A lock preserves a candidate, not a mandatory selection. Low budgets add white candidates until a legal triple exists. A budget retune affects future confirmation, not an already confirmed build. Confirmation does not restore health, mana, or cooldowns.
- Effects execute on the server. Percent stat contributions use stable replacement modifiers; raw damage bonuses add once before vanilla mitigation, typed resistance retains its existing stage, and bypass sources remain untouched. Heavy-skill framework changes are outside this slice.
- Alchemist healing has a persisted server-time cooldown and bounded per-tick remaining healing; logout grants no offline heal or reset. Death cancels residual healing while preserving cooldown. Hostile kills must be personally attributed to the player, not assistants/summons/friends, and grant mana only through CultivationService.
- Existing ordinary root commands remain unchanged. Fate commands have matching English/pinyin trees and an explicit operator-only reset; the normal player cannot reset a confirmed selection.

## Risks / Trade-offs

- Smaller pool limits variety: label the eight implemented entries and retained design roadmap explicitly.
- UI density: a scrollable PanelPage, checked at 480x270, 427x240 and 320x240 in both languages.
- Attribute load/death ordering: copyOnDeath, stable modifier IDs, clamp on maximum reduction, no refresh healing; verify actual reload/respawn.
- Combat balance remains an owner verdict: automated damage/cooldown evidence is not play-feel acceptance.

## Migration Plan

New attachment defaults to unconfirmed and empty; existing root and cultivation payload schema remain unchanged. Require matching protocol clients. Rebuild/restart after bundled-rule changes; synchronized definitions support datapack reload without reselecting a confirmed fate. Preserve all old data on ordinary login/death/dimension change.
