# Player Sect Entry (拜入宗门)

0.41.0, slice 1 of player sect entry: the player gets a record in the world
ledger (命簿), joins a sect through its gate steward (守山执事) in a
server-authoritative dialogue, leaves it, rises by a yearly review, and
becomes a rogue when the sect is destroyed. Unbuilt gates near a player are
built in frames (P4-lite). 0.42.0, slice 2, adds the scripture hall (藏经阁):
shelves in the compound's pavilions lending the sect's manuals by rank
("Scripture hall" below). 0.43.0, slice 3, adds sect tasks for contribution
and apprenticeship with a master's guidance ("Sect tasks and
apprenticeship" below). 0.44.0, slice 4, the last, adds news of the
player's sect, hostile gates, and admin war and destruction ("World
response"), and assesses real P4 ("Real P4 (worldgen placement)
assessment"). The design is `docs/player-sect-entry-brief.md`
(slices 2 to 4: scripture hall, contribution and tasks, masters, the world's
response); the package breakdown with the defaults the owner may overturn is
`docs/sect-entry-slice1-tasks.md`. There is no capability spec; this note,
[40_world_sim.md](40_world_sim.md), and the code are the reference.

A player is never a ledger `Person`. The engine settles persons every day
(cultivation, travel, death, succession); a player must not be moved by it.
Players are kept apart, are not in `SimContext.members()` or `membersAt`, and
never count toward a sect's people, resources, prestige, recruitment, or
avatar selection.

## Where each fact lives

| Fact | Source of truth |
|---|---|
| Thresholds, standings, promotion bars, steward range, realize radius and clips per tick | `player` section of `data/myvillage/world_sim/rules.json`, loaded as `sim/data/Rules.Player` |
| The record | `sim/model/PlayerMember`, in `WorldState.playerMembers`; saved by `sim/model/StateCodec` (`player_members`) |
| Admission order, join, leave, admin rank, yearly review, sect dissolved | `sim/engine/PlayerAffairs` |
| Facade (the only mutation path) | `sim/WorldSim`: `playerMember`, `playerMembers`, `stewardOf`, `admission`, `joinSect`, `leaveSect`, `promotePlayer`, `updatePlayerQualification` |
| Refusal reasons | `sim/Admission`; dialogue-only reasons `inactive`, `not_member` in `SectDialogueKeys` |
| Qualification from the cultivation profile, chat lines, `SECT_ENTRY` | `sim/runtime/player/WorldSimPlayers` |
| Server dialogue checks and pages | `sim/runtime/player/SectDialogue` |
| Scenes and options (pure) | `sim/runtime/player/SectDialogueScenes` |
| Dialogue text keys, variant and param counts | `sim/runtime/player/SectDialogueKeys`, both language files |
| Payloads | `sim/runtime/net/SectDialoguePayload`, `SectIntentPayload`, registered in `WorldSimPayloads.register` |
| Client screen | `client/sim/SectDialogueScreen`, `client/sim/ClientSectDialogue` |
| Steward selection, cell, name tag, roles on avatars | `sim/runtime/avatar/AvatarPlanner`, `WorldSimAvatars`; `NpcEntity.DATA_LEDGER_ROLE` |
| My-sect card and bearings | `sim/runtime/net/WorldSimSnapshot` (`MySect`, `SectSummary.bearing`), `WorldSimSnapshots`, `client/cultivation/panel/WorldPage` |
| Commands | `sim/runtime/WorldSimCommands` (`sect <id> join|leave <player>`, `player <player>`), `sim/runtime/avatar/GateRealizerCommands` (`gates [retry]`) |
| Framed gate build | `sim/runtime/avatar/GateRealizer`, `GateRealizePlan` (pure), `sect/SectGenerator.prepare` / `FramedSite` |
| Headless evidence | `tools/world_sim_entry_evidence.py` |

Docs point at `rules.json` rather than copying its numbers.

## Ledger

**Record.** `PlayerMember`, keyed by the player's UUID string:
`playerId`, `playerName` (last known, for chronicle text), `sectId` (-1 when
in none), `rank` (`outer`, `inner`, `elder`), `joinedDay`, `masterId` (-1;
set by apprenticeship since 0.43.0), `contribution` (earned by sect tasks since 0.43.0),
`borrowed` (technique ids borrowed from the scripture hall, 0.42.0), `standings` (交情: sect id →
-100..100), `leftSectId` and `leftDay` (the last sect left), and a
qualification snapshot `realmId` (player realm registry path: `mortal`,
`qi_refining`, `foundation_establishment`, ...), `stageIndex` (0-based within
the realm), `awakened`, `rootPeakBp` (the highest single-element affinity;
the five always sum to 10000, so a total is meaningless). The record outlives
membership: standings and borrowed manuals carry over to a later join.

**Snapshot.** The pure core never reads Minecraft. `WorldSimPlayers` refreshes
the snapshot (and the name) on login, after every settled day for online
players, and on every join or leave; the yearly review reads only the
snapshot. `updatePlayerQualification` does nothing for a player without a
record.

**Save.** `StateCodec.VERSION` 3 (since 0.41.0): an optional top-level
`player_members` array, one object per record (`player`, `name`, `sect`,
`rank`, `joined_day`, `master`, `contribution`, `borrowed`, `standings` as
`{sect, value}` pairs, `left_sect`, `left_day`, `realm`, `stage`,
`awakened`, `root_peak_bp`), in player-id order. A version-2 payload loads
with no players; a player listed twice is a `SimFormatException`.
`WorldSimSavedData.FORMAT` is unchanged.

**Admission** (`PlayerAffairs.admission`, read-only), first refusal wins:

1. `sect_inactive`: no such sect, or not active.
2. `already_member` (in this sect) or `member_elsewhere` (in another).
3. `rejoin_cooldown`: this is the sect last left and fewer than
   `leave.rejoin_years × days_per_year` days have passed.
4. `standing_too_low`: the standing with this sect is below
   `leave.rejoin_standing_at_least` (any sect, not only the one left).
5. `not_awakened`: `admission.require_awakened_root` and no awakened root.
6. `realm_too_low`: below `admission.min_realm` / `min_stage`.
7. `selective`: the sect's prestige is at least
   `selective.prestige_at_least` and the player has neither
   `selective.root_peak_bp_at_least` nor `selective.or_min_realm` (stage 0).

Realm comparison: `mortal` ranks below every ledger realm, a realm id the
ledger's `RealmTable` does not know ranks as mortal, then the stage index
decides.

