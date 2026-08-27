package xyz.ororigin.tianbot.api;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public interface BotPropertyApi {

    void register(BotProperty property);
    void unregister(String key);
    Optional<BotProperty> registered(String key);
    Collection<BotProperty> registeredProperties();
    CompletableFuture<Optional<Object>> getProperty(UUID uuid, String key);
    CompletableFuture<Optional<Object>> getProperty(String name, String key);
    CompletableFuture<Map<String, Object>> getProperties(UUID uuid);
    CompletableFuture<Map<String, Object>> getProperties(String name);
    CompletableFuture<Void> setProperty(UUID uuid, String key, @Nullable Object value);
    CompletableFuture<Void> setProperty(String name, String key, @Nullable Object value);
    CompletableFuture<Void> setProperties(UUID uuid, Map<String, Object> values);
    CompletableFuture<Void> deleteProperty(UUID uuid, String key);
    CompletableFuture<Void> deleteProperty(String name, String key);
    CompletableFuture<List<String>> findPlayersByProperty(String key, Object value);
}
