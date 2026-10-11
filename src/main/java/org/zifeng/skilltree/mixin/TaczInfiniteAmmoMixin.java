package org.zifeng.skilltree.mixin;

import com.tacz.guns.entity.shooter.LivingEntityAmmoCheck;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 无限子弹（★ 2026-10-11，TaCZ 兼容）。
 *
 * <h2>为什么注入 {@code needCheckAmmo()}</h2>
 * 这是 TaCZ 的<b>弹药总开关</b>（创造模式走的就是它）：
 * <pre>
 *   public boolean needCheckAmmo() { return !player.isCreative(); }
 * </pre>
 * 所有弹药消费点都判它，返回 false 的效果 <b>完全等同创造模式无限弹药</b>：
 * <ul>
 *   <li>{@code LivingEntityShoot.consumeAmmoFromPlayer} → {@code if (!needCheckAmmo) return;}
 *       → 开火与换弹<b>都不消耗</b>；</li>
 *   <li>{@code AbstractGunItem.hasInventoryAmmo} → {@code if (!needCheckAmmo) return true;}
 *       → 永远"有弹"；</li>
 *   <li>{@code GunHudOverlay} → {@code else cacheInventoryAmmoCount = MAX_AMMO_COUNT;}
 *       → HUD 显示<b>正常上限</b>（不再显示虚拟弹药数）。</li>
 * </ul>
 * 比"补满虚拟弹药池"干净得多：<b>没有多余 NBT、没有 4000 的怪显示、不吃实弹也不吃虚拟弹</b>。
 *
 * <p>⚠️ 双端都会调用本方法（客户端据此决定能否开火/换弹）→ 判定必须双端一致，
 * 见 {@link TaczSkills#hasInfiniteAmmo}（服务端查存档 / 客户端查已同步缓存）。
 *
 * <p>⚠️ TaCZ 是模组类 → 注解必须 {@code remap = false}（否则注解处理器找不到映射、编译失败）。
 */
@Mixin(value = LivingEntityAmmoCheck.class, remap = false)
public abstract class TaczInfiniteAmmoMixin {

    /** TaCZ 的射击者字段（private final，无 getter）→ 用 @Shadow 直接取 */
    @Shadow
    @Final
    private LivingEntity shooter;

    @Inject(remap = false, method = "needCheckAmmo", at = @At("HEAD"), cancellable = true)
    private void zifeng$infiniteAmmo(CallbackInfoReturnable<Boolean> cir) {
        TaczSkills.logActiveOnce("无限子弹 needCheckAmmo"); // 自证：首次调用即证明本 mixin 已生效
        if (TaczSkills.hasInfiniteAmmo(this.shooter)) {
            cir.setReturnValue(false); // 等同创造模式：不检查、不消耗
        }
    }
}
