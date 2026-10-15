# Learned formulas and automatic filling — 0.52.3

Source: `172b6d45ee82fa04c830924f231d1dc469d17fdb`. Scope: permanent player
knowledge, per-viewer learned menu, server-checked selection/start, planned
inventory return/refill, additive furnace persistence and protocol22.

- Generator consistency, alchemy/item/crafting validators and 56 focused Python
  tests passed. No unrelated full release/game suite was run.
- Modal `20261015T192653Z-c190ee00`, preset `alchemy-build`: 39 alchemy Java
  tests passed (zero failures/errors/skips), fresh jar built. Gradle 82.776s;
  controller 103.822s; artifacts downloaded and Sandbox termination confirmed.
- Tests cover immutable knowledge/codec, repeat learning, one-batch refill,
  repeat clicks, missing materials, full-inventory rollback and conservation
  when changing ingredients; existing batch/medicine/quality tests remain green.
- Packaged alchemy validator passed. Jar SHA256: `e3a21d5ba13a71bf40a033b9ce29681d6b7f40f901c99578a5eb1b234931aebd`.
- DevHost saved/stopped without force, backed up 0.52.2, installed 0.52.3 and
  reopened `agent-test`. DevBridge reports the loaded mod as 0.52.3.
- Mapped use opened the owner's existing furnace; its old formula and materials
  remain visible, no learned recipes appear for the unlearned player, and Start
  is disabled. Inspected Chinese 534x300 GUI screenshot:
  `out/preview/alchemy/learned-formulas-0.52.3.png`.
- Did not alter owner knowledge or supplies. Actual learning/refill/brewing,
  knowledge death/restart and second-viewer behavior remain unobserved on the
  owner's client. UI feel/balance remain owner decisions.
- Old physical-formula game capture fixtures are historical and need migration;
  they are not claimed as current learned-formula acceptance.

Usage and architecture: [KB49](../ai-kb/49_alchemy.md).
