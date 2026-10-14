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
    private record Wave(Vec3 previous, Vec3 current, Vec3 direction, long revision,
                        java.util.List<SwordQiWaveShape.Quad> mesh, double updated) { }
    private static final Map<Integer, Wave> WAVES = new HashMap<>();
    private SwordQiWaveRenderer() { }

    static void update(CombatSkillStatePayload payload) {
        var skill = CombatStyles.bundled().skill(payload.skillId()).orElse(null);
        if (skill == null) return;
        var prior = WAVES.get(payload.attackerEntityId());
        double now = ClientCombatClock.ticks();
        if (prior != null && payload.revision() < prior.revision()) return;
        Vec3 previous = prior != null && SwordQiWaveShape.continuous(prior.revision(), payload.revision(), prior.updated(), now)
                ? prior.current() : payload.origin();
        double scale = skill.scale(payload.chargeLevel());
        var mesh = prior != null && prior.revision() == payload.revision() ? prior.mesh()
                : SwordQiWaveShape.mesh(skill.halfWidth() * scale, skill.halfHeight() * scale);
        WAVES.put(payload.attackerEntityId(), new Wave(previous, payload.origin(), payload.direction().normalize(),
                payload.revision(), mesh, now));
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
            Vec3 center = wave.previous().lerp(wave.current(), SwordQiWaveShape.interpolationFraction(age)).subtract(camera);
            var frame = SwordQiWaveShape.frame(wave.direction().x, wave.direction().y, wave.direction().z);
            for (var quad : wave.mesh()) {
                vertex(vertices, quad.a(), center, frame, alpha);
                vertex(vertices, quad.b(), center, frame, alpha);
                vertex(vertices, quad.c(), center, frame, alpha);
                vertex(vertices, quad.d(), center, frame, alpha);
            }
            return false;
        });
        buffers.endBatch(CombatRenderTypes.WEAPON_TRAIL_TRANSLUCENT);
    }

    private static void vertex(VertexConsumer out, SwordQiWaveShape.Vertex v, Vec3 center,
                               SwordQiWaveShape.Frame frame, float alpha) {
        var p = frame.orient(v);
        out.addVertex((float) (center.x + p.x()), (float) (center.y + p.y()), (float) (center.z + p.z()))
                .setColor(v.red(), v.green(), v.blue(), Math.round(v.alpha() * alpha));
    }
}
