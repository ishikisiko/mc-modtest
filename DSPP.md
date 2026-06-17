# 侧墙生成逻辑排查总结

## 代码入口

- `tools/buildgen/facade.py` — facade 入口：`plan_building_facades()`
- `tools/buildgen/ops.py:wall_frame` — 真正的墙体填充
- `tools/buildgen/ops.py:interior_zone` — 室内家具摆放
- `tools/buildgen/passes.py:facade_detail_pass` — 调用链
- `tools/buildgen/archetypes.py:_zone` — 区段定义

## 关键调用顺序 (pipeline)

```
massing → structure (hollow_box + _carve_connection) →
floor_slab → stair → facade_detail (wall_frame + window) →
roof → roof_cleanup → material_variation →
interior_furnishing (interior_zone 把家具塞进 zone)
```

---

## 核心发现

### BUG #1: smithy zone 使用 volume 全量边界而非内缩边界

**位置**: `tools/buildgen/archetypes.py:769-770`

```python
_zone(graph, work, "smithy",
      (work.x0, work.z0, work.x1, work.z1))    # ← 错了：用了 volume 全量边界
```

应该写成:

```python
_zone(graph, work, "smithy",
      (work.x0 + 1, work.z0 + 1,
       work.x1 - 1, work.z1 - 1))    # ← 正确：内缩 1 格
```

对比主线 zone 的正确写法:
```python
ix0, ix1 = main.x0 + 1, main.x1 - 1      # ← 缩了
iz0, iz1 = main.z0 + 1, main.z1 - 1
_zone(graph, main, "forge", (ix0, iz0, ix0 + 2, iz1))
```

**影响链**:
- work 是 open shed，没有 wall
- work 周围最近的 STRUCTURE 块 = 主楼的东侧墙 + 烟囱
- `spots_along_walls()` 把 smithy 区内贴着 STRUCTURE 的格子作为可放置点
- anvil/barrel/furnace 被放到主楼的侧墙平面上（甚至烟囱上）
- 视觉上就是"侧墙出现杂方块"

**实证**:
- `blacksmith_005.nbt` (16, 2, 7) = anvil, 在主楼东墙东侧
- `blacksmith_007.nbt` (16, 2, 7) = barrel, 同上
- `blacksmith_008.nbt` (16, 2, 7) = barrel, 同上

---

### BUG #2: `_carve_connection` 的 zmid 不与 post / window 做冲突检查

**位置**: `tools/buildgen/passes.py:88-93`

```python
# interior connections between attached closed volumes and into side sheds
for vol in graph.volumes():
    if vol.attach_to and vol.type in ("side_wing", "rear_shed", "tower_volume"):
        _carve_connection(ctx, vol)
    elif vol.type == "shed" and vol.meta.get("open") and vol.side in ("west", "east"):
        _carve_connection(ctx, vol)
```

`_carve_connection()` 在主楼的西/东侧墙上凿 1×2 的门洞。门洞的 z 坐标是:

```python
zmid = (max(parent.z0, vol.z0) + min(parent.z1, vol.z1)) // 2
```

**问题**:
- 当 side_wing 不在正中、或 work shed 长度 < main 时，zmid 可能落在主楼侧墙的异常位置
- 可能落在某根竖向木柱中间 → 把木柱从中间挖空
- 可能挖到门/窗的相邻位置 → 破坏构图
- 门洞位置没有和 `plan_wall` 的 post 列表 / window 位置做冲突检查
- open shed 也走 carve → 在工作棚侧墙上凿洞，但工作棚根本没有墙（无意义操作）

---

### BUG #3: 四面墙的 post positions 共享 rng 导致不对齐

**位置**: `tools/buildgen/facade.py:115-127` (`plan_wall`)

```python
pos = a0 + step
posts: List[int] = []
while pos <= a1 - 2:
    p = pos
    if door_along is not None and abs(p - door_along) <= 1:
        p = door_along + 2 if p <= door_along else pos + 1
    if a0 + 1 < p < a1 - 1:
        posts.append(p)
    pos += step + (1 if rng.random() < 0.3 else 0)
```

**问题**:
1. posts 是 per-wall 计算的，但四面墙（front/back/west/east）共享同一个 rng。`rng.random()` 的副作用让四面墙的 posts 互相错位
2. 没有"咬合"检查：同一栋楼的东/西两面墙的 posts 应当对齐，否则两个 corner 之外的 mid-post 在视觉上不对应
3. `while pos <= a1 - 2` 条件：当 `a1 - a0` 很小（例如 3-4 格）时，post 数 = 0 或 1，整面墙没柱会显得空荡
4. door_along 避让逻辑 bug:
   ```python
   p = door_along + 2 if p <= door_along else p + 1
   ```
   当 p > door_along 时 `p = pos + 1`（加 1），但如果 p 本来就已经比 door_along 远 3+，再加 1 没意义，而且可能让 post 跳到 a1 - 1 之外

**实证**: tavern seed=500:
```
west posts=[3, 6]
east posts=[3, 7]   # 同侧两面墙 posts 不一致
```

---

