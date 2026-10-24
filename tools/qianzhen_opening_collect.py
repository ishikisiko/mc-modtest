#!/usr/bin/env python3
"""接入已启动的独立 Colab 客户端，执行真实映射采集；不启动或停止共享会话。

原子采集层；完整受控入口见 qianzhen_opening_run.py。执行完只产生
captured_not_accepted，需独立审阅和完整审计。
"""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))

from tools.qianzhen_opening_evidence import MappedOpeningInput, digest, guard_rcon, source_snapshot


OPS = {"key", "hold", "mouse", "left_click", "right_click", "menu_click", "close_menu", "dialogue_option", "capture", "readback", "expect_status", "wait"}


def validate_script(script):
    if not isinstance(script, list) or not 1 <= len(script) <= 2000:
        raise ValueError("脚本需要1..2000条受限操作")
    for step in script:
        if step.get("op") not in OPS:
            raise ValueError("未知操作，禁止任意shell/RCON推进: " + str(step.get("op")))
        if step["op"] in {"readback"}:
            guard_rcon(step.get("command", ""))
        if step["op"] == "wait" and (type(step.get("seconds")) not in (int, float) or not 0 < step["seconds"] <= 10):
            raise ValueError("单段等待上限10秒")
        if step["op"] == "expect_status" and not 0 < step.get("timeout", 8) <= 30:
            raise ValueError("状态等待上限30秒")
        if step["op"] == "mouse" and any(type(step.get(key)) is not int or abs(step[key]) > 240 for key in ("dx", "dy")):
            raise ValueError("真实鼠标单段位移上限240px")


