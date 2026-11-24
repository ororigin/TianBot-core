package xyz.ororigin.tianbot.bot.action.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 手动复刻原版玩家攻击判定，用于在假人持剑时主动触发横扫攻击。
 *
 * <p>在旧版 Lophine 核心下，原版 {@code Player.attack()} 内的 {@code isSweepAttack} 判定对假人
 * 不成立（观察表现为：空手攻击动画 + 满蓄力重击音效 + 无横扫）。这里不再依赖原版判定路径，
 * 而是由 TianBot 用公共 API 重新读取假人状态、严格复刻原版横扫判定；满足条件时反射调用
 * private {@code doSweepAttack} 触发横扫伤害 / 音效 / 粒子。
 *
 * <p>仅用于"主手持剑"的实体攻击路径，其余攻击仍走原版 {@code player.attack()}。
 */
public final class ManualAttackHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualAttackHelper.class);

    /** private void Player.doSweepAttack(Entity, float, DamageSource, float) */
    private static final Method SWEEP_ATTACK_METHOD = findMethod(Player.class, "doSweepAttack",
            Entity.class, float.class, DamageSource.class, float.class);
    /** protected float LivingEntity.getKnockback(Entity, DamageSource) */
    private static final Method KNOCKBACK_METHOD = findMethod(LivingEntity.class, "getKnockback",
            Entity.class, DamageSource.class);
    /** protected float Player.getEnchantedDamage(Entity, float, DamageSource) */
    private static final Method ENCHANTED_DAMAGE_METHOD = findMethod(Player.class, "getEnchantedDamage",
            Entity.class, float.class, DamageSource.class);

    private ManualAttackHelper() {
    }

    /**
     * 假人当前主手是否持剑（决定是否走手动攻击路径）。
     */
    public static boolean isHoldingSword(ServerPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS);
    }

    /**
     * 手动执行一次持剑攻击：复刻原版 {@code Player.attack()} 的核心流程，并在满足原版横扫判定时
     * 主动触发横扫。
     *
     * @return 是否命中了主目标（与 {@code executeAttack} 返回语义一致）
     */
    public static boolean perform(ServerPlayer player, Entity target) {
        ItemStack weapon = player.getWeaponItem();
        if (weapon.isEmpty() || !weapon.is(ItemTags.SWORDS)) {
            // 防御：非剑不应走到这里
            return false;
        }
        if (!(player.level() instanceof ServerLevel)) {
            return false;
        }

        // ---- 原版 Player.attack() 的攻击参数计算 ----
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // Folia 26.2：getDamageSource(LivingEntity) 单参数
        DamageSource damageSource = weapon.getDamageSource(player);
        float attackStrengthScale = player.getAttackStrengthScale(0.5F);
        float magicBoost = attackStrengthScale
                * (enchantedDamage(player, target, baseDamage, damageSource) - baseDamage);
        baseDamage *= baseDamageScaleFactor(attackStrengthScale);
        player.onAttack(target);

        // ---- 原版 Player.attack() 的判定 ----
        boolean fullStrengthAttack = attackStrengthScale > 0.9F;
        boolean knockbackAttack = player.isSprinting() && fullStrengthAttack;
        baseDamage += weapon.getItem().getAttackDamageBonus(target, baseDamage, damageSource);
        boolean criticalAttack = fullStrengthAttack && canCriticalAttack(player, target);
        if (criticalAttack) {
            // Folia 26.2：暴击时给伤害源打 critical 标记
            damageSource = damageSource.critical();
            baseDamage *= 1.5F;
        }

        // ---- 横扫判定（严格复刻原版 Player.isSweepAttack） ----
        boolean sweepAttack = isSweepAttack(player, fullStrengthAttack, criticalAttack, knockbackAttack, weapon);

        float totalDamage = baseDamage + magicBoost;
        Vec3 oldMovement = target.getDeltaMovement();

        boolean wasHurt = target.hurtOrSimulate(damageSource, totalDamage);
        if (wasHurt) {
            float knockback = getKnockback(player, target, damageSource) + (knockbackAttack ? 0.5F : 0.0F);
            // Folia 26.2：causeExtraKnockback(Entity, float, Vec3, DamageSource, float, boolean)
            player.causeExtraKnockback(target, knockback, oldMovement, damageSource, totalDamage, true);
            if (sweepAttack) {
                invokeSweepAttack(player, target, baseDamage, damageSource, attackStrengthScale);
            }
            // 原版 attackVisualEffects：横扫时 doSweepAttack 已播放 SWEEP 音效，这里不再重复播放。
            if (criticalAttack) {
                playServerSideSound(player, SoundEvents.PLAYER_ATTACK_CRIT);
                player.crit(target);
            } else if (!sweepAttack) {
                playServerSideSound(player,
                        fullStrengthAttack ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK);
            }
            player.setLastHurtMob(target);
            // Folia 26.2：causeFoodExhaustion(float, ExhaustionReason)
            player.causeFoodExhaustion(0.1F, EntityExhaustionEvent.ExhaustionReason.ATTACK);
        } else {
            playServerSideSound(player, SoundEvents.PLAYER_ATTACK_NODAMAGE);
        }
        return wasHurt;
    }

    /** 严格复刻原版 {@code Player.isSweepAttack}。 */
    private static boolean isSweepAttack(ServerPlayer player, boolean fullStrengthAttack,
                                         boolean criticalAttack, boolean knockbackAttack, ItemStack weapon) {
        if (fullStrengthAttack && !criticalAttack && !knockbackAttack && player.onGround()) {
            double approximateSpeedSq = player.getKnownMovement().horizontalDistanceSqr();
            double maxSpeedForSweepAttack = (double) player.getSpeed() * 2.5F;
            if (approximateSpeedSq < maxSpeedForSweepAttack * maxSpeedForSweepAttack) {
                return weapon.is(ItemTags.SWORDS);
            }
        }
        return false;
    }

    /** 严格复刻原版 {@code Player.canCriticalAttack}。 */
    private static boolean canCriticalAttack(ServerPlayer player, Entity target) {
        return player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.isMobilityRestricted()
                && !player.isPassenger()
                && target instanceof LivingEntity
                && !player.isSprinting();
    }

    /** 复刻原版 private {@code Player.baseDamageScaleFactor}。 */
    private static float baseDamageScaleFactor(float attackStrengthScale) {
        return 0.2F + attackStrengthScale * attackStrengthScale * 0.8F;
    }

    /** protected {@code Player.getEnchantedDamage}，反射调用；基类实现返回入参 dmg，失败降级为 dmg。 */
    private static float enchantedDamage(ServerPlayer player, Entity target, float dmg, DamageSource damageSource) {
        if (ENCHANTED_DAMAGE_METHOD != null) {
            try {
                Object result = ENCHANTED_DAMAGE_METHOD.invoke(player, target, dmg, damageSource);
                if (result instanceof Float f) {
                    return f;
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("[TianBot] getEnchantedDamage reflection failed, fallback to base damage", e);
            }
        }
        return dmg;
    }

    /** protected {@code LivingEntity.getKnockback}，反射调用；失败降级为 0。 */
    private static float getKnockback(ServerPlayer player, Entity target, DamageSource damageSource) {
        if (KNOCKBACK_METHOD != null) {
            try {
                Object result = KNOCKBACK_METHOD.invoke(player, target, damageSource);
                if (result instanceof Float f) {
                    return f;
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("[TianBot] getKnockback reflection failed, fallback to 0", e);
            }
        }
        return 0.0F;
    }

    /** private {@code Player.doSweepAttack}，反射调用；失败则跳过横扫（保留主目标伤害）。 */
    private static void invokeSweepAttack(ServerPlayer player, Entity target, float baseDamage,
                                          DamageSource damageSource, float attackStrengthScale) {
        if (SWEEP_ATTACK_METHOD == null) {
            LOGGER.warn("[TianBot] doSweepAttack reflection unavailable, sweep skipped for {}",
                    player.getPlainTextName());
            return;
        }
        try {
            SWEEP_ATTACK_METHOD.invoke(player, target, baseDamage, damageSource, attackStrengthScale);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[TianBot] doSweepAttack invocation failed, sweep skipped for {}",
                    player.getPlainTextName(), e);
        }
    }

    /** 复刻原版 private {@code Player.playServerSideSound}：向假人位置广播音效。 */
    private static void playServerSideSound(ServerPlayer player, SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... paramTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[TianBot] Failed to resolve method {}.{}{}",
                    owner.getSimpleName(), name, Arrays.toString(paramTypes), e);
            return null;
        }
    }
}
