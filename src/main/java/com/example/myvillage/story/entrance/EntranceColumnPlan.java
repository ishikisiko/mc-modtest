package com.example.myvillage.story.entrance;

import java.util.function.IntFunction;

/** A bounded dry earthwork column. Tree canopies bound clearing, never carry the foundation. */
public record EntranceColumnPlan(int foundationTop, int ground, int clearTop, int queries) {
    public enum Kind { AIR, DECORATION, FOUNDATION, FLUID, PROTECTED }
    public long writeBudget() {return (long)ground-foundationTop+1+clearTop-ground;}
    public static EntranceColumnPlan plan(int surface,int ground,int clearance,int minBuildHeight,int maxBuildHeight,
                                         int maxCut,int maxFill,int foundationDepth,IntFunction<Kind> sample) {
        if(surface-ground>maxCut||ground<minBuildHeight||ground+clearance>=maxBuildHeight)
            throw new IllegalArgumentException("入口切填或高度超过配置预算");
        // FULL-chunk WORLD_SURFACE proves all cells above surface are air. It is only a clearing
        // ceiling; the separate bounded scan below still proves the load-bearing base.
        int top=Math.max(surface,ground),queries=0;
        for(int y=top;y>ground;y--) {check(sample.apply(y));queries++;}
        int run=0,lower=Math.max(minBuildHeight,ground-maxFill-foundationDepth+1);
        for(int y=ground;y>=lower;y--) {
            Kind k=sample.apply(y);queries++;check(k);
            run=k==Kind.FOUNDATION?run+1:0;
            if(run==foundationDepth) {
                int foundation=y+foundationDepth-1;
                if(ground-foundation>maxFill)break;
                return new EntranceColumnPlan(foundation,ground,top,queries);
            }
        }
        throw new IllegalArgumentException("入口有界范围内没有连续天然土石地基（空洞或基础过深）");
    }
    private static void check(Kind k) {
        if(k==Kind.FLUID)throw new IllegalArgumentException("入口候选含水体或熔岩，不自动排水填塘");
        if(k==Kind.PROTECTED)throw new IllegalArgumentException("入口候选含需保留的方块或方块实体");
    }
}
