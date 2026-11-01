package com.example.myvillage.worldplan.roads;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadCommandsTest {
    @Test void permissiveSharedRootCannotBypassRoadAdminPermission() {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("myvillage"));
        dispatcher.register(Commands.literal("myvillage").requires(s->s.hasPermission(2)).then(RoadRuntime.tree()));
        var ordinary=new CommandSourceStack(CommandSource.NULL,Vec3.ZERO,Vec2.ZERO,null,0,"玩家",Component.literal("玩家"),null,null);
        var admin=new CommandSourceStack(CommandSource.NULL,Vec3.ZERO,Vec2.ZERO,null,2,"管理员",Component.literal("管理员"),null,null);
        var root=dispatcher.getRoot().getChild("myvillage");
        assertTrue(root.canUse(ordinary));assertFalse(root.getChild("roads").canUse(ordinary));assertTrue(root.getChild("roads").canUse(admin));
    }
}
