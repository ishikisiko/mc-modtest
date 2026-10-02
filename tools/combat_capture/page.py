"""Static review pages. Pure functions: manifest(s) in, HTML out.

The pages are developer evidence and are served publicly from the capture
host, so they show only capture facts: no verdict wording, no absolute
paths, ports, host names or credentials.
"""
from __future__ import annotations

from html import escape

from .capture import VIEWS
from .data import KEY_NAMES, fmt_tick

CSS = """
:root { --bg:#f6f6f4; --fg:#1d1d1f; --muted:#5d5d63; --line:#d9d9dc; --card:#ffffff; --accent:#8a5a00; }
@media (prefers-color-scheme: dark) { :root { --bg:#141416; --fg:#e8e8ea; --muted:#a0a0a8; --line:#2c2c31;
  --card:#1c1c20; --accent:#e0b050; } }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:15px/1.5 system-ui, sans-serif; }
main { max-width:1280px; margin:0 auto; padding:24px 16px 64px; }
h1 { font-size:24px; margin:0 0 4px; } h2 { font-size:19px; margin:32px 0 8px; border-bottom:1px solid var(--line); }
h3 { font-size:16px; margin:20px 0 6px; }
p.note { color:var(--muted); max-width:72ch; }
table { border-collapse:collapse; margin:8px 0; font-size:14px; }
th, td { border:1px solid var(--line); padding:4px 8px; text-align:left; vertical-align:top; }
th { background:var(--card); }
td.num { text-align:right; font-variant-numeric:tabular-nums; }
figure { margin:12px 0; background:var(--card); border:1px solid var(--line); padding:8px; }
figure img, video { max-width:100%; height:auto; display:block; }
figcaption { color:var(--muted); font-size:13px; margin-top:6px; }
code { font-size:13px; }
.wrap { overflow-x:auto; }
"""


def _doc(title: str, body: str) -> str:
    return ("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
            f"<title>{escape(title)}</title>\n<style>{CSS}</style>\n</head>\n<body>\n<main>\n{body}\n</main>\n"
            "</body>\n</html>\n")


def _rows(pairs) -> str:
    return "".join(f"<tr><th>{escape(str(k))}</th><td>{v}</td></tr>" for k, v in pairs)


def _source_text(m: dict) -> str:
    src = m.get("source", {})
    head = (src.get("head") or "")[:12]
    desc = src.get("describe") or ""
    dirty = " (working tree had uncommitted changes)" if src.get("dirty") else ""
    return f"<code>{escape(head)}</code> {escape(desc)}{escape(dirty)}"


NOTE = ("Developer evidence. The stills come from the client freeze probes "
        "(<code>/myvillage_pal_smoke first_person</code> and <code>third_person</code>), which pose the local "
        "client only and send nothing to the server; each still was kept once two consecutive screen grabs were "
        "identical, or three were within two colour levels of each other everywhere (whole-frame rendering "
        "noise). The combo run uses real mapped left clicks, and target health is read from the server over "
        "rcon before and after. This page records what was captured; it does not record any review outcome.")


def camera_text(m: dict) -> str:
    """One line per third-person view in this capture: F5 side, distance, pitch, yaws."""
    cams = m.get("capture", {}).get("third_person_camera", {})
    lines = []
    for view in m.get("capture", {}).get("views", []):
        c = cams.get(view[3:] if view.startswith("tp_") else view)
        if not c:
            continue
        dist = (f"barrier wall {c['wall_distance']:g} blocks from the eye" if c.get("wall_distance")
                else f"vanilla F5 distance {c.get('camera_distance', 4.0):g} blocks")
        lines.append(f"{escape(view)}: F5 {escape(str(c.get('f5', '')))}, {dist}, pitch {c.get('pitch', 0):g}, "
                     f"look yaw {c.get('look_yaw', 0):g}, body yaw {c.get('body_yaw', 0):g}")
    return "<br>".join(lines)


def rig_loaded_text(r: dict | None) -> str:
    """What the rig the client loaded declares (read from the file it reads)."""
    if not r:
        return "<br>loaded rig: not recorded (capture made before the tool recorded it)"
    if not r.get("present"):
        return "<br>loaded rig: file missing from the client's resources"
    same = r.get("same_as_source")
    return (f"<br>loaded rig ({escape(str(r.get('read_from')))}): "
            f"{'declares an off hand (rig.off_hand)' if r.get('off_hand') else 'no off hand declared'}; "
            f"sha256 <code>{escape(str(r.get('sha256', ''))[:12])}</code>"
            + ("" if same is None else ("; same as the source tree" if same else
                                        "; <strong>differs from the source tree</strong> (hot-reloaded data)")))


