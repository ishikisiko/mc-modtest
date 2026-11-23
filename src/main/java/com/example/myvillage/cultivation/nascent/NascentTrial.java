package com.example.myvillage.cultivation.nascent;

import com.example.myvillage.alchemy.MedicineRuntime;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationRecovery;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.network.NascentStatusPayload;
import com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.NascentDemon;
import com.example.myvillage.item.BeastMaterialItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The server owns the attempt, the crafting payment, and the one resulting quality. */
public final class NascentTrial {
    private static final String KEY = "yuanxian_nascent_trial";
    private static final ResourceLocation GOLDEN_LATE = ResourceLocation.parse("yuanxian:golden_late");
    private static final Map<UUID, NascentDemon> DEMONS = new HashMap<>();

    private NascentTrial() { }

    public enum Phase {
        INACTIVE, REFINING, DUEL, READY, CONDENSING;

        public boolean sitting() { return this == REFINING || this == CONDENSING; }
    }

    public static boolean source(CultivationProfile profile) {
        return GOLDEN_LATE.equals(profile.stageId()) && ModCultivationRegistries.GOLDEN_CORE_REALM_ID.equals(profile.realmId());
    }

    public static boolean canChoose(RegistryAccess access, CultivationProfile profile) {
        return source(profile) && !CultivationRecovery.canRestore(access, profile);
    }

    public static boolean active(ServerPlayer player) { return player.getPersistentData().contains(KEY); }

    private static RealmStageDefinition sourceStage(ServerPlayer player) {
        var realm = MeditationManager.realmLookup(player).apply(ModCultivationRegistries.GOLDEN_CORE_REALM_ID);
        if (realm == null) return null;
        return realm.stages().stream().filter(stage -> stage.id().equals(GOLDEN_LATE)).findFirst().orElse(null);
    }

    /** Null means the player may start a new route. Consuming a pill checks this again after swallowing. */
    public static String rejection(ServerPlayer player) {
        var profile = CultivationService.getProfile(player);
        if (!source(profile)) return "凝婴仅用于金丹后期突破元婴。";
        if (!canChoose(player.registryAccess(), profile)) return "补满修为后按 N 恢复原境界。";
        if (MeditationManager.active(player) || CombatSessionManager.hudBusy(player))
            return "请先结束当前修炼或战斗。";
        if (player.containerMenu != player.inventoryMenu || !player.onGround()
                || MeditationManager.breakthroughEligibility(player) != MeditationStopReason.NONE)
            return "请落地、脱战并结束手中操作，再准备结婴。";
        RealmStageDefinition stage = sourceStage(player);
        if (stage == null || stage.cultivationCap().isEmpty() || stage.advancement().isEmpty())
            return "当前凝婴突破定义不可用。";
        var advance = stage.advancement().orElseThrow();
        if (CultivationService.validateRealmAndStage(player.registryAccess(), advance.targetRealm(), advance.targetStage()).isPresent())
            return "元婴阶段定义不可用。";
        if (profile.cultivationProgress() < stage.cultivationCap().orElseThrow()
                || profile.stability() < advance.requiredStability())
            return "结婴需要 " + stage.cultivationCap().orElseThrow() + " 修为、" + advance.requiredStability() + " 稳定度。";
        return null;
    }

    public static boolean swallow(ServerPlayer player, ItemStack stack, int quality) {
        String reason = rejection(player);
        if (reason != null) { tell(player, reason); return false; }
        var medicine = MedicineRuntime.state(player, "ningying");
        if (medicine.cooldownTicks() > 0 || medicine.effectTicks() > 0) {
            tell(player, "尚未达到下次服药时间。");
            return false;
        }
        store(player, Attempt.start(player, Phase.CONDENSING, 1, NascentRules.get().pillTicks(quality)));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        MedicineRuntime.beginCooldown(player, "ningying");
        publish(player);
        tell(player, "凝婴丹已护住神魂。请保持静坐，直至下品元婴成形。");
        return true;
    }

