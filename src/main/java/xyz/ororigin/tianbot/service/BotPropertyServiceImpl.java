package xyz.ororigin.tianbot.service;

import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.api.BotProperty;
import xyz.ororigin.tianbot.api.BotPropertyApi;
import xyz.ororigin.tianbot.data.BotPropertyRegistry;
import xyz.ororigin.tianbot.data.CustomPropertyCodec;
import xyz.ororigin.tianbot.data.DatabaseManager;
import xyz.ororigin.tianbot.data.IBotDatabase;
import xyz.ororigin.tianbot.utils.Lang;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BotPropertyServiceImpl implements BotPropertyApi {

    private final BotPropertyRegistry registry = BotPropertyRegistry.getInstance();

    // ==================== 注册 ====================

    @Override
    public void register(BotProperty property) {
        registry.register(property);
        if (TianBotPlugin.instance != null) {
            TianBotPlugin.instance.getLogger().info(Lang.t("log.bp-registered",
                    "key", property.key(), "type", property.type().getSimpleName()));
        }
    }

    @Override
    public void unregister(String key) {
        registry.unregister(key);
        if (TianBotPlugin.instance != null) {
            TianBotPlugin.instance.getLogger().info(Lang.t("log.bp-unregistered", "key", key));
        }
    }

    @Override
    public Optional<BotProperty> registered(String key) {
        return registry.registered(key);
    }

    @Override
    public Collection<BotProperty> registeredProperties() {
        return registry.registeredProperties();
    }

    // ==================== 读取 ====================

    @Override
    public CompletableFuture<Optional<Object>> getProperty(String name, String key) {
        return getProperty(uuidOf(name), key);
    }

    @Override
    public CompletableFuture<Optional<Object>> getProperty(UUID uuid, String key) {
        BotProperty property = requireRegistered(key);
        return getCustomValues(uuid).thenApply(values -> {
            String text = values.get(key);
            if (text != null) {
                return Optional.of(CustomPropertyCodec.deserialize(property.type(), text));
            }
            return property.defaultValue() != null
                    ? Optional.of(property.defaultValue())
                    : Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getProperties(String name) {
        return getProperties(uuidOf(name));
    }

    @Override
    public CompletableFuture<Map<String, Object>> getProperties(UUID uuid) {
        return getCustomValues(uuid).thenApply(values -> {
            Map<String, Object> result = new LinkedHashMap<>();
            List<BotProperty> sorted = new ArrayList<>(registry.registeredProperties());
            sorted.sort(Comparator.comparing(BotProperty::key));
            for (BotProperty p : sorted) {
                String text = values.get(p.key());
                if (text != null) {
                    result.put(p.key(), CustomPropertyCodec.deserialize(p.type(), text));
                } else if (p.defaultValue() != null) {
                    result.put(p.key(), p.defaultValue());
                }
            }
            return Collections.unmodifiableMap(result);
        });
    }

    // ==================== 写入 ====================

    @Override
    public CompletableFuture<Void> setProperty(String name, String key, Object value) {
        return setProperty(uuidOf(name), key, value);
    }

    @Override
    public CompletableFuture<Void> setProperty(UUID uuid, String key, Object value) {
        BotProperty property = requireRegistered(key);
        if (value == null) {
            return requireDb().deleteCustomProperty(uuid, key);
        }
        CustomPropertyCodec.validateValue(property.type(), value);
        return requireDb().setCustomProperty(uuid, key,
                CustomPropertyCodec.serialize(property.type(), value));
    }

    @Override
    public CompletableFuture<Void> setProperties(UUID uuid, Map<String, Object> values) {
        Map<String, String> serialized = new HashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            BotProperty property = requireRegistered(e.getKey());
            Object value = e.getValue();
            if (value == null) {
                // null 存储为 NULL，读取时回退缺省值
                serialized.put(property.key(), null);
                continue;
            }
            CustomPropertyCodec.validateValue(property.type(), value);
            serialized.put(property.key(), CustomPropertyCodec.serialize(property.type(), value));
        }
        return requireDb().setCustomProperties(uuid, serialized);
    }

    @Override
    public CompletableFuture<Void> deleteProperty(UUID uuid, String key) {
        requireRegistered(key);
        return requireDb().deleteCustomProperty(uuid, key);
    }

    @Override
    public CompletableFuture<Void> deleteProperty(String name, String key) {
        return deleteProperty(uuidOf(name), key);
    }

    // ==================== 内部 ====================

    private BotProperty requireRegistered(String key) {
        return registry.registered(key).orElseThrow(
                () -> new IllegalArgumentException(Lang.t("error.bp-not-registered", "key", key)));
    }

    /** 写入必须落库：数据库不可用时以异常终止，让外部插件感知写入失败。 */
    private IBotDatabase requireDb() {
        IBotDatabase db = DatabaseManager.getDatabase();
        if (db == null) {
            throw new IllegalStateException(Lang.get("error.db-unavailable"));
        }
        return db;
    }

    /** 读取在数据库不可用时回退为空（与插件"DB 失败不阻断"的既有约定一致）。 */
    private CompletableFuture<Map<String, String>> getCustomValues(UUID uuid) {
        IBotDatabase db = DatabaseManager.getDatabase();
        if (db == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        return db.getCustomProperties(uuid);
    }

    /** 假人 UUID 的 OfflinePlayer 规则（与 {@code Bot.getUUID} 一致，离线也可推导）。 */
    private static UUID uuidOf(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
