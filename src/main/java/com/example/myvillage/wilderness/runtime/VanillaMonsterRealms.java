package com.example.myvillage.wilderness.runtime;

import com.example.myvillage.stats.StatsRules;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.wilderness.VanillaMonsterRules;
import com.example.myvillage.wilderness.WildernessRules;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.example.myvillage.exploration.ExplorationRules;
import com.example.myvillage.exploration.ExplorationRuntime;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/** Birth intent is recorded before vanilla initialization and applied only after it completes. */
public final class VanillaMonsterRealms {
    public static final String BIRTH = "myvillage:vanilla_monster_birth";
    private static final String REALM = "myvillage:stats_realm";
    private VanillaMonsterRealms() {}

    public static void register() {
        for (String reason : VanillaMonsterRules.bundled().spawnReasons()) MobSpawnType.valueOf(reason);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VanillaMonsterRealms::finalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VanillaMonsterRealms::join);
        NeoForge.EVENT_BUS.addListener(VanillaMonsterRealms::convert);
        NeoForge.EVENT_BUS.addListener(VanillaMonsterRealms::split);
        // Before defensive reactions and vanilla armor. Melee already uses the scaled attribute.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, VanillaMonsterRealms::damage);
    }
    private static boolean eligible(LivingEntity entity) {
        return VanillaMonsterRules.bundled().includes(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getType().getCategory() == MobCategory.MONSTER);
    }
    private static void finalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (!eligible(mob) || !VanillaMonsterRules.bundled().spawnReasons().contains(event.getSpawnType().name())
                || mob.getPersistentData().contains(BIRTH) || mob.getPersistentData().contains(REALM)) return;
        ServerLevel level = event.getLevel().getLevel();
        String special = ExplorationRules.get().birth(event.getSpawnType().name()).orElse(null);
        if (event.getSpawner() != null && event.getSpawner().left().orElse(null) instanceof TrialSpawnerBlockEntity trial
                && trial.getTrialSpawner().isOminous())
            special = ExplorationRules.get().birth("OMINOUS_TRIAL").orElse(special);
        var selected = ExplorationRuntime.at(event.getLevel(), mob.blockPosition(), special);
        if (selected.isEmpty() || selected.get().encounters() == null) return;
        var context = selected.get(); String region = context.region();
        var realm = context.encounters().sample(level.getSeed(), region + "/" + context.location().id(), mob.getRandom().nextLong());
        CompoundTag birth = new CompoundTag();
        birth.putString("realm", realm.realmId()); birth.putInt("stage", realm.stage());
        birth.putString("region", region); birth.putString("profile", context.profile());
        birth.putString("dimension", level.dimension().location().toString());
        birth.putString("location", context.location().id());
        birth.putInt("x", mob.getBlockX()); birth.putInt("y", mob.getBlockY()); birth.putInt("z", mob.getBlockZ());
        birth.putString("reason", event.getSpawnType().name());
        birth.putBoolean("pending", true);
        mob.getPersistentData().put(BIRTH, birth);
    }
    private static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !eligible(mob)) return;
        CompoundTag birth = mob.getPersistentData().getCompound(BIRTH);
        if (!birth.getBoolean("pending")) return; // Saved modifiers already restore before Health; never heal on reload.
        if (!mob.getPersistentData().contains(REALM)) {
            StatsRuntime.applyVanillaRealm(mob, birth.getString("realm"), birth.getInt("stage"));
            mob.setHealth(mob.getMaxHealth());
        }
        birth.remove("pending");
    }
    private static void inherit(LivingEntity parent, LivingEntity child, boolean freshSplit) {
        if (parent.level().isClientSide() || !eligible(child)
                || !parent.getPersistentData().contains(BIRTH, Tag.TAG_COMPOUND)
                || child.getPersistentData().contains(REALM)) return;
        CompoundTag birth = parent.getPersistentData().getCompound(BIRTH).copy();
        if (birth.getBoolean("pending")) return;
        double fraction = freshSplit ? 1 : Math.min(1, child.getHealth() / child.getMaxHealth());
        StatsRuntime.applyVanillaRealm(child, birth.getString("realm"), birth.getInt("stage"));
        child.setHealth((float) (child.getMaxHealth() * fraction));
        child.getPersistentData().put(BIRTH, birth);
    }
    private static void convert(LivingConversionEvent.Post event) { inherit(event.getEntity(), event.getOutcome(), false); }
    private static void split(MobSplitEvent event) {
        for (Mob child : event.getChildren()) inherit(event.getParent(), child, true);
    }
    private static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof Mob attacker)
                || !eligible(attacker)) return;
        String type = event.getSource().typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
        if (!VanillaMonsterRules.bundled().nonAttributeDamageTypes().contains(type)) return;
        double factor = RealmArmorRuntime.defenderScale(attacker).factor();
        if (factor != 1) event.setAmount((float) Math.min(StatsRules.bundled().limits().maxDamage(), event.getAmount() * factor));
    }
}
