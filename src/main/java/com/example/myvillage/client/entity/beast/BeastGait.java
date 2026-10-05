package com.example.myvillage.client.entity.beast;

import com.example.myvillage.entity.beast.BeastDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Locomotion clip rates that keep planted feet from sliding, measured from the clip itself.
 *
 * <p>{@link #plantedFootSpeed} poses the model at many times through a clip (same transform chain
 * as {@code ModelPart.translateAndRotate}: translate to the pivot plus position offset, rotate ZYX,
 * scale) and averages how fast cube corners touching the ground move backwards (+Z) while they
 * touch it. {@code animateWalk} then advances the clip by {@code 50 * rate} ms per unit of limb
 * swing, and limb swing advances {@code min(4 * d, 1)} per tick at {@code d} blocks per tick, so the
 * feet keep still when {@code rate = min(4 d, 1) * 20 / (4 v)}: {@code 5 / v} below a quarter block
 * per tick, {@code 20 d / v} above it.
 */
public final class BeastGait {
    /** A corner within this many model units of the clip's lowest point counts as touching the ground. */
    static final float CONTACT_UNITS = 0.5F;
    static final int SAMPLES = 240;
    /** {@code LivingEntity.travel} on ordinary ground keeps 0.546 of the speed per tick. */
    static final double GROUND_RETAIN = 0.6 * 0.91;
    /** Speed modifier of the vanilla random-stroll goal the beast uses. */
    static final double STROLL_SPEED_MODIFIER = 0.8;

    private BeastGait() {
    }

    /** Backward speed of planted feet, in blocks per clip second (0 when nothing touches the ground). */
    public static double plantedFootSpeed(BeastModelFile model, BeastAnimationFile.Clip clip) {
        float length = clip.length();
        float[][] cornersY = null;
        float[][] cornersZ = null;
        for (int sample = 0; sample <= SAMPLES; sample++) {
            List<Vector3f> corners = corners(model, clip, length * sample / SAMPLES);
            if (cornersY == null) {
                cornersY = new float[SAMPLES + 1][corners.size()];
                cornersZ = new float[SAMPLES + 1][corners.size()];
            }
            for (int index = 0; index < corners.size(); index++) {
                cornersY[sample][index] = corners.get(index).y;
                cornersZ[sample][index] = corners.get(index).z;
            }
        }
        float ground = -Float.MAX_VALUE;
        for (float[] row : cornersY) {
            for (float y : row) {
                ground = Math.max(ground, y);
            }
        }
        double sum = 0.0;
        int count = 0;
        double step = length / SAMPLES;
        for (int sample = 0; sample < SAMPLES; sample++) {
            for (int index = 0; index < cornersY[sample].length; index++) {
                if (cornersY[sample][index] >= ground - CONTACT_UNITS && cornersY[sample + 1][index] >= ground - CONTACT_UNITS) {
                    sum += (cornersZ[sample + 1][index] - cornersZ[sample][index]) / step;
                    count++;
                }
            }
        }
        return count == 0 ? 0.0 : sum / count / 16.0 * model.scale();
    }

    /** Horizontal blocks per tick a mob covers on flat ground when its navigation runs at {@code speed}. */
    public static double groundSpeed(double movementSpeed, double speedModifier) {
        double speed = movementSpeed * speedModifier;
        // MoveControl sets both the speed and the forward input to it, so the push per tick is speed^2.
        return speed * speed / (1.0 - GROUND_RETAIN);
    }

    /** Walk and run rates for a beast: stroll speed for {@code walk}, chase speed for {@code run}. */
    public static Rates rates(BeastModelFile model, BeastAnimationFile animations, BeastDefinition definition,
            String walkClip, String runClip) {
        double movement = definition.attributes().movementSpeed();
        double walkFoot = animations.clip(walkClip).map(clip -> plantedFootSpeed(model, clip)).orElse(0.0);
        double runFoot = animations.clip(runClip).map(clip -> plantedFootSpeed(model, clip)).orElse(0.0);
        double walkSpeed = groundSpeed(movement, STROLL_SPEED_MODIFIER);
        double runSpeed = groundSpeed(movement, definition.chase().speedModifier());
        return new Rates(rateFor(walkFoot, walkSpeed), rateFor(runFoot, runSpeed), walkFoot, runFoot, walkSpeed, runSpeed);
    }

