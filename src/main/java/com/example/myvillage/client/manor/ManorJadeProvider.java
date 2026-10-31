package com.example.myvillage.client.manor;

import com.example.myvillage.compat.jade.*;
import com.example.myvillage.manor.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

public final class ManorJadeProvider implements IBlockComponentProvider {
    public record Hint(Component title,List<Component> lines) { }
    @Override public ResourceLocation getUid() { return ManorJadePlugin.NAMES; }
    @Override public int getDefaultPriority() { return TooltipPosition.HEAD-90; }
    @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config) {
        var info=ManorPresentation.get().describe(accessor.getBlockState()).orElse(null);if(info==null)return;
        var level=accessor.getLevel();
        Optional<ManorLocation.Name> location=level!=null&&config.get(ManorJadePlugin.LOCATION)
                ?ManorJadeDataProvider.read(accessor.getServerData(),level.dimension().location(),accessor.getPosition(),accessor.getBlockState()):Optional.empty();
        int shape=accessor.getBlockState().getBlock() instanceof ManorComponentBlock
                ?accessor.getBlockState().getValue(ManorComponentBlock.SHAPE):-1;
        var hint=hint(info,location,accessor.showDetails(),Minecraft.getInstance().options.advancedItemTooltips,shape);
        var title=IThemeHelper.get().title(hint.title());
        if(!tooltip.replace(JadeIds.CORE_OBJECT_NAME,title))tooltip.add(0,title);
        hint.lines().forEach(tooltip::add);
    }
    public static Hint hint(ManorPresentation.Info info,Optional<ManorLocation.Name> location,boolean details,boolean advanced,int shape) {
        List<Component> lines=new ArrayList<>();
        location.ifPresent(name -> lines.add(name.component().copy().withStyle(ChatFormatting.GRAY)));
        if(details) {
            lines.add(info.materialsLine().copy().withStyle(ChatFormatting.GRAY));
            lines.add(info.styleLine().copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        if(advanced&&shape>=0)lines.add(Component.translatable("tooltip.myvillage.manor_debug",shape).withStyle(ChatFormatting.DARK_GRAY));
        return new Hint(info.name(),List.copyOf(lines));
    }
}
