package com.example.myvillage.talisman;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class TalismanItem extends Item {
    private final int kind;
    public TalismanItem(Properties p, int kind) { super(p); this.kind = kind; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (!(p instanceof ServerPlayer server) || !TalismanRuntime.use(server, stack, kind)) return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.consume(stack);
    }
    @Override public Component getName(ItemStack s) { return Component.empty().append(TalismanQuality.name(TalismanQuality.get(s))).append(" ").append(super.getName(s)); }
    @Override public void appendHoverText(ItemStack s, TooltipContext c, List<Component> out, TooltipFlag flag) {
        int q = TalismanQuality.get(s); var r = TalismanRules.get();
        out.add(switch (kind) {
            case 0 -> Component.literal("指向落点，预警后雷击一次 · 伤害 " + r.damage(q));
            case 1 -> Component.literal("地符触发一次 · 定身 " + r.rootTicks(q) + " 刻；强敌缩短或免控");
            default -> Component.literal("自用护盾 " + r.shield(q) + " · " + r.ticks("ward", "duration_ticks") / 20 + " 秒；同源替换");
        });
        if (kind == 1) out.add(Component.literal("潜行空手右击地符收回；离线/跨维度/到期消散"));
        out.add(Component.literal("激活消耗灵力 " + r.use("activation_power") + " · 三符共用间隔 " + r.use("cooldown_ticks") + " 刻"));
        out.add(Component.literal("右键使用 · 可在渡劫中使用"));
    }
}
