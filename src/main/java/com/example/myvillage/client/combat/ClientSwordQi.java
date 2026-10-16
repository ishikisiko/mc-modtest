package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.client.cultivation.QiBarLayer;
import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.CombatChargeIntentPayload;
import com.example.myvillage.combat.network.CombatCancelChargeIntentPayload;
import com.example.myvillage.combat.network.CombatReleaseIntentPayload;
import com.example.myvillage.combat.network.CombatSkillReceiver;
import com.example.myvillage.combat.network.CombatSkillStatePayload;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.HashMap;
import java.util.Map;

/** Input edges and server-driven presentation. No hit, resource or charge authority lives here. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSwordQi {
    private record Charge(ResourceLocation skill, long localStart, long revision) { }
    record ChargePose(CombatStyles.MoveRef move, float tick, float holdTick) { }

    static java.util.Optional<ChargePose> chargePose(int entityId, float partialTick) {
        var charge = CHARGES.get(entityId);
        if (charge == null) return java.util.Optional.empty();
        return CombatStyles.bundled().skill(charge.skill()).filter(skill -> skill.chargePoseTicks() > 0)
                .flatMap(skill -> CombatStyles.bundled().move(skill.feedbackMove()).map(move ->
                        new ChargePose(move, (float) skill.chargePoseTick(ClientCombatClock.now(partialTick) - charge.localStart()),
                                skill.chargePoseTicks())));
    }

    static int releaseAnimationOffset(int entityId, long revision) {
        var release = RELEASES.get(entityId);
        if (release == null || release.revision() != revision) return 0;
        return CombatStyles.bundled().skill(release.skillId())
                .map(skill -> skill.releaseAnimationOffset(release.chargeLevel())).orElse(0);
    }

    /** Capture the held hand before removing its pose; cancellation blends back, release continues it. */
    private static void endCharge(int entityId, boolean released) {
        var charge = CHARGES.get(entityId);
        if (charge == null) return;
        var mc = Minecraft.getInstance();
        var entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            FirstPersonWeaponAnimator.leaveCharge(player);
            CHARGES.remove(entityId);
            if (!released) CombatStyles.bundled().styleFor(player.getMainHandItem())
                    .ifPresent(style -> CombatAnimationController.transition(player, style.readyIdleAnimation()));
        } else CHARGES.remove(entityId);
    }
    private static final Map<Integer, Charge> CHARGES = new HashMap<>();
    private static final Map<Integer, Long> REVISIONS = new HashMap<>();
    private static final Map<Integer, CombatSkillStatePayload> RELEASES = new HashMap<>();
    private static final QiUseLatch USE = new QiUseLatch();
    private static long refusedAt = Long.MIN_VALUE;

    private ClientSwordQi() { }

    public static void install() {
        CombatSkillReceiver.install(ClientSwordQi::receive);
    }

    /** Claim use only for an actually learned skill of this weapon's family. */
    private static boolean hasSkill() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        var weapon = CombatStyles.bundled().weapon(mc.player.getMainHandItem()).orElse(null);
        var profile = ClientCultivationState.latest().orElse(null);
        if (weapon == null || weapon.family().isEmpty() || profile == null) return false;
        for (var id : profile.learnedTechniques().keySet()) {
            var technique = ModCultivationRegistries.technique(mc.level.registryAccess(), id).orElse(null);
            if (technique == null || technique.effects().isEmpty()) continue;
            var active = technique.effects().get().active().orElse(null);
            if (active == null || CombatStyles.bundled().skill(active.skill()).isEmpty()) continue;
            var school = technique.school().flatMap(s -> ModCultivationRegistries.school(mc.level.registryAccess(), s));
            if (school.flatMap(s -> s.weaponFamily()).equals(weapon.family())) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.isWindowActive() || !mc.player.isAlive()
                || ClientCombatState.mode() != CombatMode.CULTIVATION) return;
        if (event.isAttack() && chargingLocal()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || !hasSkill()) return;
        // Preserve the ordinary interaction path for shelves, steles and NPC dialogue.
        if (mc.hitResult != null && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult block
                    && mc.level != null && mc.level.getBlockState(block.getBlockPos()).hasBlockEntity()) return;
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entity
                    && entity.getEntity() instanceof com.example.myvillage.entity.npc.NpcEntity) return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (USE.press()) {
            PacketDistributor.sendToServer(CombatChargeIntentPayload.INSTANCE);
        }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null || !mc.isWindowActive()) {
            if (USE.cancel()) PacketDistributor.sendToServer(CombatCancelChargeIntentPayload.INSTANCE);
            USE.release(mc.options.keyUse.isDown());
            return;
        }
        if (USE.release(mc.options.keyUse.isDown())) {
            PacketDistributor.sendToServer(CombatReleaseIntentPayload.INSTANCE);
        }
        long now = ClientCombatClock.ticks();
        for (var entry : java.util.List.copyOf(CHARGES.entrySet())) {
            var skill = CombatStyles.bundled().skill(entry.getValue().skill()).orElse(null);
            if (skill == null || now - entry.getValue().localStart() > skill.chargeTicks() + 40)
                endCharge(entry.getKey(), false);
        }
        for (var entry : CHARGES.entrySet()) {
            var entity = mc.level.getEntity(entry.getKey());
            if (entity == null) continue;
            double phase = now * 0.7;
            Vec3 center = entity.position().add(0, 1.1, 0);
            mc.level.addParticle(ParticleTypes.ENCHANT,
                    center.x + Math.cos(phase) * 0.55, center.y + 0.2, center.z + Math.sin(phase) * 0.55,
                    -Math.cos(phase) * 0.12, -0.02, -Math.sin(phase) * 0.12);
        }
    }

    private static void receive(CombatSkillStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long previous = REVISIONS.getOrDefault(payload.attackerEntityId(), Long.MIN_VALUE);
        if (payload.revision() < previous) return;
        REVISIONS.put(payload.attackerEntityId(), payload.revision());
        switch (payload.phase()) {
            case CHARGING -> {
                CHARGES.put(payload.attackerEntityId(), new Charge(payload.skillId(),
                        ClientCombatClock.localTickAgo(ClientCombatClock.elapsedSinceServer(payload.serverTick())), payload.revision()));
                var entity = mc.level.getEntity(payload.attackerEntityId());
                if (entity instanceof net.minecraft.client.player.AbstractClientPlayer player)
                    chargePose(player.getId(), 0).ifPresent(pose -> CombatAnimationController.beginCharge(player, pose));
            }
            case RELEASED -> {
                endCharge(payload.attackerEntityId(), true);
                RELEASES.put(payload.attackerEntityId(), payload);
                SwordQiWaveRenderer.released(payload);
            }
            case WAVE -> SwordQiWaveRenderer.update(payload);
            case STOPPED -> {
                endCharge(payload.attackerEntityId(), false);
                RELEASES.remove(payload.attackerEntityId());
                SwordQiWaveRenderer.stop(payload.attackerEntityId());
            }
            default -> {
                endCharge(payload.attackerEntityId(), false);
                if (mc.player != null && payload.attackerEntityId() == mc.player.getId()) refusedAt = ClientCombatClock.ticks();
            }
        }
    }

    public static boolean chargingLocal() {
        var player = Minecraft.getInstance().player;
        return player != null && CHARGES.containsKey(player.getId());
    }

    public static double localCharge() {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        var charge = CHARGES.get(player.getId());
        if (charge == null) return -1;
        return CombatStyles.bundled().skill(charge.skill())
                .map(skill -> Math.clamp((double) (ClientCombatClock.ticks() - charge.localStart()) / skill.chargeTicks(), 0.0, 1.0))
                .orElse(-1.0);
    }

    public static boolean recentlyRefused() {
        return refusedAt != Long.MIN_VALUE && ClientCombatClock.ticks() - refusedAt < 12;
    }

    public static long presentationTick() { return ClientCombatClock.ticks(); }

    public static boolean cultivationMode() { return ClientCombatState.mode() == CombatMode.CULTIVATION; }

    static java.util.Optional<com.example.myvillage.combat.definition.AttackMoveDefinition> releasedMove(int entityId, long revision) {
        var payload = RELEASES.get(entityId);
        if (payload == null || payload.revision() != revision) return java.util.Optional.empty();
        return CombatStyles.bundled().skill(payload.skillId())
                .map(skill -> skill.releaseMove(CombatStyles.bundled(), payload.chargeLevel()));
    }

    static void forgetEntity(int entityId) {
        endCharge(entityId, false);
        RELEASES.remove(entityId);
        REVISIONS.remove(entityId);
        SwordQiWaveRenderer.stop(entityId);
    }

    private static void clear() {
        for (int id : java.util.List.copyOf(CHARGES.keySet())) endCharge(id, false);
        CHARGES.clear();
        REVISIONS.clear();
        RELEASES.clear();
        SwordQiWaveRenderer.clear();
        USE.clear();
        refusedAt = Long.MIN_VALUE;
        QiBarLayer.clear();
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent
    static void clonePlayer(ClientPlayerNetworkEvent.Clone event) { clear(); }

    @SubscribeEvent
    static void entityLeaves(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) forgetEntity(event.getEntity().getId());
    }
}
