package com.example.myvillage.entity.undead;

import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Real vanilla arrow flight/collision; only the owner resolves authored damage and bounded impacts. */
public final class UndeadArrowEntity extends AbstractArrow {
    private UndeadRules.Action action;
    private double multiplier;
    private final Set<UUID> hit=new HashSet<>();
    public UndeadArrowEntity(EntityType<? extends UndeadArrowEntity> type,Level level){super(type,level);pickup=Pickup.DISALLOWED;}
    public void prepare(UndeadEntity owner,UndeadRules.Action action,double multiplier){setOwner(owner);this.action=action;this.multiplier=multiplier;}
    @Override public byte getPierceLevel(){return (byte)(action!=null&&action.projectile().equals("piercing")?UndeadRules.get().ticks("piercing_targets"):0);}
    @Override protected double getDefaultGravity(){return UndeadRules.get().number("projectile_gravity");}
    @Override protected ItemStack getDefaultPickupItem(){return new ItemStack(Items.ARROW);}
    @Override protected boolean canHitEntity(Entity entity){return super.canHitEntity(entity)&&!hit.contains(entity.getUUID())&&getOwner() instanceof UndeadEntity owner&&entity instanceof LivingEntity living&&owner.mayHit(living);}
    @Override protected void onHitEntity(EntityHitResult result){
        if(level().isClientSide()||action==null||!(getOwner() instanceof UndeadEntity owner)||!(result.getEntity() instanceof LivingEntity victim))return;
        if(!hit.add(victim.getUUID()))return;
        boolean area=Set.of("tide","gu","explosive").contains(action.projectile());
        if(!area)owner.arrowHit(victim,this,multiplier);
        owner.arrowImpact(action,victim.position().add(0,UndeadRules.get().number("field_floor_offset"),0),area?Set.of():hit);
        if(!action.projectile().equals("piercing")||hit.size()>=UndeadRules.get().ticks("piercing_targets"))discard();
    }
    @Override protected void onHitBlock(BlockHitResult result){
        if(!level().isClientSide()&&action!=null&&getOwner() instanceof UndeadEntity owner)owner.arrowImpact(action,result.getLocation(),hit);
        discard();
    }
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide()&&(tickCount>=UndeadRules.get().ticks("projectile_lifetime")||!(getOwner() instanceof UndeadEntity owner)||!owner.isAlive()))discard();
    }
}
