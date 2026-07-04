这里按“不要做分布式，不要只给阶段建议；直接给完整架构”处理。架构目标是：**在单仓库内建立一个完整 GenOps / Agent Orchestrator，把 OpenSpec、生成器、验证器、视觉评审、审美偏好、回归和发布串成可执行流水线。**

---

# MyVillage GenOps 完整架构

## 0. 总原则

```text
不做分布式服务
不做独立后端
不做消息队列
不做多仓库编排
不让 subagent 自由聊天式协作
不让单个 agent 一次性乱改全仓库
```

架构形态：

```text
单仓库
单 manager
多 agent role
artifact-first
patch-gated
validator-gated
visual-gated
human-verdict-gated
```

也就是：

```text
OpenSpec = 能力契约
docs/ai-kb = 技术事实
GenOps = 生成器研发/agent 调度契约
reports = 每次执行证据
```

当前仓库已经有清晰的生成器和验证基础。`generate_all_structures.py` 已经是总入口，负责清理输出、生成 test house、building library、compound、civic、cultivation town、cultivation sect、chinese mansion、fallback，并复制输出。 单体建筑生成器也已经把 pipeline 写成 `Style Profile -> Archetype -> Scale Tier -> Massing Graph -> Facade Grammar -> Build Ops -> Passes -> Quality Check -> NBT + mcfunction resources`。 所以新架构不是重写项目，而是在现有生成器之上增加**调度层、任务契约层、证据层、审美闭环层**。

---

# 1. 顶层架构图

```text
User Goal
  │
  ▼
Manager Agent
  │
  ├─ 读取 genops/pipelines/*.yaml
  ├─ 读取 OpenSpec / docs/ai-kb / README / code
  ├─ 生成 task_graph.json
  ├─ 给每个 subagent 生成 context bundle
  ├─ 收集 patch / artifact / evidence
  ├─ 执行 patch guard
  ├─ 执行 validation gates
  ├─ 执行 visual gates
  ├─ 记录 human verdict
  └─ 输出 final run manifest
        │
        ▼
reports/agent_runs/<run_id>/
```

Subagent 不直接互相调度。所有依赖、输入、输出、文件权限、测试命令都由 Manager 控制。

```text
Subagent 只允许做一件原子任务：
  input: context_bundle + task_contract
  output: patch.diff + task_result.json + evidence
```

---

# 2. 新增目录结构

建议直接新增：

```text
genops/
  README.md

  agents/
    manager.md
    context-cartographer.md
    spec-guardian.md
    pipeline-architect.md
    generator-engineer.md
    java-worldgen-engineer.md
    validator-engineer.md
    visual-reviewer.md
    aesthetic-critic.md
    regression-steward.md
    docs-steward.md
    release-steward.md

  pipelines/
    building-library.full.yaml
    compound-library.full.yaml
    mansion-visual.full.yaml
    sect-worldgen.full.yaml
    visual-acceptance.full.yaml
    release.full.yaml

  schemas/
    pipeline.schema.json
    task.schema.json
    agent_output.schema.json
    run_manifest.schema.json
    visual_review.schema.json
    defect_report.schema.json
    generator_contract.schema.json
    human_verdict.schema.json

  rubrics/
    house.rubric.yaml
    mansion.rubric.yaml
    sect.rubric.yaml
    town.rubric.yaml

  defects/
    defect_dictionary.yaml

  style_bibles/
    medieval_village.yaml
    chinese_courtyard.yaml
    chinese_mansion.yaml
    cultivation_town.yaml
    cultivation_sect.yaml

  golden/
    house.golden.yaml
    mansion.golden.yaml
    sect.golden.yaml

tools/
  genops/
    __init__.py
    run_pipeline.py
    models.py
    pipeline_loader.py
    context_builder.py
    task_graph.py
    agent_executor.py
    patch_guard.py
    gate_runner.py
    artifact_writer.py
    visual_indexer.py
    defect_indexer.py
    git_guard.py
    report_writer.py

reports/
  agent_runs/
    <run_id>/
      run_manifest.json
      task_graph.json
      final_summary.md

      context/
        repo_snapshot.json
        relevant_files.json
        spec_map.md
        code_map.md

      tasks/
        <task_id>/
          task_contract.json
          context_bundle.md
          prompt.md
          patch.diff
          task_result.json
          evidence.json
          stdout.log
          stderr.log

      validation/
        commands.json
        logs/
        summary.json

      visual/
        preview_manifest.json
        contact_sheets/
        reviews/
        human_verdicts/

      artifacts/
        generator_contracts/
        defect_reports/
        style_updates/
```

`reports/agent_runs/` 默认 gitignored。
`genops/` 应该进 git，作为项目自己的生成器操作系统。

---

# 3. Manager Agent 责任

Manager 是唯一调度者。

## 3.1 输入

```json
{
  "goal": "提升 cultivation sect 的山体自然度、远景剪影和登山仪式感",
  "pipeline": "genops/pipelines/sect-worldgen.full.yaml",
  "repo_ref": "main",
  "mode": "patch",
  "human_review_required": true
}
```

