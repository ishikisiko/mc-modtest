"""长明药庐：先定功能院落与锚点，再从空场地落块；不读取 authored 几何。"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass

from . import (Adjacency, GenerationContext, SemanticPlan, SemanticSpace,
               SiteBuilder, SiteResult)
from ..build import walk_graph
from ..story_assets import canonical

SITE_ID = 'apothecary'

AIR = 'minecraft:air'
WALL = 'minecraft:smooth_sandstone'
WOOD = 'minecraft:dark_oak_planks'
POST = 'minecraft:stripped_spruce_log[axis=y]'
PAVING = 'minecraft:terracotta'
PATH = 'minecraft:cut_sandstone'
STONE = 'minecraft:smooth_stone'
TILE = 'minecraft:deepslate_tiles'


@dataclass(frozen=True)
class _Layout:
    name: str
    shop_west: int
    clinic_rear: int
    gallery_east: int
    alchemy_west: int
    alchemy_front: int
    racks_along_z: bool


def _plan(context):
    # These are spatial grammars, not palette presets. Every choice changes walls,
    # usable floor area, roof outline and the way work spaces border the court.
    layout = context.rng('massing').choice((
        _Layout('井院分庭', 4, 22, 13, 4, 30, False),
        _Layout('宽店深诊', 2, 24, 15, 4, 30, True),
        _Layout('丹房抱院', 4, 22, 13, 3, 28, True),
    ))
    spaces = (
        SemanticSpace('arrival', '临街三格宽取药入口', (17, 0, 0), (22, 5, 6), ('entry',)),
        SemanticSpace('front_shop', '取药前店、药柜、药师工作道',
                      (layout.shop_west, 0, 5), (24, 5, 17),
                      ('shop_threshold', 'counter', 'pharmacist', 'dispensing_workstation')),
        SemanticSpace('stairs', '三格宽六级药阁楼梯及上下平台',
                      (6, 0, 8), (8, 9, 15), ('stair_foot', 'stair_top')),
        SemanticSpace('archive', '二层干药储藏与病案药阁',
                      (layout.shop_west, 6, 5), (24, 20, 17), ('upper_herb_archive',)),
        SemanticSpace('records', '药阁回折书柜后的旧账席',
                      (16, 6, 10), (24, 11, 17), ('hidden_records',)),
        SemanticSpace('upper_gallery', '俯看井院的二层挑廊',
                      (5, 6, 17), (layout.gallery_east, 19, 28), ('upper_gallery',)),
        SemanticSpace('work_gallery', '挑廊下候药座席与分拣席',
                      (5, 0, 18), (layout.gallery_east, 5, 28), ('under_gallery',)),
        SemanticSpace('consultation', '独立问诊室及病样清水检验台',
                      (27, 0, 8), (38, 15, 15),
                      ('clinic_front', 'physician', 'patient_consultation', 'sample_exam')),
        SemanticSpace('ward', '隔屏留观室、周禾床位与床侧接近位',
                      (27, 0, 15), (38, 15, layout.clinic_rear),
                      ('ward_entry', 'zhou_he_bedside')),
        SemanticSpace('courtyard', '前店出院、封底井台及复命处',
                      (14, 0, 17), (31, 10, 34),
                      ('shop_to_yard', 'courtyard', 'well', 'report_back')),
        SemanticSpace('drying', '院东双排晾药架与通风工作巷',
                      (32, 0, 25), (39, 6, 34), ('drying_racks',)),
        SemanticSpace('alchemy', '后部低丹房、炉前安全位与洗切备料台',
                      (layout.alchemy_west, 0, layout.alchemy_front), (17, 15, 39),
                      ('alchemy', 'preparation', 'rear_workbench', 'supply_closet')),
        SemanticSpace('garden_exit', '三格宽后圃门及道路接驳',
                      (27, 0, 35), (31, 10, 42), ('garden_gate', 'garden_connection')),
    )
    # Connections name actual authored anchors. Paths are resolved only after the
    # planned walls, furniture and stairs exist, and must stay inside these spaces.
    connections = (
        ('arrival', 'front_shop', 'entry', 'shop_threshold'),
        ('front_shop', 'stairs', 'counter', 'stair_foot'),
        ('stairs', 'archive', 'stair_top', 'upper_herb_archive'),
        ('archive', 'records', 'upper_herb_archive', 'hidden_records'),
        ('archive', 'upper_gallery', 'upper_herb_archive', 'upper_gallery'),
        ('front_shop', 'courtyard', 'pharmacist', 'shop_to_yard'),
        ('courtyard', 'work_gallery', 'courtyard', 'under_gallery'),
        ('courtyard', 'ward', 'shop_to_yard', 'ward_entry'),
        ('ward', 'consultation', 'ward_entry', 'clinic_front'),
        ('consultation', 'ward', 'sample_exam', 'zhou_he_bedside'),
        ('courtyard', 'drying', 'well', 'drying_racks'),
        ('courtyard', 'alchemy', 'report_back', 'alchemy'),
        ('courtyard', 'garden_exit', 'report_back', 'garden_connection'),
    )
    return layout, spaces, connections


class _Masonry:
    """Collect final geometry before the builder's atomic protected-cell check."""
    def __init__(self):
        self.blocks = {}
        self.roofs = []
        self.joins = []
        self.signs = []

    def fill(self, a, b, state):
        for x in range(a[0], b[0] + 1):
            for y in range(a[1], b[1] + 1):
                for z in range(a[2], b[2] + 1):
                    self.blocks[x, y, z] = state

    def put(self, pos, state):
        self.blocks[pos] = state

    def room(self, x1, z1, x2, z2, height, floor=WOOD):
        self.fill((x1, 0, z1), (x2, 0, z2), floor)
        for z in (z1, z2):
            self.fill((x1, 1, z), (x2, height, z), WALL)
            self.fill((x1, height, z), (x2, height, z), 'minecraft:spruce_log[axis=x]')
        for x in (x1, x2):
            self.fill((x, 1, z1), (x, height, z2), WALL)
            self.fill((x, height, z1), (x, height, z2), 'minecraft:spruce_log[axis=z]')
            for z in (z1, z2):
                self.fill((x, 1, z), (x, height, z), POST)

    def roof(self, x1, z1, x2, z2, eave, axis='x'):
        """Continuous gable with one-block eaves, solid gables and real contacts."""
        low, high = (x1, x2) if axis == 'x' else (z1, z2)
        for cross in range(low, high + 1):
            y = eave + min(cross - low, high - cross)
            middle = cross - low == high - cross
            facing = ('east' if cross < (low + high) / 2 else 'west') if axis == 'x' else (
                'south' if cross < (low + high) / 2 else 'north')
            state = TILE if middle else _stairs('deepslate_tile', facing)
            a, b = ((cross, y, z1), (cross, y, z2)) if axis == 'x' else ((x1, y, cross), (x2, y, cross))
            self.fill(a, b, state)
            # Enclose gable ends; the two side walls already reach the eave.
            if low < cross < high:
                for end in ((z1 + 1, z2 - 1) if axis == 'x' else (x1 + 1, x2 - 1)):
                    a = (cross, eave, end) if axis == 'x' else (end, eave, cross)
                    b = (cross, y - 1, end) if axis == 'x' else (end, y - 1, cross)
                    self.fill(a, b, WALL)
        self.roofs.append({'axis': axis, 'min_y': eave,
                           'x_range': [x1, x2], 'z_range': [z1, z2]})
        for edge in (low + 1, high - 1):
            a = (edge, eave + 1, z1 + 1) if axis == 'x' else (x1 + 1, eave + 1, edge)
            b = (edge, eave + 1, z2 - 1) if axis == 'x' else (x2 - 1, eave + 1, edge)
            self.joins.append({'from': list(a), 'to': list(b)})

    def sign(self, pos, facing, title, detail):
        self.put(pos, f'minecraft:dark_oak_wall_sign[facing={facing},waterlogged=false]')
        self.signs.append({'pos': list(pos), 'messages': [title, detail, '', '']})

    def lantern(self, pos):
        x, y, z = pos
        self.put((x, y + 1, z), 'minecraft:spruce_log[axis=x]')
        self.put(pos, 'minecraft:lantern[hanging=true,waterlogged=false]')


