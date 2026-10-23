"""问灵小院：固定剧情站位，先规划仪式/院廊体量，再从空地生成。

测灵与传承各有自己的屋顶和碑前空间；它们仍是可选入门设施。
种子改变八角亭边界、横堂开间、折廊进深、静修廊架和观星阁，
不移动任何既有站位，不添加任务条件或自然生成入口。
"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass

from ..build import walk_graph
from . import Adjacency, GenerationContext, SemanticPlan, SemanticSpace, SiteBuilder, SiteResult

SITE_ID = 'initiation_court'


@dataclass(frozen=True)
class _Layout:
    testing_west: int
    testing_east: int
    testing_front: int
    testing_back: int
    hall_west: int
    hall_east: int
    cloister_east: int
    gallery_front: int
    quiet_front: int
    quiet_back: int
    pavilion_east: int
    pavilion_front: int

    @classmethod
    def sample(cls, context):
        rng = context.rng('massing')
        return cls(rng.choice((3, 4)), rng.choice((15, 16)),
                   rng.choice((14, 15)), rng.choice((25, 26)),
                   rng.choice((4, 5)), rng.choice((20, 21)),
                   rng.choice((5, 6)), rng.choice((26, 27)),
                   rng.choice((12, 13)), rng.choice((18, 19)),
                   rng.choice((29, 30)), rng.choice((31, 32)))

    def spaces(self):
        return (
            SemanticSpace('forecourt', '五格门亭与映水前庭；可选入门入口',
                          (2, 0, 0), (36, 10, 12), ('entry', 'gate', 'forecourt')),
            SemanticSpace('main_court', '两处仪式之间保持露天的分流主院',
                          (16, 0, 11), (25, 7, 28), ('main_court',)),
            SemanticSpace('testing', '独立八角测灵亭、真实测灵碑与东侧接近口',
                          (self.testing_west, 2, self.testing_front),
                          (self.testing_east, 14, self.testing_back),
                          ('testing', 'testing_east_approach')),
            SemanticSpace('west_cloister', '柱撑覆顶西廊，连接测灵亭与后部折廊',
                          (2, 2, 12), (self.cloister_east, 7, 30), ('west_cloister',)),
            SemanticSpace('return_gallery', '仪式空间之间的覆顶折廊及经册房前接驳',
                          (self.cloister_east, 2, self.gallery_front),
                          (28, 8, 30), ('return_gallery',)),
            SemanticSpace('inheritance', '独立横向传承堂、低台檐廊与真实传功碑',
                          (self.hall_west, 2, 28), (self.hall_east, 15, 40),
                          ('inheritance_veranda', 'inheritance')),
            SemanticSpace('scripture', '观星台下的经册房、读经桌与空储物容器',
                          (25, 2, 29), (35, 8, 39), ('scripture_room', 'scripture_storage')),
            SemanticSpace('quiet_terrace', '与测灵分开的东侧静修廊架及坐席',
                          (25, 2, self.quiet_front), (35, 7, self.quiet_back), ('quiet_seat',)),
            SemanticSpace('observation_stair', '三格净宽六级登台阶与上部落脚平台',
                          (30, 2, 19), (34, 10, 28), ('stair_bottom', 'stair_landing')),
            SemanticSpace('observation_pavilion', '偏置柱撑观星阁，保留露台东侧开敞',
                          (25, 8, self.pavilion_front), (self.pavilion_east, 18, 39),
                          ('observation_pavilion',)),
            SemanticSpace('star_deck', '经册房顶真实可达的露天静修台',
                          (25, 8, 27), (35, 10, 39), ('star_deck',)),
        )


class _Masonry:
    """小院专用几何笔；所有写入交由 SiteBuilder 的整批保护检查。"""
    def __init__(self, palette):
        self.palette = palette
        self.ops = []
        self.roofs = []
        self.joins = []

    def set(self, pos, state):
        self.ops.append({'op': 'set', 'pos': list(pos), 'state': self.palette.get(state, state)})

    def fill(self, low, high, state):
        self.ops.append({'op': 'fill', 'from': list(low), 'to': list(high),
                         'state': self.palette.get(state, state)})

    def post(self, x, z, bottom, top):
        self.fill((x, bottom, z), (x, top, z), 'post')

    def frame(self, west, east, front, back, bottom, beam):
        for x in (west, east):
            for z in (front, back):
                self.post(x, z, bottom, beam)
            self.fill((x, beam, front), (x, beam, back), 'beam')
        for z in (front, back):
            self.fill((west, beam, z), (east, beam, z), 'beam')

    def hip_roof(self, west, east, front, back, y, *, chamfer=0):
        # 实心薄层逐层收分；底层完整遮雨，八角亭裁角而不造随机破洞。
        for inset in range((min(east - west, back - front) // 2) + 1):
            w, e, f, b = west + inset, east - inset, front + inset, back - inset
            cut = min(chamfer, (min(e - w, b - f) // 2))
            for z in range(f, b + 1):
                corner = max(0, cut - min(z - f, b - z))
                self.fill((w + corner, y + inset, z), (e - corner, y + inset, z), 'tile')
        self.roofs.append({'axis': 'x', 'min_y': y,
                           'x_range': [west + chamfer, east - chamfer],
                           'z_range': [front, back]})

    def lantern(self, x, y, z):
        self.set((x, y + 1, z), 'beam')
        self.set((x, y, z), 'minecraft:lantern[hanging=true,waterlogged=false]')


def _stairs(facing):
    return f'minecraft:sandstone_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]'


def _grounds(m, p, size):
    for z in range(size[2]):
        for x in range(size[0]):
            state = 'foundation' if 2 <= x <= 36 and 2 <= z <= 40 else 'sand'
            if 4 <= x <= 34 and 3 <= z <= 10:
                state = 'court'
            if 17 <= x <= 21 and z <= 12:
                state = 'paving'
            m.set((x, 0, z), state)
    for z in range(11, 41):
        for x in range(2, 37):
            m.set((x, 1, z), _stairs('south') if z == 11 and 17 <= x <= 21 else 'foundation')
            if z == 11 and 17 <= x <= 21:
                continue
            state = 'court'
            if (17 <= x <= 21 or x <= p.cloister_east
                    or p.gallery_front <= z <= 30 or 25 <= x <= 34):
                state = 'paving'
            if p.testing_west <= x <= p.testing_east and p.testing_front <= z <= p.testing_back:
                state = 'sandstone'
            if z == 12 and 17 <= x <= 21:
                state = _stairs('south')
            m.set((x, 2, z), state)
    for x in (1, 37):
        m.fill((x, 1, 4), (x, 3, 41), 'wall')
        m.fill((x, 4, 4), (x, 4, 41), 'cap')
    m.fill((1, 1, 41), (37, 3, 41), 'wall')
    m.fill((1, 4, 41), (37, 4, 41), 'cap')
    for west, east in ((1, 15), (23, 37)):
        m.fill((west, 1, 4), (east, 3, 4), 'wall')
        m.fill((west, 4, 4), (east, 4, 4), 'cap')
    m.frame(15, 23, 2, 7, 1, 5)
    m.hip_roof(14, 24, 1, 8, 6)
    m.set((19, 5, 1), 'minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]')
    m.lantern(16, 4, 4)
    m.lantern(22, 4, 4)
    # 前庭映水盆的底/岸封闭，不接主轴。
    for z in range(6, 10):
        for x in range(6, 13):
            m.set((x, 1, z), 'minecraft:water[level=0]' if 7 <= x <= 11 and 7 <= z <= 8 else 'sandstone')


def _rituals_and_cloisters(m, p):
    w, e, f, b = p.testing_west, p.testing_east, p.testing_front, p.testing_back
    # 东侧 x13 接近口与碑前 x7..11 保持开敞，柱列只占亭边。
    for x, z in ((w + 2, f), (e - 2, f), (w, f + 2), (e, f + 2),
                 (w, b - 2), (e, b - 2), (w + 2, b), (e - 2, b)):
        m.post(x, z, 3, 6)
    m.fill((w + 2, 6, f), (e - 2, 6, f), 'beam')
    m.fill((w + 2, 6, b), (e - 2, 6, b), 'beam')
    for x in (w, e):
        m.fill((x, 6, f + 2), (x, 6, b - 2), 'beam')
    m.hip_roof(w, e, f, b, 7, chamfer=2)
    m.set((9, 3, 22), 'minecraft:chiseled_sandstone')
    m.set((9, 4, 22), 'myvillage:spirit_testing_stele')
    m.fill((7, 3, 24), (12, 3, 24), 'wall')
    m.lantern(8, 5, f + 1)

    # 覆廊宽度、折廊进深随 seed 变化；中院始终露天。
    m.fill((2, 7, 12), (p.cloister_east, 7, 30), 'tile')
    for z in (12, 18, 24, 30):
        m.post(2, z, 3, 6)
    for x in (2, p.cloister_east):
        m.fill((x, 6, 12), (x, 6, 30), 'beam')
    m.fill((p.cloister_east, 7, p.gallery_front), (24, 7, 30), 'tile')
    for z in (p.gallery_front, 30):
        m.fill((p.cloister_east, 6, z), (24, 6, z), 'beam')
    for x in (p.cloister_east, 18, 24):
        m.post(x, p.gallery_front, 3, 6)
    m.roofs.extend(({'axis': 'x', 'min_y': 7, 'x_range': [2, p.cloister_east], 'z_range': [12, 30]},
                    {'axis': 'z', 'min_y': 7, 'x_range': [p.cloister_east, 24],
                     'z_range': [p.gallery_front, 30]}))

    w, e = p.hall_west, p.hall_east
    m.fill((w, 3, 29), (e, 3, 39), 'sandstone')
    m.fill((10, 3, 28), (16, 3, 28), _stairs('south'))
    for x in (w, e):
        m.fill((x, 4, 31), (x, 7, 39), 'wall')
        for z in (31, 39):
            m.post(x, z, 4, 7)
    m.fill((w, 4, 39), (e, 7, 39), 'wall')
    for lo, hi in ((w, 9), (17, e)):
        m.fill((lo, 4, 31), (hi, 7, 31), 'wall')
    m.fill((10, 7, 31), (16, 7, 31), 'beam')
    m.hip_roof(w - 1, e + 1, 29, 41, 8)
    # 检查真实墙顶接触；前檐廊开口不冒充连续墙。
    for z in (31, 39):
        m.joins.append({'from': [w, 8, z], 'to': [e, 8, z]})
    m.fill((9, 4, 38), (16, 4, 38), 'sandstone')
    m.set((12, 4, 37), 'minecraft:chiseled_sandstone')
    m.set((12, 5, 37), 'myvillage:technique_inheritance_stele')
    for x in (w, e):
        m.fill((x, 5, 33), (x, 6, 34),
               'minecraft:glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]')
    m.set((12, 7, 30), 'minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]')
    for x in (6, 19):
        m.set((x, 4, 36), 'minecraft:lectern[facing=north,has_book=false,powered=false]')
        m.lantern(x, 6, 34)


def _scripture_and_retreat(m, p):
    for x in (25, 35):
        m.fill((x, 3, 29), (x, 7, 39), 'wall')
    for z in (29, 39):
        # 经册房前口为三格净宽，侧门成对且保留真实门洞。
        for x in range(25, 36):
            if z == 29 and 26 <= x <= 28:
                m.fill((x, 6, z), (x, 7, z), 'beam')
            else:
                m.fill((x, 3, z), (x, 7, z), 'wall')
    for x in (25, 35):
        for z in (29, 39):
            m.post(x, z, 3, 7)
    # 侧门的墙体只在这两列留空；顶梁保持连续。
    for z, hinge in ((33, 'left'), (34, 'right')):
        m.set((35, 5, z), 'minecraft:air')
        for y, half in ((3, 'lower'), (4, 'upper')):
            m.set((35, y, z), f'minecraft:dark_oak_door[facing=east,half={half},hinge={hinge},open=false,powered=false]')
    for z in (31, 36):
        m.fill((25, 4, z), (25, 5, z + 1),
               'minecraft:glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]')
    for z in (31, 33, 35, 37):
        m.fill((26, 3, z), (26, 5, z), 'minecraft:bookshelf')
    m.fill((28, 3, 38), (31, 4, 38), 'minecraft:bookshelf')
    m.set((28, 3, 34), 'minecraft:lectern[facing=east,has_book=false,powered=false]')
    m.set((34, 3, 37), 'minecraft:chest[facing=west,type=single,waterlogged=false]')
    m.set((34, 3, 31), 'minecraft:barrel[facing=up,open=false]')
    m.lantern(29, 6, 31)
    m.lantern(32, 6, 36)
    m.fill((25, 8, 27), (35, 8, 39), 'deck')
    m.frame(25, 35, 27, 39, 3, 7)
    m.roofs.append({'axis': 'z', 'min_y': 8, 'x_range': [25, 35], 'z_range': [29, 39]})
    for z in (29, 39):
        m.joins.append({'from': [25, 8, z], 'to': [35, 8, z]})
    for x in (25, 35):
        m.joins.append({'from': [x, 8, 30], 'to': [x, 8, 38]})
    for step, z in enumerate(range(21, 27), start=3):
        m.fill((31, 2, z), (33, step - 1, z), 'foundation')
        m.fill((31, step, z), (33, step, z), _stairs('south'))
        for x in (30, 34):
            m.fill((x, 2, z), (x, step, z), 'wall')
            m.set((x, step + 1, z), 'cap')

    m.frame(25, p.pavilion_east, p.pavilion_front, 38, 9, 12)
    m.hip_roof(24, p.pavilion_east + 1, p.pavilion_front - 1, 39, 13)
    m.lantern(26, 11, p.pavilion_front + 1)
    m.set((27, 9, 37), 'minecraft:smooth_sandstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    m.set((33, 9, 37), 'minecraft:lectern[facing=north,has_book=false,powered=false]')
    # 护栏只围露台外边，不封住三格宽楼梯出口；柱位留给框架。
    rails = {(x, z) for x in range(25, 36) for z in range(27, 40)
             if x in (25, 35) or z == 39 or (z == 27 and x not in (31, 32, 33))}
    posts = {(25, p.pavilion_front), (25, 38)}
    for x, z in sorted(rails - posts):
        props = ','.join(f'{name}={str((x + dx, z + dz) in rails).lower()}'
                         for name, dx, dz in (('north', 0, -1), ('south', 0, 1), ('east', 1, 0), ('west', -1, 0)))
        m.set((x, 9, z), f'minecraft:dark_oak_fence[{props},waterlogged=false]')

    m.frame(26, 34, p.quiet_front, p.quiet_back, 3, 6)
    for z in range(p.quiet_front, p.quiet_back + 1, 2):
        m.fill((26, 7, z), (34, 7, z), 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    m.fill((27, 3, p.quiet_back - 1), (31, 3, p.quiet_back - 1),
           'minecraft:smooth_sandstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')


def _path(graph, start, end, spaces):
    """由已建成的步行图证明邻接；不插值、不瞬移、不伪造两点直线。"""
    start, end = tuple(start), tuple(end)
    parents, pending = {start: None}, deque([start])
    while pending:
        node = pending.popleft()
        if node == end:
            path = []
            while node is not None:
                path.append(node)
                node = parents[node]
            return tuple(reversed(path))
        for other in graph.get(node, ()):
            if other not in parents and any(space.contains(other) for space in spaces):
                parents[other] = node
                pending.append(other)
    raise ValueError(f'问灵小院空间不连通：{start} → {end}')


def generate(context: GenerationContext) -> SiteResult:
    if context.contract.site != SITE_ID:
        raise ValueError('问灵小院生成器收到其他地点契约')
    contract = context.contract
    p = _Layout.sample(context)
    spaces = p.spaces()
    baseline = contract.blueprint()
    m = _Masonry(baseline['palette'])
    _grounds(m, p, contract.size)
    _rituals_and_cloisters(m, p)
    _scripture_and_retreat(m, p)
    builder = SiteBuilder(contract)
    builder.apply_ops(m.ops)
    blocks = {pos: cell.state for pos, cell in builder.grid.iter_cells()}
    graph = walk_graph(blocks, contract.size)
    targets = baseline['walk_targets']
    connections = (
        ('forecourt', 'main_court', 'forecourt', 'main_court'),
        ('main_court', 'testing', 'main_court', 'testing_east_approach'),
        ('testing', 'west_cloister', 'testing', 'west_cloister'),
        ('west_cloister', 'return_gallery', 'west_cloister', 'return_gallery'),
        ('return_gallery', 'inheritance', 'return_gallery', 'inheritance_veranda'),
        ('return_gallery', 'scripture', 'return_gallery', 'scripture_room'),
        ('main_court', 'quiet_terrace', 'main_court', 'quiet_seat'),
        ('quiet_terrace', 'observation_stair', 'quiet_seat', 'stair_bottom'),
        ('observation_stair', 'star_deck', 'stair_bottom', 'star_deck'),
        ('star_deck', 'observation_pavilion', 'star_deck', 'observation_pavilion'),
    )
    by_id = {space.id: space for space in spaces}
    plan = SemanticPlan(spaces, tuple(Adjacency(a, b, _path(graph, targets[start], targets[end],
                                                          (by_id[a], by_id[b])))
                                      for a, b, start, end in connections))
    return builder.finish(plan, metadata={
        'description': '问灵小院：种子规划的八角测灵亭、独立横向传承堂、覆顶折廊和可达观星静修台；可选入门。',
        'presentation': {**baseline['presentation'],
                         'summary': '前庭分流，西亭测灵、后堂传承；东侧静修与登台观星互不挤占。',
                         'spaces': [space.purpose for space in spaces],
                         'stage': '离线 seeded 几何候选；保持部署契约，原生碰撞与玩家外观尚未验证。'},
        'design_notes': [
            '测灵与传承各自独立，仍是可选入门，不增加本章任务条件。',
            '种子改变八角亭边界、横堂开间、覆廊宽深、静修廊架与偏置观星阁；所有剧情站位、接近点及设施状态固定。',
            '主入口五格宽、主院露天、西侧覆廊净宽至少三格；登台阶保持三格宽六级连续上升。',
            '静修廊架木条间隙及观星台东半部刻意露天；完整居室、仪式屋顶与覆廊另有真实截面检查。',
            '离线拓扑不等于原生碰撞、点击、映射输入或玩家外观验收；这些仍为 not_verified。',
        ],
        'roof_checks': m.roofs,
        'wall_roof_joins': m.joins,
    })
