package com.example.myvillage.entity.npc;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** 服务器在所有实际入世入口补齐普通修士外观，命簿已设置的外观保持权威。 */
public final class CultivatorAppearance {
    private CultivatorAppearance() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CultivatorAppearance::joined);
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof CultivatorEntity npc) {
            npc.prepareAppearance(event.loadedFromDisk());
        }
    }
}
