package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.CombatStyleDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data-driven first-person sword rig loaded from
 * {@code assets/myvillage/combat/qingfeng_first_person.json}.
 *
 * <p>The sword swings around a fixed shoulder pivot in camera space: {@code plane} tilts the
 * swing plane on screen, {@code sweep} turns the arm within that plane, {@code reach} is the
 * pivot-to-grip distance, and {@code lead}/{@code lift}/{@code twist} orient the blade at the
 * grip. Keys are authored in server ticks so the visible strike can be checked against each
 * move's active window. An optional per-move {@code contact} tick marks the moment the drawn
 * blade reaches the target; the hit-stop waits for it so the freeze reads as the blade biting.
 *
 * <p>The rig also carries the sword's first-person scale ({@code rig.sword_scale}, item-model
 * units to blocks), optional arm tuning ({@code rig.arm}) and the sword geometry contract. Two
 * optional pose fields drive the first-person arm and are interpolated like the rest:
 * {@code grip_roll}, the hand's turn about the handle in the sword's own frame (0 puts the knuckles
 * along the +Z edge, 90 along the +X flat normal), and {@code elbow}, degrees the elbow swivels up
 * and out around the shoulder-wrist line (0 keeps the natural low elbow). Missing values inherit
 * the previous key, and the neutral hold defaults both to 0.
 */
final class FirstPersonSwing {
    static final String RESOURCE_PATH = "combat/qingfeng_first_person.json";

    /** Item-model units (16 px) to blocks for the first-person sword when the rig omits it. */
    static final float DEFAULT_SWORD_SCALE = 0.60F;

    private final Rig rig;
    private final Pose neutral;
    private final List<Move> moves;
    private final SwordGeometry sword;

    private FirstPersonSwing(Rig rig, Pose neutral, List<Move> moves, SwordGeometry sword) {
        this.rig = rig;
        this.neutral = neutral;
        this.moves = List.copyOf(moves);
        this.sword = sword;
    }

    Rig rig() {
        return rig;
    }

    SwordGeometry sword() {
        return sword;
    }

    Pose neutral() {
        return neutral;
    }

    List<Move> moves() {
        return moves;
    }

