package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.HitboxSample;
import com.example.myvillage.combat.runtime.CombatGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * World-space 剑光 for players seen from outside (remote players, or the local player in a
 * detached camera). The ribbon's direction follows the move's own server hitbox samples, so what
 * other players see matches where the strike lands, but it is drawn at sword length around the
 * attacker's shoulder instead of at full gameplay reach, so it hugs the held blade. Its length
 * comes from the geometry of the weapon held when the move started. It freezes while the attacker
 * is in a hit-stop, like the attacker's animation.
 */
public final class CombatWorldTrails {
    static final float TRAIL_TICKS = 1.2F;
    static final float FADE_TICKS = 2.4F;
    static final int SEGMENTS = 24;
    /** Height of the blade's pivot (about the sword shoulder) above the feet, in blocks. */
    static final double PIVOT_HEIGHT = 1.3;
    /** Longest drawn blade reach from the pivot: arm plus sword, not the gameplay hitbox reach. */
    static final double MAXIMUM_TIP_RADIUS = 1.7;
    /**
     * Fallback drawn blade length from base to tip, used when the sword geometry contract is not
     * loaded. Normally the length is the contract's blade (base to tip) at the sword model's
     * third-person display scale, so the ribbon matches the drawn blade; with the shared taper
     * (newest sample from 55% of the blade outward) the fresh band spans its outer 45%.
     */
    static final double DRAWN_BLADE_LENGTH = 1.0;
    static final double MINIMUM_BLADE_LENGTH = 0.5;
    static final double MAXIMUM_BLADE_LENGTH = 1.3;
    static final float STREAK_TICKS = 3.0F;
    private static final float STREAK_HALF_WIDTH = 0.025F;
    private static final double STREAK_OVERSHOOT = 1.15;
    private static final Map<Integer, Action> ACTIONS = new HashMap<>();

    private CombatWorldTrails() {
    }

