"""Generate the selected weapon models and their forging and combat resources."""
import argparse
from pathlib import Path
import sys

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tools.forged_arms.assets import outputs

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    stale=[]
    for path,data in outputs().items():
        if not path.exists() or path.read_bytes()!=data:
            stale.append(path)
            if not args.check:
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_bytes(data)
    if args.check and stale:
        raise SystemExit('枪剑资源有差异：'+'\n'.join(map(str,stale[:15])))
    print('枪剑资源校验完成，变化文件：',len(stale))

if __name__=='__main__':
    main()
