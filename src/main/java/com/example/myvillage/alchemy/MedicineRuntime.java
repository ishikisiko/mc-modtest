package com.example.myvillage.alchemy;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.cultivation.fate.PillAegisEffects;
import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.network.MedicineEffectsPayload;
import com.example.myvillage.wilderness.ExpeditionCombat;
import com.example.myvillage.wilderness.ExpeditionRules;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Logical-server medication lifecycle. Persistent remaining counters deliberately exclude offline time. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class MedicineRuntime {
    private static final String TAG = "yuanxian_medicine";
    private static final String MESSAGE = "message.yuanxian.alchemy.";
    private static final String PILL_PREFIX = "pill_";

    private MedicineRuntime() { }

    private static CompoundTag doses(ServerPlayer player) {
        return player.getPersistentData().getCompound(TAG);
    }

    public static MedicineState state(ServerPlayer player, String id) {
        return read(doses(player), AlchemyRules.get(), AlchemyRules.medicineFamily(id));
    }

    static MedicineState read(CompoundTag doses, AlchemyRules rules, String id) {
        if (!doses.contains(id, Tag.TAG_COMPOUND)) return MedicineState.EMPTY;
        CompoundTag tag = doses.getCompound(id);
        int cooldown = bound(tag.getInt("cooldown_ticks"), rules.cooldownTicks());
        int quality = tag.contains("quality", Tag.TAG_INT) ? tag.getInt("quality") : 1;
        if (quality < 1 || quality > 4) quality = 1;
        String pill = tag.getString("active_pill");
        if (!AlchemyRules.medicineFamily(pill).equals(id)) pill = id;
        int remaining = bound(tag.getInt("effect_ticks"), durationTicks(rules, pill, quality));
        if (remaining == 0) return new MedicineState(cooldown, "", 0, 0);
        int elapsed = id.equals("yangyuan")
                ? bound(tag.getInt("healing_elapsed"), rules.effects().healingInterval() - 1) : 0;
        return new MedicineState(cooldown, pill, remaining, elapsed, quality);
    }

    private static int durationTicks(AlchemyRules rules, String id, int quality) {
        return switch (AlchemyRules.medicineFamily(id)) {
            case "yangyuan" -> rules.effects().healingDuration();
            case "xuying" -> NascentRules.get().value("reformation_pill_ticks");
            default -> {
                var medicine = ExpeditionRules.get().medicine(id);
                yield medicine == null ? 0 : medicine.durationTicks(quality);
            }
        };
    }

    static void write(CompoundTag doses, String id, MedicineState state) {
        if (state.available()) {
            doses.remove(id);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("cooldown_ticks", state.cooldownTicks());
        tag.putInt("effect_ticks", state.effectTicks());
        tag.putInt("healing_elapsed", state.healingElapsed());
        tag.putInt("quality", state.quality());
        if (state.effectTicks() > 0 && !state.effect().equals(id)) tag.putString("active_pill", state.effect());
        doses.put(id, tag);
    }

    private static void store(ServerPlayer player, CompoundTag doses) {
        if (doses.isEmpty()) player.getPersistentData().remove(TAG);
        else player.getPersistentData().put(TAG, doses);
    }

    private static int bound(int value, int maximum) { return Math.max(0, Math.min(maximum, value)); }

    /** Null means eligible. Checked at both start and completion; rejection never spends an item. */
    public static String rejection(ServerPlayer player, String id) {
        if (!player.isAlive() || player.isSpectator()) return MESSAGE + "ineligible";
        MedicineState current = state(player, id);
        if (current.cooldownTicks() > 0) return MESSAGE + "cooldown";
        if (current.effectTicks() > 0) return MESSAGE + "active";
        return switch (AlchemyRules.medicineFamily(id)) {
            case "huiling" -> {
                long cap = CultivationService.spiritualPowerCap(player);
                yield cap <= 0 ? MESSAGE + "ineligible"
                        : CultivationService.getProfile(player).currentSpiritualPower() >= cap ? MESSAGE + "full_power" : null;
            }
            case "yangyuan", "ningshen" -> player.getHealth() >= player.getMaxHealth() ? MESSAGE + "full_health" : null;
            case "xuying" -> com.example.myvillage.cultivation.CultivationRecovery.canReform(
                    player.registryAccess(), CultivationService.getProfile(player)) ? null : MESSAGE + "ineligible";
            default -> ExpeditionRules.get().isMedicine(id) ? null : MESSAGE + "ineligible";
        };
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
        String family = AlchemyRules.medicineFamily(id);
        if (family.equals("huiling")) {
            long amount = (long) rules.effects().amount(id, quality);
            if (!CultivationService.recoverSpiritualPower(player, amount).success()) return false;
        } else if (family.equals("ningshen")) {
            player.heal((float) rules.effects().amount(id, quality));
        }
        applyDose(player, family, MedicineState.EMPTY.begin(id, rules.cooldownTicks(), durationTicks(rules, id, quality), quality));
        ExpeditionCombat.refresh(player);
        return true;
    }

    public static void beginCooldown(ServerPlayer player, String id) {
        applyDose(player, id, new MedicineState(AlchemyRules.get().cooldownTicks(), "", 0, 0));
    }

    private static void applyDose(ServerPlayer player, String id, MedicineState applied) {
        CompoundTag doses = doses(player);
        write(doses, id, applied);
        store(player, doses);
        syncCooldown(player, itemId(id), applied);
        if (AlchemyRules.medicineTier(id) == 1) {
            syncCooldown(player, itemId(id + "_2"), applied);
            syncCooldown(player, itemId(id + "_3"), applied);
        }
        if (applied.effectTicks() > 0) syncEffects(player, doses);
    }

    public static double reformationMultiplier(ServerPlayer player) {
        MedicineState current = state(player, "xuying");
        return current.effectTicks() > 0 ? NascentRules.get().reformationMultiplier(current.quality()) : 1.0;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        CompoundTag doses = doses(player);
        if (doses.isEmpty()) return;
        AlchemyRules rules = AlchemyRules.get();
        var effect = rules.effects();
        double healing = 0;
        boolean effectExpired = false;
        for (String id : List.copyOf(doses.getAllKeys())) {
            MedicineState current = read(doses, rules, id);
            MedicineState.Tick tick = current.tick(effect.healingInterval());
            write(doses, id, tick.next());
            effectExpired |= current.effectTicks() == 1;
            if (tick.heal()) {
                healing = PillAegisEffects.healingAmount(id,
                        effect.amount(current.effect(), current.quality()),
                        FateService.effect(player, "pill_healing_bonus"));
            }
        }
        store(player, doses);
        if (effectExpired) syncEffects(player, doses);
        if (healing > 0) player.heal((float) healing);
    }

    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        CompoundTag doses = doses(player);
        MedicineState current = read(doses, AlchemyRules.get(), "yangyuan");
        if (current.effectTicks() == 0) return;
        write(doses, "yangyuan", current.clearBenefit());
        store(player, doses);
        syncEffects(player, doses);
    }

    private static ResourceLocation itemId(String id) {
        return ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, PILL_PREFIX + id);
    }

    private static void syncEffects(ServerPlayer player, CompoundTag doses) {
        AlchemyRules rules = AlchemyRules.get();
        var icons = doses.getAllKeys().stream().sorted()
                .map(id -> read(doses, rules, id))
                .filter(state -> state.effectTicks() > 0)
                .map(state -> itemId(state.effect())).toList();
        PacketDistributor.sendToPlayer(player, new MedicineEffectsPayload(icons));
    }

    /** Vanilla cooldown is presentation; NBT remains the check at start and completion. */
    private static void syncCooldown(ServerPlayer player, ResourceLocation itemId, MedicineState state) {
        int remaining = Math.max(state.cooldownTicks(), state.effectTicks());
        var pill = BuiltInRegistries.ITEM.get(itemId);
        player.getCooldowns().removeCooldown(pill);
        if (remaining > 0) player.getCooldowns().addCooldown(pill, remaining);
    }

    private static void syncPresentation(ServerPlayer player) {
        AlchemyRules rules = AlchemyRules.get();
        CompoundTag doses = doses(player);
        rules.recipes().stream().map(AlchemyRules.Recipe::output).distinct().map(ResourceLocation::parse)
                .forEach(itemId -> syncCooldown(player, itemId,
                        read(doses, rules, AlchemyRules.medicineFamily(itemId.getPath().substring(PILL_PREFIX.length())))));
        syncEffects(player, doses);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncPresentation(player);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag replacement = event.getOriginal().getPersistentData().getCompound(TAG).copy();
        if (event.isWasDeath()) clearBenefits(replacement, AlchemyRules.get());
        // Clone runs before the respawn packet. Sending here would update the discarded LocalPlayer.
        store(player, replacement);
    }

    static void clearBenefits(CompoundTag doses, AlchemyRules rules) {
        for (String id : List.copyOf(doses.getAllKeys())) {
            write(doses, id, read(doses, rules, id).clearBenefit());
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncPresentation(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncPresentation(player);
    }
}
