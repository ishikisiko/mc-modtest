package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Actual server work earns item-bound experience. Neither animation nor a client counter awards it. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class WorkshopProgress {
    private static final TagKey<Block> ORES=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("c","ores"));
    private WorkshopProgress(){}
    private static void earn(ItemStack stack,int amount){
        if(amount>0)stack.set(ModDataComponents.TEMPER.get(),WorkshopEquipment.record(stack).earn(amount,WorkshopRules.get()));
    }
    public static void mined(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity miner){
        if(!(level instanceof ServerLevel server)||!(miner instanceof ServerPlayer player)||player.isCreative()||player.isSpectator())return;
        var craft=WorkshopEquipment.craft(stack);if(craft.isEmpty()||!stack.isCorrectToolForDrops(state))return;
        boolean eligible=switch(craft.get().kind()){
            case "pickaxe"->state.is(ORES);
            case "axe"->state.is(BlockTags.LOGS);
            case "shovel"->state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            default->false;
        };
        if(eligible&&WorkshopHarvestLedger.get(server).claim(pos))earn(stack,craft.get().work_experience());
    }
    public static void tilled(ItemStack stack,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player){
        if(!(level instanceof ServerLevel server)||!(player instanceof ServerPlayer)||player.isCreative()||player.isSpectator())return;
        var craft=WorkshopEquipment.craft(stack);if(craft.isPresent()&&craft.get().kind().equals("hoe")&&WorkshopHarvestLedger.get(server).claim(pos))
            earn(stack,craft.get().work_experience());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event){
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level))return;
        var state=event.getPlacedBlock();
        if(state.is(ORES)||state.is(BlockTags.LOGS)||state.is(BlockTags.MINEABLE_WITH_SHOVEL)||state.is(BlockTags.DIRT))
            WorkshopHarvestLedger.get(level).claim(event.getPos());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void moved(net.neoforged.neoforge.event.level.PistonEvent.Pre event){
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level))return;
        var resolver=event.getStructureHelper();if(resolver==null||!resolver.resolve())return;
        var direction=event.getPistonMoveType().isExtend?event.getDirection():event.getDirection().getOpposite();
        for(var pos:resolver.getToPush()){
            WorkshopHarvestLedger.get(level).claim(pos);WorkshopHarvestLedger.get(level).claim(pos.relative(direction));
        }
    }
    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event){
        if(!(event.getSource().getDirectEntity() instanceof ServerPlayer player)||player.isCreative()||player.isSpectator()
                ||!(event.getEntity() instanceof Monster target)||event.getNewDamage()<=0||player.isAlliedTo(target))return;
        var stack=player.getMainHandItem();var craft=WorkshopEquipment.craft(stack);if(craft.isEmpty()||!craft.get().weapon())return;
        var data=target.getPersistentData();double prior=data.getDouble("myvillage_workshop_paid_damage");
        double next=Math.min(target.getMaxHealth(),prior+event.getNewDamage());
        if(next<=prior)return;
        var rules=WorkshopRules.get();int amount=(int)(next/rules.damage_per_experience())-(int)(prior/rules.damage_per_experience());
        data.putDouble("myvillage_workshop_paid_damage",next);earn(stack,amount);
    }
    @SubscribeEvent
    public static void speed(PlayerEvent.BreakSpeed event){
        var stack=event.getEntity().getMainHandItem();var craft=WorkshopEquipment.craft(stack);
        if(craft.isEmpty()||craft.get().weapon()||!stack.has(ModDataComponents.TEMPER.get())||!stack.isCorrectToolForDrops(event.getState()))return;
        event.setNewSpeed((float)(event.getNewSpeed()*(1+WorkshopEquipment.speedBonus(WorkshopEquipment.record(stack),WorkshopRules.get()))));
    }
}
