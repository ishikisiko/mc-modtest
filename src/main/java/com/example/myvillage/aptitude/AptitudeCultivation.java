package com.example.myvillage.aptitude;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 六类培养的正式服务器入口。每次奖励校验玩家、实际行为与当前课程。 */
public final class AptitudeCultivation {
    private static final String OWNER = "myvillage_memory_owner", COURSE = "myvillage_memory_course";
    private AptitudeCultivation() { }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::tick);
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::death);
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::interact);
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::landInteract);
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::repairAtAnvil);
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::guardDamage);
        NeoForge.EVENT_BUS.addListener(AptitudeCultivation::guardTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) -> {
            if (e.getEntity().getPersistentData().hasUUID(OWNER)) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent e) -> {
            if (e.getEntity().getPersistentData().hasUUID(OWNER)) e.setDroppedExperience(0);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) stop(p);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) stop(p);
            LAND.clear();
        });
    }
    public static int itemLimit(int tier) {
        var r = AptitudeRules.get();
        return tier < 1 || tier > r.growth("item_tiers") ? 0 : r.growth("item_initial_limit") + (tier - 1) * r.growth("item_limit_per_tier");
    }
    private static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && CultivationService.getProfile(p).awakened();
    }
    public static boolean use(ServerPlayer p, ItemStack stack, String kind) {
        if (!eligible(p)) return reject(p, "先觉醒灵根，才能进行资质培养。");
        CultivationService.initializeAptitudes(p);
        var payload = AptitudeContent.data(stack); String id = payload.getString("aptitude");
        int limit = itemLimit(payload.getInt("tier"));
        if (!AptitudeRules.get().valid(id) || limit == 0 || AptitudeRuntime.score(p, id) >= limit)
            return reject(p, "这份材料不适用于当前资质。");
        var rules = AptitudeRules.get();
        if (kind.equals("fruit") || kind.equals("book")) {
            if (kind.equals("fruit") != rules.elements().contains(id)) return false;
            int gain = Math.min(limit - AptitudeRuntime.score(p, id), rules.growth(kind.equals("fruit") ? "fruit_gain" : "book_gain"));
            if (!AptitudeRuntime.add(p, id, gain, kind.equals("fruit") ? "炼化灵果" : "研读资质书")) return false;
        } else {
            if (CultivationService.aptitudes(p).training().isPresent()) return reject(p, "先完成或停止当前研习。");
            boolean started = kind.equals("memory") ? memory(p, id, limit) : research(p, id, limit);
            if (!started) return false;
        }
        if (!p.isCreative()) stack.shrink(1);
        return true;
    }
    static boolean pay(ServerPlayer player, int amount) {
        if (player.isCreative()) return true;
        var source = com.example.myvillage.spiritstone.SpiritStoneSources.open(player);
        var copies = source.copies();
        return com.example.myvillage.spiritstone.SpiritStoneStorage.remove(copies,
                s -> s.is(com.example.myvillage.item.SpiritStoneTags.LOW_GRADE_SPIRIT_STONE), amount) && source.commit(copies);
    }
    private static AptitudeTraining training(ServerPlayer p, String kind, String id, String target, int required, int gain, int teacher, int duration, int ceiling) {
        var pos = p.blockPosition();
        return new AptitudeTraining(kind, id, target, 0, required, gain, AptitudeRuntime.clock(p) + duration,
                p.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(), teacher, ceiling);
    }
    private static void install(ServerPlayer p, AptitudeTraining course) {
        var state = CultivationService.aptitudes(p);
        CultivationService.replaceAptitudes(p, state.withTraining(Optional.of(course)), true);
    }
    public static void stop(ServerPlayer p) {
        LAND.remove(p.getUUID()); cleanup(p); CultivationService.replaceAptitudes(p, CultivationService.aptitudes(p).withTraining(Optional.empty()), true);
    }
    private static boolean memory(ServerPlayer p, String id, int limit) {
        var r = AptitudeRules.get();
        if (!r.weapons().contains(id) || p.serverLevel().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL)
            return reject(p, "战意试炼需要非和平难度及兵器传承。");
        String courseId = UUID.randomUUID().toString(); var mobs = new java.util.ArrayList<net.minecraft.world.entity.Mob>();
        int count = r.growth("memory_kills");
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.Mob mob = switch(id) {
                case "sword" -> com.example.myvillage.entity.ModEntities.RIDGE_REAVER.get().create(p.serverLevel());
                case "spear" -> com.example.myvillage.entity.ModEntities.REDPLATE_LIZARD.get().create(p.serverLevel());
                default -> com.example.myvillage.entity.ModEntities.DEMON_WOLF.get().create(p.serverLevel());
            };
            if (mob == null) return false;
            double angle = 2 * Math.PI * i / count;
            mob.moveTo(p.getX() + r.growth("memory_spawn_radius") * Math.cos(angle), p.getY(), p.getZ() + r.growth("memory_spawn_radius") * Math.sin(angle), 0, 0);
            if (!p.serverLevel().hasChunkAt(mob.blockPosition()) || !p.serverLevel().noCollision(mob)
                    || !p.serverLevel().getBlockState(mob.blockPosition().below()).isSolidRender(p.level(), mob.blockPosition().below()))
                return reject(p, "周围需要有足够平坦、已加载的演武空地。");
            mob.getPersistentData().putUUID(OWNER, p.getUUID()); mob.getPersistentData().putString(COURSE, courseId);
            mob.setPersistenceRequired(); mob.setCustomName(Component.literal(AptitudeRuntime.name(id) + "战意残影"));
            mob.setCustomNameVisible(true); mob.setTarget(p);
            mobs.add(mob);
        }
        var added = new java.util.ArrayList<net.minecraft.world.entity.Mob>();
        for (var mob : mobs) {
            if (!p.serverLevel().addFreshEntity(mob)) { added.forEach(net.minecraft.world.entity.Mob::discard); return false; }
            added.add(mob);
            var profile = CultivationService.getProfile(p);
            try { var key = com.example.myvillage.stats.StatsRules.bundled().playerKey(profile.realmId().toString(), profile.stageId().toString());
                com.example.myvillage.stats.runtime.StatsRuntime.applyRealm(mob, key.realmId(), key.stage());
                mob.setHealth(mob.getMaxHealth());
            } catch (IllegalArgumentException ignored) { }
        }
        install(p, training(p, "memory", id, courseId, count, Math.min(limit - AptitudeRuntime.score(p, id), r.growth("memory_gain")), -1, r.growth("memory_duration_ticks"), limit));
        message(p, "战意已展开，请使用" + AptitudeRuntime.name(id) + "对应兵器击败残影。"); return true;
    }
    private static boolean research(ServerPlayer p, String id, int limit) {
        var r = AptitudeRules.get(); if (!r.crafts().contains(id)) return false;
        String target = "craft";
        if (id.equals("alchemy")) {
            var formula = com.example.myvillage.alchemy.AlchemyRules.get().recipes().stream()
                    .filter(recipe -> !com.example.myvillage.alchemy.AlchemyKnowledge.knows(p, recipe.id())
                            && recipe.minimumAptitude() <= AptitudeRules.add(AptitudeRuntime.score(p,id), r.growth("research_gain")))
                    .findFirst();
            if (formula.isPresent()) target = "craft|" + formula.orElseThrow().id();
        }
        if (id.equals("forging") && p.getOffhandItem().isDamaged()) {
            ItemStack artifact = p.getOffhandItem();
            if (CombatStyles.bundled().weapon(artifact).isEmpty()) return reject(p, "副手需要持有可修复的模组兵器。");
            target = UUID.randomUUID().toString();
        }
        if (!pay(p, r.growth("research_cost"))) return reject(p, "研究所需下品灵石不足。");
        if (!target.startsWith("craft")) {
            ItemStack artifact = p.getOffhandItem();
            var data = artifact.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.putString("myvillage_research", target); artifact.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        install(p, training(p, "research", id, target, 1, r.growth("research_gain"), -1, r.growth("memory_duration_ticks"), limit));
        message(p, target.startsWith("craft") ? "课题已开始，请亲自完成一件达到研究品质要求的对应成品。" : "修复课题已开始，请在锻台修复副手标记的兵器。");
        return true;
    }
    public static void crafted(ServerPlayer p, String id, String recipe, int quality, ItemStack result) {
        var course = CultivationService.aptitudes(p).training().orElse(null);
        if (course == null || !valid(p, course) || !course.kind().equals("research") || !course.aptitude().equals(id)) return;
        boolean repair = !course.target().startsWith("craft");
        boolean accepted = repair ? result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getString("myvillage_research").equals(course.target()) && recipe.equals("repair") && !result.isDamaged()
                : !recipe.equals("repair") && quality >= AptitudeRules.get().growth("research_quality");
        if (accepted) finish(p, course);
    }
    public static void teaching(ServerPlayer p, NpcEntity npc, String id) {
        var rules = AptitudeRules.get();
        if (!eligible(p) || !npc.isAlive() || p.distanceToSqr(npc) > Math.pow(AptitudeRules.get().growth("teaching_range"), 2) || npc.getTarget() != null
                || CultivationService.aptitudes(p).training().isPresent()) return;
        int difference = AptitudeRuntime.score(npc, id) - AptitudeRuntime.score(p, id);
        if (difference <= 0) { reject(p, "这位修士当前无法继续指导这一项资质。"); return; }
        if (!pay(p, rules.growth("teaching_cost"))) { reject(p, "授业所需下品灵石不足。"); return; }
        install(p, training(p, "teaching", id, npc.getUUID().toString(), rules.growth("teaching_duration_ticks"),
                Math.min(difference, rules.growth("teaching_gain")), npc.ledgerPersonId(), rules.growth("teaching_duration_ticks") * rules.growth("teaching_timeout_multiplier"), AptitudeRuntime.score(npc, id)));
        message(p, "开始接受" + npc.getName().getString() + "的" + AptitudeRuntime.name(id) + "指导，请留在老师附近。");
    }
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof NpcEntity npc)
                || !event.getItemStack().is(ModItems.CULTIVATION_HANDBOOK.get()) || !eligible(p) || p.distanceToSqr(npc) > Math.pow(AptitudeRules.get().growth("teaching_range"), 2)) return;
        CultivationService.initializeAptitudes(p);
        if (!npc.isAlive() || npc.getTarget() != null || npc.combatEngaged()) return;
        p.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new AptitudeTeachingMenu(id, inventory, npc), Component.literal(npc.getName().getString() + " · 资质授业")));
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
    }
    private static boolean valid(ServerPlayer p, AptitudeTraining course) {
        return eligible(p) && AptitudeRuntime.clock(p) <= course.deadline()
                && p.level().dimension().location().toString().equals(course.dimension());
    }
    private static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            landTick(p);
            var course = CultivationService.aptitudes(p).training().orElse(null);
            if (course == null) continue;
            if (!valid(p, course)) { stop(p); message(p, "本次研习已结束。"); continue; }
            if (!course.kind().equals("teaching")) continue;
            NpcEntity teacher = p.serverLevel().getEntitiesOfClass(NpcEntity.class, p.getBoundingBox().inflate(AptitudeRules.get().growth("teaching_range")),
                    npc -> npc.isAlive() && (course.teacher() >= 0 ? npc.ledgerPersonId() == course.teacher() : npc.getUUID().toString().equals(course.target())))
                    .stream().findFirst().orElse(null);
            if (teacher == null || teacher.getTarget() != null) { stop(p); message(p, "离开老师后，本次授业中止。"); continue; }
            var next = course.advance(1);
            if (next.progress() >= next.required()) finish(p, next); else installSilent(p, next);
        }
    }
    private static void installSilent(ServerPlayer p, AptitudeTraining course) {
        CultivationService.replaceAptitudes(p, CultivationService.aptitudes(p).withTraining(Optional.of(course)), course.progress() % 20 == 0);
    }
    private static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer dead) { stop(dead); return; }
        CompoundTag tag = event.getEntity().getPersistentData(); if (!tag.hasUUID(OWNER)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !tag.getUUID(OWNER).equals(p.getUUID())) return;
        var course = CultivationService.aptitudes(p).training().orElse(null);
        if (course == null || !course.kind().equals("memory") || !valid(p, course) || !course.target().equals(tag.getString(COURSE))) return;
        String family = CombatStyles.bundled().weapon(p.getMainHandItem()).flatMap(w -> w.family()).orElse("");
        if (!family.equals(course.aptitude())) return;
        var next = course.advance(1);
        if (next.progress() >= next.required()) finish(p, next); else { install(p, next); message(p, "战意试炼：" + next.progress() + " / " + next.required()); }
    }
    private static void guardDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            var attackerData = attacker.getPersistentData();
            if (attackerData.hasUUID(OWNER) && !attackerData.getUUID(OWNER).equals(event.getEntity().getUUID())) {
                event.setCanceled(true); return;
            }
        }
        var tag = event.getEntity().getPersistentData(); if (!tag.hasUUID(OWNER)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !tag.getUUID(OWNER).equals(p.getUUID())
                || !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) { event.setCanceled(true); return; }
        var course = CultivationService.aptitudes(p).training().orElse(null);
        String family = CombatStyles.bundled().weapon(p.getMainHandItem()).flatMap(w -> w.family()).orElse("");
        if (course == null || !course.kind().equals("memory") || !course.target().equals(tag.getString(COURSE))
                || !family.equals(course.aptitude())) event.setCanceled(true);
    }
    private static void guardTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob) || mob.level().isClientSide()) return;
        var tag = mob.getPersistentData(); if (!tag.hasUUID(OWNER)) return;
        var owner = mob.level().getServer().getPlayerList().getPlayer(tag.getUUID(OWNER));
        var course = owner == null ? null : CultivationService.aptitudes(owner).training().orElse(null);
        if (course == null || !course.kind().equals("memory") || !course.target().equals(tag.getString(COURSE))
                || !valid(owner, course) || owner.level() != mob.level()) mob.discard();
        else mob.setTarget(owner);
    }
    private static void finish(ServerPlayer p, AptitudeTraining course) {
        if (!valid(p, course)) return;
        CultivationService.replaceAptitudes(p, CultivationService.aptitudes(p).withTraining(Optional.empty()), false);
        cleanup(p);
        int gain = Math.max(0, Math.min(course.reward(), course.ceiling() - AptitudeRuntime.score(p, course.aptitude())));
        boolean awarded = AptitudeRuntime.add(p, course.aptitude(), gain, switch(course.kind()) {
            case "teaching" -> "名师授业"; case "memory" -> "战意传承"; default -> "完成研究";
        });
        if (awarded && course.kind().equals("research") && course.target().startsWith("craft|")) {
            String recipe = course.target().substring("craft|".length());
            if (com.example.myvillage.alchemy.AlchemyKnowledge.learn(p, recipe)) message(p, "已根据手记复原丹方：" + recipe);
        }
    }
    private static void cleanup(ServerPlayer p) {
        int radius = AptitudeRules.get().growth("memory_radius");
        for (ServerLevel level : p.getServer().getAllLevels()) for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(p.blockPosition()).inflate(radius), e -> e.getPersistentData().hasUUID(OWNER)
                        && e.getPersistentData().getUUID(OWNER).equals(p.getUUID()))) mob.discard();
    }
    private static void repairAtAnvil(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !eligible(p) || !p.isShiftKeyDown()
                || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).toString().equals("myvillage:forging_anvil")) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof com.example.myvillage.block.entity.ForgingAnvilBlockEntity anvil)
                || !anvil.available()) return;
        var course = CultivationService.aptitudes(p).training().orElse(null);
        ItemStack original = event.getItemStack();
        if (course == null || !valid(p, course) || !course.kind().equals("research") || !course.aptitude().equals("forging")
                || course.target().startsWith("craft") || !original.isDamaged()
                || !original.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("myvillage_research").equals(course.target())) return;
        ItemStack result = original.copy();
        result.setDamageValue(Math.max(0,result.getDamageValue()-com.example.myvillage.forging.ForgedEquipment.repairAmount(result)));
        var source = com.example.myvillage.spiritstone.SpiritStoneSources.open(p);
        var plan = com.example.myvillage.forging.ForgingCosts.plan(source.copies(),com.example.myvillage.forging.ForgingRules.get().repairCosts());
        if (plan.isEmpty() || !source.commit(plan.orElseThrow())) { reject(p,"修复课题所需材料不足。"); return; }
        p.setItemInHand(event.getHand(), result); p.getInventory().setChanged();
        AptitudeRuntime.equipmentCompleted(p,result,true);
        if (result.isDamaged()) message(p,"本次修复完成，器物仍有损伤，可以继续修复。");
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
    }
    private static void landInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getItemStack().is(ModItems.CULTIVATION_HANDBOOK.get())
                || !p.isShiftKeyDown() || !eligible(p)) return;
        String element = land(p.serverLevel(), event.getPos()); if (element.isEmpty()) return;
        if (LAND.containsKey(p.getUUID())) { reject(p, "已开始灵地炼化，请先完成或使用 /资质 停止。"); return; }
        CultivationService.initializeAptitudes(p);
        var state = CultivationService.aptitudes(p); long now = AptitudeRuntime.clock(p);
        if (state.receipts().getOrDefault("land/" + element, 0L) > now) { reject(p, "这一系灵气尚未重新凝聚。"); return; }
        var r = AptitudeRules.get();
        if (!pay(p, r.growth("land_fuel"))) { reject(p, "炼化需要下品灵石。"); return; }
        int required = r.growth("land_duration_ticks");
        // 单次启动记录位置和完成时刻，后续 tick 只在原灵地、静止且仍处于打坐状态时推进。
        LAND.put(p.getUUID(), new Land(element, event.getPos().immutable(), p.position(), 0,
                now + r.growth("memory_duration_ticks"), p.level().dimension().location().toString()));
        message(p, "已引入" + AptitudeRuntime.name(element) + "灵气，请在原地打坐完成炼化。");
        event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
    }
    private record Land(String element, BlockPos source, net.minecraft.world.phys.Vec3 origin, int progress, long deadline, String dimension) { }
    private static final Map<UUID, Land> LAND = new java.util.HashMap<>();
    static String land(ServerLevel level, BlockPos pos) {
        var block = level.getBlockState(pos);
        if (block.is(Blocks.MAGMA_BLOCK) && nearby(level, pos, "lava")) return "fire";
        if (block.is(Blocks.BLUE_ICE) && nearby(level, pos, "water")) return "water";
        if (block.is(Blocks.MOSS_BLOCK) && nearby(level, pos, "wood")) return "wood";
        if (block.is(Blocks.RAW_IRON_BLOCK) && nearby(level, pos, "metal")) return "metal";
        if (block.is(Blocks.DRIPSTONE_BLOCK) && nearby(level, pos, "earth")) return "earth";
        return "";
    }
    private static boolean nearby(ServerLevel level, BlockPos pos, String type) {
        int count = 0; var rules = AptitudeRules.get(); int radius = rules.growth("land_scan_radius");
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-radius,-rules.growth("land_scan_below"),-radius), pos.offset(radius,rules.growth("land_scan_above"),radius))) {
            if (!level.hasChunkAt(at)) continue;
            var state = level.getBlockState(at);
            boolean match = switch(type) {
                case "lava" -> state.getFluidState().is(Fluids.LAVA);
                case "water" -> state.getFluidState().is(Fluids.WATER);
                case "wood" -> state.is(net.minecraft.tags.BlockTags.LOGS);
                case "metal" -> state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE);
                default -> state.is(Blocks.POINTED_DRIPSTONE);
            };
            if (match && ++count >= rules.growth("land_required_blocks")) return true;
        }
        return false;
    }
    public static void landTick(ServerPlayer p) {
        var work = LAND.get(p.getUUID()); if (work == null) return;
        if (!eligible(p) || !p.level().dimension().location().toString().equals(work.dimension())
                || AptitudeRuntime.clock(p) > work.deadline() || p.position().distanceToSqr(work.origin()) > Math.pow(AptitudeRules.get().growth("land_move_distance"), 2)
                || !land(p.serverLevel(), work.source()).equals(work.element())) { LAND.remove(p.getUUID()); return; }
        var meditation = com.example.myvillage.cultivation.meditation.MeditationManager.status(p);
        if (!meditation.state().meditating() || meditation.study().isPresent() || meditation.practice().isPresent()) return;
        int next = work.progress() + 1;
        if (next < AptitudeRules.get().growth("land_duration_ticks")) {
            LAND.put(p.getUUID(), new Land(work.element(), work.source(), work.origin(), next, work.deadline(), work.dimension()));
            return;
        }
        LAND.remove(p.getUUID());
        if (AptitudeRuntime.add(p, work.element(), AptitudeRules.get().growth("land_gain"), "灵地炼化")) {
            var state = CultivationService.aptitudes(p);
            CultivationService.replaceAptitudes(p, state.counter(true, "land/" + work.element(),
                    AptitudeRuntime.clock(p) + AptitudeRules.get().growth("land_cooldown_ticks")), true);
        }
    }
    private static boolean reject(ServerPlayer p, String text) { message(p, text); return false; }
    private static void message(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), false); }
}
