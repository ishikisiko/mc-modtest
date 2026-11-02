package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** 显示创建世界时已经预留的地块；无临时营造和冲突重试。 */
public final class GateRealizerCommands {
    private GateRealizerCommands(){}
    public static LiteralArgumentBuilder<CommandSourceStack> node(){
        return Commands.literal("gates").executes(ctx->{
            var source=ctx.getSource();var generator=WorldPlanRuntime.generator(source.getLevel());
            if(generator.isEmpty()){source.sendFailure(Component.literal("当前世界未使用修仙世界生成，未预留宗门地块"));return 0;}
            int total=generator.get().plan().sectSites().size();
            long bound=WorldSimRuntime.sim().map(sim->sim.sects(false).stream().filter(s->s.gateRealized()).count()).orElse(0L);
            source.sendSuccess(()->Component.literal("宗门预留地块："+total+"；已加载并绑定宗门："+bound+"。建筑随区块生成。"),false);
            return total;
        });
    }
}
