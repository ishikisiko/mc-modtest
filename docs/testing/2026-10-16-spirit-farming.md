# Spirit farming — 0.53.0

Source `85e284a`: permanent spirit-only soil, six existing herbs as their own
planting items, exactly three ages (0/1/2), authored mature harvest and 18 stage
models. No separate seed, wild spawning, water/hoe prerequisite or moisture timer.

- Three focused Python resource tests, generator consistency, mod-item and
  alchemy validators passed. Inspected actual-model contact sheet at
  `out/preview/spirit_crops/stages.png` (all six species and three stages).
- Modal `20261015T195420Z-08273a7c`, `crops-build`: three SpiritCropsTest tests
  passed, zero failures/errors/skips, fresh jar built. Gradle 80.876s;
  controller 106.446s; artifact download and termination confirmed.
- Java checks cover exact age bounds, one-stage bone meal advancement, allowed
  soil/plant combinations, permanent fertility/no drying state or random tick,
  and the same planting/pick item. Generated loot tests cover same-herb return
  and maturity-only yield multiplication.
- Every owned crop/soil resource and both runtime classes were checked inside
  the jar. SHA256:
  `32fed0be44649ac77777e6608c48508b8da3260aedaf57ca10ffdfd25b847434`.
- DevHost saved/stopped without force, backed up 0.52.3, installed 0.53.0 and
  reopened the previous `agent-test` world. DevBridge confirms 0.53.0 and all
  six crop block registrations. No player inventory or existing builds altered.
- Owner in-game model verdict, natural growth pacing, survival planting/harvest
  journey and multiplayer remain `not_verified`; preview and registration are
  developer evidence. No full release or unrelated gameplay suite was run.

See [spirit farming](../ai-kb/51_spirit_farming.md) and
[preview](http://43.156.135.198:8765/spirit_crops/index.html).
