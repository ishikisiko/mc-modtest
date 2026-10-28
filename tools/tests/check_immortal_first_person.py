#!/usr/bin/env python3
"""定向 javac + 纯逻辑/几何核验；不调用 Gradle、游戏 bootstrap 或云端。"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path("src/main/java/com/example/myvillage")
TEST = Path("src/test/java/com/example/myvillage/client/immortal/ImmortalFirstPersonArmTest.java")
PROTECTED = [
    JAVA / "client/combat/FirstPersonArmIk.java",
    JAVA / "client/combat/FirstPersonArmModel.java",
    JAVA / "client/combat/FirstPersonWeaponTransform.java",
    Path("tools/combat_preview/fp_rig.py"),
    Path("tools/tests/test_combat_preview_parity.py"),
    Path("src/test/resources/first_person_preview_parity.json"),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-context", type=Path, default=Path("/tmp/immortal-client-api-check"))
    parser.add_argument("--output", type=Path, required=True, help="新的输出目录；保留命令、日志及输入哈希")
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    classes = out / "classes"
    classes.mkdir()
    api = args.api_context.resolve()
    pal = ROOT / "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"
    if not pal.is_file():
        raise SystemExit(f"缺少契约指定 PAL jar：{pal}")
    cp = os.pathsep.join([(api / "classpath.txt").read_text().strip(), str(pal), str(api)])
    sources = [JAVA / "client/combat/FirstPersonArmRenderer.java", JAVA / "client/combat/ImmortalArmPoseBridge.java"]
    sources += sorted(p.relative_to(ROOT) for p in (ROOT / JAVA / "client/immortal").glob("*.java"))
    # Explicit pure definitions avoid stale partial javac outputs masking transitive missing classes.
    definitions = sorted(p.relative_to(ROOT) for p in (ROOT / JAVA / "combat/definition").glob("*.java"))
    commands: list[dict] = []

    def run(name: str, command: list[str]) -> None:
        started = time.monotonic()
        with (out / f"{name}.log").open("wb") as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=False)
        commands.append({"name": name, "argv": command, "exit": result.returncode,
                         "seconds": round(time.monotonic() - started, 3)})
        (out / "commands.json").write_text(json.dumps(commands, ensure_ascii=False, indent=2) + "\n")
        print(f"{name}: exit={result.returncode} log={out / (name + '.log')}", flush=True)
        if result.returncode:
            raise SystemExit(result.returncode)

    inputs = set(sources + definitions + [TEST, Path(__file__).relative_to(ROOT)] + PROTECTED)
    inputs.update(p.relative_to(ROOT) for p in (ROOT / "src/main/resources/assets/myvillage/immortal_equipment").glob("*.json"))
    inputs.update(p.relative_to(ROOT) for p in (ROOT / "src/main/resources/assets/myvillage/combat").glob("*.json"))
    # Anchor paths to the repository even if invoked from another cwd.
    inputs.update(p.relative_to(ROOT) for p in (ROOT / "src/main/resources/data/myvillage/combat").rglob("*.json"))
    hashes = {str(p): hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in sorted(inputs)}
    hashes[str(pal.name)] = hashlib.sha256(pal.read_bytes()).hexdigest()
    (out / "inputs.sha256.json").write_text(json.dumps(hashes, indent=2) + "\n")
    (out / "head.txt").write_text(subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True))
    run("protected-source", ["git", "diff", "--exit-code", "HEAD", "--", *map(str, PROTECTED)])
    run("javac-client", ["javac", "-proc:none", "-cp", cp, "-sourcepath", "src/main/java", "-d", str(classes),
                         *map(str, sources + definitions)])
    run("javac-test", ["javac", "-proc:none", "-cp", str(classes) + os.pathsep + cp,
                       "-sourcepath", "src/test/java", "-d", str(classes), str(TEST)])
    run("geometry", ["java", "-Xmx512m", "-cp", os.pathsep.join([str(classes), cp, str(ROOT / "src/main/resources")]),
                     "com.example.myvillage.client.immortal.ImmortalFirstPersonArmTest"])
    drift = [str(p) for p in inputs if hashlib.sha256((ROOT / p).read_bytes()).hexdigest() != hashes[str(p)]]
    if drift:
        (out / "input-drift.json").write_text(json.dumps(drift, ensure_ascii=False, indent=2))
        raise SystemExit("共享源码在检查期间发生变化，不能将本次检查当作同一候选通过")
    print("PASS 定向编译及纯逻辑/几何检查；native/视觉/透明排序/FPS/玩家接受均未验证")


if __name__ == "__main__":
    main()
