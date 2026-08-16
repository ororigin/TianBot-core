package xyz.ororigin.tianbot.bot;


import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotManager {

    public static Map<UUID,Bot> botMap = new ConcurrentHashMap<>();


    public static Optional<Bot> getBot(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return Optional.ofNullable(botMap.get(uuid));
    }

    public static Optional<Bot> getBot(UUID uuid) {
        return Optional.ofNullable(botMap.get(uuid));
    }

    public static Collection<Bot> bots() {
        return botMap.values();
    }

    public static void shutDown(){
        synchronized (botMap){
            for (UUID key:botMap.keySet()){
                botMap.get(key).logout();
            }
        }
    }
}
