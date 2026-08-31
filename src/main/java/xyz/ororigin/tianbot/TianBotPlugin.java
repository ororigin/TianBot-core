package xyz.ororigin.tianbot;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.ororigin.tianbot.api.BotPropertyApi;
import xyz.ororigin.tianbot.api.TianBotApi;
import xyz.ororigin.tianbot.bot.BotManager;
import xyz.ororigin.tianbot.bot.BotNamePrefix;
import xyz.ororigin.tianbot.bot.GhostInfoListener;
import xyz.ororigin.tianbot.command.TainBotAdminCommand;
import xyz.ororigin.tianbot.data.BotPropertyRegistry;
import xyz.ororigin.tianbot.data.DatabaseManager;
import xyz.ororigin.tianbot.service.BotPropertyServiceImpl;
import xyz.ororigin.tianbot.service.TianBotServiceImpl;
import xyz.ororigin.tianbot.utils.AuthMeCompat;
import xyz.ororigin.tianbot.utils.Lang;
import xyz.ororigin.tianbot.utils.PreventKickingCompat;

import java.util.logging.Level;


public final class TianBotPlugin extends JavaPlugin {

    public static TianBotPlugin instance;
    private boolean folia;
    public boolean isLoaded = false;
    private static TianBotApi api;

    /** script 模式：单个 tick 内连续执行的即时动作上限（SequenceAction 分片阀门）。 */
    public static int scriptMaxBurst = 128;

    @Override
    public void onEnable() {
        instance = this;
        folia = detectFolia();
        if(!folia){
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        scriptMaxBurst = getConfig().getInt("script.max-burst-per-tick", 128);
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
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                TainBotAdminCommand.register(event.registrar()));
        BotPropertyServiceImpl botProperties = new BotPropertyServiceImpl();
        api = new TianBotServiceImpl(botProperties);
        getServer().getServicesManager().register(TianBotApi.class, api, this, ServicePriority.Normal);
        getServer().getServicesManager().register(BotPropertyApi.class, botProperties, this, ServicePriority.Normal);
        getLogger().info(Lang.get("log.api-registered"));
        isLoaded=true;
    }

    @Override
    public void onDisable() {
        if(isLoaded){
            BotManager.shutDown();
            DatabaseManager.shutdown();
            BotPropertyRegistry.getInstance().clear();
        }
        getServer().getServicesManager().unregisterAll(this);
        api = null;
        getLogger().info(Lang.get("log.plugin-disabled"));
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
