package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** A slain uncaptured fire creature can also leave a rare seed; capturing it never awards a second seed. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class AuxiliaryFireDrops {
    private AuxiliaryFireDrops(){}
    @SubscribeEvent public static void drops(LivingDropsEvent event){
        var entity=event.getEntity();
        if(entity.level().isClientSide()||!(entity instanceof Blaze)||!(event.getSource().getEntity() instanceof ServerPlayer)
                ||entity.getPersistentData().getBoolean("myvillage_fire_captured")||entity.getRandom().nextDouble()>=FoundryRules.get().fire().beast_drop_chance())return;
        var seed=FireCaptureItem.seed("beast",0,entity.level().dimension().location().toString(),entity.blockPosition());
        event.getDrops().add(new ItemEntity(entity.level(),entity.getX(),entity.getY(),entity.getZ(),seed));
    }
}
