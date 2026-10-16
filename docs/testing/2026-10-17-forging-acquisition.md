# Forging material acquisition — 0.58.0

## Delivered behavior and scope

- Vanilla copper becomes spirit copper through a private, placed-ding infusion menu. Paid qi is saved per ingot; pause/close/restart retain progress, and resumption charges only the remainder.
- Deep Overworld dark-iron veins and visible riverbank/shallow sediment are finite natural sources. Mining yields direct forging additives; workbench acquisition recipes and their six recipe/advancement outputs remain absent.
- Existing material IDs and old weapons remain valid. Newly generated chunks are required for the new deposits. Protocol 26 covers the added menu/component/blocks.

The authoritative tuning is `src/main/resources/data/myvillage/forging/acquisition.json`. Item purpose and source hints are bilingual; the current README gives player steps.

## Validation

Gameplay source: `674ebf70eced6a352140e9a24cca6b31ccfd9925`.
Modal run: `20261016T174305Z-83233c21`, preset `forging-acquisition`.
Result: passed; artifacts downloaded and verified; Sandbox termination confirmed.

- Relevant Python checks passed: forging resources, retired-output cleanup, acquisition settings/tool/loot/worldgen contracts, chunk-palette decoding and capture behavior, Modal command/lifecycle fixtures, and protocol validation. The suite union contains 62 tests; capture/tool tests were rerun when changed.
- 36 Java tests passed, including infusion codec/wire bounds, per-ingot investment, exact remaining costs, split conservation, batch bounds and insufficient-power refusal, alongside the directly related forging/protocol regressions.
- All 21 runtime checks passed; all seven selected scenarios completed. Natural generation was verified by flushed region data (2,194 ore blocks and 32 sediment blocks in the sampled area), followed by mapped mining and actual item pickup. These counts are one fixed sample, not a density/balance acceptance target.
- Iron pickaxe and shovel extraction passed. A stone pickaxe did not yield dark iron; a pickaxe did not yield sand. Unawakened and insufficient-qi infusion attempts left the copper unchanged.
- Four copper ingots paused with two paid qi each: exactly eight qi spent. The item components survived a server/client restart. Resuming paid only the remaining batch cost.
- Two real clients used the same ding independently: four and two spirit-copper outputs, respectively, without consuming the other player's materials. Owned client processes were cleaned up.
- The naturally mined iron/sand and genuinely infused copper then produced tough, sharp and channeling weapons through mapped forging UI actions. No new additive was supplied by a give command.

Administrative fixtures supplied ordinary copper, tools, weapon base ingredients, a pattern and spiritual power. Teleports and a lit, dry access corridor isolated mining input from travel, darkness and water current. No DevBridge or direct invocation of production interaction methods was used. These fixtures do not establish the time or difficulty of discovering/obtaining materials unaided.

## Failed attempts retained

| Modal run | Tested source | Cause and correction |
|---|---|---|
| `20261016T172224Z-950cc752` | `183eacb` | Insufficient-power fixture set qi before opening the UI, allowing natural regeneration to fund a pulse. The fixture now sets zero just before starting. A hotbar replacement also overwrote harvested iron; ordinary copper is now given into available space. |
| `20261016T172744Z-2a34351a` | `d9692c5` | River flow entered the access corridor and destabilized the mining ray. Added a dry transparent approach and failure position/image readback. |
| `20261016T173222Z-281f8708` | `9211b6f` | A hard-coded 30-qi fixture exceeded the awakened mortal's actual 20 cap and was rejected. Fixtures now read and verify each player's current cap. |
| `20261016T173642Z-5f6b2a98` | `91e1ad1` | Pattern setup replaced hotbar slot 8, which held the freshly infused output. The collector now gives the pattern and selects its actual free slot. All preceding restart/private-batch checks had passed. |

All failed runs retained their evidence and confirmed Sandbox termination. Review also found and corrected a real generation issue: sediment biome filtering now follows a surface-height placement modifier, instead of querying the chunk-bottom biome. World captures no longer move a grabbed camera cursor.

## Measured phases

Successful gameplay run: image/Sandbox creation 2.385 s, upload 16.370 s, remote job 299.480 s, download 3.089 s, termination 1.028 s. Source archive: 0.329 s.

Inside the remote job: generator check 0.117 s, Java tests/build 47.188 s, runtime collector 250.261 s. The runtime collector includes initial startup 66.134 s, restart startup 58.083 s, second-client startup 46.410 s and two session shutdowns of approximately 5 s each. These phases are nested, not additive to the outer remote-job measurement. SDK exit-status polling briefly retried a connection/service error; the run completed and cleanup was confirmed.

## Artifact and acceptance boundary

Delivered jar: `build/libs/myvillage-0.58.0.jar` from the successful gameplay run.
SHA-256: `62af912bba4f616bc1774ef1a5604e5a314ca8215ad7de5586eb1fdff81d636d`.
Generated forging resources were compared byte-for-byte inside the jar; the six retired acquisition recipe/advancement paths are absent.

Developer checks establish the bounded paths above. Owner visual quality, feel, sound, balance, FPS, broad multiplayer, prolonged play and discovery pacing remain `not_verified`. The cloud client uses software rendering. Snapshot images are developer evidence, not owner approval.


## Visual handoff

Settled staged views were collected separately in Modal run `20261016T174900Z-0145d410`, source `5ffb2176fab86ab3cdf6ad2c999f490d9a6eb497`, preset `forging-acquisition-preview`. It passed, artifacts were verified and termination confirmed. The source delta since the successful gameplay run only affects collection tools/tests, not Java or packaged resources. Every uncompressed jar entry matches the gameplay-tested jar exactly.

The three mineral/ding views are deliberately staged displays, not natural-discovery screenshots. The pause/completion UI images come from the successful gameplay run. Some earlier rapid-teleport images caught unfinished chunk meshes; those are retained in raw evidence but not used as the material appearance handoff.

Preview phases: creation 2.639 s, upload 12.685 s, remote job 137.554 s (jar 34.147 s, visual collection 101.455 s), download 4.807 s, termination 1.095 s. No additional gameplay acceptance is inferred from this visual-only run.