## 3.2 Manager 做的事

```text
1. 解析 pipeline yaml
2. 检查仓库状态
3. 构造 task DAG
4. 为每个任务生成 context bundle
5. 调用对应 subagent executor
6. 收集 patch.diff / task_result.json / evidence.json
7. 检查文件权限
8. 检查 patch 是否越界
9. 执行任务级 gates
10. 执行 pipeline 级 gates
11. 生成视觉评审入口
12. 收集 human verdict
13. 输出最终 run manifest
```

## 3.3 Manager 不做的事

```text
不直接写生成器代码
不直接改 OpenSpec
不直接改 changelog
不直接判断最终美术通过
不跳过 validator
不接受没有 evidence 的 patch
```

---

# 4. Subagent Catalog

## 4.1 context-cartographer

读取仓库事实，输出当前能力地图。

输入：

```text
README.md
docs/ai-kb/INDEX.md
docs/ai-kb/*
openspec/specs/*
相关代码文件
```

输出：

```text
context/code_map.md
context/spec_map.md
context/impact_files.json
```

当前仓库的 `docs/ai-kb/` 和 `openspec/specs/` 已经有明确分工：前者是叙述性技术笔记，后者是规范性能力规格。 GenOps 应该建立在这个分工之上，而不是替代它。

---

## 4.2 spec-guardian

负责判断改动是否改变外部契约。

输出：

```json
{
  "changed_capabilities": [],
  "requires_openspec_change": true,
  "requires_docs_ai_kb_change": true,
  "affected_specs": [
    "openspec/specs/sect-worldgen-structure/spec.md",
    "openspec/specs/sect-mountain-derivation/spec.md"
  ]
}
```

OpenSpec 当前已经明确：spec 是当前 agreed baseline，不是永久冻结；当改变边界、格式、生成行为、验证规则时，应该更新相关 spec。

---

## 4.3 pipeline-architect

把目标转成 generator contract。

输出：

```json
{
  "generator": "cultivation_sect",
  "target_contract": {
    "must_preserve": [
      "world-seed determinism",
      "chunk-sliced worldgen",
      "command/worldgen shared realizer",
      "single absolute Y frame",
      "no force-load in worldgen path"
    ],
    "may_change": [
      "terrace edge shape",
      "surface material layering",
      "side landmark frequency",
      "visual rubric",
      "preview targets"
    ],
    "must_not_change": [
      "NeoForge registration model",
      "resource namespace",
      "structure resource directory",
      "version files unless release pipeline"
    ]
  }
}
```

宗门现有设计的关键是：同一套 terrace plan + realizer 同时服务原地命令和区块生成，通过 sink 区分写整世界还是写 chunk 切片，山形由 terrace profile 反推。 这个架构必须被 manager 当作硬约束保护。

---

## 4.4 generator-engineer

只负责 Python 生成器。

允许文件例：

```text
tools/buildgen/*.py
tools/generate_*_library.py
tools/preview_structure.py
tools/generate_*_preview.py
```

禁止：

```text
CHANGELOG.md
gradle.properties
src/main/resources/META-INF/neoforge.mods.toml
build/libs/*
```

---

## 4.5 java-worldgen-engineer

只负责 Java worldgen / runtime generator。

允许：

```text
src/main/java/com/example/myvillage/sect/*.java
src/main/java/com/example/myvillage/town/*.java
```

必须保留：

```text
chunk clip
seed determinism
no worldgen force-load
slotRandom 不依赖当前 chunk
at() 的 Y 不叠加 base.y
```

当前 `SectStructurePiece` 已经通过 `WorldGenSink` 把写入钳到当前 bounding box，并通过 `clip()` 让 realizer 只处理当前 chunk 区域。 这类不变量应由 java-worldgen-engineer 和 validator-engineer 双重保护。

---

## 4.6 validator-engineer

只负责 validator / tests / quality gates。

允许：

```text
tools/validate_*.py
tools/check_*.py
tools/buildgen/quality.py
tools/buildgen/tests/*
```

当前 `quality_check` 已经有硬错误和评分机制，包括入口、窗户、室内功能块、平墙、山墙、屋顶、入口阻塞、禁用方块等检查。 这个 agent 的工作是把新的审美或结构问题继续固化成：

```text
hard error
warning
score
defect label
```

---

## 4.7 visual-reviewer

只负责生成视觉证据，不做最终审美裁决。

允许：

```text
tools/preview_structure.py
tools/render_structure.py
tools/write_visual_acceptance_report.py
tools/generate_*_preview.py
out/preview/
reports/visual_acceptance_report.*
```

当前 Chunky Renderer 离线视觉管线的关键交接结论是：结构数据已经存在，主要问题集中在 camera、scene/dump/snapshot、chunkList 和 PNG 评估逻辑，而不是结构生成本身。 所以 visual-reviewer 的职责不是重写生成器，而是稳定渲染证据。

