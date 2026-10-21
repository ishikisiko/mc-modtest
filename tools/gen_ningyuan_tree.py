#!/usr/bin/env python3
"""Generate/check the Ningyuan tree, wood family, fruit cycle and decorations."""
from pathlib import Path
import argparse
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.ningyuan_tree.assets import outputs

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');p.add_argument('--preview',action='store_true');args=p.parse_args()
    changed=[]
    for relative,content in outputs().items():
        path=ROOT/relative
        if not path.is_file() or path.read_bytes()!=content:
            changed.append(str(relative))
            if not args.check:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
    if args.check and changed:
        print('Stale Ningyuan resources:\n'+'\n'.join(changed));return 1
    print(f'Ningyuan resources: {"verified" if args.check else "generated"} ({len(changed)} changed)')
    if args.preview:
        from tools.ningyuan_tree.preview import main as preview
        preview()
    return 0
if __name__=='__main__':raise SystemExit(main())
