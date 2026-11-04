package com.example.myvillage.forging;

import com.example.myvillage.block.*;
import com.example.myvillage.block.entity.ForgingAnvilBlockEntity;
import com.example.myvillage.item.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Fuel heats one real item; ready work stays warm until physically transferred. */
public final class HeatingKilnBlockEntity extends FoundryBlockEntity {
    private int burn,temper;
    private boolean ready;
    public HeatingKilnBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.HEATING_KILN.get(),pos,state);}
    @Override public FoundryMenu.Kind kind(){return FoundryMenu.Kind.KILN;}
    @Override public int fuel(){return burn;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0&&(WorkshopItems.valid(stack)||WorkshopEquipment.craft(stack).isPresent())
            ||slot==1&&isFuel(stack);}
    public static boolean isFuel(ItemStack stack){return ForgingIngredients.matches("#myvillage:forging_fuels",stack)||stack.is(net.minecraft.tags.ItemTags.COALS);}
    @Override public boolean action(ServerPlayer player,FoundryMenu menu,int button){return false;}
    public boolean transfer(ServerPlayer player,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND||!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu
                ||!player.canInteractWithBlock(worldPosition,0))return false;
        var held=player.getItemInHand(hand);
        if(held.getItem() instanceof ForgingHammerItem){
            for(var d:Direction.Plane.HORIZONTAL){
                var pos=worldPosition.relative(d);if(!level.hasChunkAt(pos))continue;
                if(level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil&&anvil.available()){
                    if(items.get(0).isEmpty()&&!anvil.input().isEmpty()&&anvil.output().isEmpty()){
                        items.set(0,anvil.contents().removeItemNoUpdate(0));anvil.contents().setChanged();anvil.setChanged();resetReady();setChanged();sync();return true;
                    }
                    if(!items.get(0).isEmpty()&&heat()>=FoundryRules.get().heat().minimum()&&anvil.input().isEmpty()&&anvil.output().isEmpty())
                        return transferTo(anvil);
                }
            }
            return false;
        }
        if(held.isEmpty()&&!items.get(0).isEmpty()){
            player.setItemInHand(hand,items.get(0));items.set(0,ItemStack.EMPTY);resetReady();player.getInventory().setChanged();setChanged();sync();return true;
        }
        if(items.get(0).isEmpty()&&canPlaceItem(0,held.copyWithCount(1))){
            items.set(0,held.split(1));resetReady();player.getInventory().setChanged();setChanged();sync();return true;
        }
        return false;
    }
    public boolean transferTo(ForgingAnvilBlockEntity anvil){
        if(!anvil.available()||!anvil.input().isEmpty()||!anvil.output().isEmpty()||items.get(0).isEmpty()
                ||heat()<FoundryRules.get().heat().minimum())return false;
        var stack=items.get(0);items.set(0,ItemStack.EMPTY);anvil.acceptFromKiln(stack);resetReady();setChanged();sync();return true;
    }
    private void resetReady(){temper=0;ready=false;}
    @Override public void setItem(int slot,ItemStack stack){super.setItem(slot,stack);if(slot==0)resetReady();}
    @Override public void serverTick(){
        var rules=FoundryRules.get().heat();boolean burning=burn>0;
        if(burn>0)burn--;
        if(!items.get(0).isEmpty()){
            if(burn==0&&isFuel(items.get(1))){items.get(1).shrink(1);burn=rules.fuel_ticks();burning=true;setChanged();}
            if(burning){
                var stack=items.get(0);int before=heat(),next=Math.min(rules.capacity(),before+rules.gain_per_tick());
                ForgeHeat.set(stack,next,level.getGameTime());status=next>=rules.ideal()?2:1;
                if(next>=rules.ideal()&&!ready){ready=true;sync();level.playSound(null,worldPosition,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.25f,1.6f);}
                var piece=WorkshopItems.valid(stack)?stack.get(ModDataComponents.WORKPIECE.get()):null;
                if(piece!=null&&piece.stage()==2&&next>=rules.ideal()){
                    if(++temper>=rules.temper_ticks()){items.set(0,WorkshopItems.advance(stack,piece.next(piece.treatment())));temper=0;status=3;sync();}
                }else temper=0;
                setChanged();
            }else status=4;
        }else status=0;
        var state=getBlockState();if(state.getValue(FoundryStationBlock.LIT)!=burning)level.setBlock(worldPosition,state.setValue(FoundryStationBlock.LIT,burning),3);
        if(level.getGameTime()%20==0)sync();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("Burn",burn);tag.putInt("Temper",temper);tag.putBoolean("Ready",ready);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);burn=Math.clamp(tag.getInt("Burn"),0,FoundryRules.get().heat().fuel_ticks());temper=Math.clamp(tag.getInt("Temper"),0,FoundryRules.get().heat().temper_ticks()-1);ready=tag.getBoolean("Ready");}
}
