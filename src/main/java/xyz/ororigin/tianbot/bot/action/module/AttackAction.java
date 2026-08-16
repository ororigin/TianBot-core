package xyz.ororigin.tianbot.bot.action.module;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import xyz.ororigin.tianbot.bot.action.ActionResource;
import xyz.ororigin.tianbot.bot.action.FinishReason;
import xyz.ororigin.tianbot.bot.action.PersistentAction;
import xyz.ororigin.tianbot.bot.action.util.BotTracer;

import java.util.Set;

/**
 * 移植 Carpet 假人的 ATTACK 逻辑
 */


public class AttackAction extends PersistentAction {

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

    // 方块挖掘状态机
    private BlockPos currentBlock;
    private int blockHitDelay;
    private float curBlockDamageMP;

    private AttackAction(Mode mode, int interval) {
        super(false, 0);
        this.mode = mode;
        this.interval = interval;
        this.next = interval;
    }

    public static AttackAction once() {
        return new AttackAction(Mode.ONCE, 1);
    }

    public static AttackAction continuous() {
        return new AttackAction(Mode.CONTINUOUS, 1);
    }

    public static AttackAction interval(int ticks) {
        return new AttackAction(Mode.INTERVAL, ticks);
    }

    @Override
    public Set<ActionResource> occupiedResources() {
        return Set.of(ActionResource.LEFT_CLICK);
    }

    @Override
    protected void onStart() {
        count = 0;
        next = interval;
        currentBlock = null;
        blockHitDelay = 0;
        curBlockDamageMP = 0;
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
            executeAttack();
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
        double reach = player.gameMode.isCreative() ? 5 : 4.5f;
        return BotTracer.rayTrace(player, 1.0F, reach, false);
    }

    private boolean executeAttack() {
        ServerPlayer player = player();
        if (player == null) {
            return false;
        }
        HitResult hit = getTarget(player);
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return false;
        }
        switch (hit.getType()) {
            case ENTITY: {
                EntityHitResult entityHit = (EntityHitResult) hit;
                Entity target = entityHit.getEntity();
                if (mode != Mode.CONTINUOUS) {
                    // Folia 防御：目标不在当前区域线程则跳过本次，避免跨线程操作实体
                    if (!TickThread.isTickThreadFor(target)) {
                        return false;
                    }
                    player.attack(target);
                    player.swing(InteractionHand.MAIN_HAND);
                }
                player.resetAttackStrengthTicker();
                player.resetLastActionTime();
                return true;
            }
            case BLOCK: {
                if (blockHitDelay > 0) {
                    blockHitDelay--;
                    return false;
                }
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                Direction side = blockHit.getDirection();
                if (player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer())) {
                    return false;
                }
                if (currentBlock != null && player.level().getBlockState(currentBlock).isAir()) {
                    currentBlock = null;
                    return false;
                }
                BlockState state = player.level().getBlockState(pos);
                boolean blockBroken = false;
                if (player.gameMode.getGameModeForPlayer().isCreative()) {
                    player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                    blockHitDelay = 5;
                    blockBroken = true;
                } else if (currentBlock == null || !currentBlock.equals(pos)) {
                    if (currentBlock != null) {
                        player.gameMode.handleBlockBreakAction(currentBlock, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                    }
                    player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                    boolean notAir = !state.isAir();
                    if (notAir && curBlockDamageMP == 0) {
                        state.attack(player.level(), pos, player);
                    }
                    if (notAir && state.getDestroyProgress(player, player.level(), pos) >= 1) {
                        currentBlock = null;
                        blockBroken = true;
                    } else {
                        currentBlock = pos;
                        curBlockDamageMP = 0;
                    }
                } else {
                    curBlockDamageMP += state.getDestroyProgress(player, player.level(), pos);
                    if (curBlockDamageMP >= 1) {
                        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                        currentBlock = null;
                        blockHitDelay = 5;
                        blockBroken = true;
                    }
                    player.level().destroyBlockProgress(-1, pos, (int) (curBlockDamageMP * 10));
                }
                player.resetLastActionTime();
                player.swing(InteractionHand.MAIN_HAND);
                return blockBroken;
            }
            default:
                return false;
        }
    }

    private void inactiveTick() {
        ServerPlayer player = player();
        if (player == null || currentBlock == null) {
            return;
        }
        player.level().destroyBlockProgress(-1, currentBlock, -1);
        player.gameMode.handleBlockBreakAction(currentBlock, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.DOWN, player.level().getMaxY(), -1);
        currentBlock = null;
    }
}
