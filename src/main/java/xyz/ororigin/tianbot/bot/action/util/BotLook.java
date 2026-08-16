package xyz.ororigin.tianbot.bot.action.util;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * 假人动作工具：朝向 / 旋转。
 * <p>
 * 对照 carpet {@code EntityPlayerActionPack} 的 {@code look} / {@code lookAt} 语义：
 * {@code lookAt} 委托原版 {@link Entity#lookAt} 计算 yaw/pitch 并同步头部偏航；
 * {@code yawPitchTo} 则复刻同一 atan2 公式但不改变假人状态（供「只算角度不转头」场景）。
 * <p>
 * 线程约束：读取/设置假人旋转角，<b>必须在假人所在区域线程内调用</b>。
 */
public final class BotLook {

    private BotLook() {
    }

    /**
     * 让假人看向目标坐标（以眼睛位置为基准），同步头部偏航。委托原版 {@code Entity.lookAt}。
     */
    public static void lookAt(ServerPlayer player, Vec3 target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    /**
     * 让假人看向目标实体（看向其眼睛位置）。
     */
    public static void lookAt(ServerPlayer player, Entity target) {
        lookAt(player, target.getEyePosition());
    }

    /**
     * 假人眼睛位置（插值系数 1.0F，即当前 tick 位置）。
     */
    public static Vec3 getEyePosition(Entity entity) {
        return entity.getEyePosition(1.0F);
    }

    /**
     * 假人视线方向单位向量（由当前 yaw/pitch 换算，{@code Entity.getViewVector}）。
     */
    public static Vec3 getLookVector(Entity entity) {
        return entity.getViewVector(1.0F);
    }

    /**
     * 计算从源实体眼睛到目标点的 yaw/pitch，<b>不改变假人状态</b>。
     * 复刻原版 {@code Entity.lookAt} 公式：
     * <pre>
     *   yaw   = atan2(dz, dx) * 180/PI - 90
     *   pitch = -atan2(dy, sqrt(dx^2 + dz^2)) * 180/PI
     * </pre>
     * 返回 {@link Vec2}{@code (yaw, pitch)}。
     */
    public static Vec2 yawPitchTo(Entity source, Vec3 target) {
        Vec3 from = source.getEyePosition();
        double xd = target.x - from.x;
        double yd = target.y - from.y;
        double zd = target.z - from.z;
        double horizontal = Math.sqrt(xd * xd + zd * zd);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(yd, horizontal) * (180F / Math.PI))));
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(zd, xd) * (180F / Math.PI)) - 90.0F);
        return new Vec2(yaw, pitch);
    }

    /**
     * 假人当前视线方向的最近朝向面（用于放置方块侧面 / 攻击方向判定）。
     */
    public static Direction getFacing(Entity entity) {
        return Direction.getApproximateNearest(entity.getViewVector(1.0F));
    }
}