### BUG #4: 小 wall_h 导致 stone_rows 退化

**位置**: `tools/buildgen/ops.py:320-353` (`wall_frame`)

```python
stone_rows = {"stone_lower_wall": 2, "mixed_stone_wood_wall": 1,
              "timber_frame_wall": 1,
              "white_plaster_timber_wall": 1}[wall_type]
stone_rows = min(stone_rows, vol.meta["wall_h"] - 2)
```

当 `wall_h = 1 或 2` 时，`stone_rows` 会变成 0 甚至 -1:
- `wall_h = 2` → `stone_rows = min(1, 0) = 0` → 全 planks，无 stone band
- `wall_h = 1` → `stone_rows = min(1, -1) = -1`

**影响**:
- 小尺寸的体积（某些 side_wing, rear_shed）的侧墙会失去 stone band
- 整面是同一种木板 → 侧墙视觉单调
- 代码注释保证 "every wall type keeps a stone plinth"，但实际行为违反了这个承诺

---

### BUG #5: material_variation_pass 随机 speckle 破坏侧墙

**位置**: `tools/buildgen/passes.py:301-341` (`material_variation_pass`)

```python
# random speckle
for pos, cell in list(grid.iter_cells()):
    if cell.protected or cell.slot not in style.variation_rate:
        continue
    if cell.state != style.primary(cell.slot):
        continue
    if rng.random() < style.variation_rate[cell.slot]:
        grid.replace_state(pos, rng.choice(style.alternates(cell.slot)))
```

**问题**:
- 直接对所有 FACADE 标记的格子做随机替换，包括侧墙
- 当 `variation_rate` 较高、或 alternates 颜色差异大时，侧墙散布"意义不明的杂方块"
- run-breaking 段也按 (axis, outer) 扫描，当 y 切片里非 FACADE 标记打断 run 后 length 重新计数，但侧墙之间没有"必须以 stone band 收尾"等约束

---

### BUG #6: chimney force=True 穿透侧墙

**位置**: `tools/buildgen/archetypes.py:526-540` + `ops.chimney()`

```python
cx = main.x0 - 1 if side == "west" else main.x1 + 1
```

优先级 STRUCTURE + force=True

**影响链**:
- 主楼一侧挂了 open shed 或 side_wing 时:
  - side_wing 占用 (main.x0-1) 那一格 → 烟囱和侧墙重叠
  - open shed 不建墙 → 烟囱的列顶到本该是 side_wing 墙的位置
  - 因为 `force=True`，烟囱凿穿侧墙
- 视觉上: 主楼的西墙在 z=3..4 那一段被烟囱替换成了 cobblestone，和侧墙 wood/plank 风格不统一

**实证**: `medium_house seed=42` 输出: (-1, 2..7, 3..4) 全是 cobblestone，而西墙 z=0..2, 5..6 仍然是 oak_planks → 侧墙材质不连贯

---

## 整面侧墙生成的数据流图

```
┌───────────────────────────────────────────────────────────┐
│                                                           │
│  archetype._zone()                                        │
│       │  ← ① BUG: smithy zone 用 work 全边界             │
│       ▼                                                   │
│  facade.plan_building_facades()                          │
│       │  ← ② BUG: 4 面墙 posts 不对齐                     │
│       │  ← ③ 缺: zmid 没和 post / window 冲突检查         │
│       ▼                                                   │
│  passes.structure_pass._carve_connection()                │
│       │  ← ④ BUG: open shed 也走 carve，凿主楼侧墙        │
│       │  ← ⑤ 副作用: chimney force=True 顶穿侧墙          │
│       ▼                                                   │
│  ops.wall_frame()                                         │
│       │  ← ⑥ BUG: wall_h 太小时 stone_rows 退化           │
│       ▼                                                   │
│  ops.interior_zone.put() → spots_along_walls()           │
│       │  ← ⑦ BUG: smithy zone 包含贴 STRUCTURE 邻居的格子 │
│       │     → anvil/barrel 落到主楼侧墙平面               │
│       ▼                                                   │
│  passes.material_variation_pass                           │
│       │  ← ⑧ BUG: 随机 speckle 让侧墙出现"杂方块"          │
│       ▼                                                   │
│  NBT 输出                                                  │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

---

## 修复优先级建议

| 优先级 | Bug | 描述 | 影响范围 |
|--------|-----|------|----------|
| **必修** | BUG #1, #7 | smithy zone 边界 + anvil 落墙 | 消除 80% "杂方块"投诉 |
| **必修** | BUG #2 | zmid 与 post/window 冲突 | 侧墙门洞位置 |
| **强烈建议** | BUG #4 | stone_rows 退化 | 小尺寸 wing 视觉一致性 |
| **强烈建议** | BUG #6 | chimney 穿透侧墙 | 带 chimney 的建筑 |
| **建议改进** | BUG #3 | posts 对齐 | 双面板美观 |
| **建议改进** | BUG #5 | speckle 噪声 | 侧墙整洁度 |

---

## Change proposal 候选

```
Change name candidate: side-wall-cleanup
Type: fix (在 0.8.1 之后, 走 -fix1)
```
