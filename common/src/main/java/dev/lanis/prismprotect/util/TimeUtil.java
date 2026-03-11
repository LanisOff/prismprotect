package dev.lanis.prismprotect.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern TOKEN = Pattern.compile("(\\d+(?:\\.\\d+)?)([smhdw])");

    private TimeUtil() {}

    public static long parseMs(String input) {
        Matcher m = TOKEN.matcher(input.toLowerCase());
        long total = 0;
        boolean found = false;
        while (m.find()) {
            found = true;
            double v = Double.parseDouble(m.group(1));
            total += switch (m.group(2)) {
                case "s" -> (long)(v * 1_000);
                case "m" -> (long)(v * 60_000);
                case "h" -> (long)(v * 3_600_000);
                case "d" -> (long)(v * 86_400_000);
                case "w" -> (long)(v * 604_800_000);
                default  -> 0L;
            };
        }
        if (!found) {
            try { return Long.parseLong(input) * 1_000L; }
            catch (NumberFormatException e) { return -1; }
        }
        return total;
    }

    public static String elapsed(long epochMs) {
        long d = Math.max(0, System.currentTimeMillis() - epochMs);
        long s = d / 1000, m = s / 60, h = m / 60, days = h / 24, weeks = days / 7;
        if (weeks > 0)  return weeks + "w " + (days % 7)  + "d ago";
        if (days  > 0)  return days  + "d " + (h % 24)    + "h ago";
        if (h     > 0)  return h     + "h " + (m % 60)    + "m ago";
        if (m     > 0)  return m     + "m " + (s % 60)    + "s ago";
        return s + "s ago";
    }

    public static String timestamp(long epochMs) {
        ZonedDateTime dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault());
        return String.format("%02d/%02d %02d:%02d:%02d",
                dt.getMonthValue(), dt.getDayOfMonth(),
                dt.getHour(), dt.getMinute(), dt.getSecond());
    }
}
