"""沙埋旧驿：完整驿院语义布局 → 东仓整跨坍塌，不采样零散破洞。"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass

from buildgen.grid import PRIORITY
from ..build import walk_graph
from . import (
    Adjacency, GenerationContext, SemanticPlan, SemanticSpace, SiteBuilder, SiteResult,
)

SITE_ID = 'old_relay'


@dataclass(frozen=True)
class _Collapse:
    name: str
    roof_end: int
    middle_truss_end: int
    rear_truss_end: int
    east_wall_end: int
    dune_front: int
    dune_west: int


# Three structural failures, ordered from the most exposed to the most retained.
# These are whole bay/cross-section decisions, never per-block damage probabilities.
_COLLAPSES = (
    _Collapse('南跨尽失', 19, 35, 33, 22, 28, 35),
    _Collapse('中跨折梁', 22, 37, 34, 24, 30, 35),
    _Collapse('后跨侧倾', 24, 39, 35, 24, 32, 36),
)


def _spaces():
    # Closed bounds follow actual rooms/floors; no catch-all site-sized placeholder.
    return (
        SemanticSpace('gate_court', '正门楼与前院主路', (18, 0, 0), (28, 10, 14),
                      ('entry', 'main_gate', 'front_court')),
        SemanticSpace('front_stair', '三格前石梯与二层入口', (8, 0, 2), (12, 9, 15),
                      ('front_stair_base', 'upper_front_landing')),
        SemanticSpace('packing_room', '西楼一层理货房与藏身隔扇', (5, 0, 14), (15, 6, 24),
                      ('packing_room', 'hideout_door')),
        SemanticSpace('hideout', '隔墙后的周禾藏身室、床与短笺', (5, 0, 24), (15, 6, 31),
                      ('zhou_he_hideout', 'hideout_bed')),
        SemanticSpace('guard_room', '西楼二层护送客舍', (5, 6, 14), (15, 17, 24),
                      ('upper_guard_room',)),
        SemanticSpace('ledger_room', '西楼二层运药账房', (5, 6, 24), (15, 17, 31),
                      ('transport_ledger',)),
        SemanticSpace('west_gallery', '双层西廊：下层通院、上层连双梯', (15, 0, 14), (20, 13, 33),
                      ('west_portico', 'upper_gallery', 'upper_rear_landing')),
        SemanticSpace('array_court', '院中旧回环与后刻直痕', (20, 0, 21), (25, 4, 28),
                      ('altered_array',)),
        SemanticSpace('rear_route', '后廊、三格后梯与后巷回路', (15, 0, 29), (29, 10, 42),
                      ('rear_stair_base', 'rear_portico', 'rear_alley')),
        SemanticSpace('dispatch_hall', '低横脊后厅与运药交接位', (23, 0, 34), (34, 10, 38),
                      ('dispatch_hall',)),
        SemanticSpace('medicine_store', '北仓遗物与仍存瓦的前跨', (29, 0, 15), (39, 13, 24),
                      ('east_ruin', 'medicine_chest')),
        SemanticSpace('broken_bays', '东仓露架、贯通三格侧口与折梁', (28, 0, 24), (41, 14, 31),
                      ('exposed_truss', 'east_breach')),
        SemanticSpace('side_route', '街角接驳、东侧入口与绕行路', (27, 0, 2), (44, 7, 14),
                      ('side_road', 'side_entry')),
        SemanticSpace('sandfall', '东南连续积沙与外侧安全观察位', (33, 0, 28), (44, 8, 41),
                      ('collapse_view',)),
    )


class _Masonry:
    """几何助手只作普通写入；固定剧情格是施工时不可动的接口。"""
    def __init__(self, builder, palette):
        self.grid = builder.grid
        self.palette = palette

    def put(self, pos, state):
        self.grid.set(pos, self.palette.get(state, state), ('STRUCTURE',), PRIORITY['STRUCTURE'])

    def fill(self, low, high, state):
        for x in range(low[0], high[0] + 1):
            for y in range(low[1], high[1] + 1):
                for z in range(low[2], high[2] + 1):
                    self.put((x, y, z), state)

    def shell(self, low, high, height):
        x, z = low
        a, b = high
        for lo, hi in (((x, 1, z), (a, height, z)), ((x, 1, b), (a, height, b)),
                       ((x, 1, z), (x, height, b)), ((a, 1, z), (a, height, b))):
            self.fill(lo, hi, 'wall')
        for px in (x, a):
            for pz in (z, b):
                self.fill((px, 1, pz), (px, height + 1, pz), 'post')
        for pz in (z, b):
            self.fill((x, height + 1, pz), (a, height + 1, pz), 'beam_x')
        for px in (x, a):
            self.fill((px, height + 1, z), (px, height + 1, b), 'beam_z')

    def roof(self, axis, cross, run, base):
        middle = (cross[0] + cross[1]) // 2
        for u in range(cross[0], cross[1] + 1):
            y = base + min(u - cross[0], cross[1] - u)
            state = 'ridge' if u == middle else ('tile_e' if u < middle else 'tile_w')
            if axis == 'z':
                state = 'ridge' if u == middle else ('tile_s' if u < middle else 'tile_n')
            for v in range(run[0], run[1] + 1):
                self.put((u, y, v) if axis == 'x' else (v, y, u), state)
        # The four lifted tips remain attached to the continuous eaves.
        for u in cross:
            for v in run:
                self.put((u, base + 1, v) if axis == 'x' else (v, base + 1, u), 'ridge')

    def gables(self, cross, ends, base, *, axis='x'):
        for u in range(cross[0], cross[1] + 1):
            top = base + min(u - cross[0], cross[1] - u)
            for v in ends:
                low = (u, base, v) if axis == 'x' else (v, base, u)
                high = (u, top, v) if axis == 'x' else (v, top, u)
                self.fill(low, high, 'wall')

    def stair(self, xs, start, direction):
        facing = 'stair_s' if direction == 1 else 'stair_n'
        for step in range(1, 7):
            z = start + direction * step
            if step > 1:
                self.fill((xs[0], 1, z), (xs[1], step - 1, z), 'sandstone')
            self.fill((xs[0], step, z), (xs[1], step, z), facing)
            for x in (xs[0] - 1, xs[1] + 1):
                self.fill((x, 1, z), (x, step, z), 'wall')
                self.put((x, step + 1, z), 'sandstone_wall')

    def doors(self, x, y, z):
        for dx, hinge in ((0, 'left'), (1, 'right')):
            for dy, half in ((0, 'lower'), (1, 'upper')):
                self.put((x + dx, y + dy, z),
                         f'minecraft:spruce_door[facing=south,half={half},hinge={hinge},open=false,powered=false]')


def _complete_relay(m):
    """先建可使用的驿院：西楼两层、东仓完整屋壳、后厅与双梯。"""
    m.fill((0, 0, 0), (44, 0, 44), 'sand')
    m.fill((4, 0, 10), (40, 0, 41), 'sandstone')
    for low, high in (((5, 0, 14), (15, 0, 31)), ((29, 0, 15), (39, 0, 31)),
                      ((23, 0, 34), (34, 0, 38))):
        m.fill(low, high, 'floor')
    for low, high in (((20, 0, 0), (24, 0, 14)), ((9, 0, 2), (44, 0, 4)),
                      ((9, 0, 4), (11, 0, 7)), ((42, 0, 2), (44, 0, 41)),
                      ((16, 0, 11), (44, 0, 13)), ((16, 0, 14), (18, 0, 32)),
                      ((26, 0, 14), (28, 0, 39)), ((16, 0, 30), (29, 0, 32)),
                      ((16, 0, 39), (44, 0, 41)), ((29, 0, 25), (44, 0, 27))):
        m.fill(low, high, 'paving')
    # Courtyard enclosure leaves the named front/side/rear approaches open.
    m.fill((3, 1, 12), (3, 3, 38), 'wall')
    m.fill((4, 1, 42), (15, 2, 42), 'wall')
    m.fill((16, 1, 43), (39, 2, 43), 'wall')
    for a, b in ((4, 17), (27, 40)):
        m.fill((a, 1, 7), (b, 3, 7), 'wall')
    m.fill((9, 1, 7), (11, 3, 7), 'air')
    for x in (18, 25):
        m.fill((x, 1, 6), (x + 1, 4, 10), 'wall')
    for x in (19, 25):
        for z in (6, 10):
            m.fill((x, 1, z), (x, 5, z), 'post')
    m.fill((18, 4, 6), (26, 5, 6), 'beam_x')
    m.fill((18, 4, 10), (26, 5, 10), 'beam_x')
    m.fill((18, 5, 7), (26, 5, 9), 'planks')
    m.gables((6, 10), (18, 26), 6, axis='z')
    m.roof('z', (4, 12), (17, 28), 6)
    # West remnant is intentionally intact: two rooms on each real floor.
    m.shell((5, 14), (15, 31), 10)
    m.fill((5, 6, 14), (19, 6, 31), 'planks')
    m.fill((15, 6, 32), (19, 6, 32), 'planks')
    m.gables((6, 14), (14, 31), 12)
    m.roof('x', (4, 16), (12, 33), 11)
    for y in (1, 7):
        m.fill((15, y, 18), (15, y + 2, 20), 'air')
    m.fill((9, 7, 14), (11, 9, 14), 'air')
    m.fill((15, 7, 27), (15, 9, 29), 'air')
    for y, top, door_x in ((1, 5, 10), (7, 10, 11)):
        m.fill((6, y, 24), (14, top, 24), 'wall')
        m.fill((door_x, y, 24), (door_x + 1, y + 2, 24), 'air')
        m.doors(door_x, y, 24)
    m.fill((13, 1, 25), (13, 3, 27), 'planks')
    for z in (18, 27):
        for y in (3, 8):
            m.fill((5, y, z), (5, y + 1, z + 1), 'air')
            m.fill((5, y, z), (5, y, z + 1),
                   'minecraft:spruce_fence[north=true,south=true,east=false,west=false,waterlogged=false]')
    m.stair((9, 11), 7, 1)
    m.stair((16, 18), 39, -1)
    for z in (15, 22, 29):
        m.fill((19, 1, z), (19, 9, z), 'post')
        m.fill((15, 9, z), (19, 9, z), 'beam_x')
    m.fill((19, 10, 14), (19, 10, 32), 'beam_z')
    m.fill((19, 7, 14), (19, 7, 32), 'fence_ns')
    m.fill((16, 7, 14), (18, 7, 14),
           'minecraft:dark_oak_fence[east=true,north=false,south=false,west=true,waterlogged=false]')
    for x in range(15, 21):
        m.fill((x, 12 - (x - 15) // 2, 13), (x, 12 - (x - 15) // 2, 33), 'tile_w')
    # Complete warehouse; the side portal is part of its original circulation.
    m.shell((29, 15), (39, 31), 5)
    m.gables((29, 39), (15, 31), 7)
    m.roof('x', (27, 41), (13, 33), 6)
    m.fill((29, 1, 21), (29, 3, 23), 'air')
    m.fill((36, 1, 15), (38, 3, 15), 'air')
    m.fill((39, 1, 25), (39, 3, 27), 'air')
    # Low crosswise dispatch hall: independent volume, through passage to back alley.
    m.shell((23, 34), (34, 38), 4)
    for z in (34, 38):
        m.fill((27, 1, z), (29, 3, z), 'air')
    m.gables((34, 38), (23, 34), 6, axis='z')
    m.roof('z', (32, 40), (21, 36), 6)
    for x in (20, 25):
        for z in (29, 33):
            m.fill((x, 1, z), (x, 4, z), 'post')
    m.fill((19, 4, 33), (26, 4, 33), 'beam_x')
    m.fill((20, 5, 29), (26, 5, 33), 'planks')


def _collapse_warehouse(m, scheme):
    # Remove one connected tail of the roof, then reconstruct only the designed
    # surviving frames. Nothing in the west floor/stairs or target sockets is damaged.
    m.fill((27, 6, scheme.roof_end + 1), (41, 14, 33), 'air')
    # The lower hall eave intersects this bounding volume. Re-emit its complete,
    # unchanged shell so warehouse damage cannot remove the dispatch passage roof.
    m.roof('z', (32, 40), (21, 36), 6)
    m.fill((39, 1, scheme.east_wall_end + 1), (39, 5, 31), 'air')
    m.fill((scheme.rear_truss_end + 1, 1, 31), (39, 5, 31), 'air')
    # Full west wall plate, exposed full front frame and shorter rear frames.
    m.fill((29, 6, 15), (29, 6, 31), 'beam_z')
    m.fill((34, 13, 15), (34, 13, 26), 'beam_z')
    for z, end in ((21, 39), (26, scheme.middle_truss_end), (31, scheme.rear_truss_end)):
        if z <= scheme.roof_end:
            continue
        m.fill((29, 6, z), (end, 6, z), 'beam_x')
        for x in range(28, end + 1):
            y = 6 + min(x - 28, 40 - x)
            m.fill((x, y, z), (x, y + 1, z), 'beam_x')
        m.fill((29, 1, z), (29, 6, z), 'post')
    # A kingpost is carried by a longitudinal beam, whose ground post is outside
    # the three-wide z=25..27 passage; no floating frame over that route.
    m.fill((34, 1, 24), (34, 6, 24), 'post')
    m.fill((34, 6, 24), (34, 6, 26), 'beam_z')
    m.fill((34, 7, 26), (34, 12, 26), 'post')
    if scheme.middle_truss_end == 39:
        m.fill((39, 1, 24), (39, 6, 24), 'post')
        m.fill((39, 6, 24), (39, 6, 26), 'beam_z')
    # A supported, stepped continuous dune catches fallen beams; east outer road
    # x=42..44 and the warehouse side breach z=25..27 remain completely clear.
    for layer in range(6):
        m.fill((scheme.dune_west + min(layer, 41 - scheme.dune_west),
                1 + layer, scheme.dune_front + min(layer, 3)),
               (41, 1 + layer, 38 - min(layer, 3)), 'sandstone' if layer == 0 else 'sand')
    m.fill((scheme.dune_west, 2, scheme.dune_front + 1),
           (scheme.dune_west + 3, 2, scheme.dune_front + 1), 'beam_x')
    m.fill((39, 4, scheme.dune_front + 3), (41, 4, scheme.dune_front + 3), 'beam_x')
    m.fill((37, 3, scheme.dune_front + 2), (38, 3, scheme.dune_front + 2), 'tile_s')
    # This low drift lives against the retained east wall, away from the medicine box.
    m.fill((36, 1, 21), (38, 1, 22), 'sand')


def _story_interior(m):
    # Furniture, signs and fixed interaction sockets are not randomized story facts.
    for x, y, z in ((7, 1, 28), (7, 7, 17)):
        for dz, part in ((0, 'foot'), (1, 'head')):
            m.put((x, y, z + dz), f'minecraft:brown_bed[facing=south,part={part},occupied=false]')
    for low, high in (((6, 1, 16), (7, 1, 18)), ((6, 1, 21), (8, 1, 22)),
                      ((6, 7, 29), (7, 7, 30)), ((30, 1, 16), (30, 2, 19)),
                      ((24, 1, 35), (25, 1, 37))):
        m.fill(low, high, 'planks')
    for pos in ((6, 1, 30), (10, 1, 30), (9, 7, 30), (31, 2, 16), (24, 2, 36)):
        m.put(pos, 'barrel')
    m.put((12, 7, 30), 'minecraft:chest[facing=north,type=single,waterlogged=false]')
    m.put((33, 1, 36), 'minecraft:chest[facing=west,type=single,waterlogged=false]')
    # Every wall sign gets a real backing, including the packing-room crossbeam.
    for pos, backing in (((22, 4, 5), (22, 4, 6)), ((7, 3, 23), (7, 3, 24)),
                         ((12, 2, 30), (12, 2, 31)), ((8, 9, 30), (8, 9, 31)),
                         ((32, 2, 16), (32, 2, 15)), ((32, 2, 37), (32, 2, 38)),
                         ((24, 2, 27), (24, 2, 28))):
        m.put(backing, 'wall')
        m.put(pos, 'sign_s' if pos == (32, 2, 16) else 'sign_n')
    for pos in ((10, 2, 30), (9, 8, 30), (31, 3, 16)):
        m.put(pos, 'minecraft:lantern[hanging=false,waterlogged=false]')
    m.put((23, 4, 31), 'minecraft:lantern[hanging=true,waterlogged=false]')
    # The cut line intersects the old loop; the protected target keeps full state.
    m.fill((20, 0, 21), (24, 0, 27), 'smooth_sandstone')
    for x in (20, 24):
        m.fill((x, 0, 22), (x, 0, 26), 'carved')
    for z in (22, 26):
        m.fill((20, 0, z), (24, 0, z), 'carved')
    m.fill((22, 0, 21), (22, 0, 27), 'jade')
    m.fill((21, 0, 24), (24, 0, 24), 'copper')
    m.put((22, 0, 24), 'jade')
    m.fill((24, 1, 28), (24, 3, 28), 'carved')


def _path(graph, start, end):
    start, end = tuple(start), tuple(end)
    parent, todo = {start: None}, deque([start])
    while todo:
        current = todo.popleft()
        if current == end:
            path = []
            while current is not None:
                path.append(current)
                current = parent[current]
            return tuple(reversed(path))
        for following in graph.get(current, ()):
            if following not in parent:
                parent[following] = current
                todo.append(following)
    raise ValueError(f'old_relay semantic route is unreachable: {start} -> {end}')


def _adjacencies(builder, targets):
    blocks = {p: c.state for p, c in builder.grid.iter_cells()}
    graph = walk_graph(blocks, builder.contract.size)
    # A connected semantic graph with explicit route obligations through both floors,
    # the hideout door, the array, medicine evidence and the independent side route.
    links = (
        ('gate_court', 'west_gallery', 'front_court', 'west_portico'),
        ('gate_court', 'front_stair', 'front_court', 'front_stair_base'),
        ('front_stair', 'guard_room', 'front_stair_base', 'upper_guard_room'),
        ('guard_room', 'ledger_room', 'upper_guard_room', 'transport_ledger'),
        ('ledger_room', 'west_gallery', 'transport_ledger', 'upper_gallery'),
        ('west_gallery', 'rear_route', 'upper_rear_landing', 'rear_stair_base'),
        ('west_gallery', 'packing_room', 'west_portico', 'packing_room'),
        ('packing_room', 'hideout', 'hideout_door', 'zhou_he_hideout'),
        ('west_gallery', 'array_court', 'west_portico', 'altered_array'),
        ('array_court', 'rear_route', 'altered_array', 'rear_portico'),
        ('rear_route', 'dispatch_hall', 'rear_alley', 'dispatch_hall'),
        ('gate_court', 'side_route', 'front_court', 'side_entry'),
        ('side_route', 'medicine_store', 'side_entry', 'medicine_chest'),
        ('array_court', 'medicine_store', 'altered_array', 'east_ruin'),
        ('medicine_store', 'broken_bays', 'east_ruin', 'exposed_truss'),
        ('broken_bays', 'sandfall', 'east_breach', 'collapse_view'),
        ('sandfall', 'rear_route', 'collapse_view', 'rear_alley'),
    )
    return tuple(Adjacency(a, b, _path(graph, targets[s], targets[t])) for a, b, s, t in links)


def generate(context: GenerationContext) -> SiteResult:
    contract = context.contract
    if contract.site != SITE_ID:
        raise ValueError('old_relay requires its own site contract')
    data = contract.blueprint()
    spaces = _spaces()
    builder = SiteBuilder(contract)
    masonry = _Masonry(builder, data['palette'])
    _complete_relay(masonry)
    # Choose only after the complete functional structure has been constructed.
    scheme = context.rng('collapse').choice(_COLLAPSES)
    _collapse_warehouse(masonry, scheme)
    _story_interior(masonry)
    plan = SemanticPlan(spaces, _adjacencies(builder, data['walk_targets']))
    roofs = [dict(spec) for spec in data['roof_checks']]
    roofs[-1] = {**roofs[-1], 'z_range': [13, scheme.roof_end]}
    presentation = {**data['presentation'], 'stage': '离线 seeded 剧情约束候选；原生碰撞、外观与玩家体验 not_verified。',
                    'summary': f'西楼藏身与楼上账房保持双梯回环；东仓采用「{scheme.name}」整跨坍塌。'}
    return builder.finish(plan, metadata={
        'description': f'沙埋旧驿：西楼与后厅保留，东仓{scheme.name}；固定调查接口与主侧路线。',
        'presentation': presentation, 'roof_checks': roofs,
        'design_notes': [
            '先生成完整两层西楼、整座东仓、低后厅及双梯，再选择整跨失稳；不随机打孔。',
            f'东仓残瓦连续保留至 z={scheme.roof_end}；中后屋架、东墙与连续积沙随坍塌方案一起变化。',
            '藏身隔墙、楼板、楼梯、运药遗物及地面改刻阵纹具有独立真实空间；主侧路接驳固定。',
            '四片完整屋壳仍检查连续截面；露天残跨不声明完整屋顶。',
            '全部剧情身份、精确状态、站位和视线由共享契约保护；不接自然 worldgen。',
            '原生碰撞、玩家外观、音效、FPS、多人和存档运行时 not_verified。',
        ],
    })
