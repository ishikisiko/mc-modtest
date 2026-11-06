package com.example.myvillage.legacy;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.example.myvillage.wilderness.runtime.WildernessRuntime;

/** UUID occupancy is retained while an encounter is unloaded; only a bound death unlocks the rubbing. */
public final class LegacySteleEntity extends BlockEntity {
    private boolean ready,cleared,claimed;
    private UUID guardian;
    public LegacySteleEntity(BlockPos p,BlockState s){super(LegacyContent.STELE_ENTITY.get(),p,s);}
    public void ready(){ready=true;setChanged();}
    private boolean sword(){return getBlockState().is(LegacyContent.SWORD_STELE.get());}
    public void tick(){
        if(!(level instanceof ServerLevel server)||!ready||cleared||guardian!=null||!sword()||server.getGameTime()%LegacyRules.get().ticks("anchor_scan_ticks")!=0)return;
        if(server.getNearestPlayer(worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,LegacyRules.get().number("anchor_spawn_scan_range"),false)!=null)spawn(server,true);
    }
    private boolean spawn(ServerLevel server,boolean dormant){
        if(guardian!=null||cleared||server.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return false;
        var type=LegacyContent.BEASTS.get(sword()?"wandering_sword":"stele_guardian").get();
        for(int z:new int[]{2,-2,3,-3}){
            BlockPos p=worldPosition.offset(0,0,z);
            if(!server.hasChunkAt(p)||!server.getBlockState(p.below()).isSolidRender(server,p.below()))continue;
            var mob=type.create(server);if(mob==null)return false;mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
            if(!server.noCollision(mob)||!server.getFluidState(p).isEmpty())continue;
            mob.bind(worldPosition,dormant);
            // Regional birth is sampled once, through the existing authority.
            LegacyEvents.assignBirth(server,mob,p,false);
            if(!server.addFreshEntity(mob))return false;
            guardian=mob.getUUID();setChanged();return true;
        }
        return false;
    }
    public void use(ServerPlayer p,InteractionHand hand){
        if(!(level instanceof ServerLevel server)||hand!=InteractionHand.MAIN_HAND||!p.isAlive()||p.isSpectator()||p.distanceToSqr(worldPosition.getCenter())>Math.pow(LegacyRules.get().number("anchor_interaction_range"),2))return;
        if(!ready){LegacyRuntime.message(p,"paper");return;}
        if(!cleared){if(guardian==null)spawn(server,false);LegacyRuntime.message(p,"trial");return;}
        if(claimed){LegacyRuntime.message(p,"claimed");return;}
        ItemStack paper=p.getItemInHand(hand);if(!paper.is(Items.PAPER)){LegacyRuntime.message(p,"paper");return;}
        // Commit the shared reward state before inventory mutation; two players cannot claim it twice.
        claimed=true;setChanged();if(!p.getAbilities().instabuild)paper.shrink(1);
        ItemStack rubbing=new ItemStack(LegacyContent.item(sword()?"sword_rubbing":"mountain_rubbing"));
        if(!p.getInventory().add(rubbing))p.drop(rubbing,false);
    }
    public void defeated(UUID id){if(guardian!=null&&guardian.equals(id)){cleared=true;setChanged();}}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider registries){super.saveAdditional(t,registries);t.putBoolean("Ready",ready);t.putBoolean("Cleared",cleared);t.putBoolean("Claimed",claimed);if(guardian!=null)t.putUUID("Guardian",guardian);}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider registries){super.loadAdditional(t,registries);ready=t.getBoolean("Ready");cleared=t.getBoolean("Cleared");claimed=t.getBoolean("Claimed");guardian=t.hasUUID("Guardian")?t.getUUID("Guardian"):null;}
}
