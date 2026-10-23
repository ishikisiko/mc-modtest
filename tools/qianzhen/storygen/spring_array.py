"""护泉古阵：保留剧情剖面，按种子重建亭廊与地下巡检支室。

下层泉池、照壁、双阶及岩肩沿用已校验的 authored 构造；上层屋面、
柱梁和维护附属空间重新生成。不是把材质随机化后返回原蓝图。
"""
from __future__ import annotations

from collections import deque

from . import Adjacency, GenerationContext, SemanticPlan, SemanticSpace, SiteBuilder, SiteResult
from ..build import walk_graph

SITE_ID = 'spring_array'


def _path(graph, start, end, allowed=lambda p: True):
    """从实际构造中取确定性的步行证据；找不到时明确失败。"""
    start, end = tuple(start), tuple(end)
    if start not in graph or end not in graph or not allowed(start) or not allowed(end):
        raise ValueError('spring_array: route endpoint is not walkable')
    previous, todo = {start: None}, deque([start])
    while todo:
        current = todo.popleft()
        if current == end:
            route = []
            while current is not None:
                route.append(current)
                current = previous[current]
            return tuple(reversed(route))
        for neighbor in graph[current]:
            if neighbor not in previous and allowed(neighbor):
                previous[neighbor] = current
                todo.append(neighbor)
    raise ValueError(f'spring_array: designed route disconnected: {start} -> {end}')


