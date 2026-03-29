package dev.lanis.prismprotect.rollback;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.database.ContainerLogEntry;
import dev.lanis.prismprotect.database.EntityLogEntry;
import dev.lanis.prismprotect.database.ItemLogEntry;
import dev.lanis.prismprotect.database.LookupParams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

public final class RollbackManager {

    private RollbackManager() {
    }

    public static int rollback(MinecraftServer server, LookupParams params) {
        List<BlockLogEntry> entries = PrismProtect.getDatabase().getForRollback(params);
        int restoredCount = 0;

        for (BlockLogEntry entry : entries) {
            ServerLevel level = findLevel(server, entry.world);
            if (level == null) {
                continue;
            }

            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            try {
                if (entry.action == BlockLogEntry.ACTION_PLACE) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    PrismProtect.getDatabase().setBlockRolledBack(entry.id, true);
                    restoredCount++;
                    continue;
                }

                if (entry.action == BlockLogEntry.ACTION_BREAK
                        || entry.action == BlockLogEntry.ACTION_EXPLODE
                        || entry.action == BlockLogEntry.ACTION_BURN) {
                    BlockState state = resolveState(entry.blockType);
                    if (state != null) {
                        level.setBlock(pos, state, Block.UPDATE_ALL);
                        if (entry.blockData != null) {
                            restoreBlockEntity(level, pos, entry.blockData);
                        }
                        PrismProtect.getDatabase().setBlockRolledBack(entry.id, true);
                        restoredCount++;
                    }
                }
            } catch (Exception ex) {
                PrismProtect.LOGGER.warn("Rollback error at {}: {}", pos, ex.getMessage());
            }
        }

