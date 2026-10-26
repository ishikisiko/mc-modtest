"""Package byte-identical NPC payloads once, retaining every original resource path as a reference.

The normal npcgen outputs and appearance goldens remain full, unchanged authoring resources.
Only a dedicated build directory receives the compact runtime representation.
"""
from pathlib import Path
from collections import defaultdict
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
CONFIG = Path(__file__).with_name('packing.json')
PREFIX = Path('assets/myvillage/npc')


def compile_resources(source: Path, config: dict):
    if config['schema'] != 1 or set(config['kinds']) != {'model', 'animations'} or config['minimum_copies'] < 2:
        raise ValueError('Invalid NPC packaging configuration')
    groups = defaultdict(list)
    for kind in config['kinds']:
        for path in sorted(source.glob(f'*_{kind}.json')):
            data = path.read_bytes()
            if 'resource_ref' in json.loads(data):
                raise ValueError(f'Authoring resource must be complete: {path}')
            groups[(kind, hashlib.sha256(data).hexdigest())].append((path.name, data))
    result = {}
    aliases = 0
    for (kind, digest), files in sorted(groups.items()):
        data = files[0][1]
        if any(other != data for _, other in files):
            raise ValueError('Digest collision between different NPC payloads')
        if len(files) >= config['minimum_copies']:
            shared = f'shared/{digest}_{kind}.json'
            result[PREFIX / shared] = data
            ref = (json.dumps({'resource_ref': f'myvillage:npc/{shared}'}, separators=(',', ':'))+'\n').encode()
            for name, _ in files:
                result[PREFIX / name] = ref
                aliases += 1
        else:
            result[PREFIX / files[0][0]] = data
    return result, {'source_files': sum(len(v) for v in groups.values()), 'reference_files': aliases,
                    'shared_payloads': sum(len(v) >= config['minimum_copies'] for v in groups.values()),
                    'source_bytes': sum(len(data) for v in groups.values() for _, data in v),
                    'runtime_bytes': sum(map(len, result.values()))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to((ROOT/'build').resolve()):
        parser.error('NPC packaging output must be a dedicated directory under build/')
    result, report = compile_resources(ROOT/'src/main/resources'/PREFIX, json.loads(CONFIG.read_text()))
    if args.check:
        actual = {p.relative_to(output): p.read_bytes() for p in (output/PREFIX).rglob('*.json')}
        if actual != result:
            raise SystemExit('Stale packaged NPC resources')
    else:
        target = output/PREFIX
        if target.exists(): shutil.rmtree(target)
        for relative, data in result.items():
            p = output/relative; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
    print(json.dumps(report))


if __name__ == '__main__':
    main()
