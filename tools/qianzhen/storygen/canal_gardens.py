"""青泉渠圃：固定剧情站位上的三级渠田、房舍和桥亭组合。"""
from __future__ import annotations

from collections import deque

from buildgen.grid import PRIORITY

from . import (
    Adjacency, GenerationContext, SemanticPlan, SemanticSpace, SiteBuilder, SiteResult,
)
from ..build import walk_graph

SITE_ID = 'canal_gardens'

AIR = 'minecraft:air'
STONE = 'minecraft:sandstone'
CUT = 'minecraft:cut_sandstone'
PAVING = 'minecraft:smooth_sandstone'
WOOD = 'minecraft:spruce_planks'
POST = 'minecraft:spruce_log[axis=y]'
TILE = 'minecraft:deepslate_tiles'
WATER = 'minecraft:water[level=0]'


def _stairs(material, facing):
    return f'minecraft:{material}_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]'


class _Plot:
    """先组合最终几何，再提交保护检查；不载入 authored 的方块或 ops。"""

    def __init__(self):
        self.cells = {}
        self.roofs = []
        self.beds = []
        self.frames = []

    def set(self, pos, state):
        self.cells[tuple(pos)] = state

    def fill(self, lo, hi, state):
        for x in range(lo[0], hi[0] + 1):
            for y in range(lo[1], hi[1] + 1):
                for z in range(lo[2], hi[2] + 1):
                    self.set((x, y, z), state)

    def gable(self, x0, x1, z0, z1, eave):
        """沿 x 的屋脊、逐格实心灰瓦坡；返回屋面在每个 z 的高度。"""
        heights = {}
        middle = (z0 + z1) // 2
        for z in range(z0, z1 + 1):
            y = eave + min(z - z0, z1 - z)
            state = TILE if z == middle else _stairs('deepslate_tile', 'south' if z < middle else 'north')
            self.fill((x0, y, z), (x1, y, z), state)
            heights[z] = y
        self.roofs.append({'axis': 'z', 'min_y': eave,
                           'x_range': [x0, x1], 'z_range': [z0, z1]})
        return heights

    def house(self, x0, x1, z0, z1, floor, eave, door_x):
        self.fill((x0, floor, z0), (x1, floor, z1), WOOD)
        for x in (x0, x1):
            self.fill((x, floor + 1, z0), (x, eave - 1, z1), PAVING)
        for z in (z0, z1):
            self.fill((x0, floor + 1, z), (x1, eave - 1, z), PAVING)
            self.fill((door_x - 1, floor + 1, z), (door_x + 1, floor + 3, z), AIR)
        heights = self.gable(x0 - 2, x1 + 2, z0 - 2, z1 + 2, eave)
        for x in (x0, x1):
            for z in range(z0, z1 + 1):
                self.fill((x, eave, z), (x, heights[z] - 1, z), PAVING)
            for z in (z0, z1):
                self.fill((x, floor + 1, z), (x, eave - 1, z), POST)

    def bed(self, name, bounds, y, plant):
        x0, x1, z0, z1 = bounds
        self.fill((x0, y, z0), (x1, y, z1), CUT)
        self.fill((x0 + 1, y, z0 + 1), (x1 - 1, y, z1 - 1), 'minecraft:farmland[moisture=7]')
        water = ((x0 + x1) // 2, y, (z0 + z1) // 2)
        self.set((water[0], y - 1, water[2]), STONE)
        self.set(water, WATER)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, y, z) != water:
                    self.set((x, y + 1, z), plant)
        self.beds.append({'name': name, 'bounds': list(bounds), 'soil_y': y, 'water': list(water)})

    def pavilion_frame(self, name, left, right, front, back, floor, eave, *, overhang=2):
        """Join the existing posts to the unchanged two-block-overhang gable.

        Side purlins and a ridge purlin carry the whole run. The two end frames
        have a tie and short king post, leaving the occupied pavilion open.
        """
        top = eave + overhang - 1
        middle = (front + back) // 2
        ridge = eave + middle - (front - overhang)
        for x in (left, right):
            for z in (front, back):
                self.fill((x, floor + 1, z), (x, top, z), POST)
            self.fill((x, top, front), (x, top, back), 'minecraft:spruce_log[axis=z]')
            self.fill((x, top + 1, middle), (x, ridge - 1, middle), POST)
        for z, y in ((front, top), (middle, ridge - 1), (back, top)):
            self.fill((left, y, z), (right, y, z), 'minecraft:spruce_log[axis=x]')
        self.frames.append({
            'id': name, 'bounds': [[left, floor + 1, front], [right, ridge, back]],
            'posts': [{'foot': [x, floor + 1, z], 'top_y': top}
                      for x in (left, right) for z in (front, back)],
            'bearings': [{'from': [left, y + 1, z], 'to': [right, y + 1, z]}
                         for z, y in ((front, top), (middle, ridge - 1), (back, top))],
        })


