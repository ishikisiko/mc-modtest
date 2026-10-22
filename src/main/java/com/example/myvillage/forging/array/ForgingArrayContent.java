package com.example.myvillage.forging.array;

import com.example.myvillage.MyVillageMod;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.*;

/** Independent registration keeps the original single-block furnace untouched. */
public final class ForgingArrayContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MyVillageMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    public static final Map<ForgingArrayRules.Role,DeferredBlock<ForgingArrayBlock>> PARTS=new EnumMap<>(ForgingArrayRules.Role.class);
    static {
        for(var role:ForgingArrayRules.Role.values()) {
            if(role==ForgingArrayRules.Role.AIR)continue;
            String id="forging_array_"+role.name().toLowerCase(Locale.ROOT);
            var block=BLOCKS.register(id,()->new ForgingArrayBlock(role,BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(3.5F,8).noOcclusion().sound(SoundType.COPPER).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s->s.getValue(ForgingArrayBlock.LIT)?ForgingArrayRules.get().light():0)));
            PARTS.put(role,block);ITEMS.registerSimpleBlockItem(id,block);
        }
    }
    public static final Supplier<BlockEntityType<ForgingArrayBlockEntity>> CONTROLLER_ENTITY=ENTITIES.register("forging_array_controller",()->BlockEntityType.Builder.of(ForgingArrayBlockEntity::new,PARTS.get(ForgingArrayRules.Role.CONTROLLER).get()).build(null));
    public static final Supplier<MenuType<ForgingArrayMenu>> MENU=MENUS.register("forging_array",()->new MenuType<>(ForgingArrayMenu::new,FeatureFlags.DEFAULT_FLAGS));
    private static final Supplier<CreativeModeTab> TAB=TABS.register("forging_array",()->CreativeModeTab.builder().title(Component.literal("玄枢炼器鼎"))
            .icon(()->new ItemStack(PARTS.get(ForgingArrayRules.Role.CONTROLLER).get()))
            .displayItems((parameters,output)->PARTS.values().forEach(block->output.accept(block.get()))).build());
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);TABS.register(bus);
    }
    private ForgingArrayContent(){ }
}
