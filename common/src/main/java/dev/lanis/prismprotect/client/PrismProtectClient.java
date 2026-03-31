package dev.lanis.prismprotect.client;

import dev.lanis.prismprotect.network.HighlightSyncPacket;

public final class PrismProtectClient {

    private static boolean initialized = false;

    private PrismProtectClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HighlightSyncPacket.registerClientReceiver();
    }
}
