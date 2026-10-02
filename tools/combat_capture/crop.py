"""Crop boxes for third-person sheets: one fixed box per view and capture set,
so every frame of a view is cut the same way and stays comparable. Frames on
disk are never changed; sheets crop on read.

Which views: third-person views without a barrier wall (the quarter views at
vanilla F5 distance), where the figure is small. The straight wall views
(tp_back, tp_front) already frame the figure and keep their full frames.

The rule (``view_crop``):
1. body box: the player's standing volume (eye at the screen centre; x and z
   within BODY_HALF_WIDTH of the body axis, from the feet up to above the
   head) projected through the view's recorded F5 camera (distance, pitch,
   look yaw, FOV 70, 960x540);
2. motion box: union over the view's frames of where each frame differs from
   the per-pixel median of all the view's frames (more than DIFF_THRESHOLD);
   the background is identical in every frame of a view, so this finds the
   weapon and the moving limbs in every pose;
3. box = union of 1 and 2, plus MARGIN pixels on every side, at least
   MIN_SIZE, clamped to the frame.
A view with fewer than MIN_FRAMES frames is not cropped (the median would be
the frame itself). `compare` uses the union of both sets' boxes for a view.
"""
from __future__ import annotations

import math
import re
import subprocess
import tempfile
from pathlib import Path

SCREEN = (960, 540)
FOV = 70.0
EYE_HEIGHT = 1.62
BODY_HALF_WIDTH = 0.5          # blocks: torso and legs (arms and weapon come from the motion box)
BODY_BOTTOM, BODY_TOP = -1.68, 0.3  # blocks relative to the eye (below the soles, above the head)
MARGIN = 16
MIN_SIZE = (160, 120)
MIN_FRAMES = 3
DIFF_THRESHOLD = "4%"          # of full scale on the grey difference (about 10 levels)
RULE = ("box = union of the player's body volume projected through the view's F5 camera and every frame's "
        "difference from the per-pixel median of the view's frames, plus a 16 px margin; the same box for "
        "every frame of the view")


def croppable(manifest: dict, view: str) -> bool:
    """Third-person views without a barrier wall (vanilla F5 distance)."""
    cam = camera_of(manifest, view)
    return cam is not None and cam.get("f5") in ("back", "front") and not cam.get("wall_distance")


def camera_of(manifest: dict, view: str):
    if not view.startswith("tp_"):
        return None
    return manifest.get("capture", {}).get("third_person_camera", {}).get(view[3:])


# ------------------------------------------------------------------ geometry
def _look(yaw: float, pitch: float):
    y, p = math.radians(yaw), math.radians(pitch)
    return (-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p))


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def project(point, camera: dict, size=SCREEN, fov: float = FOV):
    """Screen position of a point given relative to the eye (x east, y up,
    z south), seen by the F5 camera recorded in the manifest. Back: the camera
    sits `camera_distance` behind the eye along the look and looks along it.
    Front: it sits in front and looks back (yaw + 180, pitch negated, which
    is the reversed look). Returns None behind the camera."""
    f = _look(camera.get("look_yaw", 0.0), camera.get("pitch", 0.0))
    yaw = math.radians(camera.get("look_yaw", 0.0))
    right = (-math.cos(yaw), 0.0, -math.sin(yaw))
    dist = camera.get("camera_distance") or 4.0
    if camera.get("f5") == "front":
        f = (-f[0], -f[1], -f[2])
        right = (-right[0], 0.0, -right[2])
    cam = (-f[0] * dist, -f[1] * dist, -f[2] * dist)  # the camera sits dist behind itself along its forward
    up = _cross(right, f)
    v = (point[0] - cam[0], point[1] - cam[1], point[2] - cam[2])
    depth = _dot(v, f)
    if depth <= 1e-6:
        return None
    k = (size[1] / 2.0) / math.tan(math.radians(fov) / 2.0)
    return (size[0] / 2.0 + _dot(v, right) / depth * k, size[1] / 2.0 - _dot(v, up) / depth * k)


def body_box(camera: dict, size=SCREEN, fov: float = FOV):
    """(x0, y0, x1, y1) screen box of the standing player's volume."""
    pts = []
    for x in (-BODY_HALF_WIDTH, BODY_HALF_WIDTH):
        for y in (BODY_BOTTOM, BODY_TOP):
            for z in (-BODY_HALF_WIDTH, BODY_HALF_WIDTH):
                p = project((x, y, z), camera, size, fov)
                if p is not None:
                    pts.append(p)
    if not pts:
        return None
    return (math.floor(min(p[0] for p in pts)), math.floor(min(p[1] for p in pts)),
            math.ceil(max(p[0] for p in pts)), math.ceil(max(p[1] for p in pts)))