def crop_text(c: dict | None, size) -> str:
    if not c:
        return ""
    x, y, w, h = c["box"]
    return (f" Each cell shows the box {w}x{h} at ({x}, {y}) of the {size[0]}x{size[1]} frame, enlarged by an "
            f"integer factor. Rule: {escape(c['rule'])}. "
            f"Full frames are unchanged on disk.")


def meta_table(m: dict) -> str:
    frames = m.get("frames", [])
    unstable = sum(1 for f in frames if not f.get("stable", True))
    return "<table>" + _rows([
        ("Weapon", f"<code>{escape(m['weapon'])}</code> (item <code>{escape(m.get('item', ''))}</code>)"),
        ("Style", f"<code>{escape(m.get('style', ''))}</code>"),
        ("First-person rig", f"<code>{escape(m.get('rig', ''))}</code>" + rig_loaded_text(m.get("rig_loaded"))),
        ("Mod version", escape(str(m.get("source", {}).get("mod_version")))),
        ("Commit", _source_text(m)),
        ("Captured", escape(m.get("created", ""))),
        ("Capture", f"{m['capture']['size'][0]}x{m['capture']['size'][1]}, FOV {m['capture'].get('fov')}, "
                    f"views {escape(', '.join(m['capture']['views']))}"
                    + (f"; weapon drawn {m['capture']['weapon_held_length_blocks']:g} blocks long in a "
                       f"third-person hand" if m['capture'].get('weapon_held_length_blocks') else "")),
        ("Third-person cameras", camera_text(m) or "none"),
        ("Frames", f"{len(frames)} ({unstable} not stable)" + ("" if m.get("stills_complete") else
                                                               ", stills run did not complete")),
    ]) + "</table>"


def key_tick_table(m: dict) -> str:
    head = "<tr><th>#</th><th>Move</th><th>Kind</th><th>Total ticks</th>" + "".join(
        f"<th>{escape(k)}</th>" for k in KEY_NAMES) + "</tr>"
    body = ""
    for mv in m["moves"]:
        ticks = {k["key"]: k["tick"] for k in mv["keys"]}
        body += (f"<tr><td class=num>{mv['index']}</td><td><code>{escape(mv['id'])}</code></td>"
                 f"<td>{escape(mv.get('kind', ''))}</td><td class=num>{mv['total_ticks']}</td>"
                 + "".join(f"<td class=num>{fmt_tick(ticks[k]) if k in ticks else ''}</td>" for k in KEY_NAMES)
                 + "</tr>")
    return f"<div class=wrap><table>{head}{body}</table></div>"


