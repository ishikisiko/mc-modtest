package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.art.LockedSwordDefinition;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server acquires once in a broad forward cone; a lost target is never replaced mid-cast. */
public final class LockedSwordTargets {
    private LockedSwordTargets() {}
    public static Optional<Entity> acquire(ServerPlayer player,CombatSession session,LockedSwordDefinition lock) {
        Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        return player.serverLevel().getEntities(player,player.getBoundingBox().inflate(lock.range()),
                e -> CombatHitResolver.legalTarget(player,e,session)).stream()
                .filter(e -> within(player,e,lock.range()) && lock.inCone(direction(eye,e).dot(look)) && visible(player,e))
                .sorted(Comparator.<Entity>comparingDouble(e -> -direction(eye,e).dot(look))
                        .thenComparingDouble(e -> e.distanceToSqr(player)).thenComparingInt(Entity::getId)).findFirst();
    }
    private static Vec3 direction(Vec3 eye,Entity entity) {return entity.getBoundingBox().getCenter().subtract(eye).normalize();}
    public static boolean within(ServerPlayer player,Entity e,double range) {return e.distanceToSqr(player)<=range*range;}
    public static boolean visible(ServerPlayer player,Entity e) {
        Vec3 end=e.getBoundingBox().getCenter();
        return clear(player,player.getEyePosition(),end);
    }
    public static boolean clear(ServerPlayer player,Vec3 from,Vec3 to) {
        var hit=player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()!=HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(from)+.0001>=to.distanceToSqr(from);
    }
    public static List<Entity> inArray(ServerPlayer player,CombatSession session,Vec3 anchor,double radius,int maximum) {
        var found=player.serverLevel().getEntities(player,new AABB(anchor,anchor).inflate(radius),
                e -> CombatHitResolver.legalTarget(player,e,session)).stream()
                .filter(e -> e.getBoundingBox().getCenter().distanceToSqr(anchor.add(0,e.getBbHeight()/2,0))<=radius*radius
                        && visible(player,e) && clear(player,anchor.add(0,1,0),e.getBoundingBox().getCenter()))
                .sorted(Comparator.comparingDouble((Entity e)->e.distanceToSqr(anchor)).thenComparingInt(Entity::getId)).toList();
        Set<Integer> seen=new HashSet<>();
        return found.stream().filter(e->seen.add(CombatTargets.hitId(e))).limit(maximum).toList();
    }
}