    Move move(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= moves.size()) {
            throw new IllegalArgumentException("Unknown first-person sword move index: " + moveIndex);
        }
        return moves.get(moveIndex);
    }

    Pose sample(int moveIndex, float tick) {
        return move(moveIndex).sample(tick);
    }

    static FirstPersonSwing parse(JsonObject json, CombatStyleDefinition style, SwordGeometry sword) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(sword, "sword");
        JsonObject rigJson = json.getAsJsonObject("rig");
        if (rigJson == null) {
            throw new IllegalArgumentException("First-person swing file has no rig");
        }
        float[] shoulder = vector(rigJson.getAsJsonArray("shoulder"), "rig.shoulder");
        float swordScale = value(rigJson, "sword_scale", DEFAULT_SWORD_SCALE);
        if (!(swordScale >= 0.2F && swordScale <= 1.5F)) {
            throw new IllegalArgumentException("rig.sword_scale must be within 0.2..1.5");
        }
        Arm arm = Arm.parse(rigJson.has("arm") ? rigJson.getAsJsonObject("arm") : new JsonObject());
        Rig rig = new Rig(shoulder[0], shoulder[1], shoulder[2], swordScale, arm);
        Pose neutral = pose(json.getAsJsonObject("neutral"), Pose.ZERO);

        JsonObject movesJson = json.getAsJsonObject("moves");
        if (movesJson == null) {
            throw new IllegalArgumentException("First-person swing file has no moves");
        }
        List<Move> moves = new ArrayList<>();
        for (AttackMoveDefinition definition : style.moves()) {
            JsonObject moveJson = movesJson.getAsJsonObject(definition.id().toString());
            if (moveJson == null) {
                throw new IllegalArgumentException("Missing first-person swing for " + definition.id());
            }
            moves.add(move(definition, moveJson, neutral));
        }
        if (movesJson.size() != moves.size()) {
            throw new IllegalArgumentException("First-person swing file declares unknown moves");
        }
        return new FirstPersonSwing(rig, neutral, moves, sword);
    }

    private static Move move(AttackMoveDefinition definition, JsonObject json, Pose neutral) {
        JsonArray keysJson = json.getAsJsonArray("keys");
        if (keysJson == null || keysJson.size() < 3) {
            throw new IllegalArgumentException(definition.id() + " needs at least three keys");
        }
        List<Key> keys = new ArrayList<>();
        Pose previous = neutral;
        for (JsonElement element : keysJson) {
            JsonObject keyJson = element.getAsJsonObject();
            float tick = keyJson.get("tick").getAsFloat();
            Ease ease = keyJson.has("ease") ? Ease.parse(keyJson.get("ease").getAsString()) : Ease.LINEAR;
            boolean toNeutral = keyJson.has("pose") && "neutral".equals(keyJson.get("pose").getAsString());
            Pose pose = toNeutral ? neutral : pose(keyJson, previous);
            if (!keys.isEmpty() && tick <= keys.getLast().tick()) {
                throw new IllegalArgumentException(definition.id() + " key ticks must increase");
            }
            keys.add(new Key(tick, ease, pose));
            previous = pose;
        }
        if (keys.getFirst().tick() != 0.0F || keys.getLast().tick() != definition.totalTicks()) {
            throw new IllegalArgumentException(
                    definition.id() + " keys must span 0.." + definition.totalTicks() + " ticks");
        }
        if (!keys.getFirst().pose().equals(neutral) || !keys.getLast().pose().equals(neutral)) {
            throw new IllegalArgumentException(definition.id() + " must start and end at the neutral hold");
        }

        float[] strike = vector(json.getAsJsonArray("strike"), definition.id() + ".strike", 2);
        if (!(strike[0] < strike[1])
                || strike[0] > definition.activeStartTick()
                || strike[1] < definition.activeEndTick()
                || strike[1] > definition.totalTicks()) {
            throw new IllegalArgumentException(definition.id()
                    + " strike window must cover active ticks "
                    + definition.activeStartTick() + ".." + definition.activeEndTick());
        }
        float contact = json.has("contact") ? json.get("contact").getAsFloat() : strike[0];
        if (!(contact >= strike[0] && contact <= strike[1])) {
            throw new IllegalArgumentException(definition.id() + " contact tick must lie inside the strike window");
        }
        return new Move(definition.id(), definition.totalTicks(), keys, strike[0], strike[1], contact);
    }

    private static Pose pose(JsonObject json, Pose fallback) {
        if (json == null) {
            throw new IllegalArgumentException("Missing pose object");
        }
        float[] offset = json.has("offset")
                ? vector(json.getAsJsonArray("offset"), "offset")
                : new float[] {fallback.x(), fallback.y(), fallback.z()};
        return new Pose(
                value(json, "plane", fallback.plane()),
                value(json, "sweep", fallback.sweep()),
                value(json, "reach", fallback.reach()),
                value(json, "lead", fallback.lead()),
                value(json, "lift", fallback.lift()),
                value(json, "twist", fallback.twist()),
                offset[0],
                offset[1],
                offset[2],
                value(json, "grip_roll", fallback.gripRoll()),
                value(json, "elbow", fallback.elbow()));
    }

    private static float value(JsonObject json, String name, float fallback) {
        return json.has(name) ? json.get(name).getAsFloat() : fallback;
    }

    private static float[] vector(JsonArray array, String label) {
        return vector(array, label, 3);
    }

    private static float[] vector(JsonArray array, String label, int size) {
        if (array == null || array.size() != size) {
            throw new IllegalArgumentException(label + " must have " + size + " numbers");
        }
        float[] values = new float[size];
        for (int index = 0; index < size; index++) {
            values[index] = array.get(index).getAsFloat();
        }
        return values;
    }

    record Rig(float shoulderX, float shoulderY, float shoulderZ, float swordScale, Arm arm) {
    }

    /**
     * First-person arm tuning, all optional under {@code rig.arm}. Lengths are blocks; the arm's
     * cross-section is {@code thickness} times the skin's pixel size; {@code grip_diagonal} is how
     * far the handle leans across the palm (blade toward the knuckles); {@code follow_through}
     * scales the wrist lag and follow-through (0 turns it off).
     */
    record Arm(
            float shoulderOffsetX,
            float shoulderOffsetY,
            float shoulderOffsetZ,
            float upperArm,
            float forearm,
            float thickness,
            float gripDiagonal,
            float followThrough) {
        static final Arm DEFAULT = new Arm(0.03F, -0.02F, 0.0F, 0.33F, 0.33F, 0.5F, 40.0F, 1.0F);

        Arm {
            if (!(upperArm >= 0.1F && upperArm <= 0.6F && forearm >= 0.1F && forearm <= 0.6F)) {
                throw new IllegalArgumentException("rig.arm bone lengths must be within 0.1..0.6");
            }
            if (!(thickness >= 0.2F && thickness <= 1.2F)) {
                throw new IllegalArgumentException("rig.arm.thickness must be within 0.2..1.2");
            }
            if (!(gripDiagonal >= 0.0F && gripDiagonal <= 50.0F)) {
                throw new IllegalArgumentException("rig.arm.grip_diagonal must be within 0..50");
            }
            if (!(followThrough >= 0.0F && followThrough <= 2.0F)) {
                throw new IllegalArgumentException("rig.arm.follow_through must be within 0..2");
            }
            if (!Float.isFinite(shoulderOffsetX) || !Float.isFinite(shoulderOffsetY) || !Float.isFinite(shoulderOffsetZ)) {
                throw new IllegalArgumentException("rig.arm.shoulder_offset must be finite");
            }
        }

        static Arm parse(JsonObject json) {
            float[] offset = json.has("shoulder_offset")
                    ? vector(json.getAsJsonArray("shoulder_offset"), "rig.arm.shoulder_offset")
                    : new float[] {DEFAULT.shoulderOffsetX, DEFAULT.shoulderOffsetY, DEFAULT.shoulderOffsetZ};
            return new Arm(
                    offset[0],
                    offset[1],
                    offset[2],
                    value(json, "upper_arm", DEFAULT.upperArm),
                    value(json, "forearm", DEFAULT.forearm),
                    value(json, "thickness", DEFAULT.thickness),
                    value(json, "grip_diagonal", DEFAULT.gripDiagonal),
                    value(json, "follow_through", DEFAULT.followThrough));
        }
    }

    record Pose(
            float plane,
            float sweep,
            float reach,
            float lead,
            float lift,
            float twist,
            float x,
            float y,
            float z,
            float gripRoll,
            float elbow) {
        static final Pose ZERO = new Pose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        Pose {
            if (!Float.isFinite(plane) || !Float.isFinite(sweep) || !Float.isFinite(reach)
                    || !Float.isFinite(lead) || !Float.isFinite(lift) || !Float.isFinite(twist)
                    || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(gripRoll) || !Float.isFinite(elbow)) {
                throw new IllegalArgumentException("Swing pose values must be finite");
            }
        }

        static Pose interpolate(Pose start, Pose end, float progress) {
            if (progress <= 0.0F) {
                return start;
            }
            if (progress >= 1.0F) {
                return end;
            }
            return new Pose(
                    lerp(start.plane, end.plane, progress),
                    lerp(start.sweep, end.sweep, progress),
                    lerp(start.reach, end.reach, progress),
                    lerp(start.lead, end.lead, progress),
                    lerp(start.lift, end.lift, progress),
                    lerp(start.twist, end.twist, progress),
                    lerp(start.x, end.x, progress),
                    lerp(start.y, end.y, progress),
                    lerp(start.z, end.z, progress),
                    lerp(start.gripRoll, end.gripRoll, progress),
                    lerp(start.elbow, end.elbow, progress));
        }

        private static float lerp(float start, float end, float progress) {
            return start + (end - start) * progress;
        }
    }

    /**
     * Easing applied to the segment that arrives at a key. Strikes accelerate into contact
     * ({@code in}/{@code in_cubic}); {@code out_back} decelerates past its key by about 12% and
     * settles back, which authors the follow-through overshoot.
     */
    enum Ease {
        LINEAR,
        IN,
        OUT,
        IN_OUT,
        IN_CUBIC,
        OUT_CUBIC,
        IN_OUT_CUBIC,
        OUT_BACK;

        /** Overshoot constant of {@link #OUT_BACK}; 1.9 peaks about 12% past the key. */
        static final float BACK_OVERSHOOT = 1.9F;

        static Ease parse(String name) {
            return switch (name) {
                case "linear" -> LINEAR;
                case "in" -> IN;
                case "out" -> OUT;
                case "in_out" -> IN_OUT;
                case "in_cubic" -> IN_CUBIC;
                case "out_cubic" -> OUT_CUBIC;
                case "in_out_cubic" -> IN_OUT_CUBIC;
                case "out_back" -> OUT_BACK;
                default -> throw new IllegalArgumentException("Unknown swing ease: " + name);
            };
        }

        /** True when the curve leaves 0..1 before it settles on the key. */
        boolean overshoots() {
            return this == OUT_BACK;
        }

        float apply(float value) {
            float t = Math.max(0.0F, Math.min(1.0F, value));
            return switch (this) {
                case LINEAR -> t;
                case IN -> t * t;
                case OUT -> 1.0F - (1.0F - t) * (1.0F - t);
                case IN_OUT -> t * t * (3.0F - 2.0F * t);
                case IN_CUBIC -> t * t * t;
                case OUT_CUBIC -> {
                    float inverse = 1.0F - t;
                    yield 1.0F - inverse * inverse * inverse;
                }
                case IN_OUT_CUBIC -> {
                    if (t < 0.5F) {
                        yield 4.0F * t * t * t;
                    }
                    float inverse = -2.0F * t + 2.0F;
                    yield 1.0F - inverse * inverse * inverse * 0.5F;
                }
                case OUT_BACK -> {
                    float shifted = t - 1.0F;
                    yield 1.0F + (BACK_OVERSHOOT + 1.0F) * shifted * shifted * shifted
                            + BACK_OVERSHOOT * shifted * shifted;
                }
            };
        }
    }

    record Key(float tick, Ease ease, Pose pose) {
    }

    record Move(
            ResourceLocation id,
            int totalTicks,
            List<Key> keys,
            float strikeStartTick,
            float strikeEndTick,
            float contactTick) {
        Move {
            keys = List.copyOf(keys);
        }

        Move(ResourceLocation id, int totalTicks, List<Key> keys, float strikeStartTick, float strikeEndTick) {
            this(id, totalTicks, keys, strikeStartTick, strikeEndTick, strikeStartTick);
        }

        Pose sample(float tick) {
            float bounded = Math.max(0.0F, Math.min(totalTicks, tick));
            for (int index = 1; index < keys.size(); index++) {
                Key end = keys.get(index);
                if (bounded <= end.tick()) {
                    Key start = keys.get(index - 1);
                    float local = (bounded - start.tick()) / (end.tick() - start.tick());
                    return Pose.interpolate(start.pose(), end.pose(), end.ease().apply(local));
                }
            }
            return keys.getLast().pose();
        }
    }
}
