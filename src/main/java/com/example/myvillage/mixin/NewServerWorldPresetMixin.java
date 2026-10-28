package com.example.myvillage.mixin;

import com.example.myvillage.worldplan.runtime.NewWorldDefaults;
import java.util.Properties;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Only fills an omitted level-type. Minecraft still loads existing level.dat dimensions unchanged. */
@Mixin(DedicatedServerProperties.class)
public abstract class NewServerWorldPresetMixin {
    @ModifyArg(method="<init>", at=@At(value="INVOKE",
            target="Lnet/minecraft/server/dedicated/Settings;<init>(Ljava/util/Properties;)V"), index=0, require=1, allow=1)
    private static Properties myvillage$default(Properties properties) {
        return NewWorldDefaults.server(properties);
    }
}
