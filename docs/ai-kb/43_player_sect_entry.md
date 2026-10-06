# Player Sect Entry (拜入宗门)

0.41.0, slice 1 of player sect entry: the player gets a record in the world
ledger (命簿), joins a sect through its gate steward (守山执事) in a
server-authoritative dialogue, leaves it, rises by a yearly review, and
becomes a rogue when the sect is destroyed. Unbuilt gates near a player are
built in frames (P4-lite). The design is `docs/player-sect-entry-brief.md`
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
no way to take a master yet), `contribution` (0; nothing earns it yet),
`borrowed` (manual ids; empty until slice 2), `standings` (交情: sect id →
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

Cost per tick, an estimate from the implementer's offline count (not a
measurement on a running server): a compound is about 144 clips; the median
clip writes about 1k blocks and the 90th percentile about 18k, so at the
default clips per tick a whole compound should take roughly 4 to 10
seconds. Whether that stutters on a real server is open.

## Evidence

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

Results: TODO-EVIDENCE

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

Gradle tests and the headless capture above are developer evidence. The
owner's PC was not available for 0.41.0, so every surface in the README
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

Not built in this slice: the scripture hall (`borrowed`, `scripture_hall`),
contribution and sect tasks, taking a master, sect-event notifications
beyond the player's own lines, becoming sect master, and true P4 (compounds
placed at ledger gates by worldgen).

## See also

- [40_world_sim.md](40_world_sim.md) (the ledger, avatars, compounds, the 天下 page), [39_humanoid_npcs.md](39_humanoid_npcs.md) (the cultivator body, `DATA_LEDGER_ROLE`, looks), [37_cultivation_panel.md](37_cultivation_panel.md) (the H panel), [28_cultivation_core.md](28_cultivation_core.md) (profile, realms, spiritual root)
- Briefs: `docs/player-sect-entry-brief.md`, `docs/sect-entry-slice1-tasks.md`
- [humanoid-npc-runtime](../../openspec/specs/humanoid-npc-runtime/spec.md), [sect-compound-realization](../../openspec/specs/sect-compound-realization/spec.md), [sect-worldgen-structure](../../openspec/specs/sect-worldgen-structure/spec.md)
- Knowledge-base index: [INDEX.md](INDEX.md)