---

## 4.8 aesthetic-critic

只读视觉证据和 rubric，输出审美缺陷。

输入：

```text
PNG / contact sheet / viewer manifest
genops/rubrics/*.yaml
genops/defects/defect_dictionary.yaml
genops/style_bibles/*.yaml
```

输出：

```json
{
  "candidate": "cultivation_sect_001",
  "scores": {
    "silhouette": 3,
    "terrain_integration": 2,
    "ceremonial_axis": 4,
    "main_hall_dominance": 3,
    "style_fit": 4
  },
  "defects": [
    "terrace_parking_lot",
    "over_symmetric",
    "weak_side_landmark"
  ],
  "fix_rules": [
    "perturb terrace edge by 2-5 blocks outside central stair",
    "add one off-axis landmark on scripture or summit terrace",
    "increase summit hall roofline dominance"
  ]
}
```

这里采用“审美控制系统”思路：你不手搭，而是作为审美导演 / 策展人做候选裁决；AI/脚本负责候选生成、批评、渲染、规则提炼和回归。

---

## 4.9 regression-steward

只负责全量回归。

读取 pipeline gates，执行命令。

当前 validation checklist 已经列出基础命令：`generate_all_structures.py`、`validate_generated_structures.py`、fallback、plaque、compound、civic、town、runtime town、sect generation、style policy、cultivation forms。 这些应归 regression-steward，不应由每个代码 agent 自己决定跑不跑。

---

## 4.10 docs-steward

负责同步：

```text
README.md
docs/ai-kb/*.md
openspec/specs/*/spec.md
genops/style_bibles/*.yaml
genops/rubrics/*.yaml
```

---

## 4.11 release-steward

只在 release pipeline 里出现。

允许改：

```text
gradle.properties
src/main/resources/META-INF/neoforge.mods.toml
README.md jar name
CHANGELOG.md
```

版本同步规则已写在 spec-baseline-governance：改版本时，`gradle.properties`、mods.toml、README jar-name、CHANGELOG 必须同改。

---

# 5. Pipeline DSL

`genops/pipelines/*.yaml` 是 Manager 的唯一执行合同。

## 5.1 通用结构

```yaml
id: sect-worldgen.full
kind: generator-pipeline
version: 1

scope:
  repo: ishikisiko/mc-modtest
  default_branch: main
  mode: patch

goal:
  summary: "提升 cultivation sect worldgen 的山体、台地、远景剪影和视觉验收闭环"
  player_facing_result: "自然生成的宗门更像山中地标，远景更可读，登山路径更有仪式感"

constraints:
  no_distributed_services: true
  artifact_first: true
  no_background_work: true
  require_human_visual_verdict: true

protected_invariants:
  - id: resource-path
    rule: "NBT structures stay under src/main/resources/data/myvillage/structure/"
  - id: seed-determinism
    rule: "same world seed + same site yields same sect"
  - id: chunk-sliced-worldgen
    rule: "worldgen path must not force-load chunks"
  - id: single-y-frame
    rule: "terrace elevation and mountain height remain absolute Y; do not add base.y twice"
  - id: command-worldgen-shared-realizer
    rule: "command and worldgen continue sharing SectGenerator plan/realizer through SectSink"

tasks:
  - id: map-current
    agent: context-cartographer
    outputs:
      - context/code_map.md
      - context/spec_map.md
      - context/impact_files.json

  - id: define-contract
    agent: pipeline-architect
    depends_on: [map-current]
    outputs:
      - artifacts/generator_contracts/sect_worldgen_contract.json

  - id: patch-python-preview
    agent: generator-engineer
    depends_on: [define-contract]
    allowed_files:
      - "tools/buildgen/**"
      - "tools/generate_sect_plan_preview.py"
      - "tools/preview_structure.py"
    forbidden_files:
      - "gradle.properties"
      - "CHANGELOG.md"
      - "src/main/resources/data/myvillage/structure/*.nbt"
    outputs:
      - patch.diff
      - task_result.json

  - id: patch-java-worldgen
    agent: java-worldgen-engineer
    depends_on: [define-contract]
    allowed_files:
      - "src/main/java/com/example/myvillage/sect/**"
    forbidden_files:
      - "gradle.properties"
      - "CHANGELOG.md"
    outputs:
      - patch.diff
      - task_result.json

  - id: patch-validators
    agent: validator-engineer
    depends_on: [patch-python-preview, patch-java-worldgen]
    allowed_files:
      - "tools/validate_*.py"
      - "tools/check_*.py"
      - "tools/buildgen/quality.py"
      - "tools/buildgen/tests/**"
    gates:
      - "python3 tools/validate_sect_generation.py"
      - "python3 tools/validate_compound_library.py --group cultivation_sect --count 2"

  - id: visual-evidence
    agent: visual-reviewer
    depends_on: [patch-validators]
    gates:
      - "python3 tools/generate_sect_plan_preview.py --count 6"
      - "python3 tools/write_visual_acceptance_report.py"
    outputs:
      - "reports/visual_acceptance_report.json"
      - "reports/visual_acceptance_report.md"

  - id: aesthetic-review
    agent: aesthetic-critic
    depends_on: [visual-evidence]
    inputs:
      - "genops/rubrics/sect.rubric.yaml"
      - "genops/defects/defect_dictionary.yaml"
      - "reports/visual_acceptance_report.json"
    outputs:
      - "reports/agent_runs/${run_id}/visual/reviews/sect_review.json"

  - id: docs-sync
    agent: docs-steward
    depends_on: [aesthetic-review]
    allowed_files:
      - "README.md"
      - "docs/ai-kb/**"
      - "openspec/specs/**"
      - "genops/**"

  - id: regression
    agent: regression-steward
    depends_on: [docs-sync]
    gates:
      - "python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure"
      - "python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure"
      - "python3 tools/validate_mod_block_fallbacks.py"
      - "python3 tools/validate_plaque_bindings.py"
      - "python3 tools/validate_compound_library.py --count 6"
      - "python3 tools/validate_compound_library.py --group cultivation_town --count 6"
      - "python3 tools/validate_compound_library.py --group cultivation_sect --count 2"
      - "python3 tools/validate_civic_library.py"
      - "python3 tools/validate_town_generation.py"
      - "python3 tools/validate_runtime_town_plan.py"
      - "python3 tools/validate_sect_generation.py"
      - "python3 tools/check_style_policy.py"
      - "python3 tools/check_cultivation_forms.py"
      - "./gradlew build"

human_review:
  required: true
  artifact_index:
    - "out/preview/index.html"
    - "reports/visual_acceptance_report.md"
    - "reports/agent_runs/${run_id}/visual/reviews/sect_review.json"

final_outputs:
  - "reports/agent_runs/${run_id}/run_manifest.json"
  - "reports/agent_runs/${run_id}/final_summary.md"
```

