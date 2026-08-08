package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.zifeng.skilltree.client.ModKeyBindingEvents;

/**
 * 光环圆环渲染（零实体零模型，纯色半透明三角网格，单次 draw call）：
 * <ul>
 *   <li>杀戮光环：淡红色圆环，显示条件 = 光环总开关开启 且 光环有攻击能力（伤害/速度任一开启）</li>
 *   <li>磁力光环：淡蓝色圆环，显示条件 = 磁力光环已学习 且 开关开启</li>
 *   <li>位置：玩家脚下 0.2 高度，水平圆环带（玩家为圆心）</li>
 * </ul>
 */
public class AuraRingRenderer {

    private static final float RING_INNER = 1.0f;  // 圆环带内半径
    private static final float RING_OUTER = 1.5f;  // 圆环带外半径（带厚度 = 0.5）
    private static final float RING_HEIGHT = 0.2f; // 高度：玩家脚下为 0 + 0.2
    private static final int SEGMENTS = 48;        // 分段数（48 段已足够圆滑）

    // 淡红色半透明（杀戮光环）
    private static final float KR = 0.95f, KG = 0.35f, KB = 0.35f, KA = 0.45f;
    // 淡蓝色半透明（磁力光环）
    private static final float MR = 0.35f, MG = 0.65f, MB = 0.95f, MA = 0.45f;

    /**
     * 纯色半透明渲染通道（POSITION_COLOR，无光照无纹理，世界渲染专用）。
     * 注意：NeoForge 1.21.1 中 shader 字段名为 POSITION_COLOR_SHADER（无 RENDERTYPE_ 前缀），
     * 且这些状态字段均为 public static，可直接通过 RenderStateShard 访问。
     */
    private static final RenderType RING_TYPE = RenderType.create(
            "zifeng_aura_ring",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isAlive()) {
            return;
        }
        // 平滑跟随玩家位置（partialTick 插值；RenderLevelStageEvent.getPartialTick() 返回 DeltaTracker）
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double px = player.getX(partialTick);
        double py = player.getY(partialTick) + RING_HEIGHT;
        double pz = player.getZ(partialTick);

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RING_TYPE);

        // 杀戮光环：淡红色圆环（总开关 + 有攻击能力）
        if (ModKeyBindingEvents.isAuraEnabledClient() && ModKeyBindingEvents.isAuraAttackEnabled()) {
            drawRing(consumer, matrix, px, py, pz, KR, KG, KB, KA);
        }
        // 磁力光环：淡蓝色圆环（已学习 + 开关开启）
        if (ModKeyBindingEvents.isMagnetEnabledClient()) {
            drawRing(consumer, matrix, px, py, pz, MR, MG, MB, MA);
        }
        buffers.endBatch(RING_TYPE);
    }

    /** 绘制一圈带厚度的水平圆环带（内径→外径，沿圆周一整圈） */
    private static void drawRing(VertexConsumer consumer, Matrix4f matrix, double px, double py, double pz,
                                 float r, float g, float b, float a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS;
            double a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            double sin0 = Math.sin(a0), cos0 = Math.cos(a0);
            double sin1 = Math.sin(a1), cos1 = Math.cos(a1);
            // 三角 1：inner0, outer0, outer1
            consumer.addVertex(matrix, (float) (px + cos0 * RING_INNER), (float) py, (float) (pz + sin0 * RING_INNER)).setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (px + cos0 * RING_OUTER), (float) py, (float) (pz + sin0 * RING_OUTER)).setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (px + cos1 * RING_OUTER), (float) py, (float) (pz + sin1 * RING_OUTER)).setColor(r, g, b, a);
            // 三角 2：inner0, outer1, inner1
            consumer.addVertex(matrix, (float) (px + cos0 * RING_INNER), (float) py, (float) (pz + sin0 * RING_INNER)).setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (px + cos1 * RING_OUTER), (float) py, (float) (pz + sin1 * RING_OUTER)).setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (px + cos1 * RING_INNER), (float) py, (float) (pz + sin1 * RING_INNER)).setColor(r, g, b, a);
        }
    }
}
