package com.example.myvillage.cultivation.crafting;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Per-fate contributions depend only on permanent identity and authored rules, never selection order or world seed. */
public final class CraftingOpeningRules {
    public static final String RESOURCE="/data/myvillage/crafting/opening.json";
    public static final int CHOICES=3;
    public record Delta(int value,int weight){}
    public record Selection(List<String> ids,int alchemyOverride,int forgingOverride) {
        public static final Selection PENDING=new Selection(List.of(),-1,-1);
        public Selection {
            ids=ids.stream().sorted().toList();
            if((!ids.isEmpty()&&ids.size()!=CHOICES)||new HashSet<>(ids).size()!=ids.size()
                    ||ids.stream().anyMatch(id->!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
                    ||alchemyOverride < -1||forgingOverride < -1
                    ||ids.isEmpty()&&(alchemyOverride!=-1||forgingOverride!=-1))
                throw new IllegalArgumentException("Invalid opening crafting selection");
        }
        public boolean confirmed(){return !ids.isEmpty();}
    }
    private final String domain;
    private final int base,total,min,max;
    private final List<Delta> deltas;
    private CraftingOpeningRules(JsonObject root){
        fields(root,"version","hash_domain","base","weighted_deltas");integer(root,"version",1,1);
        var raw=root.get("hash_domain");
        if(raw==null||!raw.isJsonPrimitive()||!raw.getAsJsonPrimitive().isString())throw invalid("hash_domain");
        domain=raw.getAsString();if(!domain.matches("[a-z0-9:_-]{1,128}"))throw invalid("hash_domain");
        base=integer(root,"base",0,Integer.MAX_VALUE);
        if(!root.get("weighted_deltas").isJsonArray()||root.getAsJsonArray("weighted_deltas").size()==0)throw invalid("weighted_deltas");
        var parsed=new ArrayList<Delta>();var seen=new HashSet<Integer>();long weight=0;
        for(var value:root.getAsJsonArray("weighted_deltas")){
            if(!value.isJsonObject())throw invalid("weighted delta");var row=value.getAsJsonObject();fields(row,"value","weight");
            int delta=integer(row,"value",Integer.MIN_VALUE,Integer.MAX_VALUE),count=integer(row,"weight",1,Integer.MAX_VALUE);
            if(!seen.add(delta))throw invalid("duplicate delta");weight+=count;parsed.add(new Delta(delta,count));
        }
        if(weight>Integer.MAX_VALUE)throw invalid("weight total");total=(int)weight;deltas=List.copyOf(parsed);
        long lower=base+(long)CHOICES*deltas.stream().mapToInt(Delta::value).min().orElseThrow();
        long upper=base+(long)CHOICES*deltas.stream().mapToInt(Delta::value).max().orElseThrow();
        if(lower<0||upper>Integer.MAX_VALUE)throw invalid("opening score range");min=(int)lower;max=(int)upper;
    }
    private static final class Holder{static final CraftingOpeningRules VALUE=load();}
    public static CraftingOpeningRules get(){return Holder.VALUE;}
    private static CraftingOpeningRules load(){
        try(var in=CraftingOpeningRules.class.getResourceAsStream(RESOURCE)){
            if(in==null)throw new IllegalStateException("Missing "+RESOURCE);
            return parse(new InputStreamReader(in,StandardCharsets.UTF_8));
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static CraftingOpeningRules parse(Reader source){return new CraftingOpeningRules(CraftingQualityRules.readDocument(source));}
    public int base(){return base;}public String domain(){return domain;}public List<Delta> deltas(){return deltas;}
    public int totalWeight(){return total;}public int initialMinimum(){return min;}public int initialMaximum(){return max;}
    public int deltaForBucket(int bucket){
        if(bucket<0||bucket>=total)throw invalid("bucket");long bound=0;
        for(var delta:deltas){bound+=delta.weight();if(bucket<bound)return delta.value();}throw invalid("bucket");
    }
    public int contribution(UUID player,CraftingAptitudes.Profession profession,String fate){
        if(profession==CraftingAptitudes.Profession.TALISMAN)throw invalid("profession");
        if(!fate.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw invalid("fate id");
        var identity=ByteBuffer.allocate(16).putLong(player.getMostSignificantBits()).putLong(player.getLeastSignificantBits()).array();
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            for(byte[] field:List.of(domain.getBytes(StandardCharsets.UTF_8),identity,
                    profession.name().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),fate.getBytes(StandardCharsets.UTF_8))){out.writeInt(field.length);out.write(field);}
            var input=bytes.toByteArray();var digest=MessageDigest.getInstance("SHA-256");
            long limit=(1L<<32)-(1L<<32)%total;
            for(int counter=0;;counter++){
                digest.update(input);byte[] hash=digest.digest(ByteBuffer.allocate(4).putInt(counter).array());
                long value=Integer.toUnsignedLong(ByteBuffer.wrap(hash).getInt());
                if(value<limit)return deltaForBucket((int)(value%total));
            }
        }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public int score(UUID player,CraftingAptitudes.Profession profession,Selection selection){
        if(!selection.confirmed()||profession==CraftingAptitudes.Profession.TALISMAN)throw invalid("selection");
        int override=profession==CraftingAptitudes.Profession.ALCHEMY?selection.alchemyOverride():selection.forgingOverride();
        if(override>=0)return override;
        int value=base;for(String id:selection.ids())value=Math.addExact(value,contribution(player,profession,id));return value;
    }
    private static IllegalArgumentException invalid(String key){return new IllegalArgumentException("Invalid opening crafting rules: "+key);}
    private static void fields(JsonObject value,String... keys){if(!value.keySet().equals(Set.of(keys)))throw invalid("fields");}
    private static int integer(JsonObject root,String key,int min,int max){
        var value=root.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw invalid(key);
        try{int number=value.getAsBigDecimal().intValueExact();if(number<min||number>max)throw invalid(key);return number;}
        catch(ArithmeticException|NumberFormatException e){throw invalid(key);}
    }
}
