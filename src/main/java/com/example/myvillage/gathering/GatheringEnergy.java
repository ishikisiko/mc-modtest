package com.example.myvillage.gathering;

/** Charges retain the grade of the stone that created them, even after the slot changes. */
public record GatheringEnergy(int charges, GatheringFuel fuel) {
    public static final GatheringEnergy EMPTY = new GatheringEnergy(0, null);
    public GatheringEnergy {
        if (charges < 0 || charges > Short.MAX_VALUE || charges > 0 && fuel == null || charges == 0 && fuel != null)
            throw new IllegalArgumentException("Invalid gathering credit");
    }
    public int bonus(GatheringTier tier, GatheringFuel queued, GatheringRules rules) {
        return rules.tier(tier).bonus(charges > 0 ? fuel : queued);
    }
    public record Debit(GatheringEnergy remaining, boolean consumedStone) { }
    public Debit debit(GatheringTier tier, GatheringFuel queued, GatheringRules rules) {
        var source = charges > 0 ? fuel : queued;
        if (rules.tier(tier).bonus(source) == 0) return null;
        int left = (charges > 0 ? charges : rules.charges(source)) - 1;
        return new Debit(left == 0 ? EMPTY : new GatheringEnergy(left, source), charges == 0);
    }
    public static GatheringEnergy restore(int version, int charges, String grade, GatheringTier tier, GatheringRules rules) {
        if (charges <= 0 || charges > Short.MAX_VALUE) return EMPTY;
        var fuel = version == 1 ? GatheringFuel.LOW : version == 2 ? GatheringFuel.fromId(grade) : null;
        return rules.tier(tier).bonus(fuel) > 0 ? new GatheringEnergy(charges, fuel) : EMPTY;
    }
}