def generate(context: GenerationContext) -> SiteResult:
    contract = context.contract
    if contract.site != SITE_ID:
        raise ValueError('spring_array generator needs its own site contract')
    rng = context.rng('massing')
    # 真实空间先定尺寸；固定的脚部坐标不跟随体量移动。
    pavilion_width = rng.choice((13, 17, 19))
    pavilion_end = rng.choice((41, 42, 43))
    pavilion_eave = rng.choice((17, 18))
    west_end = rng.choice((26, 28, 30))
    east_end = rng.choice((26, 28, 30))
    annex_start = rng.choice((25, 27, 29))
    annex_end = annex_start + 5
    px0, px1 = 23 - pavilion_width // 2, 23 + pavilion_width // 2

    spaces = [
        SemanticSpace('gate', '门亭、入口石阶与照壁前平台', (16, 0, 0), (30, 16, 14),
                      ('entry', 'gate_landing')),
        SemanticSpace('west_screen', '照壁西侧绕行平台', (8, 7, 8), (22, 14, 16), ('screen_west',)),
        SemanticSpace('east_screen', '照壁东侧绕行平台', (24, 7, 8), (38, 14, 16), ('screen_east',)),
        SemanticSpace('west_descent', '西侧三宽下沉阶道', (8, 2, 15), (11, 10, 24),
                      ('west_descent_top', 'west_descent_foot')),
        SemanticSpace('east_descent', '东侧三宽下沉阶道', (35, 2, 15), (38, 10, 24),
                      ('east_descent_top', 'east_descent_foot')),
        SemanticSpace('west_gallery', '西环廊、柱列屋面与后山升阶接驳', (2, 7, 15), (12, 16, 35),
                      ('west_descent_top',)),
        SemanticSpace('east_gallery', '东环廊及地下支室顶板', (35, 7, 15), (45, 16, 35),
                      ('east_descent_top',)),
        SemanticSpace('spring_court', '下沉阵庭、东西桥与封闭泉池', (8, 1, 21), (38, 7, 35),
                      ('west_array_foot', 'east_array_foot', 'array_core')),
        SemanticSpace('spring_eye', '八方封泉环与北偏泉口', (17, 1, 23), (29, 6, 33), ('array_core',)),
        SemanticSpace('old_text', '西侧低廊的旧阵铭石壁', (11, 2, 30), (16, 7, 35), ('old_inscription',)),
        SemanticSpace('fresh_branch', '新刻铜痕与维护支线', (30, 2, 29), (35, 7, 34), ('fresh_cut',)),
        SemanticSpace('maintenance', '环廊下维护长室及三宽地下巡检支室', (34, 2, 24), (45, 7, 35),
                      ('maintenance_entry', 'maintenance_bench')),
        SemanticSpace('west_upper', '西山肩升阶和上层巡泉平台', (2, 7, 30), (13, 22, 43), ('upper_west',)),
        SemanticSpace('east_upper', '东山肩升阶和上层巡泉平台', (34, 7, 30), (46, 26, 43), ('upper_east',)),
        SemanticSpace('overlook', '后方观阵亭与祭台，保持两端高廊连通', (12, 12, 34), (34, 24, 43),
                      ('upper_overlook', 'rear_altar')),
    ]
    for direction, name, x, z in (('nw', '西北', 15, 22), ('ne', '东北', 31, 22),
                                  ('sw', '西南', 15, 34), ('se', '东南', 31, 34)):
        spaces.append(SemanticSpace('foot_' + direction, name + '阵脚近身巡检台',
                                    (x - 2, 2, z - 1), (x + 2, 7, z + 1), ('array_foot_' + direction,)))

    builder = SiteBuilder(contract, from_authored=True)
    ops = []

    def fill(lo, hi, state):
        ops.append({'op': 'fill', 'from': list(lo), 'to': list(hi), 'state': state})

    def set_block(pos, state):
        ops.append({'op': 'set', 'pos': list(pos), 'state': state})

    def roof(x0, x1, z0, z1, eave):
        # 成对叠起的瓦坡，边檐抬高；每个截面完整连续。
        for x in range(x0, x1 + 1):
            inset = min(x - x0, x1 - x)
            y = eave + inset // 2
            fill((x, y, z0), (x, y, z1), 'minecraft:deepslate_tiles')
        for z in (z0, z1):
            for x in (x0, x1):
                set_block((x, eave + 1, z), 'minecraft:deepslate_tiles')
        return {'axis': 'x', 'min_y': eave, 'x_range': [x0, x1], 'z_range': [z0, z1]}

    # 完整移除旧亭廊的柱梁和屋面，再按选定跨度重建；不在旧顶上叠假体量。
    fill((12, 13, 34), (34, 26, 43), 'minecraft:air')
    for x0, x1 in ((2, 8), (38, 44)):
        fill((x0, 8, 15), (x1, 17, 30), 'minecraft:air')

    roofs = [contract.blueprint()['roof_checks'][0]]  # 门亭未改动。
    for x0, x1, end in ((2, 8, west_end), (38, 44, east_end)):
        for x in (x0 + 1, x1 - 1):
            for z in (16, (16 + end - 1) // 2, end - 1):
                fill((x, 8, z), (x, 11, z), 'minecraft:dark_oak_log[axis=y]')
            fill((x, 11, 16), (x, 11, end - 1), 'minecraft:dark_oak_log[axis=z]')
        for z in (16, end - 1):
            fill((x0 + 1, 11, z), (x1 - 1, 11, z), 'minecraft:dark_oak_log[axis=x]')
        roofs.append(roof(x0, x1, 15, end, 12))

    for x in (px0 + 1, px1 - 1):
        for z in (35, pavilion_end - 1):
            fill((x, 13, z), (x, pavilion_eave - 1, z), 'minecraft:dark_oak_log[axis=y]')
        fill((x, pavilion_eave - 1, 35), (x, pavilion_eave - 1, pavilion_end - 1),
             'minecraft:dark_oak_log[axis=z]')
    for z in (35, pavilion_end - 1):
        fill((px0 + 1, pavilion_eave - 1, z), (px1 - 1, pavilion_eave - 1, z),
             'minecraft:dark_oak_log[axis=x]')
    roofs.append(roof(px0, px1, 34, pavilion_end, pavilion_eave))

    # 支室从维护长室东墙开三宽口；既有守阵人/工作台一带不动。
    # 地坪 y=2，净空 y=3..6，封闭顶板 y=7 同时承托环廊。
    fill((41, 2, annex_start), (45, 7, annex_end), 'minecraft:smooth_sandstone')
    fill((42, 2, annex_start + 1), (44, 2, annex_end - 1), 'minecraft:stone_bricks')
    fill((42, 3, annex_start + 1), (44, 6, annex_end - 1), 'minecraft:air')
    fill((41, 3, annex_start + 1), (41, 5, annex_start + 3), 'minecraft:air')
    # 实际维修台与空容器靠外墙，三宽室内纵向通行仍有两宽余量。
    set_block((44, 3, annex_end - 1), 'minecraft:crafting_table')
    set_block((44, 3, annex_start + 1), 'minecraft:barrel[facing=west,open=false]')
    builder.apply_ops(ops)

    graph = walk_graph({pos: cell.state for pos, cell in builder.grid.iter_cells()}, contract.size)
    targets = contract.blueprint()['walk_targets']
    edges = []

    def connect(source, target, a, b, allowed=lambda p: True):
        edges.append(Adjacency(source, target, _path(graph, targets[a], targets[b], allowed)))

    for side, low, high in (('west', 2, 12), ('east', 34, 44)):
        connect('gate', side + '_screen', 'gate_landing', 'screen_' + side)
        connect(side + '_screen', side + '_descent', 'screen_' + side, side + '_descent_top')
        connect(side + '_descent', 'spring_court', side + '_descent_top', side + '_descent_foot',
                lambda p, lo=low, hi=high: lo <= p[0] <= hi and 15 <= p[2] <= 24)
        gallery_entry = (5 if side == 'west' else 41, 8, 16)
        edges.append(Adjacency(side + '_descent', side + '_gallery',
                               _path(graph, targets[side + '_descent_top'], gallery_entry)))
        # 各自从下沉阶顶走侧廊到后山；证据不能绕行另一翼。
        connect(side + '_gallery', side + '_upper', side + '_descent_top', 'upper_' + side,
                lambda p, lo=low, hi=high: lo <= p[0] <= hi and p[1] >= 8)
        connect(side + '_upper', 'overlook', 'upper_' + side, 'upper_overlook', lambda p: p[1] == 13)
    connect('spring_court', 'spring_eye', 'west_array_foot', 'array_core', lambda p: p[1] == 3)
    connect('spring_court', 'old_text', 'west_array_foot', 'old_inscription', lambda p: p[1] == 3)
    connect('spring_court', 'fresh_branch', 'east_array_foot', 'fresh_cut', lambda p: p[1] == 3)
    connect('fresh_branch', 'maintenance', 'fresh_cut', 'maintenance_bench', lambda p: p[1] == 3)
    for direction in ('nw', 'ne', 'sw', 'se'):
        connect('spring_eye', 'foot_' + direction, 'array_core', 'array_foot_' + direction, lambda p: p[1] == 3)
    # 支室无新增剧情站位；用真实门口和室内地面单独证明附属空间可进入。
    edges.append(Adjacency('maintenance', 'east_descent',
                           _path(graph, (43, 3, annex_start + 2), targets['east_descent_foot'],
                                 lambda p: p[1] == 3 and p[0] >= 33)))

    presentation = contract.blueprint()['presentation']
    presentation.update({
        'summary': '照壁后双阶下探泉庭，环廊下藏巡检支室，山肩高廊托起变跨观阵亭。',
        'stage': '离线 seeded 剧情约束生成；原生碰撞、运行时与玩家外观验收 not_verified。',
        'spaces': [space.purpose for space in spaces],
    })
    return builder.finish(SemanticPlan(tuple(spaces), tuple(edges)), metadata={
        'roof_checks': roofs,
        'presentation': presentation,
        'design_notes': [
            '剧情身份、完整 state、脚部与 approach 均由公共契约保护；不接入自然 worldgen。',
            '固定剖面：入口 y=1、环廊 y=8、泉庭与维护室 y=3、后山高廊 y=13。',
            f'观阵亭宽 {pavilion_width}、后檐 z={pavilion_end}、檐高 y={pavilion_eave}；'
            f'西/东侧廊分别止于 z={west_end}/{east_end}，其余露天巡泉道连续。',
            f'地下支室位于 x=41..45、z={annex_start}..{annex_end}；'
            '三宽开口连接维护长室，完整顶板承托上方巡泉廊。',
            '下层池岸、泉眼、四阵脚、入口照壁、双阶与山肩沿用 authored 构造；'
            '上层亭廊完整重建，支室为有墙、地坪、顶板的设计空间。',
        ],
    })
