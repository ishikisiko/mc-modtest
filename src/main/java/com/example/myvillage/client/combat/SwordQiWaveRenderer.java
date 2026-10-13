package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.CombatSkillStatePayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/** A luminous crescent between received server positions; never extrapolates through a wall. */
final class SwordQiWaveRenderer {
    private record Wave(Vec3 previous, Vec3 current, Vec3 direction, double width, double updated) { }
    private static final Map<Integer, Wave> WAVES = new HashMap<>();
    private SwordQiWaveRenderer() { }

    static void update(CombatSkillStatePayload payload) {
        var skill = CombatStyles.bundled().skill(payload.skillId()).orElse(null);
        if (skill == null) return;
        var prior = WAVES.get(payload.attackerEntityId());
        Vec3 previous = prior == null ? payload.origin() : prior.current();
        WAVES.put(payload.attackerEntityId(), new Wave(previous, payload.origin(), payload.direction().normalize(),
                skill.halfWidth() * skill.scale(payload.chargeLevel()), ClientCombatClock.ticks()));
    }

    static void stop(int entityId) { WAVES.remove(entityId); }
    static void clear() { WAVES.clear(); }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WAVES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        double now = ClientCombatClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(CombatRenderTypes.WEAPON_TRAIL_TRANSLUCENT);
        WAVES.entrySet().removeIf(entry -> {
            Wave wave = entry.getValue();
            double age = Math.max(0, now - wave.updated());
            if (age > 4 || mc.level.getEntity(entry.getKey()) == null) return true;
            float alpha = (float) Math.clamp((4 - age) / 3, 0, 1);
            Vec3 center = wave.previous().lerp(wave.current(), Math.min(1, age)).subtract(camera);
            Vec3 right = wave.direction().cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1.0e-8) right = new Vec3(1, 0, 0);
            right = right.normalize();
            for (int i = 0; i < 32; i++) {
                double a = i / 16.0 - 1;
                double b = (i + 1) / 16.0 - 1;
                Vec3 frontA = center.add(right.scale(a * wave.width())).subtract(wave.direction().scale(a * a * 0.35));
                Vec3 frontB = center.add(right.scale(b * wave.width())).subtract(wave.direction().scale(b * b * 0.35));
                Vec3 innerA = frontA.subtract(wave.direction().scale(0.42 * (1 - a * a)));
                Vec3 innerB = frontB.subtract(wave.direction().scale(0.42 * (1 - b * b)));
                quad(vertices, innerA, frontA, frontB, innerB, alpha);
            }
            return false;
        });
        buffers.endBatch(CombatRenderTypes.WEAPON_TRAIL_TRANSLUCENT);
    }

    private static void quad(VertexConsumer out, Vec3 innerA, Vec3 frontA, Vec3 frontB, Vec3 innerB, float alpha) {
        vertex(out, innerA, 110, 203, 232, 0);
        vertex(out, frontA, 220, 254, 255, Math.round(230 * alpha));
        vertex(out, frontB, 220, 254, 255, Math.round(230 * alpha));
        vertex(out, innerB, 110, 203, 232, 0);
    }

    private static void vertex(VertexConsumer out, Vec3 p, int r, int g, int b, int a) {
        out.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