def validate_attachment(manifest, root):
    if os.environ.get("MYVILLAGE_CLOUD_PROVIDER") != "Colab" or not Path("/content").is_dir():
        raise ValueError("只允许显式Colab；无本机回退")
    run_id = manifest.get("run_id", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{8,100}", run_id) or run_id not in root.resolve().parts:
        raise ValueError("必须使用含run_id的独立checkout，不得接入旧失败世界")
    snapshot = source_snapshot(root)
    if (snapshot["tracked_status"] or snapshot["untracked_runtime"] or snapshot["commit"] != manifest.get("source_commit")
            or snapshot["fingerprint"] != manifest.get("source_fingerprint")):
        raise ValueError("源提交/文件已漂移或tracked不干净")
    state_path = Path(manifest["session_state"]).resolve()
    if not state_path.is_relative_to(root.resolve()) or digest(state_path) != manifest["session_state_sha256"]:
        raise ValueError("session state不在本次独立目录或身份漂移")
    state = json.loads(state_path.read_text())
    if state.get("phase") != "ready" or not state.get("natural_terrain") or state.get("world_seed") != 20261024:
        raise ValueError("需要真实自然seed20261024会话")
    from tools.combat_capture import procs
    server = state["procs"]["server"]
    if not procs.is_alive(server["pid"], server.get("start")) or not Path(f'/proc/{server["pid"]}/cwd').resolve().is_relative_to(root.resolve()):
        raise ValueError("服务器不属于本次checkout")
    world = root / "run-acceptance/combat_capture_world"
    if (not world.resolve().is_relative_to(root.resolve()) or manifest.get("fresh_world") is not True
            or not (world / "level.dat").is_file() or digest(world / "level.dat") != manifest["initial_level_dat_sha256"]):
        raise ValueError("启动世界身份不符")
    # Reuse the existing first-binding seed replay and request/pack validation.
    from tools.qianzhen.evidence_candidate import Candidate, runtime_request
    candidate_path, request_path = Path(manifest["candidate"]), Path(manifest["request"])
    if digest(request_path) != manifest["request_sha256"]:
        raise ValueError("固定候选请求漂移")
    candidate = Candidate(candidate_path, root)
    runtime_request(root, request_path, "Colab", candidate)
    with tempfile.TemporaryDirectory(prefix="qianzhen-opening-pack-") as folder:
        candidate.prepare_pack(Path(folder))
        candidate.verify_installed(world)
    log = Path(state["client_log"]).resolve()
    if not log.is_relative_to(root.resolve()):
        raise ValueError("客户端日志不属于本次checkout")
    return state


class OpeningCollector:
    """操作驱动可离线注入fake Game/RCON测试；生产默认使用真实X11和RCON。"""

    def __init__(self, game, read_command, username, out):
        self.game, self.read_command, self.username, self.out = game, read_command, username, out
        out.mkdir(parents=True, exist_ok=False)
        self.stream = (out / "actions.jsonl").open("x")
        self.readbacks = (out / "readbacks.jsonl").open("x")
        self.sequence, self.readback_id, self.capture_id, self.offset = 0, 0, 0, game.client_log.stat().st_size
        self.input = MappedOpeningInput(game, self.rotation, self.emit, username)

    def emit(self, kind, **detail):
        self.sequence += 1
        self.stream.write(json.dumps({"sequence": self.sequence, "kind": kind, "time": time.monotonic(), **detail}, ensure_ascii=False) + "\n")
        self.stream.flush()

    def read(self, command, timeout=8):
        guard_rcon(command)
        self.emit("rcon", command=command)
        self.readback_id += 1
        record = {"id": self.readback_id, "after_sequence": self.sequence,
                  "player": self.username, "command": command, "time": time.monotonic()}
        try:
            reply = self.read_command(command, timeout)
            record["state"] = reply
        except BaseException as error:
            record["failure"] = repr(error)
            raise
        finally:
            record["elapsed_seconds"] = time.monotonic() - record["time"]
            self.readbacks.write(json.dumps(record, ensure_ascii=False) + "\n")
            self.readbacks.flush()
        return reply

    def rotation(self, remaining):
        from tools.alchemy_quality_evidence import read_data
        values = read_data(self.read(f"data get entity {self.username} Rotation", min(8, remaining)))
        if len(values) != 2 or any(not isinstance(value, (int, float)) or not math.isfinite(value) for value in values):
            raise ValueError("Rotation读回不合法")
        return values

    def capture(self, label):
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", label):
            raise ValueError("截图名不合法")
        self.capture_id += 1
        target = self.out / f"{self.sequence:05d}-{self.capture_id:04d}-{label}.png"
        if target.exists():
            raise ValueError("拒绝覆盖截图")
        self.game.save_png(self.game.grab_raw(), target)
        return {"path": target.name, "sha256": digest(target)}

    def execute(self, step):
        op = step["op"]
        if op == "key":
            self.input.press(step["key"])
        elif op == "hold":
            self.input.hold(step["key"], step["seconds"])
        elif op in {"mouse", "left_click", "right_click"}:
            self.input.gate.ready("opening_" + op, allow_escape=False)
            if op in {"left_click", "right_click"}:
                self.offset = self.game.client_log.stat().st_size
                self.input.gate.xdo("click", "1" if op == "left_click" else "3")
            else:
                self.input.gate.xdo("mousemove_relative", "--", str(step["dx"]), str(step["dy"]))
            self.emit("mapped_input", player=self.username, display=self.game.display, window=self.input.gate.window,
                      pid=int(self.input.gate.pid), control=op, **{key: step[key] for key in ("dx", "dy") if key in step})
        elif op == "menu_click":
            # A screenshot plus the existing fresh pre-dispatch grab gate are mandatory.
            self.capture("menu-before-click")
            x, y = step["x"], step["y"]
            if type(x) is not int or type(y) is not int or not 0 <= x < self.game.size[0] or not 0 <= y < self.game.size[1]:
                raise ValueError("点击坐标越界")
            self.input.click_menu("opening_menu", x, y)
        elif op == "close_menu":
            self.input.close_menu()
        elif op == "dialogue_option":
            from tools.qianzhen.story_evidence_helpers import dialogue_buttons
            deadline = time.monotonic() + 8
            while True:
                with self.game.client_log.open() as stream:
                    stream.seek(self.offset)
                    buttons = dialogue_buttons(stream.read(), self.game.size, step["option"])
                if len(buttons) == 1:
                    break
                if time.monotonic() >= deadline:
                    raise TimeoutError("未收到新鲜对话选项: " + step["option"])
                time.sleep(.1)
            button = buttons[0]
            self.capture("dialogue-before-click")
            self.offset = self.game.client_log.stat().st_size
            self.input.click_menu("opening_dialogue_" + step["option"], button["x"] + button["w"] // 2, button["y"] + button["h"] // 2)
        elif op == "readback":
            self.read(step["command"])
        elif op == "expect_status":
            from tools.qianzhen.story_evidence_helpers import status_object
            deadline = time.monotonic() + step.get("timeout", 8)
            while True:
                state = status_object(self.read(f"myvillage story status {self.username}", min(8, deadline - time.monotonic())))
                actual = state
                for key in step["path"]:
                    actual = actual[key]
                if type(actual) is type(step["equals"]) and actual == step["equals"]:
                    return {"actual": actual, "expected": step["equals"]}
                if time.monotonic() >= deadline:
                    raise AssertionError({"path": step["path"], "actual": actual, "expected": step["equals"]})
                time.sleep(.15)
        elif op == "capture":
            return self.capture(step["label"])
        elif op == "wait":
            time.sleep(step["seconds"])
        else:
            raise ValueError("未知操作")

    def run(self, script):
        validate_script(script)
        deadline = time.monotonic() + 3300
        report = {"status": "running", "selected": list(range(len(script))), "completed": [], "unrun": list(range(len(script))),
                  "results": [], "failures": [], "owner_verdict": "not_verified"}
        try:
            for index, step in enumerate(script):
                if time.monotonic() >= deadline:
                    raise TimeoutError("本次采集整体预算3300秒耗尽")
                report["results"].append({"index": index, "step": step, "result": self.execute(step)})
                if time.monotonic() >= deadline:
                    raise TimeoutError("采集操作迟到，不能计入完成")
                report["completed"].append(index)
                report["unrun"].remove(index)
                self._save(report)
            report["status"] = "captured_not_accepted"
        except BaseException as error:
            report["status"] = "fail"
            report["failures"].append(repr(error))
            try:
                report["failure_frame"] = self.capture("failure")
            except Exception as capture_error:
                report["failures"].append("failure capture: " + repr(capture_error))
            raise
        finally:
            self._save(report)
            self.stream.close()
            self.readbacks.close()
        return report

    def _save(self, report):
        (self.out / "collector.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")


def run_recorded(collector, script):
    from tools.combat_capture.capture import ffmpeg_record_argv, stop_recording
    recorder, failure = None, None
    try:
        with (collector.out / "recording.log").open("xb") as log:
            recorder = subprocess.Popen(ffmpeg_record_argv(collector.game.display, collector.out / "capture.mp4", 3600,
                                        size=collector.game.size), stdin=subprocess.PIPE, stdout=log, stderr=log)
        if recorder.poll() is not None:
            raise RuntimeError("录像进程启动即退出")
        collector.run(script)
        if recorder.poll() is not None:
            raise RuntimeError("录像在采集完成前退出，不能作为完整证据")
    except BaseException as error:
        failure = error
    finally:
        cleanup = {"pid": recorder.pid if recorder else None, "terminated": recorder is None}
        if recorder:
            try:
                try:
                    cleanup["exit_code"] = stop_recording(recorder, timeout=10)
                except subprocess.TimeoutExpired:
                    recorder.kill()  # Only this Popen, never a shared display/server/client.
                    cleanup["exit_code"] = recorder.wait(timeout=3)
                cleanup["terminated"] = recorder.poll() is not None
                if cleanup["exit_code"] != 0 and failure is None:
                    failure = RuntimeError("录像未正常收尾")
            except BaseException as error:
                cleanup["failure"] = repr(error)
                if failure is None:
                    failure = error
        (collector.out / "recording-exit.json").write_text(json.dumps(cleanup) + "\n")
        collector.stream.close()
        collector.readbacks.close()
        if failure:
            report_path = collector.out / "collector.json"
            report = json.loads(report_path.read_text()) if report_path.exists() else {
                "selected": list(range(len(script))), "completed": [], "unrun": list(range(len(script))), "failures": []}
            report["status"] = "fail"
            report["failures"].append(repr(failure))
            collector._save(report)
    if failure:
        raise failure


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", type=Path, required=True, help="固定源后准备的manifest；本轮不执行")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.execute.read_text())
    validate_script(manifest["steps"])
    state = validate_attachment(manifest, ROOT)
    from tools.combat_capture.xgame import Game
    from tools.qianzhen.story_lifecycle import lifecycle_command
    game = Game(state["display"], Path(state["client_log"]), size=tuple(manifest.get("size", [960, 540])))
    # Read-only window identity check before any gate sends mouse motion.
    windows = game.xdo("search", "--onlyvisible", "--name", "Minecraft").split()
    if len(windows) != 1:
        raise ValueError("目标display必须恰好一个Minecraft窗口")
    pid = int(game.xdo("getwindowpid", windows[0]))
    if not Path(f"/proc/{pid}/cwd").resolve().is_relative_to(ROOT.resolve()):
        raise ValueError("目标窗口不属于本次独立checkout")

    def read(command, timeout):
        if timeout <= 0:
            raise TimeoutError("权威读回预算耗尽")
        return lifecycle_command(state, command, timeout)

    collector = OpeningCollector(game, read, state["username"], args.out)
    (args.out / "binding.json").write_text(json.dumps({"run_id": manifest["run_id"], "source_commit": manifest["source_commit"],
        "source_fingerprint": manifest["source_fingerprint"], "manifest_sha256": digest(args.execute),
        "session_state_sha256": manifest["session_state_sha256"], "request_sha256": manifest["request_sha256"]}, indent=2) + "\n")
    # Only this ffmpeg process is owned here. Server/client lifecycle belongs to the driver.
    run_recorded(collector, manifest["steps"])


if __name__ == "__main__":
    main()
