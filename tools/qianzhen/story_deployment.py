"""阡镇采集器专用的有界 RCON 与只读部署确认；不重发部署、不写世界。"""
from __future__ import annotations

import hashlib
import math
from pathlib import Path
import time

from tools.buildgen.nbtread import read_gzipped_nbt
from tools.combat_capture.rcon import Rcon, RconError

DEFAULT_DEPLOY_TIMEOUT = 90.0
DEFAULT_COMMAND_TIMEOUT = 30.0
MAX_DEPLOY_TIMEOUT = 180.0
MAX_COMMAND_TIMEOUT = 60.0


def deployment_budgets(total, command):
    for name, value, maximum in (('deploy-timeout', total, MAX_DEPLOY_TIMEOUT),
                                  ('deploy-command-timeout', command, MAX_COMMAND_TIMEOUT)):
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 < value <= maximum:
            raise ValueError(f'{name} must be finite and within (0, {maximum}] seconds')
    if command > total:
        raise ValueError('deploy-command-timeout must not exceed deploy-timeout')
    return float(total), float(command)


class _DeadlineRcon(Rcon):
    """Include connect/auth and every partial read in one command budget.

    Kept local to this collector: other RCON consumers retain their defaults.
    The inherited multipart drain can shorten a read to .3s, never extend the
    absolute deadline. Each command gets a fresh connection, also after timeout.
    """

    def __init__(self, state, timeout):
        super().__init__('127.0.0.1', state['rcon_port'], state['rcon_password'], timeout)
        self.deadline = time.monotonic() + timeout

    def _arm(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('Deployment RCON command budget exhausted')
        self.sock.settimeout(min(self.sock.gettimeout() or remaining, remaining))

    def _send(self, *args):
        self._arm()
        return super()._send(*args)

    def _recv_exact(self, n):
        buf = b''
        while len(buf) < n:
            self._arm()
            chunk = self.sock.recv(n-len(buf))
            if not chunk:
                raise RconError('rcon connection closed')
            buf += chunk
        return buf


def deployment_command(state, command, timeout):
    connection = _DeadlineRcon(state, timeout)
    try:
        connection.connect()
        reply = connection.cmd(command)
        connection._arm()  # Multipart draining must not swallow the deadline.
        return reply
    finally:
        # __enter__ failures do not invoke __exit__; auth timeouts must close too.
        connection.close()


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
