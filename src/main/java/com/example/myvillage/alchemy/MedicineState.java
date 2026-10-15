package com.example.myvillage.alchemy;

/** Remaining online ticks, never a wall-clock deadline. Independent from cultivation saves. */
public record MedicineState(int cooldownTicks, String effect, int effectTicks, int healingElapsed) {
    public static final MedicineState EMPTY = new MedicineState(0, "", 0, 0);

    public MedicineState {
        if (cooldownTicks < 0 || effectTicks < 0 || healingElapsed < 0)
            throw new IllegalArgumentException("Medicine counters must be non-negative");
        if (effect == null || !(effect.isEmpty() || effect.equals("yangyuan") || effect.equals("ningshen")))
            throw new IllegalArgumentException("Unknown sustained medicine effect: " + effect);
        if (effect.isEmpty() != (effectTicks == 0))
            throw new IllegalArgumentException("Medicine effect and duration must agree");
        if (!effect.equals("yangyuan") && healingElapsed != 0)
            throw new IllegalArgumentException("Only healing medicine has a healing clock");
    }

    public boolean available() { return cooldownTicks == 0 && effectTicks == 0; }

    public MedicineState begin(String id, int cooldown, int duration) {
        if (!available()) throw new IllegalStateException("Medicine effects cannot stack");
        if (id.equals("huiling")) return new MedicineState(cooldown, "", 0, 0);
        return new MedicineState(cooldown, id, duration, 0);
    }

    public MedicineState clearBenefit() { return new MedicineState(cooldownTicks, "", 0, 0); }

    /** A single online tick; the caller delivers the optional healing before storing next(). */
    public Tick tick(int healingInterval) {
        if (healingInterval <= 0) throw new IllegalArgumentException("Healing interval must be positive");
        int cooldown = Math.max(0, cooldownTicks - 1);
        if (effectTicks == 0) return new Tick(new MedicineState(cooldown, "", 0, 0), false);
        int elapsed = effect.equals("yangyuan") ? healingElapsed + 1 : 0;
        boolean heal = effect.equals("yangyuan") && elapsed >= healingInterval;
        int remaining = effectTicks - 1;
        return new Tick(remaining == 0 ? new MedicineState(cooldown, "", 0, 0)
                : new MedicineState(cooldown, effect, remaining, heal ? 0 : elapsed), heal);
    }

    public record Tick(MedicineState next, boolean heal) { }
}