---

# 6. Task Contract

每个任务落盘为：

```text
reports/agent_runs/<run_id>/tasks/<task_id>/task_contract.json
```

格式：

```json
{
  "task_id": "patch-java-worldgen",
  "agent": "java-worldgen-engineer",
  "goal": "调整 SectGenerator/SectMountain 的地形与台地视觉规则，但保持 chunk-sliced worldgen 不变量",
  "depends_on": ["define-contract"],
  "allowed_files": [
    "src/main/java/com/example/myvillage/sect/**"
  ],
  "forbidden_files": [
    "gradle.properties",
    "CHANGELOG.md",
    "src/main/resources/data/myvillage/structure/*.nbt"
  ],
  "inputs": [
    "context/code_map.md",
    "artifacts/generator_contracts/sect_worldgen_contract.json"
  ],
  "required_outputs": [
    "patch.diff",
    "task_result.json",
    "evidence.json"
  ],
  "must_preserve": [
    "no force-load in worldgen path",
    "slotRandom independent of chunk",
    "WorldGenSink clips writes to current chunk box",
    "at(base, localX, worldY, localZ) does not add base.y to Y"
  ],
  "success_gates": [
    "python3 tools/validate_sect_generation.py",
    "./gradlew build"
  ]
}
```

---

# 7. Agent Output Contract

每个 subagent 必须输出：

```json
{
  "task_id": "patch-java-worldgen",
  "status": "pass",
  "summary": "Adjusted terrace edge generation and mountain material layering while preserving chunk clipping.",
  "changed_files": [
    "src/main/java/com/example/myvillage/sect/SectGenerator.java",
    "src/main/java/com/example/myvillage/sect/SectMountain.java"
  ],
  "declared_invariants_preserved": [
    "chunk-sliced worldgen",
    "single absolute Y frame",
    "seed determinism"
  ],
  "commands_run": [
    "python3 tools/validate_sect_generation.py"
  ],
  "new_defects_addressed": [
    "terrace_parking_lot",
    "weak_silhouette"
  ],
  "risks": [
    {
      "risk": "visual impact requires preview review",
      "owner": "visual-reviewer"
    }
  ]
}
```

没有这个 JSON，Manager 不接受 patch。

---

# 8. Patch Guard

`tools/genops/patch_guard.py` 必须做硬检查。

## 8.1 文件范围检查

```text
git diff --name-only
```

必须是：

```text
changed_files ⊆ allowed_files
changed_files ∩ forbidden_files = ∅
```

## 8.2 生成物检查

禁止手改：

```text
src/main/resources/data/myvillage/structure/*.nbt
reports/*.json
out/preview/*
build/libs/*
```

除非 task 明确声明：

```yaml
generated_outputs_allowed: true
```

## 8.3 版本文件保护

默认禁止修改：

```text
gradle.properties
src/main/resources/META-INF/neoforge.mods.toml
CHANGELOG.md
README jar version references
```

只有 `release-steward` 可以改。

