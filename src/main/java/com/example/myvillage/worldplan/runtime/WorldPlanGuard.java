package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadGeneration;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Only generation writes are filtered. Player edits through ServerLevel remain unrestricted. */
public final class WorldPlanGuard {
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    private record Context(WorldPlan plan,RoadGeneration roads,StarterMineGeneration mine){}
    private static final ThreadLocal<Context> DECORATING=new ThreadLocal<>();
    private WorldPlanGuard() {}
    public static DecorationScope decorating(WorldPlan plan,RoadGeneration roads,StarterMineGeneration mine){
        DecorationScope scope=new DecorationScope(DECORATING.get());DECORATING.set(new Context(plan,roads,mine));return scope;
    }
    public static boolean allowsOre(BlockPos pos){
        Context context=DECORATING.get();if(context==null)return true;
        return !context.plan.reserved(pos.getX(),pos.getY(),pos.getZ())&&(context.roads==null||!context.roads.await().reserved(context.plan,context.roads.rules(),pos.getX(),pos.getY(),pos.getZ()))
            &&(context.mine==null||!context.mine.reserved(pos.getX(),pos.getY(),pos.getZ()));
    }
    public static final class DecorationScope implements AutoCloseable {
        private final Context previous;
        private DecorationScope(Context previous){this.previous=previous;}
        @Override public void close(){if(previous==null)DECORATING.remove();else DECORATING.set(previous);}
    }
    public static Scope ownSlice(WorldGenLevel world,BoundingBox clip){Scope s=new Scope(world,clip,ACTIVE.get());ACTIVE.set(s);return s;}
    public static boolean allows(WorldGenRegion world,BlockPos at){
        if(!(world.getLevel().getChunkSource().getGenerator() instanceof PlannedChunkGenerator generator))return true;
        Scope scope=ACTIVE.get();if(scope!=null&&scope.world==world)return scope.clip.isInside(at);
        if(generator.plan().reserved(at.getX(),at.getY(),at.getZ()))return false;
        return !world.getLevel().getServer().getWorldData().worldGenOptions().generateStructures()||
            !generator.roads().await().reserved(generator.plan(),generator.roads().rules(),at.getX(),at.getY(),at.getZ())&&!generator.starterMine().reserved(at.getX(),at.getY(),at.getZ());
    }
    public static final class Scope implements AutoCloseable {
        private final WorldGenLevel world;private final BoundingBox clip;private final Scope previous;
        private Scope(WorldGenLevel world,BoundingBox clip,Scope previous){this.world=world;this.clip=clip;this.previous=previous;}
        @Override public void close(){if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
}
