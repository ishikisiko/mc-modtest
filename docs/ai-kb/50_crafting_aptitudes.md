# 0.127.0 开局资质与技艺天赋

炼丹、炼器在测灵及三条天赋确认后，以30加各词条固定贡献生成。`crafting/opening.json` 是开局权重唯一来源；SHA-256按玩家UUID、职业、词条ID确定贡献，选择顺序和世界种子不改变两项结果。炼丹奇才、炼器奇才分别覆盖对应开局值为60，培养值不重抽。土块使均值−5、方差×4，配方品阶与难度仍使用共用正态模型。NPC不抽取这三项玩家开局天赋。详见[当前规则](../features/crafting-opening-talents.md)。

`crafting/quality.json` 格式2只持有正态参数、品阶与阈值；`crafting/rules.json` 继续用于制符。`CraftingGenerator` 等待已确认的 `CraftingOpeningRules.Selection`，`CraftingTalents` 从服务器生效定义读取固定初值与曲线修正。三项保存状态仍由CultivationService唯一替换；玩家死亡、重登、改灵根及重启不重抽已生成值。H页在未确认天赋时显示等待提示。

下文保留历史实现背景，旧品质上限、0～100存储边界和旧协议描述已由当前实现替代。

## Sources and boundaries

- `cultivation/crafting/CraftingAptitudes`: immutable version1 codec, explicit
  uninitialized state and independent alchemy/forging scores. Format bounds0..100.
- `CultivationAttachments.CRAFTING`: serialized `myvillage:crafting_aptitudes`
  with copyOnDeath. `CultivationProfile` stays v4; no manual Clone copy.
- `CultivationService`: sole replacement owner. Once-only initialization on
  awakened legacy login and before the owning snapshot after root assignment.
  Generated data survives root clearing/reawakening and profile resets.
- `CraftingGenerator`: deterministic worldseed/UUID generation with separate
  permanent profession domains; does not consume the root/world RNG stream.
- `data/myvillage/crafting/rules.json` / `CraftingRules`: initial roll range and
  ordered product-quality thresholds, strict schema/bounds validation. State
  quality eligibility rejects uninitialized players even though initialized0
  legitimately maps to lower quality. Rebuild/restart for rule changes.
- `CultivationSnapshotPayload`: existing clientbound snapshot includes crafting
  state and two server-computed caps (0 pending,1..4 lower/middle/upper/perfect).
  This snapshot layout was introduced in protocol21; current builds require protocol25. No new payload or cache. `ClientCultivationState` keeps the same
  three caches and clears the added state with the owning snapshot on disconnect.
- `CraftingPage` through `PanelContext`/`PanelTheme`: read-only H page, appended
  View. It describes alchemy quality as implemented and forging as implemented.
  Page-local diagnostic `CRAFTING_UI` records geometry/visibility for mapped evidence, not a client result authority.
- `cultivation crafting` / `xiulian jiyi`: shared status/set command trees with
  English/pinyin aliases; setters require initialized awakened target and keep
  the other aptitude. Exact commands and initial thresholds are in README.

## Validation

`tools/validate_crafting_aptitudes.py` validates data and ownership/package
boundaries. Tests cover deterministic domain separation, once-only state,
codecs/bounds, all score-to-quality boundaries, snapshot roundtrip, aliases and
read-only client access. Do not weaken the initiation validator's client guard
when adding a legitimate getter; mutation paths must still fail.

Run focused Python checks locally. Full build/release, server, client and
two-client checks default to Modal with no automatic local fallback (see
[note46](46_modal_test_runner.md)). The following foundation procedure is an
advanced remote-workspace reference: after release build and clean stage1,
`python3 tools/crafting_aptitude_evidence.py` owns two capture sessions and tests
mapped awakening, six bilingual H layouts, read-only clicks, admin boundary
fixtures, death, root/profile resets and save/restart. Legacy live migration and
unobserved lifecycle/owner surfaces remain not_verified. No DevBridge.

The 0.51.0 passing evidence is historical. Current protocol21 build and quality
integration pass recorded Modal runs (62 release gates, 42 mapped checks).
H-page layouts were not recaptured for 0.52.0 and remain `not_verified`; owner balance,
appearance and wider multiplayer remain `not_verified`.

See [alchemy-quality](../contracts/cultivation.md#alchemy-quality),
[crafting-aptitudes](../contracts/cultivation.md#crafting-aptitudes),
[cultivation-state-synchronization](../contracts/cultivation.md#cultivation-state-synchronization),
[cultivation-persistence-lifecycle](../contracts/cultivation.md#cultivation-persistence-lifecycle),
[cultivation-debug-commands](../contracts/cultivation.md#cultivation-debug-commands),
[validation](../contracts/tooling.md#validation), [panel](37_cultivation_panel.md)
and [alchemy](49_alchemy.md).
