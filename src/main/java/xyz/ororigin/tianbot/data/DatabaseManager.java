package xyz.ororigin.tianbot.data;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.ororigin.tianbot.utils.Lang;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {

    private static DatabaseManager instance;

    private final JavaPlugin plugin;
    private final Path dbPath;
    private final ExecutorService executor;
    private final SqliteBotDatabase database;

    private DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbPath = resolveDbPath(plugin);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TianBot-Sqlite");
            thread.setDaemon(true);
            return thread;
        });
        this.database = new SqliteBotDatabase(this.dbPath, this.executor);
    }

    /**
     * 初始化数据库模块并执行启动迁移（建表 / 动态补列）。
     *
     * @param plugin 插件实例（用于读取 config.yml 与日志）
     * @throws Exception 数据库不可用或迁移失败
     */
    public static synchronized void init(JavaPlugin plugin) throws Exception {
        if (instance != null) {
            throw new IllegalStateException(Lang.get("error.db-already-init"));
        }
        instance = new DatabaseManager(plugin);
        instance.plugin.getLogger().info(Lang.t("log.db-init", "path", instance.dbPath.toAbsolutePath()));
        SqliteSchemaMigrator.migrate(instance.dbPath, instance.plugin.getLogger());
        instance.plugin.getLogger().info(Lang.get("log.db-migrated"));
    }

    /**
     * 关闭线程池，释放资源。幂等，可重复调用。
     */
    public static synchronized void shutdown() {
        if (instance == null) {
            return;
        }
        instance.executor.shutdown();
        try {
            if (!instance.executor.awaitTermination(3, TimeUnit.SECONDS)) {
                instance.executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            instance.executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        instance = null;
    }

    /**
     * 获取数据库访问接口；未初始化或初始化失败时为 {@code null}。
     */
    public static IBotDatabase getDatabase() {
        return instance != null ? instance.database : null;
    }

    /**
     * 数据库模块是否可用。
     */
    public static boolean isReady() {
        return instance != null;
    }

    private static Path resolveDbPath(JavaPlugin plugin) {
        String fileName = plugin.getConfig().getString("database.file", "bots.db");
        return plugin.getDataFolder().toPath().resolve(fileName);
    }
}
