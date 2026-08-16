package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.FinishReason;

import java.util.function.Consumer;

/**
 * 移植 carpet 假人的 dismount 逻辑
 */
public class DismountAction extends Action {

    private FinishReason result;

    private DismountAction() {
    }

    public static DismountAction once() {
        return new DismountAction();
    }

    public FinishReason result() {
        return result;
    }

    @Override
    public void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        FinishReason reason;
        try {
            boolean wasRiding = player != null && !player.isRemoved() && player.isPassenger();
            if (wasRiding) {
                player.stopRiding();
            }
            reason = wasRiding ? FinishReason.SUCCESS : FinishReason.FAILED;
        } catch (Throwable t) {
            reason = FinishReason.FAILED;
        }
        this.result = reason;
        onFinished.accept(reason);
    }
}
