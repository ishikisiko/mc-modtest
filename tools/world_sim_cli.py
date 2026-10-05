#!/usr/bin/env python3
"""Run the world-sim pure core offline, without Gradle or Minecraft.

Compiles ``com.example.myvillage.sim`` (minus ``sim.runtime``) plus the pure region classes it
uses with plain ``javac`` into ``build/world_sim_cli/`` whenever a source is newer than the last
build, then runs ``com.example.myvillage.sim.cli.SimCli`` with the given arguments. Needs only a
JDK 21 on PATH and the Gson jar from the Gradle cache. No lock is needed (design §0).

Usage::

    tools/world_sim_cli.py run --seed 1 --tier small --years 300 \\
        --out out/preview/world_sim/seed1.json --text out/preview/world_sim/seed1.txt

Extra options of ``run``: ``--days-per-year N``, ``--resources DIR`` (searched before
``src/main/resources``), ``--lang zh_cn|en_us``. ``--rebuild`` (first argument) forces a compile.
"""

from __future__ import annotations

import glob
import os
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "build" / "world_sim_cli"
STAMP = OUT / ".stamp"
SIM = REPO / "src/main/java/com/example/myvillage/sim"
REGION = REPO / "src/main/java/com/example/myvillage/region/runtime"
# Pure region classes the sim core and its CLI use (RegionCatalogLoader is not pure).
REGION_CLASSES = [
    "RegionGraph", "GenRegion", "GenEdge", "IntRange", "RegionQueries", "RegionPlacement",
    "RegionContract", "RegionJson", "RegionTopologyGenerator", "Ruleset", "RegionProfile",
    "RegionRng", "RegionHash", "UnsatisfiableRulesetException",
]
MAIN = "com.example.myvillage.sim.cli.SimCli"


def gson_jar() -> str:
    pattern = os.path.expanduser(
        "~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1/*/gson-2.10.1.jar")
    jars = sorted(glob.glob(pattern))
    if not jars:
        sys.exit("gson-2.10.1.jar not found in the Gradle cache (" + pattern + ")")
    return jars[0]


def sources() -> list[Path]:
    files = [p for p in SIM.rglob("*.java") if "runtime" not in p.relative_to(SIM).parts]
    files += [REGION / f"{name}.java" for name in REGION_CLASSES]
    return sorted(files)


def compile_if_needed(force: bool) -> None:
    srcs = sources()
    if not force and STAMP.exists():
        built = STAMP.stat().st_mtime
        if all(p.stat().st_mtime <= built for p in srcs):
            return
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / "classes"
    if classes.exists():
        for f in sorted(classes.rglob("*.class")):
            f.unlink()
    cmd = ["javac", "-encoding", "UTF-8", "-nowarn", "-d", str(classes), "-cp", gson_jar()] + [str(p) for p in srcs]
    result = subprocess.run(cmd)
    if result.returncode != 0:
        sys.exit(result.returncode)
    STAMP.touch()


def main(argv: list[str]) -> int:
    force = bool(argv) and argv[0] == "--rebuild"
    if force:
        argv = argv[1:]
    compile_if_needed(force)
    cp = os.pathsep.join([str(OUT / "classes"), gson_jar()])
    cmd = ["java", "-cp", cp, MAIN] + argv + ["--root", str(REPO)]
    return subprocess.run(cmd).returncode


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
