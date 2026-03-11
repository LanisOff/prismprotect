package dev.lanis.prismprotect;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import dev.lanis.prismprotect.command.PPCommand;
import dev.lanis.prismprotect.database.DatabaseManager;
import dev.lanis.prismprotect.handler.CommonEventHandler;
import dev.lanis.prismprotect.platform.ContainerAccess;
import dev.lanis.prismprotect.platform.VanillaContainerAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrismProtect {

    public static final String MOD_ID = "prismprotect";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static DatabaseManager databaseManager;
    private static ContainerAccess containerAccess = new VanillaContainerAccess();

    private PrismProtect() {}

    public static void init(ContainerAccess platformContainerAccess) {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        if (platformContainerAccess != null) {
            containerAccess = platformContainerAccess;
        }

        initDatabase();
        CommonEventHandler.register();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                PPCommand.register(dispatcher));
        LifecycleEvent.SERVER_STOPPING.register(server -> closeDatabase());
    }

    public static DatabaseManager getDatabase() {
        return databaseManager;
    }

    public static ContainerAccess getContainerAccess() {
        return containerAccess;
    }

    private static void initDatabase() {
        Path configDir = Platform.getConfigFolder().resolve(MOD_ID);
        databaseManager = new DatabaseManager(configDir);
        databaseManager.initialize();
        LOGGER.info("PrismProtect database initialized.");
    }

    private static void closeDatabase() {
        if (databaseManager != null) {
            databaseManager.close();
            LOGGER.info("PrismProtect DB closed.");
        }
    }
}