        return restoredCount;
    }

    public static int restore(MinecraftServer server, LookupParams params) {
        List<BlockLogEntry> entries = PrismProtect.getDatabase().getForRestore(params);
        int restoredCount = 0;

        for (BlockLogEntry entry : entries) {
            ServerLevel level = findLevel(server, entry.world);
            if (level == null) {
                continue;
            }

            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            try {
                if (entry.action == BlockLogEntry.ACTION_PLACE) {
                    BlockState state = resolveState(entry.blockType);
                    if (state != null) {
                        level.setBlock(pos, state, Block.UPDATE_ALL);
                        PrismProtect.getDatabase().setBlockRolledBack(entry.id, false);
                        restoredCount++;
                    }
                    continue;
                }

                if (entry.action == BlockLogEntry.ACTION_BREAK
                        || entry.action == BlockLogEntry.ACTION_EXPLODE
                        || entry.action == BlockLogEntry.ACTION_BURN) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    PrismProtect.getDatabase().setBlockRolledBack(entry.id, false);
                    restoredCount++;
                }
            } catch (Exception ex) {
                PrismProtect.LOGGER.warn("Restore error at {}: {}", pos, ex.getMessage());
            }
        }

        return restoredCount;
    }

    public static int rollbackEntities(MinecraftServer server, LookupParams params) {
        List<EntityLogEntry> entries = PrismProtect.getDatabase().getEntitiesForRollback(params);
        int restoredCount = 0;

        for (EntityLogEntry entry : entries) {
            if (entry.action != EntityLogEntry.ACTION_KILL) {
                continue;
            }

            ServerLevel level = findLevel(server, entry.world);
            if (level == null) {
                continue;
            }

            try {
                ResourceLocation entityId = new ResourceLocation(entry.entityType);
                Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
                if (type.isEmpty()) {
                    continue;
                }

                Entity entity = type.get().create(level);
                if (entity == null) {
                    continue;
                }

                entity.setPos(entry.x, entry.y, entry.z);

                if (entry.entityData != null && !entry.entityData.isBlank()) {
                    try {
                        CompoundTag tag = TagParser.parseTag(entry.entityData);
                        entity.load(tag);
                        entity.setPos(entry.x, entry.y, entry.z);
                    } catch (Exception nbtEx) {
                        PrismProtect.LOGGER.warn("Entity NBT restore failed: {}", nbtEx.getMessage());
                    }
                }

                level.addFreshEntity(entity);
                PrismProtect.getDatabase().setEntityRolledBack(entry.id, true);
                restoredCount++;
            } catch (Exception ex) {
                PrismProtect.LOGGER.warn("Entity rollback error: {}", ex.getMessage());
            }
        }

        return restoredCount;
    }

    public static int rollbackContainers(MinecraftServer server, LookupParams params) {
        List<ContainerLogEntry> entries = PrismProtect.getDatabase().getContainerForRollback(params);
        int restoredCount = 0;

        for (ContainerLogEntry entry : entries) {
            ServerLevel level = findLevel(server, entry.world);
            if (level == null) {
                continue;
            }

            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            ItemStack stack = buildStack(entry);
            if (stack.isEmpty()) {
                continue;
            }

            try {
                if (entry.action == ContainerLogEntry.ACTION_REMOVE) {
                    if (rollbackContainerTake(server, level, pos, entry, stack)) {
                        PrismProtect.getDatabase().setContainerRolledBack(entry.id, true);
                        restoredCount++;
                    }
                    continue;
                }

                if (entry.action == ContainerLogEntry.ACTION_ADD) {
                    if (rollbackContainerPut(server, level, pos, entry, stack)) {
                        PrismProtect.getDatabase().setContainerRolledBack(entry.id, true);
                        restoredCount++;
                    }
                }
            } catch (Exception ex) {
                PrismProtect.LOGGER.warn("Container rollback error at {}: {}", pos, ex.getMessage());
            }
        }

        return restoredCount;
    }

    public static int restoreContainers(MinecraftServer server, LookupParams params) {
        List<ContainerLogEntry> entries = PrismProtect.getDatabase().getContainerForRestore(params);
        int restoredCount = 0;

        for (ContainerLogEntry entry : entries) {
            ServerLevel level = findLevel(server, entry.world);
            if (level == null) {
                continue;
            }

            BlockPos pos = new BlockPos(entry.x, entry.y, entry.z);
            ItemStack stack = buildStack(entry);
            if (stack.isEmpty()) {
                continue;
            }

            try {
                if (entry.action == ContainerLogEntry.ACTION_REMOVE) {
                    if (restoreContainerTake(server, level, pos, entry, stack)) {
                        PrismProtect.getDatabase().setContainerRolledBack(entry.id, false);
                        restoredCount++;
                    }
                    continue;
                }

                if (entry.action == ContainerLogEntry.ACTION_ADD) {
                    if (restoreContainerPut(server, level, pos, entry, stack)) {
                        PrismProtect.getDatabase().setContainerRolledBack(entry.id, false);
                        restoredCount++;
                    }
                }
            } catch (Exception ex) {
                PrismProtect.LOGGER.warn("Container restore error at {}: {}", pos, ex.getMessage());
            }
        }

        return restoredCount;
    }

    private static boolean rollbackContainerTake(
            MinecraftServer server,
            ServerLevel level,
            BlockPos pos,
            ContainerLogEntry entry,
            ItemStack stack
    ) {
        int remaining = reclaimFromPlayerOrGround(server, entry, pos, stack);
        if (remaining > 0) {
            return false;
        }

        return PrismProtect.getContainerAccess().addItem(level, pos, stack.copy());
    }

    private static boolean rollbackContainerPut(
            MinecraftServer server,
            ServerLevel level,
            BlockPos pos,
            ContainerLogEntry entry,
            ItemStack stack
    ) {
        if (!PrismProtect.getContainerAccess().removeItem(level, pos, stack.copy())) {
            return false;
        }

        giveToPlayerOrDrop(server, entry, pos, stack);
        return true;
    }

    private static boolean restoreContainerTake(
            MinecraftServer server,
            ServerLevel level,
            BlockPos pos,
            ContainerLogEntry entry,
            ItemStack stack
    ) {
        if (!PrismProtect.getContainerAccess().removeItem(level, pos, stack.copy())) {
            return false;
        }

        giveToPlayerOrDrop(server, entry, pos, stack);
        return true;
    }

    private static boolean restoreContainerPut(
            MinecraftServer server,
            ServerLevel level,
            BlockPos pos,
            ContainerLogEntry entry,
            ItemStack stack
    ) {
        int remaining = reclaimFromPlayerOrGround(server, entry, pos, stack);
        if (remaining > 0) {
            return false;
        }

        return PrismProtect.getContainerAccess().addItem(level, pos, stack.copy());
    }

    public static int rollbackItems(MinecraftServer server, LookupParams params) {
        List<ItemLogEntry> entries = PrismProtect.getDatabase().getItemsForRollback(params);
        int restoredCount = 0;

        for (ItemLogEntry entry : entries) {
            ItemStack stack = buildStack(entry.itemType, entry.amount, entry.itemData);
            if (stack.isEmpty()) {
                continue;
            }

            boolean success = entry.action == ItemLogEntry.ACTION_ADD
                    ? reclaimItem(server, entry, stack)
                    : grantItem(server, entry, stack);

            if (success) {
                PrismProtect.getDatabase().setItemRolledBack(entry.id, true);
                restoredCount++;
            }
        }

        return restoredCount;
    }

    public static int restoreItems(MinecraftServer server, LookupParams params) {
        List<ItemLogEntry> entries = PrismProtect.getDatabase().getItemsForRestore(params);
        int restoredCount = 0;

        for (ItemLogEntry entry : entries) {
            ItemStack stack = buildStack(entry.itemType, entry.amount, entry.itemData);
            if (stack.isEmpty()) {
                continue;
            }

            boolean success = entry.action == ItemLogEntry.ACTION_ADD
                    ? grantItem(server, entry, stack)
                    : reclaimItem(server, entry, stack);

            if (success) {
                PrismProtect.getDatabase().setItemRolledBack(entry.id, false);
                restoredCount++;
            }
        }

        return restoredCount;
    }

    private static ItemStack buildStack(ContainerLogEntry entry) {
        return buildStack(entry.itemType, entry.amount, entry.itemData);
    }

    private static ItemStack buildStack(String itemType, int amount, String itemData) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemType));
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, amount);
            if (itemData != null && !itemData.isBlank()) {
                stack.setTag(TagParser.parseTag(itemData));
            }
            return stack;
        } catch (Exception ex) {
            PrismProtect.LOGGER.warn("Failed to build ItemStack for {}: {}", itemType, ex.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static BlockState resolveState(String id) {
        try {
            return BuiltInRegistries.BLOCK.get(new ResourceLocation(id)).defaultBlockState();
        } catch (Exception ex) {
            PrismProtect.LOGGER.warn("Unknown block: {}", id);
            return null;
        }
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String name) {
        if (name == null) {
            return null;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) {
                return player;
            }
        }

        return null;
    }

    private static int removeFromInventory(Inventory inventory, ItemStack matcher, int remaining) {
        remaining = removeFromList(inventory.items, matcher, remaining);
        remaining = removeFromList(inventory.armor, matcher, remaining);
        remaining = removeFromList(inventory.offhand, matcher, remaining);
        return remaining;
    }

    private static int removeFromList(List<ItemStack> stacks, ItemStack matcher, int remaining) {
        for (ItemStack slotStack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (!matches(slotStack, matcher)) {
                continue;
            }

            int taken = Math.min(slotStack.getCount(), remaining);
            slotStack.shrink(taken);
            remaining -= taken;
        }
        return remaining;
    }

    private static boolean reclaimItem(MinecraftServer server, ItemLogEntry entry, ItemStack stack) {
        int remaining = stack.getCount();
        ServerPlayer player = findPlayer(server, entry.player);
        if (player != null) {
            remaining = removeFromInventory(player.getInventory(), stack, remaining);
            if (remaining == 0) {
                player.containerMenu.broadcastChanges();
            }
        }

        if (remaining > 0) {
            ServerLevel level = findLevel(server, entry.world);
            if (level != null) {
                remaining = removeFromGround(level, new BlockPos(entry.x, entry.y, entry.z), stack, remaining);
            }
        }

        return remaining == 0;
    }

    private static boolean grantItem(MinecraftServer server, ItemLogEntry entry, ItemStack stack) {
        ServerPlayer player = findPlayer(server, entry.player);
        if (player != null) {
            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.containerMenu.broadcastChanges();
            return true;
        }

        ServerLevel level = findLevel(server, entry.world);
        if (level == null) {
            return false;
        }

        ItemEntity itemEntity = new ItemEntity(
                level,
                entry.x + 0.5D,
                entry.y + 0.5D,
                entry.z + 0.5D,
                stack.copy()
        );
        level.addFreshEntity(itemEntity);
        return true;
    }

    private static int removeFromGround(ServerLevel level, BlockPos pos, ItemStack matcher, int remaining) {
        AABB box = new AABB(pos).inflate(4.0D);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (remaining <= 0) {
                break;
            }

            ItemStack entityStack = itemEntity.getItem();
            if (!matches(entityStack, matcher)) {
                continue;
            }

            int taken = Math.min(entityStack.getCount(), remaining);
            entityStack.shrink(taken);
            remaining -= taken;
            if (entityStack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(entityStack);
            }
        }
        return remaining;
    }

    private static int reclaimFromPlayerOrGround(
            MinecraftServer server,
            ContainerLogEntry entry,
            BlockPos pos,
            ItemStack stack
    ) {
        int remaining = stack.getCount();
        ServerPlayer player = findPlayer(server, entry.player);
        if (player != null) {
            remaining = removeFromInventory(player.getInventory(), stack, remaining);
            if (remaining == 0) {
                player.containerMenu.broadcastChanges();
            }
        }

        if (remaining > 0) {
            ServerLevel level = findLevel(server, entry.world);
            if (level != null) {
                remaining = removeFromGround(level, pos, stack, remaining);
            }
        }

        return remaining;
    }

    private static void giveToPlayerOrDrop(
            MinecraftServer server,
            ContainerLogEntry entry,
            BlockPos pos,
            ItemStack stack
    ) {
        ServerPlayer player = findPlayer(server, entry.player);
        if (player != null) {
            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            if (!remainder.isEmpty()) {
                player.drop(remainder, false);
            }
            player.containerMenu.broadcastChanges();
            return;
        }

        ServerLevel level = findLevel(server, entry.world);
        if (level == null) {
            return;
        }

        ItemEntity itemEntity = new ItemEntity(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                stack.copy()
        );
        level.addFreshEntity(itemEntity);
    }

    private static boolean matches(ItemStack left, ItemStack right) {
        return !left.isEmpty()
                && ItemStack.isSameItemSameTags(left, right);
    }

    private static void restoreBlockEntity(ServerLevel level, BlockPos pos, String nbt) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null && nbt.startsWith("{")) {
                blockEntity.load(TagParser.parseTag(nbt));
                blockEntity.setChanged();
            }
        } catch (Exception ex) {
            PrismProtect.LOGGER.warn("BlockEntity restore failed at {}: {}", pos, ex.getMessage());
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String worldName) {
        for (ServerLevel level : server.getAllLevels()) {
            String location = level.dimension().location().toString();
            if (location.equals(worldName) || location.replace("minecraft:", "").equals(worldName)) {
                return level;
            }
        }
        return null;
    }
}
