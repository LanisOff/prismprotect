package dev.lanis.prismprotect.mixin;

import dev.lanis.prismprotect.handler.BlockChangeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.level.block.FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void prismprotect$enterFireTick(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockChangeContext.IN_FIRE_TICK.set(true);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void prismprotect$exitFireTick(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockChangeContext.IN_FIRE_TICK.set(false);
    }
}
