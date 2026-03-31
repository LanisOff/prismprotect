package dev.lanis.prismprotect.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Map;

public final class HighlightRenderer {

    private HighlightRenderer() {
    }

    public static void render(PoseStack poseStack, Camera camera) {
        if (!HighlightRenderState.isActive()) {
            return;
        }

        Map<BlockPos, Integer> blocks = HighlightRenderState.snapshot();
        if (blocks.isEmpty()) {
            return;
        }

        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        var lines = buffers.getBuffer(RenderType.lines());

        for (Map.Entry<BlockPos, Integer> row : blocks.entrySet()) {
            BlockPos pos = row.getKey();
            AABB box = new AABB(pos).inflate(0.002D).move(-camX, -camY, -camZ);

            float[] color = colorFor(row.getValue());
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    box,
                    color[0],
                    color[1],
                    color[2],
                    0.95F
            );
        }

        buffers.endBatch(RenderType.lines());
    }

    private static float[] colorFor(int action) {
        return switch (action) {
            case 0 -> new float[]{0.22F, 0.86F, 0.38F};
            case 1 -> new float[]{0.95F, 0.24F, 0.24F};
            case 2 -> new float[]{0.98F, 0.58F, 0.14F};
            case 3 -> new float[]{0.98F, 0.86F, 0.22F};
            default -> new float[]{0.28F, 0.80F, 0.95F};
        };
    }
}
