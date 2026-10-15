## Context

Version 0.49.0 exposes six-candidate fate selection in `FatesPage`. Root generation already belongs to the testing stele and immutable cultivation service; inheritance is a second stele. The owner's chosen flow combines root presentation and fate choice in an independent screen and leaves H as a result viewer. Existing persistent roots and fate attachments must survive this UI change.

## Goals / Non-Goals

**Goals:** Root-first presentation after a server-side stele interaction; resumable three-of-six choice; a server-owned interaction gate shared by packets and ordinary commands; fixed compact controls at 480×270, 427×240 and 320×240 in English and Chinese; truthful automated and owner evidence.

**Non-Goals:** Root rerolls, changed fate balance or candidate rules, inheritance merging, new blocks/items, profile-schema migration, new client authority, or automatic confirmation.

## Decisions

1. **Server interaction opens the screen.** The testing-stele block invokes the existing awakening service only on the server. A successful awakening or an existing valid root permits the server to establish a session and send the owning player a clientbound opening notification after synchronization. Ordinary rootless failures never open selection. The client does not submit roots or a request to awaken. This keeps root authority at the existing service boundary.
2. **Separate screen, shared read model.** The awakening screen reads the latest synchronized profile, fate state, budget and definitions. It presents the root before the six-candidate choice, retains explicit confirmation and uses bounded existing fate intents. H `FatesPage` displays confirmed results or a stele instruction only. Keeping draft controls in H was rejected because it retains the interaction the owner rejected.
3. **Live interaction gate.** Server-owned transient sessions bind the player, dimension and actual testing-stele position. Before every ordinary roll, lock or confirm, server code checks liveness, the same dimension, valid stele block and allowed distance. Death, disconnect, dimension change, closure and reset invalidate the session; reconnect requires another real interaction. The session is neither saved nor accepted from a client coordinate. Ordinary command mutations use the same gate. Any diagnostic operator bypass must check the executing command source's operator permission explicitly and remain documented as a fixture path.
4. **Opening is idempotent.** Only an unconfirmed attachment with no candidates gets an initial roll. Closing and reopening retains the draft revision, candidate IDs, lock and root. Existing confirmed choices display their stored result and never roll. An explicit reroll remains a deliberate in-screen action.
5. **Fixed UI regions.** Root summary, six compact candidate entries and action controls have distinct bounded regions. Detailed effects use the focused entry/detail area rather than unbounded card growth. Confirmation controls remain reachable at every supported GUI size. Font fit is inspected in both languages using actual Minecraft evidence.

## Risks / Trade-offs

- [Client/server snapshot ordering] → Send synchronized state before opening and keep missing-state presentation safe; never fabricate a result.
- [Late requests after walking away or closing] → Revalidate at the mutation boundary and invalidate lifecycle/close paths; test rejection with unchanged state.
- [Operator diagnostics hiding a bypass] → Keep source authorization explicit, test ordinary denial and record privileged fixture usage separately.
- [Compact labels lose detail] → Preserve synchronized effect descriptions in a bounded focused detail/hover surface and inspect the smallest layout.
- [Historical H screenshots look like current acceptance] → Label 0.49.0 evidence historical and collect new stele-screen evidence; owner's visual and gameplay verdict stays pending.

## Migration Plan

Keep the existing cultivation profile and fate attachment formats. Ship current bilingual resources and a freshly built jar following `openspec/config.yaml` `rules.tasks`. Old awakened players right-click the testing stele to choose or resume once; old confirmed players retain all choices. The inheritance stele's learning operation remains unchanged. Rollback of code does not require rewriting roots or fate state.

## Open Questions

Runtime contract: protocol 19 uses `AwakeningOpenPayload(BlockPos, long sessionToken)`, `AwakeningClosePayload(long sessionToken)` and `FateIntentPayload(byte action, long revision, byte value, long sessionToken)`. Self roll/lock/confirm commands require a valid session even for operators. Only permission-level-2 targeted roll/confirm and reset are diagnostic fixture paths. Owner visual/gameplay and multiplayer acceptance remain pending observations, not design blockers.
