package xyz.ororigin.tianbot.bot.action;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;


public class StopCurrentAction extends Action {

    private final ActionHandler handler;

    public StopCurrentAction(ActionHandler handler) {
        this.handler = handler;
    }

    @Override
    public void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        handler.doStopCurrent();
        onFinished.accept(FinishReason.SUCCESS);
    }
}
