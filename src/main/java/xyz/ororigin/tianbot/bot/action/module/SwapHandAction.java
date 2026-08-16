package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;

//移植交换左右手

public class SwapHandAction extends PersistentAction {

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

    private SwapHandAction(Mode mode, int interval) {
        super(false, 0);
        this.mode = mode;
        this.interval = interval;
        this.next = interval;
    }

    public static SwapHandAction once() {
        return new SwapHandAction(Mode.ONCE, 1);
    }

    public static SwapHandAction continuous() {
        return new SwapHandAction(Mode.CONTINUOUS, 1);
    }

    public static SwapHandAction interval(int ticks) {
        return new SwapHandAction(Mode.INTERVAL, ticks);
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
            executeSwap(player);
            count++;
            if (mode == Mode.ONCE) {
                finish(FinishReason.SUCCESS);
                return;
            }
            next = interval;
        }
    }

    private void executeSwap(ServerPlayer player) {
        player.resetLastActionTime();
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        player.setItemInHand(InteractionHand.OFF_HAND, player.getItemInHand(InteractionHand.MAIN_HAND));
        player.setItemInHand(InteractionHand.MAIN_HAND, offhand);
    }
}
