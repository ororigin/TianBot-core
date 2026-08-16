package xyz.ororigin.tianbot.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 假人插件对外服务接口（服务层）。
 *
 * <p>其他插件可通过 Bukkit 的 Services Manager 获取实现：
 * <pre>{@code
 * TianBotApi api = Bukkit.getServicesManager().load(TianBotApi.class);
 * api.spawnBot("bot1").thenAccept(bot -> bot.attack());
 * }</pre>
 * 插件自身也可通过 {@code TianBotPlugin.getApi()} 便捷获取。</p>
 *
 * <p>所有方法均为异步（Folia 区域线程安全），成功时正常完成，失败时以异常完成。</p>
 */
public interface TianBotApi {

    // ==================== 生命周期 ====================

    /**
     * 以默认配置（127.0.0.1:25565，可见）生成一个假人并等待其完成上线。
     *
     * @param name 假人名字
     * @return 完成时返回已上线的假人句柄
     */
    CompletableFuture<BotHandle> spawnBot(String name);

    /**
     * 以指定配置生成一个假人并等待其完成上线。
     *
     * @param name  假人名字
     * @param host  服务器域名（用于构造假人的网络连接）
     * @param port  服务器端口
     * @param ghost true 表示不可见（ghost，不进 PlayerList）
     */
    CompletableFuture<BotHandle> spawnBot(String name, String host, int port, boolean ghost);

    /**
     * 下线一个假人（触发 PlayerQuitEvent、存档、广播离开消息并清理登记）。
     *
     * @param name 假人名字
     */
    CompletableFuture<Void> stopBot(String name);

    /**
     * 下线一个假人。
     *
     * @param uuid 假人 UUID
     */
    CompletableFuture<Void> stopBot(UUID uuid);

    /**
     * 运行时切换假人可见状态。
     *
     * @param name    假人名字
     * @param visible true=可见（注册进 PlayerList）；false=ghost
     */
    CompletableFuture<Void> setVisible(String name, boolean visible);

    /** 下线所有假人（插件关闭时调用）。 */
    CompletableFuture<Void> shutdownAll();

    // ==================== 查询 ====================

    /** 按名字查找假人（不存在或已下线返回 {@link Optional#empty()}）。 */
    Optional<BotHandle> getBot(String name);

    /** 按 UUID 查找假人。 */
    Optional<BotHandle> getBot(UUID uuid);

    /** 返回当前在线假人集合（只读视图）。 */
    Collection<BotHandle> getBots();

    // ==================== 自定义属性 ====================

    /**
     * 获取假人自定义属性服务（注册 / 读写自定义属性并持久化到数据库）。
     * 外部插件也可直接通过 Services Manager 加载 {@link BotPropertyApi}。
     */
    BotPropertyApi properties();
}
