"""Standard-library helpers for the `sweep` and `diff` commands: the --set grammar, dotted JSON paths
into a rig file (and a text edit that changes only the addressed value), frame specs, and pairing
stills by file name. No numpy here, so tools/tests/test_combat_preview_tuning.py runs under any
Python."""
from __future__ import annotations

import copy
import json
import re
from pathlib import Path

KEY_THRESHOLD = 36.0  # same default as `fp --key-threshold`: summed |RGB difference| above it = changed
CAPTURE_KEYS = ("idle", "strike_start", "contact", "strike_end", "recovery")
MAX_COLUMNS = 16


class TuningError(ValueError):
    pass


# ============================================================================ JSON paths
_SEGMENT = re.compile(r"([^.\[\]=]+)((?:\[-?\d+\])*)")


def parse_path(text: str) -> list:
    """'rig.shoulder[1]' -> ['rig', 'shoulder', 1]. Keys are separated by '.', list indices follow a key
    in brackets. A key may hold any character except . [ ] = (so move ids like ns:id work)."""
    if not text or text != text.strip():
        raise TuningError(f"bad JSON path {text!r}: empty or surrounded by spaces")
    steps = []
    for part in text.split("."):
        m = _SEGMENT.fullmatch(part)
        if not m:
            raise TuningError(f"bad JSON path {text!r}: segment {part!r} is not key or key[n]")
        steps.append(m.group(1))
        for idx in re.findall(r"\[(-?\d+)\]", m.group(2)):
            if int(idx) < 0:
                raise TuningError(f"bad JSON path {text!r}: negative index [{idx}]")
            steps.append(int(idx))
    return steps


def format_path(steps) -> str:
    out = ""
    for s in steps:
        out += f"[{s}]" if isinstance(s, int) else (f".{s}" if out else s)
    return out


def lookup(doc, steps, where="the base rig"):
    """(present, value) at steps. The parent must exist; a missing last key of an object is
    (False, None). Anything else that does not resolve raises naming the path."""
    node = doc
    for i, s in enumerate(steps):
        last = i == len(steps) - 1
        here = format_path(steps[:i + 1])
        if isinstance(s, int):
            if not isinstance(node, list):
                raise TuningError(f"{here}: {format_path(steps[:i]) or 'the root'} is not a list in {where}")
            if s >= len(node):
                raise TuningError(f"{here}: index {s} is out of range (the list has {len(node)} items) in {where}")
            node = node[s]
        else:
            if not isinstance(node, dict):
                raise TuningError(f"{here}: {format_path(steps[:i]) or 'the root'} is not an object in {where}")
            if s not in node:
                if last:
                    return False, None
                raise TuningError(f"{format_path(steps)}: {here} does not exist in {where} "
                                  "(only the last key of an existing object may be introduced)")
            node = node[s]
    return True, node


def apply_path(doc, steps, value):
    """A deep copy of doc with value at steps (a missing last key of an existing object is added)."""
    lookup(doc, steps)
    out = copy.deepcopy(doc)
    node = out
    for s in steps[:-1]:
        node = node[s]
    node[steps[-1]] = copy.deepcopy(value)
    return out


# ============================================================================ text edit that keeps the file's layout
class _Node:
    __slots__ = ("kind", "start", "end", "items")

    def __init__(self, kind, start, end, items=None):
        self.kind, self.start, self.end, self.items = kind, start, end, items


def _spans(text: str) -> _Node:
    """Parse JSON text into nodes carrying their character spans (objects: [(key, node)], arrays: [node])."""
    ws = " \t\r\n"
    dec = json.JSONDecoder()
    pos = 0

    def skip(p):
        while p < len(text) and text[p] in ws:
            p += 1
        return p

    def value(p):
        p = skip(p)
        if p >= len(text):
            raise TuningError("unexpected end of JSON")
        c = text[p]
        if c == "{":
            items, start, p = [], p, skip(p + 1)
            if text[p] == "}":
                return _Node("object", start, p + 1, items), p + 1
            while True:
                p = skip(p)
                key, p = dec.raw_decode(text, p)
                p = skip(p)
                if text[p] != ":":
                    raise TuningError(f"expected ':' at {p}")
                node, p = value(p + 1)
                items.append((key, node))
                p = skip(p)
                if text[p] == ",":
                    p += 1
                    continue
                if text[p] == "}":
                    return _Node("object", start, p + 1, items), p + 1
                raise TuningError(f"expected ',' or '}}' at {p}")
        if c == "[":
            items, start, p = [], p, skip(p + 1)
            if text[p] == "]":
                return _Node("array", start, p + 1, items), p + 1
            while True:
                node, p = value(p)
                items.append(node)
                p = skip(p)
                if text[p] == ",":
                    p += 1
                    continue
                if text[p] == "]":
                    return _Node("array", start, p + 1, items), p + 1
                raise TuningError(f"expected ',' or ']' at {p}")
        _, end = dec.raw_decode(text, p)
        return _Node("scalar", p, end), end

    root, pos = value(pos)
    if skip(pos) != len(text):
        raise TuningError("trailing data after JSON")
    return root


