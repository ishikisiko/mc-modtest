package com.example.myvillage.client.cultivation.motion;

/** Pure epoch/timing rules. Duplicate snapshots never rewind a seated observer. */
public final class MeditationTimeline {
    private long revision;
    private double start;
    private float from;
    private int duration;
    private boolean active;
    public boolean accept(long revision,boolean active,long at,int enterTicks,boolean graceful,int exitTicks) {
        if(revision<=this.revision)return false;
        float previous=weight(at);
        this.revision=revision;this.start=at;this.active=active;
        this.from=active?previous:graceful?previous:0;
        this.duration=active?enterTicks:Math.max(1,Math.round(exitTicks*from));
        return true;
    }
    public float weight(double now) {
        if(revision==0)return 0;
        float t=(float)Math.clamp((now-start)/Math.max(1,duration),0,1);t=t*t*(3-2*t);
        return active?from+(1-from)*t:from*(1-t);
    }
    public void abort(double now) {active=false;from=0;start=now;duration=1;}
    public boolean active() {return active;}
    public long revision() {return revision;}
    public double age(double now) {return Math.max(0,now-start);}
}
