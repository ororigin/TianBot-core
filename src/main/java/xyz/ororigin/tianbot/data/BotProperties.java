package xyz.ororigin.tianbot.data;

import java.util.UUID;

public record BotProperties(
        UUID uuid,
        String botName
) {
}