    public static void refine(ServerPlayer player) {
        String reason = rejection(player);
        if (reason != null) { tell(player, reason); return; }
        ItemStack material = player.getMainHandItem();
        if (!isSpiritMaterial(player, material)) {
            tell(player, "请把一枚金丹境以上且不凡以上的妖丹拿在主手。");
            return;
        }
        var profile = CultivationService.getProfile(player);
        var core = refiningCore(player, profile);
        if (core.isEmpty()) {
            tell(player, "请先装备一门当前境界可运转的主修心法。");
            return;
        }
        long qi = qiCost(player);
        if (profile.currentSpiritualPower() < qi) {
            tell(player, "炼化并碎丹时需消耗半槽灵力，请先回灵。");
            return;
        }
        var start = Attempt.start(player, Phase.REFINING, 2, NascentRules.get().value("refinement_ticks"));
        store(player, start.reserve(player.getInventory().selected, material.copyWithCount(1), qi, core.orElseThrow()));
        publish(player);
        tell(player, "以主修心法炼化手中妖丹。炼成时会消耗妖丹和半槽灵力，随后碎丹。");
    }

    public static void duel(ServerPlayer player) {
        String reason = rejection(player);
        if (reason != null) { tell(player, reason); return; }
        var attempt = Attempt.start(player, Phase.DUEL, 3, NascentRules.get().value("duel_ticks"));
        if (!spawn(player, attempt)) {
            tell(player, "附近没有能站立的心魔出生点，请去空旷平地。");
            return;
        }
        store(player, attempt);
        publish(player);
        tell(player, "心魔已现身。请在 " + attempt.duration / 20 + " 秒内亲手斩灭它。");
    }

    public static void confirm(ServerPlayer player) {
        var attempt = read(player);
        if (attempt == null || attempt.phase != Phase.READY) return;
        if (!canChoose(player.registryAccess(), CultivationService.getProfile(player))) {
            clear(player);
            return;
        }
        if (!atDuelSite(player, attempt)) {
            fail(player, attempt, "离开心魔斗法范围");
            return;
        }
        if (!player.onGround() || player.isPassenger() || player.isUsingItem()
                || !player.gameMode.isSurvival() || player.isFallFlying() || !player.isAlive()
                || player.isSleeping() || player.containerMenu != player.inventoryMenu) {
            tell(player, "停稳、落地后按 N 自行碎丹。");
            return;
        }
        int ticks = NascentRules.get().value("duel_condensation_ticks");
        store(player, attempt.condense(ticks, player.position()));
        CombatSessionManager.interrupt(player, CombatStopReason.CULTIVATION_STARTED, true);
        publish(player);
        tell(player, "心魔已斩。请原地静坐，以自身神魂承接丹元。");
    }

    public static boolean owns(ServerPlayer player, NascentDemon demon) {
        return DEMONS.get(player.getUUID()) == demon && active(player) && player.isAlive();
    }

    public static void observeTechnique(ServerPlayer player) {
        NascentDemon demon = DEMONS.get(player.getUUID());
        if (demon != null && demon.isAlive()) demon.observeTechnique(player);
    }

