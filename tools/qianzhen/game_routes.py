"""Pure route planning for native Qianzhen movement; never moves a player."""
from collections import deque
from .build import block_at


def open_door_panel(blocks, node):
    name, p = block_at(blocks, node)
    if not name.endswith('_door'):
        return None
    # Minecraft 1.21.1 DoorBlock.getShape: the opened panel is 3/16 thick.
    # Axis 0 = x, axis 2 = z; sign identifies the occupied cell boundary.
    panels = {'south': (0, 1), 'north': (0, -1),
              'west': (2, 1), 'east': (2, -1)}
    axis, side = panels[p['facing']]
    return axis, -side if p['hinge'] == 'right' else side


def opened_door_graph(graph, blocks):
    """Exclude edges across the occupied side of an already opened door."""
    result = {}
    for node, neighbors in graph.items():
        allowed = []
        for other in neighbors:
            first, second = open_door_panel(blocks, node), open_door_panel(blocks, other)
            if first and other[first[0]]-node[first[0]] == first[1]:
                continue
            if second and node[second[0]]-other[second[0]] == second[1]:
                continue
            allowed.append(other)
        result[node] = allowed
    return result


def walk_point(node, blocks):
    """Center on the usable opening, including its approach and exit cells."""
    point = [node[0]+.5, node[1], node[2]+.5]
    panel = open_door_panel(blocks, node)
    if panel is None:
        for axis in (0, 2):
            for sign in (-1, 1):
                adjacent = list(node)
                adjacent[axis] += sign
                candidate = open_door_panel(blocks, adjacent)
                if candidate and candidate[0] != axis:
                    panel = candidate
                    break
            if panel is not None:
                break
    if panel:
        point[panel[0]] -= panel[1]*3/32
    return point


def traversal_ends(path, blocks):
    corners = set(straight_ends(path))
    return [node for node in path[1:] if node in corners
            or walk_point(node, blocks) != [node[0]+.5, node[1], node[2]+.5]]


def nearest_path(graph, start, destinations):
    start = tuple(start)
    parents = {start: None}
    pending = deque([start])
    while pending:
        node = pending.popleft()
        if node in destinations:
            path = []
            while node is not None:
                path.append(node)
                node = parents[node]
            return path[::-1]
        for other in graph[node]:
            if other not in parents:
                parents[other] = node
                pending.append(other)
    raise ValueError('No route to remaining targets')


def tour(graph, targets):
    current = tuple(targets['entry'])
    remaining = {name: tuple(pos) for name, pos in targets.items() if name != 'entry'}
    result = []
    while remaining:
        path = nearest_path(graph, current, set(remaining.values()))
        current = path[-1]
        names = sorted(name for name, pos in remaining.items() if pos == current)
        result.append({'targets': names, 'path': path})
        for name in names:
            del remaining[name]
    result.append({'targets': ['return_entry'],
                   'path': nearest_path(graph, current, {tuple(targets['entry'])})})
    return result


def straight_ends(path):
    """Keep every horizontal turn; continuous stairs remain one straight leg."""
    if len(path) < 2:
        return []
    ends = []
    last = None
    for before, after in zip(path, path[1:]):
        direction = (after[0]-before[0], after[2]-before[2])
        if sum(abs(v) for v in direction) != 1:
            raise ValueError('Route contains a non-cardinal step')
        if last is not None and direction != last:
            ends.append(before)
        last = direction
    ends.append(path[-1])
    return ends


def movement_key(axis, difference):
    # The fixture faces south (yaw 0); left is east, right is west.
    return ('a' if difference > 0 else 'd') if axis == 0 else ('w' if difference > 0 else 's')
