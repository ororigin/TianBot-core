package xyz.ororigin.tianbot.bot.action;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.bot.action.module.WaitAction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

//为script模式预留的动作序列重放哨兵，用于实现循环

public class SequenceAction extends Action {

    public static final long INFINITE = -1L;

    private final ActionHandler handler;
    private final List<Action> steps;
    private final long remainingLoops;
    private final int maxBurst;

    public SequenceAction(ActionHandler handler, List<Action> steps, long loops) {
        this(handler, steps, loops, TianBotPlugin.scriptMaxBurst);
    }

    public SequenceAction(ActionHandler handler, List<Action> steps, long loops, int maxBurst) {
        if (loops == 0 || loops < INFINITE) {
            throw new IllegalArgumentException("loops must be >= 1 or -1 (infinite), got " + loops);
        }
        this.handler = handler;
        // 不可变副本：下一轮哨兵共享同一 steps 引用，避免调用方修改原始列表影响后续轮次
        this.steps = List.copyOf(steps);
        this.remainingLoops = loops;
        this.maxBurst = Math.max(1, maxBurst);
    }

    @Override
    public void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        List<Action> expanded = new ArrayList<>();
        boolean hasBlocking = false;
        int burst = 0;
        for (Action step : steps) {
            if (step.isBlocking()) {
                expanded.add(step);
                burst = 0;
                hasBlocking = true;
            } else {
                if (burst >= maxBurst) {
                    expanded.add(WaitAction.of(1));
                    hasBlocking = true;
                    burst = 0;
                }
                burst++;
                expanded.add(step);
            }
        }
        if (!hasBlocking) {
            expanded.add(WaitAction.of(1));
        }

        handler.enqueueActions(expanded);

        if (remainingLoops != 0) {
            long next = remainingLoops == INFINITE ? INFINITE : remainingLoops - 1;
            handler.enqueueActions(List.of(new SequenceAction(handler, steps, next, maxBurst)));
        }

        onFinished.accept(FinishReason.SUCCESS);
    }
}
