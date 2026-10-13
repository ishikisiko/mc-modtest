package com.example.myvillage.client.combat;

/** One press per physical hold, including when the server auto-releases a fully charged skill. */
final class QiUseLatch {
    private boolean held;
    private boolean cancelled;
    boolean press() {
        if (held) return false;
        held = true;
        cancelled = false;
        return true;
    }
    boolean release(boolean keyDown) {
        if (!held || keyDown) return false;
        held = false;
        boolean release = !cancelled;
        cancelled = false;
        return release;
    }
    boolean cancel() {
        if (!held || cancelled) return false;
        cancelled = true;
        return true;
    }
    void clear() { held = false; cancelled = false; }
}
