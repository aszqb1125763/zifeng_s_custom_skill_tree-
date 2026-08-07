package org.zifeng.skilltree.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 突破原版属性上限：RangedAttribute.sanitizeValue 原本会 clamp 到 [min, max]。
 * 修改为只 clamp 下限，不再限制上限 → 生命/护甲/攻速等属性可以无限叠加。
 * 解决"属性太高不起作用的问题"。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    @Shadow
    @Final
    private double minValue;

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void zifeng$removeMaxLimit(double value, CallbackInfoReturnable<Double> cir) {
        // 只保留下限（防止负数），移除上限限制
        cir.setReturnValue(Math.max(minValue, value));
    }
}
