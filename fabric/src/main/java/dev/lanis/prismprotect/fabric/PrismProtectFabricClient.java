package dev.lanis.prismprotect.fabric;

import dev.lanis.prismprotect.client.HighlightRenderer;
import dev.lanis.prismprotect.client.PrismProtectClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class PrismProtectFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PrismProtectClient.init();
        WorldRenderEvents.LAST.register(context -> {
            if (context.matrixStack() == null || context.camera() == null) {
                return;
            }
            HighlightRenderer.render(context.matrixStack(), context.camera());
        });
    }
}