**Join** (`joinSect`): creates or reuses the record, sets `sectId`, rank
`outer`, `joinedDay`, no master, takes the snapshot, adds
`admission.join_standing` (clamped to -100..100), and records `player_join`.
It throws `IllegalArgumentException(reason)` when admission refuses. With
`force` (admin commands only) every rule but `sect_inactive` and
`already_member` is skipped, and a member of another sect is first released
from it quietly (no penalty, no event; `leftSectId`/`leftDay` still set).

**Leave** (`leaveSect`): `sectId` -1, rank `outer`, no master,
`leftSectId`/`leftDay` set, `leave.standing_penalty` added, `player_leave`
recorded; contribution and borrowed manuals stay. Throws `not_member` when
the player is in no sect.

**Admin rank** (`promotePlayer`): sets `outer`, `inner`, or `elder`; a rise
records `player_promotion`, an unchanged or lower rank returns empty. No
command calls it yet.

**Timing.** A player action happens between settled days: its event is
dated `WorldSim.day()`, is in the chronicle at once, and the open day is
closed (`Chronicle.endDay`) so the next `step` does not return it again.

**Yearly** (`PlayerAffairs.yearly`, run by `Engine.step` right after
`SectAffairs.yearly` on days where `day % days_per_year == 0`): for each
member, a sect that is gone releases the player (a destroyed one with the
`sect_gone` line); otherwise one step up (outer → inner → elder, never to
sect master) when the snapshot reaches `promotion.inner` or
`promotion.elder` (realm, stage, contribution), recorded as
`player_promotion`; a master who died or left the sect is dropped. Then
every negative standing of every record moves toward 0 by
`leave.standing_recovery_per_year`.

**Sect dissolved.** `SectPolitics.dissolve` calls
`PlayerAffairs.sectDissolved(ctx, sect, cause)`: its players become rogues
(no penalty, `leftSectId`/`leftDay` set) with a `player_leave` event whose
line is `world_sim.event.player.leave.sect_gone.*` and whose cause is the
event that ended the sect.

**Events.** Types `player_join`, `player_leave`, `player_promotion`, all
importance 2 (kept forever, in the chronicle, rumored to players in the
region), `sects` the sect, `region` its home region, no actors; the first
param is the player's name, the second the sect name. Families:
`world_sim.event.player.join.*`, `.leave.*`, `.leave.sect_gone.*`,
`.promote.inner.*`, `.promote.elder.*`, registered in `TextKeys`.

## Steward (守山执事)

- **Derived, never stored.** `WorldSim.stewardOf(sectId)`: of `membersAt`,
  the lowest rank in the order outer, inner, elder, sect master, anything
  else, then the lowest person id. Empty when nobody is at the sect.
- **Selection.** `AvatarPlanner.select(atSect, realmOrder, max, stewardId)`
  puts the steward before everyone, then the usual master, elders, realm
  order; so the steward is shown whenever any avatar is.
- **Cell.** `AvatarPlanner.stewardCell`: on the lowest terrace, the free cell
  (two-block spacing) nearest the axis column (`AvatarPlanner.axisX`, the
  middle column of the site rectangle), then the least z (toward the gate
  opening), then the least x; falls back to `pickCell` when that terrace is
  full.
- **Name tag.** `entity.myvillage.cultivator.avatar.steward`: the usual
  name · realm · sect plus a fixed fourth part 守山执事 (a name tag is one
  line). Elders' tags are unchanged.
- **Role on the entity.** `NpcEntity.DATA_LEDGER_ROLE` (`none`, `steward`,
  `elder`; synced, never saved) is set on spawn and on every reconcile:
  steward, `elder` for elders and the master, otherwise `none`. When the
  steward changes hands both avatars are withdrawn so the next pass
  respawns them on the right cells.

## Dialogue

Server-authoritative. `NpcEntity.mobInteract` on an avatar whose role is
not `none` calls `SectDialogue.open` on the server and returns `CONSUME`;
role `none` still `PASS`es.

Every open and every intent passes `SectDialogue.check`: the avatar is a
ledger avatar with a role and not removed; player and avatar in the
overworld, the same level; distance at most `steward.interact_range`; the
ledger active; the person alive and in an active sect; the person's role by
the ledger (`stewardOf`, or rank elder / sect master) not `none`. A failed
ledger check sends `message.myvillage.world.sect.unavailable`; a failed
distance or level check sends nothing. An intent is further dropped when it
comes under 4 server ticks after the player's previous one, when the entity
is gone, when its `sectId` is not the avatar's sect, and, for an option the
current state does not offer (JOIN while a member here, LEAVE while not, or
anything at an elder), the current page is sent back instead. JOIN and
LEAVE go through `WorldSimPlayers.join(player, sect, false)` /
`leave(player)`, and the answer is a new page.

| Payload | Direction | Fields and bounds |
|---|---|---|
| `SectDialoguePayload` `myvillage:sect_dialogue` | server → client | `entityId`, `sectId`, `sectName`, `role`, `avatarName`, `prestige` (int), `memberCount`, `masterName`, `regionName`, `myRank` ("" when not of this sect), `myStanding`, `admissible`, `reason`, `lines` (at most 8 translatable components, `ComponentSerialization.TRUSTED_STREAM_CODEC`), `options` (at most 4 ids, each 0..2). Names at most 64 chars, role and rank 16, reason 32; out of range is a `DecoderException` |
| `SectIntentPayload` `myvillage:sect_intent` | client → server | `kind` (unsigned byte: 0 JOIN, 1 LEAVE, 2 FAREWELL; anything else rejected), `entityId`, `sectId`. No other field: the server decides everything |

`ModPayloads.PROTOCOL_VERSION` is `13` since 0.41.0 (was `12`).

Scenes (`SectDialogueScenes.decide`, pure; lines are scene bases
`world_sim.dialogue.<base>.<n>`):

| Speaker and state | Lines | Options |
|---|---|---|
| Steward, player admissible | `steward.greet`, `steward.intro`, `steward.invite` | JOIN, FAREWELL |
| Steward, player refused (incl. member of another sect) | `steward.greet`, `steward.intro`, `steward.refuse.<reason>` | FAREWELL |
| Steward, member of this sect | `steward.greet`, `steward.intro`, `steward.member`, `steward.leave_ask` | LEAVE, FAREWELL |
| After JOIN succeeded | `steward.welcome` | FAREWELL |
| After LEAVE succeeded | `steward.farewell_left` | FAREWELL |
| After JOIN or LEAVE refused | `steward.refuse.<reason>` | FAREWELL |
| Elder, not of this sect | `elder.greet`, `steward.intro` | FAREWELL |
| Elder, member of this sect | `elder.member` | FAREWELL |

