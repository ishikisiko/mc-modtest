package com.example.myvillage.entity.npc;

import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.cultivation.time.CultivationServerConfig;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.sim.engine.NpcNascent;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** 有命簿的身体只提交赠礼。独立修士把同一护命状态存在实体自己的 NBT。 */
public final class NpcNascentRuntime {
    private static final String SHATTERED = "yuanxian_npc_nascent_shattered";
    private static final String REFORMS_AT = "yuanxian_npc_nascent_reforms_at";
    private static final String SEARCH_DAY = "yuanxian_npc_nascent_search_day";

    private NpcNascentRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NpcNascentRuntime::onDeath);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof NpcEntity npc && !npc.level().isClientSide()
                && protectIndependent(npc, event.getSource())) event.setCanceled(true);
    }

    private static boolean protectIndependent(NpcEntity npc, DamageSource source) {
        var state = npc.getPersistentData();
        if (npc.isLedgerAvatar() || !eligible(npc) || state.getBoolean(SHATTERED)) return false;
        state.putBoolean(SHATTERED, true);
        npc.setHealth(Math.max(1F, npc.getMaxHealth() * (1F - NpcNascent.injuryAfterShatter() / 100F)));
        npc.setRemainingFireTicks(0);
        npc.fallDistance = 0;
        NpcCombatRuntime.disengage(npc);
        particles(npc);
        if (source.getEntity() instanceof ServerPlayer player) tellShattered(player, npc);
        return true;
    }

    static void give(ServerPlayer player, NpcEntity npc, ItemStack stack) {
        if (!player.isAlive() || player.isSpectator() || !npc.isAlive()
                || player.level() != npc.level() || !player.canInteractWithEntity(npc, 0)
                || !player.hasLineOfSight(npc) || !stack.is(ModItems.FIVE_COLOR_GOLDEN_LOTUS.get())) return;
        if (npc.combatEngaged() || npc.getTarget() != null || NpcPhoenixRuntime.active(npc)) {
            player.displayClientMessage(Component.literal("请等对方脱战，再赠予五彩金莲。"), true);
            return;
        }
        boolean started;
        if (npc.isLedgerAvatar()) {
            started = WorldSimRuntime.sim().map(sim -> sim.giveReformationLotus(
                    npc.ledgerPersonId(), npc.combatEpoch, player.getGameProfile().getName())).orElse(false);
            if (started) WorldSimSavedData.get(player.serverLevel().getServer().overworld()).setDirty();
        } else {
            started = eligible(npc) && awaitingLotus(npc.getPersistentData());
            if (started) begin(npc);
        }
        if (!started) {
            player.displayClientMessage(Component.literal("这名修士现在无需五彩金莲。"), true);
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.sendSystemMessage(Component.literal(npc.getName().getString() + "收下了五彩金莲，开始重凝元婴。"));
        particles(npc);
    }

    static void tickIndependent(NpcEntity npc) {
        if (npc.isLedgerAvatar() || npc.combatEngaged() || npc.getTarget() != null || !npc.isAlive()) return;
        var state = npc.getPersistentData();
        if (state.contains(REFORMS_AT, Tag.TAG_LONG) && npc.level().getGameTime() >= state.getLong(REFORMS_AT)) {
            state.remove(REFORMS_AT);
            state.remove(SHATTERED);
            particles(npc);
        }
    }

    static void explored(NpcEntity npc) {
        if (npc.isLedgerAvatar() || npc.combatEngaged()) return;
        var state = npc.getPersistentData();
        if (!awaitingLotus(state)) return;
        long day = npc.level().getGameTime() / CultivationServerConfig.scale().ticksPerDay();
        if (state.contains(SEARCH_DAY, Tag.TAG_LONG) && state.getLong(SEARCH_DAY) == day) return;
        state.putLong(SEARCH_DAY, day);
        if (npc.getRandom().nextDouble() < NascentRules.get().npcLotusChance()) {
            begin(npc);
            particles(npc);
        }
    }

    private static boolean eligible(NpcEntity npc) {
        return StatsRuntime.assignedRealm(npc).map(key -> switch (key.realmId()) {
            case "yuanxian:nascent_soul", "yuanxian:spirit_transformation" -> true;
            default -> false;
        }).orElse(false);
    }

    private static boolean awaitingLotus(CompoundTag state) {
        return state.getBoolean(SHATTERED) && !state.contains(REFORMS_AT, Tag.TAG_LONG);
    }

    private static void begin(NpcEntity npc) {
        long ticks = (long) NascentRules.get().value("npc_reformation_days") * CultivationServerConfig.scale().ticksPerDay();
        npc.getPersistentData().putLong(REFORMS_AT, npc.level().getGameTime() + ticks);
    }

    static void tellShattered(ServerPlayer player, NpcEntity npc) {
        player.sendSystemMessage(Component.literal(npc.getName().getString()
                + "碎婴护住了性命，可以赠予五彩金莲助其重凝。"));
    }

    private static void particles(NpcEntity npc) {
        if (npc.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, npc.getX(), npc.getY(0.65), npc.getZ(),
                    24, .4, .65, .4, .1);
    }
}
