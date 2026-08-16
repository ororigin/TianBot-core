package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.ActionResource;
import xyz.ororigin.tianbot.bot.action.FinishReason;

import java.util.Set;
import java.util.function.Consumer;

//解除疾跑
public class UnSprintAction extends Action {

    private FinishReason result;

    private UnSprintAction() {
    }

    public static UnSprintAction once() {
        return new UnSprintAction();
    }

    @Override
    public Set<ActionResource> releasedResources() {
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
                player.setSprinting(false);
                reason = FinishReason.SUCCESS;
            }
        } catch (Throwable t) {
            reason = FinishReason.FAILED;
        }
        this.result = reason;
        onFinished.accept(reason);
    }
}