## 8.4 OpenSpec 保护

如果改动涉及：

```text
data format
generation behavior
validation rule
resource path
worldgen behavior
player-facing command
```

但没有 docs/spec 任务，Manager 直接标记：

```json
{
  "status": "blocked",
  "reason": "requires docs/spec sync"
}
```

---

# 9. Gate Runner

`tools/genops/gate_runner.py` 统一执行命令并记录：

```json
{
  "command": "python3 tools/validate_sect_generation.py",
  "cwd": ".",
  "returncode": 0,
  "duration_seconds": 12.8,
  "stdout_log": "reports/agent_runs/.../validation/logs/validate_sect_generation.stdout.log",
  "stderr_log": "reports/agent_runs/.../validation/logs/validate_sect_generation.stderr.log",
  "status": "pass"
}
```

Pipeline 失败规则：

```text
任一 hard gate returncode != 0 → pipeline fail
任一 patch guard fail → pipeline fail
visual human verdict reject → pipeline fail
human verdict pending → pipeline not accepted
```

---

# 10. Visual Review 架构

## 10.1 视觉 artifact

```text
reports/agent_runs/<run_id>/visual/
  preview_manifest.json
  contact_sheets/
    sect_overview.png
    mansion_overview.png
  reviews/
    sect_review.json
    mansion_review.json
  human_verdicts/
    sect_verdict.json
```

当前项目已有视觉检查基础：`preview_structure.py --all`、`generate_town_plan_preview.py`、`generate_sect_plan_preview.py`、`write_visual_acceptance_report.py` 已经出现在 acceptance checklist 中。 视觉报告也明确：它是 inspection checklist，不是 image classifier；真正声称视觉验证前必须打开代表性 PNG 并总结检查内容。

## 10.2 Visual Review JSON

```json
{
  "target": "cultivation_sect",
  "candidate_set": [
    {
      "id": "cultivation_sect_001",
      "renders": {
        "plan": "out/preview/sect_plan_s20260616/plan.png",
        "isometric": "out/preview/cultivation_sect_001/isometric.png",
        "contact_sheet": "reports/agent_runs/<run_id>/visual/contact_sheets/sect_001.png"
      },
      "ai_scores": {
        "far_silhouette": 3,
        "massing_hierarchy": 4,
        "terrain_integration": 2,
        "ceremonial_axis": 4,
        "style_fit": 4
      },
      "defects": [
        "terrace_parking_lot",
        "weak_side_landmark"
      ],
      "fix_rules": [
        "break full-width terrace rectangles outside axis",
        "add off-axis landmark or detached spire variant"
      ],
      "human_verdict": "pending"
    }
  ]
}
```

## 10.3 Human Verdict

```json
{
  "target": "cultivation_sect_001",
  "verdict": "accept",
  "human_note": "远景主殿可读，山门和云海有层次；台地边缘仍可继续自然化，但本轮可接受。",
  "accepted_defects": [
    "minor_terrace_artificiality"
  ],
  "blocking_defects": []
}
```

你的审美输入应该以 verdict 形式沉淀，而不是散落在聊天里。审美文件中已经提出最有效的方式是 pairwise / accepted-rejected 选择，让 AI 把你的偏好变成规则、样板和筛选器。

---

# 11. Defect Dictionary

`genops/defects/defect_dictionary.yaml`

```yaml
version: 1

defects:
  flat_box:
    label: "方盒子"
    severity_default: warning
    applies_to: [house, mansion, sect, town]
    detectable_by: [voxel, visual]
    fix_patterns:
      - "add side volume"
      - "break roofline"
      - "add porch/chimney/tower"

  roof_too_heavy:
    label: "屋顶压头"
    severity_default: warning
    applies_to: [house, mansion]
    detectable_by: [voxel, visual]
    fix_patterns:
      - "reduce eave overhang"
      - "lower roof thickness"
      - "add wall height or supports"

  open_gable:
    label: "山墙未封"
    severity_default: error
    applies_to: [house, mansion]
    detectable_by: [voxel]
    fix_patterns:
      - "seal gable cells"
      - "add roof cleanup pass"

  weak_entrance:
    label: "入口弱"
    severity_default: warning
    applies_to: [house, mansion, sect, town]
    detectable_by: [voxel, visual]
    fix_patterns:
      - "add threshold"
      - "add path connection"
      - "add porch / gate frame"

  terrace_parking_lot:
    label: "台地像停车场"
    severity_default: warning
    applies_to: [sect]
    detectable_by: [voxel, visual]
    fix_patterns:
      - "perturb terrace edges"
      - "vary surface materials"
      - "add retaining asymmetry"

  over_symmetric:
    label: "过度镜像"
    severity_default: warning
    applies_to: [sect, mansion]
    detectable_by: [visual, plan]
    fix_patterns:
      - "add side landmark"
      - "shift one flank building"
      - "vary garden or terrace node"

  weak_silhouette:
    label: "远景剪影弱"
    severity_default: warning
    applies_to: [house, mansion, sect, town]
    detectable_by: [visual, voxel]
    fix_patterns:
      - "increase main mass dominance"
      - "add vertical landmark"
      - "break flat roofline"

  garden_clutter:
    label: "园林杂乱"
    severity_default: warning
    applies_to: [mansion]
    detectable_by: [visual, voxel]
    fix_patterns:
      - "clear visual lane"
      - "cluster plants around focal point"
      - "reduce uniform lily/rock distribution"
```

