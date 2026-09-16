package xyz.ororigin.tianbot;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.ororigin.tianbot.api.BotPropertyApi;
import xyz.ororigin.tianbot.api.TianBotApi;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.bot.BotManager;
import xyz.ororigin.tianbot.bot.BotNamePrefix;
import xyz.ororigin.tianbot.bot.GhostInfoListener;
import xyz.ororigin.tianbot.bot.fakes.FakeChannel;
import xyz.ororigin.tianbot.command.CommandRegistration;
import xyz.ororigin.tianbot.command.TainBotAdminCommand;
import xyz.ororigin.tianbot.data.BotPropertyRegistry;
import xyz.ororigin.tianbot.data.DatabaseManager;
import xyz.ororigin.tianbot.service.BotPropertyServiceImpl;
import xyz.ororigin.tianbot.service.TianBotServiceImpl;
import xyz.ororigin.tianbot.utils.AuthMeCompat;
import xyz.ororigin.tianbot.utils.Lang;
import xyz.ororigin.tianbot.utils.PreventKickingCompat;

import java.util.List;
import java.util.logging.Level;


public final class TianBotPlugin extends JavaPlugin {

    public static TianBotPlugin instance;
    private boolean folia;
    public boolean isLoaded = false;
    private static TianBotApi api;


    private final CommandRegistration commandRegistration = new CommandRegistration(this);

    private static volatile boolean shuttingDown = false;

    public static int scriptMaxBurst = 128;

    @Override
    public void onEnable() {
        shuttingDown = false;
        instance = this;
        folia = detectFolia();
        if(!folia){
            instance = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        scriptMaxBurst = getConfig().getInt("script.max-burst-per-tick", 128);
        Bot.setAutoSaveIntervalSeconds(getConfig().getLong("bot.auto-save-interval-seconds", 300L));
        BotNamePrefix.init(this);
        Lang.init(this);
        AuthMeCompat.init(this);
        PreventKickingCompat.init(this);
        getServer().getPluginManager().registerEvents(new GhostInfoListener(), this);
        try {
            DatabaseManager.init(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, Lang.get("log.db-init-failed"), e);
        }
        registerCommands();
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                registerCommands());
        BotPropertyServiceImpl botProperties = new BotPropertyServiceImpl();
        api = new TianBotServiceImpl(botProperties);
        getServer().getServicesManager().register(TianBotApi.class, api, this, ServicePriority.Normal);
        getServer().getServicesManager().register(BotPropertyApi.class, botProperties, this, ServicePriority.Normal);
        getLogger().info(Lang.get("log.api-registered"));
        isLoaded=true;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        commandRegistration.unregister();
        if(isLoaded){
            BotManager.saveGhostBots();
            BotManager.shutDown();
            BotManager.reset();
            DatabaseManager.shutdown();
            BotPropertyRegistry.reset();
        }
        FakeChannel.shutdownEventLoop();
        getServer().getServicesManager().unregisterAll(this);
        api = null;
        isLoaded = false;
        getLogger().info(Lang.get("log.plugin-disabled"));
        // 静态状态复位：不清理会让热重载后的新代码读到旧 ClassLoader 的配置与反射句柄
        Lang.reset();
        AuthMeCompat.reset();
        PreventKickingCompat.reset();
        BotNamePrefix.reset();
        scriptMaxBurst = 128;
        Bot.autoSaveIntervalNanos = Bot.DEFAULT_AUTO_SAVE_INTERVAL_NANOS;
        instance = null;
    }

    private void registerCommands() {
        if (commandRegistration.isRegistered()) {
            return;
        }
        if (commandRegistration.register(TainBotAdminCommand.build(),
                Lang.get("command.description"), List.of())) {
            getLogger().info(Lang.t("log.command-registered",
                    "command", TainBotAdminCommand.ROOT_LABEL));
        }
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }
    public static boolean isUsable() {
        TianBotPlugin plugin = instance;
        return plugin != null && plugin.isEnabled() && plugin.isLoaded;
    }

    /**
     * 获取假人服务层 API（插件自身便捷入口；其他插件推荐用 Services Manager 获取）。
     *
     * @return 服务层实例；插件未启用时为 {@code null}
     */
    public static TianBotApi getApi() {
        return api;
    }



    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServerInitEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
