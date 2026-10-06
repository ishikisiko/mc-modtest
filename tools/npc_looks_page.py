"""Side-by-side page of the cultivator's three looks: offline renders and headless in-game captures.

    python3 tools/npc_looks_page.py            # out/preview/cultivator/looks/index.html
    python3 tools/npc_looks_page.py --out DIR  # DIR/index.html

Columns are the looks (``default`` / ``f_novice`` / ``f_adept``); rows are the offline
turnaround, face sheet, close-ups and walk GIFs (``out/preview/<definition>/``, from
``python3 -m tools.npcgen preview <definition>``) and the headless in-game stills and walk
videos (``out/preview/cultivator/ingame[_<look>]/``, from
``python3 -m tools.combat_capture npc --look <look>``). A file that is not there yet shows
as "未采集". Every link is relative to the page, so the folder can be copied as it is.
"""
from __future__ import annotations

import argparse
import html
import os
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
PREVIEW = REPO / "out/preview"
NPC = "cultivator"  # the entity's preview folder (myvillage:cultivator)
MISSING = "未采集"

# look, npcgen definition (offline preview folder), column title
LOOKS = (
    ("default", "cultivator", "男修 · default"),
    ("f_novice", "cultivator_f_novice", "女修入门 · f_novice"),
    ("f_adept", "cultivator_f_adept", "女修小成 · f_adept"),
)

# row title, source ("offline" or "ingame"), kind, [(file, caption)]
ROWS = (
    ("离线六视图", "offline", "img", [("turnaround.png", "六视图")]),
    ("面部页", "offline", "img", [("face.png", "脸部")]),
    ("离线特写", "offline", "img", [("closeups.png", "细节特写")]),
    ("走路 GIF", "offline", "img", [("walk_front.gif", "左前"), ("walk_side.gif", "侧面"),
                                   ("walk_back.gif", "右后")]),
    ("无头四面立绘", "ingame", "img", [("stills/idle_front.png", "正面"), ("stills/idle_side.png", "侧面"),
                                    ("stills/idle_q_front.png", "左前 3/4"), ("stills/idle_q_back.png", "右后 3/4")]),
    ("无头特写", "ingame", "img", [("stills/close_face_front.png", "脸，正面"),
                                 ("stills/close_head_q_front.png", "头，3/4 前"),
                                 ("stills/close_head_q_back.png", "头与后发，3/4 后"),
                                 ("stills/close_chest.png", "交领"), ("stills/close_waist.png", "腰带与坠饰"),
                                 ("stills/close_sleeve.png", "袖口"), ("stills/close_hem.png", "下摆与靴"),
                                 ("stills/close_back_panel.png", "后襟")]),
    ("步行视频", "ingame", "video", [("video/walk_front.mp4", "围栏内步行，正面机位"),
                                  ("video/walk_end.mp4", "同一围栏，端头机位")]),
)


def ingame_dir_name(look: str) -> str:
    """Same rule as ``tools.combat_capture.npc.ingame_dir_name`` (kept here so the page needs no capture deps)."""
    return "ingame" if look == "default" else f"ingame_{look}"


def source_dir(preview: Path, source: str, look: str, definition: str) -> Path:
    if source == "offline":
        return preview / definition
    return preview / NPC / ingame_dir_name(look)


def rows(preview: Path = PREVIEW, page_dir: Path | None = None, looks=LOOKS) -> list[dict]:
    """One dict per row: ``title``, ``kind`` and ``cells`` (one per look), each cell a list of
    ``{"src", "caption", "present"}`` with ``src`` relative to ``page_dir`` (posix separators)."""
    page_dir = page_dir if page_dir is not None else preview / NPC / "looks"
    out = []
    for title, source, kind, files in ROWS:
        cells = []
        for look, definition, _ in looks:
            base = source_dir(preview, source, look, definition)
            cells.append([{"src": Path(os.path.relpath(base / name, page_dir)).as_posix(), "caption": caption,
                           "present": (base / name).is_file()} for name, caption in files])
        out.append({"title": title, "kind": kind, "cells": cells})
    return out


def _item(kind: str, item: dict) -> str:
    cap = html.escape(item["caption"])
    if not item["present"]:
        return f'<figure class="missing"><div>{MISSING}</div><figcaption>{cap}</figcaption></figure>'
    src = html.escape(item["src"], quote=True)
    if kind == "video":
        media = f'<video src="{src}" controls muted loop preload="metadata"></video>'
    else:
        media = f'<a href="{src}"><img src="{src}" alt="{cap}" loading="lazy"></a>'
    return f"<figure>{media}<figcaption>{cap}</figcaption></figure>"


