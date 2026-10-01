"""Minimal Minecraft RCON client (stdlib only).

Vanilla's RconClient reads the socket once and drops the connection if that
read holds more than one packet, so a command is never pipelined with a
second packet.
"""
from __future__ import annotations

import socket
import struct
import time

AUTH, EXEC = 3, 2


class RconError(RuntimeError):
    pass


class Rcon:
    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0):
        self.host, self.port, self.password, self.timeout = host, port, password, timeout
        self.sock = None
        self._id = 0

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, *exc):
        self.close()

    def connect(self):
        self.sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        rid, _ = self._request(AUTH, self.password)
        if rid == -1:
            self.close()
            raise RconError("rcon authentication failed")

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            finally:
                self.sock = None

    def _send(self, rid: int, kind: int, body: str):
        data = struct.pack("<ii", rid, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _recv_exact(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RconError("rcon connection closed")
            buf += chunk
        return buf

    def _recv(self):
        (length,) = struct.unpack("<i", self._recv_exact(4))
        payload = self._recv_exact(length)
        rid, _kind = struct.unpack("<ii", payload[:8])
        return rid, payload[8:-2].decode("utf-8", "replace")

    def _request(self, kind: int, body: str):
        self._id += 1
        self._send(self._id, kind, body)
        return self._recv()

    def cmd(self, command: str) -> str:
        """Run one command; replies over 4096 bytes arrive split, so keep
        reading while the last chunk was full."""
        self._id += 1
        rid = self._id
        self._send(rid, EXEC, command)
        parts = []
        got_id, text = self._recv()
        if got_id == rid:
            parts.append(text)
        while len(text.encode("utf-8")) >= 4096:
            try:
                self.sock.settimeout(0.3)
                got_id, text = self._recv()
            except (socket.timeout, TimeoutError):
                break
            finally:
                self.sock.settimeout(self.timeout)
            if got_id == rid:
                parts.append(text)
        return "".join(parts)


def connect(port: int, password: str, retries: int = 1, delay: float = 2.0, timeout: float = 10.0) -> Rcon:
    last = None
    for _ in range(max(1, retries)):
        try:
            r = Rcon("127.0.0.1", port, password, timeout)
            r.connect()
            return r
        except (OSError, RconError) as e:
            last = e
            time.sleep(delay)
    raise RconError(f"cannot connect rcon on 127.0.0.1:{port}: {last}")