早期 `test_house_02` 的结论已经证明这种 defect 化是有效的：技术链路通过，但美术问题被明确拆成墙体太平、屋顶压头、山墙未封、窗户缺装饰、地基太重、室内太空、入口不自然。

---

# 12. Rubric 设计

## 12.1 `genops/rubrics/sect.rubric.yaml`

```yaml
id: sect
version: 1

dimensions:
  far_silhouette:
    weight: 1.4
    question: "远景是否一眼读出山中宗门，而不是村庄/平台/石堆？"
    score_1: "平、散、主殿不可读"
    score_3: "有中轴和主殿，但轮廓一般"
    score_5: "山门、半山、主殿、侧峰/塔形成清楚层级"

  terrain_integration:
    weight: 1.3
    question: "山体是否自然承托宗门？"
    score_1: "硬切平台，像停车场"
    score_3: "能承托建筑，但边缘机械"
    score_5: "台地、崖壁、skirt、材质分层自然"

  ceremonial_axis:
    weight: 1.2
    question: "登山路径是否有仪式感？"
    score_1: "只是楼梯连接平台"
    score_3: "有中轴，但停顿节点不足"
    score_5: "山门、阶、云海、藏经/炼丹、主殿逐级展开"

  main_hall_dominance:
    weight: 1.2
    question: "主殿是否压轴？"
    score_1: "主殿和侧建筑无主次"
    score_3: "主殿略强"
    score_5: "主殿位置、高度、背景绝壁都形成终点"

  asymmetry_and_wonder:
    weight: 1.0
    question: "是否有仙气和非对称惊喜？"
    score_1: "完全镜像、机械"
    score_3: "有少量变化"
    score_5: "飞桥/孤峰/侧塔/崖边亭形成记忆点"

  style_fit:
    weight: 1.0
    question: "是否符合 cultivation_sect 风格？"
    score_1: "像普通村庄或西式城堡"
    score_3: "大体是东方宗门"
    score_5: "山门、廊、塔、主殿、云海、材质整体统一"

blocking_defects:
  - floating_base
  - chunk_seam_break
  - missing_main_hall
  - inaccessible_axis
  - worldgen_force_load_regression
```

---

# 13. Style Bible

`genops/style_bibles/cultivation_sect.yaml`

```yaml
id: cultivation_sect
version: 1

identity:
  one_sentence: "山中修真宗门：中轴登山、云海隔层、主殿压轴、侧峰/飞桥制造仙气。"

must_have:
  - "山门是玩家进入宗门的第一识别点"
  - "至少 5 层语义层级：gate / disciple / assembly / scripture / summit"
  - "主殿位于最高或最重要位置"
  - "山体承托台地，不允许建筑漂浮"
  - "世界生成和命令生成共享 plan + realizer"

should_have:
  - "非矩形台地边缘"
  - "一个 off-axis landmark"
  - "飞桥或孤峰变体"
  - "云海或雾面作为空间分隔"
  - "台阶途中有停顿节点"

must_not_have:
  - "全平台矩形停车场感"
  - "西式烟囱/porch 污染 cultivation 风格"
  - "chunk seam 上模板随机不一致"
  - "worldgen 路径 setChunkForced"

materials:
  mountain_low:
    - "grass_block"
    - "dirt"
    - "mossy_cobblestone"
  mountain_high:
    - "stone"
    - "andesite"
    - "stone_bricks"
  architecture:
    wall: "pale wall / stone / wood frame"
    roof: "dark roof"
    accent: "lantern / rail / plaque / cloud motif"

composition:
  axis:
    role: "ceremonial spine"
    allow_breaking: false
  flanks:
    role: "support hierarchy and asymmetry"
    allow_offset: true
  summit:
    role: "terminal focus"
    must_dominate: true
```

---

# 14. Golden Candidate Registry

`genops/golden/sect.golden.yaml`

```yaml
id: sect_golden
version: 1

current_golden:
  candidate_id: "cultivation_sect_001"
  source: "src/main/resources/data/myvillage/structure/cultivation_sect_001.nbt"
  accepted_in_run: "manual-or-agent-run-id"
  human_reason: "主殿和中轴可读，飞桥/云海提供宗门气质，chunk worldgen 不变量通过。"

comparison_rules:
  new_candidate_must_not_be_worse_on:
    - far_silhouette
    - terrain_integration
    - main_hall_dominance
    - chunk_safety
    - style_fit

allowed_regression_if_improved:
  - dimension: "asymmetry_and_wonder"
    may_tradeoff_against: "slight material noise"

rejected_examples:
  - candidate_id: "sect_candidate_bad_parking_lot"
    reason: "台地太直，像多层停车场"
  - candidate_id: "sect_candidate_flat_summit"
    reason: "主殿远景不可读"
```

