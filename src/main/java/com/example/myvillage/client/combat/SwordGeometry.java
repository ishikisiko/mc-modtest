package com.example.myvillage.client.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.Optional;

/**
 * A sword's geometry contract, loaded from the {@code geometry} asset its weapon entry names (for
 * Qingfeng {@code assets/myvillage/combat/qingfeng_sword_geometry.json}, written by the sword-model
 * generator). All values are item-model pixels (16 per block): the blade runs along +Y, the flat
 * normal along X, the edge along Z, the same axes as the first-person grip frame. Nothing about the
 * sword's shape is hard-coded in Java; the grip, trail and arm all read this.
 *
 * <p>A two-handed weapon also gives {@code off_hand_grip_center}, the leading hand's point: on the
 * handle axis, inside the handle and ahead of (above) {@code grip_center}. A one-handed weapon
 * omits it.
 *
 * <p>The optional {@code trail} block ({@code {"base": [x, y, z], "tip": [x, y, z]}}) names the part
 * of the weapon that draws the 剑光 trails: on the weapon axis, base below tip, within the weapon
 * from the pommel's bottom to the blade tip. Without it the trail spans {@code blade_base} to
 * {@code blade_tip}, which suits a sword; a polearm whose blade is a short head names a longer span.
 */
final class SwordGeometry {
    private final Vector3f gripCenter;
    private final float handleBottom;
    private final float handleTop;
    private final float handleHalfWidth;
    private final float handleHalfThickness;
    private final float guardBottom;
    private final float guardTop;
    private final float guardHalfWidth;
    private final float guardHalfThickness;
    private final float pommelBottom;
    private final float pommelTop;
    private final Vector3f bladeBase;
    private final Vector3f bladeTip;
    private final Vector3f offHandGripCenter;
    private final Vector3f trailBase;
    private final Vector3f trailTip;

    private SwordGeometry(
            Vector3f gripCenter,
            float[] handle,
            float handleHalfWidth,
            float handleHalfThickness,
            float[] guard,
            float guardHalfWidth,
            float guardHalfThickness,
            float[] pommel,
            Vector3f bladeBase,
            Vector3f bladeTip,
            Vector3f offHandGripCenter,
            Vector3f trailBase,
            Vector3f trailTip) {
        this.gripCenter = gripCenter;
        this.handleBottom = handle[0];
        this.handleTop = handle[1];
        this.handleHalfWidth = handleHalfWidth;
        this.handleHalfThickness = handleHalfThickness;
        this.guardBottom = guard[0];
        this.guardTop = guard[1];
        this.guardHalfWidth = guardHalfWidth;
        this.guardHalfThickness = guardHalfThickness;
        this.pommelBottom = pommel[0];
        this.pommelTop = pommel[1];
        this.bladeBase = bladeBase;
        this.bladeTip = bladeTip;
        this.offHandGripCenter = offHandGripCenter;
        this.trailBase = trailBase;
        this.trailTip = trailTip;
    }

    static SwordGeometry parse(JsonObject json) {
        Objects.requireNonNull(json, "json");
        if (!"model_pixels".equals(string(json, "units"))) {
            throw new IllegalArgumentException("Sword geometry units must be model_pixels");
        }
        JsonObject axes = json.getAsJsonObject("axes");
        if (axes == null
                || !"+y".equals(string(axes, "blade"))
                || !"x".equals(string(axes, "flat_normal"))
                || !"z".equals(string(axes, "edge"))) {
            throw new IllegalArgumentException("Sword geometry axes must be blade +y, flat_normal x, edge z");
        }
        Vector3f grip = point(json, "grip_center");
        JsonObject handle = object(json, "handle");
        JsonObject guard = object(json, "guard");
        JsonObject pommel = object(json, "pommel");
        float[] handleY = range(handle, "handle");
        float[] guardY = range(guard, "guard");
        float[] pommelY = range(pommel, "pommel");
        Vector3f base = point(json, "blade_base");
        Vector3f tip = point(json, "blade_tip");
        float handleHalfWidth = positive(handle, "half_width", "handle");
        float handleHalfThickness = positive(handle, "half_thickness", "handle");
        float guardHalfWidth = positive(guard, "half_width", "guard");
        float guardHalfThickness = positive(guard, "half_thickness", "guard");
        if (!(pommelY[1] <= handleY[0] + 1.0E-3F
                && handleY[1] <= guardY[0] + 1.0E-3F
                && guardY[1] <= base.y + 1.0E-3F
                && base.y < tip.y)) {
            throw new IllegalArgumentException(
                    "Sword geometry must stack pommel, handle, guard, blade base and tip along +Y");
        }
        if (!(grip.y > handleY[0] && grip.y < handleY[1])) {
            throw new IllegalArgumentException("Sword grip_center must lie on the handle");
        }
        // The blade must run along the grip axis (+Y), so the grip frame and the drawn blade agree.
        Vector3f bladeAxis = new Vector3f(tip).sub(base).normalize();
        if (bladeAxis.y < 0.999F || Math.abs(base.x - grip.x) > 0.05F || Math.abs(base.z - grip.z) > 0.05F) {
            throw new IllegalArgumentException("Sword blade must run along +Y through the grip axis");
        }
        Vector3f offHand = null;
        if (json.has("off_hand_grip_center")) {
            offHand = point(json, "off_hand_grip_center");
            if (Math.abs(offHand.x - grip.x) > 0.05F || Math.abs(offHand.z - grip.z) > 0.05F) {
                throw new IllegalArgumentException("Sword off_hand_grip_center must lie on the handle axis");
            }
            if (!(offHand.y > handleY[0] && offHand.y < handleY[1])) {
                throw new IllegalArgumentException("Sword off_hand_grip_center must lie on the handle");
            }
            if (!(offHand.y > grip.y)) {
                throw new IllegalArgumentException("Sword off_hand_grip_center must lie ahead of grip_center");
            }
        }
        Vector3f trailBase = base;
        Vector3f trailTip = tip;
        if (json.has("trail")) {
            JsonObject trail = object(json, "trail");
            trailBase = point(trail, "trail.base", "base");
            trailTip = point(trail, "trail.tip", "tip");
            for (Vector3f point : new Vector3f[] {trailBase, trailTip}) {
                if (Math.abs(point.x - grip.x) > 0.05F || Math.abs(point.z - grip.z) > 0.05F) {
                    throw new IllegalArgumentException("Sword trail must lie on the weapon axis");
                }
            }
            if (!(trailBase.y < trailTip.y)) {
                throw new IllegalArgumentException("Sword trail base must lie below its tip");
            }
            if (trailBase.y < pommelY[0] - 1.0E-3F || trailTip.y > tip.y + 1.0E-3F) {
                throw new IllegalArgumentException("Sword trail must lie on the weapon, from the pommel to the blade tip");
            }
        }
        return new SwordGeometry(grip, handleY, handleHalfWidth, handleHalfThickness,
                guardY, guardHalfWidth, guardHalfThickness, pommelY, base, tip, offHand, trailBase, trailTip);
    }

