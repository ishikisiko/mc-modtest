"""独立原生fixture读回/身体位置准备；无玩家tp、无剧情/作物/培养写权限。"""
from __future__ import annotations

import json
import math
from pathlib import Path
import re
import struct
import time
import uuid

from tools.qianzhen_opening_evidence import digest, fingerprint


# Machine-readable contract for the parent's next native preparation. Coordinates
# are observed after deployment; their binding pins this exact source/request/run.
EXTENDED_FIXTURE_SCHEMA = {
    '$schema': 'https://json-schema.org/draft/2020-12/schema',
    'type': 'object', 'required': ['schema', 'binding', 'provenance'],
    'properties': {
        'schema': {'enum': ['qianzhen-personal-home-v1', 'qianzhen-physical-portal-v1']},
        'binding': {'type': 'object', 'required': ['run_id', 'source_commit', 'source_fingerprint', 'request_sha256']},
        'provenance': {'type': 'object', 'minProperties': 1},
    },
    'allOf': [
        {'if': {'properties': {'schema': {'const': 'qianzhen-personal-home-v1'}}},
         'then': {'required': ['outbound', 'return', 'support', 'placed', 'support_state', 'combat_approach']}},
        {'if': {'properties': {'schema': {'const': 'qianzhen-physical-portal-v1'}}},
         'then': {'required': ['overworld_entry', 'nether_clear', 'nether_reenter', 'overworld_clear', 'player_teleport_commands'],
                  'properties': {'player_teleport_commands': {'const': []}}}},
    ],
}


def uuid_text(values):
    if not isinstance(values, list) or len(values) != 4 or any(type(v) is not int for v in values):
        raise ValueError('身体UUID必须为四个真实NBT整数')
    return str(uuid.UUID(bytes=b''.join(struct.pack('>i', v) for v in values)))


class NativeFixtureProbe:
    """Explicit sidecar. Every command is generated from a currently verified NPC UUID.

    Only displacement writes; body diagnostics are read-only. Parent may also
    declare a pre-existing combat mob UUID, which cannot be displaced or healed.
    """
    def __init__(self, send, path, root, emit, *, combat_uuid=None, provenance=None):
        self.send, self.path, self.root, self.emit = send, Path(path), Path(root), emit
        self.rows, self.bound = [], {}
        self.combat_uuid = str(uuid.UUID(combat_uuid)) if combat_uuid else None
        self.origin = provenance
        if not provenance:
            raise ValueError('fixture必须有本run实际准备来源')
        source = self.root / 'src/main/java/com/example/myvillage/entity/ModEntities.java'
        source_text = source.read_text()
        matches = re.findall(r'ENTITY_TYPES\.register\("cultivator"[^;]+?\.sized\([0-9.]+F,\s*([0-9.]+)F\)', source_text, re.S)
        if len(matches) != 1:
            raise ValueError('无法从唯一生产注册源读取cultivator实际高度')
        self.height, self.entity_source_sha256 = float(matches[0]), digest(source)
        self.stream = self.path.open('x')

    def _command(self, command, purpose):
        row = {'kind': 'independent_native_fixture', 'purpose': purpose, 'command': command,
               'time': time.monotonic(), 'receipt': len(self.rows) + 1}
        try:
            row['reply'] = self.send(command, 8)
            return row['reply']
        except BaseException as error:
            row['failure'] = repr(error)
            raise
        finally:
            self.rows.append(row)
            self.stream.write(json.dumps(row, ensure_ascii=False) + '\n')
            self.stream.flush()
            self.emit('observation', label='declared_native_fixture_receipt', fixture_file=self.path.name,
                      receipt=row['receipt'], receipt_sha256=fingerprint(row))

    def body(self, person):
        from tools.alchemy_quality_evidence import read_data
        if type(person) is not int or person < 0:
            raise ValueError('身体读回只能绑定真实personId')
        selector = '@e[nbt={WorldSimPerson:' + str(person) + '}]'
        raw = self._command('execute as ' + selector + ' run data get entity @s', 'read_unique_story_body')
        lines = [line for line in raw.splitlines() if 'has the following entity data:' in line]
        if len(lines) != 1:
            raise ValueError('必须恰好一个实际命簿身体，不能用limit=1隐藏重复')
        row = read_data(lines[0])
        if row.get('WorldSimPerson') != person or not isinstance(row.get('Pos'), list) or len(row['Pos']) != 3:
            raise ValueError('实际身体缺身份/位置读回')
        if any(not isinstance(v, (int, float)) or not math.isfinite(v) for v in row['Pos']):
            raise ValueError('身体位置非有限数')
        actual_uuid = uuid_text(row['UUID'])
        if person in self.bound and self.bound[person] != actual_uuid:
            raise ValueError('marker运动子场景中身体UUID意外改变')
        self.bound[person] = actual_uuid
        return {'personId': person, 'uuid': actual_uuid, 'pos': row['Pos'], 'height': self.height,
                'on_ground': bool(row['OnGround']), 'no_ai': bool(row.get('NoAI', 0)), 'raw': row}

    def displace(self, person, graph):
        body = self.body(person)
        if body['no_ai'] or not body['on_ground']:
            raise ValueError('必须为实际启用AI且落地的原身体；不改AI/重造身份')
        candidates = [p for p in graph if 2 <= math.dist((p[0] + .5, p[1], p[2] + .5), body['pos']) <= 3.5
                      and abs(p[1] - body['pos'][1]) < .3]
        if not candidates:
            raise ValueError('没有同层实际可达NPC偏移点')
        # The existing scene graph's connected nodes preserve authored free space.
        point = min(candidates, key=lambda p: math.dist(p, body['pos']))
        command = f'tp {body["uuid"]} {point[0] + .5} {point[1]} {point[2] + .5}'
        reply = self._command(command, 'prepare_same_person_body_position')
        if 'Teleported ' not in reply:
            raise ValueError('NPC fixture位置准备没有明确回执')
        return self.body(person)

    def combat_body(self):
        from tools.alchemy_quality_evidence import read_data
        if not self.combat_uuid:
            raise ValueError('home战斗需要声明实际敌对生物fixture UUID')
        raw = self._command('data get entity ' + self.combat_uuid, 'read_home_combat_body')
        row = read_data(raw)
        if uuid_text(row['UUID']) != self.combat_uuid or row.get('WorldSimPerson', -1) >= 0:
            raise ValueError('不能攻击剧情命簿人物冒充家园刷怪')
        if not row.get('Health', 0) > 0 or row.get('Invulnerable', 0):
            raise ValueError('战斗fixture必须为实际活着且可受伤的非人物生物')
        return row

    def is_portal(self, dimension, position):
        if dimension not in ('minecraft:overworld', 'minecraft:the_nether') or len(position) != 3 or any(type(v) is not int for v in position):
            raise ValueError('传送门只读位置/维度非法')
        raw = self._command('execute in ' + dimension + ' if block ' + ' '.join(map(str, position)) + ' minecraft:nether_portal',
                            'read_actual_portal_block')
        if 'Test passed' in raw:
            return True
        if 'Test failed' in raw or 'test failed' in raw:
            return False
        raise ValueError('实际portal方块读回不明确')

    def provenance(self):
        return {'path': self.path.name, 'sha256': digest(self.path), 'receipts': len(self.rows),
                'source': self.origin, 'entity_registration_sha256': self.entity_source_sha256,
                'player_teleport_commands': [], 'story_or_crop_writes': []}

    def close(self):
        self.stream.close()


