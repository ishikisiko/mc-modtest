"""Candidate sheets for first-person rig values (numpy rasteriser via fp; light).

    python3 -m tools.combat_preview sweep --weapon myvillage:lingxiao_spear \\
        --set rig.off_hand.thickness=0.42,0.5,0.56,0.62 --frames 1:0,1:4,2:6,3:7.5,5:7.4 \\
        --out out/preview/combat_preview/sweep_offarm

--set <json.path>=<v1>,<v2>,...  A dotted path into the rig file (keys separated by '.', list
    indices in brackets: rig.shoulder[1], neutral.off_hand_slide, moves.<move id>.keys[3].reach) and
    the candidate values, each a JSON value (0.5, [0.3,-0.27,-0.05], true) or a bare word (out_back).
    Commas inside [...] do not split. A path missing from the base rig is allowed only when its parent
    object exists (an optional field is introduced); anything else fails naming the path.
    Several --set: one column per combination, the first --set varying slowest (at most 16 columns).
--frames <move>:<tick>,...  1-based move numbers; the tick is a number or a capture key name
    (idle, strike_start, contact, strike_end, recovery). Default: 1:idle (every move starts at the
    same neutral pose with no lag) and every move's contact tick.

Each candidate rig is the base rig file (--rig, default the weapon's shipped rig) with only the
addressed value edited in the text, written to <out>/rigs/ and loaded through the fp tool's real
rig loader, so a value the game would reject fails here naming the candidate; the file can be
copied over the base as it is (a one-token diff). Frames are rendered at 960x540 as the freeze
probe shows them (no point marks), and compared with the base rig's frame: a pixel is changed when
its summed |RGB difference| exceeds --threshold (default 36, as fp --key-threshold).

Writes into --out: grid.png (rows = frames, columns = candidates, the base value's column outlined
in gold; it is added as an extra column when no candidate equals it), zoom.png and zoom/<frame>.png
(each frame cropped to where the candidates differ, plus a margin, enlarged by a whole factor),
rigs/c<n>_<slug>.json, summary.json and summary.txt (per candidate and frame: changed pixels
against the base and the solver's warnings: shoulder clamped, off hand slid along the shaft).
A candidate other than the base value that changes no pixel in any frame is reported loudly (the
renderer ignores the path, the value equals the loader default, or these frames do not show it);
when every candidate is like that the command exits 1.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import ImageDraw

from . import fp_rig, sheets
from .diff import ZOOM_MAX, ZOOM_MIN_W, bbox_of, change_mask, labelled_rows, pad_box, zoom, zoom_factor
from .env import DEFAULT_ROOT, REPO, note_missing_jar
from .tuning import (KEY_THRESHOLD, TuningError, check_sets, combinations, edit_text, frame_name,
                     lookup, parse_frames, parse_set, same_value, slug, value_text)

REF_RGB = (250, 200, 90)
ZOOM_MARGIN = 24


def build_parser():
    ap = argparse.ArgumentParser(prog="python3 -m tools.combat_preview sweep", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--weapon", required=True, help="weapon id (data/<ns>/combat/weapon/<path>.json)")
    ap.add_argument("--rig", help="base rig JSON (default: the weapon file's first_person_rig)")
    ap.add_argument("--set", dest="sets", action="append", required=True, metavar="PATH=V1,V2,...",
                    help="rig field (dotted JSON path) and its candidate values; repeat for every combination")
    ap.add_argument("--frames", help="<move>:<tick or key>,... (default 1:idle and every move's contact)")
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--threshold", type=float, default=KEY_THRESHOLD,
                    help=f"changed when the summed |RGB difference| exceeds this (default {KEY_THRESHOLD:g})")
    ap.add_argument("--cell", type=int, default=480, help="frame width in grid.png (16:9)")
    # passed to the fp tool's own parser, whose defaults apply when omitted
    ap.add_argument("--skin", help="as fp --skin (default: the capture client's skin)")
    ap.add_argument("--arms", choices=("slim", "wide"), help="as fp --arms")
    ap.add_argument("--main-arm", choices=("right", "left"), help="as fp --main-arm (default right)")
    ap.add_argument("--off-hand-occupied", action="store_true", help="as fp: the rig's off arm is not drawn")
    ap.add_argument("--no-sleeve", action="store_true", help="as fp: skip the sleeve layer")
    ap.add_argument("--root", action="append", default=[], help="as fp --root")
    ap.add_argument("--geometry", help="as fp --geometry")
    ap.add_argument("--vanilla-jar", help="as fp --vanilla-jar")
    ap.add_argument("--no-vanilla", action="store_true", help="as fp --no-vanilla")
    return ap


def fp_namespace(a, rig):
    """The fp tool's parsed options for one rig file, from its own parser (so its defaults hold)."""
    argv = ["--weapon", a.weapon, "--out", "-"]
    for r in a.root:
        argv += ["--root", r]
    for flag, value in (("--rig", rig), ("--geometry", a.geometry), ("--skin", a.skin), ("--arms", a.arms),
                        ("--main-arm", a.main_arm), ("--vanilla-jar", a.vanilla_jar)):
        if value:
            argv += [flag, str(value)]
    for flag, on in (("--off-hand-occupied", a.off_hand_occupied), ("--no-sleeve", a.no_sleeve),
                     ("--no-vanilla", a.no_vanilla)):
        if on:
            argv.append(flag)
    ns = fp_rig.parser().parse_args(argv)
    if not ns.root:
        ns.root = [str(DEFAULT_ROOT)]
    return ns


