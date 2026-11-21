package com.example.myvillage.cultivation.fate;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatHotbarService;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.ability.FateAbilityCooldowns;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import com.example.myvillage.item.CompanionSwordItem;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CompanionSwordRuntime {
    public static final String FATE = "yuanxian:primordial_sword_bone";
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("yuanxian:companion_sword_pursuit"));
    private static final String PROJECTION = "yuanxian_companion_projection";
    private static final double STEP = .28;
    private record Flight(UUID sword, int slot, int form, long until, Vec3 landing, Display.ItemDisplay display) { }
    private record Hit(UUID owner, UUID sword, DamageSource source, float health, int next, long tick) { }
    private record Trial(UUID sword, int next, long lastHit, double damage, boolean pursuit) { }
    private static final Map<ServerPlayer, Flight> FLIGHTS = new HashMap<>();
    private static final Map<LivingEntity, Hit> HITS = new WeakHashMap<>();
    private static final Map<LivingEntity, Map<UUID, Trial>> TRIALS = new WeakHashMap<>();

    private CompanionSwordRuntime() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CompanionSwordRuntime::attack);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CompanionSwordRuntime::beforeHealthLoss);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CompanionSwordRuntime::afterHealthLoss);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CompanionSwordRuntime::victory);
        NeoForge.EVENT_BUS.addListener(CompanionSwordRuntime::projectionJoined);
    }

    public static void claim(ServerPlayer player) {
        var extras = FateExtras.of(player);
        if (!FateService.has(player, FATE) || extras.sword().isPresent()) return;
        UUID sword = UUID.randomUUID();
        var stack = new ItemStack(ModItems.PRIMORDIAL_COMPANION_SWORD.get());
        stack.set(ModDataComponents.COMPANION_SWORD.get(), new CompanionSwordItem.Mark(player.getUUID(), sword, 0, extras.victories()));
        if (!player.getInventory().add(stack)) {
            player.displayClientMessage(Component.translatable("fate.yuanxian.sword.inventory_full"), false);
            return;
        }
        CultivationService.replaceFateExtras(player, extras.withSword(sword));
    }

    private static boolean owns(ServerPlayer player, CompanionSwordItem.Mark mark) {
        return mark != null && mark.owner().equals(player.getUUID())
                && FateExtras.of(player).sword().filter(mark.sword()::equals).isPresent()
                && FateService.has(player, FATE);
    }

    private static int realm(ServerPlayer player) { return FateReworkRules.get().stage(CultivationService.getProfile(player).realmId()); }

    private static FateReworkRules.Stage stage(int form) { return FateReworkRules.get().stages().get(form); }

    private static int form(ServerPlayer player) { return Math.min(realm(player), FateExtras.of(player).form()); }

    public static void tick(ServerPlayer player) {
        var extras = FateExtras.of(player);
        if (extras.sword().isEmpty()) return;
        int form = Math.max(0, form(player));
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            var mark = CompanionSwordItem.mark(stack);
            if (mark != null && mark.owner().equals(player.getUUID()) && extras.sword().filter(mark.sword()::equals).isPresent()
                    && (mark.form() != form || !mark.victories().equals(extras.victories())))
                stack.set(ModDataComponents.COMPANION_SWORD.get(), new CompanionSwordItem.Mark(mark.owner(), mark.sword(), form, extras.victories()));
        }
        var flight = FLIGHTS.get(player);
        if (flight != null && !valid(player, flight)) forget(player);
    }

    public static boolean permitsStrike(LivingEntity actor) {
        if (!actor.getMainHandItem().is(ModItems.PRIMORDIAL_COMPANION_SWORD.get())) return true;
        return actor instanceof ServerPlayer player && realm(player) >= 0 && !armed(player)
                && owns(player, CompanionSwordItem.mark(player.getMainHandItem()));
    }

    private static void attack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide() && !permitsStrike(event.getEntity())) event.setCanceled(true);
    }

    public static double damageMultiplier(LivingEntity actor, LivingEntity target, DamageSource source) {
        if (!(actor instanceof ServerPlayer player) || !swordHit(player, target, source)) return 1;
        int form = form(player);
        return form < 0 ? 1 : 1 + stage(form).swordBonus();
    }

    private static boolean swordHit(ServerPlayer actor, LivingEntity target, DamageSource source) {
        return source.getEntity() == actor && source.getDirectEntity() == actor
                && (FateDefenses.directMelee(source) || source.is(DAMAGE))
                && !CombatDamageService.isDerivedHit(target) && owns(actor, CompanionSwordItem.mark(actor.getMainHandItem()));
    }

    public static long cost(LivingEntity actor) {
        if (!(actor instanceof ServerPlayer player)) return 0;
        int form = form(player);
        return form < 0 || armed(player) ? 0 : stage(form).power();
    }

    public static boolean armed(LivingEntity actor) {
        if (!(actor instanceof ServerPlayer player)) return false;
        Flight flight = FLIGHTS.get(player);
        return flight != null && valid(player, flight);
    }

    private static boolean valid(ServerPlayer player, Flight flight) {
        var mark = CompanionSwordItem.mark(player.getMainHandItem());
        return player.isAlive() && player.level() == flight.display.level() && !flight.display.isRemoved()
                && player.serverLevel().getGameTime() < flight.until && player.getInventory().selected == flight.slot
                && form(player) == flight.form && owns(player, mark) && flight.sword.equals(mark.sword());
    }

    public static boolean cast(ServerPlayer player) {
        if (com.example.myvillage.combat.foundation.ControlService.isRooted(player)
                || com.example.myvillage.combat.runtime.CombatReactionService.isStaggered(player)) return false;
        var current = FLIGHTS.get(player);
        if (current != null && valid(player, current)) {
            forget(player);
            return chase(player, current);
        }
        if (current != null) forget(player);
        var mark = CompanionSwordItem.mark(player.getMainHandItem());
        int form = form(player);
        if (form < 0 || !owns(player, mark)) {
            player.displayClientMessage(Component.translatable("fate.yuanxian.sword.need_hand"), true);
            return false;
        }
        var level = player.serverLevel();
        var overworld = player.getServer().overworld();
        var cooldowns = FateAbilityCooldowns.get(overworld);
        long now = overworld.getGameTime();
        var stage = stage(form);
        if (cooldowns.remaining(player.getUUID(), FateReworkRules.SWORD_ART, now) > 0
                || CombatCaster.power(player) < stage.power()) return false;
        Vec3 eye = player.getEyePosition();
        var landingHit = trace(player, eye, eye.add(player.getLookAngle().scale(stage.reach() + player.getEyeHeight())));
        Vec3 landing = landingHit.getLocation().add(0, .02, 0);
        if (landingHit.getType() == HitResult.Type.MISS || landingHit.getDirection() != Direction.UP
                || landing.subtract(player.position()).horizontalDistance() > stage.reach()
                || !level.hasChunkAt(BlockPos.containing(landing))) {
            player.displayClientMessage(Component.translatable("fate.yuanxian.sword.need_ground"), true);
            return false;
        }
        var display = EntityType.ITEM_DISPLAY.create(level);
        if (display == null) return false;
        var config = new CompoundTag();
        config.putString("item_display", "fixed");
        display.load(config);
        display.getSlot(0).set(player.getMainHandItem().copy());
        display.moveTo(landing.x, landing.y + .52, landing.z, player.getYRot(), 0);
        var cast = new Flight(mark.sword(), player.getInventory().selected, form,
                level.getGameTime() + FateReworkRules.get().swordWindow(), landing, display);
        FLIGHTS.put(player, cast);
        display.getPersistentData().putUUID(PROJECTION, player.getUUID());
        if (!level.addFreshEntity(display) || !CombatCaster.spend(player, stage.power())) {
            forget(player);
            return false;
        }
        cooldowns.start(player.getUUID(), FateReworkRules.SWORD_ART, now, stage.cooldown());
        CombatHotbarService.forget(player.getUUID());
        trail(player, eye, landing);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), player.getSoundSource(), .55f, 1.35f);
        player.displayClientMessage(Component.translatable("fate.yuanxian.sword.chase_ready"), true);
        return true;
    }

    private static boolean chase(ServerPlayer player, Flight flight) {
        var stage = stage(flight.form);
        var level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 heading = flight.landing.subtract(start).multiply(1, 0, 1);
        double reach = Math.min(heading.horizontalDistance(), stage.reach());
        Vec3 direction = heading.normalize();
        if (reach < .02 || floor(player, start.x, start.z, start.y) == null) return false;
        Set<LivingEntity> targets = new LinkedHashSet<>();
        Vec3 position = start;
        for (double distance = 0; distance < reach; distance += STEP) {
            Vec3 next = position.add(direction.scale(Math.min(STEP, reach - distance)));
            if (!level.hasChunkAt(BlockPos.containing(next))) break;
            Double y = floor(player, next.x, next.z, position.y);
            if (y == null) break;
            Vec3 delta = new Vec3(next.x, y, next.z).subtract(position);
            player.move(MoverType.SELF, delta);
            Vec3 reached = player.position();
            collect(player, position, reached, targets, stage.targets());
            if (reached.subtract(position).subtract(delta).horizontalDistanceSqr() > .001) break;
            position = reached;
        }
        if (player.position().distanceToSqr(start) > 0.0001) {
            player.fallDistance = 0;
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            trail(player, start.add(0, .8, 0), player.position().add(0, .8, 0));
        }
        for (var target : targets) strike(player, target, stage);
        if (!targets.isEmpty()) level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), .8f, 1.2f);
        return true;
    }

    private static Double floor(ServerPlayer player, double x, double z, double fromY) {
        var hit = trace(player, new Vec3(x, fromY + .61, z), new Vec3(x, fromY - .76, z));
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) return null;
        double y = hit.getLocation().y;
        var bounds = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
        for (var shape : player.level().getBlockCollisions(player, bounds.deflate(.0001)))
            if (!shape.isEmpty()) return null;
        return y;
    }

    private static BlockHitResult trace(ServerPlayer player, Vec3 start, Vec3 end) {
        return player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static void collect(ServerPlayer player, Vec3 start, Vec3 end, Set<LivingEntity> targets, int limit) {
        if (targets.size() >= limit) return;
        var nearby = new ArrayList<>(player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().expandTowards(start.subtract(end)).inflate(.62, .15, .62),
                target -> !targets.contains(target) && CombatActors.mayHarm(player, target)));
        nearby.sort(Comparator.comparingDouble(target -> target.position().distanceToSqr(start)));
        for (var target : nearby) {
            Vec3 from = end.add(0, player.getBbHeight() * .52, 0);
            if (trace(player, from, target.getBoundingBox().getCenter()).getType() == HitResult.Type.MISS) targets.add(target);
            if (targets.size() >= limit) return;
        }
    }

    private static void strike(ServerPlayer player, LivingEntity target, FateReworkRules.Stage stage) {
        if (!CombatActors.mayHarm(player, target) || !target.isAttackable()
                || !CommonHooks.onPlayerAttackTarget(player, target)) return;
        var holder = player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
        var source = new DamageSource(holder, player);
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + HeartTechniques.attackExtra(player, "sword", null, true, true);
        float raw = StatsRuntime.outgoing(player, attack,
                stage.strike() * com.example.myvillage.aptitude.AptitudeRuntime.weaponMultiplier(player, "sword"));
        int old = target.invulnerableTime;
        target.invulnerableTime = 0;
        try { StatsRuntime.hurt(player, target, source, raw); }
        finally { target.invulnerableTime = Math.max(old, target.invulnerableTime); }
    }

    private static void beforeHealthLoss(LivingDamageEvent.Pre event) {
        var target = event.getEntity();
        var source = event.getSource();
        if (target.level().isClientSide() || !(source.getEntity() instanceof ServerPlayer player)
                || !swordHit(player, target, source) || event.getNewDamage() <= 0) return;
        var extras = FateExtras.of(player);
        int next = extras.form() + 1;
        if (next >= FateReworkRules.get().stages().size() || realm(player) < next) return;
        var targetRealm = StatsRuntime.assignedRealm(target).map(k -> FateReworkRules.get().stage(ResourceLocation.parse(k.realmId()))).orElse(-1);
        if (targetRealm < next) return;
        HITS.put(target, new Hit(player.getUUID(), extras.sword().orElseThrow(), source, target.getHealth(), next, target.level().getGameTime()));
    }

    private static void afterHealthLoss(LivingDamageEvent.Post event) {
        var target = event.getEntity();
        var hit = HITS.get(target);
        if (hit == null || hit.source != event.getSource() || hit.tick != target.level().getGameTime()) return;
        HITS.remove(target);
        double loss = hit.health - target.getHealth();
        if (loss <= 0) return;
        var receipts = TRIALS.computeIfAbsent(target, unused -> new HashMap<>());
        int idle = FateReworkRules.get().trialIdle();
        receipts.values().removeIf(old -> hit.tick - old.lastHit >= idle);
        var prior = receipts.get(hit.owner);
        if (prior != null && (!prior.sword.equals(hit.sword) || prior.next != hit.next)) prior = null;
        receipts.put(hit.owner, new Trial(hit.sword, hit.next, hit.tick,
                loss + (prior == null ? 0 : prior.damage), hit.source.is(DAMAGE) || prior != null && prior.pursuit));
    }

    private static void victory(LivingDropsEvent event) {
        var target = event.getEntity();
        var receipts = TRIALS.remove(target);
        if (receipts == null || target.getServer() == null) return;
        var rules = FateReworkRules.get();
        long now = target.level().getGameTime();
        receipts.forEach((owner, trial) -> {
            if (now - trial.lastHit >= rules.trialIdle() || !trial.pursuit || trial.damage < target.getMaxHealth() * rules.trialFraction()) return;
            var player = target.getServer().getPlayerList().getPlayer(owner);
            if (player == null || !FateService.has(player, FATE) || realm(player) < trial.next) return;
            var extras = FateExtras.of(player);
            if (trial.next != extras.form() + 1 || extras.sword().filter(trial.sword::equals).isEmpty()) return;
            var pos = target.blockPosition();
            String record = "%s · %s (%d, %d, %d)".formatted(target.getDisplayName().getString(),
                    target.level().dimension().location(), pos.getX(), pos.getY(), pos.getZ());
            CultivationService.replaceFateExtras(player, extras.withVictory(trial.next, record));
            tick(player);
            player.displayClientMessage(Component.translatable("fate.yuanxian.sword.evolved",
                    Component.translatable("cultivation.realm.yuanxian." + stage(trial.next).realm().getPath())), false);
        });
    }

    private static void trail(ServerPlayer player, Vec3 from, Vec3 to) {
        Vec3 path = to.subtract(from);
        int steps = Math.min(38, (int) (path.length() * 3));
        for (int i = 1; i <= steps; i++) {
            var point = from.add(path.scale((double) i / steps));
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, .025, .025, .025, .02);
        }
    }

    private static void projectionJoined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Display.ItemDisplay display)
                || !display.getPersistentData().hasUUID(PROJECTION)) return;
        if (FLIGHTS.values().stream().noneMatch(flight -> flight.display == display)) event.setCanceled(true);
    }

    public static void forget(ServerPlayer player) {
        var flight = FLIGHTS.remove(player);
        if (flight != null) {
            flight.display.discard();
            CombatHotbarService.forget(player.getUUID());
        }
    }

    static void clear() {
        for (var player : java.util.List.copyOf(FLIGHTS.keySet())) forget(player);
        HITS.clear();
        TRIALS.clear();
    }
}
