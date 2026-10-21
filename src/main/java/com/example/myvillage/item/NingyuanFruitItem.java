package com.example.myvillage.item;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public final class NingyuanFruitItem extends Item {
    private final int quality;
    public NingyuanFruitItem(Properties p,int quality){super(p);this.quality=quality;}
    public int quality(){return quality;}
    @Override public Component getName(ItemStack stack){return super.getName(stack).copy().withColor(QualityDisplay.color(quality));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.literal(quality==1?"炼丹主药：可炼下品或中品筑基丹，由丹方与炼丹资质决定":"炼丹主药：高资质可炼上品筑基丹、上品补基丹"));
    }
}