`SectDialogueKeys` holds the variant and param counts per base (refusals: the
eight `Admission` reasons plus `inactive` and `not_member`; an unknown reason
reads as `inactive`); a variant is picked from the player's UUID hash plus
the sim day. There is no `dialogue.json`; `SectDialogueKeysTest` pins both
language files to the tables.

Client: `ClientSectDialogue` opens a `SectDialogueScreen`, or refreshes the
open one in place when the page is from the same avatar, and closes it when
the avatar is gone or more than 10 blocks away. The screen (vanilla `Screen`
and `Button`, `PanelTheme` colours, does not pause) draws the title
avatar · role · sect, a muted line with region and standing, the wrapped
lines, and one button per option; JOIN and LEAVE send the intent, FAREWELL
sends it and closes, Esc closes.

Log lines:

```text
SECT_DIALOGUE option=<JOIN|LEAVE|FAREWELL> x=<px> y=<px> w=<px> h=<px>   # client, per button per layout, GUI coords × GUI scale
SECT_ENTRY player=<name> intent=<JOIN|LEAVE> sect=<id> result=<ok|reason>   # server, every join/leave, dialogue or command
```

`WorldSimPlayers` also sends the chat lines `message.myvillage.world.sect.joined`
/ `.left`, and after each settled day sends an online player every
`player_*` event whose first param is their name (yearly promotion, sect
gone).

## Panel and commands

- `WorldSimSnapshot.mine` (`MySect`: sect id and name, rank, joined day,
  master name, contribution, standing with that sect, borrowed count, sect
  active) is filled for `OVERVIEW` while the player is in a sect, and for
  `SECT` only when it is the player's own sect; null otherwise.
  `WorldSimSnapshots.build` takes the asking player's UUID string for it.
- `SectSummary.bearing`: one of `n ne e se s sw w nw`, filled only in `HERE`;
  `WorldSimSnapshots.bearing(dx, dz)` (+x east, +z south, clockwise from
  north, 45° sectors, a boundary goes clockwise) from the player to the gate
  column. Text `world_sim.bearing.*`.
- `WorldPage`: overview card 我的宗门 (or `mine_none`, a pointer to the
  steward; a destroyed sect is marked), a 我在此宗 row on the own sect's page
  (rank · joining date · standing), and `相距%1$s格（%2$s）` on each 此地 sect
  row. Still read-only: joining happens only in the world.
- Commands, permission 2, output `commands.myvillage.world.player.*`,
  `.sect_join.*`, `.sect_leave.*`, `.gates.*`:

| Command | Does |
|---|---|
| `world sect <id> join <player>` | `WorldSimPlayers.join(target, id, true)` (forced join, see Ledger) |
| `world sect <id> leave <player>` | The target must be in that sect; `WorldSimPlayers.leave` with the usual penalty and cooldown |
| `world player <player>` | Sect and rank (or rogue), joining date, master, contribution, each standing, the last sect left with its date |
| `world gates [retry]` | `GateRealizer.status()`; `retry` clears this session's failed gates |

## Gate realization (P4-lite)

`GateRealizer` (registered by `WorldSimRuntime.register()`, server tick
listener):

- **Candidates.** With no job, every 20 ticks, while the ledger is active and
  `avatars_enabled`: each active sect whose gate is not realized and not
  failed this session, with the planar distance from the gate column to the
  nearest overworld player at most `gates.realize_radius`.
  `GateRealizePlan.pick` takes the nearest (ties to the smaller id). One job
  at a time.
- **Tickets.** The build area is the worldgen-style site plus the mountain
  margin (`SectGenerator.worldgenBuildArea`), cut into whole-chunk clips
  (`GateRealizePlan.clips`, row by row, each column in exactly one clip).
  Each clip's chunk gets a non-persistent region ticket
  (`myvillage_gate_realize`, distance 1) and loads in the background; after
  1200 ticks any still missing load in place.
- **Start.** Anchor y from the surface at the gate
  (`MOTION_BLOCKING_NO_LEAVES`, as `GateBuilder`); `SectGenerator.prepare`
  plans the compound with the build command's seed and variant, derives the
  mountain, and samples the natural surface of the whole area once, so every
  clip rests on the same silhouette.
- **Clips.** Each tick `gates.clips_per_tick` clips run
  `FramedSite.realizeClip`: the whole realizer (mountain, then compound)
  restricted to the clip, as a worldgen chunk does in `SectStructurePiece`.
  Clips that tile the area build the same compound as one pass.
- **Done.** Tickets released, `GateRealizations` record written,
  `markGateRealized(true)`, saved data dirty, `WorldSimAvatars.gateChanged`.
- **Chat.** `message.myvillage.world.gate.forming` / `.formed` / `.failed`
  to players within the radius.
- **Cancel.** The ledger replaced, the sect gone or its gate moved, or the
  gate realized meanwhile (`already_realized`), or the server stopping:
  tickets released, nothing recorded. A player walking away does not cancel.
- **Failure.** Any exception: tickets released, the gate rolled back to
  unrealized (`markGateRealized(false)`, the record removed), the sect added
  to the session's failed set (`world gates retry` clears it). Blocks
  already written stay.
- **With the build command.** `world sect <id> build` is refused while the
  realizer is building that sect (`commands.myvillage.world.gates.busy`);
  building another sect synchronously meanwhile is allowed. A job whose gate
  is found realized by other means cancels itself (`already_realized`).

```text
GATE_REALIZE sect=<id> state=queued gate=<x> <z> distance=<n> chunks=<n>
GATE_REALIZE sect=<id> state=started anchor=<x> <y> <z> clips=<n> seed=<n> variant=<v> load_ticks=<n> preloaded=<a>/<b>
GATE_REALIZE sect=<id> state=clip <i>/<n>          # every 8 clips and the last
GATE_REALIZE sect=<id> state=done seconds=<s> clips=<n> blocks~=<n> written=<n>
GATE_REALIZE sect=<id> state=cancelled reason=<server_stopping|ledger_changed|sect_changed|already_realized>
GATE_REALIZE sect=<id> state=failed reason=<exception class>
```

Cost per tick, an estimate from the implementer's offline count: a
compound is about 144 clips; the median clip writes about 1k blocks and the
90th percentile about 18k, so at the default clips per tick a whole compound
should take roughly 4 to 10 seconds. The headless capture (Evidence below)
measured one compound on superflat: 135 clips, started → done 3.9 s, 7.1 ms
per tick from `tick query` during the build. Whether it stutters on a real
server and client is open.

