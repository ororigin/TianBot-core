package xyz.ororigin.tianbot.data;

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
}
