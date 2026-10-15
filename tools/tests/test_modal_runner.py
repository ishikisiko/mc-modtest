"""Upload, extraction and failure/cleanup boundaries; no cloud credentials needed."""
from contextlib import nullcontext
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from tools.modal_runner import remote, runner


class ArchiveTests(unittest.TestCase):
    def test_release_archive_baseline_detects_generated_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            generated = repo / "src/main/resources/data/myvillage/example.json"
            generated.parent.mkdir(parents=True)
            generated.write_text("original")
            (repo / ".gitignore").write_text("src/main/resources/\n")
            baseline = remote.initialize_archive_git(repo)
            self.assertTrue(baseline["synthetic"])
            self.assertIn("request.commit", baseline["purpose"])
            status_command = ["git", "status", "--porcelain=v1", "--untracked-files=all", "--", "src/main/resources"]
            self.assertEqual(subprocess.check_output(status_command, cwd=repo), b"")
            generated.write_text("changed by generator")
            self.assertIn(b" M src/main/resources/data/myvillage/example.json", subprocess.check_output(status_command, cwd=repo))
            with self.assertRaisesRegex(ValueError, "unexpectedly contains .git"):
                remote.initialize_archive_git(repo)

    def test_fixed_commit_excludes_worktree_and_untracked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo = root / "repo"
            repo.mkdir()
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=repo, stderr=subprocess.DEVNULL)
            git("init")
            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.invalid")
            (repo / "source.txt").write_text("committed")
            git("add", "source.txt")
            git("commit", "-m", "fixture")
            (repo / "source.txt").write_text("dirty")
            (repo / "secret.txt").write_text("do not upload")
            (repo / remote.PAL).write_bytes(b"pal")
            (repo / remote.CATALOG).parent.mkdir()
            (repo / remote.CATALOG).write_text("{}")
            out = root / "plan"
            req = runner.prepare(repo, "HEAD", "smoke", 180, out)
            with tarfile.open(out / "source.tar") as tf:
                self.assertEqual(tf.getnames(), ["source.txt"])
                self.assertEqual(tf.extractfile("source.txt").read(), b"committed")
            (repo / remote.PAL).write_bytes(b"changed later")
            self.assertEqual(remote.sha256(out / remote.PAL), req["inputs"][remote.PAL])
            self.assertEqual(remote.sha256(out / "remote.py"), req["inputs"]["remote.py"])
            self.assertEqual(req["image_tools"]["node"], runner.NODE_VERSION)
            self.assertEqual(req["image_tools"]["openspec"], runner.OPENSPEC_VERSION)

    def test_private_paths_and_symlinks_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "source.tar"
            for name in (".env", "config/.env.local", ".aws/credentials", "key.pem", "../escape", "/absolute"):
                with self.subTest(name=name):
                    with tarfile.open(archive, "w") as tf:
                        tf.addfile(tarfile.TarInfo(name), io.BytesIO())
                    with self.assertRaises(ValueError):
                        runner.inspect_source(archive)
            with tarfile.open(archive, "w") as tf:
                info = tarfile.TarInfo("link")
                info.type, info.linkname = tarfile.SYMTYPE, "/etc/passwd"
                tf.addfile(info)
            with self.assertRaises(ValueError):
                remote.safe_extract(archive, Path(directory) / "output")

    def test_download_traversal_rejected_without_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with tarfile.open(root / "bad.tar", "w") as tf:
                info = tarfile.TarInfo("../escape")
                info.size = 1
                tf.addfile(info, io.BytesIO(b"x"))
            with self.assertRaises(ValueError):
                remote.safe_extract(root / "bad.tar", root / "output")
            self.assertFalse((root / "escape").exists())

    def test_package_omits_worlds_and_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo, evidence = root / "repo", root / "evidence"
            for path in (repo / "run-acceptance/world/secret", repo / "credentials", repo / "build/libs/mod.jar",
                         repo / "run-acceptance/acceptance-server-stdout.log", evidence / "result.json"):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fixture")
            remote.package(repo, evidence, root / "artifacts.tar.gz")
            with tarfile.open(root / "artifacts.tar.gz") as tf:
                self.assertEqual(set(tf.getnames()), {"build/libs/mod.jar", "evidence/result.json",
                                                     "run-acceptance/acceptance-server-stdout.log"})
            with patch.object(remote, "MAX_ARTIFACT_BYTES", 1):
                with self.assertRaises(ValueError):
                    remote.package(repo, evidence, root / "too-large.tar.gz")

    def test_local_lock_rejects_competing_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lock"
            with runner.lock(path):
                with self.assertRaises(RuntimeError):
                    with runner.lock(path):
                        self.fail("second lock acquired")

    def test_invalid_bounds_before_git_or_allocation(self):
        for ref, timeout in (("--all", 1800), ("HEAD", 0), ("HEAD", 7201)):
            with self.assertRaises(ValueError):
                runner.prepare(Path("/missing"), ref, "smoke", timeout, Path("/missing"))

    def test_timed_out_process_is_killed_and_recorded(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = remote.run_step([sys.executable, "-c", "import time; time.sleep(60)"], root, root / "log", 0.1)
            self.assertEqual(result["exit_code"], 124)
            self.assertTrue(result["timed_out"])
            self.assertLess(result["wall_seconds"], 5)


class LifecycleTests(unittest.TestCase):
    def test_remote_release_and_alchemy_quality_commands(self):
        self.assertEqual(remote.PRESETS["release"], [["python3", "tools/release_gate.py"]])
        self.assertEqual(remote.PRESETS["alchemy-quality"], [
            ["python3", "tools/run_chunky_acceptance.py", "--stage", "1"],
            ["python3", "tools/alchemy_quality_evidence.py"],
        ])
        combined = remote.commands_for('release-alchemy')
        self.assertEqual(combined, remote.PRESETS['release'] + remote.PRESETS['alchemy-quality'])
        self.assertEqual(sum('tools/release_gate.py' in command for command in combined), 1)
        self.assertEqual(remote.commands_for('alchemy-quality', 'medicine')[-1][-2:], ['--cases', 'medicine'])
        self.assertNotIn('--cases', remote.PRESETS['alchemy-quality'][-1])

    def test_invalid_selection_rejected_before_git_and_allocation(self):
        for preset, cases in (('alchemy-quality', ''), ('alchemy-quality', 'unknown'),
                              ('alchemy-quality', 'medicine,medicine'), ('alchemy-quality', 'all,ui'),
                              ('release-alchemy', 'medicine'), ('release', 'all')):
            with self.subTest(preset=preset, cases=cases), patch.object(runner.subprocess, 'check_output') as git:
                with self.assertRaises(ValueError):
                    runner.prepare(Path('/missing'), 'HEAD', preset, 180, Path('/missing'), cases)
                git.assert_not_called()

    def test_selection_canonical_order_and_no_arbitrary_commands(self):
        self.assertEqual(remote.select_cases('ui,medicine'), ['medicine', 'ui'])
        for value in ('medicine,', 'medicine; echo x', 'all,medicine'):
            with self.assertRaises(ValueError): remote.select_cases(value)

    def test_image_contains_pinned_node_openspec_and_preview_interpreter(self):
        modal = MagicMock()
        base = modal.Image.from_registry.return_value
        runner.image(modal)
        commands = base.apt_install.return_value.run_commands.call_args.args
        self.assertTrue(any(f"node-v{runner.NODE_VERSION}-linux-x64.tar.gz" in command for command in commands))
        self.assertTrue(any(runner.NODE_SHA256 in command and "sha256sum --check" in command for command in commands))
        self.assertTrue(any(f"@fission-ai/openspec@{runner.OPENSPEC_VERSION}" in command for command in commands))
        env = base.apt_install.return_value.run_commands.return_value.env.call_args.args[0]
        self.assertEqual(env["MC_PREVIEW_PYTHON"], "/usr/bin/python3")
        self.assertEqual(env["LIBGL_ALWAYS_SOFTWARE"], "1")

    def exercise(self, *, exit_code=0, upload_error=False, download_error=False, terminate_error=False, create_error=False, interrupt=False, corrupt=False):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            modal, sb = MagicMock(), MagicMock()
            modal.enable_output.return_value = nullcontext()
            modal.Sandbox.create.return_value = sb
            if create_error:
                modal.Sandbox.create.side_effect = RuntimeError("name already in use")
            sb.object_id = "sb-test"
            process = sb.exec.return_value
            process.stdout, process.stderr = [], []
            process.wait.return_value = exit_code
            if interrupt:
                process.wait.side_effect = KeyboardInterrupt("cancelled")
            if upload_error:
                sb.filesystem.copy_from_local.side_effect = RuntimeError("upload failed")
            if terminate_error:
                sb.terminate.side_effect = RuntimeError("termination failed")
            evidence = out / "input-evidence"
            evidence.mkdir()
            (evidence / "result.json").write_text("{}")
            metadata = remote.package(out / "repo", evidence, out / "source-artifacts.tar.gz")
            if corrupt:
                metadata["sha256"] = "invalid"
            def download(source, dest):
                if download_error:
                    raise RuntimeError("download failed")
                if source.endswith("artifacts.json"):
                    dest.write_text(json.dumps(metadata))
                else:
                    dest.write_bytes((out / "source-artifacts.tar.gz").read_bytes())
            sb.filesystem.copy_to_local.side_effect = download
            with patch.object(runner, "image", return_value="image"):
                result = runner.execute(modal, {"run_id": "test", "commit": "fixed", "preset": "smoke", "timeout_seconds": 180}, out, out)
            state = json.loads((out / "state.json").read_text())
            if create_error:
                sb.terminate.assert_not_called()
            else:
                sb.terminate.assert_called_once_with(wait=True)
                args = modal.Sandbox.create.call_args.kwargs
                self.assertEqual(args["cpu"], (4, 4))
                self.assertEqual(args["memory"], (16384, 16384))
                self.assertEqual(args["timeout"], 180)
            modal.Sandbox.create.assert_called_once()
            return result, state

    def test_success_downloads_and_confirms_termination(self):
        code, state = self.exercise()
        self.assertEqual(code, 0)
        self.assertEqual(state["termination"], "confirmed")
        self.assertEqual(state["artifacts"], "downloaded_and_verified")

    def test_test_failure_is_not_retried(self):
        code, state = self.exercise(exit_code=7)
        self.assertEqual(code, 7)
        self.assertEqual(state["termination"], "confirmed")

    def test_upload_failure_still_terminates(self):
        code, state = self.exercise(upload_error=True)
        self.assertNotEqual(code, 0)
        self.assertEqual(state["termination"], "confirmed")

    def test_download_failure_still_terminates(self):
        code, state = self.exercise(download_error=True)
        self.assertNotEqual(code, 0)
        self.assertIn("artifact_error", state)

    def test_termination_failure_cannot_pass(self):
        code, state = self.exercise(terminate_error=True)
        self.assertNotEqual(code, 0)
        self.assertEqual(state["termination"], "unconfirmed_ttl_backstop")

    def test_duplicate_name_failure_does_not_kill_other_job(self):
        code, state = self.exercise(create_error=True)
        self.assertNotEqual(code, 0)
        self.assertEqual(state["termination"], "not_created")

    def test_cancellation_collects_and_terminates(self):
        code, state = self.exercise(interrupt=True)
        self.assertNotEqual(code, 0)
        self.assertEqual(state["termination"], "confirmed")

    def test_corrupt_download_cannot_pass(self):
        code, state = self.exercise(corrupt=True)
        self.assertNotEqual(code, 0)
        self.assertIn("checksum", state["artifact_error"])


if __name__ == "__main__":
    unittest.main()