## Scripture hall (藏经阁, 0.42.0)

Slice 2: members borrow their sect's manuals by rank. Breakdown and defaults:
`docs/sect-entry-slice2-tasks.md`.

| Fact | Source of truth |
|---|---|
| Borrow cost per manual grade (all 0 now; read and shown, never charged) | `player.scripture_hall.borrow_cost_by_grade` in `rules.json` |
| Reach | `player.steward.interact_range` (shared with the steward) |
| Borrowable list, borrow record, `player_borrow` | `sim/engine/PlayerAffairs.borrowable` / `borrow`; facade `WorldSim.borrowable`, `hasBorrowed`, `recordBorrow`; `SectView.basicTechniqueId` |
| Block and owner | `block/ScriptureShelfBlock`, `block/entity/ScriptureShelfBlockEntity` (`Sect` tag), `block/ModBlockEntities` |
| Sites and placement | `sect/SectCourtyard.scriptureShelfSites`, `sim/runtime/avatar/ScriptureShelves`, `ScriptureShelfCommands` |
| Server service, refusal reasons, rows | `sim/runtime/player/ScriptureHall`, `ScriptureHallList` (pure) |
| Payloads | `sim/runtime/net/ScriptureHallPayload`, `ScriptureBorrowPayload` |
| Client | `client/sim/ScriptureHallScreen`, `ClientScriptureHall` |

**Placement.** `scriptureShelfSites(seed, anchor, variant)` plans the
compound and returns, for each slot on the `scripture` terrace whose role
starts with `flank_` (the two scripture pavilions, sorted by role), the
slot's centre column at the terrace elevation. The pavilion's own floor is
higher, so `ScriptureShelves.place` scans that world column from one block
below the site to 12 above for the first block with a sturdy top face
(`isFaceSturdy` up, not a shelf) and two free blocks over it (air or a
shelf), and stands the shelf on it; a shelf already there is reused and only
re-owned. Called after every ledger build: `GateBuilder.build` after the
gate record and before `markGateRealized`, and `GateRealizer` before it
releases its chunk tickets; an exception is logged and never fails the
build. `world sect <id> shelves` surveys the sites (`ScriptureShelves.survey`),
`shelves place` places them again for compounds built before 0.42.0.

**List** (`PlayerAffairs.borrowable`; empty without a living sect): a
heritage sect lends its chain (`heritages.json`), outer the first technique,
inner the first two, elder all; a sect without a heritage lends
`basicTechniqueId` to outer and basic plus `signatureTechniqueId` to inner
and elder. Empty ids and `basic_breathing` (mortal grade, no manual) are
dropped; duplicates removed, chain order kept. The screen shows a hint that
mortal-grade methods come from the inheritance stele.

**Borrow.** `ScriptureHall.handleBorrow`, server thread, in this order (first
failure wins, each logs `result=<reason>`, sends a chat line, and resends the
hall when there is one):

1. Throttle: under 4 ticks since the player's last handled borrow → dropped
   silently.
2. The shelf: the overworld and the player's level (`inactive`), within
   reach (`too_far`), a loaded shelf block entity with an owner
   (`unowned`), an active ledger and an active owning sect (`inactive`).
3. Membership: no record or no sect (`not_member`), another sect
   (`member_elsewhere`).
4. The technique id in `borrowable` (`not_borrowable`).
5. A manual exists (`TechniqueManualItem.manualFor` not empty; `no_manual`).
6. `WorldSim.recordBorrow`: `not_member`, `not_borrowable`,
   `already_borrowed` in that order; adds the id to `borrowed` and records
   `player_borrow` (importance 2, player, sect, technique name; the chronicle
   refuses an importance-1 event without a person subject, and a player is
   never one), closing the open day like a join.
7. Save dirty, the manual into the inventory (dropped at the feet when full),
   `message.myvillage.world.scripture.borrowed`, `SCRIPTURE_HALL ... result=ok`,
   the refreshed hall.

`ScriptureHall.open` makes the same shelf checks (failure: one chat line, no
screen); a non-member or member elsewhere gets the hall with `member=false`,
the reason, and no entries. Logs:

```text
SCRIPTURE_SHELF sect=<id> placed=<n>/<m> at=<x y z;...>
SCRIPTURE_HALL player=<name> intent=OPEN sect=<id> member=<true|false> entries=<n>
SCRIPTURE_HALL player=<name> intent=BORROW sect=<id> technique=<id> result=<ok|reason>
SCRIPTURE_HALL_UI technique=<id> borrowed=<true|false> x=<px> y=<px> w=<px> h=<px>   # client, per borrow button per layout
```

| Payload | Direction | Fields and bounds |
|---|---|---|
| `ScriptureHallPayload` `myvillage:scripture_hall` | server → client | shelf `pos`, `sectId`, `sectName` (64), `myRank` (16), `member`, `reason` (32), at most 16 `Entry(techniqueId (64), name component, grade 0..4, category (16), borrowed, cost ≥ 0)` |
| `ScriptureBorrowPayload` `myvillage:scripture_borrow` | client → server | shelf `pos`, `techniqueId` (64); nothing else |

`ModPayloads.PROTOCOL_VERSION` is `14` since 0.42.0. The screen refreshes in
place when a new hall for the same shelf arrives and closes 10 blocks from
the shelf; a borrowed row's button is disabled and reads 已借.

**Exclusive heritages.** This slice only makes the scripture hall the
player's single in-game source of sect manuals: an exclusive heritage's
chain reaches a player through their sect's hall (an elder of the sect that
holds it can borrow the whole chain, `WorldSimScriptureTest`). The creative
tab, `/myvillage cultivation manual`, and `/give` are unchanged (admin and
development), and `HeritageDefinition.exclusive` still has no runtime
reader; `StudyStart` does not look at where a manual came from.

**Commands.** `world sect <id> rank <player> <outer|inner|elder>` calls
`WorldSim.promotePlayer` (target must be a member of that sect; a rise
records `player_promotion` and tells the player; a lowered rank changes the
record without an event; output `commands.myvillage.world.sect_rank.*`).
`world sect <id> shelves [place]` as above (`commands.myvillage.world.shelves.*`).

