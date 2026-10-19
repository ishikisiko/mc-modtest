package com.example.myvillage.client.boss;

/** Presentation-only damage echo. Healing and a new encounter never show false lost health. */
final class BossBarTrail {
    private float health, from;
    private long changedAt;
    long lastSeen;
    BossBarTrail(float health, long now) {
        this.health = this.from = BossBarLayout.fraction(health);
        changedAt = lastSeen = now;
    }
    float sample(float value, long now) {
        value = BossBarLayout.fraction(value);
        if (now < lastSeen || value > health) { from = value; changedAt = now; }
        else if (value < health) { from = current(now); changedAt = now; }
        health = value;
        lastSeen = now;
        return current(now);
    }
    private float current(long now) {
        float t = Math.clamp((now - changedAt - 250) / 500f, 0, 1);
        return Math.max(health, from + (health - from) * t);
    }
}
