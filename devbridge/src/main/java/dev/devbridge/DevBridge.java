package dev.devbridge;

import com.mojang.logging.LogUtils;
import dev.devbridge.endpoint.ClientEndpoints;
import dev.devbridge.endpoint.CommandEndpoints;
import dev.devbridge.endpoint.InfoEndpoints;
import dev.devbridge.endpoint.LogEndpoints;
import dev.devbridge.endpoint.ReflectEndpoints;
import dev.devbridge.endpoint.WorldEndpoints;
import dev.devbridge.http.BridgeHttpServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * DevBridge: a development-only HTTP bridge into a running Minecraft instance.
 *
 * It knows nothing about any particular mod. It exposes generic observation and
 * control primitives (logs, commands, registries, world state, reflection, client
 * screenshots) that an external agent / MCP server can use to verify whatever mod
 * is under development.
 */
@Mod(DevBridge.MOD_ID)
public final class DevBridge {
    public static final String MOD_ID = "devbridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static BridgeHttpServer http;

    public DevBridge(IEventBus modBus, ModContainer container) {
        // Attach the in-memory log ring buffer as early as possible so startup
        // output of other mods is captured too.
        LogBuffer.install();

        BridgeConfig config = BridgeConfig.load();
        LogBuffer.get().setCapacity(config.logBufferSize());
        GameAccess.setTimeout(config.timeoutMillis());

        http = new BridgeHttpServer(config);
        InfoEndpoints.register(http);
        LogEndpoints.register(http);
        CommandEndpoints.register(http);
        WorldEndpoints.register(http);
        ReflectEndpoints.register(http);
        if (FMLEnvironment.dist.isClient()) {
            // Only touch client classes when we're actually on the client.
            ClientEndpoints.register(http);
        }
        try {
            http.start();
            Runtime.getRuntime().addShutdownHook(new Thread(DevBridge::shutdown, "devbridge-shutdown"));
            LOGGER.info("[DevBridge] listening on http://{}:{}  (token in config/devbridge.json)",
                    config.bind(), config.port());
        } catch (Exception e) {
            // Never take the game down because of the bridge (e.g. port already in use
            // when client and server dev runs share one config).
            LOGGER.error("[DevBridge] could not start HTTP server on {}:{} - bridge disabled", config.bind(), config.port(), e);
            http = null;
        }
    }

    public static void shutdown() {
        if (http != null) {
            http.stop();
            http = null;
        }
    }
}