Golden 可以来自 AI/脚本生成，不需要你手搭。你负责批准。

---

# 15. Runner 设计

## 15.1 命令

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/sect-worldgen.full.yaml \
  --goal "提升宗门山体自然度和远景剪影" \
  --run-id 2026-07-04-sect-worldgen-aesthetic
```

## 15.2 执行流程伪代码

```python
def run_pipeline(pipeline_path: str, goal: str, run_id: str) -> int:
    pipeline = PipelineLoader.load(pipeline_path)
    repo = GitGuard.inspect_repo()

    run_dir = ReportWriter.create_run_dir(run_id)
    task_graph = TaskGraph.from_pipeline(pipeline)

    ReportWriter.write(run_dir / "task_graph.json", task_graph)

    for task in task_graph.topological_order():
        if not task.dependencies_passed():
            task.mark_blocked()
            continue

        context_bundle = ContextBuilder.build(
            task=task,
            pipeline=pipeline,
            repo=repo,
            run_dir=run_dir,
        )

        AgentExecutor.prepare_task_prompt(task, context_bundle, run_dir)

        result = AgentExecutor.execute(task)

        PatchGuard.check(
            task=task,
            patch=result.patch,
            allowed_files=task.allowed_files,
            forbidden_files=task.forbidden_files,
        )

        if result.patch:
            GitGuard.apply_patch(result.patch)

        gate_results = GateRunner.run(task.gates)

        ReportWriter.write_task_result(task, result, gate_results)

        if any(g.failed for g in gate_results):
            task.mark_failed()
            break

    pipeline_gate_results = GateRunner.run(pipeline.final_gates)
    visual_index = VisualIndexer.collect(run_dir)
    final_manifest = ReportWriter.finalize(
        pipeline=pipeline,
        task_graph=task_graph,
        gates=pipeline_gate_results,
        visual=visual_index,
    )

    return 0 if final_manifest.status == "pass" else 1
```

---

# 16. Agent Executor 后端

不做分布式，但 executor 可以有多个后端：

```yaml
executor_backends:
  manual:
    description: "只生成 prompt.md，由人复制给 Codex/Claude/ChatGPT，再把 patch/result 放回 task dir"

  codex_cli:
    description: "本地命令行执行某个 coding agent"

  llm_api:
    description: "将 context bundle 发送给模型 API，要求返回 patch + json"

  no_op:
    description: "只做规划和报告，不改代码"
```

统一输出必须一致：

```text
patch.diff
task_result.json
evidence.json
```

这样不依赖某个具体 AI 工具。

---

# 17. 与现有生成器的映射

## 17.1 Building Library Pipeline

现有 `tools/buildgen/passes.py` 的 pipeline 已经是标准 pass 列表：`massing_pass`、`structure_pass`、`mezzanine_floor_pass`、`floor_slab_pass`、`stair_pass`、`facade_detail_pass`、`connection_carve_pass`、`pagoda_shape_pass`、`roof_pass`、`roof_cleanup_pass`、`material_variation_pass`、`interior_furnishing_pass`、`exterior_decoration_pass`。

GenOps 映射：

```text
massing-agent       -> massing_pass / archetypes / massing graph
facade-agent        -> facade_detail_pass / window / door / wall rhythm
roof-agent          -> roof_pass / roof_cleanup_pass
interior-agent      -> interior_furnishing_pass
decor-agent         -> exterior_decoration_pass
validator-agent     -> quality_check
export-agent        -> buildgen/export.py
```

## 17.2 Compound / Mansion Pipeline

当前 compound 层已经定义 `CompoundVariant`、`ParcelNode`、`BuildingSlot`、`CompoundGraph`。

GenOps 映射：

```text
variant-agent       -> CompoundVariant / MansionVariant
parcel-agent        -> ParcelNode graph
slot-agent          -> BuildingSlot placement
path-agent          -> courtyard / formal / tour / waterside path
garden-agent        -> rockery / pond / pavilion
walkability-agent   -> voxel BFS validator
visual-agent        -> preview + contact sheet
```

## 17.3 Sect Worldgen Pipeline

当前 Java 宗门 worldgen 已经是：

```text
SectStructure.findGenerationPoint
→ SectStructure.generatePieces
→ SectStructurePiece.postProcess
→ SectGenerator.plan
→ SectGenerator.buildMountain
→ SectGenerator.writeMountain
→ SectGenerator.placeCloudSea
→ SectGenerator.realizeCompound
```

`SectStructure` 负责 generation point 和单 piece；siting、spacing、biome gate 来自 datapack。 `SectStructurePiece.postProcess` 每个相交 chunk 重建 plan/mountain，然后通过 `WorldGenSink` clip 当前 chunk。

GenOps 映射：

```text
sect-plan-agent
  SectGenerator.plan / slots / terraces / galleries / feature