# ------------------------------------------------------------------ boxes
BBOX = re.compile(r"(\d+)x(\d+)\+(-?\d+)\+(-?\d+)")


def parse_bbox(text: str):
    """ImageMagick %@ ('WxH+X+Y') -> (x0, y0, x1, y1) inclusive-exclusive, or
    None for an empty image (0x0)."""
    m = BBOX.search(text or "")
    if not m:
        return None
    w, h, x, y = (int(g) for g in m.groups())
    if w <= 0 or h <= 0:
        return None
    return (x, y, x + w, y + h)


def union(boxes):
    boxes = [b for b in boxes if b]
    if not boxes:
        return None
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def finish(box, size=SCREEN, margin: int = MARGIN, min_size=MIN_SIZE):
    """Margin, minimum size (grown about the centre), clamped to the frame.
    Returns [x, y, w, h] (integers) or None."""
    if not box:
        return None
    x0, y0, x1, y1 = box[0] - margin, box[1] - margin, box[2] + margin, box[3] + margin
    for lo, hi, mn, lim in ((0, 2, min_size[0], size[0]), (1, 3, min_size[1], size[1])):
        a, b = (x0, x1) if lo == 0 else (y0, y1)
        if b - a < mn:
            c = (a + b) / 2.0
            a, b = math.floor(c - mn / 2.0), math.floor(c - mn / 2.0) + mn
        if a < 0:
            b, a = b - a, 0
        if b > lim:
            a, b = max(0, a - (b - lim)), lim
        if lo == 0:
            x0, x1 = a, b
        else:
            y0, y1 = a, b
    return [int(x0), int(y0), int(x1 - x0), int(y1 - y0)]


def box_union_xywh(a, b):
    """Union of two [x, y, w, h] boxes (None counts as absent)."""
    if not a or not b:
        return a or b
    return [min(a[0], b[0]), min(a[1], b[1]),
            max(a[0] + a[2], b[0] + b[2]) - min(a[0], b[0]), max(a[1] + a[3], b[1] + b[3]) - min(a[1], b[1])]


def zoom_for(box, target: int) -> int:
    """Integer enlargement (1-3) that keeps the cropped cell at most `target` wide."""
    return max(1, min(3, target // max(1, box[2])))


def crop_spec(path, box) -> str:
    """ImageMagick read-time crop of a file."""
    return f"{path}[{box[2]}x{box[3]}+{box[0]}+{box[1]}]"


# ------------------------------------------------------------------ images
def motion_box(files: list[Path]):
    """Union over the files of their difference from the per-pixel median."""
    if len(files) < MIN_FRAMES:
        return None
    with tempfile.TemporaryDirectory() as td:
        median = Path(td) / "median.png"
        subprocess.run(["convert", *[str(f) for f in files], "-evaluate-sequence", "median", str(median)],
                       check=True, timeout=300)
        boxes = []
        for f in files:
            out = subprocess.run(["convert", str(f), str(median), "-compose", "difference", "-composite",
                                  "-colorspace", "gray", "-threshold", DIFF_THRESHOLD, "-format", "%@", "info:"],
                                 check=True, timeout=120, capture_output=True, text=True).stdout
            boxes.append(parse_bbox(out))
    return union(boxes)


def view_crop(capture_dir: Path, manifest: dict, view: str):
    """The view's crop record {"box": [x, y, w, h], "rule", "frames"} or None
    when the view is not cropped."""
    if not croppable(manifest, view):
        return None
    files = [Path(capture_dir) / f["file"] for f in manifest.get("frames", [])
             if f["view"] == view and (Path(capture_dir) / f["file"]).is_file()]
    if len(files) < MIN_FRAMES:
        return None
    size = tuple(manifest.get("capture", {}).get("size") or SCREEN)
    fov = manifest.get("capture", {}).get("fov") or FOV
    box = finish(union([body_box(camera_of(manifest, view), size, fov), motion_box(files)]), size)
    if not box:
        return None
    return {"box": box, "rule": RULE, "frames": len(files)}
