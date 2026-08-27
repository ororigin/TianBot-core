package xyz.ororigin.tianbot.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xyz.ororigin.tianbot.TianBotPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;


public final class AuthMeCompat {

    private static final String AUTHME_PLUGIN_NAME = "AuthMe";
    private static final String AUTHME_API_CLASS = "fr.xephi.authme.api.v3.AuthMeApi";
    private static final String AUTHME_PLAYER_CACHE_CLASS = "fr.xephi.authme.data.auth.PlayerCache";
    private static final String AUTHME_PLAYER_AUTH_CLASS = "fr.xephi.authme.data.auth.PlayerAuth";
    private static final String AUTHME_LOGIN_EVENT_CLASS = "fr.xephi.authme.events.LoginEvent";
    private static Method getInstanceMethod;
    private static Method isRegisteredMethod;
    private static Method forceLoginMethod;
    private static Method forceRegisterMethod;
    private static Field playerCacheField;
    private static Method playerCacheIsAuthenticated;
    private static Method playerCacheUpdatePlayer;
    private static Method playerAuthBuilder;
    private static Method playerAuthBuilderName;
    private static Method playerAuthBuilderRealName;
    private static Method playerAuthBuilderBuild;
    private static Constructor<?> loginEventConstructor;
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
            } else if (autoRegister) {
                forceRegisterMethod.invoke(api, player, password, true);
                TianBotPlugin.instance.getLogger().info(Lang.t("log.authme-auto-registered", "player", name));
            } else {
                TianBotPlugin.instance.getLogger().warning(Lang.t("log.authme-not-registered", "player", name));
            }
            markAuthenticated(player);
            return true;
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
            probeSessionHooks();
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

    private static void probeSessionHooks() {
        try {
            Class<?> apiClass = Class.forName(AUTHME_API_CLASS);
            Class<?> playerCacheClass = Class.forName(AUTHME_PLAYER_CACHE_CLASS);
            Class<?> playerAuthClass = Class.forName(AUTHME_PLAYER_AUTH_CLASS);
            Class<?> builderClass = Class.forName(AUTHME_PLAYER_AUTH_CLASS + "$Builder");
            Class<?> loginEventClass = Class.forName(AUTHME_LOGIN_EVENT_CLASS);

            playerCacheField = apiClass.getDeclaredField("playerCache");
            playerCacheField.setAccessible(true);
            playerCacheIsAuthenticated = playerCacheClass.getMethod("isAuthenticated", String.class);
            playerCacheUpdatePlayer = playerCacheClass.getMethod("updatePlayer", playerAuthClass);
            playerAuthBuilder = playerAuthClass.getMethod("builder");
            playerAuthBuilderName = builderClass.getMethod("name", String.class);
            playerAuthBuilderRealName = builderClass.getMethod("realName", String.class);
            playerAuthBuilderBuild = builderClass.getMethod("build");
            loginEventConstructor = loginEventClass.getConstructor(Player.class);
        } catch (ReflectiveOperationException e) {
            TianBotPlugin.instance.getLogger().warning(Lang.t(
                    "log.authme-cache-probe-failed", "reason", e.toString()));
        }
    }

    private static void markAuthenticated(Player player) {
        if (!available || player == null || playerCacheField == null) {
            return;
        }
        try {
            Object api = getInstanceMethod.invoke(null);
            if (api == null) {
                return;
            }
            Object playerCache = playerCacheField.get(api);
            if (playerCache == null) {
                return;
            }
            String name = player.getName();
            boolean authenticated = (Boolean) playerCacheIsAuthenticated.invoke(playerCache, name);
            if (authenticated) {
                return;
            }
            Object builder = playerAuthBuilder.invoke(null);
            builder = playerAuthBuilderName.invoke(builder, name);
            builder = playerAuthBuilderRealName.invoke(builder, name);
            Object auth = playerAuthBuilderBuild.invoke(builder);
            playerCacheUpdatePlayer.invoke(playerCache, auth);
            TianBotPlugin.instance.getLogger().info(Lang.t("log.authme-cache-marked", "player", name));
            fireLoginEvent(player);
        } catch (ReflectiveOperationException e) {
            TianBotPlugin.instance.getLogger().log(
                    Level.WARNING,
                    Lang.t("log.authme-cache-mark-failed", "reason", e.toString()),
                    e);
        }
    }

    private static void fireLoginEvent(Player player) {
        if (loginEventConstructor == null) {
            return;
        }
        try {
            Object event = loginEventConstructor.newInstance(player);
            Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
        } catch (ReflectiveOperationException e) {
            TianBotPlugin.instance.getLogger().warning(Lang.t(
                    "log.authme-login-event-failed", "reason", e.toString()));
        }
    }
}
