package xyz.ororigin.tianbot.utils;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.bot.Bot;

import java.util.List;
import java.util.Locale;


public final class PreventKickingCompat {

    public enum Mode {
        NEVER,
        ON_SPAWNING,
        ALWAYS
    }

    private static Mode mode = Mode.ON_SPAWNING;
    private static int spawnProtectionTicks = 20;
    private static List<String> ignoreReasons = List.of(
            "failed to inject into a channel",
            "login timeout",
            "登录超时"
    );

    private PreventKickingCompat() {
    }

    public static void reset() {
        mode = Mode.ON_SPAWNING;
        spawnProtectionTicks = 20;
        ignoreReasons = List.of(
                "failed to inject into a channel",
                "login timeout",
                "登录超时"
        );
    }

    public static void init(TianBotPlugin plugin) {
        mode = readMode(plugin.getConfig().getString("prevent-kicking.mode", "ON_SPAWNING"));
        spawnProtectionTicks = Math.max(0, plugin.getConfig().getInt("prevent-kicking.spawn-protection-ticks", 20));
        ignoreReasons = plugin.getConfig().getStringList("prevent-kicking.ignore-reasons");
        if (ignoreReasons.isEmpty()) {
            ignoreReasons = List.of(
                    "failed to inject into a channel",
                    "login timeout",
                    "登录超时"
            );
        }
        plugin.getLogger().info(Lang.t(
                "log.kick-prevented-config",
                "mode", mode.name(),
                "ticks", spawnProtectionTicks
        ));
    }

    public static int getSpawnProtectionTicks() {
        return spawnProtectionTicks;
    }

    private static Mode readMode(String value) {
        if (value == null) {
            return Mode.ON_SPAWNING;
        }
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.ON_SPAWNING;
        }
    }

    public static boolean shouldPrevent(@Nullable Bot bot, @Nullable Component reason) {
        if (bot == null) {
            return false;
        }
        String text = reason == null ? "" : reason.getString();
        for (String keyword : ignoreReasons) {
            if (keyword != null && !keyword.isEmpty()
                    && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        switch (mode) {
            case ALWAYS:
                return true;
            case ON_SPAWNING:
                return bot.spawnedAtMillis >= 0
                        && System.currentTimeMillis() - bot.spawnedAtMillis < spawnProtectionTicks * 50L;
            case NEVER:
            default:
                return false;
        }
    }
}
