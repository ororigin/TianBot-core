package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.ActionResource;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.ToggleAction;

import java.util.Set;
import java.util.function.Consumer;

//移植 sneak

public class SneakAction extends ToggleAction {

    private FinishReason result;

    private SneakAction() {
    }

    public static SneakAction once() {
        return new SneakAction();
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
                player.setShiftKeyDown(true);
                if (player.isSprinting()) {
                    player.setSprinting(false);
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
        UnSneakAction.once().exec(player, onFinished);
    }

    @Override
    public Class<? extends Action> undoActionClass() {
        return UnSneakAction.class;
    }
}
