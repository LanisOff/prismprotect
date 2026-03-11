package dev.lanis.prismprotect.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class MessageUtil {

    private static final String PRE = "§8[§b✦ PP§8] §r";

    private MessageUtil() {}

    public static Component raw(String text)     { return Component.literal(PRE + text); }
    public static Component success(String text) { return col(text, ChatFormatting.GREEN); }
    public static Component error(String text)   { return col(text, ChatFormatting.RED); }
    public static Component warn(String text)    { return col(text, ChatFormatting.YELLOW); }
    public static Component info(String text)    { return col(text, ChatFormatting.AQUA); }

    public static Component header(String title) {
        return Component.literal("§8§m───────§r §b✦ §f" + title + " §b✦§r §8§m───────");
    }

    public static Component divider() {
        return Component.literal("§8§m───────────────────────────§r");
    }

    private static Component col(String t, ChatFormatting f) {
        MutableComponent c = Component.literal(PRE);
        c.append(Component.literal(t).withStyle(f));
        return c;
    }
}
