"""阡镇采集器专用的有界 RCON 与只读部署确认；不重发部署、不写世界。"""
from __future__ import annotations

import hashlib
from pathlib import Path

from tools.buildgen.nbtread import read_gzipped_nbt

# Compatibility exports for existing deployment callers. Policy and transport
# are owned by story_lifecycle for all first-chapter long operations.
from tools.qianzhen.story_lifecycle import (
    DEFAULT_DEPLOY_TIMEOUT, DEFAULT_COMMAND_TIMEOUT, MAX_DEPLOY_TIMEOUT,
    MAX_COMMAND_TIMEOUT, deployment_budgets, lifecycle_command as deployment_command,
    _DeadlineRcon,
)


def saved_deployment(world, layout, origin):
    path = Path(world)/'data/myvillage_qianzhen_scene.dat'
    if not path.exists():
        return None
    _, root = read_gzipped_nbt(str(path))
    saved = root['data']
    expected = {'schema': 1, 'id': layout['id'], 'version': layout['version'],
                'dimension': 'minecraft:overworld', **dict(zip(('x', 'y', 'z'), map(int, origin)))}
    if any(saved.get(key) != value for key, value in expected.items()):
        raise ValueError('Runtime deployed scene version/origin/identity differs from requested scene')
    if saved.get('phase') not in ('PLACING', 'COMPLETE'):
        raise ValueError('Runtime deployment is terminal or invalid: '+str(saved.get('phase')))
    return {**saved, 'saved_data_sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
