#!/usr/bin/env python3
"""Export approved technique scroll paintings within a hard runtime texture budget."""
import argparse
import io
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/technique_scroll"
TARGET = ROOT / "src/main/resources/assets/myvillage/textures/gui/cultivation/technique_scroll"


def outputs():
    style = json.loads((SOURCE / "style.json").read_text())
    width, height = style["size"]
    if width > 768 or height > 512 or width * 2 != height * 3:
        raise ValueError("Scroll textures must remain bounded and keep the approved 3:2 aspect")
    if not 16 <= style["palette_colors"] <= 256:
        raise ValueError("Invalid indexed palette size")
    result = {}
    for ident, name in style["techniques"].items():
        if not ident.startswith("myvillage:") or not name.isidentifier():
            raise ValueError("Invalid exact technique mapping")
        with Image.open(SOURCE / "source" / f"{name}.png") as original:
            if original.size != (1536, 1024):
                raise ValueError(f"Unexpected approved original dimensions: {name}")
            image = original.convert("RGB").resize((width, height), Image.Resampling.LANCZOS)
            image = image.quantize(colors=style["palette_colors"], dither=Image.Dither.NONE)
            buf = io.BytesIO()
            image.save(buf, format="PNG", optimize=True, compress_level=9)
        data = buf.getvalue()
        if len(data) > style["max_texture_bytes"]:
            raise ValueError(f"Runtime texture exceeds budget: {name}: {len(data)}")
        result[TARGET / f"{name}.png"] = data
        result[TARGET / f"{name}.png.mcmeta"] = b'{"texture":{"blur":true,"clamp":true}}\n'
    if sum(map(len, result.values())) > style["max_total_bytes"]:
        raise ValueError("Combined technique scroll textures exceed the jar budget")
    return result, style


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated, style = outputs()
    stale = []
    for path, data in generated.items():
        matches = path.exists() and path.read_bytes() == data
        if args.check and path.suffix == ".png" and path.exists():
            # zlib versions may differ; dimensions/pixels and the actual packed byte budget must not.
            with Image.open(path) as actual, Image.open(io.BytesIO(data)) as expected:
                matches = (actual.mode == "P" and actual.size == expected.size
                           and actual.convert("RGB").tobytes() == expected.convert("RGB").tobytes()
                           and path.stat().st_size <= style["max_texture_bytes"])
        if not matches:
            stale.append(str(path.relative_to(ROOT)))
            if not args.check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    actual_bytes = sum(p.stat().st_size for p in generated if p.exists())
    if actual_bytes > style["max_total_bytes"]:
        stale.append("combined runtime byte budget exceeded")
    print(json.dumps({"check": args.check, "runtime_bytes": actual_bytes,
                      "budget_bytes": style["max_total_bytes"], "stale": stale if args.check else []}))
    return int(args.check and bool(stale))


if __name__ == "__main__":
    raise SystemExit(main())