def validate_fixture_bundle(spec, selected):
    """Fail before inputs for absent, malformed or foreign-run physical fixtures."""
    from tools.qianzhen_opening_extended import validate_path
    if not any(spec.get(key) for key in ('home', 'portal', 'native_fixture')):
        return
    binding = {key: spec[key] for key in ('run_id', 'source_commit', 'source_fingerprint', 'request_sha256')}
    for label, schema, paths in (
        ('home', 'qianzhen-personal-home-v1', ('outbound', 'return', 'combat_approach')),
        ('portal', 'qianzhen-physical-portal-v1', ('overworld_entry', 'nether_clear', 'nether_reenter', 'overworld_clear')),
    ):
        value = spec.get(label)
        needed = ('optional_personal_story' if label == 'home' else 'marker_matrix') in selected
        if value is None:
            if needed:
                raise ValueError('缺实际' + label + ' fixture')
            continue
        if value.get('schema') != schema or value.get('binding') != binding or not value.get('provenance'):
            raise ValueError(label + ' fixture schema/本run绑定/实际来源不符')
        for name in paths:
            validate_path(value.get(name))
        if label == 'portal' and value.get('player_teleport_commands') != []:
            raise ValueError('portal不能含玩家传送命令')
        if label == 'home':
            for name in ('support', 'placed'):
                point = value.get(name)
                if not isinstance(point, list) or len(point) != 3 or any(type(v) is not int for v in point):
                    raise ValueError('home支撑/放置坐标非法')
            if value['placed'] != [value['support'][0], value['support'][1] + 1, value['support'][2]]:
                raise ValueError('home placed必须位于实际support正上方')
            if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+(?:\[[a-z0-9_=,.-]+\])?', value.get('support_state', '')):
                raise ValueError('home实际support_state非法')
    native = spec.get('native_fixture')
    if native is not None:
        if native.get('binding') != binding or not native.get('provenance'):
            raise ValueError('native fixture未绑定本run/准备来源')
        if 'optional_personal_story' in selected and not native.get('combat_uuid'):
            raise ValueError('缺实际敌对生物UUID')
        if native.get('combat_uuid'):
            uuid.UUID(native['combat_uuid'])
