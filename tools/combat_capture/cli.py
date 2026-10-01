"""Command line for the combat capture tooling. See tools/combat_capture/README.md."""
from __future__ import annotations

import argparse
import json
import re
import secrets
import shutil
import signal
import sys
import time
from pathlib import Path

from . import procs
from .data import DataError, fmt_tick, load_weapon
from .session import REPO, RIG_LOADED, SessionConfig

RESOURCES = REPO / "src/main/resources"
BUILD_RESOURCES = REPO / "build/resources/main"
OUT_ROOT = REPO / "out/preview/combat_capture"
DEFAULT_WEAPON = "myvillage:qingfeng_sword"
ALL_VIEWS = ("fp", "tp_back", "tp_front")
RELOAD_GLOBS = (
    "assets/*/combat/**/*.json",
    "assets/*/player_animations/*.json",
    "assets/*/models/item/*.json",
    "assets/*/textures/item/*.png",
)
LABEL_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")


class UsageError(Exception):
    pass


def log(msg: str = ""):
    print(msg, flush=True)


# ---------------------------------------------------------------- argument helpers
def check_label(label: str) -> str:
    if not LABEL_RE.fullmatch(label or ""):
        raise argparse.ArgumentTypeError(f"label {label!r}: use letters, digits, '_', '.', '-' (max 80)")
    return label


def parse_views(text: str) -> list[str]:
    views = [v.strip() for v in text.split(",") if v.strip()]
    bad = [v for v in views if v not in ALL_VIEWS]
    if bad or not views:
        raise argparse.ArgumentTypeError(f"views must be a comma list of {', '.join(ALL_VIEWS)}; got {text!r}")
    return [v for v in ALL_VIEWS if v in views]  # canonical order


def parse_moves(text: str | None, available: list[int]) -> list[int]:
    if not text:
        return list(available)
    try:
        moves = sorted({int(x) for x in text.split(",") if x.strip()})
    except ValueError:
        raise UsageError(f"--moves must be a comma list of move numbers, got {text!r}") from None
    missing = [n for n in moves if n not in available]
    if missing or not moves:
        raise UsageError(f"moves {missing or text} not in the style (available {available})")
    return moves


def require_programs():
    missing = procs.missing_programs()
    if missing:
        raise UsageError("missing host program(s): " + ", ".join(missing)
                         + " (needs Xvfb, xdotool, ffmpeg and ImageMagick import/convert/montage)")


def capture_dir(root: Path, label: str) -> Path:
    return Path(root) / label


def session_config(a) -> SessionConfig:
    return SessionConfig(session_id=secrets.token_hex(6), username=a.username,
                         lock=str(a.lock or procs.default_lock_path(REPO)), lock_timeout=a.lock_timeout,
                         settle=a.settle, gradle_jvmargs=a.gradle_jvmargs)


# ---------------------------------------------------------------- commands
def cmd_check(a):
    missing = procs.missing_programs()
    for name in procs.REQUIRED_PROGRAMS:
        log(f"  {name:<8} {'MISSING' if name in missing else 'ok'}")
    w = load_weapon(RESOURCES, a.weapon)
    log(f"weapon {w.id}: item {w.item}, style {w.style_id}, {len(w.moves)} moves, rig {w.files['rig']}")
    log(f"lock {a.lock or procs.default_lock_path(REPO)}")
    if missing:
        raise UsageError("missing host program(s): " + ", ".join(missing))


def cmd_ticks(a):
    w = load_weapon(RESOURCES, a.weapon)
    log(f"{w.id} -> style {w.style_id}, rig {w.files['rig']}")
    for m in w.moves:
        keys = "  ".join(f"{k}={fmt_tick(t)}" for k, t in m.keys)
        log(f"  {m.index}  {m.id:<42} total {m.total_ticks:>2}  {keys}")


def cmd_session(a):
    from . import session
    if a.action == "start":
        require_programs()
        st = session.start(session_config(a), wait_timeout=a.timeout, log=log)
        log(f"ready: display {st['display']}, player {st['username']}, startup {st['timings']}")
    elif a.action == "status":
        session.status(log=log)
    else:
        session.stop(log=log)


def cmd_rcon(a):
    from .session import Session
    s = Session.attach(log=log)
    for c in a.commands:
        log(f"> {c}")
        log(s.run(c, warn=False)[0])


def cmd_ui_state(a):
    from .session import Session
    s = Session.attach(log=log)
    state = s.game.pointer_grabbed()
    log({True: "ingame (no screen open)", False: "screen open or window unfocused", None: "unknown"}[state])