def value_text(value) -> str:
    return json.dumps(value, ensure_ascii=False)


def edit_text(text: str, steps, value) -> str:
    """text with only the value at steps replaced (or the missing last key appended to its object),
    so a candidate rig differs from its base by one token and can be copied over it. Checked: the
    result parses to apply_path(json.loads(text), steps, value)."""
    doc = json.loads(text)
    want = apply_path(doc, steps, value)
    node = _spans(text)
    for i, s in enumerate(steps):
        if isinstance(s, int):
            node = node.items[s]
            continue
        found = [n for k, n in node.items if k == s]
        if found:
            node = found[-1]  # json.loads keeps the last duplicate
            continue
        # missing last key: append it after the object's last member (or inside an empty object)
        entry = f"{value_text(s)}: {value_text(value)}"
        if node.items:
            at = node.items[-1][1].end
            out = text[:at] + ", " + entry + text[at:]
        else:
            at = node.end - 1
            out = text[:at] + entry + text[at:]
        break
    else:
        out = text[:node.start] + value_text(value) + text[node.end:]
    if json.loads(out) != want:
        raise RuntimeError(f"internal error: editing {format_path(steps)} did not give the expected document")
    return out


# ============================================================================ --set
def split_values(text: str) -> list[str]:
    """Split on commas outside brackets, braces and double quotes: '0.4,[1,2],"a,b"' -> 3 tokens."""
    out, depth, quote, esc, cur = [], 0, False, False, ""
    for c in text:
        if quote:
            cur += c
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                quote = False
            continue
        if c == '"':
            quote = True
        elif c in "[{":
            depth += 1
        elif c in "]}":
            depth -= 1
            if depth < 0:
                raise TuningError(f"unbalanced brackets in {text!r}")
        elif c == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
            continue
        cur += c
    if quote or depth:
        raise TuningError(f"unbalanced quotes or brackets in {text!r}")
    out.append(cur.strip())
    return out


def parse_value(token: str):
    """A JSON value (0.5, [0.3,-0.2,0], true, "x"), else the bare token as a string (out_back)."""
    try:
        return json.loads(token)
    except ValueError:
        return token


def parse_set(spec: str):
    """'rig.off_hand.thickness=0.42,0.5' -> (path text, steps, [0.42, 0.5])."""
    if "=" not in spec:
        raise TuningError(f"--set {spec!r}: expected <json.path>=<v1>,<v2>,...")
    path, _, rhs = spec.partition("=")
    steps = parse_path(path)
    tokens = split_values(rhs)
    if any(t == "" for t in tokens):
        raise TuningError(f"--set {spec!r}: empty value (expected <json.path>=<v1>,<v2>,...)")
    values = [parse_value(t) for t in tokens]
    for i, v in enumerate(values):
        if any(same_value(v, w) for w in values[:i]):
            raise TuningError(f"--set {spec!r}: value {tokens[i]} is listed twice")
    return path, steps, values


def check_sets(sets) -> None:
    """Several --set: no path twice, none inside another, at most MAX_COLUMNS combinations."""
    for i, (p, s, _) in enumerate(sets):
        for q, t, _ in sets[:i]:
            n = min(len(s), len(t))
            if s[:n] == t[:n]:
                raise TuningError(f"--set {p} and --set {q} address the same value (or one contains the other)")
    total = 1
    for _, _, v in sets:
        total *= len(v)
    if total > MAX_COLUMNS:
        raise TuningError(f"{' x '.join(str(len(v)) for _, _, v in sets)} = {total} candidate columns; "
                          f"at most {MAX_COLUMNS} fit a readable sheet (split the sweep)")