def combo_section(c: dict | None, prefix: str = "") -> str:
    if not c:
        return "<p class=note>No combo run in this capture.</p>"
    rows = "".join(
        f"<tr><td>{escape(t['tag'])}</td><td>{escape(t['kind'])}</td><td class=num>{t['health_before']}</td>"
        f"<td class=num>{t['health_after']}</td><td class=num>{t['damage']}</td></tr>" for t in c["targets"])
    clicks = ", ".join(f"move {k['move']} at {k['seconds']:g}s" for k in c["clicks_planned"])
    sent = ", ".join(f"{s:g}s" for s in c.get("clicks_sent_s", []))
    anims = c.get("client_animation_starts", [])
    anim_txt = ", ".join(escape(a["animation"].split(":")[-1]) + ("" if a["accepted"] else " (rejected)")
                         + (" (confirmed prediction)" if a.get("kept_prediction") else "") for a in anims) or "none logged"
    out = (f"<figure><video controls preload=metadata src=\"{escape(prefix + c['video'])}\"></video>"
           f"<figcaption>Combo run{_combo_camera_text(c.get('camera'))}, {c['video_seconds']:g}s at 30 fps. "
           f"The first click was sent {c['lead_seconds']:g}s "
           f"after ffmpeg was launched; ffmpeg's own start-up makes it appear a few tenths of a second earlier in "
           f"the video. No audio (the capture host has no sound device).</figcaption></figure>")
    if c.get("strip"):
        out += (f"<figure><a href=\"{escape(prefix + c['strip'])}\"><img loading=lazy src=\"{escape(prefix + c['strip'])}\" "
                f"alt=\"frames from the combo video\"></a><figcaption>Evenly spaced frames from the video."
                f"</figcaption></figure>")
    if c.get("tick_rate", 20) != 20 or c.get("layout", "default") != "default":
        out += (f"<p>Tick rate during the run: {c.get('tick_rate', 20)} (clicks scaled to it). Target layout: "
                f"{escape(str(c.get('layout', 'default')))} "
                + escape(", ".join(f"{t} at {dx:+g} x, {dz:+g} z" for t, dx, dz in c.get("layout_offsets", [])))
                + " (relative to the player; +x is the player's left).</p>")
    if c.get("hits_by_move") is not None:
        hb = c["hits_by_move"]
        out += ("<h3>Damage by move (health polled over rcon during the run)</h3>"
                + ("<table><tr><th>Move</th><th>Damage per target</th></tr>" + "".join(
                    f"<tr><td>{escape(mv)}</td><td>" + escape(", ".join(f"{t} -{d:g}" for t, d in sorted(per.items())))
                    + "</td></tr>" for mv, per in hb.items()) + "</table>" if hb else "<p>No health loss seen.</p>")
                + f"<p class=note>Each loss is put on the latest client move start before it. "
                  f"{len(c.get('fp_log', []))} first-person hit-stop/resync log lines during the run.</p>")
    out += ("<h3>Target health (server, over rcon)</h3><table><tr><th>Target</th><th>Kind</th><th>Before</th>"
            f"<th>After</th><th>Difference</th></tr>{rows}</table>"
            f"<p>Total difference: {c['total_damage']:g}. Combat mode during the run: "
            f"{escape(str(c.get('combat_mode')))}.</p>"
            f"<p>Planned clicks: {escape(clicks)}.<br>Clicks sent at: {escape(sent)}.<br>"
            f"Player position before / after: <code>{escape(str(c.get('player_pos_before')))}</code> / "
            f"<code>{escape(str(c.get('player_pos_after')))}</code>.<br>"
            f"Client PAL animation starts logged during the run: {anim_txt}.</p>")
    return out


def _combo_camera_text(cam: dict | None) -> str:
    if not cam or cam.get("f5") == "first":
        return ", first person"
    return (f", F5 {escape(str(cam.get('f5')))} at {cam.get('camera_distance')} blocks, pitch {cam.get('pitch')}, "
            f"look yaw {cam.get('look_yaw')}")


def motion_section(records) -> str:
    if not records:
        return ""
    out = ("<h2>Third-person motion</h2><p class=note>Each move's PAL animation played on the client at game "
           "speed with the smoke command (typed in chat, so the chat line shows briefly); no server action, so no "
           "hits, no lunge movement and no world trail. The body keeps its facing, so the quarter view holds.</p>")
    for r in records:
        ev = ", ".join(f"{escape(e['event'])} at {e['s']:g}s" for e in r.get("events", []))
        out += (f"<h3>{escape(VIEWS.get(r['view'], {}).get('title', r['view']))}</h3><figure><video controls "
                f"preload=metadata src=\"{escape(r['video'])}\"></video><figcaption>{r['seconds']:g}s. "
                f"Events (seconds after ffmpeg was launched): {ev}.</figcaption></figure>")
    return out


def capture_page(m: dict) -> str:
    body = f"<h1>Combat capture: {escape(m['label'])}</h1>\n<p class=note>{NOTE}</p>\n{meta_table(m)}\n"
    body += "<h2>Key ticks</h2><p class=note>Read from the first-person rig: idle 0, strike start = strike[0], " \
            "contact, strike end = strike[1], recovery = the last key before the final neutral key. Third-person " \
            "stills use the same ticks on the move's PAL animation.</p>" + key_tick_table(m)
    body += "<h2>Stills</h2>"
    for view in m["capture"]["views"]:
        sheet = m.get("sheets", {}).get(view)
        body += f"<h3>{escape(VIEWS.get(view, {}).get('title', view))}</h3>"
        if sheet:
            body += (f"<figure><a href=\"{escape(sheet)}\"><img loading=lazy src=\"{escape(sheet)}\" "
                     f"alt=\"{escape(view)} sheet\"></a><figcaption>Rows are moves, columns are key ticks. Single "
                     f"frames are under <code>frames/{escape(view)}/</code>."
                     f"{crop_text(m.get('crops', {}).get(view), m['capture']['size'])}</figcaption></figure>")
        else:
            body += "<p class=note>No frames for this view.</p>"
    body += "<h2>Combo run</h2>" + combo_section(m.get("combo"))
    body += motion_section(m.get("motion"))
    if m.get("notes"):
        body += "<h2>Notes</h2><ul>" + "".join(f"<li>{escape(n)}</li>" for n in m["notes"]) + "</ul>"
    body += "<p class=note><a href=\"manifest.json\">manifest.json</a></p>"
    return _doc(f"Combat capture {m['label']}", body)