    /** Model-pixel point relative to the grip centre, scaled to blocks by {@code swordScale / 16}. */
    Vector3f toGrip(Vector3f modelPixels, float swordScale) {
        return new Vector3f(modelPixels).sub(gripCenter).mul(swordScale / 16.0F);
    }

    /** A point on the sword's centre axis at model height {@code y}, in the grip frame. */
    Vector3f axisPoint(float y, float swordScale) {
        return toGrip(new Vector3f(gripCenter.x, y, gripCenter.z), swordScale);
    }

    Vector3f gripCenter() {
        return new Vector3f(gripCenter);
    }

    /** The leading (off) hand's grip point, for a two-handed weapon. */
    Optional<Vector3f> offHandGripCenter() {
        return Optional.ofNullable(offHandGripCenter).map(Vector3f::new);
    }

    Vector3f bladeBase() {
        return new Vector3f(bladeBase);
    }

    Vector3f bladeTip() {
        return new Vector3f(bladeTip);
    }

    float bladeLengthPixels() {
        return bladeTip.distance(bladeBase);
    }

    /** Where the trail starts on the weapon: the contract's {@code trail.base}, else {@code blade_base}. */
    Vector3f trailBase() {
        return new Vector3f(trailBase);
    }

    /** Where the trail ends on the weapon: the contract's {@code trail.tip}, else {@code blade_tip}. */
    Vector3f trailTip() {
        return new Vector3f(trailTip);
    }

    /** Length of the trail span in model pixels. */
    float trailLengthPixels() {
        return trailTip.distance(trailBase);
    }

    /** Distance from the grip centre to the trail tip in model pixels: how far the weapon reaches. */
    float gripToTrailTipPixels() {
        return trailTip.distance(gripCenter);
    }

    float handleBottom() {
        return handleBottom;
    }

    float handleTop() {
        return handleTop;
    }

    float handleHalfWidth() {
        return handleHalfWidth;
    }

    float handleHalfThickness() {
        return handleHalfThickness;
    }

    float guardBottom() {
        return guardBottom;
    }

    float guardTop() {
        return guardTop;
    }

    float guardHalfWidth() {
        return guardHalfWidth;
    }

    float guardHalfThickness() {
        return guardHalfThickness;
    }

    float pommelBottom() {
        return pommelBottom;
    }

    float pommelTop() {
        return pommelTop;
    }

    private static String string(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static JsonObject object(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Sword geometry needs a " + name + " object");
        }
        return element.getAsJsonObject();
    }

    private static Vector3f point(JsonObject json, String name) {
        return point(json, name, name);
    }

    private static Vector3f point(JsonObject json, String label, String name) {
        float[] values = numbers(json.get(name), label, 3);
        return new Vector3f(values[0], values[1], values[2]);
    }

    private static float[] range(JsonObject json, String label) {
        float[] values = numbers(json.get("y"), label + ".y", 2);
        if (!(values[0] < values[1])) {
            throw new IllegalArgumentException(label + ".y must increase");
        }
        return values;
    }

    private static float positive(JsonObject json, String name, String label) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(label + "." + name + " is missing");
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw new IllegalArgumentException(label + "." + name + " must be positive");
        }
        return value;
    }

    private static float[] numbers(JsonElement element, String label, int size) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != size) {
            throw new IllegalArgumentException(label + " must have " + size + " numbers");
        }
        JsonArray array = element.getAsJsonArray();
        float[] values = new float[size];
        for (int index = 0; index < size; index++) {
            values[index] = array.get(index).getAsFloat();
            if (!Float.isFinite(values[index])) {
                throw new IllegalArgumentException(label + " must be finite");
            }
        }
        return values;
    }
}
