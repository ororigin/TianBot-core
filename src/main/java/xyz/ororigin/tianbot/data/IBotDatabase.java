package xyz.ororigin.tianbot.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public interface IBotDatabase {
    CompletableFuture<Void> create(BotProperties botProperties);
    CompletableFuture<Void> delete(UUID uuid);
    CompletableFuture<Optional<BotProperties>> get(UUID uuid);
    CompletableFuture<Void> modify(BotProperties botProperties);

    // ==================== 自定义属性（EAV 表） ====================

    /** 写入单个自定义属性（不存在则插入，存在则更新值）。value 为 null 时存储 SQL NULL。 */
    CompletableFuture<Void> setCustomProperty(UUID uuid, String key, String value);

    /** 批量写入多个自定义属性（同一连接/事务）。value 为 null 时存储 SQL NULL。 */
    CompletableFuture<Void> setCustomProperties(UUID uuid, Map<String, String> values);

    /** 读取某个假人的全部自定义属性（key → 序列化后的 TEXT 值）；无则返回空 Map。 */
    CompletableFuture<Map<String, String>> getCustomProperties(UUID uuid);

    /** 删除某个假人的单个自定义属性行。未写过的键为 no-op。 */
    CompletableFuture<Void> deleteCustomProperty(UUID uuid, String key);

    /** 删除某个假人的全部自定义属性行（随 bot 删除级联使用）。 */
    CompletableFuture<Void> deleteCustomProperties(UUID uuid);

    /**
     * 按自定义属性值反查所有持有该值的假人名字（含离线假人）。
     *
     * <p>底层经 {@code bot_custom_properties} 表按 {@code prop_key + prop_value} 精确匹配，
     * 再 JOIN 主表 {@code bot_properties} 取 {@code bot_name}。没有主表记录（从未持久化的）
     * 假人会因 JOIN 缺失而不在该结果中。</p>
     *
     * @param key   属性键
     * @param value 属性值（与写入时的序列化字符串一致）
     * @return 匹配的假人名字列表（只读，顺序不保证）
     */
    CompletableFuture<List<String>> findBotNamesByCustomProperty(String key, String value);
}
