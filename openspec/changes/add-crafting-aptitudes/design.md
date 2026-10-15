## Context

The owner explicitly clarified that aptitude caps the quality of an item made from the same recipe, not the recipe's Huang/Xuan/Di/Tian grade. This slice establishes two player aptitudes before a later result-quality system. Current alchemy remains deterministic with ungraded pills and no player-owned batches.

## Goals / Non-Goals

Goals: two independent persistent player aptitude values, deterministic once-only generation, authoritative quality ceilings, clear H-page display and administrator diagnostics.
Non-goals: crafting experience, recipe grade restrictions, pill quality components/rolls, furnace owner/start changes, natural aptitude upgrades, NPC aptitude and forging gameplay. No claim that current ungraded pills have become quality-controlled.

## Decisions

- Add `cultivation/crafting/CraftingAptitudes` as an immutable record with version1, initialized flag, alchemy and forging scores (format bounds0..100). EMPTY is uninitialized with zero scores. Register a separate `myvillage:crafting_aptitudes` attachment with codec and copyOnDeath; keep CultivationProfile v4 unchanged. Only CultivationService may install it.
- Generate once on first spiritual-root awakening, before the owning snapshot, using world seed + player UUID + distinct fixed alchemy/forging domains. Do not consume the existing root/world random stream. Existing awakened saves initialize at first login; unawakened saves remain pending. Reconnect, death, dimension change, root clear/re-awakening and normal profile reset do not reroll generated values. No ordinary client mutation exists.
- Bundled `data/myvillage/crafting/rules.json` owns initial roll range and thresholds. Initial uniform integer roll0..100, independent per profession. Quality caps:0..39下品,40..69中品,70..89上品,90..100极品. These are provisional balance values, not recipe tiers. `CraftingRules` strictly validates schema, complete ordered thresholds and bounds. Expose pure `qualityCap(score)` and `allowsQuality(score, requested)` for future producers; the current furnace does not call them yet.
- Fold crafting state plus two server-computed quality-cap integers into existing `CultivationSnapshotPayload`; no extra client cache or payload. Protocol19 ->20. Keep compatibility convenience constructors for tests/older call sites but update every production sender to include actual state. Client validates bounds and reads server-derived caps, never decides its own authority.
- Add a read-only `CraftingPage` (技艺) and View entry through PanelContext/PanelTheme. Show each aptitude/100 and its highest product quality; pending players are told to awaken at the testing stele. This page has no training, reroll or quality-production controls. Existing navigation order stays stable; append the new page. Test GUI480x270,427x240,320x240 in English and Chinese.
- Admin commands under both cultivation/xiulian: `crafting|jiyi [target]` prints one `CRAFTING` JSON row with initialized/values/quality caps; `crafting|jiyi set|shezhi <target> <alchemy|liandan|forging|lianqi> <0..100>` installs an explicit value through CultivationService. Require initialized/awakened target, preserve other aptitude. No reset/reroll command. All translation and alias branches must remain equivalent.
- Source names and quality keys use alchemy/forging and quality lower/middle/upper/perfect (codes1..4;0means pending). Numerical caps arrive from the server. Stats panel values are independent of spiritual root, spiritualAffinity and combat attributes.

## Risks / Trade-offs

- Innate hard caps can restrict a player's eventual craft quality: this is the owner's chosen rule. Initial rolls are saved and independent; future explicit rare-upgrade gameplay can change them, ordinary practice does not.
- A quality-ceiling attribute is not a result-quality system: documentation and handoff keep that distinction explicit; existing alchemy outputs remain unchanged.
- Snapshot/compatibility drift: bump protocol, update fixtures and run all cultivation/network tests plus real reconnect/restart and read-only UI evidence.
- Legacy saves must not reroll: persist initialization independently; perform deterministic preparation before committing snapshots and retain values through lifecycle changes.

## Migration Plan

Additive serialized attachment initialized once for awakened old players, no cultivation profile migration. Rebuild both client/server with protocol20. Follow openspec/config.yaml rules.tasks for version0.51.0, resources, README and changelog. Owner visual/gameplay verdict remains pending.