def _terrain(plot):
    # 保留入口 z=0 的真实落脚基础；三级退台均填实，不用悬空表面。
    for z in range(1, 46):
        inset = 4 if z in (1, 45) else 2 if z in (2, 3, 43, 44) else 1
        plot.fill((inset, 0, z), (47 - inset, 0, z), STONE)
        plot.fill((inset, 1, z), (47 - inset, 1, z), 'minecraft:sand')
    plot.fill((20, 0, 0), (24, 0, 0), STONE)
    plot.fill((20, 1, 0), (24, 1, 0), PAVING)
    for x0, x1, z0, z1, y in ((3, 44, 18, 29, 4), (7, 42, 30, 42, 7), (10, 39, 43, 44, 7)):
        plot.fill((x0, 1, z0), (x1, y, z1), 'minecraft:dirt')
        for x in (x0, x1):
            plot.fill((x, 1, z0), (x, y, z1), STONE)
        plot.fill((x0, 1, z0), (x1, y, z0), STONE)
        plot.fill((x0, y, z0), (x1, y, z0), CUT)
    for lo, hi in (
        ((3, 1, 1), (44, 1, 3)), ((20, 1, 3), (23, 1, 17)),
        ((3, 1, 12), (23, 1, 14)), ((31, 1, 10), (43, 1, 12)),
        ((35, 1, 3), (39, 1, 14)), ((5, 4, 18), (43, 4, 20)),
        ((11, 4, 20), (13, 4, 26)), ((21, 4, 20), (23, 4, 29)),
        ((36, 4, 20), (38, 4, 26)), ((8, 7, 30), (41, 7, 32)),
        ((11, 7, 32), (13, 7, 41)), ((20, 7, 32), (22, 7, 42)),
        ((30, 7, 32), (31, 7, 41)), ((36, 7, 30), (38, 7, 42)),
        ((13, 7, 41), (38, 7, 43)),
    ):
        plot.fill(lo, hi, PAVING)
    # 东西两条三格宽梯道都保留，调查后可以沿另一岸离开园圃。
    for x0, x1, start_z, floor in ((8, 10, 15, 1), (36, 38, 15, 1),
                                 (11, 13, 27, 4), (36, 38, 27, 4)):
        for i in range(3):
            z, y = start_z + i, floor + i + 1
            plot.fill((x0, 1, z), (x1, y - 1, z), STONE)
            plot.fill((x0, y, z), (x1, y, z), _stairs('sandstone', 'south'))


