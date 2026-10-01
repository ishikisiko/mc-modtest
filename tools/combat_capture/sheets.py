"""Labelled contact sheets (ImageMagick `montage`/`convert`) and the pairing
of two capture sets. Pairing and label text are pure functions."""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

from .capture import VIEWS
from .data import fmt_tick

FONT = "DejaVu-Sans"
CELL = (384, 216)
PAIR_CELL = (320, 180)
BG = "#1d1d21"
FG = "#ececec"


def cell_label(weapon: str, frame: dict, view: str) -> str:
    """Two lines: weapon and move; key, tick and view."""
    flag = "" if frame.get("stable", True) else "  [UNSTABLE]"
    return (f"{weapon}  m{frame['move']} {frame['move_short']}\n"
            f"{frame['key']}  t={fmt_tick(frame['tick'])}  {view}{flag}")


def _placeholder(tmp: Path, text: str, size=CELL) -> Path:
    p = tmp / f"ph_{abs(hash(text))}.png"
    if not p.exists():
        subprocess.run(["convert", "-size", f"{size[0]}x{size[1]}", "xc:#4a2222", "-font", FONT, "-pointsize", "18",
                        "-fill", FG, "-gravity", "center", "-annotate", "0", text, str(p)], check=True, timeout=60)
    return p


def view_sheet(capture_dir: Path, manifest: dict, view: str, out: Path) -> Path | None:
    frames = {(f["move"], f["key"]): f for f in manifest["frames"] if f["view"] == view}
    if not frames:
        return None
    moves = manifest["moves"]
    keys = [k["key"] for k in moves[0]["keys"]]
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        argv = ["montage", "-font", FONT, "-pointsize", "12", "-background", BG, "-fill", FG]
        for mv in moves:
            for key in keys:
                f = frames.get((mv["index"], key))
                if f is None:
                    tick = next((k["tick"] for k in mv["keys"] if k["key"] == key), None)
                    stub = {"move": mv["index"], "move_short": mv["short"], "key": key, "tick": tick or 0}
                    argv += ["-label", cell_label(manifest["weapon"], stub, view) + "  [missing]",
                             str(_placeholder(tmp, "missing"))]
                else:
                    argv += ["-label", cell_label(manifest["weapon"], f, view), str(capture_dir / f["file"])]
        title = (f"{manifest['label']}  |  {manifest['weapon']}  |  {VIEWS[view]['title']}  |  "
                 f"rows: moves 1-{len(moves)}, columns: {', '.join(keys)}")
        argv += ["-tile", f"{len(keys)}x{len(moves)}", "-geometry", f"{CELL[0]}x{CELL[1]}+4+4",
                 "-title", title, str(out)]
        subprocess.run(argv, check=True, timeout=300)
    return out


def build_capture_sheets(capture_dir: Path, manifest: dict) -> dict:
    sheets = {}
    for view in manifest["capture"]["views"]:
        out = Path(capture_dir) / f"sheet_{view}.png"
        if view_sheet(Path(capture_dir), manifest, view, out):
            sheets[view] = out.name
    return sheets


def combo_strip(capture_dir: Path, manifest: dict, count: int = 24, cols: int = 6) -> str | None:
    """Evenly spaced frames from the combo video between the first click and
    the expected end of the last move, each labelled with its video time."""
    combo = manifest.get("combo")
    if not combo:
        return None
    video = Path(capture_dir) / combo["video"]
    if not video.is_file():
        return None
    start = max(0.0, combo["lead_seconds"] - 1.0)  # ffmpeg start-up shifts the clicks earlier in the video
    end = min(combo["video_seconds"] - 0.1, combo["lead_seconds"] + combo["expected_combo_ticks"] * 0.05 + 1.0)
    times = [start + (end - start) * i / (count - 1) for i in range(count)]
    out = Path(capture_dir) / "combo" / "combo_strip.png"
    with tempfile.TemporaryDirectory() as td:
        argv = ["montage", "-font", FONT, "-pointsize", "12", "-background", BG, "-fill", FG]
        for i, t in enumerate(times):
            f = Path(td) / f"{i:03d}.png"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{t:.3f}", "-i", str(video),
                            "-frames:v", "1", "-vf", "scale=320:-2", str(f)], check=True, timeout=120)
            argv += ["-label", f"video t={t:.2f}s", str(f)]
        argv += ["-tile", f"{cols}x", "-geometry", "+3+3",
                 "-title", f"{manifest['label']}  |  {manifest['weapon']}  |  combo run, mapped left clicks", str(out)]
        subprocess.run(argv, check=True, timeout=300)
    return str(out.relative_to(capture_dir))


