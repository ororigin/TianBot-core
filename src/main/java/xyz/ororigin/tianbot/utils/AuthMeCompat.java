package xyz.ororigin.tianbot.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xyz.ororigin.tianbot.TianBotPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;


public final class AuthMeCompat {

    private static final String AUTHME_PLUGIN_NAME = "AuthMe";
    private static final String AUTHME_API_CLASS = "fr.xephi.authme.api.v3.AuthMeApi";

    // 反射句柄（detect() 成功探测后缓存，避免每次上线都重新反射）
    private static Method getInstanceMethod;
    private static Method isRegisteredMethod;
    private static Method forceLoginMethod;
    private static Method forceRegisterMethod;
    private static boolean available;
    private static boolean enabled;
    private static boolean autoRegister;
    private static String password;

    private AuthMeCompat() {
    }


    public static void init(TianBotPlugin plugin) {
        enabled = plugin.getConfig().getBoolean("authme.enabled", true);
        autoRegister = plugin.getConfig().getBoolean("authme.auto-register", true);
        password = plugin.getConfig().getString("authme.password", "TianBot_AutoRegister");

        if (!enabled) {
            plugin.getLogger().info(Lang.get("log.authme-disabled"));
            return;
        }
        if (!detect()) {
            // 本插件 STARTUP 加载早于 AuthMe，启动完成后延迟再探测一次
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> detect(), 40L);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean forceSkipLogin(Player player) {
        if (!enabled || player == null) {
            return false;
        }
        if (!available && !detect()) {
            return false;
        }
        try {
            Object api = getInstanceMethod.invoke(null);
            if (api == null) {
                TianBotPlugin.instance.getLogger().warning(Lang.get("log.authme-api-null"));
                return false;
            }
            String name = player.getName();
            boolean registered = (Boolean) isRegisteredMethod.invoke(api, name);
            if (registered) {
                forceLoginMethod.invoke(api, player);
                TianBotPlugin.instance.getLogger().info(Lang.t("log.authme-force-login", "player", name));
                return true;
            }
            if (autoRegister) {
                forceRegisterMethod.invoke(api, player, password, true);
                TianBotPlugin.instance.getLogger().info(Lang.t("log.authme-auto-registered", "player", name));
                return true;
            }
            TianBotPlugin.instance.getLogger().warning(Lang.t("log.authme-not-registered", "player", name));
            return false;
        } catch (ReflectiveOperationException e) {
            TianBotPlugin.instance.getLogger().log(
                    Level.WARNING,
                    Lang.t("log.authme-reflect-failed", "reason", e.toString()),
                    e);
            return false;
        }
    }


    private static boolean detect() {
        if (available) {
            return true;
        }
        try {
            Plugin authMe = Bukkit.getPluginManager().getPlugin(AUTHME_PLUGIN_NAME);
            if (authMe == null || !authMe.isEnabled()) {
                return false;
            }
            Class<?> apiClass = Class.forName(AUTHME_API_CLASS);
            getInstanceMethod = apiClass.getMethod("getInstance");
            isRegisteredMethod = apiClass.getMethod("isRegistered", String.class);
            forceLoginMethod = apiClass.getMethod("forceLogin", Player.class);
            forceRegisterMethod = apiClass.getMethod("forceRegister", Player.class, String.class, boolean.class);
            available = true;
            TianBotPlugin.instance.getLogger().info(Lang.get("log.authme-compat-enabled"));
            return true;
        } catch (ReflectiveOperationException e) {
            TianBotPlugin.instance.getLogger().log(
                    Level.WARNING,
                    Lang.t("log.authme-detect-failed", "reason", e.toString()),
                    e);
            return false;
        }
    }
}
