# OpenSpec 清理记录（2026-10-16）

清理前基线：`1388f192f4dc723ed6393420ca6b0cb9664c5056`。删除的是重复规范和流程载体，运行时代码、数据、生成资源
与实际校验器仍保留。历史原文可用 `git show 1388f192f4dc723ed6393420ca6b0cb9664c5056:openspec/<path>` 检索，
也可查 [Git 基线](https://github.com/ishikisiko/mc-modtest/tree/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec)。不复制出另一套历史目录。

## 内容去向

- 盘点并移除 `openspec/` 全部 608 个跟踪文件（2,986,722 字节）；包含 85 项主规范、11 个活动 change 和 59 个归档 change。
- 主规范及 7 个仅见于 delta 的能力，共 92 个旧 capability 名称，合并到 8 份[主题技术约束](../contracts/README.md)，保留锚点与知识库解释。
- 11 个活动目录的任务都已勾选；移除 proposal/design/tasks/status 元数据，不再另做归档仪式。归档中的未勾选验收完整转入[延期/验收索引](../ai-kb/14_deferred_roadmap.md)，仍为 `not_verified`。
- 删除本机 5 个旧 OpenSpec skill 目录和 `openspec-change.full` pipeline。物品 skill 改为直接实施和必要检查，随仓库跟踪。可选 GenOps 的实际合同、角色、patch guard、manifest 工具保留。
- 修复 README、KB、角色、pipeline、合同及工具引用；历史报告中的旧执行结果保留为历史，不作为新任务要求。

## 合并时处理的冲突

旧档案版本/稳定度常量、资源库“没有 worldgen”、NPC“没有交互”、天命设计等不得
覆盖现有代码。数值由 authored JSON 定义；已实现范围由当前模块 KB 说明。
旧假山汀步/峰亭描述与后续楼梯桥/重檐水亭冲突时，按当前实现约束保留，不恢复旧设计。
旧路线图里 NPC、命簿建宗门和徽派的过期“未实现”判断已删除或改为选题前复查。

## 新默认流程

[WORKFLOW](../WORKFLOW.md) 是唯一流程与版本规则入口：明确改动 → 实施 → 必要检查 → 提交汇报。
检查通过即收尾；失败先分清产品、fixture、环境、工具，再按具体疑点扩大。
普通工作无 proposal、CRAFT run、角色审批或归档；实际存档/协议/权威边界仍须针对性验证。
纯工具/非打包文档不升 mod 版本、不构建 jar、不启动游戏。重型检查确有必要时才走 Modal。

## 工具解除依赖

- release gate 删除 OpenSpec CLI 步骤，保留实际生成/验证/测试/构建/打包步骤。
- 入门校验器不再强制存在某种文档语法；原有代码、资源与知识库检查保留。
- Modal 镜像删除仅供 OpenSpec 使用的 Node/npm/CLI 安装；身份、隔离、固定提交和清理策略不变。
- GenOps 索引只读 run manifest/decision，普通请求不强制 CRAFT；保留显式运行的证据约束。

## 验证边界

相关工具 113 项单元测试通过；最终 GenOps 相关 26 项复验通过（包含在前述集合内）。
pipeline 语义检查、入门静态校验、239 个新增/迁移本地链接及其合同锚点、`git diff --check` 通过。
没有执行 Gradle、游戏、完整 release 或云端镜像启动；不能把离线镜像构造测试称作云端通过。
mod 版本保持 0.52.1。玩家视觉/手感/多人验收不因本次清理改变状态。

## 旧能力到新主题

### [assets](../contracts/assets.md)

`blueprint-v1`, `guideme-cultivation-guide`, `inscription-image-library`, `mod-block-catalog`, `mod-decor-block-family`, `mod-decor-motif`, `mod-item-pipeline`, `modset-profile`, `orientation-adapter`, `plaque-block-family`, `resource-export`, `runtime-mod-fallback`, `spirit-stone-resources`, `structure-json-dsl`.

### [buildings](../contracts/buildings.md)

`building-generation`, `building-orientation-variants`, `chinese-vernacular-roof-vocabulary`, `cultivation-form-vocabulary`, `cultivation-massing-grammar`, `cultivation-variant-differentiation`, `form-registry`, `ganlan-stilted-house`, `multi-story-massing`, `settlement-group`, `style-profile`, `vertical-landmark`.

### [combat-entities](../contracts/combat-entities.md)

`combat-data-validation`, `combat-style-data`, `custom-entity-runtime`, `hostile-beast-runtime`, `humanoid-npc-runtime`, `lingxiao-spear-weapon`, `rideable-flying-sword`.

### [compounds](../contracts/compounds.md)

`chinese-mansion-compound`, `compound-enclosure-planning`, `courtyard-compound`, `courtyard-ground-layer`, `courtyard-path-network`, `courtyard-voxel-walkability`, `garden-rockery`, `huipai-tianjing-mansion`, `mansion-gate-house`, `path-surface-zoning`.

### [cultivation](../contracts/cultivation.md)

`alchemy-foundation`, `alchemy-quality`, `crafting-aptitudes`, `cultivation-advancement`, `cultivation-debug-commands`, `cultivation-definition-registries`, `cultivation-gain`, `cultivation-initiation-ritual`, `cultivation-lifespan-calendar`, `cultivation-meditation`, `cultivation-persistence-lifecycle`, `cultivation-player-profile`, `cultivation-state-synchronization`, `innate-fates`, `spiritual-power-pool`, `sword-qi-technique`.

### [tooling](../contracts/tooling.md)

`chunky-acceptance-automation`, `combat-capture-tooling`, `craft-frontdoor-governance`, `cultivation-core-validation`, `docs-knowledge-base`, `genops`, `interactive-preview`, `modal-test-runner`, `spec-baseline-governance`, `validation`, `visual-reference-structure-pipeline`.

### [towns](../contracts/towns.md)

`civic-archetype-family`, `civic-precinct-framing`, `courtyard-street-town`, `cultivation-street-life`, `district-densification`, `lived-in-tissue`, `street-frontage`, `street-room`, `town-block-variety`, `town-districts`, `town-plan`, `town-realization`.

### [world](../contracts/world.md)

`cultivation-mountain-siting`, `realm-combat-stats`, `region-profile`, `region-runtime-binding`, `region-topology`, `regional-wilderness`, `sect-compound-layout`, `sect-compound-realization`, `sect-mountain-derivation`, `sect-worldgen-structure`.

## 已移除的活动目录

`add-alchemy-foundation`, `add-alchemy-quality`, `add-crafting-aptitudes`, `add-ganlan-stilted-house`, `add-guideme-cultivation-guide-slice`, `add-huipai-reference-slice`, `add-lingxiao-spear`, `add-mod-item-creation-framework`, `add-rideable-flying-sword`, `add-spiritual-power-sword-qi`, `rebuild-pagoda-landmark`.
