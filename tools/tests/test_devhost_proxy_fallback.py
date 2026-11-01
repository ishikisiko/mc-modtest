"""真实 Java HTTP 下载回归；只操作临时目录和本地模拟代理，不启动 Minecraft。"""

import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parents[2]
DEVHOST = ROOT / "devbridge/devhost"
SPEC = importlib.util.spec_from_file_location("devhostctl", DEVHOST / "devhostctl.py")
CTL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CTL)


class ProxyFixture:
    def __init__(self, body):
        self.body, self.status, self.delay, self.length = body, 200, 0, None
        self.requests = []
        self.release = threading.Event()
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                fixture.requests.append(self.path)
                fixture.release.wait(fixture.delay)
                try:
                    self.send_response(fixture.status)
                    self.send_header("Content-Length", str(fixture.length or len(fixture.body)))
                    self.end_headers()
                    if fixture.length is None:
                        self.wfile.write(fixture.body)
                except (BrokenPipeError, ConnectionResetError):
                    pass

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.address = "127.0.0.1:" + str(self.server.server_port)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.closed = False

    def close(self):
        if not self.closed:
            self.release.set()
            self.server.shutdown()
            self.server.server_close()
            self.thread.join()
            self.closed = True


class DevHostProxyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("javac") or not shutil.which("java"):
            raise RuntimeError("需要 JDK 21+ 运行真实 DevHost 回归")
        cls.compiled = tempfile.TemporaryDirectory(prefix="devhost-classes-")
        cls.addClassCleanup(cls.compiled.cleanup)
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", cls.compiled.name,
                        str(DEVHOST / "DevHost.java")], check=True, capture_output=True, text=True)
        out = io.BytesIO()
        with zipfile.ZipFile(out, "w") as jar:
            jar.writestr("META-INF/neoforge.mods.toml", '[[mods]]\nmodId="proxy_fixture"\n')
        cls.jar = out.getvalue()
        cls.sha = hashlib.sha256(cls.jar).hexdigest()

    def setUp(self):
        folder = tempfile.TemporaryDirectory(prefix="devhost-proxy-")
        self.addCleanup(folder.cleanup)
        self.folder = Path(folder.name)
        self.game = self.folder / "game"
        (self.game / "mods").mkdir(parents=True)
        self.primary, self.backup = ProxyFixture(self.jar), ProxyFixture(self.jar)
        self.addCleanup(self.primary.close)
        self.addCleanup(self.backup.close)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        cfg = self.folder / "devhost.properties"
        cfg.write_text(f"gameDir={self.game}\nlaunch={self.folder / 'never-run.sh'}\n"
                       f"bind=127.0.0.1\nport={port}\ntoken=fixture-token\n")
        self.base = f"http://127.0.0.1:{port}"
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self.log = (self.folder / "process.log").open("w")
        self.addCleanup(self.log.close)
        self.process = subprocess.Popen([
            "java", "-Ddevhost.downloadProxies=" + self.primary.address + "," + self.backup.address,
            "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=" + str(self.primary.server.server_port),
            "-Dhttp.nonProxyHosts=", "-cp", self.compiled.name, "DevHost", str(cfg)],
            stdout=self.log, stderr=subprocess.STDOUT)
        self.addCleanup(self.stop_process)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                self.fail((self.folder / "process.log").read_text())
            try:
                with self.opener.open(self.base + "/ping", timeout=0.2) as response:
                    self.assertTrue(json.load(response)["ok"])
                return
            except (urllib.error.URLError, TimeoutError):
                time.sleep(0.05)
        self.fail("DevHost 启动超时")

    def stop_process(self):
        self.process.terminate()
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()

    def install(self, **overrides):
        params = {"url": "http://download.invalid/fixture.jar", "name": "fixture.jar",
                  "sha256": self.sha, "timeoutSec": "4", **overrides}
        request = urllib.request.Request(self.base + "/mods/install-url?" + urllib.parse.urlencode(params),
                                         method="POST", headers={"Authorization": "Bearer fixture-token"})
        try:
            response = self.opener.open(request, timeout=8)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.load(response)

    def assert_installed(self, response, via):
        status, result = response
        self.assertEqual(status, 200, result)
        self.assertTrue(result["ok"], result)
        self.assertEqual(result["result"]["via"], via)
        self.assertEqual((self.game / "mods/fixture.jar").read_bytes(), self.jar)

    def assert_no_install(self):
        self.assertEqual(list((self.game / "mods").iterdir()), [])
        self.assertFalse((self.game / "devhost-backup").exists())

    def test_primary_success_does_not_contact_backup(self):
        self.assert_installed(self.install(), "proxy " + self.primary.address)
        self.assertEqual(self.primary.requests, ["http://download.invalid/fixture.jar"])
        self.assertEqual(self.backup.requests, [])

    def test_refused_primary_falls_back_for_explicit_and_default_proxy(self):
        self.primary.close()
        for params in ({}, {"proxy": self.primary.address}):
            with self.subTest(params=params):
                self.assert_installed(self.install(**params), "proxy " + self.backup.address)
        self.assertEqual(len(self.backup.requests), 2)
        self.assertIn("尝试备用代理", (self.folder / "devhost.log").read_text())

    def test_stalled_primary_leaves_time_for_backup(self):
        self.primary.delay = 10
        start = time.monotonic()
        self.assert_installed(self.install(timeoutSec="2"), "proxy " + self.backup.address)
        self.assertLess(time.monotonic() - start, 3)

    def test_gateway_errors_fall_back(self):
        for code in (502, 503, 504):
            with self.subTest(code=code):
                self.primary.status = code
                self.assert_installed(self.install(), "proxy " + self.backup.address)
                # 每个网关错误独立验证下载，避免重复安装撞上按秒命名的备份目录。
                (self.game / "mods/fixture.jar").unlink()
        self.assertEqual(len(self.backup.requests), 3)

    def test_both_fail_preserves_errors_and_does_not_install(self):
        self.primary.status, self.backup.status = 502, 504
        status, response = self.install()
        self.assertEqual(status, 502)
        for evidence in (self.primary.address, self.backup.address, "502", "504"):
            self.assertIn(evidence, response["error"])
        self.assert_no_install()

    def test_total_timeout_is_shared(self):
        self.primary.delay = self.backup.delay = 10
        start = time.monotonic()
        status, response = self.install(timeoutSec="2")
        self.assertEqual(status, 504, response)
        self.assertLess(time.monotonic() - start, 3)
        self.assertEqual(len(self.backup.requests), 1)
        self.assert_no_install()

    def test_http_errors_do_not_trigger_fallback(self):
        for code in (404, 407):
            with self.subTest(code=code):
                self.primary.status = code
                status, response = self.install()
                self.assertEqual(status, 502)
                self.assertIn(str(code), response["error"])
                self.assertEqual(self.backup.requests, [])
                self.assert_no_install()

    def test_hash_mismatch_does_not_trigger_fallback_or_install(self):
        status, response = self.install(sha256="0" * 64)
        self.assertEqual(status, 400)
        self.assertIn("sha256 mismatch", response["error"])
        self.assertEqual(self.backup.requests, [])
        self.assert_no_install()

    def test_oversize_does_not_trigger_fallback_or_install(self):
        self.primary.length = 64 * 1024 * 1024 + 1
        status, response = self.install()
        self.assertEqual(status, 413, response)
        self.assertEqual(self.backup.requests, [])
        self.assert_no_install()

    def test_explicit_direct_bypasses_configured_and_jvm_proxy(self):
        self.assert_installed(self.install(proxy="", url="http://" + self.backup.address + "/fixture.jar"), "direct")
        self.assertEqual(self.primary.requests, [])
        self.assertEqual(self.backup.requests, ["/fixture.jar"])

    def test_custom_proxy_does_not_fall_back_to_default_chain(self):
        custom = ProxyFixture(self.jar)
        self.addCleanup(custom.close)
        custom.status = 502
        status, response = self.install(proxy=custom.address)
        self.assertEqual(status, 502, response)
        self.assertEqual(len(custom.requests), 1)
        self.assertEqual(self.primary.requests + self.backup.requests, [])
        self.assert_no_install()

    def test_invalid_port_and_timeout_are_rejected_before_download(self):
        for params in ({"proxy": "127.0.0.1:0"}, {"proxy": "127.0.0.1:65536"}, {"timeoutSec": "0"}):
            with self.subTest(params=params):
                status, response = self.install(**params)
                self.assertEqual(status, 400, response)
                self.assertEqual(self.primary.requests + self.backup.requests, [])
                self.assert_no_install()


class ControllerProxyTest(unittest.TestCase):
    def test_controller_distinguishes_default_proxy_and_explicit_direct(self):
        for configured in ("", "127.0.0.1:7897"):
            with self.subTest(configured=configured), tempfile.TemporaryDirectory() as folder:
                env = {"DEVHOST_PULL_DIR": folder, "DEVHOST_PULL_URL": "http://fixture.invalid",
                       "DEVHOST_PULL_PROXY": configured}
                replies = [{"ok": False, "error": "download failed"}, {"ok": True, "result": {"via": "direct"}}]
                with patch.dict(os.environ, env, clear=True), patch.object(CTL, "host", side_effect=replies) as host:
                    result = CTL.pull_install(Path("fixture.jar"), b"fixture", "0" * 64)
                self.assertEqual(result, {"via": "direct"})
                self.assertEqual([call.kwargs["params"]["proxy"] for call in host.call_args_list],
                                 [configured or None, ""])
                self.assertEqual(list(Path(folder).iterdir()), [])


if __name__ == "__main__":
    unittest.main()
