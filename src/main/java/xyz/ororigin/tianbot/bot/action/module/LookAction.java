package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.util.BotLook;

import java.util.function.Consumer;

/**
 * 移植 Carpet 假人的 look/turn/lookAt
 */
public class LookAction extends Action {

    public enum Mode {
        DIRECTION,
        ROTATION,
        AT,
        TURN
    }

    private final Mode mode;
    private final Direction direction;
    private final float yaw;
    private final float pitch;
    private final Vec3 target;

    private FinishReason result;

    private LookAction(Mode mode, Direction direction, float yaw, float pitch, Vec3 target) {
        this.mode = mode;
        this.direction = direction;
        this.yaw = yaw;
        this.pitch = pitch;
        this.target = target;
    }

    public static LookAction north() {
        return direction(Direction.NORTH);
    }

    public static LookAction south() {
        return direction(Direction.SOUTH);
    }

    public static LookAction east() {
        return direction(Direction.EAST);
    }

    public static LookAction west() {
        return direction(Direction.WEST);
    }

    public static LookAction up() {
        return direction(Direction.UP);
    }

    public static LookAction down() {
        return direction(Direction.DOWN);
    }

    public static LookAction direction(Direction direction) {
        return new LookAction(Mode.DIRECTION, direction, 0, 0, null);
    }

    public static LookAction rotation(float yaw, float pitch) {
        return new LookAction(Mode.ROTATION, null, yaw, pitch, null);
    }

    public static LookAction lookAt(Vec3 target) {
        return new LookAction(Mode.AT, null, 0, 0, target);
    }

    public static LookAction turn(float deltaYaw, float deltaPitch) {
        return new LookAction(Mode.TURN, null, deltaYaw, deltaPitch, null);
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
                switch (mode) {
                    case DIRECTION -> applyDirection(player);
                    case ROTATION -> applyRotation(player, yaw, pitch);
                    case AT -> BotLook.lookAt(player, target);
                    case TURN -> applyRotation(player, player.getYRot() + yaw, player.getXRot() + pitch);
                }
                reason = FinishReason.SUCCESS;
            }
        } catch (Throwable t) {
            reason = FinishReason.FAILED;
        }
        this.result = reason;
        onFinished.accept(reason);
    }

    private void applyDirection(ServerPlayer player) {
        switch (direction) {
            case NORTH -> applyRotation(player, 180, 0);
            case SOUTH -> applyRotation(player, 0, 0);
            case EAST -> applyRotation(player, -90, 0);
            case WEST -> applyRotation(player, 90, 0);
            case UP -> applyRotation(player, player.getYRot(), -90);
            case DOWN -> applyRotation(player, player.getYRot(), 90);
        }
    }

    private void applyRotation(ServerPlayer player, float yaw, float pitch) {
        player.setYRot(yaw % 360);
        player.setXRot(Mth.clamp(pitch, -90, 90));
    }
}
