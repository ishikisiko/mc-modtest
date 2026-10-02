package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.WeaponDefinition;
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
 * other players see matches where the strike lands, but it is drawn at weapon length around the
 * attacker's shoulder instead of at full gameplay reach, so it hugs the held weapon. Its size
 * comes from the geometry of the attacker's weapon for the move's style (see {@link #trailWeapon}
 * and {@link #trailSize}). It freezes while the attacker is in a hit-stop, like the attacker's
 * animation.
 *
 * <p>Samples that share a server tick are spread evenly through that tick in list order (see
 * {@link #sampleTime}), so a move that sweeps several lines per tick draws one smooth arc.
 */
public final class CombatWorldTrails {
    static final float TRAIL_TICKS = 1.2F;
    static final float FADE_TICKS = 2.4F;
    static final int SEGMENTS = 24;
    /** Height of the blade's pivot (about the weapon shoulder) above the feet, in blocks. */
    static final double PIVOT_HEIGHT = 1.3;
    /**
     * Player-body constant: from the shoulder pivot to the hand's grip centre along the extended
     * arm, in blocks. A weapon's drawn tip radius is this plus the weapon's own grip-to-tip
     * length at its third-person scale. The value keeps the jian contract (19.9 px grip to tip at
     * scale 0.8, 0.995 blocks) at the tuned 0.27.0 tip radius of 1.7.
     */
    static final double ARM_REACH = 0.705;
    /**
     * Drawn size without a usable geometry contract or display scale: tip 1.7 blocks from the
     * pivot, trail 1.0 block long. With the shared taper (newest sample from 55% of the span
     * outward) the fresh band covers the outer 45% of the drawn span.
     */
    static final TrailSize FALLBACK_SIZE = new TrailSize(1.7, 1.0);
    static final float STREAK_TICKS = 3.0F;
    private static final float STREAK_HALF_WIDTH = 0.025F;
    private static final double STREAK_OVERSHOOT = 1.15;
    private static final Map<Integer, Action> ACTIONS = new HashMap<>();

    private CombatWorldTrails() {
    }

    static void start(Entity attacker, AttackMoveDefinition move, float elapsedTicks, float facingYaw) {
        ItemStack held = attacker instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
        ResourceLocation heldItem = held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
        ACTIONS.put(attacker.getId(), new Action(
                move,
                trailWeapon(CombatStyles.bundled(), heldItem, move.id()).orElse(null),
                ClientCombatClock.ticks() - Math.max(0.0F, elapsedTicks),
                facingYaw,
                new ArrayList<>()));
    }

    /**
     * The weapon whose geometry sizes an action's trail: the held item when it is a weapon of the
     * move's style, otherwise the first registered weapon of that style. A remote START can arrive
     * a tick before the attacker's equipment update, so the held item alone is not reliable.
     */
    static Optional<ResourceLocation> trailWeapon(CombatStyles styles, ResourceLocation heldItem, ResourceLocation moveId) {
        Optional<ResourceLocation> styleId = styles.move(moveId).map(ref -> ref.style().id());
        if (styleId.isEmpty()) {
            return Optional.empty();
        }
        if (heldItem != null && styles.weapon(heldItem)
                .filter(weapon -> weapon.style().equals(styleId.get()))
                .isPresent()) {
            return Optional.of(heldItem);
        }
        return styles.weapons().stream()
                .filter(weapon -> weapon.style().equals(styleId.get()))
                .map(WeaponDefinition::item)
                .findFirst();
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
        // Local ticks, like the attacker's stop it pairs with: a game-clock reset is not trail time.
        double now = ClientCombatClock.now(partialTick);
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
                        trailSize(minecraft, action.weaponItem()));
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
            TrailSize size) {
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
                    samples, Math.min(tick, last), STREAK_OVERSHOOT, size, origin, action.facingYaw());
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
                    samples, sampleTick, 1.0, size, origin, action.facingYaw());
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
     * drawn at the weapon's trail size, and turned by the facing the server started the action with.
     */
    static CombatGeometry.WorldSample worldBlade(
            List<HitboxSample> samples,
            float tick,
            double tipScale,
            TrailSize size,
            Vec3 origin,
            float facingYaw) {
        return CombatGeometry.transform(drawnBlade(blade(samples, tick), tipScale, size), origin, facingYaw);
    }

    /**
     * The drawn blade for one hitbox sample, in the same attacker-local frame: from the pivot
     * toward the sample's far end, with the tip at {@code min(reach, size.tipRadius)} (times
     * {@code tipScale}) and the base {@code size.trailLength} closer to the pivot (never past it).
     */
    static HitboxSample drawnBlade(HitboxSample sample, double tipScale, TrailSize size) {
        double x = sample.endX();
        double y = sample.endY() - PIVOT_HEIGHT;
        double z = sample.endZ();
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6) {
            return sample;
        }
        double tipRadius = Math.min(length, size.tipRadius());
        double baseRadius = Math.max(0.0, tipRadius - size.trailLength());
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
     * The drawn trail size for a weapon: the tip is {@link #ARM_REACH} plus the contract's grip
     * centre to trail tip, and the trail is the contract's trail span, both in model pixels at the
     * model's third-person display scale. {@link #FALLBACK_SIZE} without a contract or a usable
     * scale. The contract checks (span on the axis, base below tip) keep both lengths positive,
     * and {@link #drawnBlade} never draws the base behind the pivot.
     */
    static TrailSize trailSize(Optional<SwordGeometry> weapon, float thirdPersonScale) {
        if (weapon.isEmpty() || !(thirdPersonScale > 0.0F) || !Float.isFinite(thirdPersonScale)) {
            return FALLBACK_SIZE;
        }
        double blocksPerPixel = thirdPersonScale / 16.0;
        return new TrailSize(
                ARM_REACH + weapon.get().gripToTrailTipPixels() * blocksPerPixel,
                weapon.get().trailLengthPixels() * blocksPerPixel);
    }

    /** The drawn trail size for the weapon an action started with; the fallback without a rig. */
    private static TrailSize trailSize(Minecraft minecraft, ResourceLocation weaponItem) {
        if (weaponItem == null) {
            return FALLBACK_SIZE;
        }
        Optional<FirstPersonSwingResources.WeaponRig> rig = FirstPersonSwingResources.forItem(weaponItem);
        if (rig.isEmpty()) {
            return FALLBACK_SIZE;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(weaponItem));
        return trailSize(Optional.of(rig.get().swing().sword()), thirdPersonScale(minecraft, stack));
    }

    /** Length scale of the weapon model's third-person display transform along the blade (+Y). */
    private static float thirdPersonScale(Minecraft minecraft, ItemStack stack) {
        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        PoseStack scratch = new PoseStack();
        model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, scratch, false);
        return scratch.last().pose().transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).length();
    }

    /**
     * Interpolates the blade segment between authored samples in polar form so arcs stay round.
     * Each sample sits at its {@link #sampleTime}; between two samples the blade moves at a
     * constant rate, so a tick with several samples traces them in list order.
     */
    static HitboxSample blade(List<HitboxSample> samples, float tick) {
        float[] times = sampleTimes(samples);
        int before = 0;
        int after = samples.size() - 1;
        for (int index = 0; index < samples.size(); index++) {
            if (times[index] <= tick) {
                before = index;
            }
            if (times[index] >= tick) {
                after = index;
                break;
            }
        }
        HitboxSample first = samples.get(before);
        HitboxSample second = samples.get(after);
        if (before == after || !(times[after] > times[before])) {
            return first;
        }
        float progress = Math.max(0.0F, Math.min(1.0F, (tick - times[before]) / (times[after] - times[before])));
        double[] start = polarLerp(
                first.startX(), first.startY(), first.startZ(),
                second.startX(), second.startY(), second.startZ(), progress);
        double[] end = polarLerp(
                first.endX(), first.endY(), first.endZ(),
                second.endX(), second.endY(), second.endZ(), progress);
        return new HitboxSample(
                first.actionTick(),
                start[0], start[1], start[2],
                end[0], end[1], end[2],
                first.horizontalRadius(),
                first.verticalRadius());
    }

    /**
     * When the trail shows a sample: the {@code n} samples of one server tick {@code t} (in list
     * order, index {@code i}) sit at {@code t + (i + 0.5) / n - 0.5}, evenly through the tick and
     * centred on it. A lone sample sits exactly on its tick, as before; three per tick sit a third
     * of a tick apart, also across the tick boundary, so the drawn sweep keeps one speed.
     */
    static float sampleTime(List<HitboxSample> samples, int index) {
        int tick = samples.get(index).actionTick();
        int count = 0;
        int position = 0;
        for (int other = 0; other < samples.size(); other++) {
            if (samples.get(other).actionTick() == tick) {
                if (other < index) {
                    position++;
                }
                count++;
            }
        }
        return tick + (position + 0.5F) / count - 0.5F;
    }

    private static float[] sampleTimes(List<HitboxSample> samples) {
        float[] times = new float[samples.size()];
        for (int index = 0; index < times.length; index++) {
            times[index] = sampleTime(samples, index);
        }
        return times;
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

    /**
     * How big an attacker's world trail is drawn, in blocks: {@code tipRadius} from the pivot to
     * the trail's tip, {@code trailLength} from its base to its tip.
     */
    record TrailSize(double tipRadius, double trailLength) {
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
