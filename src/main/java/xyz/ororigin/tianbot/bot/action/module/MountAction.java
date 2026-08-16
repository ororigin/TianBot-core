package xyz.ororigin.tianbot.bot.action.module;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.ToggleAction;

import java.util.List;
import java.util.function.Consumer;

/**
 * 移植 carpet 假人的 mount 逻辑
 */
public class MountAction extends ToggleAction {

    public enum Mode {
        RIDEABLE,
        ANY
    }

    private final Mode mode;
    private FinishReason result;

    private MountAction(Mode mode) {
        this.mode = mode;
    }

    public static MountAction rideable() {
        return new MountAction(Mode.RIDEABLE);
    }

    public static MountAction any() {
        return new MountAction(Mode.ANY);
    }

    public FinishReason result() {
        return result;
    }

    @Override
    public void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        FinishReason reason;
        try {
            reason = mount(player) ? FinishReason.SUCCESS : FinishReason.FAILED;
        } catch (Throwable t) {
            reason = FinishReason.FAILED;
        }
        this.result = reason;
        onFinished.accept(reason);
    }

    private boolean mount(ServerPlayer player) {
        if (player == null || player.isRemoved()) {
            return false;
        }
        List<Entity> entities;
        if (mode == Mode.RIDEABLE) {
            entities = player.level().getEntities(player, player.getBoundingBox().inflate(3.0D, 1.0D, 3.0D),
                    e -> e instanceof Minecart || e instanceof Boat || e instanceof AbstractHorse);
        } else {
            entities = player.level().getEntities(player, player.getBoundingBox().inflate(3.0D, 1.0D, 3.0D));
        }
        if (entities.isEmpty()) {
            return false;
        }
        Entity closest = null;
        double distance = Double.POSITIVE_INFINITY;
        Entity currentVehicle = player.getVehicle();
        for (Entity e : entities) {
            if (e == player || (currentVehicle != null && currentVehicle == e)) {
                continue;
            }
            double dd = player.distanceToSqr(e);
            if (dd < distance) {
                distance = dd;
                closest = e;
            }
        }
        if (closest == null) {
            return false;
        }
        // Folia 防御：目标不在假人所在区域线程则跳过，避免跨线程操作实体
        if (!TickThread.isTickThreadFor(closest)) {
            return false;
        }
        if (closest instanceof AbstractHorse && mode == Mode.RIDEABLE) {
            return ((AbstractHorse) closest).mobInteract(player, InteractionHand.MAIN_HAND).consumesAction();
        }
        return player.startRiding(closest, true, true);
    }

    @Override
    public void release(ServerPlayer player, Consumer<FinishReason> onFinished) {
        DismountAction.once().exec(player, onFinished);
    }

    @Override
    public Class<? extends Action> undoActionClass() {
        return DismountAction.class;
    }
}
