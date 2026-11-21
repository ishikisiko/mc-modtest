package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.exploration.ExplorationRules;
import com.example.myvillage.ruins.BattleCacheBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.WeakHashMap;

/** 以玩家当前已加载的视野和箱子状态为准，提示只能由本人收到。 */
public final class FateSenses {
    private record Hint(BlockPos position, long until) { }
    private static final String[] DIRECTIONS = {"前方", "右前方", "右侧", "右后方", "后方", "左后方", "左侧", "左前方"};
    private static final WeakHashMap<ServerPlayer,List<Hint>> HINTS = new WeakHashMap<>();
    private FateSenses() { }

    static void tick(ServerPlayer player) {
        if (!FateActors.valid(player)) return;
        long now = player.serverLevel().getGameTime();
        if (Math.floorMod(now + player.getId(), 20) == 0) {
            if (FateService.has(player,"yuanxian:keen_senses")) hear(player);
            if (FateService.has(player,"yuanxian:treasure_sense")) seek(player,now);
        }
        var hints = HINTS.get(player);
        if (hints == null || now % 4 != 0) return;
        double range = FateService.effect(player,"treasure_range");
        hints.removeIf(h -> now >= h.until || player.distanceToSqr(h.position.getCenter()) > range*range
                || !player.serverLevel().hasChunkAt(h.position) || !reward(player.serverLevel().getBlockEntity(h.position)));
        for (Hint hint : hints) {
            Vec3 pos = Vec3.atCenterOf(hint.position).add(0,.58,0);
            particles(player, visible(player,pos) ? pos : direction(player,pos),true);
        }
        if (hints.isEmpty()) HINTS.remove(player);
    }

    static void forget(ServerPlayer player) { HINTS.remove(player); }
    static void clear() { HINTS.clear(); }

    private static void hear(ServerPlayer player) {
        double range = FateService.effect(player,"hearing_range");
        var enemy = player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(range),mob ->
                mob.isAlive() && (mob instanceof Enemy || mob.getTarget() == player)
                && mob.distanceToSqr(player) <= range*range && CombatActors.mayHarm(mob,player)
                && (mob.isInvisibleTo(player) || !player.hasLineOfSight(mob))).stream()
                .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player))).orElse(null);
        if (enemy == null) return;
        Vec3 hint = direction(player, enemy.getEyePosition());
        particles(player,hint,false);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.26F,1.25F);
        var difference = enemy.position().subtract(player.position());
        float angle = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-difference.x, difference.z)) - player.getYRot());
        int sector = Math.floorMod(Math.round(angle / 45), DIRECTIONS.length);
        if (!CompanionSwordRuntime.armed(player))
            player.displayClientMessage(Component.translatable("fate.yuanxian.hearing_hint", DIRECTIONS[sector]), true);
    }

    private static void seek(ServerPlayer player,long now) {
        double range = FateService.effect(player,"treasure_range");
        double chance = FateService.effect(player,"treasure_chance");
        long duration = (long) FateService.effect(player,"treasure_ticks");
        var level = player.serverLevel();
        var state = FateExtras.of(player);
        var checked = new HashSet<String>();
        int minX = net.minecraft.util.Mth.floor(player.getX()-range)>>4;
        int maxX = net.minecraft.util.Mth.floor(player.getX()+range)>>4;
        int minZ = net.minecraft.util.Mth.floor(player.getZ()-range)>>4;
        int maxZ = net.minecraft.util.Mth.floor(player.getZ()+range)>>4;
        for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++) {
            var chunk = level.getChunkSource().getChunkNow(x,z);
            if (chunk == null) continue;
            for (var blockEntity : chunk.getBlockEntities().values()) {
                var pos = blockEntity.getBlockPos();
                if (player.distanceToSqr(pos.getCenter()) > range*range || !reward(blockEntity)) continue;
                String key = level.dimension().location()+"/"+pos.asLong();
                if (state.examinedCaches().contains(key) || !checked.add(key)) continue;
                if (player.getRandom().nextDouble() < chance)
                    HINTS.computeIfAbsent(player, p -> new ArrayList<>()).add(new Hint(pos.immutable(),now+duration));
            }
        }
        if (!checked.isEmpty()) CultivationService.replaceFateExtras(player,state.examined(checked));
    }

    private static boolean reward(net.minecraft.world.level.block.entity.BlockEntity entity) {
        if (entity instanceof BattleCacheBlockEntity cache) return cache.hasTreasureHint();
        return entity instanceof RandomizableContainerBlockEntity box && box.getLootTable() != null
                && ExplorationRules.get().lootTables().containsKey(box.getLootTable().location().toString());
    }

    private static boolean visible(ServerPlayer player, Vec3 position) {
        return player.serverLevel().clip(new ClipContext(player.getEyePosition(),position,ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,player)).getType() == HitResult.Type.MISS;
    }

    private static Vec3 direction(ServerPlayer player,Vec3 position) {
        return player.getEyePosition().add(position.subtract(player.getEyePosition()).normalize().scale(1.35));
    }

    private static void particles(ServerPlayer player,Vec3 at,boolean treasure) {
        player.serverLevel().sendParticles(player,treasure ? ParticleTypes.WAX_ON : ParticleTypes.ENCHANT,
                false,at.x,at.y,at.z,5,.13,.10,.13,.012);
    }
}
