package dev.lanis.prismprotect.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ItemProvenanceTracker {

    private static final Map<UUID, Map<String, Deque<ProvenanceStack>>> PLAYER_PROVENANCE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_GROUP_ID = new AtomicLong(1L);

    private ItemProvenanceTracker() {
    }

    public static long nextGroupId() {
        return NEXT_GROUP_ID.getAndIncrement();
    }

    public static void addFromSource(UUID playerId, String world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        add(playerId, stack, stack.getCount(), Set.of(new ProvenanceOrigin(world, pos.getX(), pos.getY(), pos.getZ())));
    }

    public static List<ProvenanceStack> consume(UUID playerId, ItemStack stack, int count) {
        List<ProvenanceStack> consumed = new ArrayList<>();
        if (stack.isEmpty() || count <= 0) {
            return consumed;
        }

        Map<String, Deque<ProvenanceStack>> byItem = PLAYER_PROVENANCE.get(playerId);
        if (byItem == null) {
            return consumed;
        }

        Deque<ProvenanceStack> queue = byItem.get(itemKey(stack));
        if (queue == null) {
            return consumed;
        }

        int remaining = count;
        while (remaining > 0 && !queue.isEmpty()) {
            ProvenanceStack head = queue.peekFirst();
            int taken = Math.min(remaining, head.count);
            consumed.add(new ProvenanceStack(new LinkedHashSet<>(head.origins), taken));
            remaining -= taken;
            head.count -= taken;
            if (head.count == 0) {
                queue.removeFirst();
            }
        }

        if (queue.isEmpty()) {
            byItem.remove(itemKey(stack));
        }
        if (byItem.isEmpty()) {
            PLAYER_PROVENANCE.remove(playerId);
        }

        return consumed;
    }

    public static void addCraftResult(UUID playerId, ItemStack stack, List<ProvenanceStack> consumed) {
        if (stack.isEmpty() || consumed.isEmpty()) {
            return;
        }

        Set<ProvenanceOrigin> combined = new LinkedHashSet<>();
        for (ProvenanceStack entry : consumed) {
            combined.addAll(entry.origins);
        }
        if (combined.isEmpty()) {
            return;
        }

        add(playerId, stack, stack.getCount(), combined);
    }

    public static String encode(List<ProvenanceStack> stacks) {
        if (stacks.isEmpty()) {
            return null;
        }

        LinkedHashSet<ProvenanceOrigin> combined = new LinkedHashSet<>();
        for (ProvenanceStack stack : stacks) {
            combined.addAll(stack.origins);
        }

        if (combined.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ProvenanceOrigin origin : combined) {
            if (!first) {
                sb.append(';');
            }
            sb.append(origin.world).append('|')
                    .append(origin.x).append('|')
                    .append(origin.y).append('|')
                    .append(origin.z);
            first = false;
        }
        return sb.toString();
    }

    public static boolean matchesArea(String encoded, String world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (encoded == null || encoded.isBlank() || world == null) {
            return false;
        }

        for (String part : encoded.split(";")) {
            String[] tokens = part.split("\\|", 4);
            if (tokens.length != 4) {
                continue;
            }

            try {
                if (!world.equals(tokens[0])) {
                    continue;
                }

                int x = Integer.parseInt(tokens[1]);
                int y = Integer.parseInt(tokens[2]);
                int z = Integer.parseInt(tokens[3]);
                if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return false;
    }

    private static void add(UUID playerId, ItemStack stack, int count, Set<ProvenanceOrigin> origins) {
        if (count <= 0 || origins.isEmpty()) {
            return;
        }

        PLAYER_PROVENANCE
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(itemKey(stack), ignored -> new ArrayDeque<>())
                .addLast(new ProvenanceStack(new LinkedHashSet<>(origins), count));
    }

    private static String itemKey(ItemStack stack) {
        String type = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompoundTag tag = stack.getTag();
        return type + (tag != null ? "|" + tag : "");
    }

    public static final class ProvenanceStack {
        public final Set<ProvenanceOrigin> origins;
        public int count;

        public ProvenanceStack(Set<ProvenanceOrigin> origins, int count) {
            this.origins = origins;
            this.count = count;
        }
    }

    public record ProvenanceOrigin(String world, int x, int y, int z) {
    }
}
