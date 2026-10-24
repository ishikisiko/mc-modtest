"""第一章窗口/抓取门；只发真实 X 输入，Rotation 仅作权威只读回执。"""
from __future__ import annotations

import time
from dataclasses import dataclass

from tools.qianzhen.story_lifecycle import bounded_budget
from tools.qianzhen.story_pointer import BOUNDARY, probe_input_state

DEFAULT_INPUT_TIMEOUT = 8.0
MAX_INPUT_TIMEOUT = 30.0


@dataclass(frozen=True)
class InputState:
    focused: str
    grabbed: bool
    grabbed_after: bool
    focused_after: str
    window: str
    focus_events: tuple
    boundary: str

    @property
    def actor_focused(self):
        return (self.boundary == BOUNDARY and not self.focus_events
                and self.focused == self.focused_after == self.window)

    @property
    def ingame(self):
        return self.actor_focused and self.grabbed is self.grabbed_after is True

    @property
    def menu(self):
        return self.actor_focused and self.grabbed is self.grabbed_after is False


class StoryInput:
    def __init__(self, game, rotation, emit, timeout=DEFAULT_INPUT_TIMEOUT):
        self.game, self.rotation, self.emit = game, rotation, emit
        self.timeout = bounded_budget('input-timeout', timeout, MAX_INPUT_TIMEOUT)
        self.window = self.pid = None

    def remaining(self):
        value = self.deadline-time.monotonic()
        if value <= 0:
            raise TimeoutError('Story input deadline exhausted: '+self.label)
        return value

    def xdo(self, *args):
        result = self.game.xdo(*args, timeout=min(3.0, self.remaining()))
        self.remaining()  # A late result never counts as success.
        return result.strip()

    def note(self, event, **detail):
        self.emit('input_state', label=self.label, event=event, display=self.game.display,
                  client_log=str(self.game.client_log), window=self.window, pid=self.pid,
                  timeout_seconds=self.timeout,
                  elapsed_seconds=round(time.monotonic()-self.started, 4), **detail)

    def quiet(self, seconds):
        # XGrabPointer is an active probe. Never race it against a closing frame
        # or GLFW's response to the recovery click (see Game.type_chat).
        time.sleep(min(seconds, self.remaining()))
        self.remaining()

    def bind(self):
        windows = self.xdo('search', '--onlyvisible', '--name', 'Minecraft').split()
        if self.window is None:
            if len(windows) != 1:
                raise RuntimeError('Expected one visible Minecraft window on actor display: '+repr(windows))
            self.window = windows[0]
            self.pid = self.xdo('getwindowpid', self.window)
        if windows != [self.window] or self.xdo('getwindowpid', self.window) != self.pid:
            raise RuntimeError('Actor window identity changed')
        self.note('window', title=self.xdo('getwindowname', self.window))

    def focus(self):
        focused = self.xdo('getwindowfocus')
        self.note('focus', focused=focused)
        if focused != self.window:
            # Xvfb has no window manager: windowactivate's EWMH is unsuitable.
            self.xdo('windowfocus', '--sync', self.window)
            self.note('focus_input', focused=self.xdo('getwindowfocus'))
            self.quiet(.6)
        if self.xdo('getwindowfocus') != self.window:
            raise RuntimeError('Actor window did not receive focus')

    def state(self, phase='state'):
        sample = probe_input_state(self.game.display, self.window, self.deadline)
        self.remaining()
        self.note('grab', phase=phase, **{key: value for key, value in sample.items() if key != 'window'})
        if sample['grabbed'] is None or sample['grabbed_after'] is None:
            raise RuntimeError('Pointer probe unavailable; cannot prove actor input')
        return InputState(sample['focused'], sample['grabbed'], sample['grabbed_after'], sample['focused_after'],
                          self.window, tuple(sample['focus_events']), sample['boundary'])

    def middle(self):
        self.focus()
        w, h = self.game.size
        self.xdo('mousemove', '--window', self.window, str(w//2), str(h//2))
        self.xdo('click', '2')
        self.note('middle_input')
        self.quiet(.6)

    def mouse_receipt(self):
        # A display-global AlreadyGrabbed result cannot identify its owner.
        # Require this actor's yaw to follow physical motion in both directions.
        before = self.rotation(self.remaining())
        for dx, target in ((12, before[0]+1.8), (-12, before[0])):
            self.xdo('mousemove_relative', '--', str(dx), '0')
            self.note('mouse_input', dx=dx, target_yaw=target)
            self.quiet(.25)
            while True:
                after = self.rotation(self.remaining())
                self.remaining()
                error = (after[0]-target+180) % 360-180
                self.note('rotation', before=before, actual=after, target_yaw=target, error=error)
                if abs(error) < .35:
                    break
                self.quiet(.15)
        if not self.state().ingame:
            raise RuntimeError('Actor lost focus/grab during mouse receipt')

    def activate(self, label):
        """Focus before the existing second-client startup wait; no grab assertion."""
        self.started = time.monotonic()
        self.deadline, self.label = self.started+self.timeout, label
        try:
            self.bind()
            self.focus()
            self.note('activated')
        except BaseException as error:
            self.note('failed', error_type=type(error).__name__, error=str(error))
            raise

    def menu(self, label):
        self.started = time.monotonic()
        self.deadline, self.label = self.started+self.timeout, label
        try:
            self.bind()
            self.focus()
            self.require_menu()
            self.note('menu_confirmed')
        except BaseException as error:
            self.note('failed', error_type=type(error).__name__, error=str(error))
            raise

    def require_menu(self, phase='menu_sample'):
        state = self.state(phase)
        if not state.actor_focused:
            raise RuntimeError('Actor menu lost focus')
        if not state.menu:
            raise RuntimeError('Expected open actor menu before physical button input')

    def click_menu(self, label, x, y):
        """Finite start/end samples and a fresh final gate; not continuous proof."""
        self.menu(label)
        try:
            self.xdo('mousemove', '--sync', '--window', self.window, str(x), str(y))
            self.bind()  # Check identity again before the final dispatch sample.
            self.require_menu('pre_dispatch')
            # No focus recovery, sleep or unrelated subprocess after this gate.
            self.xdo('click', '1')
            self.note('menu_click_dispatched', gate='fresh-start-end-sample')
        except BaseException as error:
            self.note('failed', error_type=type(error).__name__, error=str(error))
            raise

    def ready(self, label, *, allow_escape=True):
        self.started = time.monotonic()
        self.deadline, self.label = self.started+self.timeout, label
        try:
            self.bind()
            self.focus()
            self.quiet(.6)
            ready = self.state().ingame
            if not ready:
                # Closed-but-ungrabbed must not receive Escape (would open Pause).
                # A middle click has no dialogue button action and can re-grab.
                self.middle()
                ready = self.state().ingame
            if not ready and allow_escape:
                self.focus()
                self.xdo('key', 'Escape')
                self.note('escape_input')
                self.quiet(.8)
                ready = self.state().ingame
                if not ready:
                    self.middle()
            while not self.state().ingame:
                self.quiet(.25)
            self.mouse_receipt()
            self.remaining()
            self.note('confirmed')
        except BaseException as error:
            self.note('failed', error_type=type(error).__name__, error=str(error))
            raise