def _stairs(material, facing):
    return f'minecraft:{material}_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]'


def _shop(m, layout):
    west = layout.shop_west
    m.room(west, 5, 24, 17, 5)
    m.fill((west, 6, 5), (24, 6, 17), WOOD)
    for z in (5, 17):
        m.fill((west, 7, z), (24, 11, z), WALL)
    for x in (west, 24):
        m.fill((x, 7, 5), (x, 11, 17), WALL)
    m.roof(west - 1, 4, 25, 18, 11, 'z')
    # Only the south-west valley intersects the gallery roof. Check every full
    # main section outside that valley instead of claiming an axis-mixed section.
    m.roofs[-1]['x_range'] = [layout.gallery_east + 2, 25]
    m.fill((18, 1, 5), (20, 3, 5), AIR)
    m.fill((20, 1, 17), (22, 3, 17), AIR)
    m.fill((9, 7, 17), (12, 9, 17), AIR)
    for y in (2, 8):
        for x in (west + 2, 13, 22):
            m.fill((x, y, 5), (x + 1, y + 1, 5), 'minecraft:brown_stained_glass')
    # Three-wide stairwell: open the ceiling before placing the six supported steps.
    m.fill((6, 6, 8), (8, 6, 14), AIR)
    for step in range(6):
        z, y = 9 + step, 1 + step
        m.fill((6, 1, z), (8, y - 1, z), WOOD)
        m.fill((6, y, z), (8, y, z), _stairs('dark_oak', 'south'))
    m.fill((11, 1, 12), (17, 1, 12), WOOD)
    m.fill((10, 1, 16), (18, 3, 16), 'minecraft:barrel[facing=north,open=false]')
    m.put((17, 1, 14), 'minecraft:crafting_table')
    m.fill((11, 7, 6), (15, 8, 6), 'minecraft:barrel[facing=south,open=false]')
    m.fill((16, 7, 10), (16, 10, 16), WOOD)
    m.fill((16, 7, 12), (16, 9, 14), AIR)
    m.fill((17, 7, 10), (23, 9, 10), 'minecraft:bookshelf')
    m.fill((19, 7, 16), (22, 8, 16), 'minecraft:bookshelf')
    m.put((21, 7, 14), 'minecraft:lectern[facing=north,has_book=false,powered=false]')
    m.lantern((22, 4, 15))
    m.sign((19, 4, 4), 'north', '长明药庐', '取药 · 诊病')


