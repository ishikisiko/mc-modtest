#!/usr/bin/env python3
"""Build the original Simple Fox atlas from the mapped FoxModel cuboids."""

from __future__ import annotations

import hashlib
import json
from collections import Counter, deque
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw


ART_DIR = Path(__file__).resolve().parent
REPO_ROOT = ART_DIR.parents[2]
TRUTH_PATH = ART_DIR / "uv_truth.json"
MASK_PATH = ART_DIR / "uv_islands_mask.png"
PREVIEW_PATH = ART_DIR / "atlas_preview.png"
REPORT_PATH = ART_DIR / "texture_validation.json"
TEXTURE_PATH = (
    REPO_ROOT
    / "src/main/resources/assets/myvillage/textures/entity/simple_fox/simple_fox.png"
)

PALETTE = {
    "orange_light": (235, 151, 70, 255),
    "orange": (216, 117, 43, 255),
    "orange_warm": (198, 91, 35, 255),
    "orange_shadow": (169, 72, 31, 255),
    "cream_light": (247, 225, 188, 255),
    "cream": (237, 203, 151, 255),
    "cream_shadow": (202, 161, 111, 255),
    "dark_warm": (91, 57, 40, 255),
    "dark": (55, 37, 30, 255),
    "darkest": (29, 22, 20, 255),
}

