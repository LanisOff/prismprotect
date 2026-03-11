package dev.lanis.prismprotect.mixin;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.handler.BlockChangeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Final @Shadow private Level level;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void prismprotect$maybeLogExplosion(boolean causeFire, CallbackInfo ci) {
        if (BlockChangeContext.EXPLOSION_HANDLED.get()) return;
        if (!(level instanceof ServerLevel sl)) return;

        Explosion explosion = (Explosion) (Object) this;
        String source = resolveSource(explosion.getDamageSource());
        String world = sl.dimension().location().toString();
        long now = System.currentTimeMillis();

        for (BlockPos pos : explosion.getToBlow()) {
            BlockState state = sl.getBlockState(pos);
            if (state.isAir()) continue;

            String beData = null;
            BlockEntity be = sl.getBlockEntity(pos);
            if (be != null) {
                CompoundTag tag = be.saveWithoutMetadata();
                if (!tag.isEmpty()) beData = tag.toString();
            }

            PrismProtect.getDatabase().logBlock(new BlockLogEntry(
                    now, source, world,
                    pos.getX(), pos.getY(), pos.getZ(),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    beData, BlockLogEntry.ACTION_EXPLODE
            ));
        }
    }

    @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    private void prismprotect$clearFlag(boolean causeFire, CallbackInfo ci) {
        BlockChangeContext.EXPLOSION_HANDLED.set(false);
    }

    private static String resolveSource(DamageSource ds) {
        Entity indirect = ds.getEntity();
        if (indirect instanceof ServerPlayer sp)
            return sp.getGameProfile().getName();
        Entity direct = ds.getDirectEntity();
        if (direct != null)
            return "#" + BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
        return "#explosion";
    }
}
