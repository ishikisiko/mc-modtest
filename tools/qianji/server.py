"""千机阁原生检查只在自有服务端完全释放存档和端口后继续。"""
from __future__ import annotations

import errno
import fcntl
import socket
import subprocess
import time

from roads_smoke.run import Server as BaseServer
from roadweaver_probe.run import terminate, write


class Server(BaseServer):
    def _lock_free(self):
        path = self.game / 'world/session.lock'
        try:
            with path.open('r+b') as stream:
                fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.lockf(stream, fcntl.LOCK_UN)
            return True
        except FileNotFoundError:
            return True
        except OSError as error:
            if error.errno in (errno.EACCES, errno.EAGAIN):
                return False
            raise

    def _port_open(self, port):
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=.2):
                return True
        except OSError:
            return False

    def _released(self):
        return self._lock_free() and not any(self._port_open(p) for p in (self.rport, self.sport))

    def start(self):
        if not self._released():
            raise RuntimeError('自有测试世界的锁或端口尚未释放，不能启动下一轮服务端')
        super().start()

    def stop(self):
        if self.process is None:
            return
        record = {'launcher_pid': self.process.pid, 'forced_world_shutdown': False}
        started = time.monotonic()
        if self.process.poll() is None:
            # 结束本次探针的加速和强加载，再让 Minecraft 正常保存并退出。
            for command in ('tick sprint stop', 'forceload remove all', 'save-all flush', 'stop'):
                try:
                    record[command] = self.command(command)
                except OSError as error:
                    record[command] = repr(error)
                    if command != 'stop':
                        raise
        deadline = time.monotonic() + 180
        while not self._released():
            if time.monotonic() > deadline:
                record.update(seconds=time.monotonic() - started, world_and_ports_released=False)
                write(self.out / f'shutdown-{self.starts}.json', record)
                raise TimeoutError('自有 Minecraft 服务端在停止后仍占用存档锁或端口')
            time.sleep(.5)
        try:
            self.process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            # 此时存档和两个服务端端口均已关闭；只回收本工具的 Gradle 启动器。
            terminate(self.process)
        record.update(seconds=time.monotonic() - started, world_and_ports_released=True)
        write(self.out / f'shutdown-{self.starts}.json', record)
        write(self.out / f'cleanup-{self.starts}.json', terminate(self.process))
        if hasattr(self, 'log'):
            self.log.close()
        self.process = None