**Evidence.** `python3 tools/world_sim_scripture_evidence.py [--sect ID]
[--distance 140] [--realize-timeout 600] [--shelf-distance 3.0] [--ui-scale 1]`
(holds the heavy-work lock itself): a heritage sect's gate built by walking
near it (both shelves placed), admin join, the hall as an outer disciple, a
borrow and the manual's `myvillage:technique` component in the inventory,
the button disabled on reopening, `rank inner` and the second entry, `leave`
and `member=false`. Output `out/preview/world_sim/scripture/`. The
2026-10-07 run (script `23d8ecb`, 0.42.0 tree) passed 19 of 19: 明心宗
(heritage 太白剑脉) built itself near the player (started → done 3.3 s, 135
clips, 9.7 ms per tick) and got both shelves, at (553, -35, -805) and
(581, -35, -805); the outer disciple's hall listed one entry,
`gengjin_yinqi_fa`, borrowed into a `manual_core_huang` with that technique;
on reopening the button read `borrowed=true` and the inventory held one
copy; after `rank inner` the hall listed two and `gengjin_jianjue` was
borrowed (`manual_active_xuan`); after `leave` the hall answered
`member=false entries=0`. Screenshots `hall_outer.png`, `hall_borrowed.png`,
`hall_inner.png`, `hall_refused.png`. Not captured: `shelves place`, the
chat and chronicle lines, an `already_borrowed` refusal (the disabled
button is not clicked). Slice 1's script passed 20/20 again on the same code.

**Tests.** `WorldSimScriptureTest`, `SectCourtyardScriptureTest`,
`SectCourtyardTest` (sites), `ScriptureHallPayloadTest`,
`ScriptureHallListTest`, `CombatPayloadTest` (protocol `14`),
`tools/tests/test_world_sim_scripture_evidence.py`.

**Open (owner).** The borrow cost (free now); the shelf's model (a
bookshelf-textured cube); whether one copy per technique is enough (a lost
manual can only be replaced by an admin).

## Sect tasks and apprenticeship (0.43.0)

Slice 3: one sect task a year for contribution, and a master whose guidance
speeds meditation. Breakdown and defaults: `docs/sect-entry-slice3-tasks.md`.

