package xyz.ororigin.tianbot.bot.action.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 假人动作工具：视线（Line of Sight）遮挡判断。
 * <p>
 * 线程约束：访问了方块/实体数据，<b>必须在假人所在区域线程内调用</b>。
 */
public final class BotLineOfSight {

    private BotLineOfSight() {
    }

    /**
     * 从 {@link LivingEntity} 眼睛到目标实体是否有视线。委托原版
     * {@link LivingEntity#hasLineOfSight(Entity)}（默认碰撞形状、不检测流体）。
     */
    public static boolean hasLineOfSight(LivingEntity source, Entity target) {
        return source.hasLineOfSight(target);
    }

    /**
     * 眼睛到目标点是否有视线（自定义方块/流体上下文）。
     * 从眼睛向目标点投射线，命中位置距离 {@code >=} 目标距离视为可见（目标被遮挡则命中距离更近）。
     */
    public static boolean isVisible(Entity source, Vec3 targetPos, ClipContext.Block blockCtx, ClipContext.Fluid fluidCtx) {
        Vec3 eye = source.getEyePosition();
        BlockHitResult hit = source.level().clip(new ClipContext(eye, targetPos, blockCtx, fluidCtx, source));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getLocation().distanceToSqr(eye) >= eye.distanceToSqr(targetPos);
    }

    /**
     * 眼睛到目标点是否有视线。默认使用碰撞形状（{@link ClipContext.Block#COLLIDER}）、不检测流体。
     */
    public static boolean isVisible(Entity source, Vec3 targetPos) {
        return isVisible(source, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
    }
}
