"""Labelled contact sheets for the combat previews (Pillow only). Light work: pure Python image
composition, no ImageMagick."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
BG = (24, 24, 28)
FG = (235, 235, 235)
SUB = (170, 190, 210)
ACCENT = (250, 200, 90)


def font(size):
    for f in FONT_CANDIDATES:
        if Path(f).is_file():
            try:
                return ImageFont.truetype(f, size)
            except OSError:
                pass
    return ImageFont.load_default()


def fit(img, width):
    if img.width == width:
        return img
    h = max(1, round(img.height * width / img.width))
    return img.resize((width, h), Image.LANCZOS)


def crop_frac(img, frac):
    """frac = (x0, y0, x1, y1) as fractions of the frame, or None."""
    if not frac:
        return img
    x0, y0, x1, y1 = frac
    return img.crop((round(x0 * img.width), round(y0 * img.height), round(x1 * img.width), round(y1 * img.height)))


def labelled(img, lines, width, accent_first=True):
    """img scaled to `width` with a text strip above it. lines: list of str."""
    body = fit(img.convert("RGB"), width)
    f1, f2 = font(17), font(14)
    line_h = [22 if i == 0 else 18 for i in range(len(lines))]
    strip = sum(line_h) + 8
    out = Image.new("RGB", (width, strip + body.height), BG)
    d = ImageDraw.Draw(out)
    y = 4
    for i, text in enumerate(lines):
        d.text((6, y), text, fill=ACCENT if (i == 0 and accent_first) else (FG if i == 1 else SUB),
               font=f1 if i == 0 else f2)
        y += line_h[i]
    out.paste(body, (0, strip))
    return out


def placeholder(width, height, text):
    im = Image.new("RGB", (width, height), (60, 20, 20))
    d = ImageDraw.Draw(im)
    d.text((10, height // 2 - 10), text, fill=FG, font=font(16))
    return im


def grid(rows, title_lines, gap=6, row_labels=None):
    """rows: list of lists of PIL images (already labelled). Returns one sheet."""
    ncols = max(len(r) for r in rows)
    col_w = [max((r[c].width for r in rows if c < len(r)), default=0) for c in range(ncols)]
    row_h = [max(im.height for im in r) for r in rows]
    label_w = 0
    if row_labels:
        f = font(18)
        label_w = max(int(ImageDraw.Draw(Image.new("RGB", (1, 1))).textlength(t, font=f)) for t in row_labels) + 16
    tf = [font(22)] + [font(15)] * (len(title_lines) - 1)
    title_h = sum(30 if i == 0 else 20 for i in range(len(title_lines))) + 10
    W = label_w + sum(col_w) + gap * (ncols + 1)
    H = title_h + sum(row_h) + gap * (len(rows) + 1)
    sheet = Image.new("RGB", (W, H), (12, 12, 14))
    d = ImageDraw.Draw(sheet)
    y = 6
    for i, t in enumerate(title_lines):
        d.text((gap, y), t, fill=FG if i == 0 else SUB, font=tf[i])
        y += 30 if i == 0 else 20
    y = title_h + gap
    for ri, r in enumerate(rows):
        if row_labels:
            d.text((gap, y + 8), row_labels[ri], fill=ACCENT, font=font(18))
        x = label_w + gap
        for ci, im in enumerate(r):
            sheet.paste(im, (x, y))
            x += col_w[ci] + gap
        y += row_h[ri] + gap
    return sheet


def side_by_side(a, b, gap=4, color=(250, 200, 90)):
    """Two labelled cells next to each other with a thin coloured divider."""
    h = max(a.height, b.height)
    out = Image.new("RGB", (a.width + b.width + gap, h), color)
    out.paste(a, (0, 0))
    out.paste(b, (a.width + gap, 0))
    return out
