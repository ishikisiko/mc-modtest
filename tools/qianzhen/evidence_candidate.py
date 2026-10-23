"""阡镇原生采集的显式候选、固定请求与云端启动边界（离线可检查）。"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from tools.qianzhen import story_assets as assets
from tools.qianzhen.build import source_identity
from tools.qianzhen.storygen import pipeline
from tools.qianzhen.storygen.core import CONTRACT_VERSION, SEED_ALGORITHM, derive_seed
from tools.qianzhen.storygen.pipeline import digest
from tools.combat_capture.datapack import file_hashes, verify

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_REQUEST = Path('/workspace/request.json')
PACK_NAME = 'qianzhen_candidate'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def relative_file(root, name):
    path = Path(name)
    if path.is_absolute() or not path.parts or any(p in ('.', '..') for p in path.parts):
        raise ValueError('Invalid candidate relative file: '+str(name))
    target = root/path
    if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):
        raise ValueError('Candidate file escapes root: '+str(name))
    return target


@dataclass(frozen=True)
class _VerifiedSnapshot:
    identity_json: str
    layout_json: str
    blueprints_json: str
    report_json: str


class Candidate:
    def __init__(self, folder=None, root=ROOT):
        self.root = Path(root).resolve()
        self.folder = Path(folder).resolve() if folder is not None else None
        self.source = self.folder/'source' if self.folder else self.root/'blueprints/qianzhen'
        self._verified = None
        self.pack = None
        self.pack_hashes = {}
        if self.folder:
            self.validate()
        elif (self.source/'generation-report.json').exists():
            raise ValueError('Seeded snapshot cannot be selected as default authored')

    # Keep verified data as immutable JSON, never shared mutable dictionaries.
    # Consumers still receive ordinary JSON-serializable dicts/lists, with every
    # nested value detached from the cache and from previous reads.
    @property
    def identity(self):
        return json.loads(self._verified.identity_json) if self._verified else {'mode': 'authored'}

    @property
    def layout(self):
        return json.loads(self._verified.layout_json) if self._verified else None

    @property
    def blueprints(self):
        return json.loads(self._verified.blueprints_json) if self._verified else None

    @property
    def report(self):
        return json.loads(self._verified.report_json) if self._verified else None

    def validate(self):
        report_path = self.folder/'generation-report.json'
        report = json.loads(report_path.read_text())
        if (self.source/'generation-report.json').read_bytes() != report_path.read_bytes():
            raise ValueError('Candidate generation-report copies differ')
        g = report['generation']
        manifest = json.loads((self.source/'manifest.json').read_text())
        sites = [r['id'] for r in manifest['locations']]
        if (report['status'] != 'passed' or g['mode'] != 'seeded_offline'
                or type(g['root_seed']) is not int or len(sites) != 6
                or len(set(sites)) != 6 or g['selected'] != sites
                or g['completed'] != sites or g['unrun'] or set(g['sites']) != set(sites)
                or g['props_materialized'] is not True):
            raise ValueError('Candidate must be one complete six-site seeded generation')
        authored_source = self.root/'blueprints/qianzhen'
        authored_version = json.loads((authored_source/'story_scene.json').read_text())['version']
        for key, expected in (
                ('algorithm_version', pipeline.ALGORITHM_VERSION),
                ('contract_version', CONTRACT_VERSION), ('seed_algorithm', SEED_ALGORITHM),
                ('authored_scene_version', authored_version),
                ('reproduce', pipeline.reproduction_command(g['root_seed']))):
            if g.get(key) != expected:
                raise ValueError('Candidate generation metadata mismatch: '+key)
        for site in sites:
            derived = g['sites'][site].get('derived_geometry_seed')
            if (type(derived) is not int
                    or derived != derive_seed(g['root_seed'], authored_version, site)):
                raise ValueError('Candidate root_seed/derived_geometry_seed mismatch: '+site)
        inputs = ['manifest.json', 'story_scene.json', *[r['source'] for r in manifest['locations']]]
        actual = {name: sha(relative_file(self.source, name)) for name in inputs}
        if actual != g['materialized_inputs']:
            raise ValueError('Candidate materialized source fingerprint mismatch')
        # Match build/capture provenance exactly, including README and the report.
        # The eight scene inputs remain separately comparable to generation metadata.
        export_inputs = source_identity(self.source)
        # Pin generator AND original authored inputs to this fixed checkout.
        for key, base, fingerprint in (
                ('generator_sources', self.root, 'generator_source_fingerprint'),
                ('authored_inputs', authored_source, 'authored_input_fingerprint')):
            if digest(g[key]) != g[fingerprint] or not g[key]:
                raise ValueError('Candidate '+key+' fingerprint mismatch')
            for name, expected in g[key].items():
                if sha(relative_file(base, name)) != expected:
                    raise ValueError('Candidate '+key+' drift: '+name)
        expected_names = {'data/myvillage/story/qianzhen_scene.json', *[
            'data/myvillage/structure/qianzhen/'+site+'.nbt' for site in sites]}
        if set(report['resource_sha256']) != expected_names:
            raise ValueError('Candidate resource set differs from six-site scene')
        for name, expected in report['resource_sha256'].items():
            if sha(relative_file(self.folder/'resources', name)) != expected:
                raise ValueError('Candidate resource fingerprint mismatch: '+name)
        identity = {'mode':'seeded_offline', 'root_seed':g['root_seed'],
            'scene_version':report['scene_version'], 'generation_report_sha256':sha(report_path),
            'source_sha256':actual, 'source_fingerprint':digest(actual),
            'export_source_sha256':export_inputs, 'export_source_fingerprint':digest(export_inputs),
            'resource_sha256':report['resource_sha256'],
            'resource_fingerprint':digest(report['resource_sha256']),
            'generator_source_fingerprint':g['generator_source_fingerprint']}
        identity_json = pipeline.canonical_json(identity)
        # Unchanged pinned bytes retain the already proven seed replay; startup and
        # restarts still rehash all source/resource files before this fast path.
        # Compare the ENTIRE current identity with the private verified snapshot,
        # including seed, version and aggregate hashes; public copies are no proof.
        if self._verified is not None and identity_json == self._verified.identity_json:
            return self.identity
        # A first binding must actually run all six plugins from the fixed authored
        # inputs and reported seed. Re-exporting candidate source alone cannot prove
        # seed provenance, even when every self-reported hash is consistent.
        with tempfile.TemporaryDirectory(prefix='qianzhen-candidate-replay-') as folder:
            replay = Path(folder)/'replay'
            replay_report = pipeline.generate(replay, g['root_seed'], authored_source)
            replay_source = source_identity(replay/'source')
            if set(export_inputs) != set(replay_source):
                raise ValueError('Candidate seed replay source file set mismatch')
            # Reports contain environment metadata; validate their generation claims
            # below rather than requiring Python-version-dependent report bytes.
            for name in replay_source.keys() - {'generation-report.json'}:
                if (replay/'source'/name).read_bytes() != (self.source/name).read_bytes():
                    raise ValueError('Candidate seed replay materialized source mismatch: '+name)
            for name in expected_names:
                if (replay/'resources'/name).read_bytes() != (self.folder/'resources'/name).read_bytes():
                    raise ValueError('Candidate seed replay resource mismatch: '+name)
            replay_generation = replay_report['generation']
            # Python version is a recorded environment fact, not an algorithm label.
            # Every deterministic generation claim must match the real replay,
            # including complete authored/generator file sets and site semantics.
            for key in (g.keys() | replay_generation.keys()) - {'python_version'}:
                if key not in g or key not in replay_generation or digest(g[key]) != digest(replay_generation[key]):
                    raise ValueError('Candidate seed replay generation metadata mismatch: '+key)
        layout, blueprints, assembled = assets.assemble(self.source)
        with tempfile.TemporaryDirectory(prefix='qianzhen-candidate-check-') as folder:
            output = Path(folder)
            assets.write_scene(output, layout, blueprints, assembled)
            if any(sha(output/name) != expected for name, expected in report['resource_sha256'].items()):
                raise ValueError('Candidate resources differ from materialized source/scene version')
        if layout['version'] != report['scene_version']:
            raise ValueError('Candidate scene version mismatch')
        self._verified = _VerifiedSnapshot(identity_json, pipeline.canonical_json(layout),
            pipeline.canonical_json(blueprints), pipeline.canonical_json(report))
        return self.identity

    def prepare_pack(self, out):
        if not self.folder:
            return {}
        self.validate()
        self.pack = Path(out).resolve()/'candidate-datapack'
        if self.pack.exists():
            verify(self.pack, self.pack_hashes)
        else:
            self.pack.mkdir()
            (self.pack/'pack.mcmeta').write_text(json.dumps({'pack':{
                'pack_format':48, 'description':'阡镇显式 seeded 原生验收候选'}}))
            for name in self.identity['resource_sha256']:
                target = self.pack/name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(self.folder/'resources'/name, target)
            self.pack_hashes = file_hashes(self.pack)
            shutil.copyfile(self.folder/'generation-report.json', Path(out)/'generation-report-used.json')
        return {'startup_datapack':str(self.pack), 'startup_datapack_hashes':self.pack_hashes.copy()}

    def template(self, data, default_export):
        if self.folder:
            return self.folder/'resources/data/myvillage/structure/qianzhen'/(data['presentation']['role']+'.nbt')
        return default_export/data['presentation']['role']/(data['id'].split(':')[1]+'.nbt')

    def verify_installed(self, world):
        if self.folder:
            verify(Path(world)/'datapacks'/PACK_NAME, self.pack_hashes)

    def verify_deployment(self, world, origin):
        if not self.folder:
            return None
        from tools.buildgen.nbtread import read_gzipped_nbt
        path = Path(world)/'data/myvillage_qianzhen_scene.dat'
        _, saved = read_gzipped_nbt(str(path))
        saved = saved['data']
        expected = {'schema':1, 'phase':'COMPLETE', 'id':self.layout['id'],
                    'version':self.identity['scene_version'], 'dimension':'minecraft:overworld',
                    **dict(zip(('x','y','z'), map(int, origin)))}
        if any(saved.get(k) != v for k, v in expected.items()):
            raise ValueError('Runtime deployed scene version/origin differs from candidate')
        return {'saved_data_sha256':sha(path), **expected}


def assert_cloud(root, provider, environ=None):
    env = os.environ if environ is None else environ
    root = Path(root).resolve()
    if provider == 'Modal' and root == Path('/workspace/repo'):
        return
    if (provider == 'Colab' and env.get('MYVILLAGE_CLOUD_PROVIDER') == 'Colab'
            and root.is_relative_to('/content') and root != Path('/content')):
        return
    raise RuntimeError('Minecraft evidence requires the Modal disposable /workspace/repo checkout or explicitly selected Colab /content/ checkout')


def fixed_git(root):
    head = subprocess.check_output(['git','rev-parse','HEAD'], cwd=root, text=True).strip()
    dirty = subprocess.check_output(['git','status','--porcelain','--untracked-files=no'], cwd=root, text=True)
    if dirty:
        raise ValueError('Fixed-source checkout has tracked modifications')
    return head


def runtime_request(root, request_path, provider, candidate):
    assert_cloud(root, provider)
    request = json.loads(Path(request_path).read_text())
    if not re.fullmatch('[0-9a-f]{40}', str(request.get('commit', ''))):
        raise ValueError('Acceptance request requires fixed full commit SHA')
    if request.get('provider', provider) != provider:
        raise ValueError('Acceptance provider mismatch')
    if 'repo_root' in request and Path(request['repo_root']).resolve() != Path(root).resolve():
        raise ValueError('Acceptance checkout path mismatch')
    if candidate.folder:
        if request.get('candidate') != candidate.validate():
            raise ValueError('Acceptance candidate fingerprint/seed/version mismatch')
        if fixed_git(root) != request['commit']:
            raise ValueError('Acceptance fixed commit mismatch')
    elif request.get('candidate', {'mode':'authored'}) != {'mode':'authored'}:
        raise ValueError('Seeded request cannot run default authored resources')
    elif provider == 'Colab' and fixed_git(root) != request['commit']:
        raise ValueError('Acceptance fixed commit mismatch')
    return request


def add_arguments(parser):
    parser.add_argument('--candidate', type=Path, help='显式 storygen 输出根目录；缺省仍 authored')
    parser.add_argument('--request', type=Path, default=DEFAULT_REQUEST, help='固定提交/候选请求，支持 Colab 实际路径')
    parser.add_argument('--provider', choices=('Modal','Colab'), default=os.environ.get('MYVILLAGE_CLOUD_PROVIDER','Modal'))


def main():
    parser = argparse.ArgumentParser(description='离线固定候选请求；不启动云端或 Minecraft')
    add_arguments(parser)
    args = parser.parse_args()
    candidate = Candidate(args.candidate)
    request = {'commit':fixed_git(ROOT), 'provider':args.provider, 'repo_root':str(ROOT),
               'candidate':candidate.identity}
    if args.request == DEFAULT_REQUEST:
        parser.error('写请求必须显式 --request；不写默认 Modal 请求')
    if args.request.exists():
        parser.error('保留已有请求；请选择新文件')
    args.request.parent.mkdir(parents=True, exist_ok=True)
    args.request.write_text(json.dumps(request, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'commit':request['commit'], 'candidate':candidate.identity, 'request':str(args.request)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