def _gallery(m, layout):
    east = layout.gallery_east
    m.fill((5, 0, 18), (east, 0, 28), WOOD)
    m.fill((5, 6, 17), (east, 6, 28), WOOD)
    for x in (5, east):
        for z in (18, 23, 28):
            m.fill((x, 1, z), (x, 10, z), POST)
        m.fill((x, 10, 18), (x, 10, 28), 'minecraft:spruce_log[axis=z]')
        m.fill((x, 7, 18), (x, 7, 28),
               'minecraft:dark_oak_fence[north=true,south=true,east=false,west=false,waterlogged=false]')
    m.fill((5, 10, 28), (east, 10, 28), 'minecraft:spruce_log[axis=x]')
    m.fill((5, 7, 28), (east, 7, 28),
           'minecraft:dark_oak_fence[north=false,south=false,east=true,west=true,waterlogged=false]')
    # A roofed open gallery has posts and beams, without gable infill.
    for x in range(4, east + 2):
        y = 10 + min(x - 4, east + 1 - x)
        state = TILE if 2 * x == east + 5 else _stairs('deepslate_tile', 'east' if 2 * x < east + 5 else 'west')
        m.fill((x, y, 17), (x, y, 29), state)
    m.roofs.append({'axis': 'x', 'min_y': 10, 'x_range': [4, east + 1], 'z_range': [20, 29]})
    for x in (5, east):
        m.fill((x, 10, 18), (x, 10, 28), 'minecraft:spruce_log[axis=z]')
        m.joins.append({'from': [x, 11, 20], 'to': [x, 11, 28]})
    m.fill((6, 1, 21), (6, 1, 24), _stairs('dark_oak', 'east'))
    m.lantern((10, 5, 22))


