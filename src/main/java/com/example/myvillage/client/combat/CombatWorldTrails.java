package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.HitboxSample;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.combat.runtime.CombatGeometry;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World-space 剑光 for players seen from outside (remote players, or the local player in a
 * detached camera). The ribbon follows the move's own server hitbox samples, so what other
 * players see is where the strike actually lands.
 */
public final class CombatWorldTrails {
    private static final float TRAIL_TICKS = 1.8F;
    private static final float FADE_TICKS = 2.0F;
    private static final int SEGMENTS = 12;
    private static final double BLADE_START = 0.35;
    private static final float THRUST_HALF_WIDTH = 0.07F;
    private static final Map<Integer, Action> ACTIONS = new HashMap<>();

    private CombatWorldTrails() {
    }

    static void start(Entity attacker, int moveIndex, float elapsedTicks, float facingYaw) {
        ACTIONS.put(attacker.getId(), new Action(
                moveIndex,
                attacker.level().getGameTime() - Math.max(0.0F, elapsedTicks),
                facingYaw));
    }

    static void stop(int entityId) {
        ACTIONS.remove(entityId);
    }

    static void clear() {
        ACTIONS.clear();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIONS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIONS.clear();
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(CombatRenderTypes.SWORD_TRAIL);
        // Level rendering already applies the camera rotation; vertices are camera-relative.
        Matrix4f matrix = new Matrix4f();

        ACTIONS.entrySet().removeIf(entry -> {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            Action action = entry.getValue();
            AttackMoveDefinition move = BasicSwordStyle.DEFINITION.move(action.moveIndex());
            float tick = (float) (now - action.startTick());
            if (entity == null || tick >= move.totalTicks()) {
                return true;
            }
            boolean firstPersonSelf = entity == minecraft.player && !camera.isDetached();
            if (!firstPersonSelf) {
                render(consumer, matrix, entity, action, move, tick, partialTick, cameraPosition);
            }
            return false;
        });
        buffers.endBatch(CombatRenderTypes.SWORD_TRAIL);
    }

    private static void render(
            VertexConsumer consumer,
            Matrix4f matrix,
            Entity entity,
            Action action,
            AttackMoveDefinition move,
            float tick,
            float partialTick,
            Vec3 cameraPosition) {
        List<HitboxSample> samples = move.hitbox().samples();
        float first = samples.getFirst().actionTick() - 0.5F;
        float last = samples.getLast().actionTick() + 0.5F;
        float newest = Math.min(tick, last);
        float oldest = Math.max(first, tick - TRAIL_TICKS);
        if (newest <= oldest) {
            return;
        }
        float fade = tick <= last ? 1.0F : 1.0F - (tick - last) / FADE_TICKS;
        if (fade <= 0.0F) {
            return;
        }
        boolean thrust = BasicSwordStyle.feedback(action.moveIndex()).swingSound()
                == MoveFeedback.SwingSound.THRUST;
        Vec3 origin = entity.getPosition(partialTick);
        Vector3f[] previous = null;
        float previousAlpha = 0.0F;
        for (int index = 0; index <= SEGMENTS; index++) {
            float sampleTick = newest - (newest - oldest) * index / SEGMENTS;
            float age = (tick - sampleTick) / TRAIL_TICKS;
            float alpha = (float) Math.pow(Math.max(0.0F, 1.0F - age), 1.5) * fade * 0.8F;
            CombatGeometry.WorldSample world = CombatGeometry.transform(
                    blade(samples, sampleTick), origin, action.facingYaw());
            Vector3f base = relative(world.start().lerp(world.end(), BLADE_START), cameraPosition, matrix);
            Vector3f tip = relative(world.end(), cameraPosition, matrix);
            if (thrust) {
                // A thrust sweeps no area; draw a thin cross-shaped streak along the blade instead.
                Vector3f lift = relative(world.end().add(0.0, THRUST_HALF_WIDTH, 0.0), cameraPosition, matrix);
                Vector3f drop = relative(world.end().add(0.0, -THRUST_HALF_WIDTH, 0.0), cameraPosition, matrix);
                CombatRenderTypes.ribbonQuad(consumer, base, lift, alpha, base, drop, alpha);
                previous = null;
                continue;
            }
            Vector3f[] blade = {base, tip};
            if (previous != null) {
                CombatRenderTypes.ribbonQuad(
                        consumer, previous[0], previous[1], previousAlpha, blade[0], blade[1], alpha);
            }
            previous = blade;
            previousAlpha = alpha;
        }
    }

    /** Interpolates the blade segment between authored samples in polar form so arcs stay round. */
    static HitboxSample blade(List<HitboxSample> samples, float tick) {
        HitboxSample before = samples.getFirst();
        HitboxSample after = samples.getLast();
        for (HitboxSample sample : samples) {
            if (sample.actionTick() <= tick) {
                before = sample;
            }
            if (sample.actionTick() >= tick) {
                after = sample;
                break;
            }
        }
        if (before == after || after.actionTick() == before.actionTick()) {
            return before;
        }
        float progress = Math.max(0.0F, Math.min(1.0F,
                (tick - before.actionTick()) / (float) (after.actionTick() - before.actionTick())));
        double[] start = polarLerp(
                before.startX(), before.startY(), before.startZ(),
                after.startX(), after.startY(), after.startZ(), progress);
        double[] end = polarLerp(
                before.endX(), before.endY(), before.endZ(),
                after.endX(), after.endY(), after.endZ(), progress);
        return new HitboxSample(
                before.actionTick(),
                start[0], start[1], start[2],
                end[0], end[1], end[2],
                before.horizontalRadius(),
                before.verticalRadius());
    }

    private static double[] polarLerp(
            double firstX, double firstY, double firstZ,
            double secondX, double secondY, double secondZ,
            float progress) {
        double firstAngle = Math.atan2(firstX, firstZ);
        double secondAngle = Math.atan2(secondX, secondZ);
        double angle = firstAngle + (secondAngle - firstAngle) * progress;
        double firstRadius = Math.hypot(firstX, firstZ);
        double radius = firstRadius + (Math.hypot(secondX, secondZ) - firstRadius) * progress;
        return new double[] {
                Math.sin(angle) * radius,
                firstY + (secondY - firstY) * progress,
                Math.cos(angle) * radius
        };
    }

    private static Vector3f relative(Vec3 point, Vec3 cameraPosition, Matrix4f matrix) {
        return matrix.transformPosition(new Vector3f(
                (float) (point.x - cameraPosition.x),
                (float) (point.y - cameraPosition.y),
                (float) (point.z - cameraPosition.z)));
    }

    private record Action(int moveIndex, double startTick, float facingYaw) {
    }
}
