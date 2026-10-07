"""python3 -m tools.npcgen build <npc> [--check] | goldens [--check] | preview <npc> [options]

build writes (or with --check verifies) the NPC's model JSON, animations JSON, texture and role map
under src/main/resources; standard library only. goldens writes (or verifies) the recolour goldens
for the Java NpcSkinComposer under src/test/resources/npc_skin_goldens/; standard library only. preview renders the offline sheets, GIFs and index.html
under out/preview/<npc>/; it needs numpy and Pillow and re-runs itself under the preview interpreter
(.venv-preview, or $MC_PREVIEW_PYTHON) when the current one lacks them.
"""
import argparse
import os
import sys


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    ap = argparse.ArgumentParser(prog="python3 -m tools.npcgen", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build", help="write or check the runtime files")
    b.add_argument("npc")
    b.add_argument("--check", action="store_true", help="fail when the files on disk differ")
    g = sub.add_parser("goldens", help="write or check the NpcSkinComposer goldens")
    g.add_argument("--check", action="store_true", help="fail when the files on disk differ")
    p = sub.add_parser("preview", help="offline renders under out/preview/<npc>/")
    p.add_argument("npc")
    p.add_argument("--only", help="comma list of parts: views,closeups,face,atlas,sheets,gifs,index")
    a = ap.parse_args(argv)
    if a.cmd == "build":
        from .build import run
        return run(a.npc, check=a.check)
    if a.cmd == "goldens":
        from .recolour import write_goldens
        return write_goldens(check=a.check)
    _ensure_preview_interpreter(argv)
    from .preview import run as preview_run
    return preview_run(a.npc, only=a.only)


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
    os.execve(target, [target, "-m", "tools.npcgen", *argv], e)


if __name__ == "__main__":
    sys.exit(main())
