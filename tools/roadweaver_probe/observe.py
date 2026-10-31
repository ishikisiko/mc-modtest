"""对本实验已生成的世界补采主世界画面；不重新请求道路或运行几何采集。"""
import argparse
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from roadweaver_probe.capture import capture
from roadweaver_probe.run import ExperimentServer, terminate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--runtime-jar', type=Path, required=True)
    parser.add_argument('--source', required=True)
    args = parser.parse_args()
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=False)
    for name in ['probe.json', 'columns.json', 'roads.json', 'audit.json']:
        shutil.copy2(args.evidence/name, args.out/name)
    mode = json.loads((args.evidence/'probe.json').read_text())['mode']
    with ExperimentServer(args.out, mode, args.jar.resolve(), Path(__file__).with_name('experiment.json'),
                          args.source, args.runtime_jar.resolve(), args.game) as server:
        result = capture(server, ROOT, terminate)
    if result['status'] != 'captured_for_review':
        raise SystemExit('Observation failed; see capture.json')


if __name__ == '__main__':
    main()
