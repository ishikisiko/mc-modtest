package com.example.myvillage.exploration;

import com.example.myvillage.exploration.boss.BossRules;
import com.example.myvillage.exploration.boss.BossRuntime;
import com.example.myvillage.wilderness.runtime.VanillaMonsterRealms;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.*;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.dimension.end.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native fixture calls and actual NBT save/restart; never player-input or gameplay acceptance. */
@EventBusSubscriber(modid = "myvillage")
public final class ExplorationV2Evidence {
  private static final boolean ENABLED =
      "1".equals(System.getenv("MYVILLAGE_EXPLORATION_V2_EVIDENCE"));
  private static final Path OUT = Path.of("../reports/exploration-v2-native");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final List<Map<String, Object>> CHECKS = new ArrayList<>(),
      DAMAGE = new ArrayList<>();
  private static final List<String> COMPLETED = new ArrayList<>();
  private static final List<String> INITIAL =
      List.of(
          "end_birth_compatibility",
          "boss_profiles",
          "dragon_native",
          "wither_native",
          "warden_native",
          "elder_native",
          "portal_policy",
          "end_city_loot",
          "save_fixture");
  private static final List<String> RESTART =
      List.of("boss_actual_restart", "end_actual_restart", "loot_actual_restart");
  private static boolean finished, started;
  private static int ticks;
  private static final boolean REBOOT = Files.exists(OUT.resolve("restart-fixture.json"));
  private static final List<Mob> SAVED = new ArrayList<>();
  private static final BlockPos CHEST = new BlockPos(0, 190, 80);

  private static void check(String name, boolean passed, Object detail) {
    CHECKS.add(Map.of("name", name, "passed", passed, "detail", String.valueOf(detail)));
    if (!passed) throw new AssertionError(name + ": " + detail);
  }

  private static void near(String name, double actual, double expected) {
    check(name, Math.abs(actual - expected) < .01, actual + " expected " + expected);
  }

  private static void done(String name) {
    COMPLETED.add(name);
  }

  private static Object call(
      Object object, Class<?> owner, String method, Class<?>[] args, Object... values)
      throws Exception {
    Method m = owner.getDeclaredMethod(method, args);
    m.setAccessible(true);
    return m.invoke(object, values);
  }

  private static Object field(Object object, String name) throws Exception {
    Field f = object.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(object);
  }

