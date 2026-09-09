package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.skill.Skills;

/**
 * 已绑定容器固定选框（2026-09-08，木棍工具层 BIND 模块，1.20.1）：
 * 条件：手持木棍 + 工具开 + 模式=BIND + 已学任一容器绑定技能（挪移/搬运）+ 已绑定容器。
 * 在【已绑定的容器方块】上固定显示黄绿色选框+半透明面（不随准星移动）——
 * 与磁铁屏蔽区（RANGE 红框/红面）样式区分，标明"这是当前绑定的目标"。
 * ⚠️ 复用 1.20.1 屏蔽区同款 BufferSource 管线（AFTER_BLOCK_ENTITIES 单阶段）。
 */
public final class BindTargetRenderer {
    private BindTargetRenderer() {
    }

    // 黄绿色系（绑定主题：与屏蔽区红/青区分）
    private static final float OUT_R = 0.95F, OUT_G = 0.85F, OUT_B = 0.15F;  // 外框：亮金绿
    private static final float IN_R = 0.5F, IN_G = 1.0F, IN_B = 0.3F;        // 内框：鲜绿
    private static final float FILL_R = 0.4F, FILL_G = 0.9F, FILL_B = 0.2F;  // 半透明面
    private static final float FILL_A = 0.18F;
    private static final float ALPHA = 1.0F;

    /** BIND 模块激活（客户端）：工具开 + 模式=BIND + 已学任一绑技能 */
    static boolean isBindModuleActiveClient() {
        return ModKeyBindingEvents.isStickToolOnClient()
                && ModKeyBindingEvents.getStickToolModeClient() == Skills.STICK_MODE_BIND
                && ModKeyBindingEvents.hasAnyStickToolSkillClient();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // Iris 光影软检测：阴影 pass 跳过（与屏蔽区/宝藏大师同策略）
        if (isIrisShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)) {
            return;
        }
        if (!isBindModuleActiveClient()) {
            return;
        }
        // ⚠️ 2026-09-08：改为【固定在已绑定容器】——不随准星移动。
        //    条件：有绑定 + 绑定容器在本维度（其他维度不显示；容器区块没加载也不显示）。
        String bindDim = ModKeyBindingEvents.getBindDimClient();
        if (bindDim == null) {
            return;
        }
        String curDim = mc.level.dimension().location().toString();
        if (!bindDim.equals(curDim)) {
            return;
        }
        BlockPos pos = new BlockPos(ModKeyBindingEvents.getBindXClient(),
                ModKeyBindingEvents.getBindYClient(), ModKeyBindingEvents.getBindZClient());
        var cam = event.getCamera();
        double camX = cam.getPosition().x, camY = cam.getPosition().y, camZ = cam.getPosition().z;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        float x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
        float x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;

        // 用 BufferSource + 自建 RenderType（与 1.20.1 屏蔽区渲染器同款已验证管线）
        MultiBufferSource.BufferSource bufferSource = bindBuffers();
        // 黄绿半透明面（浅浅一层，标出绑定目标）
        var fvc = bufferSource.getBuffer(BIND_FILL);
        var m4 = poseStack.last().pose();
        addFill(m4, fvc, x0, y0, z0, x1, y1, z1, FILL_R, FILL_G, FILL_B, FILL_A);
        bufferSource.endBatch(BIND_FILL);
        // 双层线框（黄绿）
        var vc = bufferSource.getBuffer(BIND_LINES);
        LevelRenderer.renderLineBox(poseStack, vc, x0 - 0.08F, y0 - 0.08F, z0 - 0.08F,
                x1 + 0.08F, y1 + 0.08F, z1 + 0.08F, OUT_R, OUT_G, OUT_B, ALPHA);
        LevelRenderer.renderLineBox(poseStack, vc, x0 - 0.02F, y0 - 0.02F, z0 - 0.02F,
                x1 + 0.02F, y1 + 0.02F, z1 + 0.02F, IN_R, IN_G, IN_B, ALPHA);
        bufferSource.endBatch(BIND_LINES);