def cmd_scene(a):
    from . import scene
    from .session import Session
    s = Session.attach(log=log)
    w = load_weapon(RESOURCES, a.weapon)
    log(f"hand: {scene.base_scene(s, w.item)}")
    log(f"combat mode: {scene.ensure_cultivation(s)}")
    if a.kind == "combo":
        for t in scene.place_targets(s, a.targets):
            log(f"  target {t['tag']} {t['kind']} at {t['pos']} health {t['health']}")


def changed_resources(src_root: Path, dst_root: Path, globs=RELOAD_GLOBS) -> list[str]:
    changed = []
    for pattern in globs:
        for p in sorted(src_root.glob(pattern)):
            if not p.is_file():
                continue
            rel = str(p.relative_to(src_root))
            dst = dst_root / rel
            if not dst.is_file() or dst.read_bytes() != p.read_bytes():
                if rel not in changed:
                    changed.append(rel)
    return changed


def cmd_reload(a):
    from .session import Session
    src, dst = Path(a.src), BUILD_RESOURCES
    changed = changed_resources(src, dst, a.paths or RELOAD_GLOBS)
    for rel in changed:
        p = src / rel
        if p.suffix == ".json":
            try:
                json.loads(p.read_text(encoding="utf-8"))
            except ValueError as e:
                raise UsageError(f"refusing to copy {rel}: invalid JSON ({e})") from None
    for rel in changed:
        (dst / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src / rel, dst / rel)
        log(f"  copied {rel}")
    log(f"{len(changed)} changed file(s) copied into build/resources/main")
    if a.no_reload:
        return
    s = Session.attach(log=log)
    res = s.game.f3_t(a.rig_pattern, settle=a.settle)
    log(f"reloaded in {res['seconds']}s: {res['rig_line']}")
    for p in res["problems"]:
        log(f"  PROBLEM: {p[:300]}")


def prepare_out(root: Path, label: str, force: bool) -> Path:
    out = capture_dir(root, label)
    if out.exists():
        if not force:
            raise UsageError(f"{out} exists; pick another --label or pass --force to replace it")
        shutil.rmtree(out)
    out.mkdir(parents=True)
    return out


def finalize(out: Path) -> dict:
    """Sheets, combo strip and index.html from whatever the manifest holds."""
    from . import capture, page, sheets
    m = capture.load_manifest(out)
    m["sheets"] = sheets.build_capture_sheets(out, m)
    if m.get("combo"):
        strip = sheets.combo_strip(out, m)
        if strip:
            m["combo"]["strip"] = strip
    capture.write_manifest(out, m)
    (out / "index.html").write_text(page.capture_page(m), encoding="utf-8")
    log(f"page {out / 'index.html'}")
    return m


def _stills(s, w, out, m, views, moves):
    from .capture import Stills
    Stills(s, w, out, m, log=log).run(views, moves)


def cmd_stills(a):
    from . import capture
    from .session import Session
    require_programs()
    w = load_weapon(RESOURCES, a.weapon)
    moves = parse_moves(a.moves, [m.index for m in w.moves])
    s = Session.attach(ui_check=not a.no_ui_check, log=log)
    out = prepare_out(a.out_root, a.label, a.force)
    m = capture.new_manifest(a.label, w, RESOURCES, a.views, moves)
    capture.write_manifest(out, m)
    try:
        _stills(s, w, out, m, a.views, moves)
    finally:
        finalize(out)


def cmd_combo(a):
    from . import capture
    from .session import Session
    require_programs()
    w = load_weapon(RESOURCES, a.weapon)
    s = Session.attach(ui_check=not a.no_ui_check, log=log)
    out = capture_dir(a.out_root, a.label)
    if (out / capture.MANIFEST).is_file():
        m = capture.load_manifest(out)  # add the combo to an existing capture set
        if m["weapon"] != w.id:
            raise UsageError(f"{out} holds a capture of {m['weapon']}, not {w.id}")
    else:
        out.mkdir(parents=True, exist_ok=True)
        m = capture.new_manifest(a.label, w, RESOURCES, [], [mv.index for mv in w.moves])
    try:
        capture.run_combo(s, w, out, m, targets=a.targets, log=log)
    finally:
        finalize(out)


def cmd_page(a):
    require_programs()
    finalize(Path(a.dir))