def comparison_page(cmp: dict) -> str:
    a, b = cmp["a"], cmp["b"]
    body = (f"<h1>Combat capture comparison: {escape(cmp['label'])}</h1>\n<p class=note>{NOTE} Frames are paired by "
            "weapon, move, key and view; each cell shows set A on the left and set B on the right with each set's "
            "own tick. A cell marked [UNPAIRED] exists in one set only.</p>")
    body += "<table><tr><th></th><th>A</th><th>B</th></tr>" + "".join(
        f"<tr><th>{escape(k)}</th><td>{fa}</td><td>{fb}</td></tr>" for k, fa, fb in [
            ("Label", escape(a["label"]), escape(b["label"])),
            ("Weapon", f"<code>{escape(a['weapon'])}</code>", f"<code>{escape(b['weapon'])}</code>"),
            ("Mod version", escape(str(a.get("source", {}).get("mod_version"))),
             escape(str(b.get("source", {}).get("mod_version")))),
            ("Commit", _source_text(a), _source_text(b)),
            ("Captured", escape(a.get("created", "")), escape(b.get("created", ""))),
            ("Frames", str(len(a.get("frames", []))), str(len(b.get("frames", [])))),
        ]) + "</table>"
    body += (f"<p>Pairs: {cmp['counts']['pairs']}; only in A: {cmp['counts']['only_a']}; "
             f"only in B: {cmp['counts']['only_b']}.</p>")
    if cmp.get("camera_differences"):
        body += ("<p><strong>Camera settings differ between A and B</strong> for paired views, so those cells "
                 "do not show the same camera:</p><ul>"
                 + "".join(f"<li>{escape(d)}</li>" for d in cmp["camera_differences"]) + "</ul>")
    if cmp.get("links"):
        body += "<p>" + " · ".join(f"<a href=\"{escape(href)}\">{escape(text)}</a>"
                                   for text, href in cmp["links"]) + "</p>"
    body += "<h2>Paired stills</h2>"
    for view, sheet in cmp.get("sheets", {}).items():
        body += (f"<h3>{escape(VIEWS.get(view, {}).get('title', view))}</h3><figure><a href=\"{escape(sheet)}\">"
                 f"<img loading=lazy src=\"{escape(sheet)}\" alt=\"{escape(view)} comparison\"></a>"
                 f"<figcaption>Left: {escape(a['label'])}. Right: {escape(b['label'])}."
                 + (" Both sides cropped to the same box {2}x{3} at ({0}, {1}) (the union of the two sets' own "
                    "boxes).".format(*cmp["crops"][view]) if cmp.get("crops", {}).get(view) else "")
                 + "</figcaption></figure>")
    unpaired = cmp.get("unpaired", [])
    if unpaired:
        body += ("<h2>Present in one set only</h2><table><tr><th>View</th><th>Move</th><th>Key</th><th>In</th></tr>"
                 + "".join(f"<tr><td>{escape(u['view'])}</td><td><code>{escape(u['move_id'])}</code></td>"
                           f"<td>{escape(u['key'])}</td><td>{escape(u['in'])}</td></tr>" for u in unpaired)
                 + "</table>")
    body += "<h2>Combo runs</h2>"
    for side, m in (("A", a), ("B", b)):
        body += f"<h3>{side}: {escape(m['label'])}</h3>" + combo_section(m.get("combo"), prefix=f"{side.lower()}/")
    body += "<p class=note><a href=\"comparison.json\">comparison.json</a></p>"
    return _doc(f"Combat capture comparison {cmp['label']}", body)
