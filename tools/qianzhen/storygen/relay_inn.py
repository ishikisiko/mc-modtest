"""阡陌驿：固定剧情站位，先组织生活空间，再从空场地生成围院客栈。"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass

from . import Adjacency, GenerationContext, SemanticPlan, SemanticSpace, SiteBuilder, SiteResult
from ..build import walk_graph

SITE_ID = 'relay_inn'


@dataclass(frozen=True)
class _Layout:
    stable_east: int
    stable_front: int
    reception_front: int
    reception_back: int
    freight_front: int
    return_gallery: bool
    spaces: tuple[SemanticSpace, ...]


def _layout(context):
    rng = context.rng('massing')
    east = rng.choice((13, 15))
    stable_front = rng.choice((16, 18))
    reception_front = rng.choice((6, 8))
    reception_back = rng.choice((15, 16))
    freight_front = rng.choice((31, 32))
    loop = bool(rng.getrandbits(1))
    # 坐标是实际地面/楼面功能区；每间客房独立，楼梯不是覆盖全场的标签。
    specs = [
        ('gate', '商路接驳与门楼通道', (16, 0, 0), (30, 12, 12), ('entry',)),
        ('arrival', '抵达商队的出生驿亭', (3, 0, 2), (15, 11, 13), ('spawn',)),
        ('reception', '程砚接待、告示与西侧候问前廊', (30, 0, 5),
         (45, 12, reception_back + 1), ('guide', 'notice')),
        ('courtyard', '商旅集散院与马饮槽接近道', (16, 0, 13), (29, 4, 29),
         ('courtyard', 'horse_trough')),
        ('stable', '向院开敞的马棚与服务道', (1, 0, stable_front - 1),
         (east + 1, 12, 29), ('stable',)),
        ('freight', '独立货栈及东侧装卸檐廊', (1, 0, freight_front - 1),
         (16, 13, 42), ('freight_store',)),
        ('tea_hall', '后客楼茶食大厅与住宿柜台', (17, 0, 29), (43, 6, 38),
         ('tea_hall', 'inn_counter')),
        ('kitchen', '备餐间与后勤内道', (18, 0, 37), (27, 5, 41), ('kitchen',)),
        ('rear_service', '后勤门外接驳', (17, 0, 41), (43, 4, 44), ('rear_service',)),
        ('underpass', '架空廊下人货穿行道', (30, 0, 25), (43, 5, 30), ('underpass',)),
        ('stairs', '三格宽连续上楼阶与顶端平台', (29, 0, 15), (33, 9, 22),
         ('stair_foot', 'stair_top')),
        ('viewing_deck', '院东架空观景平台与遮阳亭', (30, 6, 22), (44, 15, 26),
         ('viewing_deck',)),
        ('skybridge', '客楼接桥及东侧回游廊' if loop else '中央三格架空接桥',
         (33, 6, 26), (43 if loop else 36, 11, 30), ('skybridge',)),
        ('guest_gallery', '二层四客房共用临院廊', (17, 6, 29), (43, 11, 33),
         ('guest_rooms',)),
    ]
    for x, target in zip((18, 24, 30, 36),
                         ('guest_west', 'guest_west_inner', 'guest_east_inner', 'guest_east')):
        specs.append((target, '独立二层客房、床前活动区', (x, 6, 33), (x + 6, 12, 41), (target,)))
    spaces = tuple(SemanticSpace(*spec) for spec in specs)
    return _Layout(east, stable_front, reception_front, reception_back, freight_front, loop, spaces)


class _Geometry:
    """收集原 DSL；基础铺地显式继承固定站位的原有地面嵌块。"""
    def __init__(self, contract):
        self.palette = contract.blueprint()['palette']
        self.protected = dict(contract.protected)
        self.ops = []
        self.roofs = []
        self.joins = []
        self.signs = []

    def fill(self, low, high, state):
        self.ops.append({'op': 'fill', 'from': list(low), 'to': list(high), 'state': state})

    def set(self, pos, state):
        self.ops.append({'op': 'set', 'pos': list(pos), 'state': state})

    def floor(self, x0, x1, z0, z1, state):
        # 唯一允许混铺的层是 y=0：保留原剧情支撑材质，不跳过墙、空气或楼梯冲突。
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                fixed = self.protected.get((x, 0, z), self.palette.get(state, state))
                if fixed == 'minecraft:air':
                    raise ValueError('剧情地面不能为空气')
                self.set((x, 0, z), fixed)

    def posts(self, corners, bottom, top):
        for x, z in corners:
            self.fill((x, bottom, z), (x, top, z), 'post')

    def doorway(self, low, high):
        self.fill(low, high, 'air')

    def sign(self, pos, facing, messages):
        self.set(pos, f'minecraft:dark_oak_wall_sign[facing={facing},waterlogged=false]')
        self.signs.append({'pos': list(pos), 'messages': messages})

    def roof(self, x0, x1, z0, z1, eave, *, axis='z', gables=()):
        low, high = (z0, z1) if axis == 'z' else (x0, x1)
        for n in range(low, high + 1):
            y = eave + min(n - low, high - n)
            a, b = ((x0, y, n), (x1, y, n)) if axis == 'z' else ((n, y, z0), (n, y, z1))
            if n - low == high - n or abs(2 * n - low - high) == 1:
                state = 'tile'
            else:
                facing = ('south' if n < (low + high) / 2 else 'north') if axis == 'z' else (
                    'east' if n < (low + high) / 2 else 'west')
                state = f'minecraft:deepslate_tile_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]'
            self.fill(a, b, state)
            # 真实山墙从梁高封到瓦底；不以悬空屋面冒充封闭建筑。
            for side, start, end, base in gables:
                if start <= n <= end and y > base:
                    a = (side, base, n) if axis == 'z' else (n, base, side)
                    b = (side, y - 1, n) if axis == 'z' else (n, y - 1, side)
                    self.fill(a, b, 'wall')
        self.roofs.append({'axis': axis, 'min_y': eave,
                           'x_range': [x0, x1], 'z_range': [z0, z1]})


def _build(layout, g):
    # 独立地块和道路接点保持尺寸；中央五格铺装连通门楼与商旅院。
    g.floor(0, 46, 0, 44, 'sand')
    g.floor(2, 44, 2, 43, 'base')
    g.floor(21, 25, 0, 43, 'paving')
    g.floor(14, 42, 12, 29, 'paving')
    for x in (2, 44):
        g.fill((x, 1, 14 if x == 2 else 18), (x, 3, 43), 'wall')
    g.fill((2, 1, 43), (22, 3, 43), 'wall')
    g.fill((26, 1, 43), (44, 3, 43), 'wall')

    # 门楼五格宽、三格净高，出生驿亭独立偏西，不能把 entry 误作 spawn。
    g.floor(17, 29, 3, 10, 'wood')
    for x in (17, 29):
        g.fill((x, 1, 3), (x, 5, 10), 'wall')
    for z in (3, 10):
        g.fill((17, 1, z), (29, 7, z), 'wall')
        g.doorway((21, 1, z), (25, 3, z))
        g.fill((17, 4, z), (29, 4, z), 'beam_x')
    g.posts(((17, 3), (20, 3), (26, 3), (29, 3), (17, 10), (29, 10)), 1, 5)
    g.roof(16, 30, 1, 12, 6, gables=((17, 3, 10, 5), (29, 3, 10, 5)))
    g.sign((23, 4, 2), 'north', ['阡镇', '阡陌驿', '歇马 · 问路 · 留宿', ''])
    g.floor(5, 13, 4, 11, 'wood')
    g.posts(((5, 4), (5, 11), (13, 4), (13, 11)), 1, 4)
    for z in (4, 11):
        g.fill((5, 4, z), (13, 4, z), 'beam_x')
    g.roof(3, 15, 2, 13, 5, axis='x', gables=((4, 5, 13, 4), (11, 5, 13, 4)))
    g.sign((5, 3, 3), 'north', ['', '驿路初抵', '檐下稍歇', ''])
    g.fill((6, 1, 10), (11, 1, 10), 'wood')

    # 接待屋前后进深由 seed 决定，西向三格门直面告示和商旅院。
    front, back = layout.reception_front, layout.reception_back
    g.floor(33, 44, front, back, 'wood')
    for x in (33, 44):
        g.fill((x, 1, front), (x, 4, back), 'wall')
    for z in (front, back):
        g.fill((33, 1, z), (44, 6, z), 'wall')
        g.fill((33, 6, z), (44, 6, z), 'beam_x')
    g.doorway((33, 1, 10), (33, 3, 13))
    g.doorway((36, 1, back), (39, 3, back))
    g.posts(((33, front), (44, front), (33, back), (44, back)), 1, 5)
    g.fill((44, 2, 10), (44, 3, 13), 'glass')
    g.fill((40, 1, front + 1), (42, 1, front + 1), 'wood')
    g.roof(32, 45, front - 1, back + 1, 6,
           gables=((33, front, back, 5), (44, front, back, 5)))
    # 告示板独立落柱，notice 的接近格 (31,1,8) 始终留空。
    g.posts(((32, 8), (32, 14)), 1, 4)
    g.sign((31, 3, 8), 'west', ['', '向导接待', '程砚 · 商路 · 水源', ''])
    g.sign((31, 3, 14), 'west', ['阡镇告示', '入院缓行', '二层客房循阶而上', ''])

    # 西侧马棚以真实柱列开向院落，改变宽度与前沿，不只换装饰。
    east, front = layout.stable_east, layout.stable_front
    g.floor(3, east, front, 28, 'wood')
    g.fill((3, 1, front), (3, 6, 28), 'wall')
    g.fill((3, 1, 28), (east, 4, 28), 'wall')
    g.posts(((east, front), (east, 21), (east, 28)), 1, 5)
    g.fill((east, 5, front), (east, 5, 28), 'beam_z')
    for z in (front + 2, 25):
        g.fill((4, 1, z), (7, 1, z), 'fence')
        g.set((4, 1, z + 1), 'minecraft:hay_block[axis=y]')
    g.roof(1, east + 1, front - 1, 29, 5, axis='x',
           gables=((front, 3, east, 4), (28, 3, east, 4)))
    # 密闭饮槽和其东侧固定接近点分开，避免用水面充当地面。
    g.fill((16, 1, 18), (18, 1, 22), 'base')
    g.fill((17, 1, 19), (17, 1, 21), 'minecraft:water[level=0]')

    front = layout.freight_front
    g.floor(3, 13, front, 41, 'wood')
    for x in (3, 13):
        g.fill((x, 1, front), (x, 7, 41), 'wall')
    for z in (front, 41):
        g.fill((3, 1, z), (13, 5, z), 'wall')
    g.doorway((13, 1, 34), (13, 3, 37))
    g.fill((4, 1, 33), (5, 2, 39), 'minecraft:barrel[facing=east,open=false]')
    g.roof(1, 15, front - 1, 42, 6, axis='x',
           gables=((front, 3, 13, 5), (41, 3, 13, 5)))
    g.sign((14, 4, 36), 'east', ['', '驿货栈', '人马分流 · 勿近火', ''])

    # 客楼下层大厅、独立备餐间与后勤出口；上层为四间真实客房。
    g.floor(17, 43, 30, 41, 'wood')
    for x in (17, 43):
        g.fill((x, 1, 30), (x, 5, 41), 'wall')
    for z in (30, 41):
        g.fill((17, 1, z), (43, 5, z), 'wall')
    for x in (21, 31):
        g.doorway((x, 1, 30), (x + 2, 3, 30))
    g.doorway((23, 1, 41), (25, 3, 41))
    g.fill((18, 1, 37), (26, 4, 37), 'wall')
    g.doorway((21, 1, 37), (23, 3, 37))
    g.fill((26, 1, 38), (26, 4, 40), 'wall')
    g.fill((19, 1, 32), (20, 1, 35), 'wood')
    g.fill((19, 1, 40), (20, 1, 40), 'minecraft:barrel[facing=up,open=false]')
    for x in (29, 37):
        g.fill((x, 1, 35), (x + 1, 1, 36), 'wood')
    for x in (17, 43):
        g.fill((x, 2, 34), (x, 3, 36), 'glass')
    g.fill((17, 6, 29), (43, 6, 41), 'wood')
    for x in (18, 24, 30, 36, 42):
        g.fill((x, 7, 33), (x, 11, 41), 'wall')
    g.fill((18, 7, 33), (42, 11, 33), 'wall')
    g.fill((18, 7, 41), (42, 13, 41), 'wall')
    g.fill((18, 12, 33), (42, 12, 40), 'wood')
    for i, x in enumerate((21, 27, 33, 39), 1):
        for y, half in ((7, 'lower'), (8, 'upper')):
            g.set((x, y, 33), f'minecraft:spruce_door[facing=south,half={half},hinge=left,open=false,powered=false]')
        g.set((x, 9, 33), 'air')
        g.sign((x, 10, 32), 'north', ['', f'客舍 · {i:02}', '临院听铃', ''])
        for z, part in ((39, 'foot'), (40, 'head')):
            g.set((x - 1, 7, z), f'minecraft:white_bed[facing=south,occupied=false,part={part}]')
        g.set((x + 1, 7, 40), 'minecraft:barrel[facing=up,open=false]')
        g.fill((x, 8, 41), (x + 1, 9, 41), 'glass')
        g.set((x - 1, 7, 35), 'wood')
        g.set((x - 1, 8, 35), 'minecraft:lantern[hanging=false,waterlogged=false]')
    g.posts(tuple((x, 29) for x in (17, 24, 30, 36, 43)), 7, 11)
    g.fill((17, 11, 29), (43, 11, 29), 'beam_x')
    for a, b in ((18, 32), (36, 39), (43, 43)):
        g.fill((a, 7, 29), (b, 7, 29), 'fence')
    # 有回游廊时在 x=40..42 留第二个桥口，产生真实可行走环线。
    if not layout.return_gallery:
        g.fill((40, 7, 29), (42, 7, 29), 'fence')
    g.roof(17, 44, 27, 44, 11,
           gables=((18, 33, 41, 12), (42, 33, 41, 12)))
    g.joins.append({'from': [18, 13, 41], 'to': [42, 13, 41]})

    # 六级三格宽楼梯的每一级均有支撑，桥下保留五格净空。
    for z in range(16, 22):
        y = z - 15
        if y > 1:
            g.fill((30, 1, z), (32, y - 1, z), 'base')
        g.fill((30, y, z), (32, y, z),
               'minecraft:sandstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        for x in (29, 33):
            g.set((x, y + 1, z), 'fence')
    g.fill((30, 6, 22), (44, 6, 26), 'wood')
    g.posts(((30, 22), (44, 22), (30, 26), (44, 26)), 1, 6)
    g.fill((33, 7, 22), (44, 7, 22), 'fence')
    for x in (30, 44):
        g.fill((x, 7, 23), (x, 7, 26), 'fence')
    g.fill((30, 7, 26), (32, 7, 26), 'fence')
    g.fill((36, 7, 26), (39 if layout.return_gallery else 43, 7, 26), 'fence')
    g.fill((33, 6, 27), (35, 6, 28), 'wood')
    for x in (32, 36):
        g.fill((x, 7, 27), (x, 7, 28), 'fence')
    if layout.return_gallery:
        g.fill((40, 6, 27), (42, 6, 28), 'wood')
        for x in (39, 43):
            g.fill((x, 7, 27), (x, 7, 28), 'fence')
        g.fill((39, 10, 27), (43, 10, 29), 'wood')
        g.roofs.append({'axis': 'z', 'min_y': 10, 'x_range': [39, 43], 'z_range': [27, 29]})
    g.posts(((38, 22), (44, 22), (38, 26), (44, 26)), 7, 10)
    for z in (22, 26):
        g.fill((38, 10, z), (44, 10, z), 'beam_x')
    g.roof(37, 45, 20, 28, 11, gables=((38, 22, 26, 10), (44, 22, 26, 10)))


def _adjacencies(layout, builder, targets):
    blocks = {pos: cell.state for pos, cell in builder.grid.iter_cells()}
    graph = walk_graph(blocks, builder.contract.size)
    spaces = {space.id: space for space in layout.spaces}
    links = [
        ('gate', 'arrival', 'entry', 'spawn'),
        ('arrival', 'reception', 'spawn', 'guide'),
        ('reception', 'courtyard', 'guide', 'courtyard'),
        ('courtyard', 'stable', 'courtyard', 'stable'),
        ('stable', 'freight', 'stable', 'freight_store'),
        ('courtyard', 'tea_hall', 'courtyard', 'inn_counter'),
        ('tea_hall', 'kitchen', 'inn_counter', 'kitchen'),
        ('kitchen', 'rear_service', 'kitchen', 'rear_service'),
        ('courtyard', 'underpass', 'courtyard', 'underpass'),
        ('courtyard', 'stairs', 'courtyard', 'stair_foot'),
        ('stairs', 'viewing_deck', 'stair_foot', 'viewing_deck'),
        ('viewing_deck', 'skybridge', 'viewing_deck', 'skybridge'),
        ('skybridge', 'guest_gallery', 'skybridge', 'guest_rooms'),
    ]
    links.extend(('guest_gallery', name, 'guest_rooms', name) for name in (
        'guest_west', 'guest_west_inner', 'guest_east_inner', 'guest_east'))
    edges = []
    for source, target, a, b in links:
        start, end = tuple(targets[a]), tuple(targets[b])
        assert spaces[source].contains(start) and spaces[target].contains(end)
        previous, todo = {start: None}, deque([start])
        while todo and end not in previous:
            node = todo.popleft()
            for pos in sorted(graph.get(node, ())):
                if pos not in previous:
                    previous[pos] = node
                    todo.append(pos)
        if end not in previous:
            raise ValueError(f'阡陌驿空间不连通：{source} → {target}')
        path, node = [], end
        while node is not None:
            path.append(node)
            node = previous[node]
        edges.append(Adjacency(source, target, tuple(reversed(path))))
    return tuple(edges)


def generate(context: GenerationContext) -> SiteResult:
    if context.contract.site != SITE_ID:
        raise ValueError('阡陌驿生成器只能接收 relay_inn 契约')
    layout = _layout(context)
    geometry = _Geometry(context.contract)
    _build(layout, geometry)
    builder = SiteBuilder(context.contract)
    builder.apply_ops(geometry.ops, palette=geometry.palette)
    data = context.contract.blueprint()
    plan = SemanticPlan(layout.spaces, _adjacencies(layout, builder, data['walk_targets']))
    presentation = data['presentation']
    presentation['summary'] = '低门楼与出生驿亭、程砚接待、开敞商旅院和独立马棚货栈，围合高客楼。'
    presentation['stage'] = '离线 seeded 剧情约束候选；保持既有部署契约，未接入自然生成。'
    return builder.finish(plan, metadata={
        'description': '阡陌驿：剧情锚点固定，马棚进深、接待体块与二层回游廊由隔离种子确定。',
        'presentation': presentation,
        'signs': geometry.signs,
        'roof_checks': geometry.roofs,
        'wall_roof_joins': geometry.joins,
        'design_notes': [
            '先声明门楼、出生驿亭、程砚接待、马棚货栈、茶食与备餐、楼梯、架空廊和四间客房，再生成几何。',
            f'马棚东沿 x={layout.stable_east}、前沿 z={layout.stable_front}；'
            f'接待 z={layout.reception_front}..{layout.reception_back}；货栈前沿 z={layout.freight_front}。',
            '二层采用东侧回游廊。' if layout.return_gallery else '二层采用中央接桥，东侧院隙敞开。',
            '全部既有站位、NPC 与 approach、目标完整 state 保持；邻接路径来自实际楼板与连续台阶。',
            '离线拓扑不证明原生碰撞、玩家外观、手感、音效、FPS 或多人；这些项目均 not_verified。',
        ],
    })
