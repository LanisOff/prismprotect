package dev.lanis.prismprotect.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.lanis.prismprotect.PrismProtect;
import dev.lanis.prismprotect.database.BlockLogEntry;
import dev.lanis.prismprotect.database.ContainerLogEntry;
import dev.lanis.prismprotect.database.EntityLogEntry;
import dev.lanis.prismprotect.database.LookupParams;
import dev.lanis.prismprotect.config.PrismProtectConfig;
import dev.lanis.prismprotect.handler.ChangeHighlighter;
import dev.lanis.prismprotect.handler.InspectManager;
import dev.lanis.prismprotect.rollback.RollbackManager;
import dev.lanis.prismprotect.util.MessageUtil;
import dev.lanis.prismprotect.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class PPCommand {

    private static final List<String> TIME_HINTS = List.of(
            "t:30m", "t:1h", "t:2h", "t:6h", "t:12h", "t:1d", "t:1w"
    );
    private static final List<String> RADIUS_HINTS = List.of("r:10", "r:25", "r:50", "r:100");
    private static final List<String> DURATION_HINTS = List.of("d:10", "d:20", "d:30", "d:60");
    private static final List<String> LIMIT_HINTS = List.of("l:10", "l:25", "l:50", "l:100", "l:200");
    private static final List<String> PAGE_HINTS = List.of("p:1", "p:2", "p:3", "p:4");
    private static final List<String> WORLD_HINTS = List.of("w:minecraft:overworld", "w:minecraft:the_nether", "w:minecraft:the_end");
    private static final List<String> ACTIONS_LOOKUP = List.of("a:block", "a:entity", "a:container");
    private static final List<String> ACTIONS_ROLLBACK = List.of("a:entity");
    private static final List<String> ACTIONS_NONE = List.of();

    private static final int LOOKUP_LIMIT_DEFAULT = 50;
    private static final int LOOKUP_LIMIT_MAX = 500;
    private static final int ROLLBACK_QUERY_LIMIT = 100_000;

    private PPCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands
                .literal("pp")
                .requires(source -> source.hasPermission(2));

        root.executes(PPCommand::help);
        root.then(Commands.literal("help").executes(PPCommand::help));

        root.then(Commands.literal("inspect").executes(PPCommand::inspect));
        root.then(Commands.literal("i").executes(PPCommand::inspect));

        root.then(Commands.literal("status").executes(PPCommand::status));

        addParamCommand(root, "lookup", "l", ACTIONS_LOOKUP, PPCommand::lookup);
        addParamCommand(root, "highlight", "hl", ACTIONS_NONE, PPCommand::highlight);
        addParamCommand(root, "rollback", "rb", ACTIONS_ROLLBACK, PPCommand::rollback);
        addParamCommand(root, "restore", "rs", ACTIONS_NONE, PPCommand::restore);

        root.then(
                Commands.literal("purge")
                        .requires(source -> source.hasPermission(4))
                        .then(
                                Commands.argument("params", StringArgumentType.greedyString())
                                        .suggests((ctx, sb) -> suggestPurge(sb))
                                        .executes(ctx -> purge(ctx, StringArgumentType.getString(ctx, "params")))
                        )
        );

        dispatcher.register(root);
    }

    private static void addParamCommand(
            LiteralArgumentBuilder<CommandSourceStack> root,
            String literal,
            String alias,
            List<String> actions,
            ParamExecutor executor
    ) {
        root.then(buildParamCommand(literal, actions, executor));
        root.then(buildParamCommand(alias, actions, executor));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildParamCommand(
            String literal,
            List<String> actions,
            ParamExecutor executor
    ) {
        return Commands.literal(literal)
                .executes(ctx -> executor.execute(ctx, ""))
                .then(
                        Commands.argument("params", StringArgumentType.greedyString())
                                .suggests((ctx, sb) -> suggestParams(ctx, sb, actions))
                                .executes(ctx -> executor.execute(ctx, StringArgumentType.getString(ctx, "params")))
                );
    }

    private static CompletableFuture<Suggestions> suggestParams(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            List<String> actions
    ) {
        String remaining = builder.getRemaining();
        int lastSpaceIndex = remaining.lastIndexOf(' ');

        String token = lastSpaceIndex < 0 ? remaining : remaining.substring(lastSpaceIndex + 1);
        int tokenOffset = lastSpaceIndex < 0 ? 0 : lastSpaceIndex + 1;
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + tokenOffset);

        if (token.startsWith("u:")) {
            String prefix = token.substring(2).toLowerCase();
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                String name = player.getGameProfile().getName();
                if (name.toLowerCase().startsWith(prefix)) {
                    tokenBuilder.suggest("u:" + name);
                }
            }
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("t:")) {
            suggestMatching(tokenBuilder, token, TIME_HINTS);
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("r:")) {
            suggestMatching(tokenBuilder, token, RADIUS_HINTS);
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("d:")) {
            suggestMatching(tokenBuilder, token, DURATION_HINTS);
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("l:")) {
            suggestMatching(tokenBuilder, token, LIMIT_HINTS);
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("p:")) {
            suggestMatching(tokenBuilder, token, PAGE_HINTS);
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("w:")) {
            suggestMatching(tokenBuilder, token, WORLD_HINTS);
            return tokenBuilder.buildFuture();
        }
        if (token.startsWith("a:")) {
            suggestMatching(tokenBuilder, token, actions);
            return tokenBuilder.buildFuture();
        }

        Set<String> usedKeys = usedKeys(remaining);
        if (!usedKeys.contains("u") && "u:".startsWith(token)) {
            tokenBuilder.suggest("u:");
        }
        if (!usedKeys.contains("t") && "t:".startsWith(token)) {
            tokenBuilder.suggest("t:");
        }
        if (!usedKeys.contains("r") && "r:".startsWith(token)) {
            tokenBuilder.suggest("r:");
        }
        if (!usedKeys.contains("d") && "d:".startsWith(token)) {
            tokenBuilder.suggest("d:");
        }
        if (!usedKeys.contains("l") && "l:".startsWith(token)) {
            tokenBuilder.suggest("l:");
        }
        if (!usedKeys.contains("p") && "p:".startsWith(token)) {
            tokenBuilder.suggest("p:");
        }
        if (!usedKeys.contains("w") && "w:".startsWith(token)) {
            tokenBuilder.suggest("w:");
        }
        if (!actions.isEmpty() && !usedKeys.contains("a") && "a:".startsWith(token)) {
            tokenBuilder.suggest("a:");
        }
        if ("preview".startsWith(token)) {
            tokenBuilder.suggest("preview");
        }
        if ("off".startsWith(token)) {
            tokenBuilder.suggest("off");
        }

        return tokenBuilder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPurge(SuggestionsBuilder builder) {
        String token = builder.getRemaining();
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart());

        for (String hint : TIME_HINTS) {
            String suggestion = "t:" + hint.substring(2);
            if (suggestion.startsWith(token) || token.isEmpty()) {
                tokenBuilder.suggest(suggestion);
            }
        }

        return tokenBuilder.buildFuture();
    }

    private static void suggestMatching(SuggestionsBuilder builder, String token, List<String> values) {
        for (String value : values) {
            if (value.startsWith(token)) {
                builder.suggest(value);
            }
        }
    }

    private static Set<String> usedKeys(String remaining) {
        Set<String> keys = new HashSet<>();
        for (String token : remaining.split(" ")) {
            int colonIndex = token.indexOf(':');
            if (colonIndex > 0) {
                keys.add(token.substring(0, colonIndex));
            }
        }
        return keys;
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> MessageUtil.header("PrismProtect Help"), false);
        source.sendSuccess(() -> Component.literal("/pp inspect - toggle inspect mode"), false);
        source.sendSuccess(() -> Component.literal("/pp lookup [u:<p>] [t:<time>] [r:<radius>] [w:<world>] [a:block|entity|container] [l:<limit>] [p:<page>]"), false);
        source.sendSuccess(() -> Component.literal("/pp highlight [off] [u:<p>] [t:<time>] [r:<radius>] [w:<world>] [d:<sec>] [l:<limit>] [p:<page>]"), false);
        source.sendSuccess(() -> Component.literal("/pp rollback u:<player> t:<time> [r:<radius>] [w:<world>] [a:entity] [preview]"), false);
        source.sendSuccess(() -> Component.literal("/pp restore u:<player> t:<time> [r:<radius>] [w:<world>] [preview]"), false);
        source.sendSuccess(() -> Component.literal("/pp purge t:<time>"), false);
        source.sendSuccess(() -> Component.literal("/pp status"), false);
        source.sendSuccess(() -> Component.literal("Time examples: 30m 1h 6h 1d 1w"), false);
        source.sendSuccess(() -> MessageUtil.divider(), false);
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            context.getSource().sendFailure(MessageUtil.error("Only players can use inspect mode."));
            return 0;
        }

        boolean enabled = InspectManager.toggle(player.getUUID());
        player.sendSystemMessage(
                enabled
                        ? MessageUtil.success("Inspect mode ON. Click a block to view its history.")
                        : MessageUtil.warn("Inspect mode OFF.")
        );
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        long blocks = PrismProtect.getDatabase().blockLogCount();
        long entities = PrismProtect.getDatabase().entityLogCount();
        long containers = PrismProtect.getDatabase().containerLogCount();
        long items = PrismProtect.getDatabase().itemLogCount();
        long dbSizeBytes = PrismProtect.getDatabase().databaseFileSizeBytes();
        Path dbPath = PrismProtect.getDatabase().getDatabasePath();

        source.sendSuccess(() -> MessageUtil.header("Status"), false);
        source.sendSuccess(() -> Component.literal("Block records: " + blocks), false);
        source.sendSuccess(() -> Component.literal("Entity records: " + entities), false);
        source.sendSuccess(() -> Component.literal("Container records: " + containers), false);
        source.sendSuccess(() -> Component.literal("Item records: " + items), false);
        source.sendSuccess(() -> Component.literal("Active highlights: " + ChangeHighlighter.activeCount()), false);
        source.sendSuccess(() -> Component.literal(
                "Highlight config: enabled=" + PrismProtectConfig.isHighlightEnabled()
                        + ", default=" + PrismProtectConfig.defaultHighlightDurationSeconds() + "s"
                        + ", max=" + PrismProtectConfig.maxHighlightDurationSeconds() + "s"
                        + ", cap=" + PrismProtectConfig.maxHighlightedBlocks()
        ), false);
        source.sendSuccess(() -> Component.literal("Storage: SQLite (WAL), " + formatBytes(dbSizeBytes)), false);
        source.sendSuccess(() -> Component.literal("DB path: " + (dbPath == null ? "unknown" : dbPath)), false);
        source.sendSuccess(() -> Component.literal("Uptime: " + formatDuration(PrismProtect.uptimeMs())), false);
        source.sendSuccess(() -> Component.literal("Version: " + PrismProtect.VERSION + " (Architectury Fabric/Forge 1.20.1-1.20.4)"), false);
        return 1;
    }

    private static int lookup(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        LookupParams params = parse(raw, source, false);
        String action = param(raw, "a");

        if ("entity".equalsIgnoreCase(action)) {
            List<EntityLogEntry> entries = PrismProtect.getDatabase().lookupEntities(params);
            if (entries.isEmpty()) {
                source.sendSuccess(() -> MessageUtil.warn("No entity records found."), false);
                return 1;
            }

            source.sendSuccess(() -> MessageUtil.header("Entity Lookup (" + entries.size() + ", page " + params.page() + ")"), false);
            for (EntityLogEntry entry : entries) {
                String line = String.format(
                        "%s %s %s %s (%.0f,%.0f,%.0f)%s",
                        TimeUtil.elapsed(entry.time),
                        entry.player,
                        entry.actionName(),
                        entry.entityType,
                        entry.x,
                        entry.y,
                        entry.z,
                        entry.rolledBack ? " [rb]" : ""
                );
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        if ("container".equalsIgnoreCase(action)) {
            List<ContainerLogEntry> entries = PrismProtect.getDatabase().lookupContainerByParams(params);
            if (entries.isEmpty()) {
                source.sendSuccess(() -> MessageUtil.warn("No container records found."), false);
                return 1;
            }

            source.sendSuccess(() -> MessageUtil.header("Container Lookup (" + entries.size() + ", page " + params.page() + ")"), false);
            for (ContainerLogEntry entry : entries) {
                String line = String.format(
                        "%s %s %s x%d %s (%d,%d,%d)%s",
                        TimeUtil.elapsed(entry.time),
                        entry.player,
                        entry.actionName(),
                        entry.amount,
                        shortId(entry.itemType),
                        entry.x,
                        entry.y,
                        entry.z,
                        entry.rolledBack ? " [rb]" : ""
                );
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        List<BlockLogEntry> entries = PrismProtect.getDatabase().lookupBlocks(params);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.warn("No block records found."), false);
            return 1;
        }

        source.sendSuccess(() -> MessageUtil.header("Block Lookup (" + entries.size() + ", page " + params.page() + ")"), false);
        for (BlockLogEntry entry : entries) {
            String line = String.format(
                    "%s %s %s %s (%d,%d,%d)%s",
                    TimeUtil.elapsed(entry.time),
                    entry.player,
                    entry.actionName(),
                    entry.blockType,
                    entry.x,
                    entry.y,
                    entry.z,
                    entry.rolledBack ? " [rb]" : ""
            );
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return 1;
    }

    private static int rollback(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        if (raw.isBlank()) {
            source.sendFailure(MessageUtil.error("Usage: /pp rollback u:<player> t:<time> [r:<radius>] [w:<world>] [a:entity] [preview]"));
            return 0;
        }

        LookupParams params = parse(raw, source, true);
        boolean preview = hasFlag(raw, "preview") || hasFlag(raw, "dry-run");
        String action = param(raw, "a");

        if ("entity".equalsIgnoreCase(action)) {
            int attempted = PrismProtect.getDatabase().getEntitiesForRollback(params).size();
            if (preview) {
                source.sendSuccess(() -> MessageUtil.info("Preview: " + attempted + " entity records would be rolled back."), false);
                return 1;
            }
            int applied = RollbackManager.rollbackEntities(source.getServer(), params);
            int failed = Math.max(0, attempted - applied);
            source.sendSuccess(() -> MessageUtil.success("Entity rollback complete. Applied: " + applied + ", skipped/failed: " + failed), false);
            return 1;
        }

        int blockCandidates = PrismProtect.getDatabase().getForRollback(params).size();
        int containerCandidates = PrismProtect.getDatabase().getContainerForRollback(params).size();
        int itemCandidates = PrismProtect.getDatabase().getItemsForRollback(params).size();

        if (preview) {
            source.sendSuccess(() -> MessageUtil.info(
                    "Preview: blocks=" + blockCandidates
                            + ", containers=" + containerCandidates
                            + ", items=" + itemCandidates
            ), false);
            return 1;
        }

        source.sendSuccess(() -> MessageUtil.info("Rolling back blocks, containers and items..."), false);
        int blockApplied = RollbackManager.rollback(source.getServer(), params);
        int containerApplied = RollbackManager.rollbackContainers(source.getServer(), params);
        int itemApplied = RollbackManager.rollbackItems(source.getServer(), params);

        int blockFailed = Math.max(0, blockCandidates - blockApplied);
        int containerFailed = Math.max(0, containerCandidates - containerApplied);
        int itemFailed = Math.max(0, itemCandidates - itemApplied);

        source.sendSuccess(() -> MessageUtil.success(
                "Rollback complete. blocks " + blockApplied + "/" + blockCandidates
                        + ", containers " + containerApplied + "/" + containerCandidates
                        + ", items " + itemApplied + "/" + itemCandidates
                        + ". skipped/failed: " + (blockFailed + containerFailed + itemFailed)
        ), false);
        return 1;
    }

    private static int highlight(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            source.sendFailure(MessageUtil.error("Only players can run /pp highlight."));
            return 0;
        }

        if (hasWord(raw, "off")) {
            ChangeHighlighter.clear(player.getUUID(), player.serverLevel().dimension().location().toString(), player);
            source.sendSuccess(() -> MessageUtil.warn("Highlight OFF."), false);
            return 1;
        }

        if (!PrismProtectConfig.isHighlightEnabled()) {
            source.sendFailure(MessageUtil.error("Highlighter is disabled in PrismProtect config."));
            return 0;
        }

        LookupParams params = parse(raw, source, false);

        List<BlockLogEntry> entries = PrismProtect.getDatabase().lookupBlocks(params);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> MessageUtil.warn("No block records found for highlighting."), false);
            return 1;
        }

        int seconds = 0;
        String dToken = param(raw, "d");
        if (dToken != null) {
            int maxDuration = PrismProtectConfig.maxHighlightDurationSeconds();
            seconds = Math.max(3, Math.min(maxDuration, parsePositiveInt(dToken, PrismProtectConfig.defaultHighlightDurationSeconds())));
        }

        if (!player.serverLevel().dimension().location().toString().equals(params.world)) {
            source.sendFailure(MessageUtil.error("You must be in target world for highlight: " + params.world));
            return 0;
        }

        ChangeHighlighter.highlight(player, player.serverLevel(), entries, seconds);
        final int highlightSeconds = seconds;
        if (seconds <= 0) {
            source.sendSuccess(() -> MessageUtil.success("Highlight ON for " + entries.size() + " records. Use /pp highlight off to clear."), false);
        } else {
            source.sendSuccess(() -> MessageUtil.success("Highlighting " + entries.size() + " records for " + highlightSeconds + "s."), false);
        }
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        if (raw.isBlank()) {
            source.sendFailure(MessageUtil.error("Usage: /pp restore u:<player> t:<time> [r:<radius>] [w:<world>] [preview]"));
            return 0;
        }

        LookupParams params = parse(raw, source, true);
        boolean preview = hasFlag(raw, "preview") || hasFlag(raw, "dry-run");

        int blockCandidates = PrismProtect.getDatabase().getForRestore(params).size();
        int containerCandidates = PrismProtect.getDatabase().getContainerForRestore(params).size();
        int itemCandidates = PrismProtect.getDatabase().getItemsForRestore(params).size();

        if (preview) {
            source.sendSuccess(() -> MessageUtil.info(
                    "Preview: blocks=" + blockCandidates
                            + ", containers=" + containerCandidates
                            + ", items=" + itemCandidates
            ), false);
            return 1;
        }

        source.sendSuccess(() -> MessageUtil.info("Restoring blocks, containers and items..."), false);

        int blockApplied = RollbackManager.restore(source.getServer(), params);
        int containerApplied = RollbackManager.restoreContainers(source.getServer(), params);
        int itemApplied = RollbackManager.restoreItems(source.getServer(), params);

        int blockFailed = Math.max(0, blockCandidates - blockApplied);
        int containerFailed = Math.max(0, containerCandidates - containerApplied);
        int itemFailed = Math.max(0, itemCandidates - itemApplied);

        source.sendSuccess(() -> MessageUtil.success(
                "Restore complete. blocks " + blockApplied + "/" + blockCandidates
                        + ", containers " + containerApplied + "/" + containerCandidates
                        + ", items " + itemApplied + "/" + itemCandidates
                        + ". skipped/failed: " + (blockFailed + containerFailed + itemFailed)
        ), false);

        return 1;
    }

    private static int purge(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();

        String timeToken = param(raw, "t");
        if (timeToken == null) {
            source.sendFailure(MessageUtil.error("Usage: /pp purge t:<time>"));
            return 0;
        }

        long durationMs = TimeUtil.parseMs(timeToken);
        if (durationMs <= 0) {
            source.sendFailure(MessageUtil.error("Invalid time: " + timeToken));
            return 0;
        }

        int deleted = PrismProtect.getDatabase().purgeOlderThan(System.currentTimeMillis() - durationMs);
        source.sendSuccess(() -> MessageUtil.success("Purged " + deleted + " records older than " + timeToken + "."), false);
        return 1;
    }

    private static LookupParams parse(String raw, CommandSourceStack source, boolean rollbackMode) {
        LookupParams params = new LookupParams();
        Map<String, String> tokens = parseTokens(raw);

        params.world = tokens.getOrDefault("w", source.getLevel().dimension().location().toString());
        params.player = tokens.get("u");

        String time = tokens.get("t");
        if (time != null) {
            long duration = TimeUtil.parseMs(time);
            if (duration > 0) {
                params.since = System.currentTimeMillis() - duration;
            }
        }

        String radiusToken = tokens.get("r");
        if (radiusToken != null) {
            try {
                int radius = Integer.parseInt(radiusToken);
                var pos = source.getPosition();
                params.minX = (int) pos.x - radius;
                params.maxX = (int) pos.x + radius;
                params.minY = (int) pos.y - radius;
                params.maxY = (int) pos.y + radius;
                params.minZ = (int) pos.z - radius;
                params.maxZ = (int) pos.z + radius;
            } catch (NumberFormatException ignored) {
            }
        }

        if (rollbackMode) {
            params.limit = ROLLBACK_QUERY_LIMIT;
            params.offset = 0;
        } else {
            int requestedLimit = parsePositiveInt(tokens.get("l"), LOOKUP_LIMIT_DEFAULT);
            params.limit = Math.max(1, Math.min(LOOKUP_LIMIT_MAX, requestedLimit));
            int page = Math.max(1, parsePositiveInt(tokens.get("p"), 1));
            params.offset = (page - 1) * params.limit;
        }

        return params;
    }

    private static Map<String, String> parseTokens(String raw) {
        Map<String, String> tokens = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return tokens;
        }
        for (String token : raw.trim().split("\\s+")) {
            int colon = token.indexOf(':');
            if (colon <= 0 || colon >= token.length() - 1) {
                continue;
            }
            String key = token.substring(0, colon).toLowerCase();
            String value = token.substring(colon + 1);
            tokens.put(key, value);
        }
        return tokens;
    }

    private static int parsePositiveInt(String input, int fallback) {
        if (input == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean hasFlag(String raw, String flag) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String token : raw.split("\\s+")) {
            if (flag.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWord(String raw, String word) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String token : raw.split("\\s+")) {
            if (word.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static String param(String raw, String key) {
        Map<String, String> tokens = parseTokens(raw);
        return tokens.get(key);
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        try {
            return context.getSource().getPlayerOrException();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String shortId(String namespacedId) {
        int colonIndex = namespacedId.indexOf(':');
        if (colonIndex >= 0) {
            return namespacedId.substring(colonIndex + 1);
        }
        return namespacedId;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0D);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.1f MB", bytes / (1024.0D * 1024.0D));
        }
        return String.format("%.2f GB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }

    private static String formatDuration(long durationMs) {
        long seconds = durationMs / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        if (days > 0) {
            return days + "d " + (hours % 24L) + "h";
        }
        if (hours > 0) {
            return hours + "h " + (minutes % 60L) + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60L) + "s";
        }
        return seconds + "s";
    }

    @FunctionalInterface
    private interface ParamExecutor {
        int execute(CommandContext<CommandSourceStack> context, String raw);
    }
}
