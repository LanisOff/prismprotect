package dev.lanis.prismprotect.forge.client;

import dev.lanis.prismprotect.client.HighlightRenderer;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class PrismProtectForgeRenderEvents {

    private PrismProtectForgeRenderEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (event.getPoseStack() == null || event.getCamera() == null) {
            return;
        }
        HighlightRenderer.render(event.getPoseStack(), event.getCamera());
    }
}
