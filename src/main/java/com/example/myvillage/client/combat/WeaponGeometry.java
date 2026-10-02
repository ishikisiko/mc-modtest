package com.example.myvillage.client.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A weapon's geometry contract (format 2), loaded from the {@code geometry} asset its weapon entry
 * names and written by the model generator its {@code generator} field names. All values are
 * item-model pixels (16 per block): the weapon runs along +Y ({@code axes.length}), the flat normal
 * along X, the edge along Z, the same axes as the first-person grip frame. From the bottom up the
 * weapon is the {@code butt}, the {@code handle}, the {@code collar} and the {@code head} from
 * {@code head_base} to {@code head_tip} (for a sword: pommel, grip, guard and blade). Nothing about
 * the weapon's shape is hard-coded in Java; the grip, trail and arm all read this.
 *
 * <p>A two-handed weapon also gives {@code off_hand_grip_center}, the leading hand's point: on the
 * handle axis, inside the handle and ahead of (above) {@code grip_center}. A one-handed weapon
 * omits it.
 *
 * <p>The optional {@code trail} block ({@code {"base": [x, y, z], "tip": [x, y, z]}}) names the part
 * of the weapon that draws the 剑光 trails: on the weapon axis, base below tip, within the weapon
 * from the butt's bottom to the head tip. Without it the trail spans {@code head_base} to
 * {@code head_tip}, which suits a sword; a polearm whose head is short names a longer span.
 *
 * <p>Format 1 used sword names; a contract that is not format 2, or that still has a format 1
 * field, is rejected with the format 2 name rather than read with a default.
 */
final class WeaponGeometry {
    static final int FORMAT = 2;
    /** Format 1 field names and the format 2 names that replaced them, in contract order. */
    static final List<Map.Entry<String, String>> RENAMED_FIELDS = List.of(
            Map.entry("guard", "collar"),
            Map.entry("pommel", "butt"),
            Map.entry("blade", "head"),
            Map.entry("blade_base", "head_base"),
            Map.entry("blade_tip", "head_tip"));
    static final List<Map.Entry<String, String>> RENAMED_AXES = List.of(Map.entry("blade", "length"));

    private final Vector3f gripCenter;
    private final float handleBottom;
    private final float handleTop;
    private final float handleHalfWidth;
    private final float handleHalfThickness;
    private final float collarBottom;
    private final float collarTop;
    private final float collarHalfWidth;
    private final float collarHalfThickness;
    private final float buttBottom;
    private final float buttTop;
    private final Vector3f headBase;
    private final Vector3f headTip;
    private final Vector3f offHandGripCenter;
    private final Vector3f trailBase;
    private final Vector3f trailTip;

    private WeaponGeometry(
            Vector3f gripCenter,
            float[] handle,
            float handleHalfWidth,
            float handleHalfThickness,
            float[] collar,
            float collarHalfWidth,
            float collarHalfThickness,
            float[] butt,
            Vector3f headBase,
            Vector3f headTip,
            Vector3f offHandGripCenter,
            Vector3f trailBase,
            Vector3f trailTip) {
        this.gripCenter = gripCenter;
        this.handleBottom = handle[0];
        this.handleTop = handle[1];
        this.handleHalfWidth = handleHalfWidth;
        this.handleHalfThickness = handleHalfThickness;
        this.collarBottom = collar[0];
        this.collarTop = collar[1];
        this.collarHalfWidth = collarHalfWidth;
        this.collarHalfThickness = collarHalfThickness;
        this.buttBottom = butt[0];
        this.buttTop = butt[1];
        this.headBase = headBase;
        this.headTip = headTip;
        this.offHandGripCenter = offHandGripCenter;
        this.trailBase = trailBase;
        this.trailTip = trailTip;
    }