    /** {@code 5 / v} below a quarter block per tick, {@code 20 d / v} at or above it. */
    static float rateFor(double footSpeed, double blocksPerTick) {
        if (!(footSpeed > 1.0E-6)) {
            return 1.0F;
        }
        return (float) (blocksPerTick >= 0.25 ? 20.0 * blocksPerTick / footSpeed : 5.0 / footSpeed);
    }

    public record Rates(float walk, float run, double walkFootSpeed, double runFootSpeed,
            double walkBlocksPerTick, double runBlocksPerTick) {
    }

    /** Every cube corner of the model posed at {@code time} seconds into {@code clip}, in model units. */
    static List<Vector3f> corners(BeastModelFile model, BeastAnimationFile.Clip clip, float time) {
        Map<String, Matrix4f> poses = new HashMap<>();
        List<Vector3f> corners = new ArrayList<>();
        for (BeastModelFile.Bone bone : model.bones()) {
            Matrix4f pose = bone.parent() == null ? new Matrix4f() : new Matrix4f(poses.get(bone.parent()));
            Vector3f position = sample(clip, bone.name(), BeastAnimationFile.Target.POSITION, time, 0.0F);
            Vector3f rotation = sample(clip, bone.name(), BeastAnimationFile.Target.ROTATION, time, 0.0F);
            Vector3f scale = sample(clip, bone.name(), BeastAnimationFile.Target.SCALE, time, 1.0F);
            // posVec negates y: +y in the file moves the bone up, model y points down.
            pose.translate(bone.pivot().x() + position.x, bone.pivot().y() - position.y, bone.pivot().z() + position.z);
            pose.rotateZYX(
                    BeastModelFile.radians(bone.rotation().z() + rotation.z),
                    BeastModelFile.radians(bone.rotation().y() + rotation.y),
                    BeastModelFile.radians(bone.rotation().x() + rotation.x));
            pose.scale(scale.x, scale.y, scale.z);
            poses.put(bone.name(), pose);
            for (BeastModelFile.Cube cube : bone.cubes()) {
                for (int corner = 0; corner < 8; corner++) {
                    Vector3f point = new Vector3f(
                            cube.origin().x() + ((corner & 1) == 0 ? 0.0F : cube.size().x()),
                            cube.origin().y() + ((corner & 2) == 0 ? 0.0F : cube.size().y()),
                            cube.origin().z() + ((corner & 4) == 0 ? 0.0F : cube.size().z()));
                    corners.add(pose.transformPosition(point));
                }
            }
        }
        return corners;
    }

    /** Linear sample of one channel (offset {@code rest} when the clip has no such channel). */
    static Vector3f sample(BeastAnimationFile.Clip clip, String bone, BeastAnimationFile.Target target, float time, float rest) {
        for (BeastAnimationFile.Channel channel : clip.channels()) {
            if (!channel.bone().equals(bone) || channel.target() != target) {
                continue;
            }
            List<BeastAnimationFile.Key> keys = channel.keyframes();
            BeastAnimationFile.Key previous = keys.getFirst();
            if (time <= previous.time()) {
                return vector(previous.value());
            }
            for (BeastAnimationFile.Key key : keys) {
                if (key.time() >= time) {
                    float span = key.time() - previous.time();
                    float f = span <= 0.0F ? 1.0F : (time - previous.time()) / span;
                    Vector3f a = vector(previous.value());
                    return a.lerp(vector(key.value()), f);
                }
                previous = key;
            }
            return vector(keys.getLast().value());
        }
        return new Vector3f(rest);
    }

    private static Vector3f vector(BeastModelFile.Vec value) {
        return new Vector3f(value.x(), value.y(), value.z());
    }
}
