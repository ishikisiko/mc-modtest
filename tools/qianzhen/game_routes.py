"""Pure route planning for native Qianzhen movement; never moves a player."""
from collections import deque


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
