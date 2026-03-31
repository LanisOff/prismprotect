package dev.lanis.prismprotect.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import dev.lanis.prismprotect.PrismProtect;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PrismProtectConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "prismprotect-client.json";

    private static Data values = new Data();

    private PrismProtectConfig() {
    }

    public static synchronized void load() {
        Path file = configPath();
        try {
            if (!Files.exists(file)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null) {
                    values = loaded;
                }
            }
            sanitize(values);
        } catch (Exception ex) {
            PrismProtect.LOGGER.error("Failed to load PrismProtect config", ex);
            values = new Data();
        }
    }

    public static synchronized void save() {
        Path file = configPath();
        try {
            Files.createDirectories(file.getParent());
            sanitize(values);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(values, writer);
            }
        } catch (Exception ex) {
            PrismProtect.LOGGER.error("Failed to save PrismProtect config", ex);
        }
    }

    public static synchronized Data snapshot() {
        Data copy = new Data();
        copy.highlightEnabled = values.highlightEnabled;
        copy.defaultHighlightDurationSeconds = values.defaultHighlightDurationSeconds;
        copy.maxHighlightDurationSeconds = values.maxHighlightDurationSeconds;
        copy.highlightPulseIntervalTicks = values.highlightPulseIntervalTicks;
        copy.highlightParticlesPerBlock = values.highlightParticlesPerBlock;
        copy.maxHighlightedBlocks = values.maxHighlightedBlocks;
        return copy;
    }

    public static synchronized void update(Data updated) {
        values = updated == null ? new Data() : updated;
        sanitize(values);
        save();
    }

    public static synchronized boolean isHighlightEnabled() {
        return values.highlightEnabled;
    }

    public static synchronized int defaultHighlightDurationSeconds() {
        return values.defaultHighlightDurationSeconds;
    }

    public static synchronized int maxHighlightDurationSeconds() {
        return values.maxHighlightDurationSeconds;
    }

    public static synchronized int highlightPulseIntervalTicks() {
        return values.highlightPulseIntervalTicks;
    }

    public static synchronized int highlightParticlesPerBlock() {
        return values.highlightParticlesPerBlock;
    }

    public static synchronized int maxHighlightedBlocks() {
        return values.maxHighlightedBlocks;
    }

    private static void sanitize(Data data) {
        data.defaultHighlightDurationSeconds = clamp(data.defaultHighlightDurationSeconds, 3, 180);
        data.maxHighlightDurationSeconds = clamp(data.maxHighlightDurationSeconds, 3, 600);
        if (data.defaultHighlightDurationSeconds > data.maxHighlightDurationSeconds) {
            data.defaultHighlightDurationSeconds = data.maxHighlightDurationSeconds;
        }
        data.highlightPulseIntervalTicks = clamp(data.highlightPulseIntervalTicks, 2, 20);
        data.highlightParticlesPerBlock = clamp(data.highlightParticlesPerBlock, 1, 20);
        data.maxHighlightedBlocks = clamp(data.maxHighlightedBlocks, 10, 512);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Path configPath() {
        return Platform.getConfigFolder().resolve(PrismProtect.MOD_ID).resolve(FILE_NAME);
    }

    public static final class Data {
        public boolean highlightEnabled = true;
        public int defaultHighlightDurationSeconds = 20;
        public int maxHighlightDurationSeconds = 180;
        public int highlightPulseIntervalTicks = 10;
        public int highlightParticlesPerBlock = 2;
        public int maxHighlightedBlocks = 64;
    }
}
