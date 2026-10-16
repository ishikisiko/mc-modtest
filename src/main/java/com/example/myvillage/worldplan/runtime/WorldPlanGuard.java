package com.example.myvillage.worldplan.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Only generation writes are filtered. Player edits through ServerLevel remain unrestricted. */
public final class WorldPlanGuard {
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    private WorldPlanGuard() {}
    public static Scope ownSlice(WorldGenLevel world,BoundingBox clip){Scope s=new Scope(world,clip,ACTIVE.get());ACTIVE.set(s);return s;}
    public static boolean allows(WorldGenRegion world,BlockPos at){
        if(!(world.getLevel().getChunkSource().getGenerator() instanceof PlannedChunkGenerator generator))return true;
        Scope scope=ACTIVE.get();if(scope!=null&&scope.world==world)return scope.clip.isInside(at);
        return !generator.plan().reserved(at.getX(),at.getY(),at.getZ());
    }
    public static final class Scope implements AutoCloseable {
        private final WorldGenLevel world;private final BoundingBox clip;private final Scope previous;
        private Scope(WorldGenLevel world,BoundingBox clip,Scope previous){this.world=world;this.clip=clip;this.previous=previous;}
        @Override public void close(){if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
}
