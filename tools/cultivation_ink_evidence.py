#!/usr/bin/env python3
"""Modal-only native H-page evidence: mapped clicks, server readback and real moving frames."""
from pathlib import Path
import argparse
import json
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from PIL import Image, ImageChops
from tools.combat_capture import procs, scene, session as cs
from tools.combat_capture.capture import ffmpeg_record_argv
from tools.realm_stats_evidence import Evidence
from tools.golden_core_evidence import parse_profile
from tools.alchemy_evidence import meditation_bindings


def panel_bounds(width, height):
    """Capture uses auto GUI scale 2 at these three known window sizes."""
    if (width, height) not in ((960, 540), (854, 480), (640, 480)):
        raise ValueError("Unsupported capture size")
    gw, gh = width // 2, height // 2
    pw, ph = min(480, gw - 8), min(246, gh - 8)
    left, top = (gw - pw) // 2, (gh - ph) // 2
    bx, by = left + 50, top + 36
    bw = pw - 59
    bh = top + ph - 18 - by
    return dict(left=left, top=top, body_x=bx, body_y=by, body_w=bw,
                action_y=by + bh - 22 + 4 + 9)


def figure_box(size):
    bounds = panel_bounds(*size)
    if bounds["body_w"] < 330:
        raise ValueError("Motion comparison requires the wide two-column layout")
    x, y = bounds["body_x"], bounds["body_y"] + 30
    return (2 * x + 8, 2 * y, 2 * (x + int(bounds["body_w"] * .44)) - 8, 2 * (y + 112))


def changed_pixels(first, second, box):
    with Image.open(first).convert("RGB") as a, Image.open(second).convert("RGB") as b:
        if a.size != b.size:
            raise ValueError("Cannot compare differently sized frames")
        if box[0] < 0 or box[1] < 0 or box[2] > a.width or box[3] > a.height:
            raise ValueError("Motion crop leaves the captured image")
        difference = ImageChops.difference(a.crop(box), b.crop(box))
        return sum(max(pixel) > 8 for pixel in difference.getdata())


