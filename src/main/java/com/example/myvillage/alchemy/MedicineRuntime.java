package com.example.myvillage.alchemy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.cultivation.fate.PillAegisEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Logical-server medication lifecycle. Persistent remaining counters deliberately exclude offline time. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class MedicineRuntime {
    private static final String TAG = "myvillage_medicine";
    private static final String MESSAGE = "message.myvillage.alchemy.";

    private MedicineRuntime() { }

    public static MedicineState state(ServerPlayer player) {
        return read(player.getPersistentData().getCompound(TAG), AlchemyRules.get());
    }

    static MedicineState read(CompoundTag tag, AlchemyRules rules) {
        int cooldown = bound(tag.getInt("cooldown_ticks"), rules.sharedCooldownTicks());
        String effect = tag.getString("effect");
        int quality = tag.contains("quality", Tag.TAG_INT) ? tag.getInt("quality") : 1;
        if (quality < 1 || quality > 4) quality = 1;
        int maximum = switch (effect) {
            case "yangyuan" -> rules.effects().yangyuan().durationTicks();
            case "ningshen" -> rules.effects().ningshen().durationTicks();
            default -> com.example.myvillage.wilderness.ExpeditionRules.get().isMedicine(effect) ? com.example.myvillage.wilderness.ExpeditionRules.get().medicine(effect).durationTicks(quality) : 0;
        };
        int remaining = bound(tag.getInt("effect_ticks"), maximum);
        if (remaining == 0) return new MedicineState(cooldown, "", 0, 0);
        int elapsed = effect.equals("yangyuan")
                ? bound(tag.getInt("healing_elapsed"), rules.effects().yangyuan().intervalTicks() - 1) : 0;
        return new MedicineState(cooldown, effect, remaining, elapsed, quality);
    }

    static CompoundTag write(MedicineState state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown_ticks", state.cooldownTicks());
        tag.putString("effect", state.effect());
        tag.putInt("effect_ticks", state.effectTicks());
        tag.putInt("healing_elapsed", state.healingElapsed());
        tag.putInt("quality", state.quality());
        return tag;
    }

    private static void store(ServerPlayer player, MedicineState state) {
        if (state.equals(MedicineState.EMPTY)) player.getPersistentData().remove(TAG);
        else player.getPersistentData().put(TAG, write(state));
    }

    private static int bound(int value, int maximum) { return Math.max(0, Math.min(maximum, value)); }

    /** Null means eligible. Checked at both start and completion; rejection never spends an item. */
    public static String rejection(ServerPlayer player, String id) {
        if (!player.isAlive() || player.isSpectator()) return MESSAGE + "ineligible";
        MedicineState current = state(player);
        if (current.cooldownTicks() > 0) return MESSAGE + "cooldown";
        if (current.effectTicks() > 0) return MESSAGE + "active";
        return switch (id) {
            case "huiling" -> {
                long cap = CultivationService.spiritualPowerCap(player);
                yield cap <= 0 ? MESSAGE + "ineligible"
                        : CultivationService.getProfile(player).currentSpiritualPower() >= cap ? MESSAGE + "full_power" : null;
            }
            case "yangyuan" -> player.getHealth() >= player.getMaxHealth() ? MESSAGE + "full_health" : null;
            case "ningshen" -> eligibleForStability(player) ? null : MESSAGE + "ineligible";
            default -> com.example.myvillage.wilderness.ExpeditionRules.get().isMedicine(id) ? null : MESSAGE + "ineligible";
        };
    }

    private static boolean eligibleForStability(ServerPlayer player) {
        var profile = CultivationService.getProfile(player);
        if (!player.gameMode.isSurvival() || profile.spiritualRoot().isEmpty()
                || !profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)) return false;
        var realm = player.registryAccess().registry(ModCultivationRegistries.REALMS)
                .map(registry -> registry.get(profile.realmId())).orElse(null);
        if (realm == null) return false;
        RealmStageDefinition stage = realm.stages().stream().filter(entry -> entry.id().equals(profile.stageId()))
                .findFirst().orElse(null);
        return stage != null && stage.cultivationCap().isPresent()
                && profile.cultivationProgress() >= stage.cultivationCap().orElseThrow()
                && profile.stability() < stage.stabilityCap().orElseThrow();
    }

    public static boolean consume(ServerPlayer player, String id) {
        return consume(player, id, 1);
    }

    public static boolean consume(ServerPlayer player, String id, int quality) {
        PillQuality.require(quality);
        String rejected = rejection(player, id);
        if (rejected != null) {
            player.displayClientMessage(Component.translatable(rejected), true);
            return false;
        }
        AlchemyRules rules = AlchemyRules.get();
        int duration = switch (id) {
            case "yangyuan" -> rules.effects().yangyuan().durationTicks();
            case "ningshen" -> rules.effects().ningshen().durationTicks();
            default -> com.example.myvillage.wilderness.ExpeditionRules.get().isMedicine(id) ? com.example.myvillage.wilderness.ExpeditionRules.get().medicine(id).durationTicks(quality) : 0;
        };
        if (id.equals("huiling")) {
            long amount = MedicineEffects.recoveredPower(CultivationService.spiritualPowerCap(player),
                    rules.effects().huiling().powerFraction() * rules.qualityMultiplier(quality));
            if (!CultivationService.recoverSpiritualPower(player, amount).success()) return false;
        }
        MedicineState applied = state(player).begin(id, rules.sharedCooldownTicks(), duration, quality);
        store(player, applied);
        com.example.myvillage.wilderness.ExpeditionCombat.refresh(player);
        syncCooldown(player, applied);
        return true;
    }

    public static void beginFoundationCooldown(ServerPlayer player) {
        MedicineState applied=new MedicineState(AlchemyRules.get().sharedCooldownTicks(),"",0,0);
        store(player,applied);syncCooldown(player,applied);
    }

    /** Called only by the normal/spirit meditation settlement planner, never study or advancement. */
    public static double stabilityMultiplier(ServerPlayer player) {
        MedicineState current = state(player);
        return current.effect().equals("ningshen") && current.effectTicks() > 0
                ? MedicineEffects.qualityStabilityMultiplier(AlchemyRules.get().effects().ningshen().stabilityMultiplier(),
                        AlchemyRules.get().qualityMultiplier(current.quality())) : 1.0;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        MedicineState current = state(player);
        if (current.equals(MedicineState.EMPTY)) return;
        var effect = AlchemyRules.get().effects().yangyuan();
        MedicineState.Tick tick = current.tick(effect.intervalTicks());
        if (tick.heal()) player.heal((float) PillAegisEffects.healingAmount(current.effect(),
                player.getMaxHealth() * effect.healFraction() * AlchemyRules.get().qualityMultiplier(current.quality()),
                FateService.effect(player, "pill_healing_bonus")));
        store(player, tick.next());
    }

    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        MedicineState current = state(player);
        if (current.effect().equals("yangyuan")) store(player, current.clearBenefit());
    }

    /** Vanilla cooldown is presentation; NBT remains the check at start and completion. */
    private static void syncCooldown(ServerPlayer player, MedicineState state) {
        int remaining = Math.max(state.cooldownTicks(), state.effectTicks());
        for (var recipe : AlchemyRules.get().recipes()) {
            var pill = BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output()));
            player.getCooldowns().removeCooldown(pill);
            if (remaining > 0) player.getCooldowns().addCooldown(pill, remaining);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncCooldown(player, state(player));
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MedicineState original = read(event.getOriginal().getPersistentData().getCompound(TAG), AlchemyRules.get());
        MedicineState replacement = event.isWasDeath() ? original.clearBenefit() : original;
        // Clone runs before the respawn packet. Sending here would update the discarded LocalPlayer.
        store(player, replacement);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncCooldown(player, state(player));
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncCooldown(player, state(player));
    }
}
