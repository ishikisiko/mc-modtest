# 延期项与待确认验收

这是历史设计中仍值得检索的索引，不是已承诺的下一批任务。选题前按当前代码
复查；旧提案完整保留在 Git，见[清理记录](../maintenance/openspec-cleanup.md)。

## 保留的设计方向

| 方向 | 历史保留范围与当前边界 |
|---|---|
| 区域后续消费 | 罗盘/下一地区提示、正魔阵营、移动门槛、关隘路径、精确地理边界；`next_rung_regions` 在同阶仍为集合。区域命簿和野外已有消费者，不能称整个区域层未接入。 |
| 地形与城镇自然生成 | 隔边的山脉/河流等地形实现、区域形状及城镇自动选址属于待复查方向；不把命令建城当自然生成。 |
| 建筑扩展 | 北京二进、四进/跨院、旧小院规划器是否迁移、魔修风格和额外装饰模组接入；徽派现状以 [note 21](21_huipai_reference_slice.md) 为准。 |
| 表现与工具 | 预览 greedy meshing、招幌风动、体积云雾、区域规模配置、civic 北围墙、码头/演武场选址。按需求再确认价值，不自动启动。 |
| 天命后续 | [设计目录](../innate-fates-design.json) 的 36 项中已有 16 项有运行时效果，其余 20 项效果未实现，其中 10 个紫/金/红天命已有可选无效果占位，10 项仍仅设计。当前实现见 [note 48](48_innate_fates.md)。 |

NPC、命簿、宗门投影和玩家入宗已存在，现状分别见 [39](39_humanoid_npcs.md)、
[40](40_world_sim.md)、[43](43_player_sect_entry.md)。删除旧“NPC 未实现”“命簿不建宗门”的描述。
通用炼丹已存在，不能把旧天命条目里的“丹药未实现”理解成整个丹药系统缺失。

历史未采用的方案：倾斜/弯曲街网、不对称东西区名册、宗门 jigsaw、区域层主动接管
chunk/biome、资源库层复杂战利品/方块实体作者数据。它们不是待办；重新选择需要明确范围。

## 原任务文件中未签收的验收

以下全部保留 `not_verified`，不因任务文件删除而变为通过。旧汀步/峰顶亭等已被新设计
替代的描述须按当前宅园设计复查，不要求恢复旧方案。其他版本的玩家验收仍以 README
各模块记录为准；本表仅保存被删除任务文件中的未勾选项。

```text
openspec/changes/archive/2026-06-15-add-living-town-generation/tasks.md
- [ ] 6.6 Staged manual acceptance: place a town in-game and inspect 层次 (spine + dominant landmark + gradient), 人烟味 (tissue/smoke/light), 真实市井 (active frontage + furnished street), closed enclosure, and site-fit on a slope

openspec/changes/archive/2026-06-15-add-mod-runtime-fallback/tasks.md
- [ ] 4.6 Stage manual acceptance: run `/myvillage town` and `/myvillage place` once with the decor mods installed and once without, confirm authored mod blocks place real-block / vanilla-fallback respectively with no air holes and no crash, and capture screenshots.
- [ ] 5.3 Mark Phase 5 and Phase 6 `done` in `docs/external_mod_integration_plan.md` status, referencing change `add-mod-runtime-fallback`.

openspec/changes/archive/2026-06-17-densify-cultivation-town/tasks.md
- [ ] 1.4 Verify in-world: building floor is flush with the adjoining street/ground, neither sunk nor floating, no one-block hollow underneath.
- [ ] 4.3 Verify dressed courtyards have an enclosing edge and props, with no prop on a street/alley/gate cell.
- [ ] 5.3 Verify the spine reads as a market street and remains traversable end to end.

openspec/changes/archive/2026-06-19-fix-sect-worldgen-chunk-stall/tasks.md
- [ ] 4.2 Force-generate the same seed + site with `/myvillage sect` and compare to the worldgen-generated sect — identical compound, with no mismatched halves across chunk seams
- [ ] 5.2 Build the jar and confirm it loads; smoke-test `/locate` + approach once more on the release build

openspec/changes/archive/2026-06-22-rebuild-chinese-courtyard/tasks.md
- [ ] 4.6 Staged manual acceptance: place ≥2 of the rebuilt `chinese_courtyard_*` NBTs in-game and inspect (a) the silhouette reads 四合院 not "manor", (b) outer/main yard split with 影壁 blocking the sightline, (c) 垂花门 between yards, (d) 抄手游廊 connect 垂花门 to main hall, (e) main hall 台基 + 月台 + central-bay plaque, (f) the 6 NBTs visibly differ on plan and roofline. Capture screenshots; serve the preview aggregate over HTTP per `AGENTS.md` and report the host URL.

openspec/changes/archive/2026-06-28-rebuild-jiangnan-mansion/tasks.md
- [ ] 10.3 Staged manual acceptance: place ≥2 `chinese_mansion_*` NBTs in-game and confirm (a) the entry sequence reads 江南 (照壁侧立 + 敞厅 + 楼阁 + 山水花园), (b) the player walks end-to-end from gate to 正房 to 楼阁 second story to 假山 mid-mountain to 水池 shore — no 影壁封轴, no 3-block cliffs, no dead ends, (c) 假山 variants render with sub-block detail (皱褶 / 孔洞), (d) 水池 has an irregular shoreline, (e) the 6 NBTs visibly differ on plan / roofline / 花园 scale. Capture screenshots; serve the preview aggregate over HTTP per `AGENTS.md` and report the host URL.
- [ ] 10.4 Staged manual acceptance: place ≥1 regenerated `chinese_courtyard_*` NBT in-game and confirm the 影壁封轴 defect is gone (player walks from gate past the side-standing 照壁 into the main yard without detour-through-column). Capture screenshots.
- [ ] 10.5 Staged manual acceptance: place a `chinese_mansion_*` with `garden_scale=large` and confirm the 假山 is partially climbable (player can stand on `slope` / `base` variant tops via autostep), the 汀步 crosses the pond, and the 亭 on the rockery peak is reachable. Capture screenshots.

openspec/changes/archive/2026-06-28-rebuild-mansion-enclosure-plan/tasks.md
- [ ] 9.2 Build the mod jar; confirm `chinese_mansion_001..006` load and place in-game.
- [ ] 9.3 Staged manual acceptance: place ≥2 `chinese_mansion_*` NBTs in-game and confirm (a) the entrance reads as a real gate (门楼 + 门框 + through-passage, not a hole), (b) every building's door faces its yard (倒座 door toward 前院, 厢 doors inward, 正房 south), (c) the gravel path reaches every door, (d) the player walks end-to-end gate → 正房 → 楼阁 with no dead ends. Capture screenshots; serve the preview aggregate over HTTP per `AGENTS.md` and report the host URL.
- [ ] 9.4 Staged manual acceptance: place the remaining mansion variants and confirm the 6 read as visibly distinct (plan / roofline / 花园 scale) AND all share the corrected entrance + facing + path form. Capture screenshots.
```

相关约束：[world](../contracts/world.md)、[towns](../contracts/towns.md)、[compounds](../contracts/compounds.md)。
