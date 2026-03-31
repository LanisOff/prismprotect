package dev.lanis.prismprotect.handler;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.ExplosionEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.utils.value.IntValue;
import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.database.EntityLogEntry;
import dev.lanis.prismprotect.database.LookupParams;
import dev.lanis.prismprotect.util.MessageUtil;
import dev.lanis.prismprotect.util.TimeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CommonEventHandler {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CommonEventHandler() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        BlockEvent.BREAK.register(CommonEventHandler::onBreak);
        BlockEvent.PLACE.register(CommonEventHandler::onPlace);
        ExplosionEvent.DETONATE.register(CommonEventHandler::onExplosion);
        EntityEvent.LIVING_DEATH.register(CommonEventHandler::onEntityDeath);
        InteractionEvent.RIGHT_CLICK_BLOCK.register(CommonEventHandler::onRightClickBlock);
        PlayerEvent.OPEN_MENU.register(CommonEventHandler::onContainerOpen);
        PlayerEvent.CLOSE_MENU.register(CommonEventHandler::onContainerClose);
        PlayerEvent.PLAYER_QUIT.register(CommonEventHandler::onPlayerLogout);
    }

    private static EventResult onBreak(
            Level level,
            BlockPos pos,
            BlockState state,
            ServerPlayer player,
            IntValue xp
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return EventResult.pass();
        }

        if (InspectManager.isInspecting(player.getUUID())) {
            showInspect(player, serverLevel, pos, blockId(state));
            return EventResult.interruptFalse();
        }

        PrismProtect.getDatabase().logBlock(new BlockLogEntry(
                now(),
                player.getGameProfile().getName(),
                worldName(serverLevel),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                blockId(state),
                beData(serverLevel, pos),
                BlockLogEntry.ACTION_BREAK
        ));

        return EventResult.pass();
    }

    private static EventResult onPlace(Level level, BlockPos pos, BlockState state, Entity placer) {
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)) {
            return EventResult.pass();
        }

        PrismProtect.getDatabase().logBlock(new BlockLogEntry(
                now(),
                player.getGameProfile().getName(),
                worldName(serverLevel),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                blockId(state),
                null,
                BlockLogEntry.ACTION_PLACE
        ));

        return EventResult.pass();
    }

    private static void onExplosion(Level level, Explosion explosion, List<Entity> affectedEntities) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        String world = worldName(serverLevel);
        String source = resolveExplosionSource(explosion);
        long eventTime = now();

        for (BlockPos pos : explosion.getToBlow()) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            PrismProtect.getDatabase().logBlock(new BlockLogEntry(
                    eventTime,
                    source,
                    world,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    blockId(state),
                    beData(serverLevel, pos),
                    BlockLogEntry.ACTION_EXPLODE
            ));
        }

        BlockChangeContext.EXPLOSION_HANDLED.set(true);
    }

    private static EventResult onEntityDeath(LivingEntity target, DamageSource source) {
        if (target instanceof Player || !(target.level() instanceof ServerLevel serverLevel)) {
            return EventResult.pass();
        }

        ServerPlayer killer = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        CompoundTag serialized = new CompoundTag();
        target.save(serialized);

        PrismProtect.getDatabase().logEntity(new EntityLogEntry(
                now(),
                killer != null ? killer.getGameProfile().getName() : "#environment",
                worldName(serverLevel),
                target.getX(),
                target.getY(),
                target.getZ(),
                entityType,
                serialized.toString(),
                EntityLogEntry.ACTION_KILL
        ));

        return EventResult.pass();
    }

    private static EventResult onRightClickBlock(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel serverLevel)) {
            return EventResult.pass();
        }

        ContainerTracker.storeInteract(serverPlayer.getUUID(), pos, worldName(serverLevel));

        if (InspectManager.isInspecting(serverPlayer.getUUID())) {
            BlockState state = serverLevel.getBlockState(pos);
            showInspect(serverPlayer, serverLevel, pos, blockId(state));
            return EventResult.interruptFalse();
        }

        return EventResult.pass();
    }

    private static void onContainerOpen(Player player, AbstractContainerMenu menu) {
        if (player instanceof ServerPlayer serverPlayer) {
            ContainerTracker.onOpen(serverPlayer.getUUID(), menu);
        }
    }

    private static void onContainerClose(Player player, AbstractContainerMenu menu) {
        if (player instanceof ServerPlayer serverPlayer) {
            ContainerTracker.onClose(serverPlayer.getUUID(), menu, serverPlayer.getGameProfile().getName());
        }
    }

    private static void onPlayerLogout(ServerPlayer player) {
        ContainerTracker.clear(player.getUUID());
        InspectManager.remove(player.getUUID());
        ItemProvenanceTracker.clear(player.getUUID());
        ChangeHighlighter.clear(player.getUUID());
    }

    private static void showInspect(ServerPlayer player, Level level, BlockPos pos, String blockType) {
        LookupParams params = new LookupParams();
        params.world = worldName(level);
        params.minX = pos.getX();
        params.maxX = pos.getX();
        params.minY = pos.getY();
        params.maxY = pos.getY();
        params.minZ = pos.getZ();
        params.maxZ = pos.getZ();
        params.limit = 10;

        List<BlockLogEntry> entries = PrismProtect.getDatabase().lookupBlocks(params);

        player.sendSystemMessage(MessageUtil.header("Block Info"));
        player.sendSystemMessage(Component.literal(
                "§7Block: §e" + blockType + " §7(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"
        ));

        if (entries.isEmpty()) {
            player.sendSystemMessage(MessageUtil.warn("No history found for this block."));
            return;
        }

        for (BlockLogEntry entry : entries) {
            player.sendSystemMessage(Component.literal(String.format(
                    "§7%s §f%s §7%s §e%s%s",
                    TimeUtil.elapsed(entry.time),
                    entry.player,
                    entry.actionName(),
                    entry.blockType,
                    entry.rolledBack ? " §8[rb]" : ""
            )));
        }
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static String resolveExplosionSource(Explosion explosion) {
        DamageSource damageSource = explosion.getDamageSource();
        if (damageSource == null) {
            return "#explosion";
        }

        Entity indirect = damageSource.getEntity();
        if (indirect instanceof ServerPlayer player) {
            return player.getGameProfile().getName();
        }

        Entity direct = damageSource.getDirectEntity();
        if (direct != null) {
            return "#" + BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
        }

        return "#explosion";
    }

    private static String worldName(Level level) {
        return level.dimension().location().toString();
    }

    private static String beData(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        CompoundTag tag = blockEntity.saveWithoutMetadata();
        return tag.isEmpty() ? null : tag.toString();
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
