package org.zifeng.skilltree.mixin;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械·不过热（2026-10-22）。
 *
 * <p>TaCZ 的过热由 {@code ModernKineticGunItem.tickHeat(ShooterDataHolder, ItemStack, LivingEntity)}
 * 每 tick 结算（基类 {@code AbstractGunItem} 是空实现，具体枪械类覆写）。
 * 学了技能后直接 cancel 这一 tick 的热量结算：不再累积、不会过热。
 *
 * <p>⚠️ 只对玩家生效；未学技能的玩家行为不变。
 */
@Mixin(ModernKineticGunItem.class)
public class TaczNoHeatMixin {

    // remap = false：tickHeat 是 TaCZ 自己的方法（非 MC 方法），不需要 SRG 重映射
    /** 已打印过日志的玩家（避免刷屏） */
    private static final java.util.Set<java.util.UUID> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 把热量归零 + 解除过热锁定 */
    private static void zeroHeat(ItemStack stack) {
        try {
            final com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if (gun != null) {
                gun.setHeatAmount(stack, 0.0F);
                gun.setOverheatLocked(stack, false);
            }
        } catch (Throwable ignored) {
            // TaCZ 版本差异时静默降级
        }
    }

    @Inject(method = "tickHeat", at = @At("HEAD"), cancellable = true, remap = false)
    private void zifeng$noHeat(ShooterDataHolder data, ItemStack stack, LivingEntity shooter, CallbackInfo ci) {
        if (!TaczSkills.hasNoHeat(shooter)) {
            return;
        }
        if (shooter instanceof net.minecraft.server.level.ServerPlayer sp && LOGGED.add(sp.getUUID())) {
            org.zifeng.skilltree.SkillTreeMod.LOGGER.info("[不过热] mixin 已生效：{}", sp.getName().getString());
        }
        zeroHeat(stack);
        ci.cancel();
    }

    /** 射击瞬间也归零（热量由 tickHeat 里的脚本累加，这里截断源头） */
    @Inject(method = "shoot", at = @At("HEAD"), remap = false)
    private void zifeng$noHeatShoot(ShooterDataHolder data, ItemStack stack,
                                    java.util.function.Supplier<Float> pitch, java.util.function.Supplier<Float> yaw,
                                    LivingEntity shooter, CallbackInfo ci) {
        if (TaczSkills.hasNoHeat(shooter)) {
            zeroHeat(stack);
        }
    }
}
