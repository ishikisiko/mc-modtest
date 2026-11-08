package com.example.myvillage.aptitude;

import com.example.myvillage.item.ModItems;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;

/** 四种培养载体复用已有灵果和书籍模型，内容由服务器校验组件。 */
public final class AptitudeContent {
    public static final DeferredItem<TrainingItem> FRUIT = ModItems.ITEMS.registerItem("aptitude_fruit", p -> new TrainingItem(p, "fruit"));
    public static final DeferredItem<TrainingItem> BOOK = ModItems.ITEMS.registerItem("aptitude_treatise", p -> new TrainingItem(p, "book"));
    public static final DeferredItem<TrainingItem> MEMORY = ModItems.ITEMS.registerItem("combat_memory", p -> new TrainingItem(p.stacksTo(1), "memory"));
    public static final DeferredItem<TrainingItem> RESEARCH = ModItems.ITEMS.registerItem("research_notes", p -> new TrainingItem(p.stacksTo(1), "research"));
    private AptitudeContent() { }
    public static void bootstrap() { }
    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("myvillage_aptitude");
    }
    public static ItemStack create(String kind, String aptitude, int tier) {
        ItemStack result = new ItemStack(switch(kind) { case "fruit" -> FRUIT.get(); case "book" -> BOOK.get();
            case "memory" -> MEMORY.get(); case "research" -> RESEARCH.get(); default -> throw new IllegalArgumentException(kind); });
        CompoundTag payload = new CompoundTag(); payload.putString("aptitude", aptitude); payload.putInt("tier", tier);
        CompoundTag root = new CompoundTag(); root.put("myvillage_aptitude", payload);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(root)); return result;
    }
    public static final class TrainingItem extends Item {
        private final String kind;
        TrainingItem(Properties properties, String kind) { super(properties); this.kind = kind; }
        @Override public Component getName(ItemStack stack) {
            String aptitude = data(stack).getString("aptitude");
            return Component.literal(AptitudeRuntime.name(aptitude) + switch(kind) {
                case "fruit" -> "灵果"; case "book" -> "资质书"; case "memory" -> "战意残卷"; default -> "研究手记";
            });
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer server)
                return AptitudeCultivation.use(server, stack, kind) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            var rules = AptitudeRules.get(); int tier = data(stack).getInt("tier");
            lines.add(Component.literal("适用于资质低于 " + AptitudeCultivation.itemLimit(tier) + " 点时"));
            lines.add(Component.literal(switch(kind) {
                case "fruit" -> "服用后永久提高本系资质 " + rules.growth("fruit_gain") + " 点。";
                case "book" -> "研读后永久提高对应资质 " + rules.growth("book_gain") + " 点。";
                case "memory" -> "右键在周围展开战意试炼；持对应兵器击败残影。";
                default -> "右键开始课题，完成一次合格制作；副手持受损兵器可研究修复。";
            }));
        }
    }
}
