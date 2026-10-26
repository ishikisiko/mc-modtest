package com.example.myvillage.sim.runtime.life;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

class NpcLifeCommandsTest {
    @Test void explicitProductionCommandsAcceptNamespacedHerbsAndRequirePositiveQuotas() throws Exception {
        var root = NpcLifeCommands.command().build();
        for (String name : new String[]{"gathering", "garden"}) {
            var herb = (ArgumentCommandNode<CommandSourceStack, ?>) root.getChild(name)
                    .getChild("site").getChild("place").getChild("herb");
            assertEquals("myvillage:spirit_leaf", herb.getType().parse(new StringReader("myvillage:spirit_leaf")).toString());
            if (name.equals("gathering")) {
                var quota = (ArgumentCommandNode<CommandSourceStack, ?>) herb.getChild("daily_quota");
                assertEquals(1, quota.getType().parse(new StringReader("1")));
                assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                        () -> quota.getType().parse(new StringReader("0")));
            }
        }
    }

    @Test void professionsOnlyAcceptTheirOwnStationKinds() {
        assertTrue(NpcLifeCommands.supportsWork("herb_child", "gather"));
        assertTrue(NpcLifeCommands.supportsWork("herb_child", "garden"));
        assertTrue(NpcLifeCommands.supportsWork("spirit_grower", "garden"));
        assertTrue(NpcLifeCommands.supportsWork("alchemist", "work"));
        for (String kind : NpcLifeSites.KINDS) {
            if (!kind.equals("garden")) assertFalse(NpcLifeCommands.supportsWork("spirit_grower", kind));
            if (!kind.equals("work")) assertFalse(NpcLifeCommands.supportsWork("alchemist", kind));
        }
        assertFalse(NpcLifeCommands.supportsWork("herb_child", "work"));
        assertFalse(NpcLifeCommands.supportsWork("herbalist", "garden"));
        assertFalse(NpcLifeCommands.supportsWork("herbalist", "gather"));
        assertTrue(NpcLifeCommands.supportsWork("herbalist", "work"));
        assertFalse(NpcLifeCommands.supportsWork("unknown", "work"));
    }
}
