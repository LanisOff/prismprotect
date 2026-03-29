package dev.lanis.prismprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class MessageUtil {

    private MessageUtil() {
    }

    public static Component success(String text) {
        return prefixed(text, ChatFormatting.GREEN);
    }

    public static Component error(String text) {
        return prefixed(text, ChatFormatting.RED);
    }

    public static Component warn(String text) {
        return prefixed(text, ChatFormatting.YELLOW);
    }

    public static Component info(String text) {
        return prefixed(text, ChatFormatting.AQUA);
    }

    public static Component header(String title) {
        MutableComponent line = Component.literal("------------------------------").withStyle(ChatFormatting.DARK_GRAY);
        MutableComponent mid = Component.literal(" " + title + " ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        return Component.empty()
                .append(line)
                .append(mid)
                .append(line.copy());
    }

    public static Component divider() {
        return Component.literal("------------------------------------------------------------")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component prefixed(String text, ChatFormatting color) {
        MutableComponent prefix = Component.literal("[PP] ").withStyle(ChatFormatting.DARK_GRAY);
        return prefix.append(Component.literal(text).withStyle(color));
    }
}
