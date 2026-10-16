package com.example.myvillage.forging;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Shared bounded batch rules; the bundled acquisition source also owns worldgen parameters. */
public record InfusionRules(int maxBatch,int qiPerIngot,int qiPerPulse,int pulseTicks) {
    public static final String RESOURCE="/data/myvillage/forging/acquisition.json";
    public static final Codec<InfusionRules> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(1,64).fieldOf("max_batch").forGetter(InfusionRules::maxBatch),
            Codec.intRange(1,10000).fieldOf("qi_per_ingot").forGetter(InfusionRules::qiPerIngot),
            Codec.intRange(1,10000).fieldOf("qi_per_pulse").forGetter(InfusionRules::qiPerPulse),
            Codec.intRange(1,1200).fieldOf("pulse_ticks").forGetter(InfusionRules::pulseTicks)).apply(i,InfusionRules::new));
    public InfusionRules {
        if(maxBatch<1||maxBatch>64||qiPerIngot<1||qiPerIngot>10000||qiPerPulse<1||qiPerPulse>qiPerIngot||pulseTicks<1||pulseTicks>1200)
            throw new IllegalArgumentException("Invalid infusion rules");
    }
    public record Pulse(int paidQi,int cost,boolean complete) {}
    public Optional<Pulse> plan(int count,int paidQi,long availableQi) {
        if(count<1||count>maxBatch||paidQi<0||paidQi>10000||availableQi<0)return Optional.empty();
        int next=Math.min(qiPerIngot,paidQi+qiPerPulse);
        int cost=Math.max(0,next-paidQi)*count;
        return cost>availableQi?Optional.empty():Optional.of(new Pulse(next,cost,next==qiPerIngot));
    }
    private static final class Holder {
        static final InfusionRules VALUE=load();
        private static InfusionRules load() {
            try(var in=InfusionRules.class.getResourceAsStream(RESOURCE)) {
                if(in==null)throw new IllegalStateException("Missing "+RESOURCE);
                var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Invalid acquisition version");
                return CODEC.parse(JsonOps.INSTANCE,root.get("infusion")).getOrThrow();
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
    }
    public static InfusionRules get(){return Holder.VALUE;}
}
