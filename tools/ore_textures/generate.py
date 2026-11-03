"""Export the four mineral block textures from 1.21.1 bases and authored pixel clusters."""

import argparse
import json
from pathlib import Path

from tools.gen_alchemy_assets import png


ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).with_name("textures.json")
MINERALS = (
    "spirit_stone_ore",
    "deepslate_spirit_stone_ore",
    "xuantie_ore",
    "edge_tempering_sand_deposit",
)


def texture(name):
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    mineral = source["minerals"][name]
    base = source["bases"][mineral["base"]]
    rows = [[(*base["palette"][pixel], 255) for pixel in row] for row in base["pixels"]]
    for cluster in source["patterns"][mineral["pattern"]]:
        for dy, row in enumerate(cluster["pixels"]):
            for dx, pixel in enumerate(row):
                if pixel != ".":
                    rows[cluster["y"] + dy][cluster["x"] + dx] = (*mineral["palette"][pixel], 255)
    return rows


def outputs(root=ROOT, names=MINERALS):
    directory = root / "src/main/resources/assets/myvillage/textures/block"
    return {directory / f"{name}.png": png(texture(name)) for name in names}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", nargs="+", choices=MINERALS, default=MINERALS)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for path, data in outputs(names=args.only).items():
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if stale:
        print("\n".join(stale))
        return 1
    print("矿物贴图与输入一致。" if args.check else "已导出矿物贴图。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
