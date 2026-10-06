# World Simulation

0.35.0 adds a world simulation (世界模拟): a ledger, the 命簿, of sects and
named cultivators who cultivate, find fortunes (奇遇), feud, fight, found and
lose sects, and die on their own, one sim day at a time, whether or not a
player is near. The ledger is the only authority. Entities seen in the world
(化身, avatars) and sect compounds (山门) are projections of ledger records: a
sect exists in the ledger whether or not a compound has been built, and its
record says where the compound belongs. The world sim is also the first real
caller of the region query interface (see
[14_deferred_roadmap.md](14_deferred_roadmap.md) §A).

There is no capability spec for the world sim; this note and the code are the
reference. The design that came before the code is
`docs/world-sim-overnight-brief.md` (Chinese, owner-facing).

## Package map

| Package | Holds |
|---|---|
| `sim` | The facade `WorldSim` and its immutable view records (`Overview`, `SectView`, `PersonView`, `RegionView`, `SimEvent`, `SimDate`); `SettlementScheduler`, `SimRng`, `SimObserver`, `SimData`, `SimDataException`, `SimFormatException` |
| `sim.model` | The ledger records (`WorldState`, `Person`, `Sect`, `Tombstone`, `Relation`, `SectRelation`, `Boon`, `RegionState`) and `StateCodec`, the save payload |
| `sim.engine` | One class per mechanic (table below), `Engine` (the order of a day), `Genesis`, `GatePlacement`, `SimContext` (working set and derived indices), `Chronicle`, `TextKeys`, `Anchor`, `Purpose`, `Rates`, `Naming`, `People`, `Roots` |
| `sim.data` | Strict loaders: `SimDataLoader`, `Rules`, `RealmTable`, `EncounterTable`, `ContentTables`, `SimJson` |
| `sim.cli` | The offline runner `SimCli` with `ChronicleWriter`, `Lang`, `ChineseNumerals` |
| `sim.runtime` | Everything that touches Minecraft: `WorldSimRuntime`, `WorldSimDriver`, `WorldSimSavedData`, `WorldSimServerConfig`, `WorldSimCommands`, `WorldSimRumors`, `RumorBoard`, `WorldSimText` |
| `sim.runtime.avatar` | Compounds and avatars (P3): `GateBuilder`, `GateRealizations`, `WorldSimAvatars`, `AvatarPlanner`; with `sect/SectCourtyard` and the avatar mode of `entity/npc/NpcEntity` |

Everything except `sim.runtime` is the pure core. It imports only `java.*`,
Gson, its own package, and the pure region classes `RegionGraph`,
`GenRegion`, `GenEdge`, `IntRange`, `RegionQueries`, `RegionPlacement`, and
`RegionContract`; `sim.cli` may also use `RegionTopologyGenerator`, `Ruleset`,
and `RegionProfile` to build a seed's graph offline. It never names
`net.minecraft`, `net.neoforged`, `org.slf4j`, or `com.mojang`, nor
`java.util.Random`, `Math.random`, `ThreadLocalRandom`, or a wall clock.
`SimPurityGuardTest` scans the sources for this, and
`tools/validate_world_sim.py` repeats the package check. So the core compiles
and runs with plain `javac`/`java` and the Gson jar, which is how balance is
tuned without Gradle.

