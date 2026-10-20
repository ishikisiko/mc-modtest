---
name: mod-item-creation
description: Add or revise MyVillage items, block items and their resources while preserving registration, asset ownership and server authority. No mandatory OpenSpec or GenOps workflow.
---

# MyVillage item work

Read `docs/WORKFLOW.md`, `docs/contracts/assets.md` and the relevant entry in
`docs/ai-kb/22_mod_item_creation.md`. Follow the user's scope; do not create a
proposal, task manifest or role approval merely because an item is involved.

Establish the item id/type, Java registration, creative exposure, model/texture,
Chinese name, intended recipe/tag behavior and any server-owned use or saved
state. Existing JSON item contracts remain useful inputs to validators; a new
standalone contract is needed only when an actual consumer needs it.

Edit generated assets through their generator. For combat weapons also follow
KB36's technical dependencies (data, geometry, rig, generated animation and
CombatWeaponItem); select checks for what changed, not the entire playbook.

Use focused resource/registration checks and meaningful behavior tests. Build a
matching jar when delivering changed mod content. Run a real client only for an
affected boundary that offline checks cannot prove. Preserve `not_verified` for
unobserved appearance, feel and multiplayer.

GenOps may be used when explicitly requested; then declare bounded ownership and
retain its actual results. No automatic subagents, CRAFT front door, OpenSpec
change or repeated full release is required for ordinary item work.
