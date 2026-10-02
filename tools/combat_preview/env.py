"""Repository paths and the preview interpreter. Standard library only: this module runs before
numpy and Pillow are known to be importable."""
from __future__ import annotations

import importlib.util
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_ROOT = REPO / "src/main/resources"
OUT_ROOT = REPO / "out/preview/combat_preview"
VENV_PYTHON = REPO / ".venv-preview/bin/python"
REQUIREMENTS = "tools/combat_preview/requirements.txt"
PYTHON_ENV = "MC_PREVIEW_PYTHON"
REEXEC_ENV = "MC_PREVIEW_REEXEC"  # set on the re-exec, so a second miss exits instead of looping
NEEDED = ("numpy", "PIL")
SETUP = f"python3 -m venv .venv-preview && .venv-preview/bin/pip install -r {REQUIREMENTS}"


def vanilla_jar(repo: Path = REPO) -> Path:
    """The vanilla client resources jar a Gradle build leaves under build/moddev/artifacts, for the
    NeoForge version in gradle.properties."""
    version = "21.1.233"
    props = repo / "gradle.properties"
    if props.is_file():
        m = re.search(r"^neo_version\s*=\s*(\S+)", props.read_text(encoding="utf-8"), re.M)
        if m:
            version = m.group(1)
    return repo / f"build/moddev/artifacts/neoforge-{version}-client-extra-aka-minecraft-resources.jar"


def note_missing_jar(path, no_vanilla: bool):
    if not no_vanilla and (not path or not Path(path).is_file()):
        print(f"note: vanilla resources jar {path} not found (a Gradle build writes it; or pass --vanilla-jar "
              "or --no-vanilla): vanilla parent models, textures and skins are unavailable", file=sys.stderr)


def missing_modules() -> list[str]:
    return [m for m in NEEDED if importlib.util.find_spec(m) is None]


def preview_python(environ=os.environ, venv_python: Path = VENV_PYTHON) -> str | None:
    """$MC_PREVIEW_PYTHON if set, else the repository's .venv-preview interpreter if present."""
    explicit = environ.get(PYTHON_ENV)
    if explicit:
        return explicit
    if venv_python.is_file():
        return str(venv_python)
    return None


def setup_message(missing, reexec=False) -> str:
    who = f"the preview interpreter {sys.executable}" if reexec else sys.executable
    return (f"ERROR: the combat preview needs numpy and pillow; {who} lacks {', '.join(missing)}.\n"
            f"Set up the preview interpreter from the repository root:\n  {SETUP}\n"
            f"or point {PYTHON_ENV} at a Python that has both.")


def ensure_interpreter(argv, environ=os.environ, execve=os.execve):
    """Return when numpy and Pillow import here; otherwise re-exec `-m tools.combat_preview argv`
    under the preview interpreter, or exit with the setup command."""
    missing = missing_modules()
    if not missing:
        return
    target = preview_python(environ)
    if environ.get(REEXEC_ENV) or target is None:
        raise SystemExit(setup_message(missing, reexec=bool(environ.get(REEXEC_ENV))))
    env = dict(environ)
    env[REEXEC_ENV] = "1"
    env["PYTHONPATH"] = os.pathsep.join(p for p in (str(REPO), environ.get("PYTHONPATH", "")) if p)
    try:
        execve(target, [target, "-m", "tools.combat_preview", *argv], env)
    except OSError as e:
        raise SystemExit(f"ERROR: cannot run the preview interpreter {target}: {e}\n"
                         f"Set it up from the repository root:\n  {SETUP}") from None