class InkEvidence(Evidence):
    def __init__(self, layout_only=False):
        out = ROOT / "out/preview/cultivation-ink-runtime"
        if out.exists() and any(out.iterdir()):
            raise ValueError("Do not overwrite earlier runtime evidence")
        super().__init__(out)
        self.opened = False
        self.layout_only = layout_only
        self.report.update(kind="cultivation_ink_ui", selected=["qi", "foundation", "core", "spirit", "consolidation",
                            "breakthrough", "narrow", "terminal", "study"], completed=[], unrun=[],
                           inputs=[], screenshots=[], videos=[],
                           method=__doc__, owner_appearance="not_verified", owner_fps="not_verified",
                           soul_runtime="not_verified: player progression has no nascent-soul definition",
                           fixture="administrator realm/progress/stone preparation in a disposable flat world")
        if layout_only:
            self.report.update(selected=["layout"], completed=[], unrun=["layout"])

    def must(self, name, passed, detail):
        if not self.check(name, passed, detail):
            raise AssertionError(name)

    def poll(self, fn, predicate, timeout, label):
        end = time.monotonic() + timeout
        last = None
        while time.monotonic() < end:
            last = fn()
            if predicate(last):
                return last
            time.sleep(.15)
        raise TimeoutError(f"{label}: {last}")

    def profile(self):
        return parse_profile(self.rc(f"myvillage cultivation info {self.s.username}"))

    def start(self):
        if ROOT != Path("/workspace/repo"):
            raise RuntimeError("Run Minecraft only in the Modal disposable checkout")
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language="zh_cn",
                                         lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report["source_commit"] = json.loads(Path("/workspace/request.json").read_text())["commit"]
        self.report["phases"].append({"phase": "setup", "capture_timings": state.get("timings", {})})
        options = dict(line.split(":", 1) for line in (cs.CLIENT_DIR / "options.txt").read_text().splitlines() if ":" in line)
        registration = (ROOT / "src/main/java/com/example/myvillage/client/cultivation/ClientCultivationKeyMappings.java").read_text()
        self.report["verified_bindings"] = meditation_bindings(options, registration)
        self.must("profile_binding", options.get("key_key.myvillage.open_cultivation_profile", "key.keyboard.h") == "key.keyboard.h", options.get("key_key.myvillage.open_cultivation_profile", "registered H"))
        u = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(u, "minecraft:air"),
                    "myvillage world pause", "forceload add -16 -16 32 32",
                    f"myvillage cultivation awaken {u}", f"myvillage cultivation initiate {u}"):
            self.rc(cmd)
        self.poll(lambda: self.rc("execute if loaded 0 -61 0 run time query gametime"),
                  lambda r: "The time is " in r, 30, "floor chunk")
        self.rc("fill -4 -61 -4 4 -61 4 minecraft:stone")
        self.rc(f"tp {u} 0.5 -60 0.5 0 0")
        time.sleep(1)

    def click(self, x, y, label):
        self.report["inputs"].append({"action": label, "method": "mapped mouse", "x": x, "y": y})
        self.s.game.xdo("mousemove", str(x), str(y), "click", "1")
        self.s.game.xdo("mousemove", "0", "0")
        self.save()
        time.sleep(.25)

    def panel(self):
        if self.opened:
            return
        g = self.s.game
        g.key("h")
        self.report["inputs"].append({"action": "open H", "method": "mapped H key"})
        time.sleep(.4)
        b = panel_bounds(*g.size)
        self.click(2 * (b["left"] + 20), 2 * (b["top"] + 36 + 21 + 9), "select meditation tab")
        self.must("panel_pointer_released", g.pointer_grabbed() is False, "H page is open")
        self.opened = True

    def close_panel(self):
        if not self.opened:
            return
        self.s.game.xdo("key", "Escape")
        self.report["inputs"].append({"action": "close H", "method": "mapped Escape"})
        time.sleep(.3)
        self.s.game.require_ingame("after H close")
        self.opened = False

    def action(self, index):
        self.panel()
        b = panel_bounds(*self.s.game.size)
        cell = (b["body_w"] - 12) // 4
        self.click(2 * (b["body_x"] + index * (cell + 4) + cell // 2),
                   2 * b["action_y"], ["start normal", "start spirit", "advance", "stop"][index])

    def capture(self, name, moving=False):
        path = self.out / (name + ".png")
        self.s.game.xdo("mousemove", "0", "0")
        if moving:
            raw = self.s.game.grab_raw()
            capture = {"stable": False, "reason": "expected independently moving UI currents"}
        else:
            raw, capture = self.s.game.stable_grab(max_wait=3, settle=.2)
        self.s.game.save_png(raw, path)
        self.report["screenshots"].append({"file": path.name, "purpose": "moving UI frame" if moving else "idle UI layout",
                                           "moving": moving, "capture": capture})
        self.save()
        return path

    def pair(self, name, moving):
        a = self.capture(name + "_a", moving)
        time.sleep(.75)
        b = self.capture(name + "_b", moving)
        count = changed_pixels(a, b, figure_box(self.s.game.size))
        self.must(name + ("_flows" if moving else "_stops"), count > 20 if moving else count <= 10,
                  {"changed_pixels_gt8": count, "crop": figure_box(self.s.game.size)})
        return b

    def record(self, name):
        path = self.out / (name + ".mp4")
        process = subprocess.Popen(ffmpeg_record_argv(self.s.state["display"], path, 4, fps=20, size=self.s.game.size),
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        _, error = process.communicate(timeout=20)
        self.must(name + "_recording", process.returncode == 0 and path.stat().st_size > 1000, error.decode()[-500:])
        self.report["videos"].append({"file": path.name, "seconds": 4, "method": "native client screen recording"})

    def fixture(self, realm, stage, fraction=.25, stable=0):
        self.close_panel()
        self.s.game.key("x")
        data = json.loads((ROOT / f"src/main/resources/data/myvillage/myvillage/realm/{realm}.json").read_text())
        definition = next(s for s in data["stages"] if s["id"] == "myvillage:" + stage)
        for cmd in (f"myvillage cultivation setrealm {self.s.username} myvillage:{realm} myvillage:{stage}",
                    f"myvillage cultivation setprogress {self.s.username} {int(definition.get('cultivation_cap', 0) * fraction)}",
                    f"myvillage cultivation setstability {self.s.username} {stable}"):
            self.rc(cmd)
        time.sleep(.35)
        self.panel()
        return definition

    def run(self):
        self.start()
        if self.layout_only:
            for name, realm, stage in (("qi", "qi_refining", "qi_refining_6"),
                                       ("foundation", "foundation_establishment", "foundation_middle"),
                                       ("core", "golden_core", "golden_middle")):
                self.fixture(realm, stage)
                self.capture(name + "_layout")
            self.narrow()
            self.report.update(completed=["layout"], unrun=[])
            return
        for name, realm, stage in (("qi", "qi_refining", "qi_refining_6"),
                                   ("foundation", "foundation_establishment", "foundation_middle"),
                                   ("core", "golden_core", "golden_middle")):
            self.fixture(realm, stage)
            self.pair(name + "_idle", False)
            before = self.profile()["progress"]
            self.action(0)
            self.poll(self.profile, lambda p: p["progress"] > before, 10, "mapped button starts cultivation")
            self.pair(name + "_active", True)
            self.record(name + "_flow")
            self.action(3)
            stopped = self.profile()
            self.pair(name + "_stopped", False)
            self.must(name + "_server_stopped", self.profile()["progress"] == stopped["progress"], stopped)
            self.report["completed"].append(name)

        self.fixture("foundation_establishment", "foundation_middle")
        self.rc(f"give {self.s.username} myvillage:low_grade_spirit_stone 128")
        before = self.profile()["progress"]
        self.action(1)
        after = self.poll(self.profile, lambda p: p["progress"] >= before + 50, 10, "mapped spirit button")
        self.must("spirit_batch_uses_fixed_gain", (after["progress"] - before) % 50 == 0, after)
        self.pair("foundation_spirit", True)
        self.action(3)
        self.report["completed"].append("spirit")

        self.fixture("foundation_establishment", "foundation_middle", 1, 0)
        self.action(0)
        self.poll(self.profile, lambda p: p["stability"] > 0, 10, "solidification begins")
        self.pair("foundation_consolidating", True)
        self.action(3)
        self.report["completed"].append("consolidation")

        definition = self.fixture("qi_refining", "qi_refining_6", 1, 800)
        before = self.profile()
        self.action(2)
        self.pair("qi_breakthrough", True)
        # Use the current authored duration, then read the actual transition.
        after = self.poll(self.profile, lambda p: p["stage"] == definition["advancement"]["target_stage"],
                          definition["advancement"]["duration_ticks"] / 20 + 8, "mapped breakthrough")
        self.must("breakthrough_keeps_server_rules", after["progress"] == 0 and after["stability"] == before["stability"] // 2, after)
        self.capture("qi_after_breakthrough")
        self.report["completed"].append("breakthrough")

        self.fixture("golden_core", "golden_late", 0, 0)
        before = self.profile()
        self.action(2)
        time.sleep(.5)
        self.must("terminal_does_not_fabricate_progression", self.profile()["stage"] == before["stage"], before)
        self.capture("golden_late_terminal")
        self.report["completed"].append("terminal")

        self.fixture("foundation_establishment", "foundation_middle")
        self.close_panel()
        self.rc(f"clear {self.s.username}")
        self.rc(f"myvillage cultivation manual {self.s.username} myvillage:tuna_xinfa")
        self.s.game.key("1")
        self.s.game.xdo("click", "3")
        self.report["inputs"].append({"action": "study manual", "method": "mapped right mouse"})
        time.sleep(2.3)
        self.panel()
        self.capture("study_card", moving=True)
        self.action(3)
        self.report["completed"].append("study")

        self.narrow()
        self.report["completed"].append("narrow")
        self.report["unrun"] = [v for v in self.report["selected"] if v not in self.report["completed"]]

    def narrow(self):
        self.close_panel()
        window = self.s.game.xdo("search", "--name", "Minecraft").split()[0]
        self.s.game.xdo("windowsize", window, "640", "480")
        self.s.game.size = (640, 480)
        time.sleep(.8)
        self.panel()
        self.capture("narrow_320_top")
        b = panel_bounds(*self.s.game.size)
        self.s.game.xdo("mousemove", str(2 * (b["body_x"] + b["body_w"] // 2)), str(2 * (b["body_y"] + 70)),
                        "click", "--repeat", "12", "--delay", "50", "5")
        time.sleep(.3)
        self.capture("narrow_320_readouts")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--layout-only", action="store_true")
    evidence = InkEvidence(parser.parse_args().layout_only)
    try:
        evidence.run()
        evidence.report["status"] = "pass"
    except BaseException as error:
        evidence.report["status"] = "fail"
        evidence.report["failures"].append(repr(error))
        raise
    finally:
        if evidence.s:
            try:
                evidence.close_panel()
                evidence.stop()
            except BaseException as error:
                evidence.report["failures"].append("cleanup: " + repr(error))
        evidence.save()
    return int(bool(evidence.report["failures"]))


if __name__ == "__main__":
    raise SystemExit(main())
