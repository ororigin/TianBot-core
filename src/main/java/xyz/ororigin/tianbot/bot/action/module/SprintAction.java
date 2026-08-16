package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.ActionResource;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.ToggleAction;

import java.util.Set;
import java.util.function.Consumer;

//移植疾跑
public class SprintAction extends ToggleAction {

    private FinishReason result;

    private SprintAction() {
    }

    public static SprintAction once() {
        return new SprintAction();
    }

    @Override
    public Set<ActionResource> occupiedResources() {
        return Set.of(ActionResource.POSTURE);
    }

    public FinishReason result() {
        return result;
    }

    @Override
    public void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        FinishReason reason;
        try {
            if (player == null || player.isRemoved()) {
                reason = FinishReason.FAILED;
            } else {
                player.setSprinting(true);
                if (player.isShiftKeyDown()) {
                    player.setShiftKeyDown(false);
                }
                reason = FinishReason.SUCCESS;
            }
        } catch (Throwable t) {
            reason = FinishReason.FAILED;
        }
        this.result = reason;
        onFinished.accept(reason);
    }

    @Override
    public void release(ServerPlayer player, Consumer<FinishReason> onFinished) {
        UnSprintAction.once().exec(player, onFinished);
    }

    @Override
    public Class<? extends Action> undoActionClass() {
        return UnSprintAction.class;
    }
}