| Fact | Source of truth |
|---|---|
| Task rows (id, kind, count, contribution) | `data/myvillage/world_sim/sect_tasks.json`, loaded as `sim/data/ContentTables.SectTask` (`SimData.sectTasks`, `sectTask`); checked by `tools/validate_world_sim.py` `check_sect_tasks` |
| Task names and briefs | `world_sim.task.<id>.name` / `.brief`, registered by `TextKeys.taskKeys` |
| Guidance factor | `cultivation.master_guidance` in `rules.json` (the ledger's own mentoring value) |
| Offer, accept, progress, turn-in, apprenticeship, master loss | `sim/engine/PlayerAffairs` (`offerTask`, `acceptTask`, `advanceTask`, `completeTask`, `apprentice`, `daily`); facade `WorldSim.task`, `offerTask`, `acceptTask`, `advanceTask`, `completeTask`, `apprentice`; view `sim/TaskView` |
| World hooks, tribute stones, guidance math | `sim/runtime/player/SectTasks`; `WorldSimPlayers.masterGuidanceBasisPoints` |
| Dialogue scenes and keys | `SectDialogueScenes` (`Affairs`, `memberScene`, `canApprentice`, `taskReady`), `SectDialogueKeys` (`steward.task.*`, `elder.apprentice.*`) |
| Meditation | `cultivation/meditation/MeditationManager.progressFactorBasisPoints` |
| Panel | `WorldSimSnapshot.MySect.taskName/taskProgress/taskCount`, `WorldPage` |

**Record.** `PlayerMember` gains `taskId` ("" for none), `taskProgress`,
`taskTargetSectId` (courier destination, -1), `taskYear` (the year of the
last task taken, -1). Saved as `task`, `task_progress`, `task_target`,
`task_year`, always written and read as optional; the payload stays version
3 and an older one reads with no task.

**Offer** (`offerTask`, never stored). Empty for a player in no sect, with an
open task, or whose `taskYear` is this year. The year is
`floorDiv(day, days_per_year)` (between settled days, the year of the next
day to settle). One rng, `SimRng.at(seed, year × days_per_year,
playerId.hashCode(), Purpose.PLAYER_TASK (405), salt year)`, draws a row of
`sect_tasks.json` uniformly in file order, so the offer is the same all year;
a courier then draws its destination from the other active sects in id
order with the same rng, and with no other active sect the rng draws again
among the non-courier rows.

**Accept** (`acceptTask`): `not_member`, `task_active`, `task_done_this_year`,
`no_task`, first wins; records the offer with progress 0 and `taskYear`,
and a `player_task_accept` event.

**Progress** (`advanceTask(playerId, kind, amount)`, no event; false when
there is no open task of that kind; capped at the count), pushed by
`SectTasks`:

- patrol: `LivingDeathEvent` of a `BeastEntity` whose source entity is the
  player, while `RegionRuntimeService.currentRegion(player)` is the sect's
  home region, +1;
- courier: every 20 server ticks, an overworld player with a pending courier
  standing inside `SectCourtyard.footprint` of the destination's gate gets
  the full count;
- tribute: no progress; at turn-in `SectTasks.turnIn` counts the
  `low_grade_spirit_stone`s in the inventory (`tribute_short` if too few),
  asks the ledger to complete, then takes them slot by slot on the same tick.

Each patrol or courier step sends `message.myvillage.world.sect.task_progress`
and logs `SECT_TASK player=<name> kind=<kind> progress=<p>/<n>`.

**Turn-in** (`completeTask`): `not_member`, `no_task`, `not_ready` (a
non-tribute below its count), first wins; contribution += the row's
`contribution`, the task cleared, `taskYear` kept (no second task this
year), `player_task_done` event.

**Apprenticeship** (`apprentice(playerId, name, masterId)`): `not_member`,
`rank_too_low` (outer), `has_master`, `master_not_here` (the person is not a
living elder or sect master of the player's sect with status `at_sect`),
first wins; records `masterId` and `player_apprentice` with the master as
actor. In the dialogue the master is the elder avatar spoken to.

**Master loss** (`PlayerAffairs.daily`, run by `Engine.step` after the
people act, before successions): a member whose master is no longer a living
person of the player's sect loses the master that day, with
`player_master_lost` (the master as actor when the name is known). The
yearly review's silent clearing stays as a backstop. Since every `player_*`
event naming an online player is sent to them after the settled day, the
player reads it in chat.

**Guidance.** `masterGuidanceBasisPoints(player)` = `round((1 +
master_guidance) × 10000)` while the player is in a sect and the master is
alive and of the same sect, else 10000; `MeditationManager` settles with
`core technique factor × guidance / 10000` (unchanged when 10000).

**Dialogue.** For a member of the steward's sect the page is greeting,
introduction, `steward.member`, a task line, `steward.leave_ask`:

| State | Task line | Options |
|---|---|---|
| No open task, an offer | `steward.task.offer` (name, brief) | TASK_ACCEPT, LEAVE, FAREWELL |
| Open task, not ready | `steward.task.progress` (name, progress, count) | LEAVE, FAREWELL |
| Open task ready (tribute: stones in the inventory) | `steward.task.ready` | TASK_TURN_IN, LEAVE, FAREWELL |
| No open task, no offer, a task taken before | `steward.task.none_this_year` | LEAVE, FAREWELL |
| Never had a task and no offer | none (the 0.41.0 member page) | LEAVE, FAREWELL |

An elder (or the master) at the sect speaking to an inner disciple or elder
of the sect without a master shows `elder.apprentice.offer` (APPRENTICE,
FAREWELL); otherwise the elder pages are as before. Answers:
`steward.task.accepted`, `steward.task.done` (with the contribution
gained), `elder.apprentice.done`, or the refusal scene
(`steward.task.refuse.<reason>`, `elder.apprentice.refuse.<reason>`).
`handleIntent` accepts TASK_ACCEPT and TASK_TURN_IN only at a steward for a
member of its sect, APPRENTICE only at an elder for a member; anything else
gets the current page back. Option ids 3 `APPRENTICE`, 4 `TASK_ACCEPT`,
5 `TASK_TURN_IN`; `SectIntentPayload` kinds 0..5 and
`SectDialoguePayload.MAX_OPTION_ID` 5; protocol `15` since 0.43.0. Every
action logs `SECT_ENTRY player=<name> intent=TASK_ACCEPT|TASK_TURN_IN|APPRENTICE sect=<id> result=ok|<reason>`.

**Panel.** `MySect.taskName` carries the task id (the client shows
`world_sim.task.<id>.name`), `taskProgress` (0 for a tribute) and
`taskCount`; the 我的宗门 card has a 事务 row.

**Evidence.** `python3 tools/world_sim_tasks_evidence.py [--sect ID]
[--courier-tries 6] [--courier-timeout 15] [--skip-meditation]
[--meditation-seconds 10]`: admin join and `rank inner`, TASK_ACCEPT at the
steward, the task done by whichever kind was drawn (wolves summoned and
killed by the player, stones given, or a walk to other sects' gates), the
turn-in and contribution, no second offer, APPRENTICE at an elder, optional
meditation samples without and with the master, the panel. Output
`out/preview/world_sim/tasks/`. The 2026-10-07 run (script `6806fe7`)
passed 21 of 21 with 明心宗 (6 days a year): gate built in 3.8 s (135 clips,
6.3 ms per tick); admin join, `rank inner`; the steward offered TASK_ACCEPT
(`task_offer.png`) and the year's draw was `tribute_stones` (chronicle
"takes a sect task from 明心宗: Spirit Stone Tribute"); 5 stones given, the
page offered TASK_TURN_IN (`task_ready.png`), the turn-in took them (5 → 0)
and contribution went 0 → 10; reopened, the steward offered only LEAVE and
FAREWELL; the elder 侯南枝 offered APPRENTICE (`apprentice_offer.png`),
`world player` then named the master and the chronicle had the line; the
我的宗门 card with the task row is in `panel_task.png` (not machine-checked).
Not captured: patrol and courier (not drawn this run), `player_master_lost`,
and the guidance factor (both meditation samples gained 0, so the ratio
says nothing). These rest on `WorldSimTasksTest`, `PlayerAffairsTest`, and
`SectTasksTest`.

**Tests.** `WorldSimTasksTest`, `PlayerAffairsTest`, `SectDialogueScenesTest`,
`SectTasksTest`, `WorldSimSnapshotsTest`, `SimDataLoaderTest`,
`SectDialoguePayloadTest`, `WorldSimPayloadCodecTest`, `CombatPayloadTest`
(protocol `15`), `tools/tests/test_validate_world_sim.py`,
`tools/tests/test_world_sim_tasks_evidence.py`.

**Open (owner).**

1. Task counts and rewards (`sect_tasks.json`).
2. One task a year: enough? An unfinished task does not expire at the new
   year; it blocks the next offer until it is turned in.
3. Apprenticeship does not ask the master to be of a higher realm than the
   player.
4. The contribution bars for promotion are still 0 (`rules.json`
   `player.promotion`), so contribution buys nothing yet.
5. A player does not take one of the master's `disciplesPerMaster` places.

## World response (0.44.0)

Slice 4: news of the player's sect, hostile gates, and two admin acts.
Breakdown: `docs/sect-entry-slice4-tasks.md`.

| Fact | Source of truth |
|---|---|
| Which events are news | `sim/runtime/player/SectNews.relevant` (pure) |
| Sending news and `player_*` lines | `WorldSimPlayers.announce` (settled days and admin acts) |
| Hostile bar | `player.admission.hostile_standing_below` in `rules.json` (`Rules.PlayerAdmission.hostileStandingBelow`) |
| Turning visitors away | `SectDialogueScenes.turnedAway`, `Affairs.visitor` / `hostile()`; `SectDialogue.visitor` computes war and standing |
| Admin war and destruction | `sim/engine/AdminActs`, facade `WorldSim.declareWar` / `destroySect`; `SectPolitics.declare` / `dissolve`; commands in `WorldSimCommands` |

**News.** `SectNews.relevant(event, member)`: importance at least 2; not a
`player_*` event (those go only to the player whose name is `params[0]`);
for a member, `event.sects()` holds their sect; for a player in no sect,
`event.sects()` holds `leftSectId` and the type starts with `sect` (so a
rogue hears the destruction of the sect they just lost, but not its wars).
Region plays no part, unlike rumors. `WorldSimPlayers.announce(server, sim,
events)` walks the online players and the events in order, sends each
`player_*` line to its player and each relevant event as
`message.myvillage.world.sect.news` ("【宗门】%1$s" around the event line),
and logs `SECT_NEWS player=<name> event=<id> type=<type> sects=<[ids]>`. It
runs for every settled day (from `onDaySettled`) and after each admin act,
whose events a settled day never returns. Offline players are not told
later. A news line and a rumor of the same event can both arrive.

**Hostile gates.** `SectDialogue` computes, for a player who is not of the
speaker's sect, `atWar` (the player is in a sect whose relation to the
speaker's sect has state `war`; never for a rogue) and the standing with the
speaker's sect, and passes the hostile bar from the rules
(`Integer.MIN_VALUE`, never hostile, without rules).
`SectDialogueScenes.turnedAway`, checked by `decide` before every other
scene, gives `steward|elder.refuse.at_war` for a war, else
`steward|elder.refuse.hostile` for a standing below the bar, each with
FAREWELL only and said without the greeting and introduction. A member of
the speaker's sect is never turned away. `handleIntent` does not count JOIN
as offered while the visitor is turned away, so a forged JOIN gets the
refusal page back and the ledger is not asked.

**Admin acts** (between settled days, the open day closed like a player
action):

- `declareWar(a, b)`: refusals `no_sect`, `same_sect`, `sect_inactive`,
  `already_at_war`, first wins; then `SectPolitics.declare(ctx, a, b, -1)`,
  the same path as a war of the yearly politics (the declarer's master
  speaks the `world_sim.event.war.declare` line, both relations at war from
  today). The war then runs its yearly course.
- `destroySect(id)`: `no_sect`, `sect_inactive`; then a `sect_destroyed`
  event (importance 3, the master as subject, the ruin line
  `TextKeys.SECT_RUIN`) and `SectPolitics.dissolve(ctx, sect, false, id)`:
  members become rogues, players are released with the `sect_gone` line
  (`PlayerAffairs.sectDissolved`), a held heritage goes to the lost pool,
  all caused by the destruction event.
- Commands `world sect <a> war <b>` and `world sect <id> destroy`
  (`commands.myvillage.world.sect_war.*`, `.sect_destroy.*`) mark the save
  dirty and call `WorldSimPlayers.announce` with the act's events (for a
  destruction, every kept event from the destruction on). A destroyed
  sect's avatars are withdrawn by the next avatar pass, which drops gates
  the ledger no longer agrees with.

**Evidence.** `python3 tools/world_sim_news_evidence.py [--sect-a ID]
[--sect-b ID] [--news-timeout 10] [--withdraw-timeout 10]` (S4-E): admin join
of sect A; at sect B's gate, far from A, `world sect A war B` and the news
line in the client's chat (`【宗门】`, `[Sect]` in en_us; again after
`world advance 1` if needed); B's steward offering only FAREWELL
(`SECT_DIALOGUE` lines, the server's `steward.refuse.at_war`); back at A,
`world sect A destroy`, `world player` a rogue, the chronicle and chat line,
and A's avatars counted down to 0. The elder's refusal is not in the
script.
Output `out/preview/world_sim/news/`. Results: TODO-EVIDENCE

**Tests.** `WorldSimAdminActsTest` (war line and both relations, refusals,
destruction making members and players rogues), `SectNewsTest` (own sect,
minor and other sects and `player_*` excluded, a rogue's last sect, the
line), `SectDialogueScenesTest` (at war, hostile before any invitation,
rogues and own members).

**Open (owner).**

1. Should news be kept for offline players and told at login?
2. The hostile bar. With the shipped `player` numbers no play path reaches
   it: a join gives +20 and a leave -40, so one cycle bottoms out at -20,
   standings recover by 10 a year, and a sect only takes a player back at a
   standing of at least 0 after the cooldown. Only a war turns a player away
   today.
3. Real P4 (next section).

## Real P4 (worldgen placement) assessment

Not built. P4-lite (`GateRealizer`) builds a ledger sect's compound when a
player comes near, on terrain that already exists. Real P4 would have world
generation place the compound at the ledger gate, so a new world has every
gate standing from the start. What it would take:

- **Gate coordinates before chunks generate.** A structure is decided per
  chunk by its `StructurePlacement`. The ledger's gates (`GatePlacement`,
  one chunk-centred point per sect) would have to reach a custom
  `StructurePlacement` type (registered `StructurePlacementType`) that
  answers "is this a gate chunk" from an immutable snapshot of the gate
  chunks, readable from the worldgen threads, and a structure set using it.