# ====================================================================== pairing
def frame_key(manifest: dict, frame: dict) -> tuple:
    return (manifest["weapon"], frame["move_id"], frame["key"], frame["view"])


def pair_frames(a: dict, b: dict) -> dict:
    """Pair two capture manifests by (weapon, move, key, view).
    Returns {"pairs": [...], "only_a": [...], "only_b": [...]} where each entry
    is {"weapon","move_id","move","move_short","key","view","a": frame|None,"b": frame|None},
    ordered by view, then A's move/key order (B-only entries last)."""
    fa = {frame_key(a, f): f for f in a.get("frames", [])}
    fb = {frame_key(b, f): f for f in b.get("frames", [])}
    order = list(fa) + [k for k in fb if k not in fa]
    view_rank = {v: i for i, v in enumerate(VIEWS)}
    order.sort(key=lambda k: view_rank.get(k[3], 99))
    out = {"pairs": [], "only_a": [], "only_b": []}
    for k in order:
        A, B = fa.get(k), fb.get(k)
        ref = A or B
        entry = {"weapon": k[0], "move_id": k[1], "move": ref["move"], "move_short": ref["move_short"],
                 "key": k[2], "view": k[3], "a": A, "b": B}
        if A and B:
            out["pairs"].append(entry)
        elif A:
            out["only_a"].append(entry)
        else:
            out["only_b"].append(entry)
    return out


def compare_sheet(dir_a: Path, dir_b: Path, label_a: str, label_b: str, entries: list[dict], view: str,
                  out: Path, title: str) -> Path | None:
    """Rows = moves, columns = keys; each cell holds A | B side by side.
    A cell present in one set only shows a 'only in ...' placeholder."""
    rows: dict[int, list[dict]] = {}
    for e in entries:
        if e["view"] == view:
            rows.setdefault(e["move"], []).append(e)
    if not rows:
        return None
    ncols = max(len(r) for r in rows.values())
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        cells = []
        for move in sorted(rows):
            for i, e in enumerate(rows[move]):
                pair = tmp / f"m{move}_{i}.png"
                argv = ["montage", "-font", FONT, "-pointsize", "11", "-background", BG, "-fill", FG]
                for side, d, lab in (("a", dir_a, label_a), ("b", dir_b, label_b)):
                    f = e[side]
                    if f is None:
                        argv += ["-label", f"{lab}: not in this set",
                                 str(_placeholder(tmp, f"only in {label_b if side == 'a' else label_a}", PAIR_CELL))]
                    else:
                        argv += ["-label", f"{lab}: t={fmt_tick(f['tick'])}" + ("" if f.get("stable", True) else " [UNSTABLE]"),
                                 str(Path(d) / f["file"])]
                argv += ["-tile", "2x1", "-geometry", f"{PAIR_CELL[0]}x{PAIR_CELL[1]}+2+2", str(pair)]
                subprocess.run(argv, check=True, timeout=120)
                marker = "" if e["a"] and e["b"] else "  [UNPAIRED]"
                cells.append((f"{e['weapon']}  m{move} {e['move_short']}\n{e['key']}  {view}{marker}", pair))
            for _ in range(ncols - len(rows[move])):
                cells.append(("", _placeholder(tmp, "-", (PAIR_CELL[0] * 2, PAIR_CELL[1]))))
        argv = ["montage", "-font", FONT, "-pointsize", "12", "-background", "#111114", "-fill", FG]
        for lab, p in cells:
            argv += ["-label", lab, str(p)]
        argv += ["-tile", f"{ncols}x{len(rows)}", "-geometry", "+6+6", "-title", title, str(out)]
        subprocess.run(argv, check=True, timeout=600)
    return out


def copy_tree(src: Path, dst: Path) -> None:
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
