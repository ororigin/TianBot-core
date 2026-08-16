package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;

/**
 * 移植 Carpet 假人的 DROP_ITEM / DROP_STACK 逻辑
 */
public class DropAction extends PersistentAction {

    public enum Mode {
        ONCE,
        CONTINUOUS,
        INTERVAL
    }

    public enum DropType {
        ITEM,
        STACK
    }

    private final DropType type;
    private final Mode mode;
    private final int interval;

    // 调度计数
    private int count;
    private int next;

    private DropAction(DropType type, Mode mode, int interval) {
        super(false, 0);
        this.type = type;
        this.mode = mode;
        this.interval = interval;
        this.next = interval;
    }

    public static DropAction item() {
        return new DropAction(DropType.ITEM, Mode.ONCE, 1);
    }

    public static DropAction itemContinuous() {
        return new DropAction(DropType.ITEM, Mode.CONTINUOUS, 1);
    }

    public static DropAction itemInterval(int ticks) {
        return new DropAction(DropType.ITEM, Mode.INTERVAL, ticks);
    }

    public static DropAction stack() {
        return new DropAction(DropType.STACK, Mode.ONCE, 1);
    }

    public static DropAction stackContinuous() {
        return new DropAction(DropType.STACK, Mode.CONTINUOUS, 1);
    }

    public static DropAction stackInterval(int ticks) {
        return new DropAction(DropType.STACK, Mode.INTERVAL, ticks);
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
            boolean dropped = executeDrop(player);
            count++;
            if (mode == Mode.ONCE) {
                finish(dropped ? FinishReason.SUCCESS : FinishReason.FAILED);
                return;
            }
            next = interval;
        }
    }

    private boolean executeDrop(ServerPlayer player) {
        player.resetLastActionTime();
        return player.drop(type == DropType.STACK);
    }
}
