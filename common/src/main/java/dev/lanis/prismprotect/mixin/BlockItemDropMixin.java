package dev.lanis.prismprotect.mixin;

import dev.lanis.prismprotect.handler.ItemTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockItemDropMixin {

    @Inject(
            method = "playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private void prismprotect$beginBlockDrop(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            ItemStack tool,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            ItemTracker.beginBlockDrop(serverPlayer, pos, serverLevel);
        }
    }

    @Inject(
            method = "playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN")
    )
    private void prismprotect$endBlockDrop(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            ItemStack tool,
            CallbackInfo ci
    ) {
        ItemTracker.endBlockDrop();
    }

    @Inject(
            method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private static void prismprotect$logBlockDrop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        ItemTracker.logBlockDrop(level, pos, stack);
    }

    @Inject(
            method = "popResourceFromFace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private static void prismprotect$logBlockDropFromFace(
            Level level,
            BlockPos pos,
            net.minecraft.core.Direction direction,
            ItemStack stack,
            CallbackInfo ci
    ) {
        ItemTracker.logBlockDrop(level, pos, stack);
    }
}
