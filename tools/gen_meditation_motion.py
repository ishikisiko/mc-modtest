#!/usr/bin/env python3
"""修炼动作唯一作者：规范化关节方向，输出客户端姿态资源。"""
import argparse
import json
import math
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/meditation_motion/pose.json'
OUTPUT = ROOT / 'src/main/resources/assets/myvillage/cultivation/meditation_motion.json'

def generate():
    data = json.loads(SOURCE.read_text())
    if data['schema'] != 1 or set(data['limbs']) != {'right_arm', 'left_arm', 'right_leg', 'left_leg'}:
        raise ValueError('未知修炼骨架')
    for name, limb in data['limbs'].items():
        for key in ('upper', 'lower'):
            v = limb[key]
            if len(v) != 3 or any(not math.isfinite(x) for x in v):
                raise ValueError(f'{name}.{key} 方向无效')
            length = math.sqrt(sum(x*x for x in v))
            if length < .01:
                raise ValueError('零长度骨骼')
            limb[key] = [round(x/length, 8) for x in v]
    for key in ('exit_ticks', 'breath_ticks'):
        if not 1 <= data[key] <= 200:
            raise ValueError('动作时长越界')
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output = generate()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != output:
            raise SystemExit('修炼动作资源不是最新生成结果')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(output)
    print('修炼动作资源校验通过')
if __name__ == '__main__':
    main()
