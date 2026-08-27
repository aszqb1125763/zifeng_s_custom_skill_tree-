package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Matrix4f;
import org.zifeng.skilltree.client.ModKeyBindingEvents;

/**
 * 玩家脚下的光环星空环渲染层（Avaritia 式 RenderLayer 绑定玩家模型）：
 * <ul>
 *   <li>零实体零存档，随玩家模型渲染，第三人称/他人可见（无闪烁、存档不受影响）</li>
 *   <li>星空贴图：原版末地传送门 shader（动态星空），自定义通道加 NO_CULL（底部也可见）+ 半透明</li>
 *   <li>内径 1.0 / 外径 2.0；内圈紫色描边；外径渐变虚化（多层递减透明度）</li>
 *   <li>位置：玩家脚底为 0 坐标基点，上移 2 像素（0.1 格）</li>
 *   <li>渲染条件：只绑定杀戮光环（总开关开启 + 伤害/速度任一开启），与磁力光环无关</li>
 * </ul>
 */
public class PlayerRingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** 1.20.1：RenderStateShard 的 shard 常量是 protected，需在子类内部复制暴露（RenderLayer 需保留为基类） */
    private static final class ShardAccess extends RenderStateShard {
        private ShardAccess() {
            super("zifeng_shard_access", () -> {
            }, () -> {
            });
        }

        static final RenderStateShard.ShaderStateShard END_PORTAL_SHADER = RENDERTYPE_END_PORTAL_SHADER;
        static final RenderStateShard.ShaderStateShard POSITION_COLOR_SHADER_S = RenderStateShard.POSITION_COLOR_SHADER;
        static final RenderStateShard.TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final RenderStateShard.CullStateShard NO_CULL_S = NO_CULL;
        static final RenderStateShard.WriteMaskStateShard COLOR_WRITE_S = COLOR_WRITE;
        static final RenderStateShard.WriteMaskStateShard COLOR_DEPTH_WRITE_S = COLOR_DEPTH_WRITE;
    }

    /** 环内半径（格） */
    private static final float RING_INNER = 1.0f;
    /** 环外半径（格） */
    private static final float RING_RADIUS = 2.0f;
    /** 外圈虚化范围（格）：2.0 → 2.2 渐变淡出 */
    private static final float FADE_RADIUS = 2.2f;
    /** 位置：RenderLayer 坐标 Y 轴被 LivingEntityRenderer 的 scale(-1,-1,1) 翻转（py 越大越低，每 +1 = 下降 1 格）。
     *  实测校准：py=0.1 胸口、py=-1.4 头顶、py=2.3 脚底下方1格 → 脚底在 py=1.3；
     *  目标脚底上方 2 像素 → 1.2；用户要求再往下调 2 像素（0.1 格）→ 1.2（当前=降低到脚底上方0像素，再低2像素=贴近地面）
     *  最新：降低到脚底下方 2 像素 → 1.3 */
    private static final float RING_HEIGHT = 1.3f;
    /** 环带分段数 */
    private static final int SEGMENTS = 48;

    /** 末地传送门星空通道（复制原版 END_PORTAL + NO_CULL + 半透明，支持混合 + 深度测试防透视） */
    private static final RenderType RING_STAR = RenderType.create(
            "zifeng_ring_star",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.END_PORTAL_SHADER)
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                            .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                            .build())
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setWriteMaskState(ShardAccess.COLOR_WRITE_S)
                    .createCompositeState(false));

    /** 描边/渐变环通道（POSITION_COLOR，半透明，无剔除） */
    private static final RenderType RING_OVERLAY = RenderType.create(
            "zifeng_ring_overlay",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_COLOR_SHADER_S)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setWriteMaskState(ShardAccess.COLOR_DEPTH_WRITE_S)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .createCompositeState(false));

    public PlayerRingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        // 渲染条件：只绑定杀戮光环（伤害/速度任一开启 = 杀戮光环单独开关），与磁力光环无关
        boolean auraOn = ModKeyBindingEvents.isAuraAttackEnabled();
        if (!auraOn) {
            return;
        }
        // 2026-08-27 性能优化：删除每帧 debug 日志（参数 getName().getString() 每帧分配字符串）

        Matrix4f matrix = poseStack.last().pose();
        // ⚠️ 不强制转 BufferSource（Iris 会包装成 BufferSourceWrapper，强转崩溃）；
        // 直接用 MultiBufferSource 接口 + 不手动 endBatch（由外层渲染系统统一批处理，RenderLayer 标准做法）
        MultiBufferSource buf = bufferSource;

        // ① 星空环（内径 1.0 ~ 外径 2.0）：末地传送门动态星空
        VertexConsumer star = buf.getBuffer(RING_STAR);
        drawRingBand(star, matrix, RING_HEIGHT, RING_INNER, RING_RADIUS);

        // ② 内圈描边（1.0 处亮紫细环）
        VertexConsumer overlay = buf.getBuffer(RING_OVERLAY);
        drawRingBandColor(overlay, matrix, RING_HEIGHT, RING_INNER - 0.04f, RING_INNER + 0.04f,
                0x9B, 0x5F, 0xFF, 230);
        // ③ 外圈渐变虚化（2.0 → 2.6 多层递减透明度）
        for (int i = 1; i <= 6; i++) {
            float r0 = RING_RADIUS + (i - 1) * (FADE_RADIUS - RING_RADIUS) / 6.0f;
            float r1 = RING_RADIUS + i * (FADE_RADIUS - RING_RADIUS) / 6.0f;
            int alpha = (int) (90 * (1.0f - i / 7.0f));
            if (alpha <= 0) break;
            drawRingBandColor(overlay, matrix, RING_HEIGHT, r0, r1,
                    0x5F, 0x3F, 0x9B, alpha);
        }
    }

    /** 星空环带（只有位置，末地传送门 shader 上色） */
    private static void drawRingBand(VertexConsumer consumer, Matrix4f matrix, float py,
                                     float inner, float outer) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS;
            double a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            double sin0 = Math.sin(a0), cos0 = Math.cos(a0);
            double sin1 = Math.sin(a1), cos1 = Math.cos(a1);
            // ⚠️ 1.20.1：vertex() 只设置位置不提交，必须 endVertex() 结束顶点（1.21 的 addVertex 才自动提交）
            consumer.vertex(matrix, (float) (cos0 * inner), py, (float) (sin0 * inner)).endVertex();
            consumer.vertex(matrix, (float) (cos0 * outer), py, (float) (sin0 * outer)).endVertex();
            consumer.vertex(matrix, (float) (cos1 * outer), py, (float) (sin1 * outer)).endVertex();
            consumer.vertex(matrix, (float) (cos1 * inner), py, (float) (sin1 * inner)).endVertex();
        }
    }

    /** 带颜色的环带（描边/渐变） */
    private static void drawRingBandColor(VertexConsumer consumer, Matrix4f matrix, float py,
                                          float inner, float outer, int r, int g, int b, int a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS;
            double a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            double sin0 = Math.sin(a0), cos0 = Math.cos(a0);
            double sin1 = Math.sin(a1), cos1 = Math.cos(a1);
            // ⚠️ 1.20.1：vertex()/color() 后必须 endVertex() 结束顶点（1.21 的 addVertex 才自动提交）
            consumer.vertex(matrix, (float) (cos0 * inner), py, (float) (sin0 * inner)).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, (float) (cos0 * outer), py, (float) (sin0 * outer)).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, (float) (cos1 * outer), py, (float) (sin1 * outer)).color(r, g, b, a).endVertex();
            consumer.vertex(matrix, (float) (cos1 * inner), py, (float) (sin1 * inner)).color(r, g, b, a).endVertex();
        }
    }
}
