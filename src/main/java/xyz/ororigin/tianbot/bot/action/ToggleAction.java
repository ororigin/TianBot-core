package xyz.ororigin.tianbot.bot.action;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

public abstract class ToggleAction extends Action {
    public abstract void release(ServerPlayer player, Consumer<FinishReason> onFinished);
    public abstract Class<? extends Action> undoActionClass();
}
