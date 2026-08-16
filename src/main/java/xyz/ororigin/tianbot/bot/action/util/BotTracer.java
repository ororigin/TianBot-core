package xyz.ororigin.tianbot.bot.action.util;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 假人动作工具：视线射线追踪。
 * <p>
 * 移植自 fabric-carpet 的 {@code carpet.script.utils.Tracer}（26.x Mojang 映射），
 * 与 minecraft-fakeplayer 的 {@code Tracer} 同源（后者注释注明 "copy from fabric carpet"）。
 * <p>
 * 线程约束：本工具为纯读取操作，但访问了方块/实体数据，<b>必须在假人所在区域线程
 * （实体调度器线程）内调用</b>。动作模块的 {@code onTick} 已运行在该线程，可直接使用。
 */
public final class BotTracer {

    private BotTracer() {
    }

    /**
     * 综合射线：先打方块，再以方块命中距离为上限打实体；实体更近则返回实体，否则返回方块。
     * 未命中任何目标返回 {@code null}（等价于 {@link HitResult.Type#MISS}）。
     *
     * @param source       射线源（通常为假人 {@code ServerPlayer}）
     * @param partialTicks 插值系数，动作场景固定传 {@code 1.0F}
     * @param reach        射程（格）
     * @param fluids       是否检测流体
     */
    public static HitResult rayTrace(Entity source, float partialTicks, double reach, boolean fluids) {
        BlockHitResult blockHit = rayTraceBlocks(source, partialTicks, reach, fluids);
        double maxSqDist = reach * reach;
        if (blockHit != null) {
            maxSqDist = blockHit.getLocation().distanceToSqr(source.getEyePosition(partialTicks));
        }
        EntityHitResult entityHit = rayTraceEntities(source, partialTicks, reach, maxSqDist);
        return entityHit == null ? blockHit : entityHit;
    }

    /**
     * 纯方块射线：从眼睛位置沿视线方向投射线，检测方块轮廓（{@link ClipContext.Block#OUTLINE}，可选流体）。
     * 未命中返回 {@code null}。
     */
    public static BlockHitResult rayTraceBlocks(Entity source, float partialTicks, double reach, boolean fluids) {
        Vec3 pos = source.getEyePosition(partialTicks);
        Vec3 rotation = source.getViewVector(partialTicks);
        Vec3 reachEnd = pos.add(rotation.x * reach, rotation.y * reach, rotation.z * reach);
        return source.level().clip(new ClipContext(
                pos, reachEnd,
                ClipContext.Block.OUTLINE,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                source));
    }

    /**
     * 纯实体射线（上层）：沿视线方向生成搜索盒，返回最近的满足条件的可拾取实体。
     *
     * @param maxSqDist 最大距离平方（综合射线中传方块命中距离，避免隔墙选中实体）
     */
    public static EntityHitResult rayTraceEntities(Entity source, float partialTicks, double reach, double maxSqDist) {
        Vec3 pos = source.getEyePosition(partialTicks);
        Vec3 reachVec = source.getViewVector(partialTicks).scale(reach);
        AABB box = source.getBoundingBox().expandTowards(reachVec).inflate(1);
        return rayTraceEntities(source, pos, pos.add(reachVec), box,
                e -> !e.isSpectator() && e.isPickable(), maxSqDist);
    }

    /**
     * 纯实体射线（底层）：对搜索盒内每个实体做 AABB 与线段的求交，取最近命中。
     * 处理了「起点已在目标体内」（距离视为 0）与「同乘骑实体的平局」两种情况，
     * 语义与 carpet/fakeplayer 完全一致。
     *
     * @param box           搜索盒（由调用方构造，通常为 {@code source.getBoundingBox().expandTowards(dir).inflate(1)}）
     * @param maxSqDistance 最大距离平方；返回 {@code null} 表示未命中
     */
    public static EntityHitResult rayTraceEntities(Entity source, Vec3 start, Vec3 end, AABB box,
                                                   Predicate<Entity> predicate, double maxSqDistance) {
        Level world = source.level();
        double targetDistance = maxSqDistance;
        Entity target = null;
        Vec3 targetHitPos = null;
        // getEntities 会排除 source 自身
        for (Entity current : world.getEntities(source, box, predicate)) {
            AABB currentBox = current.getBoundingBox().inflate(current.getPickRadius());
            Optional<Vec3> currentHit = currentBox.clip(start, end);
            if (currentBox.contains(start)) {
                if (targetDistance >= 0) {
                    target = current;
                    targetHitPos = currentHit.orElse(start);
                    targetDistance = 0;
                }
            } else if (currentHit.isPresent()) {
                Vec3 currentHitPos = currentHit.get();
                double currentDistance = start.distanceToSqr(currentHitPos);
                if (currentDistance < targetDistance || targetDistance == 0) {
                    if (current.getRootVehicle() == source.getRootVehicle()) {
                        if (targetDistance == 0) {
                            target = current;
                            targetHitPos = currentHitPos;
                        }
                    } else {
                        target = current;
                        targetHitPos = currentHitPos;
                        targetDistance = currentDistance;
                    }
                }
            }
        }
        return target == null ? null : new EntityHitResult(target, targetHitPos);
    }
}
