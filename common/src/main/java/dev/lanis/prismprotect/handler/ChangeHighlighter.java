package dev.lanis.prismprotect.handler;

import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.config.PrismProtectConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChangeHighlighter {

    private static final Map<UUID, Long> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicLong TOKEN = new AtomicLong(1L);

    private ChangeHighlighter() {
    }

    public static void highlight(ServerPlayer player, ServerLevel level, List<BlockLogEntry> entries, int seconds) {
        if (entries.isEmpty()) {
            clear(player.getUUID());
            return;
        }

        int durationSeconds = Math.max(3, Math.min(PrismProtectConfig.maxHighlightDurationSeconds(), seconds));
        long token = TOKEN.getAndIncrement();
        ACTIVE.put(player.getUUID(), token);

        Map<BlockPos, BlockLogEntry> byPosition = new LinkedHashMap<>();
        for (BlockLogEntry entry : entries) {
            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            byPosition.putIfAbsent(pos, entry);
        }

        int endTick = player.server.getTickCount() + durationSeconds * 20;
        schedulePulse(player.server, player.getUUID(), token, level, byPosition, endTick);
    }

    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    private static void schedulePulse(
            MinecraftServer server,
            UUID playerId,
            long token,
            ServerLevel level,
            Map<BlockPos, BlockLogEntry> byPosition,
            int endTick
    ) {
        server.tell(new TickTask(server.getTickCount(), () -> {
            Long activeToken = ACTIVE.get(playerId);
            if (activeToken == null || activeToken != token) {
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.level() != level) {
                ACTIVE.remove(playerId);
                return;
            }

            int currentTick = server.getTickCount();
            if (currentTick >= endTick) {
                ACTIVE.remove(playerId);
                return;
            }

            for (Map.Entry<BlockPos, BlockLogEntry> row : byPosition.entrySet()) {
                BlockPos pos = row.getKey();
                DustParticleOptions particle = colorFor(row.getValue());

                level.sendParticles(
                        player,
                        particle,
                        true,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.65D,
                        pos.getZ() + 0.5D,
                        PrismProtectConfig.highlightParticlesPerBlock(),
                        0.30D,
                        0.20D,
                        0.30D,
                        0.0D
                );
            }

            server.tell(new TickTask(currentTick + PrismProtectConfig.highlightPulseIntervalTicks(), () ->
                    schedulePulse(server, playerId, token, level, byPosition, endTick)
            ));
        }));
    }

    private static DustParticleOptions colorFor(BlockLogEntry entry) {
        Vector3f rgb = switch (entry.action) {
            case BlockLogEntry.ACTION_PLACE -> new Vector3f(0.22F, 0.86F, 0.38F);
            case BlockLogEntry.ACTION_BREAK -> new Vector3f(0.95F, 0.24F, 0.24F);
            case BlockLogEntry.ACTION_EXPLODE -> new Vector3f(0.98F, 0.58F, 0.14F);
            case BlockLogEntry.ACTION_BURN -> new Vector3f(0.98F, 0.86F, 0.22F);
            default -> new Vector3f(0.28F, 0.80F, 0.95F);
        };
        return new DustParticleOptions(rgb, 1.0F);
    }
}
