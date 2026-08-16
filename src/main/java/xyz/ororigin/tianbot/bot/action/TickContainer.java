package xyz.ororigin.tianbot.bot.action;

import java.util.UUID;

public record TickContainer(PersistentAction action, UUID id) {
    public void runTick() {
        action.tick();
    }
}
