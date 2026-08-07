package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.zifeng.skilltree.entity.AuraSwordEntity;

/**
 * 钻石剑渲染器：在实体位置渲染一把钻石剑（环绕玩家的视觉表现）。
 */
public class AuraSwordRenderer extends EntityRenderer<AuraSwordEntity> {

    private static final ItemStack DIAMOND_SWORD = new ItemStack(Items.DIAMOND_SWORD);

    public AuraSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AuraSwordEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // 腿部环绕：剑身略微倾斜朝外，营造环绕感（尺寸贴合腿部比例）
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(1.15f, 1.15f, 1.15f);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                DIAMOND_SWORD, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                entity.level(), 0);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AuraSwordEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
