package com.example.myvillage.item;

import com.example.myvillage.cultivation.fate.FateReworkRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.UUID;

public final class CompanionSwordItem extends SwordItem {
    public record Mark(UUID owner, UUID sword, int form, List<String> victories) {
        public static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Mark::owner),
                UUIDUtil.CODEC.fieldOf("sword").forGetter(Mark::sword),
                Codec.intRange(0, 4).fieldOf("form").forGetter(Mark::form),
                Codec.STRING.listOf(0, 4).optionalFieldOf("victories", List.of()).forGetter(Mark::victories)
        ).apply(i, Mark::new));

        public Mark { victories = List.copyOf(victories); }
    }

    public CompanionSwordItem(Properties properties) { super(Tiers.IRON, properties); }

    public static Mark mark(ItemStack stack) {
        return stack.is(ModItems.PRIMORDIAL_COMPANION_SWORD.get()) ? stack.get(ModDataComponents.COMPANION_SWORD.get()) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var mark = mark(stack);
        if (mark == null) return;
        var stage = FateReworkRules.get().stages().get(mark.form());
        lines.add(Component.translatable("fate.yuanxian.sword.form",
                Component.translatable("cultivation.realm.yuanxian." + stage.realm().getPath()),
                Math.round(stage.swordBonus() * 100)).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("fate.yuanxian.sword.owner").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("fate.yuanxian.sword.art_stats", stage.power(), stage.reach(), stage.targets(),
                stage.cooldown() / 20).withStyle(ChatFormatting.GRAY));
        for (String victory : mark.victories()) lines.add(Component.literal(victory).withStyle(ChatFormatting.DARK_AQUA));
    }
}
