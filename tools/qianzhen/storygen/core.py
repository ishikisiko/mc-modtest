"""共享契约：现有剧情事实 → 受保护局部几何 → 现有蓝图/场景校验。"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import importlib
import json
from pathlib import Path
import random
import re
import tempfile
from typing import Iterable, Mapping

from .. import story_assets as assets
from ..build import check_walks, validate, walk_graph
from buildgen.grid import BlockGrid, PRIORITY

Pos = tuple[int, int, int]
CONTRACT_VERSION = 'protected-story-v1'
SEED_ALGORITHM = 'qianzhen.storygen.v1'


def _json(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def _pos(value, size, label):
    # The older DSL coerces floats/strings; the plugin contract must not truncate anchors.
    if len(value) != 3 or any(type(v) is not int for v in value):
        raise ValueError(f'{label}: coordinates must be three integers')
    return assets.nbt.validate_pos(list(value), list(size), label)


def derive_seed(seed: int, scene_version: str, site: str, stream: str = 'geometry') -> int:
    """SHA-256 域分离；不使用进程 hash、共享 Random 或全场景内容指纹。"""
    if type(seed) is not int or not all(isinstance(v, str) and v for v in (scene_version, site, stream)):
        raise ValueError('seed must be an integer; version/site/stream must be nonempty strings')
    payload = [SEED_ALGORITHM, seed, scene_version, site, stream]
    return int.from_bytes(hashlib.sha256(_json(payload).encode('utf-8')).digest(), 'big')


@dataclass(frozen=True)
class SemanticSpace:
    """闭区间局部体积；walk_targets 引用现有蓝图键，不创建剧情事实。"""
    id: str
    purpose: str
    minimum: Pos
    maximum: Pos
    walk_targets: tuple[str, ...]

    def contains(self, pos):
        return all(self.minimum[i] <= pos[i] <= self.maximum[i] for i in range(3))


@dataclass(frozen=True)
class Adjacency:
    """两语义空间间实际可步行的路径，含两端，可跨台阶；不是距离近似。"""
    source: str
    target: str
    path: tuple[Pos, ...]


@dataclass(frozen=True)
class SemanticPlan:
    spaces: tuple[SemanticSpace, ...]
    adjacencies: tuple[Adjacency, ...] = ()


@dataclass(frozen=True)
class SiteContract:
    site: str
    scene_version: str
    origin: Pos
    size: Pos
    max_size: Pos
    # JSON snapshots avoid mutable dictionaries leaking between independent plugins.
    blueprint_json: str
    roles_json: str
    targets_json: str
    protected: tuple[tuple[Pos, str], ...]
    facilities_json: str = '{}'

    def blueprint(self):
        """已应用 scene props 的独立副本；不能再套用原 props。"""
        return json.loads(self.blueprint_json)

    @property
    def roles(self):
        return json.loads(self.roles_json)

    @property
    def targets(self):
        return json.loads(self.targets_json)

    @property
    def facilities(self):
        return json.loads(self.facilities_json)


@dataclass(frozen=True)
class SceneContract:
    manifest_json: str
    config_json: str
    sites: tuple[SiteContract, ...]

    @property
    def site_ids(self):
        return tuple(s.site for s in self.sites)

    def site(self, site):
        for contract in self.sites:
            if contract.site == site:
                return contract
        raise ValueError('unknown story site: ' + site)


@dataclass(frozen=True)
class GenerationContext:
    contract: SiteContract
    root_seed: int

    @property
    def seed(self):
        return derive_seed(self.root_seed, self.contract.scene_version, self.contract.site)

    def rng(self, stream='geometry'):
        """每次返回新的独立流；调用方保存返回值用于该阶段，禁止模块级 Random。"""
        return random.Random(derive_seed(self.root_seed, self.contract.scene_version, self.contract.site, stream))


@dataclass(frozen=True)
class SiteResult:
    site: str
    blueprint_json: str
    semantics: SemanticPlan

    def to_blueprint(self):
        return json.loads(self.blueprint_json)


def load_contract(source: Path = assets.SOURCE) -> SceneContract:
    """从唯一 authored 事实源提取契约，并复用当前整场景验收，不维护另一套 role/target。"""
    source = Path(source)
    manifest = json.loads((source / 'manifest.json').read_text())
    config = json.loads((source / 'story_scene.json').read_text())
    ids = [row['id'] for row in manifest['locations']]
    if len(ids) != len(set(ids)) or any(not re.fullmatch(r'[a-z][a-z0-9_]*', s) for s in ids):
        raise ValueError('site ids must be unique Python module names')
    _, authored, _ = assets.assemble(source)
    contracts = []
    for row in manifest['locations']:
        site = row['id']
        data = authored[site]
        size, _, blocks, _ = assets.nbt.expand_structure(data)
        roles, targets, facilities, protected = {}, {}, {}, {}

        def reserve(pos):
            pos = _pos(pos, size, site + '.protected')
            protected[pos] = assets.canonical(blocks[pos])

        def standing(pos):
            pos = _pos(pos, size, site + '.feet')
            for dy in (-1, 0, 1):
                reserve(assets.add(pos, (0, dy, 0)))

        for pos in data['walk_targets'].values():
            standing(pos)
        for anchor in data['story_anchors']:
            if 'pos' in anchor:
                reserve(anchor['pos'])
        for service in data.get('service_blocks', []):
            reserve(service['pos'])
        for key, spec in config['positions'].items():
            if spec['site'] == site:
                approach = spec.get('approach', data['walk_targets'][spec['walk_target']])
                roles[key] = {**spec, 'approach': approach}
                standing(spec['pos'])
                standing(approach)
        for key, spec in config['targets'].items():
            if spec['site'] == site:
                approach = spec.get('approach', data['walk_targets'][spec['walk_target']])
                targets[key] = {**spec, 'approach': approach, 'state': blocks[tuple(spec['pos'])]}
                reserve(spec['pos'])
                standing(approach)
                ray = assets.check_target_sightline(blocks, key, spec['pos'], approach)
                for pos in ray['intermediate_cells']:
                    reserve(pos)
        for key, spec in config.get('facilities', {}).items():
            if spec['site'] == site:
                approach = spec['approach'] if 'approach' in spec else data['walk_targets'][spec['walk_target']]
                facilities[key] = {**spec, 'approach': approach, 'state': blocks[tuple(spec['pos'])]}
                reserve(spec['pos'])
                standing(approach)
                ray = assets.check_target_sightline(blocks, key, spec['pos'], approach)
                for pos in ray['intermediate_cells']:
                    reserve(pos)
        contracts.append(SiteContract(site, config['version'], tuple(row['origin']), tuple(size),
                                      tuple(row['max_size']), _json(data), _json(roles), _json(targets),
                                      tuple(sorted(protected.items())), _json(facilities)))
    return SceneContract(_json(manifest), _json(config), tuple(contracts))


class SiteBuilder:
    """BlockGrid PROTECTED + 显式冲突错误；导出时再次验证，不能 force 绕过。"""
    def __init__(self, contract: SiteContract, *, from_authored: bool = False):
        self.contract = contract
        self.grid = BlockGrid()
        self._protected = dict(contract.protected)
        if from_authored:
            _, _, blocks, _ = assets.nbt.expand_structure(contract.blueprint())
            for pos, state in blocks.items():
                if state != 'minecraft:air':
                    self.grid.set(pos, state, ('STRUCTURE',), PRIORITY['STRUCTURE'])
        for pos, state in contract.protected:
            self.grid.set(pos, state, ('PROTECTED',), PRIORITY['PROTECTED'])

    def apply_ops(self, ops: Iterable[dict], *, palette: Mapping[str, str] | None = None):
        """原有 set/fill/line DSL；整批先检查，冲突不产生半写入。"""
        data = {'size': list(self.contract.size), 'ops': list(ops), 'palette': dict(palette or {})}
        # Check exact integers before invoking the permissive legacy DSL parser.
        for op in data['ops']:
            for key in ('pos', 'from', 'to'):
                if key in op:
                    _pos(op[key], self.contract.size, 'op.' + key)
        blocks = {}
        for op in data['ops']:
            _, _, writes, _ = assets.nbt.expand_structure({**data, 'ops': [op]})
            for pos, state in writes.items():
                if pos in self._protected and assets.canonical(state) != self._protected[pos]:
                    raise ValueError(f'protected story cell would be overwritten: {self.contract.site} {pos}')
            blocks.update(writes)
        for pos, state in blocks.items():
            if pos not in self._protected:
                self.grid.set(pos, state, ('STRUCTURE',), PRIORITY['STRUCTURE'])
        return self

    def finish(self, semantics: SemanticPlan, *, metadata: Mapping | None = None) -> SiteResult:
        data = self.contract.blueprint()
        if metadata:
            data.update(metadata)
        # Fixed local origin/extent; never crop/recenter through BlockGrid.bounds().
        data.pop('palette', None)
        data['ops'] = []
        for pos, cell in sorted(self.grid.iter_cells(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
            _pos(pos, self.contract.size, 'grid')
            if cell.state != 'minecraft:air':
                data['ops'].append({'op': 'set', 'pos': list(pos), 'state': cell.state})
        result = SiteResult(self.contract.site, _json(data), semantics)
        validate_result(self.contract, result)
        return result


def validate_result(contract: SiteContract, result: SiteResult):
    """最终 DSL 检查阻断手工改 ops/grid 或外部插件绕过 builder 的情况。"""
    if result.site != contract.site:
        raise ValueError('result site differs from contract')
    data, baseline = result.to_blueprint(), contract.blueprint()
    for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors', 'service_blocks', 'entities'):
        if data.get(key) != baseline.get(key):
            raise ValueError('immutable story metadata changed: ' + key)
    if data.get('fill_air') is not True:
        raise ValueError('fill_air must be true')
    for op in data['ops']:
        for key in ('pos', 'from', 'to'):
            if key in op:
                _pos(op[key], contract.size, 'op.' + key)
    blocks, _, checks = validate(data, contract.max_size)
    for pos, state in contract.protected:
        if assets.canonical(blocks[pos]) != state:
            raise ValueError(f'protected story cell changed: {contract.site} {pos}')
    required = dict(data['walk_targets'])
    for key, spec in contract.roles.items():
        required['role:' + key + ':feet'] = spec['pos']
        required['role:' + key + ':approach'] = spec['approach']
    for key, spec in contract.targets.items():
        required['target:' + key] = spec['approach']
        if assets.canonical(blocks[tuple(spec['pos'])]) != assets.canonical(spec['state']):
            raise ValueError('exact target state changed: ' + key)
        ray = assets.check_target_sightline(blocks, key, spec['pos'], spec['approach'])
        if sum((a-b)**2 for a, b in zip(ray['eye'], ray['click_point'])) > 4.5**2:
            raise ValueError('target too far from approach: ' + key)
    for key, spec in contract.facilities.items():
        required['facility:' + key] = spec['approach']
        if assets.canonical(blocks[tuple(spec['pos'])]) != assets.canonical(spec['state']):
            raise ValueError('exact facility state changed: ' + key)
        ray = assets.check_target_sightline(blocks, key, spec['pos'], spec['approach'])
        if sum((a-b)**2 for a, b in zip(ray['eye'], ray['click_point'])) > 4.5**2:
            raise ValueError('facility too far from approach: ' + key)
    check_walks(blocks, required, contract.size)
    semantic_points = [spec['pos'] for spec in contract.roles.values()]
    semantic_points += [spec['approach'] for spec in contract.roles.values()]
    semantic_points += [p for spec in contract.targets.values() for p in (spec['pos'], spec['approach'])]
    semantic_points += [p for spec in contract.facilities.values() for p in (spec['pos'], spec['approach'])]
    semantic_points += [a['pos'] for a in data['story_anchors'] if 'pos' in a]
    semantic_points += [s['pos'] for s in data.get('service_blocks', [])]
    _validate_semantics(result.semantics, data, blocks, semantic_points)
    return checks


def _validate_semantics(plan, data, blocks, semantic_points):
    spaces = {space.id: space for space in plan.spaces}
    if not spaces or len(spaces) != len(plan.spaces) or any(not k for k in spaces):
        raise ValueError('semantic spaces need unique nonempty ids')
    assigned = set()
    for space in plan.spaces:
        _pos(space.minimum, data['size'], 'space.minimum')
        _pos(space.maximum, data['size'], 'space.maximum')
        if any(a > b for a, b in zip(space.minimum, space.maximum)) or not space.purpose.strip():
            raise ValueError('semantic space needs ordered bounds and purpose: ' + space.id)
        if not space.walk_targets:
            raise ValueError('semantic space needs an existing walk target: ' + space.id)
        for key in space.walk_targets:
            if key not in data['walk_targets'] or not space.contains(data['walk_targets'][key]):
                raise ValueError('semantic walk target outside space or unknown: ' + key)
            assigned.add(key)
    if assigned != set(data['walk_targets']):
        raise ValueError('semantic plan must cover every authored walk target')
    if any(not any(space.contains(pos) for space in spaces.values()) for pos in semantic_points):
        raise ValueError('story anchor outside all semantic spaces')
    graph = walk_graph(blocks, data['size'])
    for edge in plan.adjacencies:
        if edge.source not in spaces or edge.target not in spaces or edge.source == edge.target:
            raise ValueError('adjacency needs two distinct known spaces')
        if len(edge.path) < 2:
            raise ValueError('adjacency needs a walk path with both endpoints')
        path = [_pos(p, data['size'], 'adjacency.path') for p in edge.path]
        if not spaces[edge.source].contains(path[0]) or not spaces[edge.target].contains(path[-1]):
            raise ValueError('adjacency endpoints outside semantic spaces')
        if any(b not in graph.get(a, ()) for a, b in zip(path, path[1:])):
            raise ValueError('adjacency path is not walkable')


def generate_sites(scene: SceneContract, seed: int, sites: Iterable[str]) -> dict[str, SiteResult]:
    """仅加载显式选中的独立模块；缺失插件报错，其余地点由 compose_scene 沿用 authored。"""
    selected = list(sites)
    if len(selected) != len(set(selected)):
        raise ValueError('duplicate selected site')
    results = {}
    for site in selected:
        contract = scene.site(site)
        module = importlib.import_module(f'{__package__}.{site}')
        if module.SITE_ID != site:
            raise ValueError('plugin SITE_ID differs from requested site: ' + site)
        result = module.generate(GenerationContext(contract, seed))
        validate_result(contract, result)
        results[site] = result
    return results


def compose_scene(scene: SceneContract, generated: Mapping[str, SiteResult]):
    """混合 generated + authored，交给原 assemble 检查道路、NPC占位及全场景视线。"""
    for site, result in generated.items():
        validate_result(scene.site(site), result)
    manifest, config = json.loads(scene.manifest_json), json.loads(scene.config_json)
    # load_contract already materialized all scene props; applying them twice is an error.
    config['props'] = []
    with tempfile.TemporaryDirectory(prefix='qianzhen-storygen-') as folder:
        source = Path(folder)
        (source / 'manifest.json').write_text(_json(manifest), encoding='utf-8')
        (source / 'story_scene.json').write_text(_json(config), encoding='utf-8')
        for row in manifest['locations']:
            contract = scene.site(row['id'])
            data = generated[row['id']].to_blueprint() if row['id'] in generated else contract.blueprint()
            path = source / row['source']
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(_json(data), encoding='utf-8')
        return assets.assemble(source)
