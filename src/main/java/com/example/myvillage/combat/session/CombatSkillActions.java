package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.CombatSkillDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.example.myvillage.combat.network.CombatSkillStatePayload;
import com.example.myvillage.combat.network.CombatSkillStatePayload.Phase;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.combat.runtime.CombatFeedbackService;
import com.example.myvillage.combat.runtime.CombatHitResolver;
import com.example.myvillage.combat.runtime.CombatWaveResolver;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Charge/wave metadata. Only CombatSessionManager calls this; no second action authority. */
final class CombatSkillActions {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatSkillActions.class);
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private CombatSkillActions() {}
    record Choice(ResourceLocation technique, int grade, CombatSkillDefinition skill, int baseCost) {}
    private record Entry(CombatSkillSession state, ItemStack held, int slot, ResourceLocation dimension) {}

    static boolean has(ServerPlayer player) { return ENTRIES.containsKey(player.getUUID()); }
    static boolean charging(ServerPlayer player) {
        Entry e = ENTRIES.get(player.getUUID()); return e != null && e.state().charging();
    }
    static Optional<Choice> choose(Collection<ResourceLocation> learned,
            Function<ResourceLocation, Optional<TechniqueDefinition>> lookup,
            Function<ResourceLocation, Optional<String>> schoolFamily,
            Optional<String> heldFamily, CombatStyles styles) {
        if (heldFamily.isEmpty()) return Optional.empty();
        List<Choice> choices = new ArrayList<>();
        for (ResourceLocation id : learned) {
            Optional<TechniqueDefinition> technique = lookup.apply(id);
            if (technique.isEmpty() || technique.get().category() != TechniqueCategory.ACTIVE) continue;
            Optional<TechniqueEffects.Active> active = technique.get().effects().flatMap(TechniqueEffects::active);
            if (active.isEmpty()) continue;
            Optional<CombatSkillDefinition> skill = styles.skill(active.get().skill());
            if (skill.isEmpty() || !technique.get().school().equals(Optional.of(skill.get().school()))
                    || !heldFamily.equals(schoolFamily.apply(skill.get().school()))) continue;
            choices.add(new Choice(id, technique.get().grade(), skill.get(), active.get().qiCost()));
        }
        return choices.stream().sorted(Comparator.comparingInt(Choice::grade).reversed()
                .thenComparing(c -> c.technique().toString())).findFirst();
    }
    static Optional<Choice> choice(ServerPlayer player) {
        CombatStyles styles = CombatStyles.bundled();
        return choose(CultivationService.getProfile(player).learnedTechniques().keySet(),
                id -> ModCultivationRegistries.technique(player.registryAccess(), id),
                id -> ModCultivationRegistries.school(player.registryAccess(), id).flatMap(s -> s.weaponFamily()),
                styles.weapon(player.getMainHandItem()).flatMap(WeaponDefinition::family), styles);
    }
    static boolean start(ServerPlayer player, CombatSession session) {
        Optional<Choice> choice = choice(player);
        if (choice.isEmpty()) return reject(player, "NO_TECHNIQUE");
        Choice c = choice.get();
        long now = player.serverLevel().getGameTime();
        CombatSkillSession state = new CombatSkillSession(c.skill(), c.technique(), c.baseCost(), now, session.nextRevision());
        ENTRIES.put(player.getUUID(), new Entry(state, player.getMainHandItem(), player.getInventory().selected,
                player.level().dimension().location()));
        player.setSprinting(false);
        CombatSessionManager.setCommitment(player, c.skill().movementMultiplier() - 1);
        send(player, state, Phase.CHARGING, player.position().add(0, c.skill().launchHeight(), 0), player.getLookAngle());
        LOGGER.info("SWORD_QI player={} t={} result=charging skill={} revision={}",
                player.getGameProfile().getName(), now, c.skill().id(), state.revision());
        return true;
    }
    static boolean valid(ServerPlayer player) {
        Entry e = ENTRIES.get(player.getUUID());
        return e == null || (e.held() == player.getMainHandItem() && e.slot() == player.getInventory().selected
                && e.dimension().equals(player.level().dimension().location())
                && choice(player).map(c -> c.technique().equals(e.state().technique())).orElse(false));
    }
    static void tickCharge(ServerPlayer player) {
        Entry e = ENTRIES.get(player.getUUID());
        if (e != null && e.state().charging() && e.state().full(player.serverLevel().getGameTime())) {
            CombatSessionManager.handleReleaseIntent(player);
        }
    }
    static Optional<CombatSession.StartEvent> release(ServerPlayer player, CombatSession session) {
        Entry e = ENTRIES.get(player.getUUID());
        if (e == null || !e.state().charging()) return Optional.empty();
        CombatSkillSession state = e.state();
        long now = player.serverLevel().getGameTime();
        long cost = com.example.myvillage.forging.ForgedEquipment.qiCost(player.getMainHandItem(), state.costAt(now));
        if (!CultivationService.spendSpiritualPower(player, cost)) {
            send(player, state, Phase.REJECTED, player.position(), player.getLookAngle());
            stop(player, "POWER");
            session.discardPendingAction();
            reject(player, "POWER");
            CombatSessionManager.clearCommitment(player);
            return Optional.empty();
        }
        state.release(now, player.position().add(0, state.skill().launchHeight(), 0), player.getLookAngle(),
                cost, FateService.effect(player, "skill_refund_fraction"));
        CombatSession.StartEvent start = session.startExternal(state.skill().releaseMove(CombatStyles.bundled(),
                        state.chargeLevel()), now,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()),
                e.dimension(), player.getYRot());
        // Metadata precedes START so the client can use skill timing for the ordinary release clip.
        send(player, state, Phase.RELEASED, state.origin(), state.direction());
        LOGGER.info("SWORD_QI player={} t={} result=released skill={} revision={} charge={} cost={}",
                player.getGameProfile().getName(), now, state.skill().id(), state.revision(), state.chargeLevel(), cost);
        return Optional.of(start);
    }
    static void resolve(ServerPlayer player, CombatSession session) {
        Entry e = ENTRIES.get(player.getUUID());
        if (e == null) return;
        CombatSkillSession state = e.state();
        long now = player.serverLevel().getGameTime();
        if (!state.waveActive(now)) return;
        Vec3 extents = CombatWaveResolver.extents(state.direction(), session.facingYaw(),
                state.skill().halfWidth() * state.scale(), state.skill().halfHeight() * state.scale());
        CombatWaveResolver.Resolution result = CombatWaveResolver.resolve(player, session, state.position(),
                state.nextPosition(), extents, state.skill().maximumTargets());
        var move = session.currentMove();
        List<CombatHitResolver.TargetContact> successful = new ArrayList<>();
        for (CombatWaveResolver.Contact contact : result.contacts()) {
            if (!session.markAttempted(contact.target().getId())) continue;
            CombatDamageService.DamageResult damage = CombatDamageService.apply(player, contact.target(), move, session);
            // Enchantment retaliation/death can synchronously interrupt the owner inside hurt().
            if (ENTRIES.get(player.getUUID()) != e || !session.hasActiveAction()) return;
            if (damage.successful()) {
                long refund = state.claimHitRefund();
                if (refund > 0) {
                    long beforeRefund = CultivationService.getProfile(player).currentSpiritualPower();
                    var refunded = CultivationService.recoverSpiritualPower(player, refund);
                    LOGGER.info("SWORD_QI player={} t={} result=hit_refund skill={} revision={} paid={} refund={} recovered={} success={}",
                            player.getGameProfile().getName(), now, state.skill().id(), state.revision(),
                            state.paidPower(), refund, Math.max(0, refunded.profile().currentSpiritualPower() - beforeRefund),
                            refunded.success());
                }
                successful.add(new CombatHitResolver.TargetContact(contact.target(),
                        state.origin().distanceToSqr(contact.position()), contact.position()));
                LOGGER.info("SWORD_QI player={} t={} result=hit revision={} target={} damage={}",
                        player.getGameProfile().getName(), now, state.revision(), contact.target().getId(), damage.actualDamage());
            }
        }
        CombatFeedbackService.hit(player, move, session.revision(), successful);
        state.advance(result.position(), result.blocked());
        send(player, state, Phase.WAVE, result.position(), state.direction());
        if (state.waveStopped()) {
            LOGGER.info("SWORD_QI player={} t={} result=wave_stop revision={} reason={} x={} y={} z={}",
                    player.getGameProfile().getName(), now, state.revision(), result.blocked() ? "WALL" : "RANGE",
                    result.position().x, result.position().y, result.position().z);
        }
    }
    static void stop(ServerPlayer player, String reason) {
        Entry e = ENTRIES.remove(player.getUUID());
        if (e == null) return;
        CombatSkillSession state = e.state();
        send(player, state, Phase.STOPPED, state.charging() ? player.position() : state.position(),
                state.charging() ? player.getLookAngle() : state.direction());
        LOGGER.info("SWORD_QI player={} t={} result=cancelled reason={} revision={}",
                player.getGameProfile().getName(), player.serverLevel().getGameTime(), reason, state.revision());
    }
    static void remove(UUID id) { ENTRIES.remove(id); }
    static void clear() { ENTRIES.clear(); }
    static boolean reject(ServerPlayer player, String reason) {
        LOGGER.info("SWORD_QI player={} t={} result=rejected reason={}",
                player.getGameProfile().getName(), player.serverLevel().getGameTime(), reason);
        return false;
    }
    private static void send(ServerPlayer player, CombatSkillSession state, Phase phase, Vec3 origin, Vec3 direction) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CombatSkillStatePayload(player.getId(),
                state.skill().id(), state.revision(), player.serverLevel().getGameTime(), phase, origin, direction,
                state.levelAt(player.serverLevel().getGameTime())));
    }
}
