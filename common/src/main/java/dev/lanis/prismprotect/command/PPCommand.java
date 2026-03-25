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
import dev.lanis.prismprotect.handler.InspectManager;
import dev.lanis.prismprotect.rollback.RollbackManager;
import dev.lanis.prismprotect.util.MessageUtil;
import dev.lanis.prismprotect.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class PPCommand {

    private static final List<String> TIME_HINTS = List.of(
            "t:30m", "t:1h", "t:2h", "t:6h", "t:12h", "t:1d", "t:1w"
    );
    private static final List<String> RADIUS_HINTS = List.of("r:10", "r:25", "r:50", "r:100");
    private static final List<String> ACTIONS_LOOKUP = List.of("a:block", "a:entity", "a:container");
    private static final List<String> ACTIONS_ROLLBACK = List.of("a:entity");
    private static final List<String> ACTIONS_NONE = List.of();

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
        if (!actions.isEmpty() && !usedKeys.contains("a") && "a:".startsWith(token)) {
            tokenBuilder.suggest("a:");
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
        source.sendSuccess(() -> Component.literal("§b/pp inspect §7— toggle inspect mode (click blocks)"), false);
        source.sendSuccess(() -> Component.literal("§b/pp lookup §e[u:<p>] [t:<time>] [r:<r>] [a:block|entity|container]"), false);
        source.sendSuccess(() -> Component.literal("§b/pp rollback §e[u:<p>] [t:<time>] [r:<r>] [a:entity]"), false);
        source.sendSuccess(() -> Component.literal("§b/pp restore §e[u:<p>] [t:<time>] [r:<r>]"), false);
        source.sendSuccess(() -> Component.literal("§b/pp purge §et:<time>"), false);
        source.sendSuccess(() -> Component.literal("§b/pp status"), false);
        source.sendSuccess(() -> Component.literal("§7Time examples: §f30m §71h §f6h §71d §f1w"), false);
        source.sendSuccess(() -> Component.literal("§7Rollback: blocks §8+ §7containers by default, §oa:entity §r§7for mobs"), false);
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

        source.sendSuccess(() -> MessageUtil.header("Status"), false);
        source.sendSuccess(() -> Component.literal("§7Block records:     §f" + blocks), false);
        source.sendSuccess(() -> Component.literal("§7Entity records:    §f" + entities), false);
        source.sendSuccess(() -> Component.literal("§7Container records: §f" + containers), false);
        source.sendSuccess(() -> Component.literal("§7Storage: §fSQLite (WAL)"), false);
        source.sendSuccess(() -> Component.literal("§7Version: §f1.1 §7for §fArchitectury Fabric/Forge 1.20.1"), false);
        return 1;
    }

    private static int lookup(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        LookupParams params = parse(raw, source);

        if (raw.contains("a:entity")) {
            List<EntityLogEntry> entries = PrismProtect.getDatabase().lookupEntities(params);
            if (entries.isEmpty()) {
                source.sendSuccess(() -> MessageUtil.warn("No entity records found."), false);
                return 1;
            }

            source.sendSuccess(() -> MessageUtil.header("Entity Lookup (" + entries.size() + ")"), false);
            for (EntityLogEntry entry : entries) {
                String line = String.format(
                        "§7%s §f%s §7%s §e%s §7(%.0f,%.0f,%.0f)%s",
                        TimeUtil.elapsed(entry.time),
                        entry.player,
                        entry.actionName(),
                        entry.entityType,
                        entry.x,
                        entry.y,
                        entry.z,
                        entry.rolledBack ? " §8[rb]" : ""
                );
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        }

        if (raw.contains("a:container")) {
            List<ContainerLogEntry> entries = PrismProtect.getDatabase().lookupContainerByParams(params);
            if (entries.isEmpty()) {
                source.sendSuccess(() -> MessageUtil.warn("No container records found."), false);
                return 1;
            }

            source.sendSuccess(() -> MessageUtil.header("Container Lookup (" + entries.size() + ")"), false);
            for (ContainerLogEntry entry : entries) {
                String line = String.format(
                        "§7%s §f%s §7%s §ex%d §f%s §7(%d,%d,%d)%s",
                        TimeUtil.elapsed(entry.time),
                        entry.player,
                        entry.actionName(),
                        entry.amount,
                        shortId(entry.itemType),
                        entry.x,
                        entry.y,
                        entry.z,
                        entry.rolledBack ? " §8[rb]" : ""
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

        source.sendSuccess(() -> MessageUtil.header("Block Lookup (" + entries.size() + ")"), false);
        for (BlockLogEntry entry : entries) {
            String line = String.format(
                    "§7%s §f%s §7%s §e%s §7(%d,%d,%d)%s",
                    TimeUtil.elapsed(entry.time),
                    entry.player,
                    entry.actionName(),
                    entry.blockType,
                    entry.x,
                    entry.y,
                    entry.z,
                    entry.rolledBack ? " §8[rb]" : ""
            );
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return 1;
    }

    private static int rollback(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        if (raw.isBlank()) {
            source.sendFailure(MessageUtil.error("Usage: /pp rollback u:<player> t:<time> [r:<radius>] [a:entity]"));
            return 0;
        }

        LookupParams params = parse(raw, source);
        params.limit = ROLLBACK_QUERY_LIMIT;

        if (raw.contains("a:entity")) {
            source.sendSuccess(() -> MessageUtil.info("Rolling back entities..."), false);
            int rolledBack = RollbackManager.rollbackEntities(source.getServer(), params);
            source.sendSuccess(() -> MessageUtil.success("Rolled back §f" + rolledBack + "§a entities."), false);
            return 1;
        }

        source.sendSuccess(() -> MessageUtil.info("Rolling back blocks, containers and items..."), false);
        int blockCount = RollbackManager.rollback(source.getServer(), params);
        int containerCount = RollbackManager.rollbackContainers(source.getServer(), params);
        int itemCount = RollbackManager.rollbackItems(source.getServer(), params);

        source.sendSuccess(
                () -> MessageUtil.success(
                        "Rolled back §f" + blockCount + "§a block, §f" + containerCount + "§a container and §f" + itemCount + "§a item changes."
                ),
                false
        );
        return 1;
    }

    private static int restore(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        if (raw.isBlank()) {
            source.sendFailure(MessageUtil.error("Usage: /pp restore u:<player> t:<time> [r:<radius>]"));
            return 0;
        }

        LookupParams params = parse(raw, source);
        params.limit = ROLLBACK_QUERY_LIMIT;

        source.sendSuccess(() -> MessageUtil.info("Restoring blocks, containers and items..."), false);

        int blockCount = RollbackManager.restore(source.getServer(), params);
        int containerCount = RollbackManager.restoreContainers(source.getServer(), params);
        int itemCount = RollbackManager.restoreItems(source.getServer(), params);

        source.sendSuccess(
                () -> MessageUtil.success(
                        "Restored §f" + blockCount + "§a block, §f" + containerCount + "§a container and §f" + itemCount + "§a item changes."
                ),
                false
        );

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
        source.sendSuccess(
                () -> MessageUtil.success("Purged §f" + deleted + "§a records older than " + timeToken + "."),
                false
        );

        return 1;
    }

    private static LookupParams parse(String raw, CommandSourceStack source) {
        LookupParams params = new LookupParams();
        params.world = source.getLevel().dimension().location().toString();

        String player = param(raw, "u");
        if (player != null) {
            params.player = player;
        }

        String time = param(raw, "t");
        if (time != null) {
            long duration = TimeUtil.parseMs(time);
            if (duration > 0) {
                params.since = System.currentTimeMillis() - duration;
            }
        }

        String radius = param(raw, "r");
        if (radius != null) {
            try {
                int radiusValue = Integer.parseInt(radius);
                var pos = source.getPosition();

                params = LookupParams.ofRadius(
                        params.world,
                        (int) pos.x,
                        (int) pos.y,
                        (int) pos.z,
                        radiusValue
                );

                if (player != null) {
                    params.player = player;
                }

                if (time != null) {
                    long duration = TimeUtil.parseMs(time);
                    if (duration > 0) {
                        params.since = System.currentTimeMillis() - duration;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return params;
    }

    private static String param(String raw, String key) {
        int keyIndex = raw.indexOf(key + ":");
        if (keyIndex < 0) {
            return null;
        }

        int valueStart = keyIndex + key.length() + 1;
        int valueEnd = raw.indexOf(' ', valueStart);
        if (valueEnd < 0) {
            return raw.substring(valueStart);
        }

        return raw.substring(valueStart, valueEnd);
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

    @FunctionalInterface
    private interface ParamExecutor {
        int execute(CommandContext<CommandSourceStack> context, String raw);
    }
}
