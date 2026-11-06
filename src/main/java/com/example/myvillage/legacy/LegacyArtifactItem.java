package com.example.myvillage.legacy;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import com.example.myvillage.item.TechniqueManualItem;

public final class LegacyArtifactItem extends Item {
    private final String kind;
    public LegacyArtifactItem(Properties properties,String kind) {super(properties);this.kind=kind;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(stack);
        if(level.isClientSide)return InteractionResultHolder.sidedSuccess(stack,true);
        if(!(player instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator())return InteractionResultHolder.fail(stack);
        if(kind.equals("watermarked_silk")) {
            if(!p.isInWaterOrBubble()){LegacyRuntime.message(p,"water");return InteractionResultHolder.fail(stack);}
            ItemStack book=TechniqueManualItem.manualFor(p.registryAccess(),ResourceLocation.parse("myvillage:fenying_bu"));
            if(book.isEmpty())return InteractionResultHolder.fail(stack);
            // Item.use installs its returned stack in the used hand. Returning an emptied silk
            // stack after Inventory.add filled that same slot would erase the newly revealed book.
            if(!p.getAbilities().instabuild&&stack.getCount()==1)return InteractionResultHolder.success(book);
            if(!p.getAbilities().instabuild)stack.shrink(1);
            if(!p.getInventory().add(book))p.drop(book,false);
            return InteractionResultHolder.success(stack);
        }
        return LegacyRuntime.useArtifact(p,stack,kind)?InteractionResultHolder.success(stack):InteractionResultHolder.fail(stack);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        if(kind.equals("mirror")||kind.equals("gourd")) {
            int max=kind.equals("mirror")?1:LegacyRules.get().ticks("gourd_max_charges");
            lines.add(Component.translatable("legacy.myvillage.charge",LegacyRuntime.charges(stack,max),max));
            lines.add(Component.translatable("tooltip.myvillage.legacy."+(kind.equals("mirror")?"zhaoshui_mirror":"chiyu_gourd")));
        } else if(kind.equals("watermarked_silk"))lines.add(Component.translatable("legacy.myvillage.water"));
    }
}