def _clinic(m, layout):
    rear = layout.clinic_rear
    m.room(27, 8, 38, rear, 6)
    m.roof(26, 7, 39, rear + 1, 6)
    m.fill((30, 1, 8), (32, 3, 8), AIR)
    m.fill((27, 1, 17), (27, 3, 19), AIR)
    m.fill((28, 1, 15), (37, 4, 15), WALL)
    m.fill((30, 1, 15), (32, 3, 15), AIR)
    for z in (10, 18):
        m.fill((38, 2, z), (38, 3, z + 2), 'minecraft:brown_stained_glass')
    m.fill((29, 2, rear), (36, 3, rear), 'minecraft:brown_stained_glass')
    m.fill((29, 1, 12), (30, 1, 12), WOOD)
    m.put((35, 1, 12), STONE)
    m.put((36, 1, 12), 'minecraft:cauldron')
    # Keep the existing clear eye ray above the examination cauldron.
    m.put((36, 1, 10), 'minecraft:barrel[facing=west,open=false]')
    for x, color in ((34, 'white'), (36, 'light_gray')):
        for z, part in ((19, 'head'), (20, 'foot')):
            m.put((x, 1, z), f'minecraft:{color}_bed[facing=north,part={part},occupied=false]')
    if rear == 24:
        # Deeper ward gains a real southern wash alcove, separate from bedside access.
        m.fill((28, 1, 22), (30, 1, 23), STONE)
        m.put((29, 2, 23), 'minecraft:flower_pot')
    m.lantern((32, 4, 12))
    m.lantern((31, 4, 19))
    m.sign((31, 4, 7), 'north', '长明诊室', '辨样在前 · 留观在后')


def _alchemy(m, layout, contract):
    west, front = layout.alchemy_west, layout.alchemy_front
    m.room(west, front, 17, 39, 6, STONE)
    m.roof(west - 1, front - 1, 18, 40, 6)
    m.fill((17, 1, 32), (17, 3, 34), AIR)
    m.fill((11, 1, 34), (11, 4, 38), WOOD)
    m.fill((12, 1, 34), (16, 4, 34), WOOD)
    m.fill((11, 1, 36), (11, 3, 37), AIR)
    for z, hinge in ((36, 'left'), (37, 'right')):
        for y, half in ((1, 'lower'), (2, 'upper')):
            m.put((11, y, z), f'minecraft:dark_oak_door[facing=east,half={half},hinge={hinge},open=false,powered=false]')
    m.fill((14, 1, 38), (15, 2, 38), 'minecraft:barrel[facing=north,open=false]')
    m.fill((west + 1, 1, front + 2), (west + 1, 2, front + 4), 'minecraft:barrel[facing=east,open=false]')
    m.fill((6, 0, 34), (8, 0, 36), 'minecraft:polished_deepslate')
    for service in contract.blueprint().get('service_blocks', []):
        m.put(tuple(service['pos']), service['state'])
    m.fill((6, 1, 38), (9, 1, 38), STONE)
    m.put((9, 1, 38), 'minecraft:crafting_table')
    m.put((13, 1, 32), 'minecraft:crafting_table')
    if front == 28:
        # A north preparation bay forms an L with the gallery, enclosing the west court.
        m.fill((5, 1, 29), (8, 1, 29), STONE)
        m.fill((10, 2, front), (14, 3, front), 'minecraft:brown_stained_glass')
    m.lantern((9, 4, 33))
    m.sign((18, 4, 33), 'east', '备料丹房', '洗切 · 分拣 · 炼丹')


