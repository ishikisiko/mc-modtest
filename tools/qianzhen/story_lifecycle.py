"""第一章长命令预算和传输的唯一来源；普通短查询不受影响。"""
from __future__ import annotations

from dataclasses import dataclass
import math
import time

from tools.combat_capture.rcon import Rcon, RconError


@dataclass(frozen=True)
class LifecycleRule:
    default: float
    maximum: float
    command_default: float
    command_maximum: float


# A new long operation must register here and use the lifecycle dispatcher.
# Deploy can confirm by read-only polling; flush has one request, no retries.
LIFECYCLE_RULES = {
    'deploy': LifecycleRule(90.0, 180.0, 30.0, 60.0),
    'flush': LifecycleRule(60.0, 120.0, 60.0, 120.0),
}
DEFAULT_DEPLOY_TIMEOUT = LIFECYCLE_RULES['deploy'].default
DEFAULT_COMMAND_TIMEOUT = LIFECYCLE_RULES['deploy'].command_default
DEFAULT_FLUSH_TIMEOUT = LIFECYCLE_RULES['flush'].default
MAX_DEPLOY_TIMEOUT = LIFECYCLE_RULES['deploy'].maximum
MAX_COMMAND_TIMEOUT = LIFECYCLE_RULES['deploy'].command_maximum


def bounded_budget(name, value, maximum):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 < value <= maximum:
        raise ValueError(f'{name} must be finite and within (0, {maximum}] seconds')
    return float(value)


def deployment_budgets(total, command):
    rule = LIFECYCLE_RULES['deploy']
    total = bounded_budget('deploy-timeout', total, rule.maximum)
    command = bounded_budget('deploy-command-timeout', command, rule.command_maximum)
    if command > total:
        raise ValueError('deploy-command-timeout must not exceed deploy-timeout')
    return total, command


def flush_budget(timeout):
    return bounded_budget('flush-timeout', timeout, LIFECYCLE_RULES['flush'].maximum)


def lifecycle_operation(command):
    words = command.strip().lstrip('/').split()
    if words[:1] == ['save-all']:
        if words != ['save-all', 'flush']:
            raise ValueError('Story lifecycle requires explicit save-all flush')
        return 'flush'
    if words[:3] == ['myvillage', 'qianzhen', 'deploy']:
        return 'deploy'
    return None


class _DeadlineRcon(Rcon):
    """Include connect/auth and every partial read in one command budget.

    Shared by the story collector lifecycle operations; short queries retain their defaults.
    The inherited multipart drain can shorten a read to .3s, never extend the
    absolute deadline. Each command gets a fresh connection, also after timeout.
    """

    def __init__(self, state, timeout):
        super().__init__('127.0.0.1', state['rcon_port'], state['rcon_password'], timeout)
        self.deadline = time.monotonic() + timeout

    def _arm(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('Lifecycle RCON command budget exhausted')
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


def lifecycle_command(state, command, timeout):
    connection = _DeadlineRcon(state, timeout)
    try:
        connection.connect()
        reply = connection.cmd(command)
        connection._arm()  # Multipart draining must not swallow the deadline.
        return reply
    finally:
        # __enter__ failures do not invoke __exit__; auth timeouts must close too.
        connection.close()
