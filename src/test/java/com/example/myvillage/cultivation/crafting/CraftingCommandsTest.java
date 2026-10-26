package com.example.myvillage.cultivation.crafting;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingCommandsTest {
    @Test void setterAliasesHaveEquivalentProfessionBranchesAndBoundedIntegerArguments() {
        var tree = CraftingCommands.tree("crafting").build();
        assertEquals(Set.of("target", "set", "shezhi"), children(tree));
        assertNotNull(tree.getCommand()); assertNotNull(tree.getChild("target").getCommand());
        for (String setter : new String[] {"set", "shezhi"}) {
            var target = tree.getChild(setter).getChild("target");
            assertEquals(Set.of("alchemy", "liandan", "forging", "lianqi", "talisman", "zhifu"), children(target));
            for (String profession : children(target)) {
                var value = target.getChild(profession).getChild("value");
                assertNotNull(value.getCommand());
                var type = (IntegerArgumentType) ((ArgumentCommandNode<CommandSourceStack, ?>) value).getType();
                assertEquals(0, type.getMinimum()); assertEquals(100, type.getMaximum());
                assertTrue(value.getChildren().isEmpty());
            }
        }
        assertNull(tree.getChild("reset")); assertNull(tree.getChild("roll"));
    }
    @Test void diagnosticOutputDistinguishesPendingFromLegitimateZeroAptitude() {
        var rules = CraftingRules.get();
        var pending = CraftingCommands.status(CraftingAptitudes.EMPTY, rules);
        assertEquals(Set.of("initialized", "alchemy", "forging", "alchemy_quality_cap", "forging_quality_cap", "talisman_initialized", "talisman", "talisman_quality_cap"), pending.keySet());
        assertFalse(pending.get("initialized").getAsBoolean());
        assertEquals(0, pending.get("alchemy_quality_cap").getAsInt());
        var initialized = CraftingCommands.status(new CraftingAptitudes(1, true, 0, 100), rules);
        assertTrue(initialized.get("initialized").getAsBoolean());
        assertEquals(1, initialized.get("alchemy_quality_cap").getAsInt());
        assertEquals(4, initialized.get("forging_quality_cap").getAsInt());
    }
    private static Set<String> children(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }
}
