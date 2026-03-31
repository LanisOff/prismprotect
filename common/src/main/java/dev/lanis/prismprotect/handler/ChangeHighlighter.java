package dev.lanis.prismprotect.handler;

import dev.lanis.prismprotect.config.PrismProtectConfig;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.network.HighlightSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChangeHighlighter {

    private static final Map<UUID, Long> ACTIVE = new ConcurrentHashMap<>();

    private ChangeHighlighter() {
    }

    public static void highlight(ServerPlayer player, ServerLevel level, List<BlockLogEntry> entries, int seconds) {
        if (entries.isEmpty()) {
            clear(player.getUUID(), level.dimension().location().toString(), player);
            return;
        }

        int durationTicks;
        if (seconds <= 0) {
            durationTicks = -1;
        } else {
            int durationSeconds = Math.max(3, Math.min(PrismProtectConfig.maxHighlightDurationSeconds(), seconds));
            durationTicks = durationSeconds * 20;
        }

        Map<BlockPos, BlockLogEntry> byPosition = new LinkedHashMap<>();
        for (BlockLogEntry entry : entries) {
            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            byPosition.putIfAbsent(pos, entry);
        }

        int maxBlocks = PrismProtectConfig.maxHighlightedBlocks();
        if (byPosition.size() > maxBlocks) {
            List<Map.Entry<BlockPos, BlockLogEntry>> nearest = new java.util.ArrayList<>(byPosition.entrySet());
            nearest.sort(java.util.Comparator.comparingDouble(
                    row -> row.getKey().distToCenterSqr(player.getX(), player.getY(), player.getZ())
            ));

            byPosition.clear();
            for (int i = 0; i < maxBlocks; i++) {
                Map.Entry<BlockPos, BlockLogEntry> row = nearest.get(i);
                byPosition.put(row.getKey(), row.getValue());
            }
        }

        List<BlockLogEntry> payload = new java.util.ArrayList<>(byPosition.values());
        HighlightSyncPacket.send(player, level.dimension().location().toString(), durationTicks, payload);
        ACTIVE.put(player.getUUID(), durationTicks < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationTicks * 50L);
    }

    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void clear(UUID playerId, String world, ServerPlayer player) {
        ACTIVE.remove(playerId);
        HighlightSyncPacket.sendClear(player, world);
    }

    public static int activeCount() {
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(row -> row.getValue() <= now);
        return ACTIVE.size();
    }
}
