"""检查预览几何中的连续站位；不替代 Minecraft 原生碰撞与输入验收。"""
from __future__ import annotations

from collections import defaultdict
import json
import math
from pathlib import Path

from .model import read


def route_audit(model, catalog=None):
    rules = (catalog or read('catalog'))['rules']
    radius, height = rules['player_radius'], rules['player_height']
    epsilon = rules['standing_epsilon']
    cell = 8
    buckets = defaultdict(list)
    for b in model['boxes']:
        if b[8] or b[7] in ('water', 'encounter_actor', 'scale_actor'):
            continue
        for xx in range(math.floor(b[0] / cell), math.floor((b[0] + b[3]) / cell) + 1):
            for zz in range(math.floor(b[2] / cell), math.floor((b[2] + b[5]) / cell) + 1):
                buckets[xx, zz].append(b)
    failures = []
    checked = 0
    for edge in model['routes']:
        previous = None
        for a, b in zip(edge['points'], edge['points'][1:]):
            steps = max(1, math.ceil(math.hypot(b[0] - a[0], b[2] - a[2]) / rules['route_sampling_step']))
            for j in range(steps + 1):
                x, nominal, z = (a[i] + (b[i] - a[i]) * j / steps for i in range(3))
                nearby = {id(box): box for ix in range(math.floor((x - radius) / cell), math.floor((x + radius) / cell) + 1)
                          for iz in range(math.floor((z - radius) / cell), math.floor((z + radius) / cell) + 1)
                          for box in buckets[ix, iz]}
                overlap = [box for box in nearby.values() if box[0] < x + radius and box[0] + box[3] > x - radius
                           and box[2] < z + radius and box[2] + box[5] > z - radius]
                supports = [box[1] + box[4] for box in overlap
                            if abs(box[1] + box[4] - nominal) <= rules['route_support_window']]
                checked += 1
                if not supports:
                    failures.append(dict(edge=[edge['a'], edge['b']],pos=[round(x,2),round(nominal,2),round(z,2)],reason='没有足下支撑'))
                    continue
                feet = max(supports)
                blocking = sorted({box[7] for box in overlap
                                   if box[1] < feet + height and box[1] + box[4] > feet + epsilon})
                reason = '站立体积被占用' if blocking else '台阶高度超过步行上限' if previous is not None and abs(feet - previous) > rules['walk_step_height'] else None
                if reason:
                    failures.append(dict(edge=[edge['a'],edge['b']],pos=[round(x,2),round(feet,2),round(z,2)],reason=reason,layers=blocking))
                previous = feet
    return dict(scope='offline_preview_geometry',samples=checked,failed=len(failures),failures=failures,
                minecraft_collision='not_verified')


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args()
    index=json.loads((args.out/'index.json').read_text())
    result={item['id']:route_audit(json.loads((args.out/(item['id']+'.json')).read_text())) for item in index['maps']}
    (args.out/'route-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    for key,value in result.items():
        print(key,'检查',value['samples'],'处，失败',value['failed'])
        for failure in value['failures'][:8]:print(failure)
    if any(v['failed'] for v in result.values()):raise SystemExit(1)


if __name__=='__main__':main()
