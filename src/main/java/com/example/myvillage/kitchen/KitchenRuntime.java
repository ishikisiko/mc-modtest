package com.example.myvillage.kitchen;

import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class KitchenRuntime {
    private static final ResourceLocation GROUND_SPEED = KitchenRules.id("kitchen/ground_speed");
    private static final ResourceLocation FASTING_HEAL = KitchenRules.id("kitchen/fasting_recovery");
    private static final ResourceLocation QI_REFINING = KitchenRules.id("qi_refining");
    private static final TagKey<Block> MINING = TagKey.create(Registries.BLOCK, KitchenRules.id("kitchen_mining"));
    private static final Map<ServerPlayer, Long> LAST_COMBAT = new WeakHashMap<>();
    private static final Map<String, KitchenRules.Meal> MEALS = KitchenRules.get().meals.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(KitchenRules.Meal::id, meal -> meal));

    private KitchenRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(KitchenRuntime::tick);
        NeoForge.EVENT_BUS.addListener(KitchenRuntime::attacked);
        NeoForge.EVENT_BUS.addListener(KitchenRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(KitchenRuntime::melee);
        NeoForge.EVENT_BUS.addListener(KitchenRuntime::mining);
        NeoForge.EVENT_BUS.addListener(KitchenRuntime::breathing);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) LAST_COMBAT.remove(player);
        });
    }

    public static void rememberMeal(ServerPlayer player) {
        for (var meal : MEALS.values()) {
            var effect = KitchenContent.effect(meal.id());
            if (player.hasEffect(effect)) player.removeEffect(effect);
        }
    }

    public static int percent(LivingEntity entity, String id) {
        return entity.hasEffect(KitchenContent.effect(id)) ? MEALS.get(id).percent() : 0;
    }

    public static boolean fasting(RegistryAccess access, CultivationProfile profile) {
        if (!profile.awakened()) return false;
        var realms = access.registryOrThrow(ModCultivationRegistries.REALMS);
        var minimum = realms.get(ModCultivationRegistries.FOUNDATION_ESTABLISHMENT_REALM_ID);
        var current = realms.get(profile.realmId());
        return minimum != null && current != null && current.hasStage(profile.stageId())
                && current.sortOrder() >= minimum.sortOrder();
    }

    public static boolean fasting(ServerPlayer player) {
        return fasting(player.registryAccess(), CultivationService.getProfile(player));
    }

    public static void vanillaHeal(Player player, float amount) {
        if (!(player instanceof ServerPlayer server) || fasting(server)) return;
        if (outOfCombat(server)) {
            if (CultivationService.getProfile(server).realmId().equals(QI_REFINING))
                amount *= server.getMaxHealth() / KitchenRules.get().referenceHealth;
            amount *= 1 + percent(server, "kitchen_dew_lamb") / 100.0F;
        }
        server.heal(amount);
    }

    private static long now(ServerPlayer player) { return player.serverLevel().getGameTime(); }

    private static boolean outOfCombat(ServerPlayer player) {
        return now(player) - LAST_COMBAT.computeIfAbsent(player, KitchenRuntime::now)
                >= KitchenRules.get().combatDelay;
    }

    private static void attacked(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LAST_COMBAT.put(player, now(player));
    }

    private static void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) LAST_COMBAT.put(player, now(player));
        if (event.getSource().getEntity() instanceof ServerPlayer player) LAST_COMBAT.put(player, now(player));
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((!player.isAlive() || player.isSpectator())) {
            groundSpeed(player, 0);
            return;
        }
        boolean safe = outOfCombat(player);
        boolean walking = player.onGround() && !player.isPassenger() && !player.isShiftKeyDown()
                && !player.isSwimming() && !player.isFallFlying() && !player.getAbilities().flying;
        groundSpeed(player, safe && walking ? percent(player, "kitchen_beef_noodles") : 0);
        var rules = KitchenRules.get();
        if (!safe || !player.isHurt() || player.tickCount % rules.recoveryInterval != 0
                || !player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION) || !fasting(player)) return;
        float amount = player.getMaxHealth() * rules.recoveryFraction;
        amount *= 1 + percent(player, "kitchen_dew_lamb") / 100.0F;
        HealingRuntime.heal(player, amount, FASTING_HEAL);
    }

    private static void groundSpeed(ServerPlayer player, int percent) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (percent == 0) {
            if (speed.hasModifier(GROUND_SPEED)) speed.removeModifier(GROUND_SPEED);
        } else if (!speed.hasModifier(GROUND_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(GROUND_SPEED, percent / 100.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void mining(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        int boost = percent(player, "kitchen_beef_stew");
        if (boost > 0 && event.getState().is(MINING) && player.getMainHandItem().is(ItemTags.PICKAXES))
            event.setNewSpeed(event.getNewSpeed() * (1 + boost / 100.0F));
    }

    private static void melee(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var source = event.getSource();
        if (source.getEntity() != source.getDirectEntity() || !(source.getEntity() instanceof LivingEntity)
                || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) return;
        int reduction = percent(player, "kitchen_honey_pork");
        if (reduction > 0) event.setNewDamage(event.getNewDamage() * (1 - reduction / 100.0F));
    }

    private static void breathing(LivingBreatheEvent event) {
        if (event.canBreathe() || !(event.getEntity() instanceof ServerPlayer player)) return;
        int percent = percent(player, "kitchen_salmon_soup");
        if (percent > 0 && Math.floorMod(Math.floorMod(player.tickCount, 100) * percent, 100) < percent)
            event.setConsumeAirAmount(0);
    }

    public static boolean preserveBoneMeal(Player player, BlockState state) {
        if (!(player instanceof ServerPlayer server) || server instanceof FakePlayer) return false;
        if (!state.is(Blocks.WHEAT) && !state.is(Blocks.CARROTS) && !state.is(Blocks.POTATOES) && !state.is(Blocks.BEETROOTS)) return false;
        int probability = percent(server, "kitchen_carrot_eggs");
        return probability > 0 && server.getRandom().nextInt(100) < probability;
    }
}
