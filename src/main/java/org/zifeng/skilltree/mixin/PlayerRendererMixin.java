package org.zifeng.skilltree.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.skilltree.client.renderer.PlayerRingLayer;

/**
 * 玩家渲染器 Mixin：构造时添加星空环渲染层（Avaritia 无尽套绑定玩家模型同款方案）。
 * 零实体零存档：星空环随玩家模型渲染，第三人称/他人可见，无闪烁、不影响存档。
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void zifeng$addRingLayer(EntityRendererProvider.Context context, boolean useSlimModel, CallbackInfo ci) {
        this.addLayer(new PlayerRingLayer(this));
    }
}