def cmd_compare(a):
    from . import capture, page, sheets
    require_programs()
    da, db = Path(a.a), Path(a.b)
    ma, mb = capture.load_manifest(da), capture.load_manifest(db)
    out = prepare_out(a.out_root, a.label, a.force)
    paired = sheets.pair_frames(ma, mb)
    entries = sorted(paired["pairs"] + paired["only_a"] + paired["only_b"],
                     key=lambda e: (list(ALL_VIEWS).index(e["view"]) if e["view"] in ALL_VIEWS else 9,
                                    e["move"], _key_rank(e["key"])))
    views = [v for v in ALL_VIEWS if any(e["view"] == v for e in entries)]
    sheet_files = {}
    for view in views:
        p = out / f"compare_{view}.png"
        title = f"{a.label}  |  A: {ma['label']}  vs  B: {mb['label']}  |  {capture.VIEWS[view]['title']}"
        if sheets.compare_sheet(da, db, ma["label"], mb["label"], entries, view, p, title):
            sheet_files[view] = p.name
            log(f"sheet {p}")
    for side, d, m in (("a", da, ma), ("b", db, mb)):
        if m.get("combo") and (d / "combo").is_dir():
            shutil.copytree(d / "combo", out / side / "combo")
    unpaired = [{"view": e["view"], "move_id": e["move_id"], "key": e["key"],
                 "in": f"A ({ma['label']})" if e["a"] else f"B ({mb['label']})"}
                for e in paired["only_a"] + paired["only_b"]]
    links = []
    if da.resolve().parent == out.resolve().parent:
        links.append((f"A: {ma['label']}", f"../{da.name}/index.html"))
    if db.resolve().parent == out.resolve().parent:
        links.append((f"B: {mb['label']}", f"../{db.name}/index.html"))
    cmp = {"schema": 1, "kind": "comparison", "label": a.label, "created": capture.now_iso(),
           "a": ma, "b": mb,
           "counts": {k: len(v) for k, v in paired.items()}, "unpaired": unpaired, "sheets": sheet_files,
           "links": links}
    (out / "comparison.json").write_text(json.dumps(cmp, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (out / "index.html").write_text(page.comparison_page(cmp), encoding="utf-8")
    log(f"pairs {cmp['counts']['pairs']}, only A {cmp['counts']['only_a']}, only B {cmp['counts']['only_b']}")
    log(f"page {out / 'index.html'}")


def _key_rank(key: str) -> int:
    from .data import KEY_NAMES
    return KEY_NAMES.index(key) if key in KEY_NAMES else len(KEY_NAMES)


def cmd_run(a):
    """session start -> stills -> combo -> session stop -> sheets and page."""
    from . import capture, session
    from .session import Session
    require_programs()
    w = load_weapon(RESOURCES, a.weapon)
    moves = parse_moves(a.moves, [m.index for m in w.moves])
    out = prepare_out(a.out_root, a.label, a.force)
    m = capture.new_manifest(a.label, w, RESOURCES, a.views, moves)
    capture.write_manifest(out, m)
    t0 = time.time()
    timings = {}
    failure = None
    try:
        st = session.start(session_config(a), wait_timeout=a.timeout, log=log)
        timings["lock_wait_s"] = st["timings"].get("lock_wait_s")
        timings["session_start_s"] = st["timings"].get("startup_s")
        s = Session.attach(ui_check=not a.no_ui_check, log=log)
        t1 = time.time()
        if a.views:
            _stills(s, w, out, m, a.views, moves)
        timings["stills_s"] = round(time.time() - t1, 1)
        if not a.no_combo:
            t2 = time.time()
            capture.run_combo(s, w, out, m, targets=a.targets, log=log)
            timings["combo_s"] = round(time.time() - t2, 1)
    except BaseException as e:  # noqa: BLE001 - stop the session, keep partial output, re-raise
        failure = e
        m.setdefault("notes", []).append(f"run failed: {type(e).__name__}: {e}"[:500])
        capture.write_manifest(out, m)
    finally:
        t3 = time.time()
        session.stop(log=log)
        timings["session_stop_s"] = round(time.time() - t3, 1)
    m = capture.load_manifest(out)
    t4 = time.time()
    m["timings"] = timings
    capture.write_manifest(out, m)
    finalize(out)
    timings["sheets_page_s"] = round(time.time() - t4, 1)
    timings["total_s"] = round(time.time() - t0, 1)
    m = capture.load_manifest(out)
    m["timings"] = timings
    capture.write_manifest(out, m)
    log(f"timings {timings}")
    if failure is not None:
        raise failure


# ---------------------------------------------------------------- parser
def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(prog="python3 -m tools.combat_capture", description=__doc__)
    sub = ap.add_subparsers(dest="command", required=True)

    def session_opts(p):
        p.add_argument("--username", default="CaptureDev")
        p.add_argument("--lock", help="heavy-work lock file (default $MC_HEAVY_LOCK or .mc-heavy.lock beside the main checkout)")
        p.add_argument("--lock-timeout", type=float, default=5400.0, help="seconds to wait for the lock")
        p.add_argument("--timeout", type=float, default=7800.0, help="seconds to wait for the session to be ready")
        p.add_argument("--settle", type=float, default=10.0, help="seconds to wait after the client is in game")
        p.add_argument("--gradle-jvmargs", default="-Xmx1g", help="org.gradle.jvmargs for the two Gradle runs")

    def capture_opts(p, label=True):
        p.add_argument("--weapon", default=DEFAULT_WEAPON)
        if label:
            p.add_argument("--label", required=True, type=check_label)
        p.add_argument("--out-root", type=Path, default=OUT_ROOT)
        p.add_argument("--no-ui-check", action="store_true", help="send keys without the in-game check (unsafe)")

    p = sub.add_parser("check", help="report host programs and the weapon's data (light)")
    p.add_argument("--weapon", default=DEFAULT_WEAPON)
    p.add_argument("--lock")
    p.set_defaults(func=cmd_check)

    p = sub.add_parser("ticks", help="print the weapon's moves and key ticks (light)")
    p.add_argument("--weapon", default=DEFAULT_WEAPON)
    p.set_defaults(func=cmd_ticks)

    p = sub.add_parser("session", help="start, inspect or stop the Xvfb + server + client session")
    p.add_argument("action", choices=("start", "status", "stop"))
    session_opts(p)
    p.set_defaults(func=cmd_session)

    p = sub.add_parser("rcon", help="run server commands in the session")
    p.add_argument("commands", nargs="+")
    p.set_defaults(func=cmd_rcon)

    p = sub.add_parser("ui-state", help="is the client in game with no screen open?")
    p.set_defaults(func=cmd_ui_state)

    p = sub.add_parser("scene", help="set up the stills or combo scene")
    p.add_argument("kind", choices=("stills", "combo"))
    p.add_argument("--weapon", default=DEFAULT_WEAPON)
    p.add_argument("--targets", choices=("dummy", "golem"), default="dummy")
    p.set_defaults(func=cmd_scene)

    p = sub.add_parser("reload", help="copy changed combat client resources to build/resources/main and F3+T")
    p.add_argument("--src", default=str(RESOURCES), help="resources root to copy from")
    p.add_argument("--paths", nargs="+", help=f"globs relative to --src (default {' '.join(RELOAD_GLOBS)})")
    p.add_argument("--no-reload", action="store_true", help="copy only")
    p.add_argument("--rig-pattern", default=RIG_LOADED, help="client log line that marks the rig load")
    p.add_argument("--settle", type=float, default=5.0)
    p.set_defaults(func=cmd_reload)

    p = sub.add_parser("stills", help="per-move stills at the rig's key ticks in a running session")
    capture_opts(p)
    p.add_argument("--views", type=parse_views, default=list(ALL_VIEWS))
    p.add_argument("--moves", help="comma list of move numbers (default all)")
    p.add_argument("--force", action="store_true", help="replace an existing capture with this label")
    p.set_defaults(func=cmd_stills)

    p = sub.add_parser("combo", help="mapped-click combo run with video and target health")
    capture_opts(p)
    p.add_argument("--targets", choices=("dummy", "golem"), default="dummy")
    p.set_defaults(func=cmd_combo)

    p = sub.add_parser("compare", help="pair two capture sets into side-by-side sheets and a page")
    p.add_argument("a", help="capture directory A (left)")
    p.add_argument("b", help="capture directory B (right)")
    p.add_argument("--label", required=True, type=check_label)
    p.add_argument("--out-root", type=Path, default=OUT_ROOT)
    p.add_argument("--force", action="store_true")
    p.set_defaults(func=cmd_compare)

    p = sub.add_parser("page", help="rebuild sheets and index.html of a capture directory")
    p.add_argument("dir")
    p.set_defaults(func=cmd_page)

    p = sub.add_parser("run", help="full pass: session start, stills, combo, session stop, page")
    capture_opts(p)
    session_opts(p)
    p.add_argument("--views", type=parse_views, default=list(ALL_VIEWS))
    p.add_argument("--moves", help="comma list of move numbers (default all)")
    p.add_argument("--targets", choices=("dummy", "golem"), default="dummy")
    p.add_argument("--no-combo", action="store_true")
    p.add_argument("--force", action="store_true")
    p.set_defaults(func=cmd_run)
    return ap


def main(argv=None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv[:1] == ["_supervise"]:
        from .session import supervise_main
        return supervise_main(argv[1:])
    ap = build_parser()
    a = ap.parse_args(argv)

    def _terminate(signum, frame):  # SIGTERM runs the same cleanup as Ctrl-C (session stop in `run`)
        raise KeyboardInterrupt(f"signal {signum}")

    signal.signal(signal.SIGTERM, _terminate)
    try:
        a.func(a)
    except (UsageError, DataError) as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("interrupted", file=sys.stderr)
        return 130
    except Exception as e:  # noqa: BLE001 - one line for operators; details are in the session logs
        print(f"ERROR: {type(e).__name__}: {e}", file=sys.stderr)
        return 1
    return 0