ORANGE_FACES = {
    "down": "orange_shadow",
    "up": "orange_light",
    "west": "orange_warm",
    "north": "orange",
    "east": "orange_shadow",
    "south": "orange_warm",
}
CREAM_FACES = {
    "down": "cream_shadow",
    "up": "cream_light",
    "west": "cream_shadow",
    "north": "cream_light",
    "east": "cream_shadow",
    "south": "cream",
}
DARK_FACES = {
    "down": "darkest",
    "up": "dark_warm",
    "west": "dark",
    "north": "dark_warm",
    "east": "darkest",
    "south": "dark",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def image_data(image: Image.Image):
    getter = getattr(image, "get_flattened_data", None)
    return getter() if getter is not None else image.getdata()


def load_truth() -> dict[str, Any]:
    data = json.loads(TRUTH_PATH.read_text(encoding="utf-8"))
    if data.get("canvas") != {"width": 48, "height": 32}:
        raise ValueError("uv_truth.json must declare the FoxModel 48x32 canvas")
    return data


def face_rectangles(
    tex_offset: list[int], box: list[int]
) -> dict[str, tuple[int, int, int, int]]:
    u, v = tex_offset
    dx, dy, dz = box
    u0 = u
    u1 = u + dz
    u2 = u1 + dx
    u3 = u2 + dx
    u4 = u2 + dz
    u5 = u4 + dx
    v0 = v
    v1 = v + dz
    v2 = v1 + dy
    return {
        "down": (u1, v0, u2, v1),
        "up": (u2, v0, u3, v1),
        "west": (u0, v1, u1, v2),
        "north": (u1, v1, u2, v2),
        "east": (u2, v1, u4, v2),
        "south": (u4, v1, u5, v2),
    }


def fill_half_open(
    draw: ImageDraw.ImageDraw,
    rect: tuple[int, int, int, int],
    fill: int | tuple[int, int, int, int],
) -> None:
    x0, y0, x1, y1 = rect
    draw.rectangle((x0, y0, x1 - 1, y1 - 1), fill=fill)


def connected_component_areas(mask: Image.Image) -> list[int]:
    pixels = mask.load()
    width, height = mask.size
    remaining = {
        (x, y)
        for y in range(height)
        for x in range(width)
        if pixels[x, y] == 255
    }
    areas: list[int] = []
    while remaining:
        start = remaining.pop()
        queue = deque([start])
        area = 1
        while queue:
            x, y = queue.popleft()
            for point in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if point in remaining:
                    remaining.remove(point)
                    queue.append(point)
                    area += 1
        areas.append(area)
    return sorted(areas, reverse=True)


def build_mask(
    truth: dict[str, Any],
) -> tuple[Image.Image, dict[str, dict[str, tuple[int, int, int, int]]]]:
    size = (truth["canvas"]["width"], truth["canvas"]["height"])
    mask = Image.new("L", size, 0)
    owner: dict[tuple[int, int], str] = {}
    all_faces: dict[str, dict[str, tuple[int, int, int, int]]] = {}
    draw = ImageDraw.Draw(mask)

    for part in truth["parts"]:
        part_id = part["id"]
        faces = face_rectangles(part["tex_offset"], part["box"])
        all_faces[part_id] = faces
        for rect in faces.values():
            x0, y0, x1, y1 = rect
            if x0 < 0 or y0 < 0 or x1 > size[0] or y1 > size[1]:
                raise ValueError(f"{part_id} UV face {rect} leaves the atlas")
            for y in range(y0, y1):
                for x in range(x0, x1):
                    previous = owner.setdefault((x, y), part_id)
                    if previous != part_id:
                        raise ValueError(
                            f"unexpected UV overlap at {(x, y)}: {previous} and {part_id}"
                        )
            fill_half_open(draw, rect, 255)

    opaque = sum(1 for value in image_data(mask) if value == 255)
    components = connected_component_areas(mask)
    if opaque != truth["expected"]["opaque_texels"]:
        raise ValueError(f"mask has {opaque} opaque texels, expected 998")
    if components != truth["expected"]["component_areas_descending"]:
        raise ValueError(f"mask component areas differ: {components}")
    return mask, all_faces


def fill_part_faces(
    draw: ImageDraw.ImageDraw,
    faces: dict[str, tuple[int, int, int, int]],
    tones: dict[str, str],
) -> None:
    for face, rect in faces.items():
        fill_half_open(draw, rect, PALETTE[tones[face]])


def paint_texture(
    mask: Image.Image,
    faces: dict[str, dict[str, tuple[int, int, int, int]]],
) -> Image.Image:
    texture = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(texture)

    fill_part_faces(draw, faces["head"], ORANGE_FACES)
    fill_part_faces(draw, faces["right_ear"], DARK_FACES)
    fill_part_faces(draw, faces["left_ear"], DARK_FACES)
    fill_part_faces(draw, faces["muzzle"], CREAM_FACES)
    fill_part_faces(draw, faces["body"], ORANGE_FACES)
    fill_part_faces(draw, faces["left_legs_shared"], ORANGE_FACES)
    fill_part_faces(draw, faces["right_legs_shared"], ORANGE_FACES)
    fill_part_faces(draw, faces["tail"], ORANGE_FACES)

    # Face: a narrow cream blaze, paired one-pixel eyes, and cream cheeks.
    head_front = faces["head"]["north"]
    hx0, hy0, _, _ = head_front
    draw.rectangle((hx0 + 3, hy0 + 1, hx0 + 4, hy0 + 3), fill=PALETTE["cream_light"])
    draw.point((hx0 + 2, hy0 + 2), fill=PALETTE["darkest"])
    draw.point((hx0 + 5, hy0 + 2), fill=PALETTE["darkest"])
    draw.rectangle((hx0 + 1, hy0 + 3, hx0 + 6, hy0 + 5), fill=PALETTE["cream"])
    draw.point((hx0 + 2, hy0 + 3), fill=PALETTE["dark"])
    draw.point((hx0 + 5, hy0 + 3), fill=PALETTE["dark"])

    # Carry the cheek color around both sides and under the jaw.
    for side in ("west", "east"):
        x0, y0, x1, y1 = faces["head"][side]
        draw.rectangle((x0, y1 - 2, x1 - 1, y1 - 1), fill=PALETTE["cream_shadow"])
    fill_half_open(draw, faces["head"]["down"], PALETTE["cream"])

    # Each two-pixel ear face gets a warm inner-ear pixel pair.
    for ear in ("right_ear", "left_ear"):
        x0, y0, x1, y1 = faces[ear]["north"]
        draw.point((x0, y0), fill=PALETTE["orange_shadow"])
        draw.point((x1 - 1, y1 - 1), fill=PALETTE["cream_shadow"])

    # The muzzle remains cream; two front pixels form a restrained nose.
    mx0, my0, mx1, my1 = faces["muzzle"]["north"]
    draw.rectangle((mx0 + 1, my1 - 1, mx1 - 2, my1 - 1), fill=PALETTE["darkest"])

    # With the vanilla body rotated 90 degrees, local north reads as the belly.
    fill_half_open(draw, faces["body"]["north"], PALETTE["cream_shadow"])
    bx0, by0, bx1, _ = faces["body"]["north"]
    draw.rectangle((bx0 + 1, by0, bx1 - 2, by0 + 2), fill=PALETTE["cream"])

    # Shared left/right leg islands receive dark paws at their positive-Y ends.
    for legs in ("left_legs_shared", "right_legs_shared"):
        fill_half_open(draw, faces[legs]["down"], PALETTE["darkest"])
        for side in ("west", "north", "east", "south"):
            x0, _, x1, y1 = faces[legs][side]
            draw.rectangle((x0, y1 - 2, x1 - 1, y1 - 1), fill=PALETTE["dark"])

    # The tail's positive-Y cap and final three side rows form an original cream tip.
    fill_half_open(draw, faces["tail"]["down"], PALETTE["cream_light"])
    for side in ("west", "north", "east", "south"):
        x0, _, x1, y1 = faces["tail"][side]
        draw.rectangle((x0, y1 - 3, x1 - 1, y1 - 1), fill=PALETTE["cream"])
        for x in range(x0, x1, 2):
            draw.point((x, y1 - 4), fill=PALETTE["cream_shadow"])

    # Alpha is derived only from the exact cuboid mask; hidden RGB is zeroed.
    texture.putalpha(mask)
    pixels = texture.load()
    mask_pixels = mask.load()
    for y in range(texture.height):
        for x in range(texture.width):
            if mask_pixels[x, y] == 0:
                pixels[x, y] = (0, 0, 0, 0)
    return texture


def build_preview(texture: Image.Image, scale: int = 16) -> Image.Image:
    size = (texture.width * scale, texture.height * scale)
    preview = Image.new("RGBA", size, (0, 0, 0, 255))
    draw = ImageDraw.Draw(preview)
    checker = scale
    colors = ((54, 58, 64, 255), (78, 83, 91, 255))
    for y in range(0, size[1], checker):
        for x in range(0, size[0], checker):
            fill = colors[((x // checker) + (y // checker)) % 2]
            draw.rectangle((x, y, x + checker - 1, y + checker - 1), fill=fill)
    enlarged = texture.resize(size, Image.Resampling.NEAREST)
    preview.alpha_composite(enlarged)
    return preview.convert("RGB")


def validate_and_report(
    truth: dict[str, Any], mask: Image.Image, texture: Image.Image
) -> dict[str, Any]:
    if texture.size != (48, 32) or texture.mode != "RGBA":
        raise ValueError(f"unexpected texture format: {texture.mode} {texture.size}")
    alpha = texture.getchannel("A")
    alpha_values = sorted(set(image_data(alpha)))
    if alpha_values != [0, 255]:
        raise ValueError(f"alpha is not binary: {alpha_values}")
    if list(image_data(alpha)) != list(image_data(mask)):
        raise ValueError("texture alpha differs from the generated UV mask")

    opaque_pixels = []
    transparent_nonzero = 0
    for pixel in image_data(texture):
        if pixel[3] == 255:
            opaque_pixels.append(pixel[:3])
        elif pixel != (0, 0, 0, 0):
            transparent_nonzero += 1
    palette_counts = Counter(opaque_pixels)
    max_colors = int(truth["expected"]["max_opaque_colors"])
    if len(palette_counts) > max_colors:
        raise ValueError(f"palette has {len(palette_counts)} colors, maximum {max_colors}")
    if transparent_nonzero:
        raise ValueError(f"{transparent_nonzero} hidden texels contain RGB data")

    return {
        "schema_version": 1,
        "status": "pass",
        "texture": str(TEXTURE_PATH.relative_to(REPO_ROOT)),
        "texture_size": list(texture.size),
        "texture_mode": texture.mode,
        "alpha_values": alpha_values,
        "opaque_texels": len(opaque_pixels),
        "transparent_texels": texture.width * texture.height - len(opaque_pixels),
        "transparent_nonzero_rgb_texels": transparent_nonzero,
        "connected_component_areas_descending": connected_component_areas(mask),
        "opaque_color_count": len(palette_counts),
        "max_opaque_colors": max_colors,
        "opaque_palette": [
            {"rgba": [*rgb, 255], "count": count}
            for rgb, count in sorted(palette_counts.items())
        ],
        "resampling": "nearest",
        "uv_truth_sha256": sha256(TRUTH_PATH),
        "build_script_sha256": sha256(Path(__file__)),
    }


def main() -> int:
    truth = load_truth()
    mask, faces = build_mask(truth)
    texture = paint_texture(mask, faces)

    MASK_PATH.parent.mkdir(parents=True, exist_ok=True)
    TEXTURE_PATH.parent.mkdir(parents=True, exist_ok=True)
    mask.save(MASK_PATH, format="PNG", optimize=False)
    texture.save(TEXTURE_PATH, format="PNG", optimize=False)
    build_preview(texture).save(PREVIEW_PATH, format="PNG", optimize=False)

    report = validate_and_report(truth, mask, texture)
    report.update(
        {
            "uv_mask": str(MASK_PATH.relative_to(REPO_ROOT)),
            "atlas_preview": str(PREVIEW_PATH.relative_to(REPO_ROOT)),
            "uv_mask_sha256": sha256(MASK_PATH),
            "texture_sha256": sha256(TEXTURE_PATH),
            "atlas_preview_sha256": sha256(PREVIEW_PATH),
        }
    )
    REPORT_PATH.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
