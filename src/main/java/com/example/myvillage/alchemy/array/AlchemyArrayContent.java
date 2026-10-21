package com.example.myvillage.alchemy.array;

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
public final class AlchemyArrayContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MyVillageMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    public static final Map<AlchemyArrayRules.Role,DeferredBlock<AlchemyArrayBlock>> PARTS=new EnumMap<>(AlchemyArrayRules.Role.class);
    static {
        for(var role:AlchemyArrayRules.Role.values()) {
            if(role==AlchemyArrayRules.Role.AIR)continue;
            String id="alchemy_array_"+role.name().toLowerCase(Locale.ROOT);
            var block=BLOCKS.register(id,()->new AlchemyArrayBlock(role,BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(3.5F,8).noOcclusion().sound(SoundType.COPPER).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s->s.getValue(AlchemyArrayBlock.LIT)?AlchemyArrayRules.get().lightLevel():0)));
            PARTS.put(role,block);ITEMS.registerSimpleBlockItem(id,block);
        }
    }
    public static final Supplier<BlockEntityType<AlchemyArrayBlockEntity>> CONTROLLER_ENTITY=ENTITIES.register("alchemy_array_controller",()->BlockEntityType.Builder.of(AlchemyArrayBlockEntity::new,PARTS.get(AlchemyArrayRules.Role.CONTROLLER).get()).build(null));
    public static final Supplier<BlockEntityType<AlchemyArrayPortBlockEntity>> PORT_ENTITY=ENTITIES.register("alchemy_array_port",()->BlockEntityType.Builder.of(AlchemyArrayPortBlockEntity::new,
            PARTS.get(AlchemyArrayRules.Role.INPUT).get(),PARTS.get(AlchemyArrayRules.Role.OUTPUT).get(),PARTS.get(AlchemyArrayRules.Role.FUEL).get()).build(null));
    public static final Supplier<MenuType<AlchemyArrayMenu>> MENU=MENUS.register("alchemy_array",()->new MenuType<>(AlchemyArrayMenu::new,FeatureFlags.DEFAULT_FLAGS));
    private static final Supplier<CreativeModeTab> TAB=TABS.register("alchemy_array",()->CreativeModeTab.builder().title(Component.literal("青铜丹炉阵"))
            .icon(()->new ItemStack(PARTS.get(AlchemyArrayRules.Role.CONTROLLER).get()))
            .displayItems((parameters,output)->PARTS.values().forEach(block->output.accept(block.get()))).build());
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);TABS.register(bus);
        bus.addListener(AlchemyArrayContent::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,PORT_ENTITY.get(),(port,side)->port.handler(side));
    }
    private AlchemyArrayContent(){ }
}
