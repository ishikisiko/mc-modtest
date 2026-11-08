package com.example.myvillage.aptitude;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import com.example.myvillage.entity.npc.NpcEntity;
import java.util.List;

/** 原版容器界面只展示授业选项，槽位不持有可领取物品。 */
final class AptitudeTeachingMenu extends ChestMenu {
    private final NpcEntity teacher;
    AptitudeTeachingMenu(int id, Inventory inventory, NpcEntity teacher) {
        super(MenuType.GENERIC_9x3, id, inventory, new SimpleContainer(27), 3); this.teacher = teacher;
        for (int i = 0; i < AptitudeRules.get().ids().size(); i++) {
            String topic = AptitudeRules.get().ids().get(i); int score = AptitudeRuntime.score(teacher, topic);
            ItemStack icon = new ItemStack(Items.BOOK);
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(AptitudeRuntime.name(topic) + "授业"));
            icon.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("老师资质：" + score),
                    Component.literal("你的资质：" + AptitudeRuntime.score(inventory.player, topic)),
                    Component.literal("费用：" + AptitudeRules.get().growth("teaching_cost") + " 下品灵石"),
                    Component.literal("点选后留在老师附近接受指导。"))));
            getSlot(i).set(icon);
        }
    }
    @Override public boolean stillValid(Player player) { return teacher.isAlive() && player.distanceToSqr(teacher) <= Math.pow(AptitudeRules.get().growth("teaching_range"), 2) && teacher.getTarget() == null; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType click, Player player) {
        if (player instanceof ServerPlayer server && stillValid(player) && slot >= 0 && slot < AptitudeRules.get().ids().size()
                && click == ClickType.PICKUP && button == 0) {
            AptitudeCultivation.teaching(server, teacher, AptitudeRules.get().ids().get(slot)); server.closeContainer();
        }
    }
}
