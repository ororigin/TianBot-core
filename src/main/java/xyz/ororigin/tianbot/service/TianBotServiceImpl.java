package xyz.ororigin.tianbot.service;

import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.api.BotHandle;
import xyz.ororigin.tianbot.api.BotPropertyApi;
import xyz.ororigin.tianbot.api.TianBotApi;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.bot.BotConfig;
import xyz.ororigin.tianbot.bot.BotManager;
import xyz.ororigin.tianbot.data.BotProperties;
import xyz.ororigin.tianbot.data.DatabaseManager;
import xyz.ororigin.tianbot.data.IBotDatabase;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TianBotServiceImpl implements TianBotApi {

    private final BotPropertyApi botProperties;

    public TianBotServiceImpl(BotPropertyApi botProperties) {
        this.botProperties = botProperties;
    }

    @Override
    public BotPropertyApi properties() {
        return botProperties;
    }

    @Override
    public CompletableFuture<BotHandle> spawnBot(String name) {
        return spawnBot(name, "127.0.0.1", 25565, false);
    }

    @Override
    public CompletableFuture<BotHandle> spawnBot(String name, String host, int port, boolean ghost) {
        BotConfig config = new BotConfig();
        config.botName = name;
        config.address = host;
        config.port = port;
        config.isGhost = ghost;
        Bot bot = new Bot(config);
        UUID botUuid = bot.getUUID(name);
        BotManager.botMap.put(botUuid, bot);
        // 假人属性持久化（异步，不阻塞游戏线程）
        persist(botUuid, name);
        // spawnBot() 的 future 在区块 tick 线程完成，完成后提升为 BotHandle
        return bot.spawnBot().thenApply(unused -> bot);
    }

    @Override
    public CompletableFuture<Void> stopBot(String name) {
        Bot bot = BotManager.getBot(name).orElseThrow(
                () -> new IllegalStateException(Lang.t("error.bot-not-found", "player", name)));
        bot.logout();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stopBot(UUID uuid) {
        Bot bot = BotManager.getBot(uuid).orElseThrow(
                () -> new IllegalStateException(Lang.t("error.bot-not-found", "player", uuid)));
        bot.logout();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setVisible(String name, boolean visible) {
        Bot bot = BotManager.getBot(name).orElseThrow(
                () -> new IllegalStateException(Lang.t("error.bot-not-found", "player", name)));
        return bot.setVisible(visible);
    }

    @Override
    public CompletableFuture<Void> runScript(String name, String json) {
        Bot bot = BotManager.getBot(name).orElseThrow(
                () -> new IllegalStateException(Lang.t("error.bot-not-found", "player", name)));
        return bot.runScript(json);
    }

    @Override
    public CompletableFuture<Void> shutdownAll() {
        BotManager.shutDown();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Optional<BotHandle> getBot(String name) {
        return BotManager.getBot(name).map(bot -> bot);
    }

    @Override
    public Optional<BotHandle> getBot(UUID uuid) {
        return BotManager.getBot(uuid).map(bot -> bot);
    }

    @Override
    public Collection<BotHandle> getBots() {
        List<BotHandle> bots = BotManager.bots().stream()
                .map(bot -> (BotHandle) bot)
                .collect(Collectors.toList());
        return java.util.Collections.unmodifiableList(bots);
    }

    private void persist(UUID uuid, String name) {
        IBotDatabase db = DatabaseManager.getDatabase();
        if (db == null) {
            return;
        }
        db.create(new BotProperties(uuid, name)).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                TianBotPlugin.instance.getLogger().warning(
                        Lang.t("log.persist-failed", "reason", throwable.getMessage()));
            }
        });
    }
}
