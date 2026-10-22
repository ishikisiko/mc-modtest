#!/usr/bin/env python3
"""Generate/check authored skill-bar sprites, separate from weapon geometry and item textures."""
from pathlib import Path
import argparse
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.advanced_arts.icons import ICONS, icon

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args()
    import json
    ids={x['id'].split(':')[1] for x in json.loads((ROOT/'tools/advanced_arts/arts.json').read_text())['arts']}
    if ids!=set(ICONS):raise ValueError('Every current art must have one distinct authored icon')
    changed=[]
    for name,kind in ICONS.items():
        path=ROOT/f'src/main/resources/assets/myvillage/textures/gui/advanced_arts/{name}.png'
        data=icon(kind)
        if not path.exists() or path.read_bytes()!=data:
            changed.append(name)
            if not a.check:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print('Advanced art icons:', 'stale '+','.join(changed) if a.check and changed else 'verified' if a.check else 'generated')
    return int(a.check and bool(changed))
if __name__=='__main__':raise SystemExit(main())
