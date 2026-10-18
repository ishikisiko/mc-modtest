#!/usr/bin/env python3
"""Opt-in, two-boot native V2 fixtures in a disposable Modal checkout only.

This tool does not operate a client or claim mapped input, natural boss tactics,
balance, or owner acceptance. Failed worlds and logs are deliberately retained.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "reports/exploration-v2-native"
WORLD = "exploration-v2-native-world"


def command():
    return ["./gradlew", "runAcceptanceServer", "-PvanillaRealmEvidence",
            "-x", "generateAllStructures", "-x", "generateWorldPlanAssets",
            "-x", "generateNpcLifePlaces", "-x", "generateExplorationGuide",
            "--no-daemon", "--max-workers=4", "-Dorg.gradle.jvmargs=-Xmx2g", "--console=plain"]


def verify_release_jar(root):
    jars = list((root / "build/libs").glob("myvillage-*.jar"))
    if len(jars) != 1:
        raise RuntimeError(f"Expected exactly one release jar, found {len(jars)}")
    with zipfile.ZipFile(jars[0]) as jar:
        evidence = [name for name in jar.namelist()
                    if name.endswith("Evidence.class") or "ExplorationV2Evidence$" in name]
        if evidence:
            raise RuntimeError(f"Test-only evidence leaked into release jar: {evidence}")
    return {jars[0].name: hashlib.sha256(jars[0].read_bytes()).hexdigest()}


def validate_phase(result, phase):
    if result.get("phase") != phase or result.get("passed") is not True:
        raise RuntimeError(f"{phase} failed: {result.get('error', 'invalid result envelope')}")
    selected, completed = result["selected"], result["completed"]
    if result.get("unrun") or set(selected) != set(completed):
        raise RuntimeError(f"{phase} incomplete: selected={selected}, completed={completed}")
    if not result.get("checks") or any(row.get("passed") is not True for row in result["checks"]):
        raise RuntimeError(f"{phase} contains missing or failed checks")


def main():
    if ROOT != Path("/workspace/repo"):
        raise RuntimeError("Native V2 server evidence requires the disposable Modal checkout")
    run = ROOT / "run-acceptance"
    if (run / WORLD).exists() or OUT.exists():
        raise RuntimeError("Refusing to overwrite a prior native V2 world or evidence directory")
    hashes = verify_release_jar(ROOT)
    OUT.mkdir(parents=True)
    run.mkdir(exist_ok=True)
    (run / "eula.txt").write_text("eula=true\n")
    defaults = run / "defaultconfigs"
    defaults.mkdir(exist_ok=True)
    (defaults / "myvillage-exploration-server.toml").write_text("[portal_gates]\nenabled = true\n")
    (run / "server.properties").write_text("\n".join([
        f"level-name={WORLD}", "level-seed=20261002", "level-type=minecraft:normal",
        "generate-structures=true", "online-mode=false", "server-ip=127.0.0.1",
        "server-port=25579", "view-distance=2", "simulation-distance=2",
        "difficulty=normal", "max-tick-time=180000", "spawn-protection=0",
        "sync-chunk-writes=true", "enable-command-block=false", ""]))
    summary = {"kind": "vanilla_exploration_v2_native", "selected": ["initial", "restart"],
               "completed": [], "unrun": ["initial", "restart"], "jar_sha256": hashes,
               "method": "native server fixtures; no mapped client input", "status": "running"}
    if Path("/workspace/request.json").exists():
        summary["source_commit"] = json.loads(Path("/workspace/request.json").read_text())["commit"]
    try:
        for phase in summary["selected"]:
            with (OUT / f"{phase}-stdout.log").open("w") as log:
                subprocess.run(command(), cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                               check=True, timeout=1800,
                               env={**os.environ, "MYVILLAGE_EXPLORATION_V2_EVIDENCE": "1",
                                    "MYVILLAGE_EXPLORATION_EVIDENCE": "0"})
            result = json.loads((OUT / f"{phase}.json").read_text())
            validate_phase(result, phase)
            summary["completed"].append(phase)
            summary["unrun"].remove(phase)
            print(f"{phase}: {len(result['checks'])} checks passed", flush=True)
        summary["status"] = "pass"
    except BaseException as error:
        summary.update(status="failed", error=f"{type(error).__name__}: {error}")
        raise
    finally:
        (OUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
