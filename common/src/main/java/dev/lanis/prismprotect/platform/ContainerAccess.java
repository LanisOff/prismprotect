package dev.lanis.prismprotect.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public interface ContainerAccess {

    boolean addItem(ServerLevel level, BlockPos pos, ItemStack stack);

    boolean removeItem(ServerLevel level, BlockPos pos, ItemStack stack);
}
