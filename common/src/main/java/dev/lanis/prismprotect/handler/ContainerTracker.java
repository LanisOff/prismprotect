package dev.lanis.prismprotect.handler;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.ContainerLogEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerTracker {

    private static final Map<UUID, BlockInfo> LAST_INTERACTED_BLOCK = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private ContainerTracker() {
    }

    record BlockInfo(BlockPos pos, String world) {
    }

    record Snapshot(BlockPos pos, String world, Map<String, SlotCount> counts) {
    }

    record SlotCount(String itemType, String itemData, int count) {
    }

    public static void storeInteract(UUID playerId, BlockPos pos, String world) {
        LAST_INTERACTED_BLOCK.put(playerId, new BlockInfo(pos, world));
    }

    public static void onOpen(UUID playerId, AbstractContainerMenu menu) {
        BlockInfo blockInfo = LAST_INTERACTED_BLOCK.get(playerId);
        if (blockInfo == null) {
            return;
        }

        SNAPSHOTS.put(playerId, new Snapshot(blockInfo.pos, blockInfo.world, aggregate(menu)));
    }

    public static void onClose(UUID playerId, AbstractContainerMenu menu, String playerName) {
        Snapshot snapshot = SNAPSHOTS.remove(playerId);
        if (snapshot == null) {
            return;
        }

        Map<String, SlotCount> before = snapshot.counts;
        Map<String, SlotCount> after = aggregate(menu);
        long eventTime = System.currentTimeMillis();

        for (Map.Entry<String, SlotCount> entry : before.entrySet()) {
            SlotCount beforeCount = entry.getValue();
            int previous = beforeCount.count;
            int current = after.getOrDefault(entry.getKey(), new SlotCount(beforeCount.itemType, beforeCount.itemData, 0)).count;

            int diff = previous - current;
            if (diff > 0) {
                ItemStack transfer = buildStack(beforeCount.itemType, beforeCount.itemData, diff);
                PrismProtect.getDatabase().logContainer(new ContainerLogEntry(
                        eventTime,
                        playerName,
                        snapshot.world,
                        snapshot.pos.getX(),
                        snapshot.pos.getY(),
                        snapshot.pos.getZ(),
                        beforeCount.itemType,
                        diff,
                        beforeCount.itemData,
                        ContainerLogEntry.ACTION_REMOVE
                ));
                ItemProvenanceTracker.addFromSource(playerId, snapshot.world, snapshot.pos, transfer);
            }
        }

        for (Map.Entry<String, SlotCount> entry : after.entrySet()) {
            SlotCount afterCount = entry.getValue();
            int current = afterCount.count;
            int previous = before.getOrDefault(entry.getKey(), new SlotCount(afterCount.itemType, afterCount.itemData, 0)).count;

            int diff = current - previous;
            if (diff > 0) {
                ItemStack transfer = buildStack(afterCount.itemType, afterCount.itemData, diff);
                PrismProtect.getDatabase().logContainer(new ContainerLogEntry(
                        eventTime,
                        playerName,
                        snapshot.world,
                        snapshot.pos.getX(),
                        snapshot.pos.getY(),
                        snapshot.pos.getZ(),
                        afterCount.itemType,
                        diff,
                        afterCount.itemData,
                        ContainerLogEntry.ACTION_ADD
                ));
                ItemProvenanceTracker.consume(playerId, transfer, diff);
            }
        }
    }

    public static void clear(UUID playerId) {
        LAST_INTERACTED_BLOCK.remove(playerId);
        SNAPSHOTS.remove(playerId);
    }

    private static Map<String, SlotCount> aggregate(AbstractContainerMenu menu) {
        Map<String, SlotCount> counts = new LinkedHashMap<>();

        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            String key = itemKey(stack);
            String itemType = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String itemData = nbtWithoutCount(stack);

            counts.merge(
                    key,
                    new SlotCount(itemType, itemData, stack.getCount()),
                    (previous, next) -> new SlotCount(previous.itemType, previous.itemData, previous.count + next.count)
            );
        }

        return counts;
    }

    private static String itemKey(ItemStack stack) {
        String type = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompoundTag tag = stack.getTag();
        return type + (tag != null ? "|" + tag : "");
    }

    private static String nbtWithoutCount(ItemStack stack) {
        if (!stack.hasTag()) {
            return null;
        }

        CompoundTag copy = stack.getTag().copy();
        copy.remove("Count");
        return copy.isEmpty() ? null : copy.toString();
    }

    private static ItemStack buildStack(String itemType, String itemData, int count) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(itemType)), count);
        if (itemData != null && !itemData.isBlank()) {
            try {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(itemData));
            } catch (Exception ignored) {
            }
        }
        return stack;
    }
}
