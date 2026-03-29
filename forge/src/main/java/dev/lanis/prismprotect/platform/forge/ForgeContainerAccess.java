package dev.lanis.prismprotect.platform.forge;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.platform.VanillaContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;

public class ForgeContainerAccess extends VanillaContainerAccess {

    @Override
    public boolean addItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            PrismProtect.LOGGER.warn("No block entity at {} for container rollback", pos);
            return false;
        }

        var capOpt = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (capOpt.isPresent() && capOpt.get() instanceof IItemHandlerModifiable handler) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            boolean success = remaining.isEmpty();
            if (!success) {
                PrismProtect.LOGGER.warn(
                        "Could not fully add {} x{} to container at {} ({} remaining)",
                        stack.getItem(), stack.getCount(), pos, remaining.getCount());
            }
            be.setChanged();
            return success;
        }

        return super.addItem(level, pos, stack);
    }

    @Override
    public boolean removeItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            PrismProtect.LOGGER.warn("No block entity at {} for container restore", pos);
            return false;
        }

        var capOpt = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (capOpt.isPresent() && capOpt.get() instanceof IItemHandlerModifiable handler) {
            int toRemove = stack.getCount();
            for (int slot = 0; slot < handler.getSlots() && toRemove > 0; slot++) {
                ItemStack slotStack = handler.getStackInSlot(slot);
                if (!slotStack.isEmpty() && ItemStack.isSameItemSameTags(slotStack, stack)) {
                    int take = Math.min(slotStack.getCount(), toRemove);
                    handler.extractItem(slot, take, false);
                    toRemove -= take;
                }
            }
            if (toRemove > 0) {
                PrismProtect.LOGGER.warn(
                        "Could not fully remove {} x{} from container at {} ({} missing)",
                        stack.getItem(), stack.getCount(), pos, toRemove
                );
            }
            be.setChanged();
            return toRemove == 0;
        }

        return super.removeItem(level, pos, stack);
    }
}
