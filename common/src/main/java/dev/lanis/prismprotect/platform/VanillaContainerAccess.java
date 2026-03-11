package dev.lanis.prismprotect.platform;

import dev.lanis.prismprotect.PrismProtect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class VanillaContainerAccess implements ContainerAccess {

    @Override
    public boolean addItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            PrismProtect.LOGGER.warn("Block at {} has no vanilla inventory", pos);
            return false;
        }

        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                container.setItem(slot, remaining.split(remaining.getCount()));
            } else if (ItemStack.isSameItemSameTags(existing, remaining)) {
                int space = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (space > 0) {
                    existing.grow(space);
                    remaining.shrink(space);
                }
            }
        }

        be.setChanged();
        return true;
    }

    @Override
    public boolean removeItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            PrismProtect.LOGGER.warn("Block at {} has no vanilla inventory", pos);
            return false;
        }

        int toRemove = stack.getCount();
        for (int slot = 0; slot < container.getContainerSize() && toRemove > 0; slot++) {
            ItemStack slotStack = container.getItem(slot);
            if (!slotStack.isEmpty() && ItemStack.isSameItemSameTags(slotStack, stack)) {
                int take = Math.min(slotStack.getCount(), toRemove);
                slotStack.shrink(take);
                if (slotStack.isEmpty()) {
                    container.setItem(slot, ItemStack.EMPTY);
                }
                toRemove -= take;
            }
        }

        be.setChanged();
        return true;
    }
}
