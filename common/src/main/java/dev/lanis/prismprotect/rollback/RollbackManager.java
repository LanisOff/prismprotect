package dev.lanis.prismprotect.rollback;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.database.ContainerLogEntry;
import dev.lanis.prismprotect.database.EntityLogEntry;
import dev.lanis.prismprotect.database.LookupParams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
                    if (PrismProtect.getContainerAccess().addItem(level, pos, stack)) {
                        PrismProtect.getDatabase().setContainerRolledBack(entry.id, true);
                        restoredCount++;
                    }
                    continue;
                }

                if (entry.action == ContainerLogEntry.ACTION_ADD) {
                    if (PrismProtect.getContainerAccess().removeItem(level, pos, stack)) {
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
                    if (PrismProtect.getContainerAccess().removeItem(level, pos, stack)) {
                        PrismProtect.getDatabase().setContainerRolledBack(entry.id, false);
                        restoredCount++;
                    }
                    continue;
                }

                if (entry.action == ContainerLogEntry.ACTION_ADD) {
                    if (PrismProtect.getContainerAccess().addItem(level, pos, stack)) {
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

    private static ItemStack buildStack(ContainerLogEntry entry) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(entry.itemType));
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, entry.amount);
            if (entry.itemData != null && !entry.itemData.isBlank()) {
                stack.setTag(TagParser.parseTag(entry.itemData));
            }
            return stack;
        } catch (Exception ex) {
            PrismProtect.LOGGER.warn("Failed to build ItemStack for {}: {}", entry.itemType, ex.getMessage());
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
