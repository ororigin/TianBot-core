package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;

/**
 * 移植 Carpet 假人的 JUMP 逻辑
 */
public class JumpAction extends PersistentAction {

    public enum Mode {
        ONCE,
        CONTINUOUS,
        INTERVAL
    }

    private final Mode mode;
    private final int interval;

    // 调度计数
    private int count;
    private int next;

    private JumpAction(Mode mode, int interval) {
        super(false, 0);
        this.mode = mode;
        this.interval = interval;
        this.next = interval;
    }

    public static JumpAction once() {
        return new JumpAction(Mode.ONCE, 1);
    }

    public static JumpAction continuous() {
        return new JumpAction(Mode.CONTINUOUS, 1);
    }

    public static JumpAction interval(int ticks) {
        return new JumpAction(Mode.INTERVAL, ticks);
    }

    @Override
    protected void onStart() {
        count = 0;
        next = interval;
    }

    @Override
    protected void onTick() {
        ServerPlayer player = player();
        if (player == null || player.isRemoved()) {
            finish(FinishReason.FAILED);
            return;
        }
        next--;
        if (next <= 0) {
            if (interval == 1 && mode != Mode.CONTINUOUS) {
                inactiveTick();
            }
            executeJump(player);
            count++;
            if (mode == Mode.ONCE) {
                finish(FinishReason.SUCCESS);
                return;
            }
            next = interval;
        } else {
            inactiveTick();
        }
    }

    @Override
    protected void onFinish(FinishReason reason) {
        inactiveTick();
    }

    /**
     * 镜像 Carpet JUMP.execute：
     * once → 落地跳跃 / 空中开鞘翅滑翔；continuous/interval → 按住跳跃键（setJumping(true)）。
     */
    private void executeJump(ServerPlayer player) {
        if (mode == Mode.ONCE) {
            if (player.onGround()) {
                player.jumpFromGround();
            } else if (!player.onClimbable()) {
                player.tryToStartFallFlying();
            }
        } else {
            player.setJumping(true);
        }
    }

    private void inactiveTick() {
        ServerPlayer player = player();
        if (player != null) {
            player.setJumping(false);
        }
    }
}
