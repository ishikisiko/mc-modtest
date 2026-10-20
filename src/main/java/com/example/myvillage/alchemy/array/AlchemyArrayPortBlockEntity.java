package com.example.myvillage.alchemy.array;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** Stateless facade: no inventory, saved binding, or cached controller can duplicate items. */
public final class AlchemyArrayPortBlockEntity extends BlockEntity {
    public AlchemyArrayPortBlockEntity(BlockPos pos,BlockState state){super(AlchemyArrayContent.PORT_ENTITY.get(),pos,state);}
    public IItemHandler handler(Direction side){
        if(side==null||!sideAllowed(side))return null;
        // Capture this port's identity, so a held handler cannot target a replacement at the same coordinates.
        return new IItemHandler(){
            private UUID boundController;
            private AlchemyArrayBlockEntity resolve(){
                if(level==null||level.isClientSide()||isRemoved()||!level.hasChunkAt(worldPosition)||level.getBlockEntity(worldPosition)!=AlchemyArrayPortBlockEntity.this||!sideAllowed(side))return null;
                var controller=AlchemyArrayPattern.controller(level,worldPosition);
                if(controller==null)return null;
                if(boundController==null)boundController=controller.identity();
                return boundController.equals(controller.identity())?controller:null;
            }
            public int getSlots(){return role()==AlchemyArrayRules.Role.INPUT?2:role()==AlchemyArrayRules.Role.OUTPUT?4:1;}
            private int mapped(int slot){return role()==AlchemyArrayRules.Role.INPUT?slot:role()==AlchemyArrayRules.Role.OUTPUT?slot+3:2;}
            private boolean valid(int slot){return slot>=0&&slot<getSlots();}
            public ItemStack getStackInSlot(int slot){var root=resolve();return root==null||!valid(slot)?ItemStack.EMPTY:root.getItem(mapped(slot)).copy();}
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){var root=resolve();return root==null||!valid(slot)||role()==AlchemyArrayRules.Role.OUTPUT?stack.copy():root.insertPort(mapped(slot),stack,simulate);}
            public ItemStack extractItem(int slot,int amount,boolean simulate){var root=resolve();return root==null||!valid(slot)||role()!=AlchemyArrayRules.Role.OUTPUT?ItemStack.EMPTY:root.extractPort(mapped(slot),amount,simulate);}
            public int getSlotLimit(int slot){var root=resolve();return root==null||!valid(slot)?0:root.getMaxStackSize();}
            public boolean isItemValid(int slot,ItemStack stack){var root=resolve();return root!=null&&valid(slot)&&role()!=AlchemyArrayRules.Role.OUTPUT&&root.accepts(mapped(slot),stack);}
        };
    }
    private AlchemyArrayRules.Role role(){return ((AlchemyArrayBlock)getBlockState().getBlock()).role();}
    private boolean sideAllowed(Direction side){
        if(!(getBlockState().getBlock() instanceof AlchemyArrayBlock block))return false;
        return side==getBlockState().getValue(AlchemyArrayBlock.FACING)||(block.role()==AlchemyArrayRules.Role.OUTPUT&&side==Direction.DOWN);
    }
}
