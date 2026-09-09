package org.zifeng.skilltree.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.skill.Skills;

/**
 * 机械共鸣·操作区 渲染（2026-09-08，木棍工具模式 2-5）：
 * 手持木棍 + 当前模式对应技能已学 时显示：
 * <ul>
 *   <li>该技能已框选的操作区：金色双线框 + 半透明填充（仅当前维度）</li>
 *   <li>选区进行中（已选第一角）：第一角 → 当前角点 的青色预览框</li>
 * </ul>
 * ⚠️ 渲染管线与 MagnetExclusionRenderer 完全同款（宝藏大师管线：手动 Tesselator + 半透明 QUADS + DEBUG_LINES，
 *    实测必显示含光影）。本文件为独立复制（磁铁渲染器内部逻辑高度内聚，不宜耦合）。
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

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
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
        boolean any = false;
        String dim = mc.level.dimension().location().toString();
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
        org.joml.Matrix4f poseMat = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        // ===== 半透明六面填充 =====
        RenderSystem.depthMask(false);
        BufferBuilder fill = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            addBoxFaces(fill, poseMat, zone.minX(), zone.minY(), zone.minZ(),
                    zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1,
                    FILL_R, FILL_G, FILL_B, FILL_A);
        }
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            addBoxFaces(fill, poseMat,
                    Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1,
                    Math.max(first.getZ(), second.getZ()) + 1,
                    PRE_R, PRE_G, PRE_B, PRE_A);
        }
        BufferUploader.drawWithShader(fill.buildOrThrow());
        RenderSystem.depthMask(true);

        // ===== 线框 =====
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            float x0 = zone.minX(), y0 = zone.minY(), z0 = zone.minZ();
            float x1 = zone.maxX() + 1, y1 = zone.maxY() + 1, z1 = zone.maxZ() + 1;
            LevelRenderer.renderLineBox(poseStack, buffer, x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x1 + 0.12F, y1 + 0.12F, z1 + 0.12F, R1, G1, B1, 1.0F);
            LevelRenderer.renderLineBox(poseStack, buffer, x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x1 + 0.03F, y1 + 0.03F, z1 + 0.03F, R2, G2, B2, 1.0F);
        }
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            LevelRenderer.renderLineBox(poseStack, buffer,
                    Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()),
                    Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1,
                    Math.max(first.getZ(), second.getZ()) + 1,
                    P_R, P_G, P_B, 1.0F);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** 预览框第二角：按住 Alt → 眼前 1 格；未按 Alt → 左键取角规则（所见即所得） */
    private static BlockPos previewSecondCorner(Minecraft mc) {
        return MagnetExclusionInputHandler.isAltDown()
                ? MagnetExclusionInputHandler.cornerInFront(mc)
                : MagnetExclusionInputHandler.pickCorner();
    }

    /** 六面半透明盒（与磁铁渲染同款：addVertex(matrix,...) 必须带矩阵） */
    private static void addBoxFaces(BufferBuilder buf, org.joml.Matrix4f matrix,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    float r, float g, float b, float a) {
        buf.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z0).setColor(r, g, b, a);
    }
}