def _canal(plot, rng):
    water = set()

    def reach(x0, x1, z0, z1, y):
        water.update((x, y, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))

    reach(25, 26, 4, 17, 1)
    reach(25, 26, 18, 29, 4)
    reach(25, 26, 30, 40, 7)
    # 实际变化沉砂段的宽度/长度和泉口池轮廓，而非只替换水边植物。
    reach(24, rng.choice((27, 28)), rng.choice((21, 22)), 25, 4)
    reach(23, rng.choice((28, 29)), rng.choice((36, 37)), 40, 7)
    for z, lo, hi in ((17, 1, 4), (29, 4, 7)):
        water.update((x, y, z) for x in (25, 26) for y in range(lo, hi + 1))
        plot.fill((24, hi + 1, z), (27, hi + 1, z), CUT)
    # 逐格底与岸；水井的侧壁随水柱上升，水面上方不假造地面。
    for x, y, z in sorted(water):
        for dx, dy, dz in ((0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
            p = (x + dx, y + dy, z + dz)
            if p not in water:
                plot.set(p, CUT)
    for pos in sorted(water):
        plot.set(pos, WATER)
    plot.fill((23, 7, 41), (29, 11, 41), STONE)
    plot.fill((25, 8, 40), (27, 9, 40), 'minecraft:oxidized_cut_copper')
    plot.fill((22, 12, 40), (30, 12, 42), TILE)


def _beds(plot, rng):
    plot.bed('下畦·籽穗', (3, 7, 15, 17), 1, 'minecraft:wheat[age=7]')
    plot.bed('下畦·根药', (13, rng.choice((18, 19)), 15, 17), 1, 'minecraft:carrots[age=5]')
    plot.bed('下畦·赤根', (31, 34, 4, 8), 1, 'minecraft:beetroots[age=3]')
    for name, x0, x1, plant in (
        ('中畦·西穗', 4, 10, 'minecraft:wheat[age=5]'),
        ('中畦·西根', 14, 19, 'minecraft:beetroots[age=3]'),
        ('中畦·东根', 30, 35, 'minecraft:carrots[age=5]'),
        ('中畦·东穗', 39, 43, 'minecraft:wheat[age=7]'),
    ):
        if rng.choice((False, True)):
            # 两块独立小畦，中间留下实际巡田路。
            for number, (z0, z1) in enumerate(((21, 23), (25, 27)), 1):
                plot.bed(f'{name}·{number}', (x0, x1, z0, z1), 4, plant)
            plot.fill((x0, 4, 24), (x1, 4, 24), PAVING)
        else:
            plot.bed(name, (x0, x1, 21, 26), 4, plant)
    for name, x0, x1 in (('上畦·青根', 8, 10), ('上畦·根样', 14, 18)):
        for i, (z0, z1) in enumerate(((33, 36), (38, 40)) if rng.choice((False, True)) else ((33, 40),)):
            plot.bed(f'{name}·{i + 1}', (x0, x1, z0, z1), 7, 'minecraft:carrots[age=7]')
    plot.fill((40, 1, 14), (44, 1, 16), 'minecraft:sand')
    for p in ((40, 2, 15), (43, 2, 15), (44, 2, 16)):
        plot.set(p, 'minecraft:dead_bush')


def _bridges(plot, rng):
    for floor, left, right, z0, z1 in (
        (2, 23, 29, rng.choice((8, 9)), 13),
        (8, 23, 28, rng.choice((31, 32)), rng.choice((35, 36))),
    ):
        plot.fill((left, floor, z0), (right, floor, z1), WOOD)
        for x in (left, right):
            # 桥台和支柱落到实体基底；水中间净跨，不堵塞渠流。
            plot.fill((x, 0, z0), (x, floor, z0), POST)
            plot.fill((x, 0, z1), (x, floor, z1), POST)
        for z in range(z0 + 1, z1):
            plot.set((left - 1, floor, z), _stairs('sandstone', 'east'))
            plot.set((right + 1, floor, z), _stairs('sandstone', 'west'))
        for z in (z0, z1):
            plot.fill((left, floor + 1, z), (right, floor + 1, z),
                      'minecraft:spruce_fence[east=true,north=false,south=false,west=true,waterlogged=false]')
        if floor == 8:
            for x in (left, right):
                for z in (z0, z1):
                    plot.fill((x, floor + 1, z), (x, 12, z), POST)
            plot.gable(21, 30, z0 - 1, z1 + 1, 13)
            plot.pavilion_frame('inspection_bridge', left, right, z0, z1, floor, 13, overhang=1)
            for z in (z0, z1):
                plot.fill((left, 12, z), (right, 12, z), 'minecraft:spruce_log[axis=x]')


def _buildings(plot, rng):
    plot.house(3, 13, 4, 11, 1, 7, 8)
    # 工具棚为田舍附属体块；进深和接檐随种子变化，站位保持原处。
    shed_back = rng.choice((11, 12))
    plot.fill((14, 1, 5), (20, 1, shed_back), WOOD)
    plot.fill((14, 2, 5), (20, 4, 5), PAVING)
    for x in (15, 19):
        plot.fill((x, 2, shed_back), (x, 5, shed_back), POST)
    shed_step = rng.choice((16, 17))
    for z in (5, shed_back):
        plot.fill((14, 5, z), (shed_step, 5, z), 'minecraft:spruce_log[axis=x]')
    for x in range(14, 21):
        y = 6 if x <= shed_step else 5
        plot.fill((x, y, 4), (x, y, shed_back + 1), TILE)
    plot.roofs.append({'axis': 'z', 'min_y': 5, 'x_range': [14, 20], 'z_range': [4, shed_back + 1]})
    for p, state in (((4, 2, 6), 'minecraft:barrel[facing=up,open=false]'),
                     ((4, 2, 9), 'minecraft:crafting_table'),
                     ((17, 2, 6), 'minecraft:barrel[facing=up,open=false]')):
        plot.set(p, state)

    pavilion_left = rng.choice((32, 33))
    plot.fill((pavilion_left, 1, 9), (43, 1, 13), WOOD)
    for x in (pavilion_left, 43):
        for z in (9, 13):
            plot.fill((x, 2, z), (x, 5, z), POST)
    plot.gable(pavilion_left - 1, 44, 7, 15, 6)
    plot.pavilion_frame('sample_pavilion', pavilion_left, 43, 9, 13, 1, 6)
    plot.fill((pavilion_left, 5, 9), (43, 5, 9), 'minecraft:spruce_log[axis=x]')
    plot.fill((37, 4, 9), (37, 5, 9), 'minecraft:spruce_log[axis=x]')
    plot.fill((40, 2, 10), (41, 2, 10), WOOD)
    plot.set((40, 3, 10), 'minecraft:flower_pot')
    plot.set((41, 3, 10), 'minecraft:potted_fern')

    plot.house(32, 41, 33, 40, 7, 13, 36)
    plot.fill((32, 8, 35), (32, 10, 37), AIR)
    plot.set((39, 8, 36), 'minecraft:barrel[facing=up,open=false]')
    plot.set((30, 8, 38), 'minecraft:oxidized_cut_copper')
    plot.set((31, 8, 38), 'minecraft:lever[face=wall,facing=east,powered=false]')
    for x in (20, 24):
        for z in (19, 24, 28):
            plot.fill((x, 0, z), (x, 4, z), STONE)
            plot.fill((x, 5, z), (x, 8, z), POST)
        plot.fill((x, 9, 19), (x, 9, 28), 'minecraft:spruce_log[axis=z]')
    for x0, x1, y in ((19, 25, 10), (20, 24, 11), (21, 23, 12)):
        plot.fill((x0, y, 18), (x1, y, 29), TILE)
    plot.roofs.append({'axis': 'x', 'min_y': 10, 'x_range': [19, 25], 'z_range': [18, 29]})
    # This stepped gallery has a full lower tile layer at y=10: its existing
    # purlins already touch the roof. Only close the timber frame with ties below
    # that layer; never replace existing tiles with a new raised wooden frame.
    for z in (19, 24, 28):
        plot.fill((20, 9, z), (24, 9, z), 'minecraft:spruce_log[axis=x]')
    plot.frames.append({
        'id': 'canal_gallery', 'bounds': [[20, 5, 19], [24, 10, 28]],
        'posts': [{'foot': [x, 5, z], 'top_y': 9}
                  for x in (20, 24) for z in (19, 24, 28)],
        'bearings': [{'from': [x, y, 19], 'to': [x, y, 28]}
                     for x, y in ((20, 10), (24, 10))],
    })
    plot.fill((19, 5, 21), (19, 9, 21), POST)
    for p in ((8, 5, 3), (37, 4, 8), (36, 11, 32), (19, 6, 20)):
        plot.set(p, 'minecraft:spruce_wall_sign[facing=north,waterlogged=false]')
    # 闸房招牌支承在门洞上方；田舍牌也在连续门楣上。
    plot.set((36, 11, 33), 'minecraft:spruce_log[axis=x]')


def _route(graph, points):
    result = []
    for start, end in zip(points, points[1:]):
        start, end = tuple(start), tuple(end)
        parents, todo = {start: None}, deque([start])
        while todo and end not in parents:
            current = todo.popleft()
            for pos in sorted(graph.get(current, ())):
                if pos not in parents:
                    parents[pos] = current
                    todo.append(pos)
        if end not in parents:
            raise ValueError(f'canal garden route missing: {start} -> {end}')
        segment, current = [], end
        while current is not None:
            segment.append(current)
            current = parents[current]
        segment.reverse()
        result.extend(segment[1:] if result else segment)
    return tuple(result)


def _semantics(contract, cells):
    spaces = (
        SemanticSpace('arrival', '低岸入口及运料通道', (20, 0, 0), (24, 4, 17), ('entry',)),
        SemanticSpace('fieldhouse', '田舍及前后出入口', (1, 0, 2), (15, 13, 13), ('fieldhouse',)),
        SemanticSpace('tool_store', '田舍附属工具棚', (14, 0, 4), (20, 7, 13), ('tool_store',)),
        SemanticSpace('lower_beds', '低岸药畦与西侧上行梯道', (3, 0, 12), (20, 5, 18), ('lower_beds',)),
        SemanticSpace('cart_bridge', '跨下渠的运料板桥及桥台', (21, 0, 8), (31, 5, 14), ('cart_bridge',)),
        SemanticSpace('sample_pavilion', '清水样釜所在的开放水亭', (31, 0, 7), (45, 11, 13), ('sample_pavilion',)),
        SemanticSpace('withered_bed', '取样亭后的沙化枯畦与破罐', (39, 0, 13), (45, 4, 17), ('withered_clue',)),
        SemanticSpace('middle_west', '中层西岸分区药畦及巡田路', (3, 1, 18), (20, 8, 29), ('middle_west',)),
        SemanticSpace('canal_gallery', '沉砂段西岸覆顶观察廊', (19, 1, 18), (25, 13, 29), ('canal_gallery',)),
        SemanticSpace('middle_east', '中层东岸药畦与上行梯道', (30, 1, 18), (44, 8, 29), ('middle_east',)),
        SemanticSpace('upper_beds', '高层样株畦与取水巡田路', (7, 1, 30), (20, 10, 43), ('upper_beds',)),
        SemanticSpace('inspection_bridge', '高渠上的覆顶取水廊桥', (21, 1, 29), (30, 18, 37), ('inspection_bridge',)),
        SemanticSpace('intake_basin', '高位泉口池与干岸观察点', (20, 1, 36), (30, 14, 42), ('intake_basin',)),
        SemanticSpace('sluice', '闸柄及闸房西侧操作通道', (30, 1, 35), (32, 11, 40), ('sluice_valve',)),
        SemanticSpace('gatehouse', '高位泉闸房及前后入口', (30, 1, 31), (43, 19, 42), ('gatehouse',)),
        SemanticSpace('rear_path', '后侧巡渠回程步道', (10, 1, 41), (40, 10, 44), ('rear_path',)),
    )
    links = (
        ('arrival', 'fieldhouse', ()), ('fieldhouse', 'tool_store', ()),
        ('arrival', 'lower_beds', ()), ('arrival', 'cart_bridge', ()),
        ('cart_bridge', 'sample_pavilion', ()), ('sample_pavilion', 'withered_bed', ()),
        ('lower_beds', 'middle_west', ((9, 2, 14), (9, 5, 18))),
        ('sample_pavilion', 'middle_east', ((37, 2, 14), (37, 5, 18))),
        ('middle_west', 'canal_gallery', ()),
        ('middle_west', 'upper_beds', ((12, 5, 26), (12, 8, 30))),
        ('middle_east', 'gatehouse', ((37, 5, 26), (37, 8, 30))),
        ('upper_beds', 'inspection_bridge', ()), ('inspection_bridge', 'intake_basin', ()),
        ('inspection_bridge', 'sluice', ()), ('sluice', 'gatehouse', ()),
        ('gatehouse', 'rear_path', ()),
    )
    by_id = {space.id: space for space in spaces}
    targets = contract.blueprint()['walk_targets']
    graph = walk_graph(cells, contract.size)
    edges = []
    for source, target, via in links:
        a = targets[by_id[source].walk_targets[0]]
        b = targets[by_id[target].walk_targets[0]]
        edges.append(Adjacency(source, target, _route(graph, [a, *via, b])))
    return SemanticPlan(spaces, tuple(edges))


def generate(context: GenerationContext) -> SiteResult:
    contract = context.contract
    if contract.site != SITE_ID:
        raise ValueError('canal_gardens requires its own site contract')
    plot = _Plot()
    _terrain(plot)
    _canal(plot, context.rng('canal'))
    _beds(plot, context.rng('beds'))
    _bridges(plot, context.rng('bridges'))
    _buildings(plot, context.rng('buildings'))
    for target in contract.targets.values():
        plot.set(target['pos'], target['state'])
    # 所有写入先形成最终方案再核对；不靠 force 或事后恢复保护格过关。
    for pos, state in contract.protected:
        if plot.cells.get(pos, AIR) != state:
            raise ValueError(f'canal garden plan conflicts with protected story cell: {pos}')
    builder = SiteBuilder(contract)
    for pos, state in sorted(plot.cells.items()):
        builder.grid.set(pos, state, ('STRUCTURE',), PRIORITY['STRUCTURE'])
    presentation = contract.blueprint()['presentation']
    presentation.update({
        'summary': '三级实体台地、封闭泉渠与分区药畦，串联田舍附棚、运料桥、清水取样亭和高位闸房。',
        'spaces': ['低岸田舍及附属工具棚、运料桥', '清水取样亭及沙化枯畦',
                   '中层分区药畦、沉砂渠与覆顶岸廊', '高层样株畦、取水廊桥、泉口池与闸房'],
        'stage': 'seeded 离线剧情约束生成；原生碰撞、水流外观与玩家审美 not_verified。',
    })
    return builder.finish(_semantics(contract, plot.cells), metadata={
        'description': '从空地组合三级渠圃，保留调查点、双岸梯道和既有部署坐标。',
        'presentation': presentation,
        'roof_checks': plot.roofs,
        'timber_roof_frames': plot.frames,
        'irrigation_beds': plot.beds,
        'design_notes': [
            '砂岩退台、深色木构与灰瓦沿用渠圃方向；种子改变药畦分区、沉砂渠段、附棚和桥亭体量。',
            '渠底、渠岸、桥台和梯道均为实体；取样釜、破罐、接近点与入口坐标保持剧情契约。',
            '水体和种植仅作场景表达；自然生成、任务机制修改及原生游戏验收不在本生成器范围。',
        ],
    })
