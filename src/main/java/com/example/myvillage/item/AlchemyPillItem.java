package com.example.myvillage.item;

import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.alchemy.MedicineRuntime;
import com.example.myvillage.alchemy.MedicineEffects;
import com.example.myvillage.alchemy.PillQuality;
import com.example.myvillage.cultivation.fate.PillAegisRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import java.math.BigDecimal;

/** Timed use, revalidated and applied only on the logical server at completion. */
public final class AlchemyPillItem extends Item {
    private final String effectId;

    public AlchemyPillItem(Properties properties, String effectId) {
        super(properties.stacksTo(16));
        if (!List.of("huiling", "yangyuan", "ningshen").contains(effectId) && !com.example.myvillage.wilderness.ExpeditionRules.get().isMedicine(effectId))
            throw new IllegalArgumentException("Unknown medicine: " + effectId);
        this.effectId = effectId;
    }

    public String effectId() { return effectId; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            String rejection = MedicineRuntime.rejection(serverPlayer, effectId);
            if (rejection != null) {
                serverPlayer.displayClientMessage(Component.translatable(rejection), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return AlchemyRules.get().useTicks(); }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.EAT; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!stack.isEmpty() && entity instanceof ServerPlayer player
                && MedicineRuntime.consume(player, effectId, PillQuality.get(stack))) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            PillAegisRuntime.afterSuccessfulConsumption(player, effectId);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        int quality = PillQuality.get(stack);
        return Component.empty().append(Component.translatable("item.myvillage.alchemy.quality_name", PillQuality.name(quality),
                super.getName(stack)).withStyle(style -> style.withColor(QualityDisplay.color(quality))));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        int quality = PillQuality.get(stack);
        if (!lines.isEmpty()) lines.set(0, lines.getFirst().copy()
                .withStyle(style -> style.withColor(QualityDisplay.color(quality))));
        if (stack.has(DataComponents.CUSTOM_NAME) || stack.has(DataComponents.ITEM_NAME)) {
            lines.add(Component.translatable("tooltip.myvillage.alchemy.quality", PillQuality.name(quality))
                    .withStyle(style -> style.withColor(QualityDisplay.color(quality))));
        }
        var rules = AlchemyRules.get();
        double factor = rules.qualityMultiplier(PillQuality.get(stack));
        Object[] arguments = switch (effectId) {
            case "huiling" -> new Object[] {number(rules.effects().huiling().powerFraction() * factor * 100)};
            case "yangyuan" -> new Object[] {number(rules.effects().yangyuan().healFraction() * factor * 100),
                    number(rules.effects().yangyuan().intervalTicks() / 20.0),
                    number(rules.effects().yangyuan().durationTicks() / 20.0)};
            case "ningshen" -> new Object[] {number(MedicineEffects.qualityStabilityMultiplier(rules.effects().ningshen().stabilityMultiplier(), factor)),
                    number(rules.effects().ningshen().durationTicks() / 20.0)};
            default -> {var e=com.example.myvillage.wilderness.ExpeditionRules.get().medicine(effectId);yield e==null?new Object[0]:new Object[]{number(e.strengthForQuality(quality)*100)+"%",number(e.durationTicks(quality)/20.0)};}
        };
        lines.add(Component.translatable("tooltip.myvillage.alchemy.pill_" + effectId, arguments).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.alchemy.shared_interval",
                number(rules.sharedCooldownTicks() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
    }
    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
