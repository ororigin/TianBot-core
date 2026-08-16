package xyz.ororigin.tianbot.bot.action.module;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.ororigin.tianbot.bot.action.ActionResource;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;
import xyz.ororigin.tianbot.bot.action.util.BotTracer;

import java.util.Set;

/**
 * 移植 Carpet 假人的 USE 逻辑
 */
public class UseAction extends PersistentAction {

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

    // 右键使用冷却
    private int itemUseCooldown;

    private UseAction(Mode mode, int interval) {
        super(false, 0);
        this.mode = mode;
        this.interval = interval;
        this.next = interval;
    }

    public static UseAction once() {
        return new UseAction(Mode.ONCE, 1);
    }

    public static UseAction continuous() {
        return new UseAction(Mode.CONTINUOUS, 1);
    }

    public static UseAction interval(int ticks) {
        return new UseAction(Mode.INTERVAL, ticks);
    }

    @Override
    public Set<ActionResource> occupiedResources() {
        return Set.of(ActionResource.RIGHT_CLICK);
    }

    @Override
    protected void onStart() {
        count = 0;
        next = interval;
        itemUseCooldown = 0;
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
            executeUse();
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

    private HitResult getTarget(ServerPlayer player) {
        double reach = player.gameMode.isCreative() ? 5 : 4.5;
        return BotTracer.rayTrace(player, 1.0F, reach, false);
    }

    private boolean executeUse() {
        ServerPlayer player = player();
        if (player == null) {
            return false;
        }
        if (itemUseCooldown > 0) {
            itemUseCooldown--;
            return false;
        }
        if (player.isUsingItem()) {
            return true;
        }

        HitResult hit = getTarget(player);
        for (InteractionHand hand : InteractionHand.values()) {
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                player.resetLastActionTime();
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                Direction side = blockHit.getDirection();
                if (pos.getY() < player.level().getMaxY() - (side == Direction.UP ? 1 : 0)
                        && player.level().mayInteract(player, pos)) {
                    InteractionResult result = player.gameMode.useItemOn(
                            player, player.level(), player.getItemInHand(hand), hand, blockHit);
                    if (result instanceof InteractionResult.Success) {
                        player.swing(hand);
                        itemUseCooldown = 3;
                        return true;
                    }
                }
            } else if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                player.resetLastActionTime();
                EntityHitResult entityHit = (EntityHitResult) hit;
                Entity target = entityHit.getEntity();
                // Folia 防御：目标不在当前区域线程则跳过本次，避免跨线程操作实体
                if (!TickThread.isTickThreadFor(target)) {
                    return false;
                }
                boolean handWasEmpty = player.getItemInHand(hand).isEmpty();
                boolean itemFrameEmpty = target instanceof ItemFrame && ((ItemFrame) target).getItem().isEmpty();
                Vec3 relativeHitPos = entityHit.getLocation().subtract(target.getX(), target.getY(), target.getZ());
                if (target.interact(player, hand, relativeHitPos).consumesAction()) {
                    itemUseCooldown = 3;
                    return true;
                }
                if (player.interactOn(target, hand, relativeHitPos).consumesAction()
                        && !(handWasEmpty && itemFrameEmpty)) {
                    itemUseCooldown = 3;
                    return true;
                }
            }
            if (player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand).consumesAction()) {
                player.resetLastActionTime();
                itemUseCooldown = 3;
                return true;
            }
        }
        return false;
    }

    private void inactiveTick() {
        ServerPlayer player = player();
        if (player == null) {
            return;
        }
        itemUseCooldown = 0;
        player.releaseUsingItem();
    }
}
