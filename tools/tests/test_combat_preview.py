"""tools/combat_preview without numpy: the command line and the choice of interpreter.
The rendering itself is checked by test_combat_preview_parity.py under the preview interpreter."""
from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.combat_preview import cli, env


class PreviewPythonTest(unittest.TestCase):
    def test_environment_variable_wins_over_the_repository_venv(self):
        with tempfile.TemporaryDirectory() as tmp:
            venv = Path(tmp) / "python"
            venv.write_text("")
            self.assertEqual(env.preview_python({env.PYTHON_ENV: "/opt/py/bin/python"}, venv), "/opt/py/bin/python")
            self.assertEqual(env.preview_python({}, venv), str(venv))
            self.assertIsNone(env.preview_python({}, Path(tmp) / "absent"))
            self.assertEqual(env.preview_python({env.PYTHON_ENV: ""}, venv), str(venv))


class EnsureInterpreterTest(unittest.TestCase):
    def ensure(self, environ, missing=("numpy",), target="/venv/bin/python", execve=None):
        execve = execve or mock.Mock()
        with mock.patch.object(env, "missing_modules", return_value=list(missing)), \
                mock.patch.object(env, "preview_python", return_value=target):
            env.ensure_interpreter(["fp", "--weapon", "x"], environ, execve)
        return execve

    def test_nothing_happens_when_numpy_and_pillow_import(self):
        execve = self.ensure({}, missing=())
        execve.assert_not_called()

    def test_reexecs_the_same_command_under_the_preview_interpreter(self):
        execve = self.ensure({"PYTHONPATH": "/elsewhere"})
        path, argv, environ = execve.call_args.args
        self.assertEqual(path, "/venv/bin/python")
        self.assertEqual(argv, ["/venv/bin/python", "-m", "tools.combat_preview", "fp", "--weapon", "x"])
        self.assertEqual(environ[env.REEXEC_ENV], "1")
        self.assertEqual(environ["PYTHONPATH"].split(":"), [str(env.REPO), "/elsewhere"])

    def test_without_a_preview_interpreter_it_exits_with_the_setup_command(self):
        with self.assertRaises(SystemExit) as caught:
            self.ensure({}, target=None)
        self.assertIn(env.SETUP, str(caught.exception.code))
        self.assertIn(env.PYTHON_ENV, str(caught.exception.code))

    def test_a_second_miss_after_the_reexec_exits_instead_of_looping(self):
        execve = mock.Mock()
        with self.assertRaises(SystemExit) as caught:
            self.ensure({env.REEXEC_ENV: "1"}, execve=execve)
        execve.assert_not_called()
        self.assertIn("the preview interpreter", str(caught.exception.code))
        self.assertIn(env.SETUP, str(caught.exception.code))

    def test_an_unrunnable_interpreter_exits_with_the_setup_command(self):
        with self.assertRaises(SystemExit) as caught:
            self.ensure({}, execve=mock.Mock(side_effect=FileNotFoundError("no such file")))
        self.assertIn(env.SETUP, str(caught.exception.code))


class VanillaJarTest(unittest.TestCase):
    def test_jar_name_follows_the_neoforge_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            (repo / "gradle.properties").write_text("mod_version=1\nneo_version=21.1.999\n", encoding="utf-8")
            self.assertEqual(env.vanilla_jar(repo), repo / "build/moddev/artifacts/"
                             "neoforge-21.1.999-client-extra-aka-minecraft-resources.jar")


class CommandLineTest(unittest.TestCase):
    def run_cli(self, argv):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(cli, "ensure_interpreter") as ensure, \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            rc = cli.main(argv)
        return rc, out.getvalue() + err.getvalue(), ensure

    def test_help_and_bad_tool_need_no_numpy(self):
        for argv, code in (([], 2), (["-h"], 0), (["render"], 2)):
            with self.subTest(argv=argv):
                rc, text, ensure = self.run_cli(argv)
                self.assertEqual(rc, code)
                self.assertIn("fp|pose|model", text)
                ensure.assert_not_called()

    def test_tool_runs_with_its_own_arguments_after_the_interpreter_check(self):
        with mock.patch("importlib.import_module") as load:
            load.return_value.main.return_value = 0
            rc, _, ensure = self.run_cli(["pose", "--animations", "a.json"])
        self.assertEqual(rc, 0)
        ensure.assert_called_once_with(["pose", "--animations", "a.json"])
        load.assert_called_once_with("tools.combat_preview.pal_pose")
        load.return_value.main.assert_called_once_with(["--animations", "a.json"])


if __name__ == "__main__":
    unittest.main()
