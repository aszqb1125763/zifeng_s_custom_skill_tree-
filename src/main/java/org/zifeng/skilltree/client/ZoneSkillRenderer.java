package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.skill.Skills;

/**
 * 机械共鸣·操作区 渲染（2026-09-08，木棍工具模式 2-5，1.20.1）：
 * 手持木棍 + 当前模式对应技能已学 时显示：
 * <ul>
 *   <li>该技能已框选的操作区：金色双线框 + 半透明填充（仅当前维度）</li>
 *   <li>选区进行中（已选第一角）：第一角 → 当前角点 的青色预览框</li>
 * </ul>
 * ⚠️ 渲染管线与 MagnetExclusionRenderer 1.20.1 完全同款（自定义 RenderType + MultiBufferSource +
 *    BufferSource.endBatch，经 ClientTreasureEvents/磁铁实测必显示含光影）。本文件独立复制避免耦合。
 */
public final class ZoneSkillRenderer {
    private ZoneSkillRenderer() {
    }

    // 已框选操作区：金（区别于磁铁红）
    private static final float R1 = 0.55F, G1 = 0.4F, B1 = 0.05F;      // 外暗金
    private static final float R2 = 1.0F, G2 = 0.78F, B2 = 0.2F;      // 内亮金
    private static final float FILL_R = 0.9F, FILL_G = 0.72F, FILL_B = 0.15F, FILL_A = 0.22F; // 半透明金
    // 选区预览：青
    private static final float P_R = 0.1F, P_G = 0.9F, P_B = 0.9F;
    private static final float PRE_R = 0.1F, PRE_G = 0.9F, PRE_B = 0.95F, PRE_A = 0.22F;

    // ============ 不透视 lines RenderType（depth 正常：被挡住不显示） ============
    private static final class ShardAccess extends net.minecraft.client.renderer.RenderStateShard {
        private ShardAccess() {
            super("zifeng_zone_shard", () -> {
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

    private static final net.minecraft.client.renderer.RenderType ZONE_LINES = net.minecraft.client.renderer.RenderType.create(
            "zifeng_zone_lines",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES,
            1536, false, false,
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

    private static final net.minecraft.client.renderer.RenderType ZONE_FILL = net.minecraft.client.renderer.RenderType.create(
            "zifeng_zone_fill",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            4096, false, true,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POS_COLOR_SHADER)
                    .setLayeringState(ShardAccess.VIEW_OFFSET)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH)
                    .createCompositeState(false));

    private static MultiBufferSource.BufferSource ZONE_BUFFERS;

    private static MultiBufferSource.BufferSource zoneBuffers() {
        if (ZONE_BUFFERS == null) {
            ZONE_BUFFERS = MultiBufferSource.immediateWithBuffers(
                    java.util.Map.of(
                            ZONE_LINES, new com.mojang.blaze3d.vertex.BufferBuilder(256),
                            ZONE_FILL, new com.mojang.blaze3d.vertex.BufferBuilder(2048)),
                    new com.mojang.blaze3d.vertex.BufferBuilder(256));
        }
        return ZONE_BUFFERS;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // Iris 光影软检测：阴影 pass 跳过（与磁铁渲染同策略）
        if (isIrisShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 条件：主手或副手任一木棍 + 当前模式为机械共鸣区（2-5）且对应技能已学
        // ⚠️ 2026-09-08：副手木棍仅【显示】，框选/删除等交互仍要求主手木棍（输入层未放宽）
        if (!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)
                && !mc.player.getOffhandItem().is(net.minecraft.world.item.Items.STICK)) {
            return;
        }
        String skill = ZoneSelectionInputHandler.zoneSkillOfCurrentMode();
        if (skill == null || !ZoneSelectionInputHandler.isZoneModuleActiveClient()) {
            return;
        }
        // 当前模式展示区：防护 = 多块全画（上限 10）；其余技能 = 单块
        java.util.List<OperZone> zones = new java.util.ArrayList<>();
        if (Skills.MACHINE_ZONE_PROTECT.equals(skill)) {
            zones.addAll(ModKeyBindingEvents.getProtectZonesClient());
        } else {
            OperZone single = ModKeyBindingEvents.getOperZoneClient(skill);
            if (single != null) {
                zones.add(single);
            }
        }
        BlockPos first = ZoneExclusionClientState.getFirstCorner();
        String dim = mc.level.dimension().location().toString();
        boolean any = false;
        for (OperZone z : zones) {
            if (z.dim().equals(dim)) {
                any = true;
                break;
            }
        }
        if (!any && first == null) {
            return;
        }

        var cam = event.getCamera();
        double camX = cam.getPosition().x, camY = cam.getPosition().y, camZ = cam.getPosition().z;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        MultiBufferSource.BufferSource bufferSource = zoneBuffers();
        var poseMat = poseStack.last().pose();

        // ===== 半透明六面填充 =====
        var fillVertex = bufferSource.getBuffer(ZONE_FILL);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            addBoxFaces(poseMat, fillVertex, zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1,
                    FILL_R, FILL_G, FILL_B, FILL_A);
        }
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            addBoxFaces(poseMat, fillVertex,
                    Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1,
                    Math.max(first.getZ(), second.getZ()) + 1,
                    PRE_R, PRE_G, PRE_B, PRE_A);
        }
        bufferSource.endBatch(ZONE_FILL);

        // ===== 线框（双线金框 + 选区预览青框） =====
        var vertex = bufferSource.getBuffer(ZONE_LINES);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            float x0 = zone.minX(), y0 = zone.minY(), z0 = zone.minZ();
            float x1 = zone.maxX() + 1, y1 = zone.maxY() + 1, z1 = zone.maxZ() + 1;
            LevelRenderer.renderLineBox(poseStack, vertex, x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x1 + 0.12F, y1 + 0.12F, z1 + 0.12F, R1, G1, B1, 1.0F);
            LevelRenderer.renderLineBox(poseStack, vertex, x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x1 + 0.03F, y1 + 0.03F, z1 + 0.03F, R2, G2, B2, 1.0F);
        }
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            LevelRenderer.renderLineBox(poseStack, vertex,
                    Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1,
                    Math.max(first.getZ(), second.getZ()) + 1,
                    P_R, P_G, P_B, 1.0F);
        }
        bufferSource.endBatch(ZONE_LINES);

        poseStack.popPose();
    }

    /** 预览框第二角：按住 Alt → 眼前 1 格；未按 Alt → 左键取角规则（所见即所得） */
    private static BlockPos previewSecondCorner(Minecraft mc) {
        return MagnetExclusionInputHandler.isAltDown()
                ? MagnetExclusionInputHandler.cornerInFront(mc)
                : MagnetExclusionInputHandler.pickCorner();
    }

    /** 六面半透明盒（1.20.1 VertexConsumer：vertex(matrix,x,y,z).color().endVertex()） */
    private static void addBoxFaces(org.joml.Matrix4f matrix, com.mojang.blaze3d.vertex.VertexConsumer buf,
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

    /**
     * Iris 光影软检测（无 Iris 时返回 false）：阴影 pass → 跳过渲染。
     * <p>⚠️ 2026-09-11：改用共享 {@link IrisCompat}（修复「未装 Iris 时每帧抛
     * ClassNotFoundException」，并消除重复实现）。
     */
    private static boolean isIrisShadowPass() {
        return IrisCompat.isShadowPass();
    }
}
