package xyz.ororigin.tianbot.data;

import xyz.ororigin.tianbot.api.BotProperty;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class BotPropertyRegistry {

    private static final BotPropertyRegistry INSTANCE = new BotPropertyRegistry();


    private static final Set<String> RESERVED_KEYS = Set.of("uuid", "bot_name", "name");

    private final Map<String, BotProperty> properties = new ConcurrentHashMap<>();

    private BotPropertyRegistry() {
    }

    public static BotPropertyRegistry getInstance() {
        return INSTANCE;
    }


    public void register(BotProperty property) {
        if (property == null) {
            throw new IllegalArgumentException(Lang.get("error.bp-key-blank"));
        }
        String key = property.key();
        if (RESERVED_KEYS.contains(key)) {
            throw new IllegalArgumentException(Lang.t("error.bp-key-reserved", "key", key));
        }
        if (properties.containsKey(key)) {
            throw new IllegalArgumentException(Lang.t("error.bp-key-duplicate", "key", key));
        }
        if (!SqliteTypeMapper.isSupportedType(property.type())) {
            throw new IllegalArgumentException(
                    Lang.t("error.bp-type-unsupported", "type", property.type().getName()));
        }
        properties.put(key, property);
    }


    public void unregister(String key) {
        properties.remove(key);
    }

    public Optional<BotProperty> registered(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    public Collection<BotProperty> registeredProperties() {
        return Collections.unmodifiableCollection(properties.values());
    }

    public boolean isRegistered(String key) {
        return properties.containsKey(key);
    }

    public boolean isReserved(String key) {
        return RESERVED_KEYS.contains(key);
    }


    public void clear() {
        properties.clear();
    }
}
