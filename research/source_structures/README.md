# Source Structures Research

## Summary

本目录是项目的本地素材候选库，用于人工观察、风格拆解、体量评估和后续手工建模参考。当前项目无发布计划，因此不再按发布可用性把候选拆成 `usable` / `needs_permission` / `reference_only` / `reject` 四类；所有候选统一标记为 `local_research`。

许可证、来源 URL 和署名文件仍保留在每个候选目录中。这些字段是来源事实和后续追溯信息，不再作为当前本地研究阶段的分流标签。

## Candidate Counts

| decision | count |
|---|---:|
| local_research | 30 |

## Local Research Priority

根据结构质量、风格适配、体量代表性和后续手工拆解价值，建议优先人工检查以下素材：

1. **candidate_003 - Hui-style Chinese Village House**：徽派/江南民居方向，与当前中式院落和江南大宅目标最贴近。
2. **candidate_005 - Ganlan Stilted House**：干栏式高脚结构，可补充山地、水岸和区域多样性。
3. **candidate_001 - Large Survival House**：体量适中、结构清楚，适合作为小型住宅模板的拆解样本。
4. **candidate_010 - Traditional Chinese Temple (assorted)**：寺庙组合适合宗门主殿、附属殿和轴线建筑参考。
5. **candidate_006 - Chinese Pagoda Tower**：高塔形态清晰，适合测试垂直地标、楼层比例和屋檐节奏。
6. **candidate_004 - Yaodong Earth Shelter House**：窑洞/土坡嵌入建筑可用于区域风格扩展。
7. **candidate_009 - Leshan-style Buddha Statue Temple**：大型石佛和寺院组合，适合圣地或特殊地标参考。
8. **candidate_015 - Medieval House Variation Collection**：住宅变体多，适合观察屋顶、立面和体量差异。
9. **candidate_008 - Sky Palace**：浮空/秘境方向参考价值高。
10. **candidate_007 - Chinese Castle/Fortress**：城墙、要塞和外城体量可作为宗门防御层参考。

## House Top 3

| id | title | decision | why |
|---|---|---|---|
| candidate_001 | Large Survival House | local_research | 现代木屋设计，比例适合村庄主屋或高级民居参考。 |
| candidate_015 | Medieval House Variation Collection | local_research | 样式多样，可观察中小住宅的立面变化和屋顶组合。 |
| candidate_011 | Cozy Retro-istic Home | local_research | 造型温馨，适合作为小体量住宅气氛参考。 |

## Jiangnan / Chinese Component Top 3

| id | title | decision | why |
|---|---|---|---|
| candidate_003 | Hui-style Chinese Village House | local_research | 徽派民居，马头墙、白墙黑瓦，适合江南村庄和院落模块参考。 |
| candidate_005 | Ganlan Stilted House | local_research | 高脚木楼结构可用于水乡、山间和地形适配参考。 |
| candidate_004 | Yaodong Earth Shelter House | local_research | 黄土高原窑洞建筑，能补充区域建筑差异。 |

## Sect / Cultivation Component Top 3

| id | title | decision | why |
|---|---|---|---|
| candidate_010 | Traditional Chinese Temple (assorted) | local_research | 可拆解为宗门主殿、配殿和轴线建筑参考。 |
| candidate_006 | Chinese Pagoda Tower | local_research | 九层宝塔适合宗门宝塔、城镇地标和高度节奏参考。 |
| candidate_009 | Leshan-style Buddha Statue Temple | local_research | 大型佛像与寺院组合适合圣地、山门或特殊关卡参考。 |

## Metadata Policy

- `usage_decision` 统一为 `local_research`，表示当前仅进入本地研究与人工筛选队列。
- `license.txt`、`source_url.txt`、`attribution.md` 保留原始来源信息，便于以后追溯。
- 本目录不包含原始 `.nbt`、`.schem`、`.litematic`、世界存档或其他结构文件。
- 若未来项目目标改为发布、分发或打包第三方素材，需要重新按当时用途做许可复核。

## Risks and Unknowns

- **结构质量**：未导入前无法确认是否包含命令方块、实体、刷怪笼或异常方块。
- **体量拆分**：大型寺院、城堡和浮空建筑可能需要人工分块，再决定是否转为生成规则。
- **风格转译**：视频和截图类候选更适合作为比例、轮廓、路径和装饰节奏参考，不应机械复刻。
- **后续用途变化**：如果以后产生发布、共享、商用或整合包分发需求，需要重新恢复发布导向的许可分类。
