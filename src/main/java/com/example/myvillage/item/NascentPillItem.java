package com.example.myvillage.item;

import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.alchemy.MedicineRuntime;
import com.example.myvillage.alchemy.PillQuality;
import com.example.myvillage.cultivation.nascent.NascentRules;
import com.example.myvillage.cultivation.nascent.NascentTrial;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

public final class NascentPillItem extends Item {
    public NascentPillItem(Properties properties) { super(properties.stacksTo(16)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            String reason = NascentTrial.rejection(server);
            var medicine = MedicineRuntime.state(server, "ningying");
            if (reason == null && (medicine.cooldownTicks() > 0 || medicine.effectTicks() > 0))
                reason = "尚未达到下次服药时间。";
            if (reason != null) {
                server.displayClientMessage(Component.literal(reason), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity living) { return AlchemyRules.get().useTicks(); }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.EAT; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (living instanceof ServerPlayer player && !stack.isEmpty()) {
            player.stopUsingItem();
            NascentTrial.swallow(player, stack, PillQuality.get(stack));
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        int quality = PillQuality.get(stack);
        return PillQuality.name(quality).copy().append(super.getName(stack)).withColor(QualityDisplay.color(quality));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("金丹后期修为和稳定度达标后，长按右键服用。"));
        lines.add(Component.literal("护住神魂，静坐 " + NascentRules.get().pillTicks(PillQuality.get(stack)) / 20
                + " 秒可结下品元婴。"));
        lines.add(Component.literal("开始碎丹时消耗一枚。中断则丹药耗尽，损失四分之一稳定度。"));
        lines.add(Component.translatable("tooltip.yuanxian.alchemy.interval", AlchemyRules.get().cooldownTicks() / 20));
    }
}