sect-mountain-agent
  SectMountain / Python parity / skirt / cliff / cloud sea / spire

sect-realizer-agent
  carveTerraces / stairs / retaining / galleries / templates

chunk-safety-agent
  Clip / WorldGenSink / slotRandom / at() / no force-load

sect-validator-agent
  validate_sect_generation.py / Java-Python parity checks

sect-visual-agent
  generate_sect_plan_preview.py / render_structure.py / visual report
```

---

# 18. 完整 Run Manifest

每次运行最终生成：

```json
{
  "run_id": "2026-07-04-sect-worldgen-aesthetic",
  "pipeline": "sect-worldgen.full",
  "repo_ref": "main",
  "status": "human_review_pending",
  "goal": "提升宗门山体自然度和远景剪影",
  "tasks": [
    {
      "id": "map-current",
      "agent": "context-cartographer",
      "status": "pass"
    },
    {
      "id": "patch-java-worldgen",
      "agent": "java-worldgen-engineer",
      "status": "pass",
      "changed_files": [
        "src/main/java/com/example/myvillage/sect/SectGenerator.java"
      ]
    }
  ],
  "gates": [
    {
      "command": "python3 tools/validate_sect_generation.py",
      "status": "pass"
    },
    {
      "command": "./gradlew build",
      "status": "pass"
    }
  ],
  "visual": {
    "report": "reports/visual_acceptance_report.md",
    "reviews": [
      "reports/agent_runs/2026-07-04-sect-worldgen-aesthetic/visual/reviews/sect_review.json"
    ],
    "human_verdict": "pending"
  },
  "defects_addressed": [
    "terrace_parking_lot",
    "weak_silhouette"
  ],
  "open_risks": [
    "custom myvillage block appearance still requires Minecraft client review when present"
  ]
}
```

Chunky Renderer 当前已知限制必须记录：普通方块可用于离线布局/取景检查，但自定义 `myvillage:` block 外观不能直接用 Chunky 作为最终视觉证据；文档里已经记录 `myvillage:rockery_block` 会渲染成 unknown-block placeholder。

---

# 19. 完整状态机

```text
created
  ↓
context_ready
  ↓
task_graph_ready
  ↓
task_running
  ↓
patch_submitted
  ↓
patch_guard_passed
  ↓
task_gates_passed
  ↓
pipeline_gates_running
  ↓
visual_evidence_ready
  ↓
aesthetic_review_ready
  ↓
human_review_pending
  ↓
accepted
```

失败状态：

```text
blocked_missing_context
blocked_patch_scope
blocked_spec_sync_required
failed_task_gate
failed_pipeline_gate
failed_visual_evidence
rejected_by_human
```

---

# 20. 完整 Definition of Done

一个 GenOps pipeline 通过，必须同时满足：

```text
1. 所有 task 都有 task_contract.json
2. 所有执行过的 task 都有 task_result.json
3. 所有 patch 都通过 PatchGuard
4. 所有 hard gates returncode = 0
5. 生成器输出不是手写 NBT 偷改
6. docs/spec 是否需要同步已由 spec-guardian 判定
7. visual-reviewer 已生成视觉证据
8. aesthetic-critic 已输出 rubric review
9. human verdict 不为 pending
10. final run_manifest.json 存在
```

---

# 21. 这个架构解决的问题

## OpenSpec 不够的问题

OpenSpec 继续负责：

```text
SHALL / SHALL NOT
数据格式
玩家可见行为
世界生成行为
验证规则
```

GenOps 负责：

```text
谁改
改哪里
怎么拆任务
每个 agent 输入什么
输出什么 artifact
哪些 gate 阻断
视觉证据在哪里
你的审美如何沉淀
```

## 生成器越来越复杂的问题

用 pipeline contract 管：

```text
building pipeline
compound pipeline
mansion visual pipeline
sect worldgen pipeline
visual acceptance pipeline
release pipeline
```

## 多 agent 容易乱的问题

用三层硬约束：

```text
allowed_files / forbidden_files
patch_guard
gate_runner
```

## 审美不可控的问题

用四件东西固定：

```text
rubric
defect dictionary
style bible
golden registry
```

## 视觉验证散乱的问题

用统一 artifact：

```text
visual_review.json
contact_sheet.png
human_verdict.json
visual_acceptance_report.md
```

---

# 22. 最终形态

完整架构一句话：

```text
MyVillage GenOps 是一个单仓库、artifact-first 的生成器研发调度系统：
Manager 读取 pipeline，把目标拆成原子 subagent 任务；
每个 subagent 只产出 patch 和结构化证据；
Manager 用文件权限、patch guard、validator、visual report、审美 rubric、human verdict 层层阻断；
OpenSpec 只做能力契约，GenOps 做研发过程契约，reports 做每次运行证据。
```

这就是完整架构。