    static void start(Entity attacker, AttackMoveDefinition move, float elapsedTicks, float facingYaw) {
        ItemStack held = attacker instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
        ACTIONS.put(attacker.getId(), new Action(
                move,
                held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()),
                attacker.level().getGameTime() - Math.max(0.0F, elapsedTicks),
                facingYaw,
                new ArrayList<>()));
    }

    static void stop(int entityId) {
        ACTIONS.remove(entityId);
    }

    static void clear() {
        ACTIONS.clear();
    }

    /** Freezes the attacker's world trail for a hit-stop that starts at {@code startTime}. */
    static void hitStop(int attackerEntityId, double startTime, float stopTicks) {
        Action action = ACTIONS.get(attackerEntityId);
        if (action != null && stopTicks > 0.0F) {
            action.stops().add(new double[] {startTime, stopTicks});
        }
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
        VertexConsumer consumer = buffers.getBuffer(CombatRenderTypes.SWORD_TRAIL_TRANSLUCENT);

        ACTIONS.entrySet().removeIf(entry -> {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            Action action = entry.getValue();
            AttackMoveDefinition move = action.move();
            float tick = (float) (now - action.startTick() - action.lostTicks(now));
            if (entity == null || tick >= move.totalTicks()) {
                return true;
            }
            boolean firstPersonSelf = entity == minecraft.player && !camera.isDetached();
            if (!firstPersonSelf) {
                render(consumer, entity, action, move, tick, partialTick, cameraPosition,
                        bladeLength(minecraft, action.weaponItem()));
            }
            return false;
        });
        buffers.endBatch(CombatRenderTypes.SWORD_TRAIL_TRANSLUCENT);
    }

    private static void render(
            VertexConsumer consumer,
            Entity entity,
            Action action,
            AttackMoveDefinition move,
            float tick,
            float partialTick,
            Vec3 cameraPosition,
            double bladeLength) {
        List<HitboxSample> samples = move.hitbox().samples();
        float first = samples.getFirst().actionTick() - 0.5F;
        float last = samples.getLast().actionTick() + 0.5F;
        Vec3 origin = entity.getPosition(partialTick);
        if (SwordTrailShape.streak(move.kind())) {
            // A thrust sweeps no area: one camera-facing streak along the blade, drawn once.
            float alpha = SwordTrailShape.streakAlpha(tick, first, STREAK_TICKS);
            if (alpha <= 0.0F) {
                return;
            }
            CombatGeometry.WorldSample world = worldBlade(
                    samples, Math.min(tick, last), STREAK_OVERSHOOT, bladeLength, origin, action.facingYaw());
            CombatRenderTypes.streak(
                    consumer,
                    relative(world.start(), cameraPosition),
                    relative(world.end(), cameraPosition),
                    STREAK_HALF_WIDTH,
                    alpha);
            return;
        }

        float newest = Math.min(tick, last);
        float oldest = Math.max(first, tick - TRAIL_TICKS);
        if (newest <= oldest) {
            return;
        }
        float fade = SwordTrailShape.fade(tick, last, FADE_TICKS);
        if (fade <= 0.0F) {
            return;
        }
        Vector3f[] previous = null;
        float previousAge = 0.0F;
        float previousAlpha = 0.0F;
        for (int index = 0; index <= SEGMENTS; index++) {
            float sampleTick = newest - (newest - oldest) * index / SEGMENTS;
            float age = (tick - sampleTick) / TRAIL_TICKS;
            float alpha = SwordTrailShape.alpha(age, fade);
            CombatGeometry.WorldSample world = worldBlade(
                    samples, sampleTick, 1.0, bladeLength, origin, action.facingYaw());
            Vector3f[] blade = {relative(world.start(), cameraPosition), relative(world.end(), cameraPosition)};
            if (previous != null) {
                CombatRenderTypes.trailSegment(
                        consumer,
                        previous[0], previous[1], previousAge, previousAlpha,
                        blade[0], blade[1], age, alpha);
            }
            previous = blade;
            previousAge = age;
            previousAlpha = alpha;
        }
    }

    /**
     * The drawn blade in the world at {@code tick}: the move's own hitbox samples, interpolated,
     * drawn at sword length, and turned by the facing the server started the action with.
     */
    static CombatGeometry.WorldSample worldBlade(
            List<HitboxSample> samples,
            float tick,
            double tipScale,
            double bladeLength,
            Vec3 origin,
            float facingYaw) {
        return CombatGeometry.transform(drawnBlade(blade(samples, tick), tipScale, bladeLength), origin, facingYaw);
    }

    /**
     * The drawn blade for one hitbox sample, in the same attacker-local frame: from the sword
     * pivot toward the sample's far end, with the tip at {@code min(reach, 1.7)} (times
     * {@code tipScale}) and the base {@link #DRAWN_BLADE_LENGTH} closer to the pivot.
     */
    static HitboxSample drawnBlade(HitboxSample sample, double tipScale) {
        return drawnBlade(sample, tipScale, DRAWN_BLADE_LENGTH);
    }

    /** As {@link #drawnBlade(HitboxSample, double)} with the base {@code bladeLength} closer to the pivot. */
    static HitboxSample drawnBlade(HitboxSample sample, double tipScale, double bladeLength) {
        double x = sample.endX();
        double y = sample.endY() - PIVOT_HEIGHT;
        double z = sample.endZ();
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6) {
            return sample;
        }
        double tipRadius = Math.min(length, MAXIMUM_TIP_RADIUS);
        double baseRadius = Math.max(0.0, tipRadius - bladeLength);
        double tip = tipRadius * tipScale / length;
        double base = baseRadius / length;
        return new HitboxSample(
                sample.actionTick(),
                x * base, PIVOT_HEIGHT + y * base, z * base,
                x * tip, PIVOT_HEIGHT + y * tip, z * tip,
                sample.horizontalRadius(),
                sample.verticalRadius());
    }

    /**
     * The drawn blade length: the geometry contract's blade in model pixels at the model's
     * third-person display scale, bounded to a sane range; {@link #DRAWN_BLADE_LENGTH} without a
     * contract or a usable scale.
     */
    static double drawnBladeLength(Optional<SwordGeometry> sword, float thirdPersonScale) {
        if (sword.isEmpty() || !(thirdPersonScale > 0.0F) || !Float.isFinite(thirdPersonScale)) {
            return DRAWN_BLADE_LENGTH;
        }
        double length = sword.get().bladeLengthPixels() / 16.0 * thirdPersonScale;
        return Math.max(MINIMUM_BLADE_LENGTH, Math.min(MAXIMUM_BLADE_LENGTH, length));
    }

    /** The drawn blade length for the weapon an action started with; the fallback without a rig. */
    private static double bladeLength(Minecraft minecraft, ResourceLocation weaponItem) {
        if (weaponItem == null) {
            return DRAWN_BLADE_LENGTH;
        }
        Optional<FirstPersonSwingResources.WeaponRig> rig = FirstPersonSwingResources.forItem(weaponItem);
        if (rig.isEmpty()) {
            return DRAWN_BLADE_LENGTH;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(weaponItem));
        return drawnBladeLength(Optional.of(rig.get().swing().sword()), thirdPersonScale(minecraft, stack));
    }

    /** Length scale of the weapon model's third-person display transform along the blade (+Y). */
    private static float thirdPersonScale(Minecraft minecraft, ItemStack stack) {
        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        PoseStack scratch = new PoseStack();
        model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, scratch, false);
        return scratch.last().pose().transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).length();
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

    // Level rendering already applies the camera rotation; vertices are camera-relative.
    private static Vector3f relative(Vec3 point, Vec3 cameraPosition) {
        return new Vector3f(
                (float) (point.x - cameraPosition.x),
                (float) (point.y - cameraPosition.y),
                (float) (point.z - cameraPosition.z));
    }

    /**
     * One attacker's trail; {@code weaponItem} is the main-hand item when it started (null when
     * empty), {@code stops} holds {startTime, stopTicks} for each hit-stop.
     */
    private record Action(
            AttackMoveDefinition move,
            ResourceLocation weaponItem,
            double startTick,
            float facingYaw,
            List<double[]> stops) {
        /** Trail time lost to hit-stops so far, so the ribbon holds still during each stop. */
        double lostTicks(double now) {
            double lost = 0.0;
            for (double[] stop : stops) {
                lost += CombatImpactFx.hitStopLostTicks((float) (now - stop[0]), (float) stop[1]);
            }
            return lost;
        }
    }
}