def load_scene(ns, who):
    """fp_rig.Scene for ns, with the loud messages its loading raised; loader errors name `who`."""
    start = len(fp_rig.FAIL)
    try:
        scene = fp_rig.Scene(ns)
    except SystemExit as e:
        msg = str(e.code)
        raise SystemExit(f"ERROR: {who}: {msg[7:] if msg.startswith('ERROR: ') else msg}") from None
    return scene, list(fp_rig.FAIL[start:])


def rel(p) -> str:
    p = Path(p)
    try:
        return str(p.resolve().relative_to(REPO))
    except ValueError:
        return str(p)


def resolve_frames(scene, text):
    """[{name, label, move, move_id, tick, key}] for --frames (or the default) against the rig's moves."""
    n_moves = len(scene.rig.moves)
    specs = parse_frames(text) if text else [(1, None, "idle")] + [(n, None, "contact") for n in range(1, n_moves + 1)]
    out = []
    for m, tick, key in specs:
        if not 1 <= m <= n_moves:
            raise TuningError(f"--frames {m}:...: the style has moves 1..{n_moves}")
        mv = scene.rig.moves[m - 1]
        keys = mv.capture_ticks()
        if key:
            tick = dict(keys)[key]
        elif tick > mv.total:
            raise TuningError(f"--frames {m}:{tick:g}: move {m} ends at tick {mv.total}")
        same = key or next((k for k, t in keys if abs(t - tick) < 1e-9), None)
        name = frame_name(m, tick, key)
        if any(f["name"] == name for f in out):
            raise TuningError(f"--frames: {name} is listed twice")
        out.append({"name": name, "label": f"m{m} t {tick:g}" + (f" {same}" if same else ""),
                    "move": m, "move_id": mv.id, "tick": float(tick), "key": same})
    return out


def solver_warnings(m):
    """The fp report's loud solver states for one frame."""
    out = []
    if m.get("ik", {}).get("shoulder_clamped"):
        out.append("SHOULDER CLAMPED")
    o = m.get("off_ik")
    if o:
        if o.get("slid"):
            out.append(f"OFF HAND SLID {o['wanted_grip_y_px']:.1f}->{o['grip_y_px']:.1f} px")
        if o.get("shoulder_clamped"):
            out.append("OFF SHOULDER CLAMPED")
    return out


def render(scene, frame):
    mv = scene.rig.moves[frame["move"] - 1]
    a = scene.a
    start = len(fp_rig.FAIL)
    im, fr, pose, sol, pts = fp_rig.render_image(scene, mv, frame["tick"], fp_rig.GAME_W, fp_rig.GAME_H, ss=a.ss,
                                                 lag=not a.no_lag, trail=not a.no_trail, marks=False, hud=False)
    m = fp_rig.measure(scene, fr, pts, sol, mv, frame["tick"], pose)
    return im.convert("RGB"), solver_warnings(m) + list(fp_rig.FAIL[start:])


