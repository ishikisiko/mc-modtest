"""从正式城镇蓝图的公共门洞生成首版驿道出口；不修改城市施工资源。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'config/roads/prototype.json'
OUTPUT = ROOT / 'src/main/resources/data/myvillage/roads/prototype.json'


def generate(root: Path = ROOT) -> str:
    source = root / 'config/roads/prototype.json'
    config = json.loads(source.read_text())
    width = config['width']
    if width < 3 or width > 9 or width % 2 != 1:
        raise ValueError('驿道路幅必须为 3–9 的奇数')
    ports = []
    for binding in config['ports']:
        path = root / binding['layout']
        layout = json.loads(path.read_text())
        matches = [p for p in layout['public_passages'] if p['id'] == binding['passage']]
        if len(matches) != 1 or binding['facing'] != 'south':
            raise ValueError('首版仅接受唯一的、正式声明的南向公共门洞')
        passage = matches[0]
        gate_width = binding['port_width']
        if gate_width < 3 or gate_width > width or gate_width % 2 != 1:
            raise ValueError('门洞净宽必须显式声明，且不能超过城外路幅')
        x0, z0, x1, z1 = passage['bounds']
        dimensions = layout['dimensions']
        x = (x0 + x1) // 2
        if x - gate_width // 2 < x0 or x + gate_width // 2 >= x1 or passage['height'] < config['clearance']:
            raise ValueError('公共门洞不能容纳完整路幅和净空')
        if layout['city_id'] != 'capital_' + binding['region'] or not 0 <= z0 < z1 <= dimensions[2]:
            raise ValueError('出口必须属于绑定城市的本地坐标')
        ports.append({
            'region': binding['region'], 'city': layout['city_id'], 'passage': passage['id'],
            'x': x, 'z': z0, 'feet_y': passage['y'], 'facing': binding['facing'],
            'bounds': passage['bounds'], 'dimensions': dimensions,
            'port_width': gate_width,
            'layout': binding['layout'], 'layout_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        })
    if len({p['region'] for p in ports}) != len(ports):
        raise ValueError('出口绑定重复')
    config['ports'] = ports
    config['source_sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
    return json.dumps(config, ensure_ascii=False, indent=2) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    text = generate()
    if args.check:
        if not OUTPUT.is_file() or OUTPUT.read_text() != text:
            raise SystemExit('驿道出口资源已过期：运行 python3 tools/gen_road_ports.py')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(text)
    data = json.loads(text)
    gates = '；'.join(f"{p['region']} 门洞 {p['port_width']} 格" for p in data['ports'])
    print(f"城外驿道 {data['width']} 格；{gates}，分别检查完整路幅")


if __name__ == '__main__':
    main()
