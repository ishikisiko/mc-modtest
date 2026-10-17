package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.HashSet;

/** Independent attachment. New optional combat state preserves all existing choices and timers. */
public record FateState(long revision, long rolls, List<String> candidates, String locked,
        List<String> selected, boolean confirmed, long recoveryReadyAt,
        int healingTicks, double healingPerTick, long hunterReadyAt,
        PillAegisState pillAegis, TemperedBonesState temperedBones,
        SwordHeartState swordHeart, WindStepState windStep, FireSpiritCooldownState fireSpirit) {
    private static final Codec<List<String>> IDS = Codec.STRING.listOf().validate(ids ->
            ids.size() <= 6 && new HashSet<>(ids).size() == ids.size()
                    && ids.stream().allMatch(s -> s.length() <= 128 && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                    ? com.mojang.serialization.DataResult.success(ids)
                    : com.mojang.serialization.DataResult.error(() -> "Invalid bounded fate IDs"));
    public static final Codec<FateState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("revision", 0L).forGetter(FateState::revision),
            Codec.LONG.optionalFieldOf("rolls", 0L).forGetter(FateState::rolls),
            IDS.optionalFieldOf("candidates", List.of()).forGetter(FateState::candidates),
            Codec.STRING.optionalFieldOf("locked", "").forGetter(FateState::locked),
            IDS.optionalFieldOf("selected", List.of()).forGetter(FateState::selected),
            Codec.BOOL.optionalFieldOf("confirmed", false).forGetter(FateState::confirmed),
            Codec.LONG.optionalFieldOf("recovery_ready_at", 0L).forGetter(FateState::recoveryReadyAt),
            Codec.intRange(0, 24000).optionalFieldOf("healing_ticks", 0).forGetter(FateState::healingTicks),
            Codec.DOUBLE.optionalFieldOf("healing_per_tick", 0.0).forGetter(FateState::healingPerTick),
            Codec.LONG.optionalFieldOf("hunter_ready_at", 0L).forGetter(FateState::hunterReadyAt),
            PillAegisState.CODEC.optionalFieldOf("pill_aegis", PillAegisState.EMPTY).forGetter(FateState::pillAegis),
            TemperedBonesState.CODEC.optionalFieldOf("tempered_bones", TemperedBonesState.EMPTY).forGetter(FateState::temperedBones),
            SwordHeartState.CODEC.optionalFieldOf("sword_heart", SwordHeartState.EMPTY).forGetter(FateState::swordHeart),
            WindStepState.CODEC.optionalFieldOf("wind_step", WindStepState.EMPTY).forGetter(FateState::windStep),
            FireSpiritCooldownState.CODEC.optionalFieldOf("fire_spirit", FireSpiritCooldownState.EMPTY).forGetter(FateState::fireSpirit)
    ).apply(i, FateState::new));
    public FateState {
        java.util.Objects.requireNonNull(pillAegis, "pillAegis");
        java.util.Objects.requireNonNull(temperedBones, "temperedBones");
        java.util.Objects.requireNonNull(swordHeart, "swordHeart");
        java.util.Objects.requireNonNull(windStep, "windStep");
        java.util.Objects.requireNonNull(fireSpirit, "fireSpirit");
        candidates = List.copyOf(candidates); selected = List.copyOf(selected);
        if (revision < 0 || rolls < 0 || candidates.size() > 6 || selected.size() > 3
                || new HashSet<>(candidates).size() != candidates.size()
                || new HashSet<>(selected).size() != selected.size()
                || (!locked.isEmpty() && !candidates.contains(locked))
                || !candidates.containsAll(selected) || (confirmed && selected.size() != 3)
                || (!confirmed && !selected.isEmpty()) || healingTicks < 0 || healingTicks > 24000
                || !Double.isFinite(healingPerTick) || healingPerTick < 0
                || recoveryReadyAt < 0 || hunterReadyAt < 0) throw new IllegalArgumentException("Invalid fate state");
    }
    /** Source compatibility for older fixtures; CODEC supplies the same defaults for legacy saves. */
    public FateState(long revision, long rolls, List<String> candidates, String locked,
            List<String> selected, boolean confirmed, long recoveryReadyAt,
            int healingTicks, double healingPerTick, long hunterReadyAt) {
        this(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, PillAegisState.EMPTY, TemperedBonesState.EMPTY);
    }
    public FateState(long revision, long rolls, List<String> candidates, String locked,
            List<String> selected, boolean confirmed, long recoveryReadyAt,
            int healingTicks, double healingPerTick, long hunterReadyAt,
            PillAegisState pillAegis, TemperedBonesState temperedBones) {
        this(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, pillAegis, temperedBones,
                SwordHeartState.EMPTY, WindStepState.EMPTY, FireSpiritCooldownState.EMPTY);
    }
    public static FateState empty() { return new FateState(0, 0, List.of(), "", List.of(), false, 0, 0, 0, 0); }
    public FateState choice(List<String> pool, String lock, List<String> picks, boolean done, long newRolls) {
        return new FateState(Math.addExact(revision, 1), newRolls, pool, lock, picks, done,
                recoveryReadyAt, healingTicks, healingPerTick, hunterReadyAt, pillAegis, temperedBones, swordHeart, windStep, fireSpirit);
    }
    public FateState timers(long recovery, int ticks, double perTick, long hunter) {
        return new FateState(revision, rolls, candidates, locked, selected, confirmed, recovery, ticks, perTick, hunter,
                pillAegis, temperedBones, swordHeart, windStep, fireSpirit);
    }
    public FateState withPillAegis(PillAegisState state) {
        return new FateState(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, state, temperedBones, swordHeart, windStep, fireSpirit);
    }
    public FateState withTemperedBones(TemperedBonesState state) {
        return new FateState(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, pillAegis, state, swordHeart, windStep, fireSpirit);
    }
    public FateState withSwordHeart(SwordHeartState state) {
        return new FateState(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, pillAegis, temperedBones, state, windStep, fireSpirit);
    }
    public FateState withWindStep(WindStepState state) {
        return new FateState(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, pillAegis, temperedBones, swordHeart, state, fireSpirit);
    }
    public FateState withFireSpirit(FireSpiritCooldownState state) {
        return new FateState(revision, rolls, candidates, locked, selected, confirmed, recoveryReadyAt,
                healingTicks, healingPerTick, hunterReadyAt, pillAegis, temperedBones, swordHeart, windStep, state);
    }
    public FateState afterDeath() {
        return timers(recoveryReadyAt, 0, 0, hunterReadyAt)
                .withPillAegis(pillAegis.clearShield()).withTemperedBones(TemperedBonesState.EMPTY)
                .withSwordHeart(swordHeart.clearOpening()).withWindStep(windStep.clearAirCharge());
    }
    public FateState resetChoices() {
        return new FateState(Math.addExact(revision, 1), rolls, List.of(), "", List.of(), false,
                recoveryReadyAt, 0, 0, hunterReadyAt, pillAegis.clearShield(), TemperedBonesState.EMPTY,
                swordHeart.clearOpening(), windStep.clearAirCharge(), fireSpirit);
    }
}
