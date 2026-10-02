"""Before/after evidence from two sets of stills (numpy + Pillow; light).

    python3 -m tools.combat_preview diff <before> <after> --out <dir> [--view fp] [--frames m1_idle,m3_contact]

Each argument is a tools/combat_capture directory (its frames/<view>/ PNGs, default view fp, in the
manifest's order) or a plain directory of PNGs. Frames pair by file name; files present on one side
only are listed (stdout, sheet header, summary), not dropped silently.

A pixel counts as changed when the summed absolute RGB difference exceeds --threshold (default 36,
as fp --key-threshold); the bounding box is inclusive, in the stills' own pixels.

Writes into --out: sheet.png (rows = frames: before | after | changed pixels in red over the dimmed
after, bounding box in yellow), zoom.png (the same three, cropped to the bounding box plus --margin
and enlarged by a whole factor), summary.json (per frame: changed_px, bbox [x0, y0, x1, y1] or null,
size), and prints one line per frame.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from . import sheets
from .tuning import KEY_THRESHOLD, TuningError, pair_names, png_names, select_names, stills_dir

CHANGED_RGB = (235, 50, 50)
ZOOM_MAX = 800  # zoomed crops: the largest whole factor that keeps them within ZOOM_MAX x ZOOM_MAX
ZOOM_MIN_W = 360  # and at least this wide on the sheet, so the labels fit
BOX_RGB = (250, 220, 60)


# ============================================================================ image helpers (also used by sweep)
def change_mask(a, b, threshold):
    """Pixels whose summed |RGB difference| exceeds threshold. a, b: PIL images or HxWx3 arrays."""
    A = np.asarray(a.convert("RGB") if hasattr(a, "convert") else a, np.int16)
    B = np.asarray(b.convert("RGB") if hasattr(b, "convert") else b, np.int16)
    return np.abs(A - B).sum(-1) > threshold


def bbox_of(mask):
    """Inclusive (x0, y0, x1, y1) of the True pixels, or None."""
    ys, xs = np.nonzero(mask)
    if not len(xs):
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def pad_box(box, margin, W, H, min_w=48, min_h=48):
    """Inclusive box grown by margin and to at least min_w x min_h, kept inside W x H; returned as a
    PIL crop box (exclusive right/bottom)."""
    x0, y0, x1, y1 = box
    b = [x0 - margin, y0 - margin, x1 + 1 + margin, y1 + 1 + margin]
    for lo, hi, size, minimum in ((0, 2, W, min_w), (1, 3, H, min_h)):
        need = min(size, minimum) - (b[hi] - b[lo])
        if need > 0:
            b[lo] -= need // 2
            b[hi] += need - need // 2
        if b[lo] < 0:
            b[hi] -= b[lo]
            b[lo] = 0
        if b[hi] > size:
            b[lo] = max(0, b[lo] - (b[hi] - size))
            b[hi] = size
    return tuple(int(v) for v in b)


def zoom_factor(crop_w, crop_h, target_w, target_h, max_factor=6):
    """Largest whole factor (1..max_factor) that keeps the crop within target_w x target_h."""
    return max(1, min(max_factor, target_w // max(1, crop_w), target_h // max(1, crop_h)))


def zoom(img, crop_box, factor):
    c = img.convert("RGB").crop(crop_box)
    return c.resize((c.width * factor, c.height * factor), Image.NEAREST) if factor > 1 else c


def labelled_rows(rows, accent=None):
    """rows of (image, label lines[, outlined]) -> rows of labelled cells, every image padded (not
    resampled) to the widest image of all rows so the columns line up."""
    w = max([ZOOM_MIN_W] + [cell[0].width for row in rows for cell in row])
    out = []
    for row in rows:
        cells = []
        for cell in row:
            im = sheets.labelled(pad_to(cell[0], w), cell[1], w)
            if len(cell) > 2 and cell[2]:
                im = im.copy()
                ImageDraw.Draw(im).rectangle((0, 0, im.width - 1, im.height - 1), outline=accent or sheets.ACCENT,
                                             width=4)
            cells.append(im)
        out.append(cells)
    return out


def pad_to(img, width, height=None):
    """img on a background canvas at least width (and height) wide, top-left aligned, not resampled."""
    w, h = max(width, img.width), max(height or 0, img.height)
    if (w, h) == img.size:
        return img
    out = Image.new("RGB", (w, h), sheets.BG)
    out.paste(img, (0, 0))
    return out


def changed_view(after, mask, box):
    """The after still dimmed, changed pixels in red, the inclusive bounding box outlined."""
    A = np.asarray(after.convert("RGB"), np.float32) * 0.35
    A[mask] = CHANGED_RGB
    im = Image.fromarray(A.astype(np.uint8))
    if box is not None:
        ImageDraw.Draw(im).rectangle(box, outline=BOX_RGB, width=1)
    return im


def fmt_box(box):
    return "none" if box is None else f"({box[0]},{box[1]})-({box[2]},{box[3]})"


# ============================================================================ diff command
def main(argv=None):
    ap = argparse.ArgumentParser(prog="python3 -m tools.combat_preview diff", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("before", help="capture directory (tools/combat_capture) or directory of PNGs")
    ap.add_argument("after", help="capture directory (tools/combat_capture) or directory of PNGs")
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--view", default="fp", help="capture view whose frames/<view>/ stills to use (default fp)")
    ap.add_argument("--frames", help="only these frames, comma list of file names without .png (e.g. m1_idle,m3_contact)")
    ap.add_argument("--threshold", type=float, default=KEY_THRESHOLD,
                    help=f"changed when the summed |RGB difference| exceeds this (default {KEY_THRESHOLD:g}, as fp --key-threshold)")
    ap.add_argument("--margin", type=int, default=16, help="pixels around the changed box in the zoomed crops")
    ap.add_argument("--cell", type=int, default=480, help="still width in sheet.png")
    a = ap.parse_args(argv)
    try:
        bdir, order = stills_dir(a.before, a.view)
        adir, _ = stills_dir(a.after, a.view)
        paired, only_b, only_a = pair_names(png_names(bdir), png_names(adir), order)
        names = select_names(paired, a.frames)
    except TuningError as e:
        raise SystemExit(f"ERROR: {e}")
    loud = []
    if only_b:
        loud.append(f"only in before ({len(only_b)}): {', '.join(only_b)}")
    if only_a:
        loud.append(f"only in after ({len(only_a)}): {', '.join(only_a)}")
    for msg in loud:
        print(f"!!! unpaired: {msg}", file=sys.stderr)
    if not names:
        raise SystemExit(f"ERROR: no file name is in both {bdir} and {adir}")
    bl, al = Path(a.before).name or str(a.before), Path(a.after).name or str(a.after)
    rows, zrows, frames = [], [], []
    for name in names:
        before = Image.open(bdir / f"{name}.png").convert("RGB")
        after = Image.open(adir / f"{name}.png").convert("RGB")
        rec = {"name": name, "size": [before.width, before.height]}
        if before.size != after.size:
            msg = f"{name}: sizes differ ({before.width}x{before.height} before, {after.width}x{after.height} after)"
            loud.append(msg)
            print(f"!!! {msg}", file=sys.stderr)
            rec.update(changed_px=None, bbox=None, error="size mismatch", after_size=[after.width, after.height])
            frames.append(rec)
            ph = sheets.placeholder(a.cell, round(a.cell * before.height / before.width), "size mismatch")
            rows.append([sheets.labelled(before, [f"before · {bl}", name], a.cell),
                         sheets.labelled(after, [f"after · {al}", name], a.cell),
                         sheets.labelled(ph, ["size mismatch", ""], a.cell)])
            continue
        mask = change_mask(before, after, a.threshold)
        box = bbox_of(mask)
        count = int(mask.sum())
        rec.update(changed_px=count, bbox=None if box is None else list(box))
        frames.append(rec)
        print(f"{name:20s} changed {count:7d} px  bbox {fmt_box(box)}")
        stat = f"changed {count} px · bbox {fmt_box(box)}"
        rows.append([sheets.labelled(before, [f"before · {bl}", name], a.cell),
                     sheets.labelled(after, [f"after · {al}", name], a.cell),
                     sheets.labelled(changed_view(after, mask, box), [stat, f"red = changed (> {a.threshold:g})"],
                                     a.cell)])
        if box is None:
            continue
        crop = pad_box(box, a.margin, before.width, before.height)
        f = zoom_factor(crop[2] - crop[0], crop[3] - crop[1], ZOOM_MAX, ZOOM_MAX)
        z = [zoom(im, crop, f) for im in (before, after, changed_view(after, mask, box))]
        cap = f"crop ({crop[0]},{crop[1]})-({crop[2] - 1},{crop[3] - 1}) x{f}"
        zrows.append([(z[0], [f"before · {name}", cap]), (z[1], [f"after · {name}", cap]), (z[2], [stat, cap])])
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    title = [f"before | after | changed · {bl} -> {al} · view {a.view} · {len(frames)} frame(s)",
             f"changed = summed |RGB difference| > {a.threshold:g}; bbox inclusive, in still pixels"]
    title += [f"!!! {m}" for m in loud[:6]]
    sheets.grid(rows, title, row_labels=[r["name"] for r in frames]).save(out / "sheet.png")
    if zrows:
        sheets.grid(labelled_rows(zrows), [f"changed regions, enlarged · {bl} -> {al}", "before | after | changed"]).save(out / "zoom.png")
    elif (out / "zoom.png").exists():
        (out / "zoom.png").unlink()
    summary = {"before": str(a.before), "after": str(a.after), "view": a.view, "threshold": a.threshold,
               "frames": frames, "only_in_before": only_b, "only_in_after": only_a, "loud": loud}
    (out / "summary.json").write_text(json.dumps(summary, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {out}/sheet.png" + (", zoom.png" if zrows else " (nothing changed: no zoom.png)")
          + f", summary.json ({len(frames)} pair(s))")
    if loud:
        print(f"!!! {len(loud)} loud warning(s) above (also in the sheet header and summary.json)", file=sys.stderr)
    return 0