        poseStack.popPose();
    }

    /** 六面填充（1.20.1 VertexConsumer.vertex(matrix,...).color().endVertex()） */
    private static void addFill(org.joml.Matrix4f matrix, com.mojang.blaze3d.vertex.VertexConsumer buf,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float r, float g, float b, float a) {
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
    }

    // ============ 黄绿线框 RenderType（1.20.1 BufferSource 管线，与屏蔽区渲染同款） ============
    private static final class ShardAccess extends net.minecraft.client.renderer.RenderStateShard {
        private ShardAccess() {
            super("zifeng_shard", () -> {
            }, () -> {
            });
        }

        static final DepthTestStateShard LEQUAL_DEPTH = LEQUAL_DEPTH_TEST;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final ShaderStateShard LINES_SHADER = RENDERTYPE_LINES_SHADER;
        static final ShaderStateShard POS_COLOR_SHADER = POSITION_COLOR_SHADER;
        static final LayeringStateShard VIEW_OFFSET = VIEW_OFFSET_Z_LAYERING;
        static final OutputStateShard ITEM_TARGET = ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE;
        static final CullStateShard NO_CULL_S = NO_CULL;
        static final LineStateShard LINE_WIDTH = new LineStateShard(java.util.OptionalDouble.empty());
    }

    private static final net.minecraft.client.renderer.RenderType BIND_FILL = net.minecraft.client.renderer.RenderType.create(
            "zifeng_bind_fill",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            1024, false, true,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POS_COLOR_SHADER)
                    .setLayeringState(ShardAccess.VIEW_OFFSET)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH)
                    .createCompositeState(false));

    private static final net.minecraft.client.renderer.RenderType BIND_LINES = net.minecraft.client.renderer.RenderType.create(
            "zifeng_bind_lines",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES,
            512, false, false,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.LINES_SHADER)
                    .setLineState(ShardAccess.LINE_WIDTH)
                    .setLayeringState(ShardAccess.VIEW_OFFSET)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH)
                    .createCompositeState(false));

    private static MultiBufferSource.BufferSource BIND_BUFFERS;

    private static MultiBufferSource.BufferSource bindBuffers() {
        if (BIND_BUFFERS == null) {
            BIND_BUFFERS = MultiBufferSource.immediateWithBuffers(
                    java.util.Map.of(
                            BIND_LINES, new com.mojang.blaze3d.vertex.BufferBuilder(256),
                            BIND_FILL, new com.mojang.blaze3d.vertex.BufferBuilder(1024)),
                    new com.mojang.blaze3d.vertex.BufferBuilder(256));
        }
        return BIND_BUFFERS;
    }

    /** Iris 光影软检测（无 Iris 时返回 false）：阴影 pass → 跳过渲染 */
    private static java.lang.reflect.Method IRIS_GET_INSTANCE, IRIS_IS_SHADER_IN_USE, IRIS_IS_SHADOW_PASS;

    private static boolean isIrisShadowPass() {
        try {
            if (IRIS_GET_INSTANCE == null) {
                Class<?> irisApiCls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                IRIS_GET_INSTANCE = irisApiCls.getMethod("getInstance");
                IRIS_IS_SHADER_IN_USE = irisApiCls.getMethod("isShaderPackInUse");
                IRIS_IS_SHADOW_PASS = irisApiCls.getMethod("isRenderingShadowPass");
            }
            Object instance = IRIS_GET_INSTANCE.invoke(null);
            if (instance == null) {
                return false;
            }
            if (!(Boolean) IRIS_IS_SHADER_IN_USE.invoke(instance)) {
                return false; // 未开光影
            }
            return (Boolean) IRIS_IS_SHADOW_PASS.invoke(instance); // 阴影 pass
        } catch (Throwable t) {
            return false; // 无 Iris：不跳
        }
    }
}
