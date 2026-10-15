package com.example.myvillage.ruins;

import com.example.myvillage.block.*;
import com.example.myvillage.item.TechniqueManualItem;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The cache owns both encounter progress and its once-only reward inventory. */
public final class BattleCacheBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider {
    private String kind="";
    private BattleProgress progress=new BattleProgress(List.of(1),"DORMANT",0,List.of(),List.of());
    private NonNullList<ItemStack> items=NonNullList.withSize(27,ItemStack.EMPTY);
    private long nextWaveAt;
    public BattleCacheBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.BATTLE_CACHE.get(),p,s);}
    private BattleRules rules(){return BattleRules.get(kind);}
    private BlockPos target(RuinRules.Pos p){
        int turns=switch(getBlockState().getValue(BattleCacheBlock.FACING)){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        var d=p.subtract(rules().cache()).rotate(turns);return worldPosition.offset(d.x(),d.y(),d.z());
    }
    private boolean eligible(ServerPlayer p){return p.isAlive() && !p.isSpectator() && !p.isCreative() && p.level()==level;}
    private List<ServerPlayer> nearby(){
        if(!(level instanceof ServerLevel server) || rules()==null)return List.of();
        double radius=rules().leashRadius();
        return server.players().stream().filter(this::eligible).filter(p->p.distanceToSqr(worldPosition.getCenter())<=radius*radius).toList();
    }
    private void message(ServerPlayer p,String key,Object...args){p.displayClientMessage(Component.translatable("message.myvillage.battle."+key,args),true);}
    public void use(ServerPlayer p){
        var r=rules();
        if(r==null || !p.isAlive() || p.isSpectator() || !p.mayBuild() || p.level()!=level
            || p.distanceToSqr(worldPosition.getCenter())>r.interactionDistance()*r.interactionDistance())return;
        if(progress.state()==BattleProgress.State.DORMANT){message(p,"dormant");return;}
        if(progress.state()==BattleProgress.State.REWARDED){p.openMenu(this);level.playSound(null,worldPosition,SoundEvents.CHEST_OPEN,SoundSource.BLOCKS,.5F,1F);return;}
        if(level.getDifficulty()==Difficulty.PEACEFUL){message(p,"peaceful");return;}
        if(progress.state()==BattleProgress.State.READY && r.mode().equals("arena")){
            progress.start();nextWaveAt=level.getGameTime()+r.waveDelayTicks();setChanged();message(p,"start");return;
        }
        message(p,"locked",progress.wave()+1,r.waves().size());
    }
    public void tick(){
        var r=rules();
        if(!(level instanceof ServerLevel server) || r==null || progress.state()==BattleProgress.State.DORMANT
                || server.getGameTime()%r.tickInterval()!=0)return;
        if(progress.state()==BattleProgress.State.REWARDED)return;
        var players=nearby();
        var receipts=BattleReceipts.get(server);
        for(int i=0;i<progress.count();i++){
            UUID id=progress.guard(i); if(id==null || progress.dead(i))continue;
            String status=receipts.status(id);
            if(status.equals("dead")){progress.died(id);receipts.forget(id);setChanged();}
            else if(status.equals("retry")){progress.retry(i);receipts.forget(id);setChanged();}
        }
        if(progress.advance()){
            nextWaveAt=server.getGameTime()+r.waveDelayTicks();setChanged();
            for(var p:players)message(p,progress.state()==BattleProgress.State.CLEARED?"clear":"next",progress.wave()+1);
        }
        if(progress.state()==BattleProgress.State.CLEARED){reward(server);return;}
        if(server.getDifficulty()==Difficulty.PEACEFUL || players.isEmpty())return;
        var wave=r.waves().get(progress.wave()); var trigger=target(wave.trigger());
        boolean nearTrigger=players.stream().anyMatch(p->p.distanceToSqr(trigger.getCenter())<=r.triggerRadius()*r.triggerRadius());
        if(progress.state()==BattleProgress.State.READY && r.mode().equals("route") && nearTrigger){progress.start();setChanged();}
        if(progress.state()!=BattleProgress.State.ACTIVE)return;
        boolean maySpawn=(r.mode().equals("arena") || nearTrigger) && server.getGameTime()>=nextWaveAt;
        for(int i=0;i<progress.count();i++){
            if(progress.dead(i))continue;
            UUID id=progress.guard(i);
            if(id==null){if(maySpawn)spawn(server,wave.guards().get(i),i,players.getFirst());continue;}
            Entity entity=server.getEntity(id);
            if(entity instanceof Mob mob && mob.isAlive()){
                var home=target(wave.guards().get(i).pos());
                if(mob.distanceToSqr(home.getCenter())>r.leashRadius()*r.leashRadius() && server.hasChunkAt(home)
                        && server.getBlockState(home).isAir() && server.getBlockState(home.above()).isAir())
                    mob.teleportTo(home.getX()+.5,home.getY(),home.getZ()+.5);
                if(mob.getTarget()==null || !mob.getTarget().isAlive())mob.setTarget(players.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElseThrow());
            }
        }
        int remaining=0; for(int i=0;i<progress.count();i++)if(!progress.dead(i))remaining++;
        for(var p:players)message(p,"progress",Component.translatable("structure.myvillage."+kind),progress.wave()+1,r.waves().size(),remaining);
    }
    private void spawn(ServerLevel server,BattleRules.Guard definition,int slot,ServerPlayer player){
        BlockPos p=target(definition.pos());
        if(!server.hasChunkAt(p) || !server.getBlockState(p.below()).isCollisionShapeFullBlock(server,p.below()))return;
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(definition.entity()));
        Entity entity=type.create(server);
        if(!(entity instanceof Mob mob))return;
        mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        if(!server.noCollision(mob) || server.containsAnyLiquid(mob.getBoundingBox()))return;
        mob.setPersistenceRequired(); mob.setCanPickUpLoot(false);
        if(!definition.weapon().isEmpty())mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(definition.weapon()))));
        if(!definition.entity().equals("myvillage:demon_wolf")){
            ItemStack helmet=new ItemStack(Items.IRON_HELMET);helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(false));mob.setItemSlot(EquipmentSlot.HEAD,helmet);
        }
        for(var equipment:EquipmentSlot.values())mob.setDropChance(equipment,0F);
        StatsRuntime.applyRealm(mob,definition.realm(),definition.stage());mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.translatable("entity.myvillage.battle."+(definition.elite()?"elite":"guard"),Component.translatable("structure.myvillage."+kind)));
        mob.setCustomNameVisible(definition.elite());
        mob.addTag("combat_ruin_guard");mob.addTag(kind+"_guard");mob.getPersistentData().putLong("myvillage:battle_cache",worldPosition.asLong());
        mob.restrictTo(p,(int)rules().leashRadius());mob.setTarget(player);
        UUID id=mob.getUUID();
        progress.bind(slot,id);BattleReceipts.get(server).register(id);setChanged();
        if(!server.addFreshEntity(mob)){progress.retry(slot);BattleReceipts.get(server).forget(id);setChanged();return;}
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,p.getX()+.5,p.getY()+1,p.getZ()+.5,12,.3,.6,.3,0);
    }
    private void reward(ServerLevel server){
        var r=rules(); ItemStack manual=TechniqueManualItem.manualFor(server.registryAccess(),ResourceLocation.parse(r.manual()));
        if(manual.isEmpty())return;
        var rewards=NonNullList.withSize(27,ItemStack.EMPTY);rewards.set(0,manual);int slot=1;
        for(var reward:r.rewards()){
            var key=ResourceLocation.parse(reward.id());if(!BuiltInRegistries.ITEM.containsKey(key))return;
            ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(key),reward.count());
            if(stack.getCount()>stack.getMaxStackSize())return;
            rewards.set(slot++,stack);
        }
        if(!progress.reward())return;
        items=rewards;setChanged();
        server.setBlock(worldPosition,getBlockState().setValue(BattleCacheBlock.LIT,true),Block.UPDATE_ALL);
        server.playSound(null,worldPosition,SoundEvents.PLAYER_LEVELUP,SoundSource.BLOCKS,1F,.8F);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putString("Kind",kind);tag.putString("BattleState",progress.state().name());
        tag.putInt("Wave",progress.wave());tag.putLong("NextWaveAt",nextWaveAt);
        var guards=new ListTag();for(int i=0;i<progress.count();i++){var g=new CompoundTag();if(progress.guard(i)!=null)g.putUUID("Id",progress.guard(i));g.putBoolean("Dead",progress.dead(i));guards.add(g);}tag.put("Guards",guards);
        ContainerHelper.saveAllItems(tag,items,registries);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);kind=tag.getString("Kind");nextWaveAt=tag.getLong("NextWaveAt");
        var r=rules();List<UUID> ids=new ArrayList<>();List<Boolean> dead=new ArrayList<>();
        for(var t:tag.getList("Guards",Tag.TAG_COMPOUND)){var g=(CompoundTag)t;ids.add(g.hasUUID("Id")?g.getUUID("Id"):null);dead.add(g.getBoolean("Dead"));}
        progress=new BattleProgress(r==null?List.of(1):r.waves().stream().map(w->w.guards().size()).toList(),tag.getString("BattleState"),tag.getInt("Wave"),ids,dead);
        items=NonNullList.withSize(27,ItemStack.EMPTY);
        if(progress.state()==BattleProgress.State.REWARDED)ContainerHelper.loadAllItems(tag,items,registries);
    }
    @Override public Component getDisplayName(){return Component.translatable("block.myvillage.battle_cache");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return progress.state()==BattleProgress.State.REWARDED?ChestMenu.threeRows(id,inv,this):null;}
    @Override public int getContainerSize(){return 27;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return progress.state()==BattleProgress.State.REWARDED?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(progress.state()!=BattleProgress.State.REWARDED)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(progress.state()!=BattleProgress.State.REWARDED)return ItemStack.EMPTY;var result=ContainerHelper.takeItem(items,slot);setChanged();return result;}
    @Override public void setItem(int slot,ItemStack stack){if(progress.state()==BattleProgress.State.REWARDED){items.set(slot,stack);setChanged();}}
    @Override public void clearContent(){items.clear();setChanged();}
    @Override public boolean stillValid(Player p){return progress.state()==BattleProgress.State.REWARDED && Container.stillValidBlockEntity(this,p);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return false;}
    @Override public int[] getSlotsForFace(Direction face){return new int[0];}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction face){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction face){return false;}
}
