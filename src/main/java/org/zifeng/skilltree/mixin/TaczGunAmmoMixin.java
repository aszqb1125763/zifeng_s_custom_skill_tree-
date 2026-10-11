package org.zifeng.skilltree.mixin;

import com.tacz.guns.item.ModernKineticGunScriptAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.skilltree.compat.TaczSkills;

/**
 * 枪械技能 · 弹匣容量 + 无限子弹（★ 2026-10-11，TaCZ 兼容）。
 *
 * <p>注入 {@code ModernKineticGunScriptAPI} —— 该类同时持有
 * <b>{@code getShooter()}</b>（玩家上下文）和弹匣读写方法，是最干净的挂点：
 * <ul>
 *   <li>{@code getNeededAmmoAmount} / {@code getMaxAmmoCount} / {@code putAmmoInMagazine}：
 *       三处容量函数全部重定向 → 换弹流程与 HUD 显示一致地使用加成后的容量；</li>
 *   <li>{@code consumeAmmoFromPlayer}：换弹扣备弹时报告成功但不扣 → <b>无限换弹</b>。</li>
 * </ul>
 *
 * <p>⚠️ 加成只对服务端玩家生效（见 {@link TaczSkills}），客户端预测不受影响。
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class TaczGunAmmoMixin {

    /**
     * 弹匣容量加成：把 {@code AttachmentDataUtils.getAmmoCountWithAttachment} 的返回值
     * 换成「按玩家技能加成后的容量」。
     *
     * <p>⚠️ <b>必须覆盖全部三个调用点</b>，只改一个等于没效果 —— TaCZ 的换弹流程是：
     * <ol>
     *   <li>{@code getNeededAmmoAmount()} 用<b>原始容量</b>算出"要装几发"；</li>
     *   <li>{@code putAmmoInMagazine(那几发)} 再夹紧到容量上限。</li>
     * </ol>
     * 若只改第 2 步：第 1 步按原始容量只申请那么多 → 永远装不满加成后的上限（用户实测"不生效"）。
     * <p>{@code getMaxAmmoCount()} 也要一并改，否则 HUD 显示的上限与真实不符。
     */
    @Redirect(
            remap = false,
            method = "getNeededAmmoAmount",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/util/AttachmentDataUtils;getAmmoCountWithAttachment"
                            + "(Lnet/minecraft/world/item/ItemStack;"
                            + "Lcom/tacz/guns/resource/pojo/data/gun/GunData;)I"))
    private int zifeng$applyCapacityNeeded(net.minecraft.world.item.ItemStack gunItem,
                                           com.tacz.guns.resource.pojo.data.gun.GunData gunData) {
        return zifeng$capacity((ModernKineticGunScriptAPI) (Object) this, gunItem, gunData);
    }

    /** 弹匣容量加成 · HUD 显示上限（{@code getMaxAmmoCount}） */
    @Redirect(
            remap = false,
            method = "getMaxAmmoCount",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/util/AttachmentDataUtils;getAmmoCountWithAttachment"
                            + "(Lnet/minecraft/world/item/ItemStack;"
                            + "Lcom/tacz/guns/resource/pojo/data/gun/GunData;)I"))
    private int zifeng$applyCapacityMax(net.minecraft.world.item.ItemStack gunItem,
                                        com.tacz.guns.resource.pojo.data.gun.GunData gunData) {
        return zifeng$capacity((ModernKineticGunScriptAPI) (Object) this, gunItem, gunData);
    }

    /**
     * mixin 生效自证（★ 2026-10-11 诊断）：{@code getMaxAmmoCount} 是 HUD 显示上限，
     * 第一次被调用即证明本 mixin 已成功应用（日志只打一次）。
     */
    @Inject(remap = false, method = "getMaxAmmoCount", at = @At("HEAD"))
    private void zifeng$probeApplied(CallbackInfoReturnable<Integer> cir) {
        TaczSkills.logActiveOnce("弹匣容量 getMaxAmmoCount");
    }

    /** 弹匣容量加成 · 实际装填夹紧上限（{@code putAmmoInMagazine}） */
    @Redirect(
            remap = false,
            method = "putAmmoInMagazine",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/util/AttachmentDataUtils;getAmmoCountWithAttachment"
                            + "(Lnet/minecraft/world/item/ItemStack;"
                            + "Lcom/tacz/guns/resource/pojo/data/gun/GunData;)I"))
    private int zifeng$applyCapacityPut(net.minecraft.world.item.ItemStack gunItem,
                                        com.tacz.guns.resource.pojo.data.gun.GunData gunData) {
        return zifeng$capacity((ModernKineticGunScriptAPI) (Object) this, gunItem, gunData);
    }

    /** 三个重定向共用的计算（取原始容量 → 套用玩家技能加成） */
    private static int zifeng$capacity(ModernKineticGunScriptAPI self,
                                       net.minecraft.world.item.ItemStack gunItem,
                                       com.tacz.guns.resource.pojo.data.gun.GunData gunData) {
        int base = com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(gunItem, gunData);
        return TaczSkills.applyCapacity(self.getShooter(), base);
    }

    /**
     * mixin 生效自证（★ 2026-10-11 诊断）：{@code putAmmoInMagazine} 在<b>每次装填</b>时都会调用，
     * 第一次被调用即证明本 mixin 已成功应用（日志只打一次）。
     * <p>⚠️ 早先挂的 {@code getMaxAmmoCount} 极少被调用，日志没出现并不能说明 mixin 没加载。
     */
    @Inject(remap = false, method = "putAmmoInMagazine", at = @At("HEAD"))
    private void zifeng$probeApplied(int amount, CallbackInfoReturnable<Integer> cir) {
        TaczSkills.logActiveOnce("弹匣容量 putAmmoInMagazine");
    }
}
