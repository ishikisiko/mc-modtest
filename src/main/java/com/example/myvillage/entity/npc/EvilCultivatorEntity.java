package com.example.myvillage.entity.npc;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.drops.DropRules;
import com.example.myvillage.drops.DropRuntime;
import com.example.myvillage.drops.SpiritStoneAmounts;
import com.example.myvillage.exploration.ExplorationRuntime;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.WildernessRosterRuntime;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class EvilCultivatorEntity extends NpcEntity implements Enemy {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "evil_cultivator");
    private static final String BIRTH = "yuanxian:evil_cultivator_birth";

    public EvilCultivatorEntity(EntityType<? extends EvilCultivatorEntity> type, Level level) {
        super(type, level);
        xpReward = EvilCultivatorRules.get().attributes().experience();
    }

    public static AttributeSupplier.Builder createAttributes() {
        var stats = EvilCultivatorRules.get().attributes();
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, stats.health())
                .add(Attributes.ATTACK_DAMAGE, stats.attack()).add(Attributes.ATTACK_KNOCKBACK, 0)
                .add(Attributes.ARMOR, stats.armor()).add(Attributes.MOVEMENT_SPEED, stats.speed())
                .add(Attributes.FOLLOW_RANGE, stats.followRange());
    }

    @Override protected double strollSpeedModifier() { return EvilCultivatorRules.get().attributes().strollSpeed(); }

    @Override protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void setTarget(LivingEntity target) {
        if (target != null && target != getTarget()) {
            combatAnchor = position();
            lastThreatTick = level().getGameTime();
            nextCombatDecision = lastThreatTick + NpcCombatRules.get().reactionTicks();
        }
        super.setTarget(target);
    }

    @Override protected boolean shouldDespawnInPeaceful() { return true; }
    @Override public boolean removeWhenFarAway(double distance) {
        return getPersistentData().getCompound(BIRTH).getBoolean("natural");
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PLAYER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PLAYER_DEATH; }

    private static boolean natural(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    public static boolean checkSpawn(EntityType<EvilCultivatorEntity> type, ServerLevelAccessor world,
            MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!natural(reason)) return Mob.checkMobSpawnRules(type, world, reason, pos, random);
        return WildernessRosterRuntime.checkSpawn(type, world, reason, pos, random) && wildSurface(world, pos);
    }

    /** Use existing plan reservations and loaded village data; this predicate never creates chunks. */
    private static boolean wildSurface(ServerLevelAccessor world, BlockPos pos) {
        ServerLevel level = world.getLevel();
        if (!world.canSeeSky(pos) || level.isVillage(pos)) return false;
        var planned = WorldPlanRuntime.generator(level);
        if (planned.isPresent()) {
            var plan = planned.get().plan();
            int x = pos.getX(), z = pos.getZ();
            if (plan.intersectsCity(x, z, x, z) || plan.settlementChunk(x >> 4, z >> 4).isPresent()) return false;
        }
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return ExplorationRuntime.structuresAt(world, pos).stream().map(ResourceLocation::parse)
                .noneMatch(id -> structures.getHolder(id).filter(holder -> holder.is(StructureTags.VILLAGE)).isPresent());
    }

    @SubscribeEvent
    public static void finalizeNaturalBirth(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof EvilCultivatorEntity npc) || !natural(event.getSpawnType())
                || npc.getPersistentData().contains(BIRTH)) return;
        var world = event.getLevel();
        var level = world.getLevel();
        var pos = npc.blockPosition();
        var region = WildernessRuntime.regionAt(level, pos);
        if (region.isEmpty() || !wildSurface(world, pos)) {
            event.setSpawnCancelled(true);
            return;
        }
        npc.rememberBirth(level, region.get(), true);
    }

    private void rememberBirth(ServerLevel level, String region, boolean naturallySpawned) {
        RealmKey realm = WildernessRules.bundled().region(region)
                .map(r -> r.rogue().sample(level.getSeed(), region, getRandom().nextLong()))
                .orElseGet(() -> EvilCultivatorRules.get().fallback());
        var data = new CompoundTag();
        data.putString("region", region);
        data.putString("realm", realm.realmId());
        data.putInt("stage", realm.stage());
        data.putLong("pos", blockPosition().asLong());
        data.putBoolean("natural", naturallySpawned);
        getPersistentData().put(BIRTH, data);
    }

    /** Called by the shared join lifecycle after equipment exists, before generic cultivation preparation. */
    void initializeEncounter() {
        if (!(level() instanceof ServerLevel server) || isLedgerAvatar()) return;
        if (!getPersistentData().contains(BIRTH)) {
            rememberBirth(server, WildernessRuntime.regionAt(server, blockPosition()).orElse(""), false);
        }
        if (StatsRuntime.assignedRealm(this).isEmpty()) {
            var birth = getPersistentData().getCompound(BIRTH);
            StatsRuntime.applyRealm(this, birth.getString("realm"), birth.getInt("stage"));
        }
        if (NpcSkillRuntime.initializeWild(this)) {
            spiritualPower = NpcCultivationRuntime.cap(this);
            setHealth(getMaxHealth());
        }
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentPlayerHit) {
        super.dropCustomDeathLoot(level, source, recentPlayerHit);
        var stones = EvilCultivatorRules.get().tier(prodigy()).spiritStones();
        Integer realmRank = StatsRuntime.assignedRealm(this).map(DropRuntime::realmRank).orElse(null);
        var bounds = SpiritStoneAmounts.forRealm(stones.getFirst(), stones.getLast(), DropRules.realmLevel(realmRank));
        int count = getRandom().nextIntBetweenInclusive(bounds.min(), bounds.max());
        DropRuntime.splitStacks(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get()), count, this::spawnAtLocation);
        var manual = EvilCultivatorTechniques.manual(this, source);
        if (!manual.isEmpty()) spawnAtLocation(manual);
    }
}
