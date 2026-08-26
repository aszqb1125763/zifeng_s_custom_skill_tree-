package org.zifeng.skilltree.mixin;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.skilltree.client.ModKeyBindingEvents;
import org.zifeng.skilltree.skill.Skills;

/**
 * 碧波清眸（UNDERWATER_VISION，2026-08-27 v2）：水下/岩浆完全无雾。
 * <p>修复背景：ViewportEvent.RenderFog 事件在 {@code setupFog} 内部 shader 设置之后触发，
 * 水下是球体指数雾，改 start/end 无效（玩家仍只能看 1~4 格）。
 * 用 Mixin 在 {@code setupFog} 入口直接拦截：技能开启时等价 {@code setupNoFog}
 * （雾 start/end 设为 Float.MAX_VALUE → 雾完全禁用），与空气中视野一致。
 * <p>仅当相机浸没在水/岩浆且本地玩家开启技能时生效；其余情况零开销（一个 FogType 判断）。
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void zifeng$clearUnderwaterFog(Camera camera, FogRenderer.FogMode mode,
                                                  float renderDistance, boolean foggy, float partialTick, CallbackInfo ci) {
        FogType type = camera.getFluidInCamera();
        if (type != FogType.WATER && type != FogType.LAVA) {
            return; // 非水/岩浆：走原逻辑，零开销
        }
        Entity entity = camera.getEntity();
        if (!(entity instanceof LocalPlayer)) {
            return; // 仅本地玩家视角（多人其他人视角不受影响）
        }
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.UNDERWATER_VISION)) {
            return; // 技能未学/未开启
        }
        // 完全禁用雾（等价 setupNoFog：start 设 MAX_VALUE）
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
        ci.cancel();
    }
}