def _cell(kind: str, items: list[dict]) -> str:
    if not any(i["present"] for i in items):
        return f'<td class="none">{MISSING}</td>'
    many = " many" if len(items) > 1 else ""
    return f'<td><div class="items{many}">{"".join(_item(kind, i) for i in items)}</div></td>'


INTRO = """<p>同一实体 <code>myvillage:cultivator</code> 的三套外观（存档标签 <code>Look</code>）：
<b>default</b> 男修；<b>f_novice</b> 女修入门（低马尾 + 发带）；<b>f_adept</b> 女修小成（配色 A：绛紫外衫 + 月白裙 + 金饰，披帛淡金/杏色）。
上四行是离线渲染（<code>python3 -m tools.npcgen preview &lt;定义名&gt;</code>，游戏的两盏固定实体光，不是游戏内截图）；
下三行是服务器无头采集（<code>python3 -m tools.combat_capture npc --look &lt;look&gt;</code>，960x540 软件 GL）。
格子写“未采集”表示该文件还没生成。</p>
<p>留给 owner 定：配色 A 还是 B（现为 A）；低马尾还是双丫髻（现为低马尾）；化身是否按性别与境界自动换装
（现为：女性且 ≤ 炼气 → f_novice，≥ 筑基 → f_adept，男性 → default）；两张女修脸能否读出“稚气 / 英气”，只能真机看。</p>
<p class="status">三套外观全部 <code>not_verified</code>：本页是开发者证据，不是 owner 结论。</p>"""


def render(table: list[dict], looks=LOOKS) -> str:
    head = "".join(f"<th>{html.escape(title)}</th>" for _, _, title in looks)
    body = "\n".join(f'<tr><th class="row">{html.escape(r["title"])}</th>'
                     f'{"".join(_cell(r["kind"], c) for c in r["cells"])}</tr>' for r in table)
    return f"""<!doctype html>
<html lang="zh"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>修仙者三套外观</title>
<style>
:root {{ --bg: #1c1e24; --panel: #262931; --line: #353a45; --text: #dfe3ea; --muted: #9aa3b2; --warn: #e0b46c; }}
body {{ margin: 0; padding: 16px; background: var(--bg); color: var(--text); font: 15px/1.5 system-ui, sans-serif; }}
h1 {{ font-size: 20px; margin: 0 0 8px; }} p {{ margin: 0 0 10px; color: var(--muted); max-width: 70em; }}
p.status {{ color: var(--warn); }} code {{ color: var(--text); }}
.wrap {{ overflow-x: auto; margin-top: 14px; }}
table {{ border-collapse: collapse; width: 100%; min-width: 900px; table-layout: fixed; }}
th, td {{ border: 1px solid var(--line); padding: 8px; vertical-align: top; }}
thead th {{ background: var(--panel); font-size: 15px; }}
th.row {{ width: 7em; text-align: left; color: var(--muted); font-weight: 600; }}
td.none {{ color: var(--muted); text-align: center; vertical-align: middle; }}
.items.many {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }}
figure {{ margin: 0; }} figure.missing div {{ color: var(--muted); padding: 24px 0; text-align: center;
  background: var(--panel); border-radius: 4px; }}
img, video {{ display: block; width: 100%; height: auto; image-rendering: pixelated; border-radius: 4px; }}
figcaption {{ padding-top: 4px; color: var(--muted); font-size: 12px; }}
</style></head><body>
<h1>修仙者 · 三套外观对比</h1>
{INTRO}
<div class="wrap"><table>
<thead><tr><th class="row"></th>{head}</tr></thead>
<tbody>
{body}
</tbody></table></div>
</body></html>
"""


def write(out_dir: Path, preview: Path = PREVIEW) -> tuple[Path, list[dict]]:
    out_dir.mkdir(parents=True, exist_ok=True)
    table = rows(preview, out_dir)
    page = out_dir / "index.html"
    page.write_text(render(table), encoding="utf-8")
    return page, table


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--out", type=Path, default=PREVIEW / NPC / "looks",
                    help="output directory (default out/preview/cultivator/looks)")
    a = ap.parse_args(argv)
    page, table = write(a.out.resolve())
    filled = sum(any(i["present"] for i in c) for r in table for c in r["cells"])
    print(f"page: {page} ({filled}/{len(table) * len(LOOKS)} cells filled)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
