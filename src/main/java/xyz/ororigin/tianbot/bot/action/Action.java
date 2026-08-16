package xyz.ororigin.tianbot.bot.action;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.function.Consumer;

public abstract class Action {
    public abstract void exec(ServerPlayer player, Consumer<FinishReason> onFinished);

    public boolean isPersistent() {
        return false;
    }

    public boolean isBlocking() {
        return false;
    }

    public Set<ActionResource> occupiedResources() {
        return Set.of();
    }

    public Set<ActionResource> releasedResources() {
        return Set.of();
    }
}