def _yard(m, layout):
    # A shallow, sealed oasis well rather than an exposed infinite water patch.
    m.fill((24, 0, 25), (27, 1, 28), 'minecraft:sandstone')
    m.fill((25, 1, 26), (26, 1, 27), 'minecraft:water[level=0]')
    for x in (24, 27):
        for z in (25, 28):
            m.fill((x, 2, z), (x, 4, z), POST)
    m.fill((24, 4, 25), (27, 4, 28), 'minecraft:dark_oak_slab[type=top,waterlogged=false]')
    # Two usable drying alleys: transverse benches versus longitudinal trellises.
    for edge in ((32, 39) if layout.racks_along_z else (25, 33)):
        a, b = ((edge, 1, 25), (edge, 2, 33)) if layout.racks_along_z else ((32, 1, edge), (39, 2, edge))
        m.fill(a, b, 'minecraft:dark_oak_slab[type=top,waterlogged=false]')
        for end in ((25, 33) if layout.racks_along_z else (32, 39)):
            p = (edge, 1, end) if layout.racks_along_z else (end, 1, edge)
            m.fill(p, (p[0], 4, p[2]), POST)
        a, b = ((edge, 4, 25), (edge, 4, 33)) if layout.racks_along_z else ((32, 4, edge), (39, 4, edge))
        m.fill(a, b, 'minecraft:spruce_log[axis=z]' if layout.racks_along_z else 'minecraft:spruce_log[axis=x]')
    for z in (25, 29, 33):
        m.fill((32, 5, z), (39, 5, z), 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    m.fill((22, 1, 32), (25, 1, 32), _stairs('dark_oak', 'north'))
    for x in (2, 40):
        m.fill((x, 1, 25), (x, 2, 40), WALL)
    m.fill((2, 1, 40), (40, 3, 40), WALL)
    m.fill((28, 1, 40), (30, 3, 40), AIR)
    for x in (27, 31):
        m.fill((x, 1, 40), (x, 4, 40), POST)
    m.fill((27, 4, 40), (31, 4, 40), 'minecraft:spruce_log[axis=x]')
    m.fill((27, 5, 39), (31, 5, 41), TILE)
    m.sign((29, 4, 39), 'north', '青泉渠圃', '后圃接驳')


def _path(graph, start, end, allowed):
    todo, previous = deque([start]), {start: None}
    while todo:
        current = todo.popleft()
        if current == end:
            result = []
            while current is not None:
                result.append(current)
                current = previous[current]
            return tuple(reversed(result))
        for point in sorted(graph.get(current, ())):
            if point not in previous and any(s.contains(point) for s in allowed):
                previous[point] = current
                todo.append(point)
    raise ValueError(f'apothecary planned adjacency is blocked: {start} -> {end}')


def generate(context: GenerationContext) -> SiteResult:
    if context.contract.site != SITE_ID:
        raise ValueError('apothecary needs its own site contract')
    layout, spaces, connections = _plan(context)
    contract = context.contract
    m = _Masonry()
    m.fill((0, 0, 0), (42, 0, 42), 'minecraft:sandstone')
    m.fill((2, 0, 2), (40, 0, 40), PAVING)
    _shop(m, layout)
    _gallery(m, layout)
    _clinic(m, layout)
    _alchemy(m, layout, contract)
    _yard(m, layout)
    # Three-wide, level roads link the fixed north entry to the fixed rear exit.
    for a, b in (((18, 0, 0), (20, 0, 10)), ((20, 0, 10), (22, 0, 19)),
                 ((18, 0, 18), (20, 0, 37)), ((20, 0, 17), (32, 0, 19)),
                 ((18, 0, 35), (30, 0, 37)), ((28, 0, 35), (30, 0, 42))):
        m.fill(a, b, PATH)
    # Retain the authored support mosaic at fixed anchors. This is a foundation
    # interface, never a mask for walls/obstacles or target/air conflicts.
    for pos, state in contract.protected:
        if pos[1] == 0:
            m.put(pos, state)
    for pos, state in contract.protected:
        if canonical(m.blocks.get(pos, AIR)) != state:
            raise ValueError(f'apothecary geometry conflicts with fixed story cell {pos}')
    builder = SiteBuilder(contract)
    builder.apply_ops({'op': 'set', 'pos': list(pos), 'state': state}
                      for pos, state in sorted(m.blocks.items()))
    # The graph is built from the same final geometry that finish exports.
    graph = walk_graph(m.blocks, contract.size)
    targets = contract.blueprint()['walk_targets']
    by_id = {space.id: space for space in spaces}
    edges = []
    for source, target, first, last in connections:
        allowed = (by_id[source], by_id[target])
        edges.append(Adjacency(source, target, _path(graph, tuple(targets[first]), tuple(targets[last]), allowed)))
    presentation = contract.blueprint()['presentation']
    presentation.update(summary=f'{layout.name}：高前店、独立诊室、挑廊井院与低丹房。',
                        spaces=[s.purpose for s in spaces],
                        stage='离线 seeded 剧情约束地点；原生碰撞、外观与玩家验收尚未验证。')
    return builder.finish(SemanticPlan(spaces, tuple(edges)), metadata={
        'description': f'长明药庐 · {layout.name}；浅砂岩深木灰瓦的西漠绿洲药庐。',
        'presentation': presentation, 'signs': m.signs,
        'roof_checks': m.roofs, 'wall_roof_joins': m.joins,
        'design_notes': ['先规划前店、问诊留观、药阁、井院、晾药与丹房，再生成方块。',
                         'seed 改变前店宽度、留观进深、挑廊宽度、丹房备料翼与晾药架方向。',
                         '固定剧情坐标、精确 state、三格宽前后道路与六级楼梯保持兼容。',
                         '主屋与挑廊交叠谷口保留；屋面检查覆盖谷口外完整截面。',
                         '此结果为离线几何；自然 worldgen、原生碰撞与玩家外观验收不在本实现范围。'],
    })
