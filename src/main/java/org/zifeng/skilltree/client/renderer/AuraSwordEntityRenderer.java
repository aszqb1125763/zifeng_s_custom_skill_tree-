package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.zifeng.skilltree.entity.AuraSwordEntity;

/**
 * 飞剑实体渲染器（2026-10-21）：在实体位置画一把竖立的钻石剑，剑面朝飞行方向。
 *
 * <p>飞剑的位置/朝向由服务端逐 tick 同步（{@code AuraSwordEntity.tick} 里
 * {@code setYRot}），这里只做姿态转换：实体 yaw → 绕 Y 轴，再绕 Z 轴立起来。
 */
public class AuraSwordEntityRenderer extends EntityRenderer<AuraSwordEntity> {

    private static final ItemStack SWORD = new ItemStack(Items.DIAMOND_SWORD);

    public AuraSwordEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * A 方案：实体只负责碰撞判定，<b>不渲染</b>。
     * <p>画面由 {@code AuraSwordRenderer} 的贝塞尔飞剑负责（好看、可拖尾）。
     */
    @Override
    public void render(AuraSwordEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 不画任何东西
    }

    @Override
    public boolean shouldRender(AuraSwordEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return false; // 连可见性计算都省掉（性能）
    }

    @Override
    public ResourceLocation getTextureLocation(AuraSwordEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
