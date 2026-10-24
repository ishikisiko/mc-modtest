#!/usr/bin/env python3
"""新开局离线计划、原始证据审计和映射输入适配；本文件没有游戏启动入口。

原生运行仍需固定源后适配生产接口。审计只验证证据链完整性，不替代画面审阅。
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
FIXTURE = Path(__file__).with_name("qianzhen_opening_fixture.json")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()


def fixture():
    value = json.loads(FIXTURE.read_text())
    ids = [stage["id"] for stage in value["stages"]]
    if len(ids) != len(set(ids)) or not ids:
        raise ValueError("重复或缺少场景")
    return value


def readonly_rcon(command):
    """只接受一条明确只读命令；不接受 execute 包装、换行或意图推进。"""
    if any(char in command for char in "\n\r\x00") or command != command.strip():
        return False
    # Predicate-only execute has no `run`, store, selector or mutation suffix.
    block = r"-?\d+ -?\d+ -?\d+ (?:#?[a-z0-9_.-]+:[a-z0-9_./-]+)(?:\[[a-z0-9_=,.-]+\])?"
    return bool(re.fullmatch(r"execute(?:(?: if| unless) block " + block + r")+", command)
                or re.fullmatch(r"execute as [A-Za-z0-9_]{1,16} at @s anchored eyes positioned \^ \^ \^ if entity @s\[distance=1[.]615[.][.]1[.]625\]", command)
                or re.fullmatch(r"execute if entity [A-Za-z0-9_]{1,16}\[gamemode=survival\]", command)
                or re.fullmatch(r"myvillage cultivation (?:info|fate status) [A-Za-z0-9_]{1,16}", command)
                or re.fullmatch(r"myvillage story status [A-Za-z0-9_]{1,16}", command)
                or re.fullmatch(r"data get entity [A-Za-z0-9_]{1,16}(?: [^\n\r\x00]+)?", command)
                or re.fullmatch(r"data get block -?\d+ -?\d+ -?\d+(?: [^\n\r\x00]+)?", command)
                or command in ("list", "time query gametime"))


def guard_rcon(command, *, purpose="readback", continuous=False):
    if purpose == "readback" and readonly_rcon(command):
        return
    # Never allow admin setting of story/root/realm/rewards as a test action.
    # Fixture writes have to go through a later explicit, isolated adapter.
    raise ValueError("本适配器只允许权威读回；独立夹具写入须另行记录: " + command)


def source_snapshot(root):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()
    names = git("ls-files", "--cached", "--others", "--exclude-standard", "--",
                "src/main/java/com/example/myvillage/story", "src/main/java/com/example/myvillage/client/story", "src/main/resources/data/myvillage/story",
                "tools/qianzhen", "blueprints/qianzhen", "build.gradle", "gradle.properties").splitlines()
    names.extend(str(path.relative_to(root)) for path in (root / "tools").glob("qianzhen_opening_*") if path.is_file())
    hashes = {name: digest(root / name) for name in sorted(set(names)) if (root / name).is_file()}
    return {"commit": git("rev-parse", "HEAD"), "tracked_status": git("status", "--short", "--untracked-files=no"),
            "untracked_runtime": git("ls-files", "--others", "--exclude-standard", "--", "src/main", "tools/qianzhen").splitlines(),
            "files": hashes, "fingerprint": fingerprint(hashes)}


def plan(root=ROOT):
    spec = fixture()
    selected = [stage["id"] for stage in spec["stages"]]
    return {"schema": "qianzhen-opening-plan-v1", "status": "ready_not_run", "evidence_tier": "offline",
            "source_snapshot": source_snapshot(root), "fixed_source": False,
            "fixture_sha256": digest(FIXTURE), "selected": selected, "completed": [], "unrun": selected.copy(),
            "stages": spec["stages"], "termination": "not_started", "native": "pending_fixed_source_and_adapter",
            "world": spec["world"], "seed": spec["seed"], "not_verified": spec["not_verified"],
            "blockers": ["父线程固定整合提交与同源candidate/request", "自然新世界入口和新剧情status字段适配", "原生截图/录像与独立保存世界"]}


def _artifact(root, row):
    path = PurePosixPath(row["path"])
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError("证据路径越界")
    actual = (root / path).resolve()
    if not actual.is_relative_to(root.resolve()) or not actual.is_file() or digest(actual) != row["sha256"]:
        raise ValueError("证据缺失或哈希漂移: " + str(path))
    if actual.stat().st_size == 0:
        raise ValueError("空证据: " + str(path))
    return actual


def audit(trace, artifact_root):
    """审计规范化采集记录，始终不自动授予原生/玩家验收通过。"""
    spec = fixture()
    known = {stage["id"]: stage for stage in spec["stages"]}
    selected = trace.get("selected", [])
    errors, completed = [], []
    if not selected or len(selected) != len(set(selected)) or set(selected) - known.keys():
        errors.append("selected为空、重复或未知")
    if trace.get("evidence_tier") != "native" or trace.get("status") == "ready_not_run":
        errors.append("离线计划不能作为原生证据")
    source = trace.get("source", {})
    if (not re.fullmatch(r"[0-9a-f]{40}", source.get("commit", "")) or source.get("tracked_status") != ""
            or source.get("fixed") is not True or not re.fullmatch(r"[0-9a-f]{64}", source.get("request_sha256", ""))):
        errors.append("缺少干净固定提交/请求身份")
    hashes = source.get("resource_sha256", {})
    if len(hashes) < 7 or any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in hashes.values()):
        errors.append("缺少精确资源/依赖哈希")
    if (not source.get("scene_version") or not re.fullmatch(r"[0-9a-f]{64}", source.get("candidate_identity_sha256", ""))
            or not source.get("dependency_sha256")
            or any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in source.get("dependency_sha256", {}).values())):
        errors.append("缺少候选或依赖身份")
    if trace.get("fixture_sha256") != digest(FIXTURE):
        errors.append("验收fixture已漂移")
    run_id = trace.get("run_id")
    if not isinstance(run_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{8,100}", run_id):
        errors.append("缺少唯一run_id")
    world = trace.get("world", {})
    base, path = PurePosixPath(world.get("isolation_root", ".")), PurePosixPath(world.get("directory", "."))
    if (world.get("fresh") is not True or not base.is_absolute() or base == PurePosixPath("/")
            or not path.is_relative_to(base) or path == base or ".." in path.parts
            or str(run_id) not in base.parts or world.get("preserved_world") is True):
        errors.append("世界目录未证明独立新建")
    rows = trace.get("stages", [])
    ids = [row.get("id") for row in rows]
    if len(ids) != len(set(ids)) or set(ids) - set(selected):
        errors.append("重复或未选中的阶段记录")
    preflight_ok = not errors
    for row in rows:
        stage = known.get(row.get("id"))
        before = len(errors)
        if not stage:
            continue
        if row.get('status') in {'failed', 'fail', 'unrun', 'running'}:
            errors.append(stage['id'] + ': 场景执行失败或未完成')
        if row.get("run_id") != run_id or row.get("source_commit") != source.get("commit") or row.get("request_sha256") != source.get("request_sha256"):
            errors.append(stage["id"] + ": 混用其他运行/源/请求")
        artifacts = row.get("artifacts", {})
        loaded = {}
        for required in ("actions", "readbacks", "video", "screenshot"):
            try:
                loaded[required] = _artifact(artifact_root, artifacts[required])
            except (KeyError, OSError, ValueError) as error:
                errors.append(stage["id"] + ": " + required + " " + str(error))
        actions = row.get("actions", [])
        readbacks = []
        try:
            raw_actions = [json.loads(line) for line in loaded["actions"].read_text().splitlines()]
            if raw_actions != actions:
                raise ValueError("动作摘要与原始日志不同")
            readbacks = [json.loads(line) for line in loaded["readbacks"].read_text().splitlines()]
        except (KeyError, OSError, ValueError) as error:
            errors.append(stage["id"] + ": 原始日志无法核对 " + str(error))
        seqs = [action.get("sequence") for action in actions]
        if (not actions or any(type(seq) is not int for seq in seqs) or seqs != sorted(set(seqs))):
            errors.append(stage["id"] + ": 动作序列无效")
        mapped = []
        for action in actions:
            if action.get("kind") == "mapped_input":
                if (not action.get("player") or not action.get("display") or not action.get("window")
                        or type(action.get("pid")) is not int or action["pid"] <= 0 or not action.get("control")):
                    errors.append(stage["id"] + ": 映射输入缺少玩家/窗口/PID/按键")
                else:
                    mapped.append(action)
            elif action.get("kind") == "rcon":
                if not readonly_rcon(action.get("command", "")):
                    errors.append(stage["id"] + ": RCON不能推进玩家动作")
            elif action.get("kind") == "fixture":
                allowed = {"legacy_accepted": "load_legacy_world", "existing_awakened": "load_awakened_world",
                           "marker_matrix": "marker_occluder", "restart_two_players": "lifecycle"}
                if stage["continuous"] or action.get("purpose") != allowed.get(stage["id"]):
                    errors.append(stage["id"] + ": 夹具污染自然连续流程")
                command = action.get("command")
                if command and not (action.get("purpose") == "marker_occluder"
                        and re.fullmatch(r"setblock -?\d+ -?\d+ -?\d+ minecraft:(?:stone|air)", command)):
                    errors.append(stage["id"] + ": 夹具命令越界")
            elif action.get("kind") in {"input_state", "observation"}:
                if action.get("command") is not None:
                    errors.append(stage["id"] + ": 诊断事件不能夹带命令")
            else:
                errors.append(stage["id"] + ": 未知动作来源")
        if stage["input_required"] and not mapped:
            errors.append(stage["id"] + ": 没有真实映射输入")
        checks = row.get("checks", [])
        names = [check.get("id") for check in checks]
        if len(names) != len(set(names)) or set(names) != set(stage["checks"]):
            errors.append(stage["id"] + ": 断言覆盖缺失/重复/未知")
        for check in checks:
            evidence = [record for record in readbacks if record.get("id") == check.get("readback_id")]
            if (check.get("pass") is not True or check.get("readback_after_sequence") not in seqs
                    or not check.get("observation") or len(evidence) != 1
                    or evidence[0].get("after_sequence") != check.get("readback_after_sequence")
                    or evidence[0].get("state") != check.get("observation")
                    or not evidence[0].get("player") or not evidence[0].get("state")):
                errors.append(stage["id"] + ": 断言未通过或缺少动作后的观测")
        if preflight_ok and len(errors) == before:
            completed.append(stage["id"])
    return {"status": "trace_valid" if not errors else "invalid", "errors": errors,
            "selected": selected, "completed": completed, "unrun": [key for key in selected if key not in completed],
            "native_verdict": "requires_raw_artifact_review", "player_acceptance": "not_verified"}


class MappedOpeningInput:
    """复用 StoryInput 门；注入现有 Game 与 Rotation 只读函数，不拥有进程。"""

    def __init__(self, game, rotation, emit, player, timeout=8):
        from tools.qianzhen.story_input import StoryInput
        self.game, self.emit, self.player = game, emit, player
        self.gate = StoryInput(game, rotation, emit, timeout)

    def press(self, control):
        if control not in {"w", "a", "s", "d", "space", "e", "h", "o", "y", "v", "x", "F1", "F5", "Escape", *map(str, range(1, 10))}:
            raise ValueError("不支持的映射按键: " + control)
        self.gate.ready("opening_" + control, allow_escape=False)
        self.gate.xdo("key", control)
        self.emit("mapped_input", player=self.player, control=control, display=self.game.display,
                  window=self.gate.window, pid=int(self.gate.pid), time=time.monotonic())

    def click_menu(self, label, x, y):
        if type(x) is not int or type(y) is not int or not 0 <= x < self.game.size[0] or not 0 <= y < self.game.size[1]:
            raise ValueError("映射点击必须位于本次游戏窗口内")
        self.gate.click_menu(label, x, y)
        self.emit("mapped_input", player=self.player, control="left_mouse", display=self.game.display,
                  window=self.gate.window, pid=int(self.gate.pid), time=time.monotonic(), x=x, y=y)

    def close_menu(self):
        self.gate.menu("opening_close_menu")
        self.gate.require_menu("pre_escape")
        self.gate.xdo("key", "Escape")
        self.emit("mapped_input", player=self.player, control="Escape", display=self.game.display,
                  window=self.gate.window, pid=int(self.gate.pid), time=time.monotonic())

    def hold(self, control, seconds):
        if control not in {"w", "a", "s", "d", "space"} or not 0 < seconds <= 2:
            raise ValueError("移动段必须为受限映射按键且不超过2秒")
        self.gate.ready("opening_walk_" + control, allow_escape=False)
        if self.gate.remaining() <= seconds:
            raise TimeoutError("输入预算不足以完成整段移动")
        try:
            self.gate.xdo("keydown", control)
            self.emit("mapped_input", player=self.player, control=control, display=self.game.display,
                      window=self.gate.window, pid=int(self.gate.pid), time=time.monotonic(), hold_seconds=seconds)
            time.sleep(min(seconds, self.gate.remaining()))
            self.gate.remaining()
        finally:
            # Key release must still happen after the work deadline; never leave W held.
            self.game.xdo("keyup", control, timeout=1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--plan", action="store_true")
    mode.add_argument("--audit", type=Path)
    parser.add_argument("--artifact-root", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.audit and not args.artifact_root:
        parser.error("--audit需要--artifact-root")
    result = plan() if args.plan else audit(json.loads(args.audit.read_text()), args.artifact_root)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("x") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("status", "selected", "completed", "unrun")}, ensure_ascii=False))
    if result["status"] == "invalid":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
