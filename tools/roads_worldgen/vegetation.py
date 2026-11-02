"""Read-only saved-world check for the observed floating oak component."""
from collections import deque
from pathlib import Path

from yunshan.natural_terrain import read_region, decode_section, section_state


def wood_components(world, starts):
    world = Path(world)
    regions, sections, visited, result = {}, {}, set(), []

    def block(x, y, z):
        region = (x // 512, z // 512)
        if region not in regions:
            path = world / 'region' / f'r.{region[0]}.{region[1]}.mca'
            regions[region] = read_region(path)[0] if path.exists() else {}
        chunk = regions[region].get(((x // 16) % 32, (z // 16) % 32))
        if chunk is None:
            return None
        key = (x // 16, y // 16, z // 16)
        if key not in sections:
            section = next((s for s in chunk.get('sections', []) if int(s['Y']) == y // 16), {})
            sections[key] = decode_section(section)
        return section_state(sections[key], x % 16, y % 16, z % 16).split('[')[0]

    def wood(state):
        return state is not None and state.endswith(('_log', '_wood'))

    ground = {'minecraft:' + name for name in ['dirt', 'grass_block', 'podzol', 'coarse_dirt',
              'rooted_dirt', 'mud', 'moss_block', 'snow_block', 'stone', 'sand', 'gravel', 'mycelium', 'clay']}
    adjacent = [(x, y, z) for x in [-1, 0, 1] for y in [-1, 0, 1] for z in [-1, 0, 1] if (x, y, z) != (0, 0, 0)]
    for start in starts:
        if start in visited:
            continue
        initial = block(*start)
        if initial is None:
            raise ValueError(f'Observed tree chunk is missing: {start}')
        if not wood(initial):
            continue
        queue, component, supported, unknown = deque([start]), set(), False, False
        while queue and len(component) < 2048:
            at = queue.popleft()
            if at in component:
                continue
            component.add(at)
            x, y, z = at
            supported |= block(x, y - 1, z) in ground
            for dx, dy, dz in adjacent:
                neighbor = (x + dx, y + dy, z + dz)
                state = block(*neighbor)
                if state is None:
                    unknown = True
                elif wood(state) and neighbor not in component:
                    queue.append(neighbor)
        visited.update(component)
        result.append(dict(logs=sorted(component), supported=supported, unknown=unknown or bool(queue)))
    return result
