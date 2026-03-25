package dev.lanis.prismprotect.handler;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.ItemLogEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public final class ItemTracker {

    private static final ThreadLocal<BlockDropContext> BLOCK_DROP_CONTEXT = new ThreadLocal<>();

    private ItemTracker() {
    }

    public static void beginBlockDrop(ServerPlayer player, BlockPos pos, ServerLevel level) {
        BLOCK_DROP_CONTEXT.set(new BlockDropContext(
                System.currentTimeMillis(),
                player.getUUID(),
                player.getGameProfile().getName(),
                level.dimension().location().toString(),
                pos.immutable()
        ));
    }

    public static void endBlockDrop() {
        BLOCK_DROP_CONTEXT.remove();
    }

    public static void logBlockDrop(Level level, BlockPos dropPos, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || stack.isEmpty()) {
            return;
        }

        BlockDropContext context = BLOCK_DROP_CONTEXT.get();
        if (context == null || !context.world.equals(serverLevel.dimension().location().toString())) {
            return;
        }

        BlockPos sourcePos = context.pos;
        if (!sameDropArea(sourcePos, dropPos)) {
            return;
        }

        PrismProtect.getDatabase().logItem(new ItemLogEntry(
                context.time,
                context.player,
                context.world,
                sourcePos.getX(),
                sourcePos.getY(),
                sourcePos.getZ(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                nbtWithoutCount(stack),
                ItemLogEntry.ACTION_ADD,
                ItemLogEntry.SOURCE_BLOCK_DROP,
                0L,
                null
        ));
        ItemProvenanceTracker.addFromSource(context.playerId, context.world, sourcePos, stack);
    }

    public static void logCraftResult(ServerPlayer player, ItemStack stack, long groupId, boolean areaLinked) {
        if (stack.isEmpty()) {
            return;
        }

        BlockPos pos = player.blockPosition();
        PrismProtect.getDatabase().logItem(new ItemLogEntry(
                System.currentTimeMillis(),
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                nbtWithoutCount(stack),
                ItemLogEntry.ACTION_ADD,
                ItemLogEntry.SOURCE_CRAFT,
                groupId,
                null
        ));
    }

    public static void logCraftIngredient(ServerPlayer player, ItemStack stack, long groupId, List<ItemProvenanceTracker.ProvenanceStack> provenance) {
        logCraftDelta(player, stack, ItemLogEntry.ACTION_REMOVE, groupId, ItemProvenanceTracker.encode(provenance));
    }

    public static void logCraftRemainder(ServerPlayer player, ItemStack stack, long groupId) {
        logCraftDelta(player, stack, ItemLogEntry.ACTION_ADD, groupId, null);
    }

    public static void trackCraftResult(ServerPlayer player, ItemStack stack, List<ItemProvenanceTracker.ProvenanceStack> provenance) {
        ItemProvenanceTracker.addCraftResult(player.getUUID(), stack, provenance);
    }

    private static void logCraftDelta(ServerPlayer player, ItemStack stack, int action, long groupId, String provenanceData) {
        if (stack.isEmpty()) {
            return;
        }

        BlockPos pos = player.blockPosition();
        PrismProtect.getDatabase().logItem(new ItemLogEntry(
                System.currentTimeMillis(),
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location().toString(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                nbtWithoutCount(stack),
                action,
                ItemLogEntry.SOURCE_CRAFT,
                groupId,
                provenanceData
        ));
    }

    private static boolean sameDropArea(BlockPos sourcePos, BlockPos dropPos) {
        return Math.abs(sourcePos.getX() - dropPos.getX()) <= 1
                && Math.abs(sourcePos.getY() - dropPos.getY()) <= 1
                && Math.abs(sourcePos.getZ() - dropPos.getZ()) <= 1;
    }

    private static String nbtWithoutCount(ItemStack stack) {
        if (!stack.hasTag()) {
            return null;
        }

        CompoundTag copy = stack.getTag().copy();
        copy.remove("Count");
        return copy.isEmpty() ? null : copy.toString();
    }

    private record BlockDropContext(long time, UUID playerId, String player, String world, BlockPos pos) {
    }
}