    public static void demonDefeated(NascentDemon demon, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !owns(player, demon)) return;
        var attempt = read(player);
        if (attempt == null || attempt.phase != Phase.DUEL) return;
        DEMONS.remove(player.getUUID());
        int ticks = NascentRules.get().value("confirmation_ticks");
        store(player, attempt.next(Phase.READY, ticks, 0));
        publish(player);
        tell(player, "心魔已斩。请在 " + ticks / 20 + " 秒内停稳并按 N 自行碎丹。");
    }

    public static void tick(ServerPlayer player) {
        var attempt = read(player);
        if (!player.isAlive()) return;
        if (attempt == null) {
            if (active(player)) clear(player);
            return;
        }
        var profile = CultivationService.getProfile(player);
        if (!canChoose(player.registryAccess(), profile)) { clear(player); return; }
        if (!player.level().dimension().location().equals(attempt.dimension) || !player.gameMode.isSurvival()) {
            fail(player, attempt, "离开结婴现场");
            return;
        }
        if (attempt.phase.sitting() && (player.position().distanceToSqr(attempt.anchor) > .01 * .01
                || player.isPassenger() || player.isUsingItem() || player.swinging
                || player.isSleeping() || player.containerMenu != player.inventoryMenu)) {
            fail(player, attempt, "运功中断");
            return;
        }
        if ((attempt.phase == Phase.DUEL || attempt.phase == Phase.READY) && !atDuelSite(player, attempt)) {
            fail(player, attempt, "离开心魔斗法范围");
            return;
        }
        if (attempt.phase == Phase.DUEL) {
            var demon = DEMONS.get(player.getUUID());
            if (demon == null || demon.isRemoved()) {
                if (!spawn(player, attempt)) { fail(player, attempt, "无法恢复心魔站位"); return; }
                demon = DEMONS.get(player.getUUID());
            }
            attempt = attempt.health(demon.getHealth());
        }
        if (attempt.remaining <= 1) {
            switch (attempt.phase) {
                case REFINING -> finishRefinement(player, profile, attempt);
                case CONDENSING -> finish(player, profile, attempt);
                case DUEL, READY -> fail(player, attempt, "本次结婴超时");
                default -> clear(player);
            }
            return;
        }
        store(player, attempt.elapse());
        if ((attempt.remaining - 1) % 20 == 0) {
            publish(player);
            if (attempt.phase == Phase.DUEL)
                tell(player, "心魔 " + (int) Math.ceil(attempt.health) + " 生命 · 剩余 " + (attempt.remaining / 20) + " 秒");
        }
    }

    private static void finishRefinement(ServerPlayer player, CultivationProfile profile, Attempt attempt) {
        ItemStack current = player.getMainHandItem();
        if (player.getInventory().selected != attempt.slot || current.isEmpty()
                || !ItemStack.isSameItemSameComponents(current, attempt.material)
                || !refiningCore(player, profile).equals(attempt.core)) {
            fail(player, attempt, "炼化用的妖丹或主修心法已改变");
            return;
        }
        if (!CultivationService.spendSpiritualPower(player, attempt.qi)) {
            fail(player, attempt, "灵力已不足");
            return;
        }
        if (!player.getAbilities().instabuild) current.shrink(1);
        player.getInventory().setChanged();
        store(player, attempt.condense(NascentRules.get().value("spirit_condensation_ticks"), player.position()));
        publish(player);
        tell(player, "妖丹已炼成，开始碎丹。请原地静坐至元婴成形。");
    }

    private static void finish(ServerPlayer player, CultivationProfile profile, Attempt attempt) {
        var stage = sourceStage(player);
        var advance = stage == null ? null : stage.advancement().orElse(null);
        if (advance == null || stage.cultivationCap().isEmpty()
                || profile.cultivationProgress() < stage.cultivationCap().orElseThrow()
                || profile.stability() < advance.requiredStability()) {
            fail(player, attempt, "结婴条件已改变");
            return;
        }
        var next = profile.withRealmAndStage(advance.targetRealm(), advance.targetStage())
                .withCultivationProgress(0).withStability(profile.stability() / 2)
                .withMemory(profile.memory().form(attempt.quality));
        if (!CultivationService.replaceProfile(player, next).success()) {
            fail(player, attempt, "结婴结算失败");
            return;
        }
        clear(player);
        player.sendSystemMessage(Component.literal("碎丹结婴成功，已成" + qualityName(attempt.quality) + "元婴，获得一次元婴护境。"));
    }

    public static String qualityName(int quality) {
        return switch (quality) { case 1 -> "下品"; case 2 -> "中品"; case 3 -> "上品"; default -> "尚未结婴"; };
    }

    public static boolean interrupt(ServerPlayer player, MeditationStopReason reason) {
        var attempt = read(player);
        if (attempt == null) return false;
        if (reason == MeditationStopReason.DIED) { clear(player); return true; }
        if (reason == MeditationStopReason.REQUESTED || reason == MeditationStopReason.DIMENSION_CHANGED
                || reason == MeditationStopReason.GAME_MODE_CHANGED
                || attempt.phase.sitting() && (reason == MeditationStopReason.DAMAGED || reason == MeditationStopReason.JUMPED
                  || reason == MeditationStopReason.INTERACTED || reason == MeditationStopReason.ATTACKED
                  || reason == MeditationStopReason.MINING || reason == MeditationStopReason.MOUNTED)) {
            fail(player, attempt, "本次结婴中断");
            return true;
        }
        return false;
    }

    private static void fail(ServerPlayer player, Attempt attempt, String reason) {
        clear(player);
        var profile = CultivationService.getProfile(player);
        var stage = sourceStage(player);
        if (attempt.phase != Phase.REFINING && source(profile) && stage != null && stage.advancement().isPresent()) {
            int required = stage.advancement().orElseThrow().requiredStability();
            var rules = NascentRules.get();
            int percent = rules.value(attempt.quality == 1 ? "pill_failure_stability_percent" : "committed_failure_stability_percent");
            int loss = (int) ((long) required * percent / 100);
            var replacement = profile.withStability(Math.max(0, profile.stability() - loss));
            if (attempt.quality == 3) {
                long cap = stage.cultivationCap().orElse(0L);
                long progressLoss = cap * rules.value("duel_failure_progress_percent") / 100;
                replacement = replacement.withCultivationProgress(Math.max(0L, profile.cultivationProgress() - progressLoss));
            }
            CultivationService.replaceProfile(player, replacement);
        }
        player.sendSystemMessage(Component.literal(reason + "。"));
    }

    public static void logout(ServerPlayer player) {
        NascentDemon demon = DEMONS.remove(player.getUUID());
        if (demon == null) return;
        var attempt = read(player);
        if (attempt != null && attempt.phase == Phase.DUEL && demon.isAlive()) store(player, attempt.health(demon.getHealth()));
        demon.discard();
    }

    public static void reset() {
        DEMONS.values().forEach(NascentDemon::discard);
        DEMONS.clear();
    }

    private static void clear(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
        NascentDemon demon = DEMONS.remove(player.getUUID());
        if (demon != null) demon.discard();
        publish(player);
    }

    public static Optional<MeditationStatus> sittingStatus(ServerPlayer player) {
        var attempt = read(player);
        return attempt == null || !attempt.phase.sitting() ? Optional.empty()
                : Optional.of(new MeditationStatus(MeditationState.ADVANCING_ORDINARY, 0, MeditationStopReason.NONE,
                        Optional.of(AdvancementKind.ORDINARY), attempt.duration, attempt.remaining));
    }

    public static void publish(ServerPlayer player) {
        var attempt = read(player);
        PacketDistributor.sendToPlayer(player, new NascentStatusPayload(attempt == null ? Phase.INACTIVE : attempt.phase,
                attempt == null ? 0 : attempt.remaining));
        MeditationManager.notifyStatus(player);
    }

    private static boolean isSpiritMaterial(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof BeastMaterialItem material)) return false;
        var variant = material.variant();
        if (!variant.family().equals("beast_core") || variant.rarity().equals("common")) return false;
        var lookup = MeditationManager.realmLookup(player);
        var realm = lookup.apply(ResourceLocation.parse(variant.realm()));
        var golden = lookup.apply(ModCultivationRegistries.GOLDEN_CORE_REALM_ID);
        return realm != null && golden != null && realm.sortOrder() >= golden.sortOrder();
    }

    private static Optional<ResourceLocation> refiningCore(ServerPlayer player, CultivationProfile profile) {
        return profile.activeCoreTechnique().filter(id -> profile.learnedTechniques().containsKey(id)
                && ModCultivationRegistries.technique(player.registryAccess(), id)
                .filter(definition -> definition.isCore()
                        && TechniqueRequirementEvaluator.evaluate(player.registryAccess(), profile, definition).satisfied())
                .isPresent());
    }

    private static boolean atDuelSite(ServerPlayer player, Attempt attempt) {
        int radius = NascentRules.get().value("duel_radius");
        return player.level().dimension().location().equals(attempt.dimension)
                && player.position().distanceToSqr(attempt.duelCenter) <= radius * (double) radius;
    }

    private static long qiCost(ServerPlayer player) {
        return Math.max(1, (long) Math.ceil(CultivationService.spiritualPowerCap(player)
                * (NascentRules.get().value("refinement_qi_percent") / 100.0)));
    }

    private static boolean spawn(ServerPlayer player, Attempt attempt) {
        var level = player.serverLevel();
        var demon = ModEntities.NASCENT_DEMON.get().create(level);
        if (demon == null) return false;
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6.0;
            Vec3 pos = attempt.duelCenter.add(4 * Math.cos(angle), 0, 4 * Math.sin(angle));
            BlockPos floor = BlockPos.containing(pos.x, pos.y - .1, pos.z);
            demon.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180, 0);
            if (!level.hasChunkAt(floor) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                    || !level.noCollision(demon) || level.containsAnyLiquid(demon.getBoundingBox())) continue;
            demon.bind(player, attempt.health);
            if (!level.addFreshEntity(demon)) return false;
            DEMONS.put(player.getUUID(), demon);
            return true;
        }
        demon.discard();
        return false;
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static Attempt read(ServerPlayer player) {
        if (!active(player)) return null;
        var tag = player.getPersistentData().getCompound(KEY);
        int id = tag.getInt("phase");
        int duration = tag.getInt("duration");
        int remaining = tag.getInt("remaining");
        var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (id <= 0 || id >= Phase.values().length || duration < 1 || remaining < 1
                || remaining > duration || dimension == null) return null;
        Phase phase = Phase.values()[id];
        int quality = tag.getInt("quality");
        int slot = tag.getInt("slot");
        Optional<ResourceLocation> core = tag.contains("core", Tag.TAG_STRING)
                ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString("core"))) : Optional.empty();
        if (quality < 1 || quality > 3
                || phase == Phase.REFINING && (quality != 2 || !Inventory.isHotbarSlot(slot) || core.isEmpty())
                || (phase == Phase.READY || phase == Phase.DUEL) && quality != 3
                || tag.getLong("qi") < 0) return null;
        return new Attempt(phase, quality, dimension,
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                new Vec3(tag.getDouble("center_x"), tag.getDouble("center_y"), tag.getDouble("center_z")),
                duration, remaining, slot, ItemStack.parseOptional(player.registryAccess(), tag.getCompound("material")),
                tag.getLong("qi"), core, tag.getFloat("health"));
    }

    private static void store(ServerPlayer player, Attempt attempt) {
        var tag = new CompoundTag();
        tag.putInt("phase", attempt.phase.ordinal());
        tag.putInt("quality", attempt.quality);
        tag.putString("dimension", attempt.dimension.toString());
        tag.putDouble("x", attempt.anchor.x);
        tag.putDouble("y", attempt.anchor.y);
        tag.putDouble("z", attempt.anchor.z);
        tag.putDouble("center_x", attempt.duelCenter.x);
        tag.putDouble("center_y", attempt.duelCenter.y);
        tag.putDouble("center_z", attempt.duelCenter.z);
        tag.putInt("duration", attempt.duration);
        tag.putInt("remaining", attempt.remaining);
        tag.putInt("slot", attempt.slot);
        if (!attempt.material.isEmpty()) tag.put("material", attempt.material.save(player.registryAccess()));
        tag.putLong("qi", attempt.qi);
        attempt.core.ifPresent(id -> tag.putString("core", id.toString()));
        tag.putFloat("health", attempt.health);
        player.getPersistentData().put(KEY, tag);
    }

    private record Attempt(Phase phase, int quality, ResourceLocation dimension, Vec3 anchor, Vec3 duelCenter,
            int duration, int remaining, int slot, ItemStack material, long qi, Optional<ResourceLocation> core, float health) {
        static Attempt start(ServerPlayer player, Phase phase, int quality, int ticks) {
            return new Attempt(phase, quality, player.level().dimension().location(), player.position(), player.position(),
                    ticks, ticks, -1, ItemStack.EMPTY, 0, Optional.empty(), NascentRules.get().value("demon_health"));
        }

        Attempt reserve(int slot, ItemStack material, long qi, ResourceLocation core) {
            return new Attempt(phase, quality, dimension, anchor, duelCenter, duration, remaining, slot, material, qi,
                    Optional.of(core), health);
        }

        Attempt elapse() {
            return new Attempt(phase, quality, dimension, anchor, duelCenter, duration, remaining - 1, slot, material, qi, core, health);
        }

        Attempt health(float value) {
            return new Attempt(phase, quality, dimension, anchor, duelCenter, duration, remaining, slot, material, qi, core, value);
        }

        Attempt next(Phase target, int ticks, float health) {
            return new Attempt(target, quality, dimension, anchor, duelCenter, ticks, ticks, -1, ItemStack.EMPTY, 0, Optional.empty(), health);
        }

        Attempt condense(int ticks, Vec3 position) {
            return new Attempt(Phase.CONDENSING, quality, dimension, position, duelCenter, ticks, ticks, -1, ItemStack.EMPTY, 0, Optional.empty(), 0);
        }
    }
}