- **Genesis comes too late.** The ledger is created on `ServerStartedEvent`
  (`WorldSimRuntime.onServerStarted`), after the region runtime, which also
  binds on `ServerStartedEvent`; the spawn area's chunks are generated
  before that event. Genesis (and the region graph it needs) would have to
  move to `ServerAboutToStartEvent`, using the seed from the world's
  generation options, or be written into SavedData when the world is
  created, before any chunk exists.
- **The random `myvillage:sect` structure.** `worldgen/structure_set/sect.json`
  scatters anonymous, empty compounds by `random_spread`. With real P4 they
  would compete with ledger compounds for the same look. Suggested: turn the
  random ones into abandoned gates (废弃山门: ruined, no avatars, maybe loot),
  or remove the structure set.
- **Old worlds.** Chunks already generated are never generated again, so a
  gate in explored land would get no compound from worldgen. P4-lite stays
  as the fallback for old worlds, for gates in chunks generated before the
  ledger existed, and for sects founded later (founding happens in play,
  long after their region was generated).
- **Reuse.** `SectStructurePiece` already builds a compound chunk by chunk
  with clips (the same slicing `GateRealizer` uses), so the piece can stay;
  what is new is the placement and the timing of genesis.
- **Risks.** `WorldSim.moveGate` (`world sect <id> build here`) moves a gate
  after its compound may already exist in generated chunks, leaving a
  compound the ledger no longer points to; a destroyed sect's compound
  stays in the world; `GateRealizations` would also have to learn of
  compounds worldgen built (today it is written only by the two build
  paths), or avatars and shelves would not find them. The scripture shelves
  (`ScriptureShelves.place`) run after a build and would need a worldgen
  equivalent (a post-processing step on the piece, or placement when the
  gate is first visited).

Decision for the owner: keep P4-lite only, or build real P4 for new worlds
with P4-lite as the fallback.

## Evidence (slice 1)

```bash
python3 tools/world_sim_entry_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600] [--skip-promotion] [--dialogue-scale 1]
```

One headless session (fresh superflat world, creative, noon, settlement
paused; holds the heavy-work lock itself, so never wrap it in `flock`). The
player gets an awakened root (peak 4000 bp) and the stage `mortal_qi_sensed`.
Steps: auto realization 140 blocks from the most-populated unbuilt gate
(`GATE_REALIZE ... done` within 600 s, `tick query` while building; on a
timeout the check fails and `world sect <id> build` is the recorded
fallback); the steward found by its `avatar.steward` name and photographed;
right click, JOIN clicked at the logged `SECT_DIALOGUE` coordinates,
`SECT_ENTRY ... result=ok`, `world player`, `world chronicle 5`; the 天下 tab
and the 我的宗门 card (screenshot only); `world advance 24` below the bar, then
`cultivation setrealm` to qi_refining_5 and another year
(`player.promote.inner`); LEAVE, then JOIN refused `rejoin_cooldown`; admin
join past the cooldown and admin leave. Output
`out/preview/world_sim/entry/`: `index.html`, `evidence.json`,
`commands.txt`, `server_log.txt`, `client_log.txt`, screenshots. Pure helpers
are tested in `tools/tests/test_world_sim_entry_evidence.py`.

