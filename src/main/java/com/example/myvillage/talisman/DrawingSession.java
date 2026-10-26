package com.example.myvillage.talisman;

/** 单线程有界心跳时钟；不保存租期，因此重登/重启不续画。 */
public final class DrawingSession {
    private long expires = Long.MIN_VALUE, nextPulse = Long.MIN_VALUE;
    public void heartbeat(long now, int leaseTicks) { expires = now + leaseTicks; }
    public void stop() { expires = Long.MIN_VALUE; }
    public boolean holding(long now) { return now < expires; }
    public boolean due(long now) { return holding(now) && now >= nextPulse; }
    public void accepted(long now, int interval) { nextPulse = now + interval; }
    /** Reject before payment; the commit runs once and cannot be retried in the same interval. */
    public boolean pulse(long now, int interval, boolean materialsReady, boolean outputFits,
            java.util.function.BooleanSupplier payment, Runnable commit) {
        if (!due(now) || !materialsReady || !outputFits || !payment.getAsBoolean()) return false;
        // Reserve the interval before invoking the container commit, preventing reentrant double-payment.
        accepted(now, interval); commit.run(); return true;
    }
    public void reset() { stop(); nextPulse = Long.MIN_VALUE; }
}
