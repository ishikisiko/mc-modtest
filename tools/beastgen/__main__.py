"""python3 -m tools.beastgen build <beast> [--check] | preview <beast> [options]

build writes (or with --check verifies) the beast's model JSON, animations JSON, texture and glow
layer under src/main/resources; standard library only. preview renders the offline sheets, GIFs and
index.html under out/preview/<beast>/; it needs numpy and Pillow and re-runs itself under the
preview interpreter (.venv-preview, or $MC_PREVIEW_PYTHON) when the current one lacks them.
"""
import argparse
import os
import sys


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    ap = argparse.ArgumentParser(prog="python3 -m tools.beastgen", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build", help="write or check the runtime files")
    b.add_argument("beast")
    b.add_argument("--check", action="store_true", help="fail when the files on disk differ")
    p = sub.add_parser("preview", help="offline renders under out/preview/<beast>/")
    p.add_argument("beast")
    p.add_argument("--only", help="comma list of parts: views,atlas,sheets,gifs,index")
    p.add_argument("--size", type=int, default=0, help="override the frame size (pixels)")
    a = ap.parse_args(argv)
    if a.cmd == "build":
        from .build import run
        return run(a.beast, check=a.check)
    _ensure_preview_interpreter(argv)
    from .preview import run as preview_run
    return preview_run(a.beast, only=a.only, size=a.size)


def _ensure_preview_interpreter(argv):
    from ..combat_preview import env
    missing = env.missing_modules()
    if not missing:
        return
    target = env.preview_python()
    if os.environ.get(env.REEXEC_ENV) or target is None:
        raise SystemExit(env.setup_message(missing, reexec=bool(os.environ.get(env.REEXEC_ENV))))
    e = dict(os.environ)
    e[env.REEXEC_ENV] = "1"
    e["PYTHONPATH"] = os.pathsep.join(x for x in (str(env.REPO), os.environ.get("PYTHONPATH", "")) if x)
    os.execve(target, [target, "-m", "tools.beastgen", *argv], e)


if __name__ == "__main__":
    sys.exit(main())
