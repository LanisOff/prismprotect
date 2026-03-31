package dev.lanis.prismprotect.forge.client;

import dev.lanis.prismprotect.client.PrismProtectClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class PrismProtectForgeClient {

    private PrismProtectForgeClient() {
    }

    public static void init() {
        PrismProtectClient.init();
        MinecraftForge.EVENT_BUS.register(PrismProtectForgeRenderEvents.class);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        new PrismProtectForgeConfigScreen(parent)
                )
        );
    }
}
