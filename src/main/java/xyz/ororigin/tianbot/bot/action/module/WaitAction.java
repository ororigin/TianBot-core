package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;


//为script模式准备的wait功能

public class WaitAction extends PersistentAction {

    private final int ticks;
    private int remaining;

    private WaitAction(int ticks) {
        super(true, 0); // block=true 阻塞队列；timeoutMs=0 不限超时
        this.ticks = ticks;
    }

    public static WaitAction of(int ticks) {
        return new WaitAction(Math.max(1, ticks));
    }

    @Override
    protected void onStart() {
        remaining = ticks;
    }

    @Override
    protected void onTick() {
        ServerPlayer player = player();
        if (player == null || player.isRemoved()) {
            finish(FinishReason.FAILED);
            return;
        }
        remaining--;
        if (remaining <= 0) {
            finish(FinishReason.SUCCESS);
        }
    }
}
