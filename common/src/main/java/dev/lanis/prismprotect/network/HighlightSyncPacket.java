package dev.lanis.prismprotect.network;

import dev.architectury.networking.NetworkManager;
import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.client.HighlightRenderState;
import dev.lanis.prismprotect.database.BlockLogEntry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class HighlightSyncPacket {

    public static final ResourceLocation ID = new ResourceLocation(PrismProtect.MOD_ID, "highlight_sync");

    private HighlightSyncPacket() {
    }

    public static void send(ServerPlayer player, String world, int durationTicks, List<BlockLogEntry> entries) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(world);
        buf.writeVarInt(durationTicks);
        buf.writeVarInt(entries.size());
        for (BlockLogEntry entry : entries) {
            buf.writeInt(entry.x);
            buf.writeInt(entry.y);
            buf.writeInt(entry.z);
            buf.writeVarInt(entry.action);
        }
        NetworkManager.sendToPlayer(player, ID, buf);
    }

    public static void sendClear(ServerPlayer player, String world) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(world);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        NetworkManager.sendToPlayer(player, ID, buf);
    }

    public static void registerClientReceiver() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ID, (buf, context) -> {
            String world = buf.readUtf();
            int durationTicks = buf.readVarInt();
            int size = buf.readVarInt();

            List<HighlightRenderState.HighlightBlock> blocks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                blocks.add(new HighlightRenderState.HighlightBlock(
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readVarInt()
                ));
            }

            context.queue(() -> HighlightRenderState.apply(world, durationTicks, blocks));
        });
    }
}
