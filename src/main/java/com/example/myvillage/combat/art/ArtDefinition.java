package com.example.myvillage.combat.art;

import com.example.myvillage.combat.definition.*;
import com.example.myvillage.combat.elemental.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled multi-phase arts, independent of the single travelling-wave skill schema.
 * Cost/timing/shape/feedback values are authored in tools/advanced_arts/arts.json.
 * Phase timings and PAL/first-person poses retain the referenced move's exact contract.
 */
public record ArtDefinition(ResourceLocation id, ResourceLocation school, String family, int slot,
        int qiCost, int cooldownTicks, double guardReduction, double guardArc,
        double counterMultiplier, boolean requiresGround, boolean interruptOnDamage, int fxLifetimeTicks, float castPitch,
        float red, float green, float blue, int particleCount,
        List<Phase> phases, Optional<LockedSwordDefinition> lock, Optional<ElementalSpell> elemental) {
    public ArtDefinition { phases = List.copyOf(phases); }
    /** The locked sword timeline is shared by server resolution and client animation. */
    public AttackMoveDefinition move(int phase, double scale) {
        var base = phases.get(phase).move(CombatStyles.bundled(), scale);
        return elemental.map(e -> e.move(base)).orElseGet(() -> lock.map(l -> l.move(base)).orElse(base));
    }
    public String translationKey() { return "cultivation.technique." + id.getNamespace() + "." + id.getPath(); }
    public record Phase(ResourceLocation moveId, String shape, double reach, double arc,
            double width, double height, double halfHeight, double damage,
            int targets, double stepDistance, ReactionDefinition reaction, String fx) {
        public AttackMoveDefinition move(CombatStyles styles, double counterScale) {
            AttackMoveDefinition base = styles.move(moveId).orElseThrow().move();
            Optional<StepDefinition> step = stepDistance > 0
                    ? Optional.of(new StepDefinition(base.step().map(StepDefinition::actionTick)
                            .orElse(Math.max(0, base.activeStartTick() - 1)), stepDistance,
                            base.step().map(StepDefinition::supportDepth).orElse(.35))) : base.step();
            return new AttackMoveDefinition(base.id(), base.displayKey(), base.kind(), base.totalTicks(),
                    base.activeStartTick(), base.activeEndTick(), damage * counterScale, targets, reach,
                    base.bufferStartTick(), base.chainTick(), reaction, base.animation(), base.hitbox(),
                    step, base.feedback(), base.camera(), base.trail());
        }
    }
    private static class Holder {
        static final List<ArtDefinition> ALL = load();
    }
    public static List<ArtDefinition> all() { return Holder.ALL; }
    public static Optional<ArtDefinition> find(ResourceLocation id) {
        return all().stream().filter(a -> a.id.equals(id)).findFirst();
    }
    public static Optional<ArtDefinition> forSlot(String family, int slot) {
        return all().stream().filter(a -> slot >= 0 && a.slot == slot && a.family.equals(family)).findFirst();
    }
    private static List<ArtDefinition> load() {
        String path = "/data/myvillage/combat/advanced_arts.json";
        try (var stream = ArtDefinition.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing " + path);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (integer(root, "schema", 1, 1) != 1) throw new IllegalArgumentException("Art schema");
            var result = new ArrayList<ArtDefinition>();
            Set<ResourceLocation> ids = new HashSet<>();
            Set<String> slots = new HashSet<>();
            CombatStyles styles = CombatStyles.bundled();
            JsonArray rows = root.getAsJsonArray("arts");
            rows.addAll(LockedSwordDefinition.document().getAsJsonArray("arts"));
            rows.addAll(ElementalRules.document().getAsJsonArray("arts"));
            if (rows.isEmpty() || rows.size() > 64) throw new IllegalArgumentException("Art count 1..64");
            for (var element : rows) {
                var r = element.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(string(r, "id"));
                ResourceLocation school = ResourceLocation.parse(string(r, "school"));
                String family = string(r, "family");
                var lock = r.has("lock") ? Optional.of(LockedSwordDefinition.parse(r.getAsJsonObject("lock")))
                        : Optional.<LockedSwordDefinition>empty();
                var elemental = r.has("elemental") ? Optional.of(ElementalSpell.parse(r.getAsJsonObject("elemental"))) : Optional.<ElementalSpell>empty();
                if(elemental.isPresent() && (lock.isPresent() || !family.equals("elemental"))) throw new IllegalArgumentException("Elemental executor mismatch");
                int slot = integer(r, "slot", lock.isPresent() || elemental.isPresent() ? -1 : 0, 1);
                if(elemental.isPresent() && slot!=-1)throw new IllegalArgumentException("Elemental arts require explicit bindings");
                if (!ids.add(id) || (slot >= 0 && !slots.add(family + ":" + slot))) throw new IllegalArgumentException("Duplicate art/slot " + id);
                if (lock.isPresent() && (!family.equals("sword") || slot != -1))
                    throw new IllegalArgumentException("Locked swords are explicitly bound sword arts");
                List<Phase> phases = new ArrayList<>();
                for (var pe : r.getAsJsonArray("phases")) {
                    var p = pe.getAsJsonObject();
                    ResourceLocation moveId = ResourceLocation.parse(string(p, "move"));
                    var ref = styles.move(moveId).orElseThrow(() -> new IllegalArgumentException("Unknown move " + moveId));
                    if (elemental.isEmpty() && styles.weapons().stream().noneMatch(w -> w.style().equals(ref.style().id())
                            && w.family().equals(Optional.of(family)))) throw new IllegalArgumentException("Wrong move family " + id);
                    var reaction = p.getAsJsonObject("reaction");
                    String shape = string(p, "shape"), fx = string(p, "fx");
                    if (!Set.of("line", "fan", "ring").contains(shape)
                            || !Set.of("arc", "helix", "ring", "lotus", "shock").contains(fx))
                        throw new IllegalArgumentException("Unsupported art shape/fx " + id);
                    phases.add(new Phase(moveId, shape, number(p,"reach",.1,16), number(p,"arc_degrees",1,360),
                            number(p,"width",.1,3), number(p,"height",.1,3), number(p,"half_height",.1,2),
                            number(p,"damage_multiplier",.01,4), integer(p,"maximum_targets",1,12),
                            number(p,"step_distance",0,StepDefinition.MAXIMUM_STEP_DISTANCE),
                            new ReactionDefinition(integer(reaction,"hitstun_ticks",0,60),
                                    number(reaction,"slide_distance",0,4), number(reaction,"lift",0,.6),
                                    number(reaction,"lateral_bias",-1,1)), fx));
                }
                if (phases.isEmpty() || phases.size() > 6) throw new IllegalArgumentException("Phase count 1..6");
                JsonArray c = r.getAsJsonArray("color");
                if (c.size() != 3) throw new IllegalArgumentException("Art color");
                float[] rgb = new float[3];
                for (int i=0;i<3;i++) {
                    rgb[i] = c.get(i).getAsFloat();
                    if (!Float.isFinite(rgb[i]) || rgb[i]<0 || rgb[i]>1) throw new IllegalArgumentException("Art color");
                }
                if ((lock.isPresent() || elemental.isPresent()) && phases.size() != 1) throw new IllegalArgumentException("Locked sword has one timeline");
                int cost = lock.isPresent() ? LockedSwordDefinition.techniqueCost(id) : integer(r,"qi_cost",1,1000);
                result.add(new ArtDefinition(id, school, family, slot, cost,
                        integer(r,"cooldown_ticks",20,1200), number(r,"guard_reduction",0,.85),
                        number(r,"guard_arc_degrees",1,180), number(r,"counter_multiplier",1,2),
                        bool(r,"requires_ground"),bool(r,"interrupt_on_damage"),integer(r,"fx_lifetime_ticks",1,40),
                        (float)number(r,"cast_pitch",.1,2),rgb[0],rgb[1],rgb[2],integer(r,"particle_count",8,64), phases, lock, elemental));
            }
            return List.copyOf(result);
        } catch (Exception e) { throw new IllegalStateException("Invalid bundled advanced arts: " + path, e); }
    }
    private static boolean bool(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("Expected boolean " + key);
        return o.get(key).getAsBoolean();
    }
    private static String string(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Expected string " + key);
        return o.get(key).getAsString();
    }
    private static double number(JsonObject o, String key, double min, double max) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Expected number " + key);
        double n = o.get(key).getAsDouble();
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("Out of bounds " + key);
        return n;
    }
    private static int integer(JsonObject o, String key, int min, int max) {
        double n = number(o,key,min,max);
        if (n != Math.rint(n)) throw new IllegalArgumentException("Expected integer " + key);
        return (int)n;
    }
}