def with_hud(img):
    """The frame with the HUD boxes outlined (white, 2 px): in game the hotbar and hearts cover them."""
    img = img.copy()
    d = ImageDraw.Draw(img)
    for x0, y0, x1, y1 in fp_rig.HUD_BOXES:
        d.rectangle((x0, y0, x1 - 1, y1 - 1), outline=(255, 255, 255), width=2)
    return img


def outline(img, color=REF_RGB, width=4):
    img = img.copy()
    ImageDraw.Draw(img).rectangle((0, 0, img.width - 1, img.height - 1), outline=color, width=width)
    return img


def main(argv=None):
    a = build_parser().parse_args(argv)
    try:
        sets = [parse_set(s) for s in a.sets]
        check_sets(sets)
    except TuningError as e:
        raise SystemExit(f"ERROR: {e}")
    ref_word = "base" if a.rig else "shipped"
    base_ns = fp_namespace(a, a.rig)
    note_missing_jar(base_ns.vanilla_jar, base_ns.no_vanilla)
    base_scene, base_loud = load_scene(base_ns, f"the {ref_word} rig")
    base_path = base_scene.rig_path
    base_text = base_path.read_text(encoding="utf-8")
    base_doc = json.loads(base_text)
    try:
        frames = resolve_frames(base_scene, a.frames)
        base_vals = [lookup(base_doc, steps, f"the {ref_word} rig {rel(base_path)}") for _, steps, _ in sets]
    except TuningError as e:
        raise SystemExit(f"ERROR: {e}")
    paths = [p for p, _, _ in sets]
    base_desc = " · ".join(f"{p} = {value_text(v) if present else 'absent (loader default)'}"
                           for p, (present, v) in zip(paths, base_vals))

    out = Path(a.out)
    (out / "rigs").mkdir(parents=True, exist_ok=True)
    (out / "zoom").mkdir(parents=True, exist_ok=True)
    for stale in [*(out / "rigs").glob("*.json"), *(out / "zoom").glob("*.png")]:
        stale.unlink()

    # columns: one per combination (+ the base when no combination equals it)
    columns = []
    for i, combo in enumerate(combinations(sets), 1):
        text = base_text
        for (_, steps, _), v in zip(sets, combo):
            text = edit_text(text, steps, v)
        name = f"c{i}_" + "_".join(f"{slug(str(steps[-1]))}-{slug(value_text(v))}" for (_, steps, _), v in zip(sets, combo))
        rig_file = out / "rigs" / f"{name}.json"
        rig_file.write_text(text, encoding="utf-8")
        is_ref = all(present and same_value(v, bv) for v, (present, bv) in zip(combo, base_vals))
        columns.append({"column": i, "values": dict(zip(paths, combo)), "ref": is_ref, "rig_file": rig_file,
                        "label": " · ".join(f"{p} = {value_text(v)}" for p, v in zip(paths, combo)),
                        "short": "/".join(value_text(v) for v in combo)})
    ref_col = next((c for c in columns if c["ref"]), None)
    if ref_col is None:
        ref_col = {"column": 0, "values": {p: (v if present else None) for p, (present, v) in zip(paths, base_vals)},
                   "ref": True, "rig_file": base_path, "label": f"{ref_word}: {base_desc}", "short": ref_word}
        columns.insert(0, ref_col)

    loud = [f"{ref_word} rig: {m}" for m in base_loud]
    for c in columns:
        if c["column"] == 0:
            c["scene"], c["loader_warnings"] = base_scene, base_loud
            continue
        c["scene"], c["loader_warnings"] = load_scene(
            fp_namespace(a, c["rig_file"]), f"candidate c{c['column']} ({c['label']}, {rel(c['rig_file'])})")
        loud += [f"c{c['column']} ({c['label']}): {m}" for m in c["loader_warnings"]]

    # render every column x frame, compare with the reference column
    for c in columns:
        c["images"], c["warnings"] = zip(*(render(c["scene"], f) for f in frames))
    for fi, f in enumerate(frames):
        ref = np.asarray(ref_col["images"][fi], np.int16)
        union, union_any = np.zeros(ref.shape[:2], bool), np.zeros(ref.shape[:2], bool)
        for c in columns:
            img = np.asarray(c["images"][fi], np.int16)
            mask = change_mask(img, ref, a.threshold)
            c.setdefault("frames", []).append({"name": f["name"], "changed_px": int(mask.sum()), "bbox": bbox_of(mask),
                                               "identical": bool((img == ref).all()),
                                               "warnings": list(c["warnings"][fi])})
            union |= mask
            union_any |= (img != ref).any(-1)
        box = bbox_of(union)
        f["below_threshold"] = box is None and bool(union_any.any())
        f["zoom_box"] = pad_box(box or bbox_of(union_any), ZOOM_MARGIN, fp_rig.GAME_W, fp_rig.GAME_H, 96, 96) \
            if union_any.any() else None
    for c in columns:
        c["identical"] = not c["ref"] and all(r["identical"] for r in c["frames"])
    identical = [c for c in columns if c["identical"]]
    for c in identical:
        loud.append(f"IDENTICAL: c{c['column']} ({c['label']}) draws exactly what the {ref_word} rig draws in every "
                    f"frame: the renderer ignores the path, the value equals the loader default, or these frames "
                    f"do not show it")
    others = [c for c in columns if not c["ref"]]
    nothing = bool(others) and len(identical) == len(others)
    if nothing:
        loud.insert(0, f"NOTHING DIFFERS: no candidate changes a single pixel of any frame against the {ref_word} "
                       f"rig ({', '.join(paths)})")
    for f in frames:
        if f["zoom_box"] is None and others and not nothing:
            print(f"note: {f['name']}: no candidate changes this frame", file=sys.stderr)

    # grid (the HUD outline is drawn on the grid cells only: it is not content and would cross the zoomed arm)
    cw = a.cell
    rows, zraw, zcaps = [], [], []
    for fi, f in enumerate(frames):
        cells, zcells = [], []
        crop = f["zoom_box"]
        factor = zoom_factor(crop[2] - crop[0], crop[3] - crop[1], ZOOM_MAX, ZOOM_MAX) if crop else 1
        for c in columns:
            r = c["frames"][fi]
            head = c["label"] + (f"  [{ref_word.upper()}]" if c["ref"] else "")
            stat = (f"{ref_word} (reference)" if c["ref"] else
                    f"{r['changed_px']} px changed" + (" (IDENTICAL)" if r["identical"] else ""))
            warn = " · ".join(r["warnings"]) or "-"
            cell = sheets.labelled(with_hud(c["images"][fi]), [head, stat, warn], cw)
            cells.append(outline(cell) if c["ref"] else cell)
            if crop:
                zcells.append((zoom(c["images"][fi], crop, factor), [head, stat], c["ref"]))
        rows.append(cells)
        if crop:
            zraw.append(zcells)
            zcaps.append(f"crop ({crop[0]},{crop[1]})-({crop[2] - 1},{crop[3] - 1}) of 960x540, x{factor}"
                         + (" · differences are all below the threshold" if f["below_threshold"] else ""))
        else:
            zraw.append([(sheets.placeholder(ZOOM_MIN_W, 120, "no candidate changes this frame"), ["", ""])])
            zcaps.append("nothing differs")
    zrows = labelled_rows(zraw, REF_RGB)
    sk = base_scene
    title = [f"sweep · {a.weapon} · {', '.join(paths)} · rows = frames, columns = candidates",
             f"{ref_word} rig {rel(base_path)} (sha {base_scene.rig_sha[:10]}): {base_desc} (gold outline) · "
             f"changed px = summed |RGB difference| > {a.threshold:g} against it, in 960x540 frames · white box = HUD · "
             f"skin {sk.skin_label}, {sk.arms} arm, main arm {sk.a.main_arm}, arm lag "
             f"{'off' if sk.a.no_lag else 'on'}" + (", off hand occupied" if sk.a.off_hand_occupied else "")]
    title += [f"!!! {m}" for m in loud[:6]]
    sheets.grid(rows, title, row_labels=[f["label"] for f in frames]).save(out / "grid.png")
    ztitle = [f"where the candidates differ · {a.weapon} · {', '.join(paths)}",
              f"each frame cropped to the union of changed pixels (> {a.threshold:g}) plus {ZOOM_MARGIN} px, "
              f"enlarged by a whole factor (nearest neighbour)"]
    if nothing:
        ztitle.append(f"!!! {loud[0]}")
    strips = []
    for f, zcells, cap in zip(frames, zrows, zcaps):
        strip = sheets.grid([zcells], [f"{f['label']} · {cap}"])
        strip.save(out / "zoom" / f"{f['name']}.png")
        strips.append(strip)
    sheets.grid([[s] for s in strips], ztitle).save(out / "zoom.png")

    # summary
    summary = {
        "weapon": a.weapon, "base_rig": rel(base_path), "base_rig_sha256": base_scene.rig_sha, "base_is": ref_word,
        "sets": [{"path": p, "values": v, "base_present": present, "base_value": bv}
                 for (p, _, v), (present, bv) in zip(sets, base_vals)],
        "threshold": a.threshold, "frame_size": [fp_rig.GAME_W, fp_rig.GAME_H],
        "render": {"skin": sk.skin_label, "arms": sk.arms, "main_arm": sk.a.main_arm, "lag": not sk.a.no_lag,
                   "off_hand_occupied": bool(sk.a.off_hand_occupied), "sleeve": bool(sk.a.sleeve)},
        "frames": [{k: f[k] for k in ("name", "move", "move_id", "tick", "key")}
                   | {"zoom_box": None if f["zoom_box"] is None else [*f["zoom_box"][:2], f["zoom_box"][2] - 1,
                                                                      f["zoom_box"][3] - 1]}
                   for f in frames],
        "candidates": [{"column": c["column"], "label": c["label"], "values": c["values"], "reference": c["ref"],
                        "rig_file": rel(c["rig_file"]),
                        "rig_sha256": hashlib.sha256(Path(c["rig_file"]).read_bytes()).hexdigest(),
                        "loader_warnings": c["loader_warnings"],
                        "identical_to_reference": c["identical"],
                        "frames": [{**r, "bbox": None if r["bbox"] is None else list(r["bbox"])} for r in c["frames"]]}
                       for c in columns],
        "nothing_differs": nothing, "loud": loud,
        "outputs": ["grid.png", "zoom.png"] + [f"zoom/{f['name']}.png" for f in frames] + ["summary.json", "summary.txt"],
    }
    (out / "summary.json").write_text(json.dumps(summary, indent=1) + "\n", encoding="utf-8")
    text = summary_text(summary, columns, frames, ref_word)
    (out / "summary.txt").write_text(text, encoding="utf-8")
    print(text, end="")
    print(f"wrote {out}/grid.png, zoom.png, zoom/, rigs/ ({len(columns)} rig file(s)), summary.json, summary.txt")
    for m in loud:
        print(f"!!! {m}", file=sys.stderr)
    return 1 if nothing else 0


