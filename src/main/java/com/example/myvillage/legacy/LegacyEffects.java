package com.example.myvillage.legacy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded, server-issued views. These entities never perform damage or inventory changes. */
public final class LegacyEffects {
    private LegacyEffects() {}
    public static LegacyProjectionEntity emit(LivingEntity owner,int kind,Vec3 from,Vec3 to,double radius,int ticks) {
        if(!(owner.level() instanceof ServerLevel level)||!owner.isAlive())return null;
        var rules=LegacyRules.get();
        if(!Double.isFinite(from.lengthSqr())||!Double.isFinite(to.lengthSqr())||from.distanceTo(owner.position())>rules.visual("effect_range"))return null;
        int count=level.getEntitiesOfClass(LegacyProjectionEntity.class,owner.getBoundingBox().inflate(rules.visual("effect_range")),
                e->e.ownerId()==owner.getId()&&e.kind()>=LegacyProjectionEntity.FIRE_BURST&&!e.isRemoved()).size();
        if(count>=rules.visual("max_effects_per_owner"))return null;
        var view=new LegacyProjectionEntity(LegacyContent.PROJECTION.get(),level);
        view.setPos(from);view.setYRot(owner.getYRot());view.initialize(kind,owner,ItemStack.EMPTY,ticks);view.visual(to.subtract(from),radius);
        return level.addFreshEntity(view)?view:null;
    }
    public static AABB rulerBounds(Vec3 at) {
        var r=LegacyRules.get();double half=r.deployedRulerWidth()/2+r.visual("placement_margin");
        return new AABB(at.x-half,at.y,at.z-half,at.x+half,at.y+r.deployedRulerHeight()+r.visual("placement_margin"),at.z+half);
    }
}
