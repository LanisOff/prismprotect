package dev.lanis.prismprotect.fabric;

import dev.lanis.prismprotect.PrismProtect;
import net.fabricmc.api.ModInitializer;

public final class PrismProtectFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PrismProtect.init(null);
    }
}
