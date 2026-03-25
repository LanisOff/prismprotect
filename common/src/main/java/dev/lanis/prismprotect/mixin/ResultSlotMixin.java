package dev.lanis.prismprotect.mixin;

import dev.lanis.prismprotect.handler.ItemTracker;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow @Final private CraftingContainer craftSlots;

    @Inject(
            method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private void prismprotect$logCraft(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        long groupId = dev.lanis.prismprotect.handler.ItemProvenanceTracker.nextGroupId();
        List<dev.lanis.prismprotect.handler.ItemProvenanceTracker.ProvenanceStack> consumedProvenance = new ArrayList<>();

        Level level = player.level();
        NonNullList<ItemStack> remainingItems = level.getRecipeManager()
                .getRemainingItemsFor(RecipeType.CRAFTING, craftSlots, level);

        for (int slotIndex = 0; slotIndex < craftSlots.getContainerSize(); slotIndex++) {
            ItemStack ingredient = craftSlots.getItem(slotIndex);
            if (!ingredient.isEmpty()) {
                ItemStack consumed = ingredient.copy();
                consumed.setCount(1);
                List<dev.lanis.prismprotect.handler.ItemProvenanceTracker.ProvenanceStack> provenance =
                        dev.lanis.prismprotect.handler.ItemProvenanceTracker.consume(serverPlayer.getUUID(), consumed, 1);
                consumedProvenance.addAll(provenance);
                ItemTracker.logCraftIngredient(serverPlayer, consumed, groupId, provenance);
            }

            ItemStack remainder = remainingItems.get(slotIndex);
            if (!remainder.isEmpty()) {
                ItemTracker.logCraftRemainder(serverPlayer, remainder.copy(), groupId);
            }
        }

        ItemTracker.logCraftResult(serverPlayer, stack.copy(), groupId, !consumedProvenance.isEmpty());
        ItemTracker.trackCraftResult(serverPlayer, stack.copy(), consumedProvenance);
    }
}
