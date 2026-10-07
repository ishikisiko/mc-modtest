"""python3 -m tools.portraitgen sheet --ledger FILE.json --out DIR [--count 48]
   python3 -m tools.portraitgen one --id N [--gender f] [--realm R] [--rank R] [--age Y] --out FILE.png"""
from __future__ import annotations

import argparse
import sys

from .person import Person, assign
from .render import render
from .sheet import build


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="portraitgen")
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("sheet")
    s.add_argument("--ledger", required=True)
    s.add_argument("--out", required=True)
    s.add_argument("--count", type=int, default=48)
    o = sub.add_parser("one")
    o.add_argument("--id", type=int, required=True)
    o.add_argument("--gender", default="m")
    o.add_argument("--realm", default="qi_refining")
    o.add_argument("--rank", default="outer")
    o.add_argument("--age", type=float, default=20.0)
    o.add_argument("--out", required=True)
    a = ap.parse_args(argv)
    if a.cmd == "sheet":
        r = build(a.ledger, a.out, a.count)
        print(f"{len(r['people'])} portraits, everyone={r['everyone']} -> {a.out}")
        return 0
    p = Person(a.id, gender=a.gender, realm=a.realm, rank=a.rank, age_years=a.age)
    ch = assign(p)
    render(p, ch).save(a.out)
    print(ch.summary())
    return 0


if __name__ == "__main__":
    sys.exit(main())
