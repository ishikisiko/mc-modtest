package com.example.myvillage.worldplan;

import com.google.gson.*;
import java.util.*;
import java.util.function.BiFunction;

/** V3: preserves the exact V1 biome except where a bounded local probe finds a
 * configured incompatible neighbour. Probes always read the unmodified source,
 * so transitions cannot propagate, depend on visit order or consume interiors. */
public final class WorldPlanEcotone {
    private record Transition(String from, Set<String> near, String biome) {}
    public final int radius;
    public final boolean clearSpawnCanopy;
    private final Map<String,String> groups;
    private final List<Transition> transitions;
    private final List<int[]> offsets;
    private final Set<String> biomes;

    public WorldPlanEcotone(JsonObject json, Set<String> requiredBiomes) {
        if(json==null)throw new IllegalArgumentException("V3 requires local ecotone rules");
        JsonElement n=json.get("radius");
        if(n==null||!n.isJsonPrimitive()||!n.getAsJsonPrimitive().isNumber()||!Double.isFinite(n.getAsDouble())
            ||n.getAsDouble()!=n.getAsInt()||n.getAsInt()<4||n.getAsInt()>64)
            throw new IllegalArgumentException("Ecotone radius must be an integer in [4,64]");
        radius=n.getAsInt();
        JsonElement canopy=json.get("clear_spawn_canopy");
        if(canopy==null||!canopy.isJsonPrimitive()||!canopy.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Missing canopy rule");
        clearSpawnCanopy=canopy.getAsBoolean();
        Map<String,String> mapping=new HashMap<>();Set<String> groupIds=new HashSet<>();
        for(var e:json.getAsJsonObject("groups").entrySet()){
            groupIds.add(e.getKey());
            for(var value:e.getValue().getAsJsonArray()){
                String id=biomeId(value);
                if(mapping.put(id,e.getKey())!=null)throw new IllegalArgumentException("Duplicate ecotone biome "+id);
            }
        }
        if(!mapping.keySet().containsAll(requiredBiomes))throw new IllegalArgumentException("Ecotone groups must cover all original biomes");
        groups=Map.copyOf(mapping);List<Transition> rules=new ArrayList<>();Set<String> outputs=new HashSet<>();Set<String> pairs=new HashSet<>();
        for(var value:json.getAsJsonArray("transitions")){
            JsonObject o=value.getAsJsonObject();String from=o.get("from").getAsString();Set<String> near=new HashSet<>();
            if(!groupIds.contains(from))throw new IllegalArgumentException("Unknown ecotone source group");
            for(var g:o.getAsJsonArray("near")){
                String peer=g.getAsString();
                if(!groupIds.contains(peer)||peer.equals(from)||!pairs.add(from+":"+peer))throw new IllegalArgumentException("Invalid ecotone peer");
                near.add(peer);
            }
            if(near.isEmpty())throw new IllegalArgumentException("Empty ecotone transition");
            String result=biomeId(o.get("biome"));outputs.add(result);rules.add(new Transition(from,Set.copyOf(near),result));
        }
        transitions=List.copyOf(rules);biomes=Set.copyOf(outputs);
        int d=(int)StrictMath.floor(radius/StrictMath.sqrt(2));
        offsets=List.of(new int[]{radius,0},new int[]{-radius,0},new int[]{0,radius},new int[]{0,-radius},
            new int[]{d,d},new int[]{d,-d},new int[]{-d,d},new int[]{-d,-d});
    }
    public Set<String> biomes(){return biomes;}
    public String group(String biome){return Objects.requireNonNull(groups.get(biome),"Unclassified source biome "+biome);}
    public String apply(int x,int z,String original,BiFunction<Integer,Integer,String> source){
        String own=group(original);
        List<Transition> eligible=transitions.stream().filter(t->t.from.equals(own)).toList();
        if(eligible.isEmpty())return original;
        Set<String> neighbours=new HashSet<>();
        for(int[] p:offsets)neighbours.add(group(source.apply(x+p[0],z+p[1])));
        // Authored rule order resolves a three-way meeting deterministically.
        for(var t:eligible)if(t.near.stream().anyMatch(neighbours::contains))return t.biome;
        return original;
    }
    private static String biomeId(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!value.getAsString().matches("minecraft:[a-z0-9_]+"))
            throw new IllegalArgumentException("Invalid ecotone biome ID");
        return value.getAsString();
    }
}