def summary_text(summary, columns, frames, ref_word):
    heads = [c["short"] + ("*" if c["ref"] else "") for c in columns]
    w0 = max(len(f["label"]) for f in frames) + 2
    ws = [max(len(h), 8) + 2 for h in heads]
    lines = [f"sweep {', '.join(s['path'] for s in summary['sets'])} · {summary['weapon']} · {ref_word} rig "
             f"{summary['base_rig']} (* = {ref_word} value)",
             f"changed pixels (summed |RGB difference| > {summary['threshold']:g}) against the {ref_word} column:",
             "frame".ljust(w0) + "".join(h.rjust(w) for h, w in zip(heads, ws))]
    for fi, f in enumerate(frames):
        lines.append(f["label"].ljust(w0) + "".join(
            ("-" if c["ref"] else str(c["frames"][fi]["changed_px"])).rjust(w) for c, w in zip(columns, ws)))
    lines.append("rig files: " + ", ".join(f"{h} {rel(c['rig_file'])}" for h, c in zip(heads, columns)))
    warn = [f"  {c['short']} {f['label']}: {' · '.join(c['frames'][fi]['warnings'])}"
            for c in columns for fi, f in enumerate(frames) if c["frames"][fi]["warnings"]]
    lines += ["solver warnings:"] + (warn or ["  none"])
    lines += [f"!!! {m}" for m in summary["loud"]]
    return "\n".join(lines) + "\n"
