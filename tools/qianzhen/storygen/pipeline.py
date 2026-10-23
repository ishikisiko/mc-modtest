"""显式离线六地点生成、来源快照及现有资源/预览导出接线。"""
from __future__ import annotations

import argparse
from dataclasses import asdict
import hashlib
import json
from pathlib import Path
import platform
import shutil

from .. import story_assets as assets
from .core import (CONTRACT_VERSION, SEED_ALGORITHM, compose_scene, derive_seed,
                   generate_sites, load_contract)

ALGORITHM_VERSION = 'six-site-pipeline-v1'


def canonical_json(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def digest(value):
    return hashlib.sha256(canonical_json(value).encode('utf-8')).hexdigest()


def geometry_sha256(data):
    """展开后的真实格子身份，排除 seed、描述、语义标签等元数据。"""
    size, _, blocks, _ = assets.nbt.expand_structure(data)
    return digest({'size': size, 'blocks': [[*p, assets.canonical(s)] for p, s in sorted(blocks.items())]})


def prepare_scene(source: Path, seed: int):
    if type(seed) is not int:
        raise ValueError('seed must be an integer')
    source = Path(source)
    scene = load_contract(source)
    # There is deliberately no direct plugin import or fallback to authored here.
    generated = generate_sites(scene, seed, scene.site_ids)
    layout, blueprints, report = compose_scene(scene, generated)
    manifest = json.loads(scene.manifest_json)
    inputs = {name: assets.sha(source / name) for name in
              ['manifest.json', 'story_scene.json', *[r['source'] for r in manifest['locations']]]}
    tool_paths = [*sorted(Path(__file__).parent.glob('*.py')),
                  assets.ROOT / 'tools/qianzhen/story_assets.py',
                  assets.ROOT / 'tools/qianzhen/build.py',
                  assets.ROOT / 'tools/json_to_nbt.py',
                  assets.ROOT / 'tools/buildgen/grid.py',
                  assets.ROOT / 'tools/buildgen/nbtread.py',
                  assets.ROOT / 'tools/yunshan/build_inn.py']
    sources = {str(p.relative_to(assets.ROOT)): assets.sha(p) for p in tool_paths}
    report['generation'] = {
        'mode': 'seeded_offline', 'root_seed': seed,
        'algorithm_version': ALGORITHM_VERSION, 'contract_version': CONTRACT_VERSION,
        'seed_algorithm': SEED_ALGORITHM,
        'authored_scene_version': json.loads(scene.config_json)['version'],
        'python_version': platform.python_version(),
        'authored_inputs': inputs, 'authored_input_fingerprint': digest(inputs),
        'materialized_inputs': layout['inputs'],
        'generator_sources': sources, 'generator_source_fingerprint': digest(sources),
        'props_materialized': True,
        'selected': list(scene.site_ids), 'completed': list(generated), 'unrun': [],
        'sites': {site: {
            'generator': f'tools/qianzhen/storygen/{site}.py',
            'source_sha256': sources[f'tools/qianzhen/storygen/{site}.py'],
            'derived_geometry_seed': derive_seed(seed, scene.site(site).scene_version, site),
            'geometry_sha256': geometry_sha256(blueprints[site]),
            'authored_geometry_sha256': geometry_sha256(scene.site(site).blueprint()),
            'semantics': asdict(generated[site].semantics),
        } for site in scene.site_ids},
        'reproduce': ['python3', '-m', 'tools.qianzhen.storygen', '--seed', str(seed),
                      '--source', '<authored-source-with-recorded-hashes>', '--out', '<new-output-directory>'],
    }
    return layout, blueprints, report


def write_snapshot(out, source, blueprints):
    """可直接交给 build.py / assemble；props 已物化，绝不能再应用原 props。"""
    manifest = json.loads((source / 'manifest.json').read_text())
    config = json.loads((source / 'story_scene.json').read_text())
    config['props'] = []
    out.mkdir(parents=True, exist_ok=True)
    for name, value in [('manifest.json', manifest), ('story_scene.json', config)]:
        (out / name).write_text(canonical_json(value), encoding='utf-8')
    for row in manifest['locations']:
        path = out / row['source']
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(canonical_json(blueprints[row['id']]), encoding='utf-8')
    shutil.copyfile(source / 'README.md', out / 'README.md')


def generate(out: Path, seed: int, source: Path = assets.SOURCE):
    out, source = Path(out).resolve(), Path(source).resolve()
    if out == source or out in source.parents or source in out.parents:
        raise ValueError('output and authored source directories must be separate')
    layout, blueprints, report = prepare_scene(source, seed)
    report = assets.write_scene(out / 'resources', layout, blueprints, report)
    write_snapshot(out / 'source', source, blueprints)
    # Verify the persisted source really is the exact compose_scene input.
    for name, expected in layout['inputs'].items():
        if assets.sha(out / 'source' / name) != expected:
            raise ValueError('materialized source hash differs: ' + name)
    path = out / 'generation-report.json'
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    shutil.copyfile(path, out / 'source/generation-report.json')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seed', type=int, required=True, help='同一场景根种子，显式生成全部六处')
    parser.add_argument('--out', type=Path, required=True, help='独立离线产物目录；不写默认生产目录')
    parser.add_argument('--source', type=Path, default=assets.SOURCE, help='原 authored 事实源目录')
    args = parser.parse_args()
    report = generate(args.out, args.seed, args.source)
    print(json.dumps({'status': report['status'], 'scene_version': report['scene_version'],
                      'seed': args.seed, 'completed': report['generation']['completed']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