    static WeaponGeometry parse(JsonObject json) {
        Objects.requireNonNull(json, "json");
        checkFormat(json);
        if (!"model_pixels".equals(string(json, "units"))) {
            throw new IllegalArgumentException("Weapon geometry units must be model_pixels");
        }
        JsonObject axes = json.getAsJsonObject("axes");
        if (axes == null
                || !"+y".equals(string(axes, "length"))
                || !"x".equals(string(axes, "flat_normal"))
                || !"z".equals(string(axes, "edge"))) {
            throw new IllegalArgumentException("Weapon geometry axes must be length +y, flat_normal x, edge z");
        }
        Vector3f grip = point(json, "grip_center");
        JsonObject handle = object(json, "handle");
        JsonObject collar = object(json, "collar");
        JsonObject butt = object(json, "butt");
        float[] handleY = range(handle, "handle");
        float[] collarY = range(collar, "collar");
        float[] buttY = range(butt, "butt");
        Vector3f base = point(json, "head_base");
        Vector3f tip = point(json, "head_tip");
        float handleHalfWidth = positive(handle, "half_width", "handle");
        float handleHalfThickness = positive(handle, "half_thickness", "handle");
        float collarHalfWidth = positive(collar, "half_width", "collar");
        float collarHalfThickness = positive(collar, "half_thickness", "collar");
        if (!(buttY[1] <= handleY[0] + 1.0E-3F
                && handleY[1] <= collarY[0] + 1.0E-3F
                && collarY[1] <= base.y + 1.0E-3F
                && base.y < tip.y)) {
            throw new IllegalArgumentException(
                    "Weapon geometry must stack butt, handle, collar, head base and tip along +Y");
        }
        if (!(grip.y > handleY[0] && grip.y < handleY[1])) {
            throw new IllegalArgumentException("Weapon grip_center must lie on the handle");
        }
        // The head must run along the grip axis (+Y), so the grip frame and the drawn weapon agree.
        Vector3f headAxis = new Vector3f(tip).sub(base).normalize();
        if (headAxis.y < 0.999F || Math.abs(base.x - grip.x) > 0.05F || Math.abs(base.z - grip.z) > 0.05F) {
            throw new IllegalArgumentException("Weapon head must run along +Y through the grip axis");
        }
        Vector3f offHand = null;
        if (json.has("off_hand_grip_center")) {
            offHand = point(json, "off_hand_grip_center");
            if (Math.abs(offHand.x - grip.x) > 0.05F || Math.abs(offHand.z - grip.z) > 0.05F) {
                throw new IllegalArgumentException("Weapon off_hand_grip_center must lie on the handle axis");
            }
            if (!(offHand.y > handleY[0] && offHand.y < handleY[1])) {
                throw new IllegalArgumentException("Weapon off_hand_grip_center must lie on the handle");
            }
            if (!(offHand.y > grip.y)) {
                throw new IllegalArgumentException("Weapon off_hand_grip_center must lie ahead of grip_center");
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
                    throw new IllegalArgumentException("Weapon trail must lie on the weapon axis");
                }
            }
            if (!(trailBase.y < trailTip.y)) {
                throw new IllegalArgumentException("Weapon trail base must lie below its tip");
            }
            if (trailBase.y < buttY[0] - 1.0E-3F || trailTip.y > tip.y + 1.0E-3F) {
                throw new IllegalArgumentException("Weapon trail must lie on the weapon, from the butt to the head tip");
            }
        }
        return new WeaponGeometry(grip, handleY, handleHalfWidth, handleHalfThickness,
                collarY, collarHalfWidth, collarHalfThickness, buttY, base, tip, offHand, trailBase, trailTip);
    }

    /**
     * Rejects a contract that is not format 2 or still uses a format 1 name, naming the new field,
     * so an old contract fails loudly instead of being read with defaults.
     */
    private static void checkFormat(JsonObject json) {
        JsonElement format = json.get("format");
        if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()
                || format.getAsDouble() != FORMAT) {
            throw new IllegalArgumentException("Weapon geometry format must be " + FORMAT + ", got " + format
                    + " (format 2 renamed axes.blade to axes.length, pommel to butt, guard to collar, blade to head,"
                    + " blade_base to head_base and blade_tip to head_tip)");
        }
        for (Map.Entry<String, String> renamed : RENAMED_FIELDS) {
            if (json.has(renamed.getKey())) {
                throw new IllegalArgumentException("Weapon geometry field " + renamed.getKey()
                        + " was renamed " + renamed.getValue() + " in format " + FORMAT);
            }
        }
        JsonElement axes = json.get("axes");
        for (Map.Entry<String, String> renamed : RENAMED_AXES) {
            if (axes != null && axes.isJsonObject() && axes.getAsJsonObject().has(renamed.getKey())) {
                throw new IllegalArgumentException("Weapon geometry field axes." + renamed.getKey()
                        + " was renamed axes." + renamed.getValue() + " in format " + FORMAT);
            }
        }
    }

    /** Model-pixel point relative to the grip centre, scaled to blocks by {@code weaponScale / 16}. */
    Vector3f toGrip(Vector3f modelPixels, float weaponScale) {
        return new Vector3f(modelPixels).sub(gripCenter).mul(weaponScale / 16.0F);
    }

    /** A point on the weapon's centre axis at model height {@code y}, in the grip frame. */
    Vector3f axisPoint(float y, float weaponScale) {
        return toGrip(new Vector3f(gripCenter.x, y, gripCenter.z), weaponScale);
    }

    Vector3f gripCenter() {
        return new Vector3f(gripCenter);
    }

    /** The leading (off) hand's grip point, for a two-handed weapon. */
    Optional<Vector3f> offHandGripCenter() {
        return Optional.ofNullable(offHandGripCenter).map(Vector3f::new);
    }

    Vector3f headBase() {
        return new Vector3f(headBase);
    }

    Vector3f headTip() {
        return new Vector3f(headTip);
    }

    float headLengthPixels() {
        return headTip.distance(headBase);
    }

    /** Where the trail starts on the weapon: the contract's {@code trail.base}, else {@code head_base}. */
    Vector3f trailBase() {
        return new Vector3f(trailBase);
    }

    /** Where the trail ends on the weapon: the contract's {@code trail.tip}, else {@code head_tip}. */
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

    float collarBottom() {
        return collarBottom;
    }

    float collarTop() {
        return collarTop;
    }

    float collarHalfWidth() {
        return collarHalfWidth;
    }

    float collarHalfThickness() {
        return collarHalfThickness;
    }

    float buttBottom() {
        return buttBottom;
    }

    float buttTop() {
        return buttTop;
    }

    private static String string(JsonObject json, String name) {
        JsonElement element = json.get(name);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static JsonObject object(JsonObject json, String name) {
        JsonElement element = json.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Weapon geometry needs a " + name + " object");
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
