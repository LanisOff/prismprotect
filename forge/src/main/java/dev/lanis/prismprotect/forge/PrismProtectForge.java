package dev.lanis.prismprotect.forge;

import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.platform.forge.ForgeContainerAccess;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PrismProtect.MOD_ID)
public final class PrismProtectForge {

    public PrismProtectForge() {
        EventBuses.registerModEventBus(
                PrismProtect.MOD_ID,
                FMLJavaModLoadingContext.get().getModEventBus()
        );
        PrismProtect.init(new ForgeContainerAccess());
    }
}
