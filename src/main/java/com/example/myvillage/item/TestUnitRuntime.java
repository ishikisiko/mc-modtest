package com.example.myvillage.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.entity.npc.NpcCombatRules;
import com.example.myvillage.entity.npc.NpcCultivationRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.network.TestUnitPayload;
import com.example.myvillage.network.TestUnitSnapshotPayload;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 取物器生成的测试身体独立保存，不注册命簿人物，也不写入其他玩家的测试配置。 */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class TestUnitRuntime {
    public static final String TAG = "myvillage:test_unit";
    public static final int LIMIT = 64, RANGE = 64;
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private TestUnitRuntime() { }

    public static boolean allowedType(EntityType<?> type) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && (id.getNamespace().equals("myvillage") || id.getNamespace().equals("minecraft"))
                && (id.toString().equals("myvillage:cultivator") || type.getCategory() == MobCategory.MONSTER);
    }

    public static TestUnitRelations.Unit relations(Entity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(TAG);
        if (!tag.hasUUID("owner")) return null;
        var pairs = new HashMap<UUID, Boolean>();
        CompoundTag saved = tag.getCompound("pairs");
        for (String id : saved.getAllKeys()) pairs.put(UUID.fromString(id), saved.getBoolean(id));
        return new TestUnitRelations.Unit(tag.getUUID("owner"), tag.getInt("team"), tag.getInt("enemies"),
                tag.getBoolean("player"), tag.getBoolean("monsters"), pairs);
    }

    public static void relations(Mob mob, TestUnitRelations.Unit unit) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("owner", unit.owner()); tag.putInt("team", unit.team()); tag.putInt("enemies", unit.enemies());
        tag.putBoolean("player", unit.player()); tag.putBoolean("monsters", unit.monsters());
        CompoundTag pairs = new CompoundTag(); unit.pairs().forEach((id, hostile) -> pairs.putBoolean(id.toString(), hostile));
        tag.put("pairs", pairs); mob.getPersistentData().put(TAG, tag);
    }

    public static Boolean relation(LivingEntity actor, Entity target) {
        return TestUnitRelations.hostile(actor.getUUID(), relations(actor), actor instanceof Enemy, actor instanceof Player,
                target.getUUID(), relations(target), target instanceof Enemy, target instanceof Player);
    }

    public static boolean authorized(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && QualityPicker.held(player);
    }

    private static List<Mob> owned(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                mob -> { var u = relations(mob); return u != null && u.owner().equals(player.getUUID()); })
                .stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(LIMIT).toList();
    }

    private static Mob selected(ServerPlayer player, JsonObject data, String key) {
        UUID id = UUID.fromString(data.get(key).getAsString());
        var entity = player.serverLevel().getEntity(id);
        if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.distanceToSqr(player) > RANGE * RANGE
                || relations(mob) == null || !relations(mob).owner().equals(player.getUUID()))
            throw new IllegalArgumentException("只能修改附近由你召唤的测试单位");
        return mob;
    }

    public static void request(ServerPlayer player, TestUnitPayload payload) {
        if (!authorized(player)) return;
        long now = player.serverLevel().getGameTime();
        if (REQUESTS.getOrDefault(player.getUUID(), Long.MIN_VALUE) == now) return;
        REQUESTS.put(player.getUUID(), now);
        try {
            switch (payload.action()) {
                case "list" -> snapshot(player, "已刷新附近测试单位");
                case "inspect_skills" -> {
                    var mob=selected(player,TestUnitConfig.object(payload.data()),"unit");
                    if(!(mob instanceof NpcEntity npc)||npc.isLedgerAvatar())throw new IllegalArgumentException("请选择普通测试 NPC");
                    skillSnapshot(player,npc,"已读取该 NPC 的功法与技能");
                }
                case "skills" -> {
                    var data=TestUnitConfig.object(payload.data());var mob=selected(player,data,"unit");
                    if(!(mob instanceof NpcEntity npc)||npc.isLedgerAvatar())throw new IllegalArgumentException("请选择普通测试 NPC");
                    var skills=com.example.myvillage.entity.npc.NpcSkillLoadout.parse(data.get("skills").toString());
                    com.example.myvillage.entity.npc.NpcSkillRuntime.validate(npc,skills);
                    stop(npc);com.example.myvillage.entity.npc.NpcSkillRuntime.configure(npc,skills);
                    skillSnapshot(player,npc,"已应用功法与技能；自动施放仍检查境界、资质、武器和灵力");
                }
                case "spawn" -> {
                    Mob mob = spawn(player, TestUnitConfig.parse(payload.data()));
                    snapshot(player, "已召唤「" + mob.getName().getString() + "」" + (mob.isNoAi() ? "，战斗已暂停" : ""));
                }
                case "relations" -> {
                    var data = TestUnitConfig.object(payload.data()); var mob = selected(player, data, "unit");
                    var old = relations(mob);
                    var next = new TestUnitRelations.Unit(player.getUUID(), data.get("team").getAsInt(),
                            data.get("enemies").getAsInt(), data.get("player").getAsBoolean(),
                            data.get("monsters").getAsBoolean(), old.pairs());
                    relations(mob, next); stop(mob); snapshot(player, "已更新敌对关系");
                }
                case "pair" -> {
                    var data = TestUnitConfig.object(payload.data()); var a = selected(player, data, "unit");
                    var b = selected(player, data, "other"); int mode = data.get("mode").getAsInt();
                    if (a == b || mode < -1 || mode > 1) throw new IllegalArgumentException("请选择两个不同的单位");
                    pair(a, b, mode); pair(b, a, mode); stop(a); stop(b); snapshot(player, "已更新两个单位的关系");
                }
                case "pause", "resume", "remove" -> {
                    var mob = selected(player, TestUnitConfig.object(payload.data()), "unit");
                    stop(mob);
                    if (payload.action().equals("remove")) mob.discard();
                    else mob.setNoAi(payload.action().equals("pause"));
                    snapshot(player, "已处理所选测试单位");
                }
                case "pause_all", "resume_all", "clear" -> {
                    for (var mob : owned(player)) {
                        stop(mob);
                        if (payload.action().equals("clear")) mob.discard();
                        else mob.setNoAi(payload.action().equals("pause_all"));
                    }
                    snapshot(player, payload.action().equals("clear") ? "已清理附近由你召唤的测试单位" : "已更新附近测试单位的战斗状态");
                }
                default -> throw new IllegalArgumentException("未知测试操作");
            }
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | UnsupportedOperationException failure) {
            snapshot(player, "操作失败：" + (failure.getMessage() == null ? "配置无效" : failure.getMessage()));
        }
    }

    private static void pair(Mob a, Mob b, int mode) {
        var old = relations(a); var pairs = new HashMap<>(old.pairs());
        if (mode < 0) pairs.remove(b.getUUID()); else pairs.put(b.getUUID(), mode == 1);
        relations(a, new TestUnitRelations.Unit(old.owner(), old.team(), old.enemies(), old.player(), old.monsters(), pairs));
    }

    public static Mob spawn(ServerPlayer player, TestUnitConfig config) {
        if (!authorized(player) || !config.relations().owner().equals(player.getUUID()) || !config.relations().pairs().isEmpty())
            throw new IllegalArgumentException("召唤权限或归属无效");
        int count = 0;
        for (ServerLevel level : player.server.getAllLevels()) for (Entity entity : level.getAllEntities()) {
            var u = relations(entity); if (u != null && u.owner().equals(player.getUUID())) count++;
        }
        if (count >= LIMIT) throw new IllegalArgumentException("已加载测试单位达到上限，请先清理");
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(config.entity())).orElseThrow(
                () -> new IllegalArgumentException("实体类型不存在"));
        if (!allowedType(type) || !(type.create(player.serverLevel()) instanceof Mob mob))
            throw new IllegalArgumentException("此实体不支持测试召唤");
        ServerLevel level = player.serverLevel();
        var view = player.getLookAngle();
        Vec3 start = player.getEyePosition(), end = start.add(view.scale(12));
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point = hit.getType() == HitResult.Type.MISS ? player.position().add(new Vec3(view.x, 0, view.z).normalize().scale(4))
                : Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        boolean found = false;
        for (int dy = 0; dy <= 3; dy++) {
            mob.setPos(point.x, point.y + dy, point.z);
            if (level.hasChunkAt(mob.blockPosition()) && level.getWorldBorder().isWithinBounds(mob.getBoundingBox())
                    && level.noCollision(mob) && level.getBlockState(mob.blockPosition().below()).isFaceSturdy(
                        level, mob.blockPosition().below(), net.minecraft.core.Direction.UP)) { found = true; break; }
        }
        if (!found) throw new IllegalArgumentException("视线前方没有足够的落脚空间，请对准开阔地面");
        // 不调用原版 finalizeSpawn：测试身体不需要地区抽签、骑乘附属体或随机装备。
        relations(mob, config.relations());
        for (var entry : config.attributes().entrySet()) {
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(entry.getKey())).orElseThrow(
                    () -> new IllegalArgumentException("属性不存在：" + entry.getKey()));
            var instance = mob.getAttribute(holder);
            double v = entry.getValue();
            if (instance == null || !(holder.value() instanceof RangedAttribute range) || v < range.getMinValue() || v > range.getMaxValue()
                    || holder.is(Attributes.MAX_HEALTH) && v <= 0)
                throw new IllegalArgumentException("属性超出此实体的范围：" + entry.getKey());
        }
        if (mob instanceof NpcEntity npc) NpcCultivationRuntime.configureTest(npc, config);
        else if (!config.fates().isEmpty() || !config.aptitudes().isEmpty() || config.power() >= 0 || config.powerCap() >= 0)
            throw new IllegalArgumentException("天赋、资质和灵力目前适用于修士 NPC");
        StatsRuntime.applyRealm(mob, config.realm(), config.stage());
        for (var entry : config.attributes().entrySet()) {
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(entry.getKey())).orElseThrow();
            var attribute = mob.getAttribute(holder);
            double realmAddition = 0;
            for (var key : List.of(StatsRuntime.REALM_HEALTH, StatsRuntime.REALM_ATTACK, StatsRuntime.REALM_DEFENSE)) {
                var modifier = attribute.getModifier(key); if (modifier != null) realmAddition += modifier.amount();
            }
            attribute.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    ResourceLocation.parse("myvillage:test_override"), entry.getValue() - attribute.getBaseValue() - realmAddition,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        if (mob instanceof NpcEntity npc) com.example.myvillage.cultivation.fate.FateRuntime.refresh(npc);
        if (mob instanceof NpcEntity npc) NpcCultivationRuntime.fillTestPower(npc, config.power());
        if(mob instanceof NpcEntity npc)com.example.myvillage.entity.npc.NpcSkillRuntime.configure(npc,config.skills());
        else if(!config.skills().equals(com.example.myvillage.entity.npc.NpcSkillLoadout.EMPTY))throw new IllegalArgumentException("功法技能编辑目前适用于修士 NPC");
        mob.setHealth(mob.getMaxHealth()); mob.setPersistenceRequired(); mob.setNoAi(config.paused());
        if (!config.name().isBlank()) { mob.setCustomName(Component.literal(config.name())); mob.setCustomNameVisible(true); }
        if (!level.addFreshEntity(mob)) throw new IllegalStateException("世界拒绝生成该单位");
        return mob;
    }

    private static void stop(Mob mob) {
        if (mob instanceof NpcEntity npc) NpcCombatRuntime.disengage(npc);
        if (mob instanceof com.example.myvillage.entity.beast.BeastEntity beast) beast.cancelTestMove();
        mob.setTarget(null); mob.getNavigation().stop(); mob.setAggressive(false);
    }

    public static void snapshot(ServerPlayer player, String message) {
        JsonObject data = new JsonObject(); data.addProperty("message", message); JsonArray units = new JsonArray();
        for (Mob mob : owned(player)) {
            var u = relations(mob); JsonObject row = new JsonObject();
            row.addProperty("id", mob.getUUID().toString()); row.addProperty("name", mob.getName().getString());
            row.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
            row.addProperty("health", mob.getHealth()); row.addProperty("maximum", mob.getMaxHealth());
            row.addProperty("team", u.team()); row.addProperty("enemies", u.enemies()); row.addProperty("player", u.player());
            row.addProperty("monsters", u.monsters()); row.addProperty("paused", mob.isNoAi());
            JsonObject pairs = new JsonObject(); u.pairs().forEach((id, value) -> pairs.addProperty(id.toString(), value));
            row.add("pairs", pairs); units.add(row);
        }
        data.add("units", units); PacketDistributor.sendToPlayer(player, new TestUnitSnapshotPayload(data.toString()));
    }
    private static void skillSnapshot(ServerPlayer player,NpcEntity npc,String message) {
        JsonObject data=new JsonObject();data.addProperty("message",message);data.addProperty("skill_unit",npc.getUUID().toString());
        data.addProperty("skill_name",npc.getName().getString());
        data.add("skills",com.google.gson.JsonParser.parseString(com.example.myvillage.entity.npc.NpcSkillRuntime.get(npc).json()));
        PacketDistributor.sendToPlayer(player,new TestUnitSnapshotPayload(data.toString()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && relations(mob) != null)
            mob.targetSelector.addGoal(0, new TestTargetGoal(mob));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void changingTarget(LivingChangeTargetEvent event) {
        var target = event.getNewAboutToBeSetTarget();
        if (!event.getEntity().level().isClientSide() && target != null && Boolean.FALSE.equals(relation(event.getEntity(), target)))
            event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && (Boolean.FALSE.equals(relation(attacker, event.getEntity()))
                    || relations(attacker) != null && attacker instanceof Mob mob && mob.isNoAi())) event.setCanceled(true);
    }

    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { REQUESTS.clear(); }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.getEntity().getUUID());
    }

    private static final class TestTargetGoal extends Goal {
        private final Mob mob;
        TestTargetGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() { return mob.isAlive() && !mob.isNoAi() && relations(mob) != null; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { choose(); }
        @Override public void stop() { mob.setTarget(null); }
        @Override public void tick() { if (mob.tickCount % NpcCombatRules.get().decisionTicks() == 0) choose(); }
        private void choose() {
            double range = Math.min(RANGE, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
            var current = mob.getTarget();
            if (current != null && current.isAlive() && mob.distanceToSqr(current) <= range * range
                    && Boolean.TRUE.equals(relation(mob, current)) && CombatActors.mayHarm(mob, current)) return;
            LivingEntity target = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range),
                    other -> other != mob && mob.distanceToSqr(other) <= range * range
                        && Boolean.TRUE.equals(relation(mob, other)) && other.isAttackable()
                        && CombatActors.mayHarm(mob, other) && mob.hasLineOfSight(other)).stream()
                    .min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            if (mob instanceof NpcEntity npc) NpcCombatRuntime.selectTestTarget(npc, target);
            else mob.setTarget(target);
        }
    }
}
