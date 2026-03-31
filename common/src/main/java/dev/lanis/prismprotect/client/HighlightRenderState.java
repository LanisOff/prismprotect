package dev.lanis.prismprotect.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HighlightRenderState {

    private static volatile Map<BlockPos, Integer> actions = Map.of();
    private static volatile String world = "";
    private static volatile long expireAtMs = 0L;

    private HighlightRenderState() {
    }

    public static void apply(String worldId, int durationTicks, List<HighlightBlock> blocks) {
        world = worldId;

        if (durationTicks == 0 || blocks.isEmpty()) {
            actions = Map.of();
            expireAtMs = 0L;
            return;
        }

        Map<BlockPos, Integer> next = new HashMap<>(blocks.size());
        for (HighlightBlock block : blocks) {
            next.put(new BlockPos(block.x, block.y, block.z), block.action);
        }
        actions = Map.copyOf(next);

        if (durationTicks < 0) {
            expireAtMs = -1L;
            return;
        }
        expireAtMs = System.currentTimeMillis() + (durationTicks * 50L);
    }

    public static Map<BlockPos, Integer> snapshot() {
        if (isExpired()) {
            actions = Map.of();
            return Map.of();
        }
        return actions;
    }

    public static boolean isActive() {
        return !actions.isEmpty() && !isExpired() && isSameWorld();
    }

    private static boolean isSameWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        return minecraft.level.dimension().location().toString().equals(world);
    }

    private static boolean isExpired() {
        if (expireAtMs < 0L) {
            return false;
        }
        return expireAtMs == 0L || System.currentTimeMillis() > expireAtMs;
    }

    public record HighlightBlock(int x, int y, int z, int action) {
    }
}
