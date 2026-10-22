package com.example.myvillage.combat.elemental;

/** Bounded per-element combat accumulation. Read/consume expiry is exact, not dependent on a tick sweep. */
public final class ElementalCharge {
    private int value;
    private long until;
    public int value(long now){if(now>=until)value=0;return value;}
    public int gain(long now,int maximum,int lifetime){value=Math.min(maximum,value(now)+1);until=now+lifetime;return value;}
    public int consume(long now){int old=value(now);value=0;return old;}
    public static double multiplier(int charges,double bonus){return 1+Math.max(0,charges)*bonus;}
}