def combinations(sets) -> list[tuple]:
    """Every combination of the --set values, the first --set varying slowest."""
    combos = [()]
    for _, _, values in sets:
        combos = [c + (v,) for c in combos for v in values]
    return combos


def same_value(a, b) -> bool:
    """JSON equality: 0.5 == 0.50 and 1 == 1.0, but true is not 1."""
    if isinstance(a, bool) or isinstance(b, bool):
        return type(a) is type(b) and a == b
    return a == b


def slug(text: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", text).strip("_") or "x"


# ============================================================================ frames
def parse_frames(text: str) -> list[tuple]:
    """'1:0,1:contact,3:7.5' -> [(1, 0.0, None), (1, None, 'contact'), (3, 7.5, None)]: 1-based move
    numbers; a tick number or one of the capture tool's key names."""
    out = []
    for item in (s.strip() for s in text.split(",")):
        if not item:
            continue
        m, sep, t = item.partition(":")
        if not sep or not m.strip().isdigit() or int(m) < 1:
            raise TuningError(f"--frames {item!r}: expected <move>:<tick or key>, move 1-based (e.g. 2:6 or 2:contact)")
        t = t.strip()
        if t in CAPTURE_KEYS:
            out.append((int(m), None, t))
            continue
        try:
            tick = float(t)
        except ValueError:
            raise TuningError(f"--frames {item!r}: tick must be a number or one of {', '.join(CAPTURE_KEYS)}") from None
        if tick < 0:
            raise TuningError(f"--frames {item!r}: negative tick")
        out.append((int(m), tick, None))
    if not out:
        raise TuningError("--frames: no frames given")
    return out


def frame_name(move: int, tick: float, key: str | None) -> str:
    return f"m{move}_{key}" if key else f"m{move}_t{tick:g}"


# ============================================================================ stills and pairing
def natural_key(name: str):
    return [int(p) if p.isdigit() else p for p in re.split(r"(\d+)", name)]


def stills_dir(path, view: str = "fp"):
    """(directory of PNGs, preferred order of names or None) for a tools/combat_capture directory
    (frames/<view>/, ordered as its manifest) or a plain directory of PNGs."""
    root = Path(path)
    if not root.is_dir():
        raise TuningError(f"{path}: not a directory")
    frames = root / "frames"
    if frames.is_dir():
        d = frames / view
        if not d.is_dir():
            views = sorted(p.name for p in frames.iterdir() if p.is_dir())
            raise TuningError(f"{path}: capture directory has no view {view!r} (views: {', '.join(views) or 'none'})")
        order = None
        man = root / "manifest.json"
        if man.is_file():
            try:
                entries = json.loads(man.read_text(encoding="utf-8")).get("frames", [])
                order = [Path(f["file"]).stem for f in entries if f.get("view") == view and "file" in f]
            except (ValueError, AttributeError, TypeError):
                order = None
        return d, order
    if not any(root.glob("*.png")):
        raise TuningError(f"{path}: neither a capture directory (frames/<view>/) nor a directory of PNGs")
    return root, None


def png_names(directory) -> list[str]:
    return sorted((p.stem for p in Path(directory).glob("*.png")), key=natural_key)


def pair_names(before, after, order=None):
    """(paired names, only in before, only in after). Paired names follow `order` (e.g. the capture
    manifest's) where it lists them, then natural order."""
    b, a = set(before), set(after)
    rank = {n: i for i, n in enumerate(order or [])}
    key = lambda n: (rank.get(n, len(rank)), natural_key(n))
    return sorted(b & a, key=key), sorted(b - a, key=natural_key), sorted(a - b, key=natural_key)


def select_names(paired, wanted_text):
    """The paired names named in --frames (comma list, with or without .png), in the order given."""
    if not wanted_text:
        return list(paired)
    wanted = [w.strip()[:-4] if w.strip().endswith(".png") else w.strip() for w in wanted_text.split(",") if w.strip()]
    missing = [w for w in wanted if w not in paired]
    if missing:
        raise TuningError(f"--frames: {', '.join(missing)} not in both directories (paired: {', '.join(paired) or 'none'})")
    return wanted
