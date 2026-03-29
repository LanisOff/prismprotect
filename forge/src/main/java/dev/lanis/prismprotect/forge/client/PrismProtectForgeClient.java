package dev.lanis.prismprotect.forge.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class PrismProtectForgeClient {

    private PrismProtectForgeClient() {
    }

    public static void init() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        new PrismProtectForgeConfigScreen(parent)
                )
        );
    }
}