  private static void field(Object object, String name, Object value) throws Exception {
    Field f = object.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(object, value);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingIncomingDamageEvent e) {
    if (ENABLED && !finished) {
      DAMAGE.add(
          Map.of(
              "type",
              e.getSource()
                  .typeHolder()
                  .unwrapKey()
                  .map(k -> k.location().toString())
                  .orElse("unknown"),
              "amount",
              e.getAmount(),
              "victim",
              e.getEntity().getUUID().toString(),
              "owner",
              String.valueOf(e.getSource().getEntity()),
              "direct",
              String.valueOf(e.getSource().getDirectEntity())));
    }
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!ENABLED || finished || ++ticks < 40) return;
    var server = event.getServer();
    try {
      Files.createDirectories(OUT);
      server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
      if (REBOOT) {
        if (!restart(server, ticks >= 300)) return;
        finish(server, null);
      } else {
        if (!started) {
          initial(server);
          started = true;
        }
        if (ticks >= 100) finish(server, null);
      }
    } catch (Throwable error) {
      finish(server, error);
    }
  }

  private static void finish(MinecraftServer server, Throwable error) {
    finished = true;
    try {
      var selected = REBOOT ? RESTART : INITIAL;
      var unrun = new ArrayList<>(selected);
      unrun.removeAll(COMPLETED);
      var report = new LinkedHashMap<String, Object>();
      report.put("phase", REBOOT ? "restart" : "initial");
      report.put("passed", error == null && unrun.isEmpty());
      report.put("selected", selected);
      report.put("completed", COMPLETED);
      report.put("unrun", unrun);
      report.put("checks", CHECKS);
      report.put("damage_events", DAMAGE);
      report.put(
          "method",
          "native server fixtures; reflection advances native AI/respawn methods; fake players are"
              + " policy/recipient fixtures, never mapped input");
      report.put("owner_balance", "not_verified");
      report.put("owner_feel", "not_verified");
      if (error != null) {
        error.printStackTrace();
        report.put("error", error.toString());
      }
      Files.writeString(OUT.resolve(REBOOT ? "restart.json" : "initial.json"), GSON.toJson(report));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    server.halt(false);
  }

  private static <T extends Mob> T spawn(
      ServerLevel level, EntityType<T> type, BlockPos pos, MobSpawnType reason) {
    level.getChunk(pos);
    T mob = type.spawn(level, pos, reason);
    if (mob == null) throw new AssertionError("spawn " + type);
    freeze(mob);
    return mob;
  }

  private static void freeze(Mob mob) {
    mob.setNoAi(true);
    mob.setNoGravity(true);
    mob.setPersistenceRequired();
  }

  private static Mob victim(ServerLevel level, BlockPos pos) {
    Mob cow = spawn(level, EntityType.COW, pos, MobSpawnType.COMMAND);
    cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
    cow.setHealth(1024);
    return cow;
  }

  private static BossRules.Profile profile(Mob mob) {
    var p = BossRuntime.profile(mob).orElseThrow();
    near("profile_health_" + p.id(), mob.getMaxHealth(), p.health());
    if (p.attributeAttack() != null)
      near(
          "profile_attack_" + p.id(),
          mob.getAttributeValue(Attributes.ATTACK_DAMAGE),
          p.attributeAttack());
    check(
        "separate_ownership_" + p.id(),
        !mob.getPersistentData().contains(VanillaMonsterRealms.BIRTH),
        mob.getPersistentData());
    check(
        "profile_realm_" + p.id(),
        mob.getPersistentData().getCompound(BossRuntime.BIRTH).getInt("stage") == p.realm().stage(),
        mob.getPersistentData());
    return p;
  }

  private static void amount(Mob victim, int from, String type, float expected) {
    var rows =
        DAMAGE.subList(from, DAMAGE.size()).stream()
            .filter(
                r ->
                    r.get("victim").equals(victim.getUUID().toString())
                        && r.get("type").equals("minecraft:" + type))
            .toList();
    check("native_source_" + type, rows.size() == 1, rows);
    near(
        "native_amount_once_" + type,
        ((Number) rows.getFirst().get("amount")).doubleValue(),
        expected);
  }

  private static ServerPlayer player(ServerLevel level, String name) {
    return FakePlayerFactory.get(
        level,
        new GameProfile(
            UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
  }

  private static void initial(MinecraftServer server) throws Exception {
    var end = server.getLevel(Level.END);
    var overworld = server.overworld();
    BlockPos p = new BlockPos(80, 190, 80);
    end.getChunk(p);
    overworld.getChunk(p);
    var ordinary = spawn(end, EntityType.ENDERMAN, p, MobSpawnType.NATURAL);
    check(
        "end_new_ordinary_assigned",
        ordinary.getPersistentData().contains(VanillaMonsterRealms.BIRTH),
        ordinary.getPersistentData());
    SAVED.add(ordinary);
    var legacy = EntityType.ENDERMAN.create(end);
    legacy.moveTo(84, 190, 80, 0, 0);
    legacy.setHealth(legacy.getMaxHealth() / 2);
    var nbt = new CompoundTag();
    legacy.saveWithoutId(nbt);
    legacy.discard();
    var loaded = EntityType.ENDERMAN.create(end);
    loaded.load(nbt);
    end.addFreshEntity(loaded);
    freeze(loaded);
    check(
        "end_legacy_unassigned",
        !loaded.getPersistentData().contains(VanillaMonsterRealms.BIRTH),
        loaded.getPersistentData());
    near("end_legacy_no_heal", loaded.getHealth(), 20);
    loaded.discard();
    done("end_birth_compatibility");
    var elder = spawn(overworld, EntityType.ELDER_GUARDIAN, p, MobSpawnType.NATURAL);
    profile(elder);
    var warden = spawn(overworld, EntityType.WARDEN, p.offset(40, 0, 0), MobSpawnType.TRIGGERED);
    profile(warden);
    var wither = EntityType.WITHER.create(overworld);
    wither.moveTo(180, 190, 80, 0, 0);
    wither.makeInvulnerable();
    float fraction = wither.getHealth() / wither.getMaxHealth();
    overworld.addFreshEntity(wither);
    freeze(wither);
    profile(wither);
    near("wither_summon_fraction_retained", wither.getHealth() / wither.getMaxHealth(), fraction);
    var dragon = EntityType.ENDER_DRAGON.create(end);
    dragon.moveTo(80, 210, 80, 0, 0);
    end.addFreshEntity(dragon);
    freeze(dragon);
    profile(dragon);
    check(
        "four_independent_profiles",
        new HashSet<>(
                    List.of(
                        BossRuntime.profile(elder).orElseThrow().id(),
                        BossRuntime.profile(warden).orElseThrow().id(),
                        BossRuntime.profile(wither).orElseThrow().id(),
                        BossRuntime.profile(dragon).orElseThrow().id()))
                .size()
            == 4,
        BossRules.get().profiles());
    // Old native NBT receives the fixed profile while retaining its saved wounded fraction.
    var old = EntityType.ELDER_GUARDIAN.create(overworld);
    old.setHealth(old.getMaxHealth() * .25f);
    var oldSave = new CompoundTag();
    old.saveWithoutId(oldSave);
    var adopted = EntityType.ELDER_GUARDIAN.create(overworld);
    adopted.load(oldSave);
    adopted.moveTo(88, 190, 88, 0, 0);
    overworld.addFreshEntity(adopted);
    near("boss_legacy_nbt_adoption_fraction", adopted.getHealth() / adopted.getMaxHealth(), .25);
    check(
        "boss_legacy_nbt_adoption_profile",
        BossRuntime.profile(adopted).isPresent(),
        adopted.getPersistentData());
    adopted.discard();
    var foreign = EntityType.ELDER_GUARDIAN.create(overworld);
    foreign.getPersistentData().put(BossRuntime.REALM, new CompoundTag());
    foreign.moveTo(88, 190, 88, 0, 0);
    overworld.addFreshEntity(foreign);
    check(
        "boss_foreign_ownership_not_replaced",
        BossRuntime.profile(foreign).isEmpty()
            && !foreign.getPersistentData().contains(BossRuntime.BIRTH),
        foreign.getPersistentData());
    foreign.discard();
    done("boss_profiles");
    dragon(server, end, dragon);
    done("dragon_native");
    wither(overworld, wither);
    done("wither_native");
    warden(overworld, warden);
    done("warden_native");
    elder(overworld, elder);
    done("elder_native");
    portal(server);
    done("portal_policy");
    loot(end, server);
    done("end_city_loot");
    SAVED.addAll(List.of(elder, warden, wither, dragon));
    var saved = new JsonObject();
    var entities = new JsonArray();
    for (Mob mob : SAVED) {
      mob.setHealth(mob.getMaxHealth() * .4f);
      freeze(mob);
      if (mob instanceof WitherBoss wb) wb.setInvulnerableTicks(0);
      var level = (ServerLevel) mob.level();
      level.setChunkForced(mob.getBlockX() >> 4, mob.getBlockZ() >> 4, true);
      var row = new JsonObject();
      row.addProperty("dimension", level.dimension().location().toString());
      row.addProperty("uuid", mob.getUUID().toString());
      row.addProperty("x", mob.getBlockX());
      row.addProperty("z", mob.getBlockZ());
      row.addProperty("health", mob.getHealth());
      row.addProperty("max", mob.getMaxHealth());
      row.addProperty(
          "birth",
          mob.getPersistentData()
              .getCompound(mob == ordinary ? VanillaMonsterRealms.BIRTH : BossRuntime.BIRTH)
              .toString());
      entities.add(row);
    }
    saved.add("entities", entities);
    saved.addProperty(
        "chest",
        ((ChestBlockEntity) end.getBlockEntity(CHEST))
            .saveWithoutMetadata(end.registryAccess())
            .toString());
    Files.writeString(OUT.resolve("restart-fixture.json"), GSON.toJson(saved));
    done("save_fixture");
  }

  private static void dragon(MinecraftServer server, ServerLevel level, EnderDragon dragon)
      throws Exception {
    var target = victim(level, new BlockPos(90, 210, 80));
    var fake = player(level, "V2DragonAttacker");
    dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
    float before = dragon.getHealth();
    dragon.head.hurt(level.damageSources().mobAttack(target), 20);
    near("dragon_nonplayer_native_immunity", dragon.getHealth(), before);
    dragon.invulnerableTime = 0;
    dragon.head.hurt(level.damageSources().playerAttack(fake), 20);
    check("dragon_head_player_damage", dragon.getHealth() < before, dragon.getHealth());
    float head = before - dragon.getHealth();
    dragon.invulnerableTime = 0;
    before = dragon.getHealth();
    dragon.hurt(level.damageSources().playerAttack(fake), 20);
    check(
        "dragon_body_native_reduction",
        before - dragon.getHealth() > 0 && before - dragon.getHealth() < head,
        before - dragon.getHealth());
    var birth = dragon.getPersistentData().getCompound(BossRuntime.BIRTH).copy();
    float max = dragon.getMaxHealth();
    var other = player(level, "V2SecondAttacker");
    dragon.invulnerableTime = 0;
    dragon.head.hurt(level.damageSources().playerAttack(other), 1);
    near("two_fake_attacker_intents_no_rescale", dragon.getMaxHealth(), max);
    check(
        "two_fake_attacker_intents_same_birth",
        birth.equals(dragon.getPersistentData().getCompound(BossRuntime.BIRTH)),
        birth);
    dragon.setHealth(dragon.getMaxHealth() / 2);
    var crystal = EntityType.END_CRYSTAL.create(level);
    crystal.moveTo(dragon.getX() + 2, dragon.getY(), dragon.getZ(), 0, 0);
    level.addFreshEntity(crystal);
    dragon.nearestCrystal = crystal;
    dragon.tickCount = 10;
    before = dragon.getHealth();
    call(dragon, EnderDragon.class, "checkCrystals", new Class<?>[] {});
    near("dragon_crystal_native_heals", dragon.getHealth(), before + 1);
    crystal.discard();
    target.discard();
    target = victim(level, new BlockPos(92, 210, 80));
    int from = DAMAGE.size();
    call(dragon, EnderDragon.class, "hurt", new Class<?>[] {List.class}, List.of(target));
    amount(
        target,
        from,
        "mob_attack",
        BossRuntime.profile(dragon)
            .orElseThrow()
            .damage()
            .get(BossRules.Channel.DRAGON_CONTACT)
            .apply(10));
    target.discard();
    for (int amplifier : new int[] {0, 1}) {
      target = victim(level, new BlockPos(100, 210, 80));
      var cloud = new AreaEffectCloud(level, target.getX(), target.getY(), target.getZ());
      cloud.setOwner(dragon);
      cloud.setWaitTime(0);
      cloud.setDuration(40);
      cloud.setRadius(3);
      cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, amplifier));
      level.addFreshEntity(cloud);
      cloud.tickCount = 4;
      from = DAMAGE.size();
      cloud.tick();
      amount(
          target,
          from,
          "indirect_magic",
          BossRuntime.profile(dragon)
              .orElseThrow()
              .damage()
              .get(BossRules.Channel.DRAGON_BREATH)
              .apply(amplifier == 0 ? 3 : 6));
      cloud.discard();
      target.discard();
    }
    // Create and resurrect using the actual fight controller and the full native crystal animation.
    var fight = level.getDragonFight();
    check("native_end_fight_present", fight != null, fight);
    var arena =
        (EnderDragon) call(fight, EndDragonFight.class, "createNewDragon", new Class<?>[] {});
    check(
        "fight_created_dragon_assigned",
        BossRuntime.profile(arena).isPresent(),
        arena.getPersistentData());
    UUID old = arena.getUUID();
    fight.setDragonKilled(arena);
    arena.discard();
    BlockPos portal = (BlockPos) field(fight, "portalLocation");
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      var c = EntityType.END_CRYSTAL.create(level);
      var at = Vec3.atBottomCenterOf(portal.above().relative(direction, 2));
      c.moveTo(at.x, at.y, at.z, 0, 0);
      level.addFreshEntity(c);
    }
    fight.tryRespawn();
    check(
        "four_crystals_start_native_respawn",
        field(fight, "respawnStage") != null,
        field(fight, "respawnStage"));
    int advances = 0;
    while (field(fight, "respawnStage") != null && advances++ < 700) {
      var stage = (DragonRespawnAnimation) field(fight, "respawnStage");
      int time = (Integer) field(fight, "respawnTime");
      field(fight, "respawnTime", time + 1);
      @SuppressWarnings("unchecked")
      var crystals = (List<EndCrystal>) field(fight, "respawnCrystals");
      stage.tick(level, fight, crystals, time, (BlockPos) field(fight, "portalLocation"));
    }
    check("native_respawn_animation_completed", field(fight, "respawnStage") == null, advances);
    var reborn = level.getEntity(fight.getDragonUUID());
    check(
        "native_reborn_new_identity_and_profile",
        reborn instanceof EnderDragon
            && !reborn.getUUID().equals(old)
            && BossRuntime.profile((EnderDragon) reborn).isPresent(),
        reborn);
    reborn.discard();
  }

  private static void wither(ServerLevel level, WitherBoss wither) throws Exception {
    var target = victim(level, wither.blockPosition().offset(2, 0, 0));
    float health = wither.getHealth();
    check(
        "wither_charge_native_immunity",
        !wither.hurt(level.damageSources().mobAttack(target), 20),
        wither.getInvulnerableTicks());
    near("wither_charge_health_unchanged", wither.getHealth(), health);
    wither.setInvulnerableTicks(1);
    wither.tickCount = 1;
    int from = DAMAGE.size();
    call(wither, WitherBoss.class, "customServerAiStep", new Class<?>[] {});
    check(
        "wither_charge_native_explosion",
        wither.getInvulnerableTicks() == 0
            && DAMAGE.subList(from, DAMAGE.size()).stream()
                .anyMatch(
                    r ->
                        (r.get("type").equals("minecraft:explosion")
                            || r.get("type").equals("minecraft:player_explosion"))),
        DAMAGE.subList(from, DAMAGE.size()));
    target.discard();
    wither.setHealth(wither.getMaxHealth() * .4f);
    check("wither_half_health_native_powered", wither.isPowered(), wither.getHealth());
    var shooter = player(level, "V2WitherArcher");
    var arrow = new Arrow(EntityType.ARROW, level);
    arrow.setOwner(shooter);
    wither.invulnerableTime = 0;
    health = wither.getHealth();
    check(
        "wither_powered_arrow_immunity",
        !wither.hurt(level.damageSources().arrow(arrow, shooter), 10),
        health);
    near("wither_powered_arrow_no_loss", wither.getHealth(), health);
    target = victim(level, wither.blockPosition().offset(12, 0, 0));
    from = DAMAGE.size();
    var skull = new ImpactSkull(level, wither);
    skull.moveTo(target.position());
    level.addFreshEntity(skull);
    skull.hitEntity(target);
    amount(
        target,
        from,
        "wither_skull",
        BossRuntime.profile(wither)
            .orElseThrow()
            .damage()
            .get(BossRules.Channel.WITHER_SKULL)
            .apply(8));
    check(
        "wither_skull_native_status",
        target.hasEffect(MobEffects.WITHER),
        target.getActiveEffects());
    target.discard();
    // Same native skull explosion and geometry with an unassigned owner establish the vanilla
    // input.
    // The owner is deliberately never joined; no boss policy can be attached to this baseline.
    var baselineOwner = EntityType.WITHER.create(level);
    target = victim(level, wither.blockPosition().offset(20, 0, 0));
    from = DAMAGE.size();
    var baselineSkull = new ImpactSkull(level, baselineOwner);
    baselineSkull.moveTo(target.getX() + 1, target.getY(), target.getZ());
    level.addFreshEntity(baselineSkull);
    baselineSkull.hitBlock();
    String baselineId = target.getUUID().toString();
    var baselineHits =
        DAMAGE.subList(from, DAMAGE.size()).stream()
            .filter(
                r ->
                    (r.get("type").equals("minecraft:explosion")
                            || r.get("type").equals("minecraft:player_explosion"))
                        && r.get("victim").equals(baselineId))
            .toList();
    check("native_skull_explosion_baseline_hits", baselineHits.size() == 1, baselineHits);
    float vanillaExplosion = ((Number) baselineHits.getFirst().get("amount")).floatValue();
    target.discard();
    target = victim(level, wither.blockPosition().offset(20, 0, 0));
    from = DAMAGE.size();
    skull = new ImpactSkull(level, wither);
    skull.moveTo(target.getX() + 1, target.getY(), target.getZ());
    level.addFreshEntity(skull);
    skull.hitBlock();
    String targetId = target.getUUID().toString();
    var hits =
        DAMAGE.subList(from, DAMAGE.size()).stream()
            .filter(
                r ->
                    (r.get("type").equals("minecraft:explosion")
                            || r.get("type").equals("minecraft:player_explosion"))
                        && r.get("victim").equals(targetId))
            .toList();
    check(
        "wither_skull_explosion_owned_and_capped",
        hits.size() == 1
            && ((Number) hits.getFirst().get("amount")).doubleValue()
                <= BossRuntime.profile(wither)
                    .orElseThrow()
                    .damage()
                    .get(BossRules.Channel.WITHER_SKULL_EXPLOSION)
                    .maximum(),
        hits);
    near(
        "wither_skull_explosion_scaled_once",
        ((Number) hits.getFirst().get("amount")).doubleValue(),
        BossRuntime.profile(wither)
            .orElseThrow()
            .damage()
            .get(BossRules.Channel.WITHER_SKULL_EXPLOSION)
            .apply(vanillaExplosion));
    target.discard();
  }

  private static void warden(ServerLevel level, Warden warden) throws Exception {
    var target = victim(level, warden.blockPosition().offset(8, 0, 0));
    warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    var sonic = new SonicBoom();
    call(
        sonic,
        SonicBoom.class,
        "start",
        new Class<?>[] {ServerLevel.class, Warden.class, long.class},
        level,
        warden,
        level.getGameTime());
    warden.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_SOUND_DELAY);
    int from = DAMAGE.size();
    call(
        sonic,
        SonicBoom.class,
        "tick",
        new Class<?>[] {ServerLevel.class, Warden.class, long.class},
        level,
        warden,
        level.getGameTime() + 35);
    amount(
        target,
        from,
        "sonic_boom",
        BossRuntime.profile(warden)
            .orElseThrow()
            .damage()
            .get(BossRules.Channel.WARDEN_SONIC)
            .apply(10));
    warden.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    target.discard();
    var fake = player(level, "V2SneakFixture");
    fake.setShiftKeyDown(true);
    check(
        "warden_no_global_combat_gate_for_sneak",
        fake.isSteppingCarefully()
            && !warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),
        "Sneak flag and absent attack memory only; actual silent traversal is mapped-client"
            + " evidence");
  }

  private static void elder(ServerLevel level, ElderGuardian elder) throws Exception {
    var target = victim(level, elder.blockPosition().offset(8, 0, 0));
    elder.setTarget(target);
    Class<?> goal = Class.forName("net.minecraft.world.entity.monster.Guardian$GuardianAttackGoal");
    Constructor<?> ctor = goal.getDeclaredConstructor(Guardian.class);
    ctor.setAccessible(true);
    Object attack = ctor.newInstance(elder);
    call(attack, goal, "start", new Class<?>[] {});
    int from = DAMAGE.size();
    for (int i = 0; i < 100 && elder.getTarget() != null; i++)
      call(attack, goal, "tick", new Class<?>[] {});
    amount(
        target,
        from,
        "indirect_magic",
        BossRuntime.profile(elder)
            .orElseThrow()
            .damage()
            .get(BossRules.Channel.GUARDIAN_BEAM)
            .apply(3));
    check(
        "elder_native_attribute_followup",
        DAMAGE.subList(from, DAMAGE.size()).stream()
            .anyMatch(r -> r.get("type").equals("minecraft:mob_attack")),
        DAMAGE.subList(from, DAMAGE.size()));
    target.discard();
    target = victim(level, elder.blockPosition().offset(4, 0, 0));
    from = DAMAGE.size();
    elder.invulnerableTime = 0;
    elder.hurt(level.damageSources().mobAttack(target), 1);
    amount(
        target,
        from,
        "thorns",
        BossRuntime.profile(elder)
            .orElseThrow()
            .damage()
            .get(BossRules.Channel.GUARDIAN_THORNS)
            .apply(2));
    target.discard();
    var recipient = player(level, "V2FatigueRecipient");
    recipient.moveTo(elder.getX() + 4, elder.getY(), elder.getZ(), 0, 0);
    level.addNewPlayer(recipient);
    recipient.removeAllEffects();
    elder.tickCount = Math.floorMod(-elder.getId(), 1200);
    call(elder, ElderGuardian.class, "customServerAiStep", new Class<?>[] {});
    check(
        "elder_native_mining_fatigue",
        recipient.hasEffect(MobEffects.DIG_SLOWDOWN)
            && recipient.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier() == 2,
        recipient.getActiveEffects());
    level.removePlayerImmediately(recipient, Entity.RemovalReason.DISCARDED);
  }

  private static void portal(MinecraftServer server) throws Exception {
    check(
        "server_portal_config_registered_loaded",
        ExplorationServerConfig.SPEC.isLoaded(),
        ExplorationServerConfig.FILE_NAME);
    check(
        "server_portal_config_enabled",
        ExplorationServerConfig.portalGatesEnabled(),
        "disposable world defaultconfigs fixture");
    var hookPlayer = player(server.overworld(), "V2RealPreHook");
    hookPlayer.portalProcess = new PortalProcessor((Portal) Blocks.END_PORTAL, BlockPos.ZERO);
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
        new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(hookPlayer));
    check(
        "registered_pre_hook_clears_native_portal_processor",
        hookPlayer.portalProcess == null,
        "actual event subscriber + loaded server config; no player tick or mapped input claim");
    JsonObject json;
    try (var in = ExplorationRules.class.getResourceAsStream(ExplorationRules.PATH)) {
      json =
          JsonParser.parseString(
                  new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
    }
    json.getAsJsonObject("portal_gates").addProperty("enabled", true);
    var enabled = ExplorationRules.parse(json.toString());
    json.getAsJsonObject("portal_gates").addProperty("enabled", false);
    var disabled = ExplorationRules.parse(json.toString());
    for (var block : List.of(Blocks.NETHER_PORTAL, Blocks.END_PORTAL)) {
      var player =
          player(server.overworld(), "V2Gate" + BuiltInRegistries.BLOCK.getKey(block).getPath());
      player.portalProcess = new PortalProcessor((Portal) block, BlockPos.ZERO);
      check(
          "gate_disabled_" + block,
          !ExplorationRuntime.gatePortal(player, disabled) && player.portalProcess != null,
          player.portalProcess);
      check(
          "gate_enabled_denies_" + block,
          ExplorationRuntime.gatePortal(player, enabled) && player.portalProcess == null,
          player.portalProcess);
      check(
          "gate_no_portal_command_path_" + block,
          !ExplorationRuntime.gatePortal(player, enabled),
          player.portalProcess);
      com.example.myvillage.cultivation.CultivationService.setRealmAndStage(
          player,
          ResourceLocation.parse("myvillage:golden_core"),
          ResourceLocation.parse("myvillage:golden_late"));
      player.portalProcess = new PortalProcessor((Portal) block, BlockPos.ZERO);
      check(
          "gate_qualified_player_allowed_" + block,
          !ExplorationRuntime.gatePortal(player, enabled) && player.portalProcess != null,
          player.portalProcess);
    }
    for (var dimension : List.of(Level.NETHER, Level.END)) {
      var player = player(server.getLevel(dimension), "V2Return" + dimension.location().getPath());
      player.portalProcess =
          new PortalProcessor(
              (Portal) (dimension == Level.END ? Blocks.END_PORTAL : Blocks.NETHER_PORTAL),
              BlockPos.ZERO);
      check(
          "gate_return_allowed_" + dimension.location(),
          !ExplorationRuntime.gatePortal(player, enabled) && player.portalProcess != null,
          player.portalProcess);
    }
    var gatewayPlayer = player(server.getLevel(Level.END), "V2GatewayFixture");
    gatewayPlayer.portalProcess = new PortalProcessor((Portal) Blocks.END_GATEWAY, BlockPos.ZERO);
    check(
        "gate_end_gateway_unchanged",
        !ExplorationRuntime.gatePortal(gatewayPlayer, enabled)
            && gatewayPlayer.portalProcess != null,
        gatewayPlayer.portalProcess);
    var nonPlayer = victim(server.overworld(), new BlockPos(0, 190, 100));
    nonPlayer.portalProcess = new PortalProcessor((Portal) Blocks.END_PORTAL, BlockPos.ZERO);
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
        new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(nonPlayer));
    check(
        "registered_entity_tick_leaves_nonplayer_portal",
        nonPlayer.portalProcess != null,
        "entity tick fixture; no player-only gate applies");
    nonPlayer.discard();
  }

  private static void loot(ServerLevel end, MinecraftServer server) {
    end.getChunk(CHEST);
    end.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
    var chest = (ChestBlockEntity) end.getBlockEntity(CHEST);
    chest.setLootTable(
        ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.withDefaultNamespace("chests/end_city_treasure")),
        34994L);
    chest.unpackLootTable(null);
    int vanilla = 0, mod = 0;
    for (int i = 0; i < chest.getContainerSize(); i++) {
      var stack = chest.getItem(i);
      if (stack.isEmpty()) continue;
      if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"))
        vanilla++;
      else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("myvillage"))
        mod++;
    }
    check(
        "end_city_root_vanilla_plus_finite_mod_reward",
        vanilla > 0 && mod > 0,
        chest.saveWithoutMetadata(end.registryAccess()));
    String before = chest.saveWithoutMetadata(end.registryAccess()).toString();
    chest.unpackLootTable(null);
    check(
        "end_city_second_open_no_refill",
        before.equals(chest.saveWithoutMetadata(end.registryAccess()).toString()),
        before);
    end.setChunkForced(CHEST.getX() >> 4, CHEST.getZ() >> 4, true);
  }

  private static boolean restart(MinecraftServer server, boolean last) throws Exception {
    var saved =
        JsonParser.parseString(Files.readString(OUT.resolve("restart-fixture.json")))
            .getAsJsonObject();
    var loaded = new ArrayList<Mob>();
    for (var item : saved.getAsJsonArray("entities")) {
      var row = item.getAsJsonObject();
      var dimension =
          ResourceKey.create(
              Registries.DIMENSION, ResourceLocation.parse(row.get("dimension").getAsString()));
      var level = server.getLevel(dimension);
      level.getChunk(row.get("x").getAsInt() >> 4, row.get("z").getAsInt() >> 4);
      var entity = level.getEntity(UUID.fromString(row.get("uuid").getAsString()));
      if (entity == null && !last) return false;
      check("actual_restart_loaded_" + row.get("uuid"), entity instanceof Mob, entity);
      loaded.add((Mob) entity);
    }
    for (int i = 0; i < loaded.size(); i++) {
      var mob = loaded.get(i);
      var row = saved.getAsJsonArray("entities").get(i).getAsJsonObject();
      near(
          "actual_restart_no_heal_" + mob.getType(),
          mob.getHealth(),
          row.get("health").getAsDouble());
      near(
          "actual_restart_no_rescale_" + mob.getType(),
          mob.getMaxHealth(),
          row.get("max").getAsDouble());
      String key = mob instanceof EnderMan ? VanillaMonsterRealms.BIRTH : BossRuntime.BIRTH;
      check(
          "actual_restart_birth_" + mob.getType(),
          row.get("birth")
              .getAsString()
              .equals(mob.getPersistentData().getCompound(key).toString()),
          mob.getPersistentData());
      if (mob instanceof WitherBoss wb)
        check(
            "actual_restart_wither_half_phase",
            wb.isPowered() && wb.getInvulnerableTicks() == 0,
            wb.getHealth());
    }
    done("boss_actual_restart");
    done("end_actual_restart");
    var end = server.getLevel(Level.END);
    end.getChunk(CHEST);
    var chest = (ChestBlockEntity) end.getBlockEntity(CHEST);
    chest.unpackLootTable(null);
    check(
        "actual_restart_end_city_loot_no_refill",
        saved
            .get("chest")
            .getAsString()
            .equals(chest.saveWithoutMetadata(end.registryAccess()).toString()),
        chest.saveWithoutMetadata(end.registryAccess()));
    done("loot_actual_restart");
    return true;
  }

  private static final class ImpactSkull extends WitherSkull {
    ImpactSkull(ServerLevel level, WitherBoss owner) {
      super(level, owner, new Vec3(1, 0, 0));
    }

    void hitEntity(Entity entity) {
      super.onHitEntity(new EntityHitResult(entity));
    }

    void hitBlock() {
      super.onHit(new BlockHitResult(position(), Direction.UP, blockPosition(), false));
    }
  }
}
