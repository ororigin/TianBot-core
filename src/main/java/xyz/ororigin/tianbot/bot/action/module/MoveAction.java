package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.action.ActionResource;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;

import java.util.Set;

/**
 * 移植 Carpet 假人的 move
 */
public class MoveAction extends PersistentAction {

    private final float forward;   // +1 前进 / -1 后退 / 0
    private final float strafing;  // +1 左移 / -1 右移 / 0
    private final boolean stop;    // stop()：一次性清空移动

    private MoveAction(float forward, float strafing, boolean stop) {
        super(false, 0); // 非阻塞：可与 attack/use 等其他类型持久动作并行
        this.forward = forward;
        this.strafing = strafing;
        this.stop = stop;
    }

    public static MoveAction forward() {
        return vector(1, 0);
    }

    public static MoveAction backward() {
        return vector(-1, 0);
    }

    public static MoveAction left() {
        return vector(0, 1);
    }

    public static MoveAction right() {
        return vector(0, -1);
    }

    /** 通用移动向量（forward/strafing 可同时非零 → 斜向移动） */
    public static MoveAction vector(float forward, float strafing) {
        return new MoveAction(forward, strafing, false);
    }

    /** 停止移动：一次性清空 zza/xxa（依赖同类型替换取消当前 move） */
    public static MoveAction stop() {
        return new MoveAction(0, 0, true);
    }

    @Override
    public Set<ActionResource> occupiedResources() {
        return Set.of(ActionResource.MOVE);
    }

    @Override
    protected void onStart() {
        if (stop) {
            // stop()：无需 tick，立即完成；移动输入已由被替换旧 move 的 onFinish 清零
            finish(FinishReason.SUCCESS);
            return;
        }
        applyMove();
    }

    @Override
    protected void onTick() {
        ServerPlayer player = player();
        if (player == null || player.isRemoved()) {
            finish(FinishReason.FAILED);
            return;
        }
        applyMove();
    }

    @Override
    protected void onFinish(FinishReason reason) {
        // 同类型替换/terminate/超时结束时重置输入，防止假人持续滑动
        ServerPlayer player = player();
        if (player != null) {
            player.zza = 0;
            player.xxa = 0;
        }
    }

    /**
     * 镜像 Carpet {@code EntityPlayerActionPack.onUpdate} 的移动写入：
     * 潜行时（shiftKeyDown）速度 ×0.3。
     */
    private void applyMove() {
        ServerPlayer player = player();
        if (player == null) {
            return;
        }
        float vel = player.isShiftKeyDown() ? 0.3F : 1.0F;
        player.zza = forward * vel;
        player.xxa = strafing * vel;
    }
}