Run of 2026-10-07 (capture script of `225c5b3`; developer evidence, not
owner acceptance): 20 of 20 checks passed. Files in
`out/preview/world_sim/entry/`.

| Check | Result |
|---|---|
| `world_ledger_active`, `player_qualified_setup` | ledger active; root and realm set for the player |
| `gate_realized_automatically` | player 140 blocks from the gate of 玄黄阁 (sect 3); `GATE_REALIZE` started → done 3.9 s by the log, 135 chunk clips, all preloaded; `gate_far.png` |
| `server_responsive_while_building` | `tick query` during the build: 7.1 ms per tick |
| `gate_realized_in_ledger` | `world sect` shows the gate (built) |
| `steward_present`, `player_faces_steward` | an avatar whose name tag has the 守山执事 part; `steward_nameplate.png` |
| `dialogue_opens_with_join` | options JOIN, FAREWELL; `dialogue_open.png` |
| `join_ok`, `player_record_outer`, `chronicle_has_join` | `SECT_ENTRY ... intent=JOIN ... result=ok`; `world player`: outer disciple of 玄黄阁, standing +20; the join in `world chronicle`; `dialogue_welcome.png` |
| (no check) | `panel_after_join.png`: the 我的宗门 card with 玄黄阁 / 外门弟子 |
| `no_promotion_below_threshold` | a year at `mortal_qi_sensed`: still outer |
| `promoted_inner_at_threshold` | realm set to 炼气五层, one day advanced (the snapshot refreshes after a settled day), then a year: `player.promote.inner`, `world player` shows inner |
| `steward_present_after_advance`, `dialogue_offers_leave` | the steward re-found after the advances; options LEAVE, FAREWELL; `dialogue_member.png` |
| `leave_ok` | `intent=LEAVE result=ok`; `dialogue_left.png` |
| `rejoin_refused_cooldown` | within the cooldown the dialogue offers only FAREWELL, so there is no JOIN to press and no `result=rejoin_cooldown` line; the refusal line is in `dialogue_rejoin_refused.png` |
| `admin_join_forced`, `admin_join_record`, `admin_leave` | `world sect 3 join` past the cooldown (`result=ok`, outer, standing 0); `world sect 3 leave` (`result=ok`) |

The capture world runs 6 days a year. Not captured: bearings on 此地 (unit
test only), `world gates [retry]`, promotion to elder, a destroyed sect
turning the player rogue (unit test only), and how any of it looks or feels
on a physical client.

## Tests

- Java: `sim/WorldSimPlayerMembersTest` (every admission reason, join,
  leave and standing, the rejoin cooldown, forced join, admin promotion, the
  steward rule, the snapshot, players not counted as people, codec v3 round
  trip, a version-2 payload read with no players, a malformed record),
  `sim/engine/PlayerAffairsTest` (one rank a year by snapshot, the
  contribution bar, standing recovery, a dropped master, a destroyed or
  dissolved sect, mortal below every ledger realm), `sim/runtime/avatar/AvatarPlannerTest`
  (steward first, steward cell), `GateRealizePlanTest` (pick, radius, clips
  cover the area once), `sim/runtime/net/SectDialoguePayloadTest`,
  `BearingTest`, `WorldSimSnapshotsTest` and `WorldSimPayloadCodecTest`
  (`mine`, `bearing`), `sim/runtime/player/SectDialogueScenesTest`,
  `SectDialogueKeysTest`, `combat/network/CombatPayloadTest` (protocol `13`).
  `WorldSimDeterminismTest` and the health bands are unchanged (players are
  not persons).
- Python: `tools/tests/test_world_sim_entry_evidence.py`.

## Verified and not verified

Gradle tests and the headless capture above (20 of 20 checks on
2026-10-07) are developer evidence. The owner's PC was not available for 0.41.0, so every surface in the README
ledger "Player sect entry (0.41.0)" is `not_verified`: auto realization and
whether it stutters, the steward's tag and place, the dialogue screen, join,
leave, the rejoin cooldown, the admin commands, the 我的宗门 card, bearings,
yearly promotion, a destroyed sect turning the player rogue, multiplayer,
and the Chinese text on a real client.

## Open decisions (owner)

1. The numbers in `rules.json` `player` (admission bars, standings,
   cooldown, promotion bars).
2. How the steward is marked: the fourth name-tag part, or a second line
   above the head (needs a custom renderer).
3. The realize radius and clips per tick (stutter can only be judged on a
   real machine).
4. The dialogue screen's style (plain vanilla buttons now).
5. Snapshot timing: the qualification snapshot is refreshed after each
   settled day (and on login, join, leave), and the yearly review reads it at
   the start of the year, so a player who breaks through on the last day
   before the new year is reviewed only a year later. Refreshing all online
   players' snapshots right before the review would close the gap.
6. A refused rejoin shows no JOIN button (the refusal line explains), so the
   `SECT_ENTRY ... result=rejoin_cooldown` line is only seen when JOIN is
   pressed on a stale page.

Not built yet: becoming sect master, news kept for offline players, and
real P4 (compounds placed at ledger gates by worldgen; assessed above).

## See also

- [40_world_sim.md](40_world_sim.md) (the ledger, avatars, compounds, the 天下 page), [39_humanoid_npcs.md](39_humanoid_npcs.md) (the cultivator body, `DATA_LEDGER_ROLE`, looks), [37_cultivation_panel.md](37_cultivation_panel.md) (the H panel), [28_cultivation_core.md](28_cultivation_core.md) (profile, realms, spiritual root)
- Briefs: `docs/player-sect-entry-brief.md`, `docs/sect-entry-slice1-tasks.md`, `docs/sect-entry-slice2-tasks.md`, `docs/sect-entry-slice3-tasks.md`, `docs/sect-entry-slice4-tasks.md`
- Manuals and study: [41_technique_system.md](41_technique_system.md) ("Manuals and study")
- [humanoid-npc-runtime](../../openspec/specs/humanoid-npc-runtime/spec.md), [sect-compound-realization](../../openspec/specs/sect-compound-realization/spec.md), [sect-worldgen-structure](../../openspec/specs/sect-worldgen-structure/spec.md)
- Knowledge-base index: [INDEX.md](INDEX.md)