`WorldSim` is all that the runtime and the tools call: `loadData`, `genesis`
(optionally with a `SimObserver`), `fromBytes`/`toBytes`, `step`, `day`,
`date`, `tierId`, `prehistoryDays`, `seed`, `scheduler`, the views
(`overview`, `sects`, `sect`, `findPersons`, `person`, `membersAt`,
`recentEvents`, `region`), and two mutations, `markGateRealized` and
`moveGate` (admin and test only; it rejects a point outside every region and
moves the sect's home region and its members who are at the sect).
`SimEvent` carries `id`, `day`, `type`, `importance`, `actors` (subject
first), `sects`, `regionId`, `causeId` (-1 for none), `textKey`, and
`params`; a param that starts with `@` is itself a language key.

## Data files

Directory `src/main/resources/data/myvillage/world_sim/`, every file
`"schema": 1`. `SimDataLoader` reads all six through a caller-supplied opener
(the runtime opens them through the server's resource manager at server start;
tests and the CLI read the source tree). An unknown or missing field, a bad
value, a repeated key, or a missing file throws a `SimDataException` naming
the file and field; there is no partial result. Every number lives here;
Java holds no balance constants, and docs point at the files rather than
copying values.

| File | Holds |
|---|---|
| `rules.json` | All balance numbers, by section: `time` (prehistory years, default days per year), `tiers` (`small`, `medium`, `large`: population, sects, rogue share), `chronicle`, `scheduler`, `roots`, `techniques` (grade factors, element match), `cultivation`, `breakthrough`, `injury`, `death_importance_by_rank`, `entrants`, `sects` (recruitment, promotion, mentoring, income, upkeep, pills), `genesis`, `gates`, `naming`, `importance`, `travel`, `seclusion`, `danger`, `fortune`, `artifact_power`, `meetings`, `combat`, `revenge`, `sect_relations`, `succession`, `decline`, `founding`. Rates are per year |
| `realms.json` | The sim's own realm ladder: 炼气, 筑基, 金丹, 元婴, each with lifespan, prestige, death importance, title suffix (真人, 真君), stages, and breakthrough parameters. Separate from the player realm registry; realms that exist in both must have equal lifespans |
| `encounters.json` | Fortunes: weight, rarity, importance, the text-key suffix, optional site kind, danger and tier bounds, realms, statuses, a contested flag, and effects (`progress`, `technique`, `breakthrough_pill`, `lifespan`, `root`, `artifact`, `injury`, `death`) |
| `names.json` | Surnames, given names (male, female, neutral), Daoist titles, sect prefixes, weighted sect suffixes |
| `techniques.json` | Techniques (功法): id, Chinese name, grade (`huang`, `xuan`, `di`, `tian`), element (five elements or `none`) |
| `lore.json` | Artifacts (with grade), sites (`ruin`, `cave`, `secret_realm`, `battlefield`, `tomb`), beasts (rank 1 to 4) |

Text is in both language files: `world_sim.*` (event lines, the two era
dates, realm, stage, rank, root, grade and cause words, report headings),
`commands.myvillage.world.*` (command output), and
`message.myvillage.world.rumor` (the rumor prefix). Person, sect, technique,
artifact, and place names are Chinese literals in both languages. An event
line is a family `<base>.1 .. <base>.n`, the variant chosen by hash so a
common event does not repeat one sentence; most lines open with a "who" block
(sect, rank key, name) so each person is anchored on first mention.
`TextKeys` registers every key the engine can emit with its param count, and
the chronicle refuses anything else. Keys emitted by checkpoint-1 builds stay
in the files (`TextKeys.legacyKeys`) because old saves may hold them.

## Time and dating

Sim day 0 is genesis. `WorldSim.genesis` builds the world (`Genesis`: sects in
regions that admit them, the anchor region getting the strongest, each with a
gate, a dead founder, a master of believable age and realm, elders and
disciples of mixed ages, a few rogues), then runs `time.prehistory_years ×
days_per_year` ordinary steps before handing the world over, so a new world
already has lineages, grudges, and sects of different standing.

Dates use the 启元 era (`SimDate`): a day at or after the end of prehistory is
启元 N 年 (`world_sim.date.era`), an earlier one 启元前 N 年
(`world_sim.date.before_era`); in `en_us` they read "Year N of Qiyuan" and
"Year N before Qiyuan". A fresh world that was never paused shows the
same year number as the cultivation calendar (修仙历). The ledger keeps its own
day count; ages and chronicle dates use it, so after a pause it lags the
calendar.

Rates in `rules.json` are per year. `Rates` converts them with the
`days_per_year` passed to each `step`: `1 - (1 - p)^(1/days_per_year)` for a
probability, `1 - e^(-n/days_per_year)` for an expected count, `rate /
days_per_year` for a linear quantity, all with `StrictMath`. Yearly business
runs on days where `day % days_per_year == 0`.

## Daily settlement

`Engine.step` settles one day. On the first day of a year: sect economy and
ranks, sect politics, fortune regrowth, entrants. Then every living person in
id order runs the per-person mechanics, stopping at once if they die. Then
masters' seats emptied that day are filled. People who arrive today act from
tomorrow.

| Mechanic | Class | Driven by (`rules.json` sections, other data) |
|---|---|---|
| Cultivation: progress = base × root × technique (+ element match) × rank × sect resources × master guidance × region qi × status × injury; minor stage-ups at the cap | `Cultivation` | `cultivation`, `roots`, `techniques`; region `qi` from the graph |
| Great-realm breakthrough: attempts at the last stage's cap, desperate attempts when lifespan runs out, failure with injury or death (走火入魔); a fortune that tipped a success, or an old wound behind a death, is named and becomes the `causeId` | `Breakthrough` | `breakthrough`, `roots`, `techniques`, `sects` (pills), `importance`; `realms.json` breakthrough parameters |
| Lifespan and healing: injuries heal yearly; death of old age (坐化) at realm lifespan plus bonuses | `Lifespan`, `Deaths` | `injury`, `death_importance_by_rank`; `realms.json` lifespans |
| Travel and seclusion: journeys along 连 edges of the region graph (a walled region only through its pass), destination by qi, tier, and for the bold danger; seclusion near a bottleneck; a hunter heads for the quarry's region | `Travel` | `travel`, `seclusion`; region graph edges |
| Beasts on the road: rate climbs with region danger; win, flee, wound, or death | `Danger` | `danger`, `combat`; `lore.json` beasts |
| Fortunes (奇遇): rarer finds in high-tier, dangerous regions; each find depletes the region's richness, which regrows yearly; contested finds start a fight; every boon remembers the event that granted it | `Fortunes` | `fortune`, `artifact_power`, `roots`; `encounters.json`, `techniques.json`, `lore.json` |
| Meetings: one sampled partner in the same region; friendship, quarrel, spar, robbery, or a fight; members of sects at war fight on sight | `Meetings`, `Combat` | `meetings`, `combat`, `revenge`, `techniques` |
| Fights: power = stage power × technique × best artifact × injury, win chance steep in the noisy power ratio; flee, wound, or kill; a kill makes the victim's master, disciples, and close friends avengers and sours the two sects' standing | `Combat` | `combat`, `meetings`, `revenge`, `importance` |
| Revenge chains: avengers act when they judge they can win, the cautious cultivate first; a revenge kill names the wrong it answers and points at it; old grudges lapse | `Revenge` | `revenge`, `travel` |
| Sect economy (yearly): resources from region qi and membership, prestige, promotion outer → inner → elder, masters taking disciples | `SectAffairs` | `sects`, `cultivation` |
| Sect politics (yearly): standing drifts, drops on wounds and kills, becomes feud then war; set battles; truce, tribute, annexation, or destruction; decline and ruin; schisms; rogues taking service; founding by a strong rogue, a losing heir, or a schismatic elder (开宗立派) | `SectPolitics` | `sect_relations`, `decline`, `founding`, `succession`, `sects` |
| Succession (end of day): the strongest elder succeeds; a comparable ambitious rival may contest and leave to found a rival sect; a sect with no one strong enough declines, one with no one left is extinct | `Succession` | `succession` |
| Entrants (yearly): new disciples and rogues (炼气一层, teenagers) pull the population toward the tier's target; prestigious sects test more candidates | `Entrants` | `entrants`, `sects`, `decline`, `roots`, `tiers` |
| Gates: one chunk-centred coordinate per sect at founding, inside the home region per `RegionQueries.regionAt`, within a radius of the origin and spaced from other gates; never recomputed | `GatePlacement` | `gates` |
| Names: unique across history where possible | `Naming` | `naming`; `names.json` |
| Chronicle pruning | `Chronicle` | `chronicle` |

Every event with a cause carries its `causeId`, and its text states the cause
in words (a distinct key per cause variant), because the chronicle is read as
prose.

## Determinism and save format

- **Hash-derived randomness.** Every decision draws from
  `SimRng.at(seed, day, subject, purpose[, salt])`, a splitmix64 mix of those
  values, never from a shared stream. Purpose codes are explicit int constants
  in `engine.Purpose`; never reuse or renumber one. Adding a behaviour or a
  person therefore does not reshuffle the other decisions.
- **Same inputs, same bytes.** The world is deterministic in (seed, region
  graph, data, tier, days per year). People act in id order, and the derived
  indices in `SimContext` are rebuilt from the ledger, so a restored world
  never diverges.
- **Importance.** 1 minor, 2 notable, 3 major. Importance 2 and 3 are kept
  forever; importance 1 keeps the latest `chronicle.minor_per_person` per
  living person and is dropped when they die. The dead become compact
  tombstones (name, title, sect, realm, birth and death day, cause, killer)
  kept forever, so later events can still name them.
- **Payload.** `StateCodec` writes canonical compact UTF-8 JSON (fixed key
  order, maps in id order) with `"format": "myvillage:world_sim"` and
  `"version": 1`, so equal states give equal bytes. It holds the seed, tier,
  genesis days per year, prehistory days, day, id counters, the scheduler's
  state, region richness, sects, persons, tombstones, and the kept chronicle.
  A reader ignores unknown fields and defaults missing optional ones; an
  unknown format, malformed JSON, an unknown realm, a region missing from the
  graph, or a tier missing from the rules throws `SimFormatException`, and a
  newer `version` fails with "saved by a newer world-sim format version N".
  Change it additively and bump `version` when the payload changes.
- **Wrapper.** `WorldSimSavedData` is overworld SavedData
  `myvillage_world_sim` (`data/myvillage_world_sim.dat`): a wrapper `format`
  (1), the `tier`, a `paused` mirror, and the core payload as a byte array.
  While a live world is attached every save serialises it afresh; otherwise
  the loaded tag is written back unchanged, so a payload this build cannot
  read is never overwritten.

The `SimObserver` (`onEvent`, `onDayEnd`) is not saved; the CLI uses it to
record every event ever emitted, before pruning.

## Runtime wiring

`WorldSimRuntime.register()` runs after the region runtime in
`MyVillageMod`. At server start it takes the graph from
`RegionRuntimeService.graph()`, loads the data, and either restores the saved
ledger or, for a world with none (old worlds included), runs genesis once with
the overworld seed, the configured tier, and the calendar's current days per
year. The tier is then fixed in the save; a later config change is ignored
(and logged). The ledger stays inactive for the session, and the save on disk
is left as it is, when the region runtime did not load, the data failed to
load, the wrapper or the payload is from a newer format, the payload cannot
be restored, or the file exists but could not be read (genesis is refused
then). Commands report the reason.

Each server tick, `WorldSimDriver` feeds the calendar's day index
(`elapsedCalendarTicks / ticks_per_day`) to `SettlementScheduler.observe` and
settles at most one pending day:

- each new calendar day adds one pending day, capped at
  `WorldSimDriver.pendingCap`: the smaller of the config's
  `catch_up_cap_days` and `rules.json` `scheduler.max_pending_days` (the
  rules value is a hard ceiling the config can only lower), so a jump of the day index (a smaller
  `ticks_per_day`) cannot stall the server; a day index that moves backwards
  adds nothing and re-anchors;
- the calendar advances on every tick with at least one player online, in any
  game mode ([30_cultivation_playable_loop.md](30_cultivation_playable_loop.md));
  with no player online neither the calendar nor the ledger moves;
- `pause` stops settlement only: the calendar keeps running, the anchor
  follows it, nothing accumulates, and `resume` does not catch up (days
  already pending when the pause began are kept);
- `advance <days>` settles days now, even while paused, and leaves the
  scheduler alone.

The save data is marked dirty after every settled day, scheduler change,
pause, and advance. If settlement throws, the ledger goes inactive until
restart and the save keeps the last checkpoint, never a half-settled day.

## Commands and config

All under `/myvillage world`, permission 2; output goes through
`commands.myvillage.world.*` keys, so each client reads its own language.

| Command | Shows or does |
|---|---|
| `/myvillage world` or `world info` | Era date, sim day, calendar day, pending days, running or paused, tier, population against target, deaths, living per realm, sects, event count, the five foremost people |
| `world sects [all]` | Active sects (with `all`, the destroyed too): region, master, members, top realm, prestige, gate coordinate, whether the gate is realized |
| `world sect <id\|name>` | One sect: founding, parent sect, master, members, resources, prestige, signature technique (镇派功法), gate, relations to other sects, strongest members at the sect |
| `world sect <id> build [here]` | Build the sect's compound (山门) at its ledger gate, or with `here` move the gate to the caller first; synchronous (see "P3" below) |
| `world person <name>` | Up to five people whose name or Daoist title contains the text, living first: realm and stage, root grade, age, sect and rank, place and status, technique, master, relation counts; for the dead, death date, cause, and killer |
| `world chronicle [1-50]` | The latest notable and major events (importance 2+), oldest first; default 10 |
| `world here` | The caller's region (`RegionRuntimeService.currentRegion`): tier, qi, danger, living count, seated sects with gate distance, the strongest people present, recent notable events. Players only |
| `world pause` / `world resume` | Stop or restart settlement; saved with the ledger |
| `world advance <1-3650>` | Settle that many days now, even while paused; reports events and notable events |

Server config `myvillage-world_sim-server.toml` (instance `config/`, a copy
in a world's `serverconfig/` overrides it):

| Key | Default | Meaning |
|---|---|---|
| `world_sim.tier` | `small` | `small` (about 80 people), `medium` (about 300), `large` (about 1000); read only at genesis |
| `world_sim.catch_up_cap_days` | 30 | Most pending sim days (1 to 3650), never above `rules.json` `scheduler.max_pending_days` |
| `rumors.rumors_enabled` | true | Rumors on or off |
| `rumors.rumors_per_minute` | 2 | Most rumors one player receives per real minute |
| `rumors.rumor_queue_cap` | 6 | Most rumors waiting per player; the oldest is dropped on overflow |
| `avatars.avatars_enabled` | true | Project avatars at all |
| `avatars.avatar_spawn_radius` | 64 | Avatars appear while a player is within this many blocks (8 to 128) of a realized compound's site; they are withdrawn beyond this radius plus 32 |
| `avatars.max_avatars_per_sect` | 12 | Most avatars per compound (0 to 64) |
| `avatars.max_avatars` | 40 | Most avatars in the world (0 to 256); the compound nearest a player is served first |

The values other than `tier` are read on use.

## Rumors (江湖传闻)

`WorldSimRumors` listens to every settled day (from the tick and from
`advance`). `RumorBoard.audience` decides who hears an event: a major one
(importance 3) reaches every online player, those standing in the event's
region first; a notable one (importance 2) only the players in its region;
minor ones nobody. Each player has a capped queue and a sliding one-minute
window; queues drain after each settled day and every 20 ticks, in offer
order. A line is
`message.myvillage.world.rumor` around the event's own text key, rendered by
the client in its language. A player's queue is forgotten on logout, and the
board is cleared when the server stops or rumors are switched off.
`RumorBoard` has no Minecraft types and is unit-tested.

## Offline CLI and chronicle report

```bash
/usr/bin/python3 tools/world_sim_cli.py run --seed 1 --tier small --years 300 \
    --out out/preview/world_sim/seed1.json --text out/preview/world_sim/seed1.txt
/usr/bin/python3 tools/world_sim_report.py --runs small:1,2,3 medium:1,2,3 --years 300
```

`tools/world_sim_cli.py` compiles the core (without `sim.runtime`) and the
pure region classes with `javac` into `build/world_sim_cli/` whenever a source
is newer (`--rebuild` as the first argument forces it), then runs `SimCli`:
it builds the seed's region graph from the source tree, runs genesis and N
more years, and writes one JSON document with `config`, `final_state` (the
save payload), `census` (day 0, then every year), and `events` (every event
ever emitted, with rendered text). Options: `--days-per-year N`,
`--resources DIR` (searched before `src/main/resources`, for trying data
variants), `--lang zh_cn|en_us` (default `zh_cn`). `--text` writes the
chronicle as prose: notable and major events by year, then biographies of
the most eventful people. No lock and no Gradle are needed.

`tools/world_sim_report.py` runs the CLI for each `tier:seed` unless its data
file exists for the same year count (`--force` reruns, `--render-only` never
runs it), then renders every `out/preview/world_sim/data/*.json` into a
self-contained, deterministic `<tier>_<seed>.html` and an `index.html`. A page
has population and realm charts (人口与境界), a sect timeline (宗门兴衰),
biographies (列传·代表人物), what drove successes and failures (成败之由), the
chronicle (纪事), the roll of the dead (陨落名录), and data self-checks
(数据自检). `out/preview/` is served publicly on port 8766:
`http://43.156.135.198:8766/world_sim/index.html`.

## Validator

`tools/validate_world_sim.py` (a release-gate step, `validate-world-sim`)
checks: every data file parses with schema 1, required fields, enum values,
positive weights, unique ids, and cross-references (realm ids and stages
named by rules and encounters, site kinds, a technique and artifact for every
grade an encounter or genesis can grant, sim lifespans equal to the player
realm files for shared ids, the config's three tiers present in rules);
every `world_sim.` key the core can emit (literals, constants, and the
data-derived realm, stage, rank, root, grade, breakthrough, and fortune keys)
exists in both `en_us` and `zh_cn`; every `world_sim.*` key in one file is in
the other with the same `%n$s` slots numbered 1..n, and a family's variants
match; the runtime's `commands.myvillage.world.*` and
`message.myvillage.world.*` keys exist in both with the same slots; and the
core is free of Minecraft, NeoForge, Mojang, and slf4j. `TextKeyCoverageTest`
pins the param count per key.

## Tests

Pure core (`src/test/java/com/example/myvillage/sim/`):
`WorldSimDeterminismTest` (identical bytes and events for the same inputs, a
different seed differs, prehistory runs, a second days-per-year value is also
deterministic, era dates), `WorldSimSaveLoadTest` (byte-identical round trip,
a restored world continues exactly like the original, newer version and
garbage fail, added and missing optional fields tolerated, unknown realm is a
format error), `SettlementSchedulerTest` (one pending day per calendar day,
capped forward jump, backwards re-anchors, pause never catches up, a paused
run matches the unpaused one only later), `WorldSimGenesisTest` (sects in
regions that admit them, gates inside their region, within the radius,
chunk-centred, spaced; gate mutations), `SimDataLoaderTest`,
`SimPurityGuardTest`, `RealmLifespanAgreementTest` (shared realm lifespans
equal the player realm files; the mortal lifespan is not contradicted),
`TextKeyCoverageTest` (every emittable key in both files with matching
slots), `WorldSimLivenessTest` (small tier, three seeds, 400 years),
`WorldSimHealthTest`, and `WorldSimPerformanceTest` (ms per day on the medium
and large tiers, with limits several times the observed cost).

Runtime (`sim/runtime/`, no Minecraft server needed): `WorldSimDriverTest`,
`RumorBoardTest`, `WorldSimSavedDataTest` (newer format written back
untouched, detach keeps the last checkpoint), `WorldSimServerConfigTest`,
`WorldSimTextTest`; for P3, `avatar/AvatarPlannerTest` (selection order,
lowest terrace first with spacing, a full terrace spills upward, stable cell
per person, the name tag, per-sect build seed and variant),
`avatar/GateRealizationsTest` (round trip in sect order, a rebuild replaces
the record), and `sect/SectCourtyardTest` (cells against the real plan).

`WorldSimHealthTest` runs small seeds 1 to 3, medium seeds 1 and 2, and
large seed 1 for 300 years after prehistory at 6 days per year, and checks
for each run: population within a band of the tier's target; an averaged
realm pyramid (炼气 > 筑基 > 金丹 ≥ 元婴) with 元婴 at most 2 % of the target;
someone reaching 金丹; at least five successions; active sects never below a
per-tier minimum; on the small tier no sect holding over 60 % of all members
for more than 100 years running; importance-3 events per year within a band
and importance-2 events per year under a cap; and, across the set, sects
both founded and destroyed. The bands, pinned from observed runs with a
margin, are in the test's `BANDS`:

| Tier | Population (× target) | Importance 3 per year | Importance 2 per year | Min active sects |
|---|---|---|---|---|
| small | 0.80 to 1.15 | 0.15 to 3.0 | at most 8 | 2 |
| medium | 0.85 to 1.12 | 0.4 to 10 | at most 25 | 5 |
| large | 0.88 to 1.10 | 1.0 to 20 | at most 60 | 10 |

A deliberate retune that moves a run outside a band updates the band in the
same change.

```bash
/usr/bin/python3 tools/validate_world_sim.py
/usr/bin/python3 -m unittest tools.tests.test_validate_world_sim tools.tests.test_world_sim_report
flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew test --tests 'com.example.myvillage.sim.*'
```

## Headless evidence

```bash
python3 tools/world_sim_evidence.py [--part session|restart|all] [--ticks-per-day N]
```

It holds the shared heavy-work lock itself (never wrap it in `flock`).
`session` (about 6 to 8 minutes) starts the acceptance server and one client
in a fresh superflat world with the calendar sped up (`ticks_per_day`, default
200, set in `run-acceptance/config/myvillage-server.toml` and restored
afterwards), switches the player to creative, runs every `/myvillage world`
command over RCON, shows the calendar and the ledger advancing, pauses
while at least two calendar days pass, resumes, and floods rumors with `advance` to show
the rate limit. `restart` (about 3 to 4 minutes, server only, so the calendar
stands still) advances and pauses a kept world, stops, adds a per-world
config with `tier = "medium"`, starts again, and compares `info` and
`chronicle`. Output goes to `out/preview/world_sim/evidence/` (`summary.md`,
`evidence.json`, command and rumor transcripts, server logs, a chat
screenshot). RCON replies are rendered in `en_us`. The 2026-10-06 run passed
all 17 checks; it is developer evidence, not an owner verdict.

## Known limits

- P4 is not done: worldgen does not place compounds at ledger gates, so a
  ledger sect has no compound until one is built with
  `/myvillage world sect <id> build`. The `myvillage:sect` compounds that
  `worldgen/structure_set/sect.json` scatters at random (biome tag
  `has_sect`) are unrelated to the ledger, empty, and unknown to
  `/myvillage world`.
- Every avatar uses the one cultivator look; there are no per-person or
  per-sect variants.
- Avatars are neutral: no combat, dialogue, trade, or other interaction, and
  nothing a player does to one reaches the ledger.
- `build` without `here`, on real terrain far from the player, has not been
  run; it loads or generates the gate chunk and builds synchronously, and a
  long build may trip a production server's `max-tick-time` watchdog.
- The player is not in the ledger and has no relation to its people.
- The health bands are pinned at 6 days per year only; another days-per-year
  value is tested for determinism, not for the long-run shape.
- `/reload` does not re-read the data; it is read once at server start.
- How the chronicle, rumors, and avatars look on a physical client (zh_cn
  especially), and multiplayer, have not been observed; see the README ledger.

## P3: sect compounds and avatars

### Building a compound at a ledger gate

`/myvillage world sect <id> build [here]` (`sim/runtime/avatar/GateBuilder`,
overworld only, active sects only) builds the worldgen-style compound
(derived mountain, terraces, grand stairs, axis corridor, buildings;
`SectGenerator.generateForcedAt`) where the ledger puts the gate. Without
`here` it loads (or generates) the gate's chunk and takes the anchor's y from
the surface there (`MOTION_BLOCKING_NO_LEAVES`); with `here` it first moves
the ledger gate to the caller (`WorldSim.moveGate`, refused outside every
region) and builds at the caller's feet. The build seed hashes the world seed
with the sect id and the spire variant is picked per sect from that seed, so
a rebuild at the same place builds the same compound. The build runs on the
server thread and blocks it until done; the start message says so. On
success it records the anchor (with y), seed, variant, and sim day in
overworld SavedData `myvillage_world_sim_gates` (`GateRealizations`, one
record per sect, a rebuild replaces it) and calls `markGateRealized`. The
ledger stays the authority: the build follows its record, never the reverse.

### Courtyard ground

`sect/SectCourtyard.cells(seed, anchor, variant)` derives, from the same plan
the generator builds, where a figure can stand in the open: each terrace's
floor rectangle minus its edge row (retaining band faces, cliff back), minus
every building slot (the larger of slot bounds and template footprint, plus a
one-block margin), minus every grand stair with its cheek walls (plus one
block), minus the axis corridor x 28..34 (kept clear so nobody stands in the
way), minus a detached spire and its flying bridge only when the spire is
actually built (with margin; since 0.35.1 no variant is, see below).
Positions are terrace floor + 1, lowest terrace first, then by z and x,
deterministic. `SectCourtyard.footprint` gives the compound's whole site
rectangle. `SectCourtyardTest` pins the cells against the real plan (seed 7:
272 on the gate terrace, either side of the corridor behind the gate and in
the two front corners; 310 on the disciple terrace).

The compound itself (0.35.1, after the owner rejected 0.35.0's floating eave
and gallery fragments and its blocked axis): terraces symmetric about x 31; a
levelled forecourt in front of the gate; a paved corridor through a passage
cut in the gate building up to the row before the principal hall, kept clear
by a final pass; solid retaining bands with stone-brick faces (no wall
blocks); 9-wide grand stairs with a landing and cheek walls; flanks mirrored
beside the axis; no covered galleries and no cloud sea. Template blocks that
touch no other template block are dropped at placement
(`DropIsolatedBlocks`). The detached spire is built only where it clears every
slot, terrace and stair, which none of the three variants does yet, so it is
skipped. `SectCompoundRealizationTest` builds whole compounds with the real
templates into an in-memory world (both build paths) and checks the axis
walk, floating blocks, wall blocks, stairs and courtyard ground.

### Avatars (化身)

An avatar is a `myvillage:cultivator` made a ledger avatar by
`NpcEntity.becomeLedgerAvatar(personId)` before it is added (irreversible).
The ledger person id is synced to clients (`DATA_LEDGER_PERSON`, -1 for an
ordinary NPC). An avatar is never saved with its chunk (`shouldBeSaved`
false); it is not attackable and takes no damage except from sources that
bypass invulnerability (`/kill`, the void); it is fire immune and not
pushable; its stroll goal is removed (it still looks at players and around);
`mobInteract` passes without effect. Its name tag is always visible:
`entity.myvillage.cultivator.avatar` = `%1$s · %2$s · %3$s` (name, realm
through `world_sim.realm.*`, sect), so each client reads the realm in its
own language. A summoned or spawn-egg cultivator is unchanged. If a copy
escapes some other way it carries the `WorldSimPerson` tag, loads as an
avatar its manager does not know, and is refused.

`sim/runtime/avatar/WorldSimAvatars` is the only thing that spawns, renames,
or withdraws avatars. Every 20 ticks, for each gate record the ledger agrees
with (sect active, gate realized, same x/z):

- the distance to the nearest player is measured to the compound's site
  rectangle, not to the gate;
- within `avatar_spawn_radius`, the members at the sect (`WorldSim.membersAt`)
  are chosen by `AvatarPlanner.select`: master, then elders, then the rest,
  each group by realm and stage, up to the per-sect cap, with the global cap
  served nearest compound first; each gets a courtyard cell
  (`AvatarPlanner.pickCell`: lowest terrace with room, at least two blocks
  from the other avatars on that terrace, starting from a cell hashed from
  the person id so a person tends to stand in the same place), once its chunk
  is loaded;
- beyond the spawn radius plus 32 blocks, the gate's avatars are discarded;
  between the two radii nothing changes.

Each pass and after every settled day, avatars of people no longer selected
(dead, left, travelling, secluded, displaced by the cap) are discarded and
names refreshed. Everything is withdrawn when the ledger is inactive, when
avatars are disabled, on server stop, and if a pass throws. One entity per
person id: the manager keeps the entities themselves, so it can discard one
even in a chunk that has since unloaded, and on `EntityJoinLevelEvent` it
cancels any avatar it did not spawn.

The `humanoid-npc-runtime` spec and `genops/contracts/entities/cultivator.yaml`
describe avatars: the contract's `state.synced` lists `ledger_person_id`,
`state.persisted` lists `WorldSimPerson`, and a `world_sim_avatar` section
records the mode; `tools/validate_custom_entities.py` (`check_npc_state`)
checks the synced fields and persisted tags against the Java.

### Avatar evidence

```bash
python3 tools/world_sim_avatar_evidence.py [--x 200 --z 200]   # out/preview/world_sim/avatars/
```

One headless session (about 8 to 12 minutes, holds the heavy-work lock
itself; superflat, creative, settlement paused) builds the compound of the
sect with the most members at the gate with `build here`, then checks the
avatars on the courtyard, withdraws them by moving the player 300 blocks away
with the courtyard chunks force-loaded, returns, runs `advance 60`, and tries
`/damage` on an avatar and on a summoned cultivator. The 2026-10-06 run
passed all 19 checks: 12 named avatars (the cap; 14 members were at the gate)
on the stone-brick floor of the lowest terrace with open sky above; 0 at 300
blocks; 12 again on return, all with new UUIDs; after `advance 60` the set
followed the ledger (11 at the sect: 3 left, 2 arrived) with no duplicates;
avatars invulnerable, the summoned cultivator damaged; an escaped avatar copy
refused. Screenshots and `index.html` are in
`out/preview/world_sim/avatars/`. RCON renders the name tags in `en_us`
without the client's translation of the realm. Developer evidence, not an
owner verdict.

## See Also

- Region runtime and its query API: [13_region_topology.md](13_region_topology.md), [`region-runtime-binding`](../../openspec/specs/region-runtime-binding/spec.md)
- Deferred region consumers: [14_deferred_roadmap.md](14_deferred_roadmap.md) §A
- Shared calendar and personal lifespan: [30_cultivation_playable_loop.md](30_cultivation_playable_loop.md), [`cultivation-lifespan-calendar`](../../openspec/specs/cultivation-lifespan-calendar/spec.md)
- The cultivator body used by avatars: [39_humanoid_npcs.md](39_humanoid_npcs.md), [`humanoid-npc-runtime`](../../openspec/specs/humanoid-npc-runtime/spec.md), `genops/contracts/entities/cultivator.yaml`
- Sect compounds: [`sect-compound-realization`](../../openspec/specs/sect-compound-realization/spec.md), [`sect-worldgen-structure`](../../openspec/specs/sect-worldgen-structure/spec.md)
- Knowledge-base index: [INDEX.md](INDEX.md)
