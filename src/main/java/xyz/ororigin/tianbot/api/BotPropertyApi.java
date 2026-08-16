package xyz.ororigin.tianbot.api;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 假人自定义属性服务接口（服务层）。
 *
 * <p>其他插件可通过 Bukkit 的 Services Manager 获取实现，也可通过
 * {@code TianBotApi.properties()} 获取：
 * <pre>{@code
 * BotPropertyApi props = Bukkit.getServicesManager().load(BotPropertyApi.class);
 * props.register(BotProperty.of("home_world", String.class, "overworld"));
 * props.setProperty("bot1", "home_world", "nether");
 * props.getProperty("bot1", "home_world");
 * }</pre>
 * </p>
 *
 * <p>属性值只支持 {@code SqliteTypeMapper} 支持的标量类型（String / UUID / byte / short /
 * int / long / boolean / float / double 及包装类）。读写均异步（Folia 区域线程安全），
 * 底层经 {@code TianBot-Sqlite} 单线程 executor 持久化到独立 EAV 表
 * {@code bot_custom_properties}。</p>
 *
 * <p>按名字读写时用 OfflinePlayer 规则推导假人 UUID，因此离线假人也同样可读可写。</p>
 */
public interface BotPropertyApi {

    // ==================== 注册 ====================

    /**
     * 注册一个新的假人自定义属性。
     *
     * <p>键重复、键为保留键（uuid / bot_name / name）、键格式非法、类型不受支持或默认值
     * 类型不匹配时抛 {@link IllegalArgumentException}（不注册任何东西）。</p>
     *
     * @param property 属性描述
     */
    void register(BotProperty property);

    /**
     * 注销一个已注册属性。已写入数据库的历史数据保留，只是不再受管、不再出现在
     * {@link #getProperties(UUID)} 的结果中。未注册的键为 no-op。
     */
    void unregister(String key);

    /** 查询某键是否已注册。 */
    Optional<BotProperty> registered(String key);

    /** 返回当前已注册的全部属性（只读视图，顺序不保证）。 */
    Collection<BotProperty> registeredProperties();

    // ==================== 读取 ====================

    /**
     * 读取某个假人的单个属性值。
     *
     * @return 命中已写值返回该值；未写过且注册时有缺省值返回缺省值；否则 {@link Optional#empty()}
     * @throws IllegalArgumentException 键未注册
     */
    CompletableFuture<Optional<Object>> getProperty(UUID uuid, String key);

    /** 按假人名字读取单个属性值（名字按 OfflinePlayer 规则推导 UUID）。 */
    CompletableFuture<Optional<Object>> getProperty(String name, String key);

    /**
     * 读取某个假人的全部已注册属性值（未写过的键回退缺省值；无缺省值则不在结果中）。
     * 返回只读 Map，按键排序。
     */
    CompletableFuture<Map<String, Object>> getProperties(UUID uuid);

    /** 按假人名字读取全部已注册属性值。 */
    CompletableFuture<Map<String, Object>> getProperties(String name);

    // ==================== 写入 ====================

    /**
     * 写入某个假人的单个属性值。
     *
     * @param value 属性值，类型须与注册时一致；为 {@code null} 表示删除该属性的已存值
     * @throws IllegalArgumentException 键未注册或值类型不匹配
     */
    CompletableFuture<Void> setProperty(UUID uuid, String key, @Nullable Object value);

    /** 按假人名字写入单个属性值。 */
    CompletableFuture<Void> setProperty(String name, String key, @Nullable Object value);

    /**
     * 批量写入某个假人的多个属性值。值类型须与各注册类型一致；值为 {@code null} 的属性将
     * 存储为 NULL（读取时回退缺省值），如需物理删除请用 {@link #deleteProperty(UUID, String)}。
     */
    CompletableFuture<Void> setProperties(UUID uuid, Map<String, Object> values);

    /** 物理删除某个假人的单个属性行。未写过的键为 no-op。 */
    CompletableFuture<Void> deleteProperty(UUID uuid, String key);

    /** 按假人名字物理删除单个属性行。 */
    CompletableFuture<Void> deleteProperty(String name, String key);
}
