package xyz.ororigin.tianbot.bot;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xyz.ororigin.tianbot.command.BotNameArgumentType;

public final class BotNamePrefix {

    private static boolean initialized = false;
    private static String prefix = "";

    private BotNamePrefix() {
    }

    public static synchronized void init(JavaPlugin plugin) {
        String raw = plugin.getConfig().getString("bot.name-prefix");
        prefix = raw == null ? "" : raw;
        initialized = true;
    }

    /** 复位静态状态（onDisable 调用），避免热重载时旧 ClassLoader 的前缀值残留。 */
    public static synchronized void reset() {
        initialized = false;
        prefix = "";
    }

    @NotNull
    public static String prefix() {
        return prefix == null ? "" : prefix;
    }

    public static boolean enabled() {
        return initialized && prefix != null && !prefix.isEmpty();
    }

    @NotNull
    public static String getBotRealName(String displayName) {
        return prefix() + (displayName == null ? "" : displayName);
    }

    @NotNull
    public static String getDisplayName(String realName) {
        if (realName == null) {
            return "";
        }
        String p = prefix();
        if (enabled() && !p.isEmpty() && realName.startsWith(p)) {
            return realName.substring(p.length());
        }
        return realName;
    }

    public static int maxDisplayNameLength() {
        int prefixLen = prefix().length();
        return Math.max(1, BotNameArgumentType.MAX_NAME_LENGTH - prefixLen);
    }

    public static boolean isDisplayNameValid(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return false;
        }
        return getBotRealName(displayName).length() <= BotNameArgumentType.MAX_NAME_LENGTH;
    }
}
