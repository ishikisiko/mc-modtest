package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.equipment.EquipmentArmorItem;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.stats.runtime.RealmArmorRuntime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual server work earns item-bound experience. Neither animation nor a client counter awards it. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class WorkshopProgress {
    private static final TagKey<Block> ORES=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("c","ores"));
    private static final String MELEE_PAID="myvillage_workshop_paid_damage";
    private static final String RECEIVED_PAID="myvillage_workshop_armor_received_paid_damage";
    // 精确容器身份区分嵌套命中；值不持有容器或实体。
    // Hold each identity until tick cleanup; GC must not reopen the per-tick quota.
    private static final Map<DamageContainer,DamageReceipt> DAMAGE=new IdentityHashMap<>();
    private record DamageReceipt(UUID target,float healthBefore,UUID attacker,boolean consumed){
        DamageReceipt consume(){return new DamageReceipt(target,healthBefore,attacker,true);}
    }
    private record DamagePayment(double before,double after){
        int experience(double damagePerExperience){return (int)(after/damagePerExperience)-(int)(before/damagePerExperience);}
    }
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
    private static boolean eligiblePlayer(ServerPlayer player){
        return !player.isCreative()&&!player.isSpectator()&&!player.isRemoved();
    }
    private static List<ItemStack> wornArmor(ServerPlayer player){
        var recipients=new ArrayList<ItemStack>();
        for(var slot:EquipmentSlot.values()){
            var worn=player.getItemBySlot(slot);
            if(worn.getItem() instanceof EquipmentArmorItem armor
                    &&armor.getEquipmentSlot()==slot&&WorkshopEquipment.craft(worn).map(WorkshopRules.Craft::armor).orElse(false))recipients.add(worn);
        }
        return recipients;
    }
    private static boolean melee(DamageSource source,LivingEntity target){
        return source.getDirectEntity() instanceof ServerPlayer player&&eligiblePlayer(player)
                &&source.is(DamageTypes.PLAYER_ATTACK)&&target instanceof Monster
                &&!player.isAlliedTo(target);
    }
    private static boolean attributedEnemyAttack(DamageSource source,LivingEntity attacker){
        if(source.getDirectEntity()==attacker)
            return source.is(DamageTypes.MOB_ATTACK)||source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)||source.is(DamageTypes.STING);
        return source.is(DamageTypeTags.IS_PROJECTILE)&&source.getDirectEntity() instanceof Projectile projectile
                &&projectile.getOwner()==attacker;
    }
    private static LivingEntity incomingAttacker(DamageSource source,ServerPlayer player){
        if(!(source.getEntity() instanceof Mob attacker)||!eligiblePlayer(player)
                ||!(attacker instanceof Enemy||attacker.getTarget()==player)
                ||!CombatActors.mayHarm(attacker,player)||!attributedEnemyAttack(source,attacker)
                ||CombatDamageService.isDerivedHit(player))return null;
        return attacker;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void beforeDamage(LivingDamageEvent.Pre event){
        var target=event.getEntity();
        if(!(target.level() instanceof ServerLevel)||!Float.isFinite(event.getNewDamage())||event.getNewDamage()<=0
                ||!Float.isFinite(target.getHealth())||target.getHealth()<=0||DAMAGE.containsKey(event.getContainer()))return;
        UUID attacker=null;
        if(target instanceof ServerPlayer player){
            if(wornArmor(player).isEmpty())return;
            var enemy=incomingAttacker(event.getSource(),player);if(enemy==null)return;
            attacker=enemy.getUUID();
        }else{
            if(!melee(event.getSource(),target))return;
            var player=(ServerPlayer)event.getSource().getDirectEntity();
            if(!WorkshopEquipment.craft(player.getMainHandItem()).map(WorkshopRules.Craft::weapon).orElse(false)
                    &&wornArmor(player).isEmpty())return;
        }
        if(DAMAGE.size()<WorkshopRules.get().damage_receipt_limit())
            DAMAGE.put(event.getContainer(),new DamageReceipt(target.getUUID(),target.getHealth(),attacker,false));
    }
    private static DamagePayment claim(CompoundTag data,String key,double damage,double maximum){
        double prior=data.getDouble(key);
        if(!Double.isFinite(prior)||prior<0||!Double.isFinite(damage)||damage<=0
                ||!Double.isFinite(maximum)||maximum<=prior)return null;
        double next=Math.min(maximum,prior+damage);
        if(next<=prior)return null;
        data.putDouble(key,next);return new DamagePayment(prior,next);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void damaged(LivingDamageEvent.Post event){
        var target=event.getEntity();
        if(!(target.level() instanceof ServerLevel))return;
        var container=RealmArmorRuntime.currentContainer(target);
        var receipt=DAMAGE.get(container);
        if(receipt==null||receipt.consumed()||!receipt.target().equals(target.getUUID())
                ||container.getSource()!=event.getSource())return;
        // 保留已消费收据到 tick 结束，重复 Post 或 Pre/Post 不会再次结算。
        DAMAGE.put(container,receipt.consume());
        if(!Float.isFinite(event.getNewDamage())||event.getNewDamage()<=0)return;
        if(receipt.attacker()!=null&&target instanceof ServerPlayer player)received(event,player,receipt);
        else if(melee(event.getSource(),target))attacked(event,(ServerPlayer)event.getSource().getDirectEntity());
    }
    private static void attacked(LivingDamageEvent.Post event,ServerPlayer player){
        var weapon=player.getMainHandItem();
        boolean supportedWeapon=WorkshopEquipment.craft(weapon).map(WorkshopRules.Craft::weapon).orElse(false);
        var armor=wornArmor(player);if(!supportedWeapon&&armor.isEmpty())return;
        var target=event.getEntity();var rules=WorkshopRules.get();
        var paid=claim(target.getPersistentData(),MELEE_PAID,event.getNewDamage(),target.getMaxHealth());if(paid==null)return;
        if(supportedWeapon)earn(weapon,paid.experience(rules.damage_per_experience()));
        int amount=paid.experience(rules.armor_melee_damage_per_experience());
        armor.forEach(item->earn(item,amount));
    }
    private static void received(LivingDamageEvent.Post event,ServerPlayer player,DamageReceipt receipt){
        if(!eligiblePlayer(player)||!(event.getSource().getEntity() instanceof LivingEntity attacker)
                ||!receipt.attacker().equals(attacker.getUUID())||attacker.level()!=player.level()
                ||attacker==player||player.isAlliedTo(attacker)||attacker.isAlliedTo(player)
                ||!attributedEnemyAttack(event.getSource(),attacker))return;
        var armor=wornArmor(player);if(armor.isEmpty())return;
        var rules=WorkshopRules.get();
        // 最终伤害同时受真实生命损失限制，排除致死溢出与浮点舍入后的零损失；不再次减伤。
        double actual=Math.min(event.getNewDamage(),Math.max(0,receipt.healthBefore()-player.getHealth()));
        var paid=claim(attacker.getPersistentData(),RECEIVED_PAID,actual,
                attacker.getMaxHealth()*rules.armor_received_budget_health_multiplier());if(paid==null)return;
        int amount=paid.experience(rules.armor_received_damage_per_experience());
        armor.forEach(item->earn(item,amount));
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        // 完全吸收或取消后也会清理，过期收据不会成为后续奖励。
        DAMAGE.clear();
    }
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event){
        DAMAGE.clear();
    }
    @SubscribeEvent
    public static void speed(PlayerEvent.BreakSpeed event){
        var stack=event.getEntity().getMainHandItem();var craft=WorkshopEquipment.craft(stack);
        if(craft.isEmpty()||(craft.get().weapon()||craft.get().armor())||!stack.has(ModDataComponents.TEMPER.get())||!stack.isCorrectToolForDrops(event.getState()))return;
        event.setNewSpeed((float)(event.getNewSpeed()*FoundryEquipment.speed(stack)));
    }
}
