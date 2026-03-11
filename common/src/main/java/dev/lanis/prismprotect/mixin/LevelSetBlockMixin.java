package dev.lanis.prismprotect.mixin;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.handler.BlockChangeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelSetBlockMixin {

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
        at = @At("HEAD")
    )
    private void prismprotect$onSetBlock(
            BlockPos pos, BlockState newState, int flags,
            CallbackInfoReturnable<Boolean> cir) {

        if (BlockChangeContext.IN_FIRE_TICK.get() && (Object) this instanceof ServerLevel sl) {
            BlockState oldState = sl.getBlockState(pos);
            if (!oldState.isAir() && newState.getBlock() instanceof BaseFireBlock) {
                PrismProtect.getDatabase().logBlock(new BlockLogEntry(
                        System.currentTimeMillis(),
                        "#fire",
                        sl.dimension().location().toString(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        BuiltInRegistries.BLOCK.getKey(oldState.getBlock()).toString(),
                        null,
                        BlockLogEntry.ACTION_BREAK
                ));
            }
        }
    }
}
