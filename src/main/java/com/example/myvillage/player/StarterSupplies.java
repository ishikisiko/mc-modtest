package com.example.myvillage.player;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** 每个世界分别保存玩家的领取记录；重连和死亡均不刷新初始物资。 */
public final class StarterSupplies {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "myvillage");
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> RECEIVED =
            TYPES.register("starter_supplies_received", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL).copyOnDeath().build());
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue COOKED_BEEF_COUNT;

    static {
        var builder = new ModConfigSpec.Builder();
        COOKED_BEEF_COUNT = builder.comment("每名玩家首次进入这个世界时获得的熟牛排数量。")
                .defineInRange("cookedBeefCount", 16, 1, 64);
        SPEC = builder.build();
    }

    private StarterSupplies() {}

    public static void register(IEventBus bus, ModContainer container) {
        TYPES.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "myvillage-starter-supplies-server.toml");
        NeoForge.EVENT_BUS.addListener(StarterSupplies::login);
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getData(RECEIVED)) return;
        player.setData(RECEIVED, true);
        // 原版背包入口会把放不下的余量掉在玩家脚边，不覆盖已有物品。
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.COOKED_BEEF, COOKED_BEEF_COUNT.get()));
    }
}
