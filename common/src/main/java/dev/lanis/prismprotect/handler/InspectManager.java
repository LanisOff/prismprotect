package dev.lanis.prismprotect.handler;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InspectManager {

    private static final Set<UUID> active =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private InspectManager() {}

    public static boolean toggle(UUID id) {
        if (active.remove(id)) return false;
        active.add(id); return true;
    }

    public static boolean isInspecting(UUID id) { return active.contains(id); }
    public static void remove(UUID id)          { active.remove(id); }
}
