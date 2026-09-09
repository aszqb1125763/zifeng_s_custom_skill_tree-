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
import org.zifeng.skilltree.data.MagnetExclusionZone;

import java.util.List;

/**
 * 磁铁屏蔽区渲染（2026-09-07）：
 * 主手或副手持木棍 + 磁铁已学 时显示：
 * <ul>
 *   <li>所有已存屏蔽区：红色双线框（本维度）</li>
 *   <li>选区进行中（已选第一角）：第一角 → 当前视线命中点 的青色预览框</li>
 *   <li>潜行删除瞄准：命中某区的框会亮白/高亮提示</li>
 * </ul>
 * ⚠️ 2026-09-08 最终方案：与宝藏大师(ClientTreasureEvents)完全同款的手动 Tesselator
 *    管线（enableBlend + disableCull + setShader positionColor + drawWithShader）——
 *    实测该模式必显示（含光影环境）。半透明六面用 QUADS、线框用 DEBUG_LINES，
 *    均在该管线内绘制。depth 测试正常开启（不透视原则延续）。
 */
public final class MagnetExclusionRenderer {
    private MagnetExclusionRenderer() {
    }

    // 红（已存区）双层：外暗红 + 内亮红
    private static final float R1 = 0.55F, G1 = 0.05F, B1 = 0.05F;
    private static final float R2 = 1.0F, G2 = 0.2F, B2 = 0.2F;
    // 青（选区预览）
    private static final float P_R = 0.1F, P_G = 0.9F, P_B = 0.9F;
    private static final float ALPHA = 1.0F;
    // 半透明六面填充（2026-09-08：屏蔽区以"半透明方块"呈现，线框保留做边缘强调）
    private static final float FILL_R = 0.85F, FILL_G = 0.12F, FILL_B = 0.12F, FILL_A = 0.30F;       // 已存区：红色
    private static final float FILL_AIM_R = 1.0F, FILL_AIM_G = 1.0F, FILL_AIM_B = 1.0F, FILL_AIM_A = 0.35F; // 潜行瞄准删除目标：白亮提亮
    private static final float FILL_PRE_R = 0.1F, FILL_PRE_G = 0.9F, FILL_PRE_B = 0.95F, FILL_PRE_A = 0.26F; // 选区预览：青色

    /** 潜行瞄准命中高亮（白）时替换内框色 */
    private static float aimR = R2, aimG = G2, aimB = B2;
    private static boolean isAiming = false; // 本帧是否在删除瞄准（用鼠标十字射线命中检测）

    /** 射线是否命中某屏蔽区（供潜行删除预览高亮 + 渲染时判断） */
    public static boolean isRayHittingZone(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 dir) {
        List<MagnetExclusionZone> zs = MagnetExclusionClientState.getZones();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        String dim = mc.level.dimension().location().toString();
        for (MagnetExclusionZone z : zs) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    z.minX(), z.minY(), z.minZ(), z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1);
            if (box.clip(from, from.add(dir.scale(200))).isPresent()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 条件：主手或副手任一木棍 + 工具开 + RANGE 模块（磁铁已学；仅已学判定，磁铁开关不影响查看/配置）
        // ⚠️ 2026-09-08：副手木棍仅【显示】，框选/删除等交互仍要求主手木棍（输入层未放宽）
        if (!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.STICK)
                && !mc.player.getOffhandItem().is(net.minecraft.world.item.Items.STICK)) {
            return;
        }
        if (!MagnetExclusionInputHandler.isRangeModuleActiveClient()) {
            return;
        }
        List<MagnetExclusionZone> zones = MagnetExclusionClientState.getZones();
        BlockPos first = MagnetExclusionClientState.getFirstCorner();
        if (zones.isEmpty() && first == null) {
            return;
        }
        String dim = mc.level.dimension().location().toString();

        // 潜行删除瞄准检测（射线命中高亮）
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        isAiming = mc.player.isShiftKeyDown() && isRayHittingZone(eye, look);

        var cam = event.getCamera();
        double camX = cam.getPosition().x, camY = cam.getPosition().y, camZ = cam.getPosition().z;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        // ===== 宝藏大师同款管线（2026-09-08 实测必显示） =====
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // ⚠️ 不 disableDepthTest —— 被遮挡部分不显示（不透视，用户要求）
        RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        // ===== 半透明六面填充（QUADS；面不写深度避免遮挡后续半透明） =====
        // ⚠️ 2026-09-08：必须带 pose 矩阵（addVertex(matrix,...)），否则顶点画错位置不显示
        org.joml.Matrix4f poseMat = poseStack.last().pose();
        net.minecraft.world.phys.Vec3 fillAimHit = isAiming ? rayFirstHit(eye, look, zones, dim) : null;
        RenderSystem.depthMask(false);
        BufferBuilder fill = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (MagnetExclusionZone z : zones) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            boolean aimed = fillAimHit != null && hitZoneEquals(fillAimHit, z);
            addBoxFaces(fill, poseMat, z.minX(), z.minY(), z.minZ(), z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1,
                    aimed ? FILL_AIM_R : FILL_R, aimed ? FILL_AIM_G : FILL_G, aimed ? FILL_AIM_B : FILL_B,
                    aimed ? FILL_AIM_A : FILL_A);
        }
        if (first != null) {
            // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则，所见即所得）
            net.minecraft.core.BlockPos fillSecond = previewSecondCorner(mc);
            addBoxFaces(fill, poseMat,
                    Math.min(first.getX(), fillSecond.getX()), Math.min(first.getY(), fillSecond.getY()),
                    Math.min(first.getZ(), fillSecond.getZ()),
                    Math.max(first.getX(), fillSecond.getX()) + 1, Math.max(first.getY(), fillSecond.getY()) + 1,
                    Math.max(first.getZ(), fillSecond.getZ()) + 1,
                    FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A);
        }
        BufferUploader.drawWithShader(fill.buildOrThrow());
        RenderSystem.depthMask(true);

        // ===== 线框（DEBUG_LINES）：已存红框 + 选区青框 =====
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        net.minecraft.world.phys.Vec3 aimHit = isAiming ? rayFirstHit(eye, look, zones, dim) : null;
        for (MagnetExclusionZone z : zones) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            float x0 = z.minX(), y0 = z.minY(), z0 = z.minZ();
            float x1 = z.maxX() + 1, y1 = z.maxY() + 1, z1 = z.maxZ() + 1;
            // 潜行瞄准的区：内框亮白强调（删除目标）
            boolean aimed = aimHit != null && hitZoneEquals(aimHit, z);
            float ir = aimed ? 1.0F : R2, ig = aimed ? 1.0F : G2, ib = aimed ? 1.0F : B2;
            LevelRenderer.renderLineBox(poseStack, buffer, x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x1 + 0.12F, y1 + 0.12F, z1 + 0.12F, R1, G1, B1, ALPHA);
            LevelRenderer.renderLineBox(poseStack, buffer, x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x1 + 0.03F, y1 + 0.03F, z1 + 0.03F, ir, ig, ib, ALPHA);
        }
        if (first != null) {
            // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则）
            net.minecraft.core.BlockPos second = previewSecondCorner(mc);
            float x0 = Math.min(first.getX(), second.getX());
            float y0 = Math.min(first.getY(), second.getY());
            float z0 = Math.min(first.getZ(), second.getZ());
            float x1 = Math.max(first.getX(), second.getX()) + 1;
            float y1 = Math.max(first.getY(), second.getY()) + 1;
            float z1 = Math.max(first.getZ(), second.getZ()) + 1;
            LevelRenderer.renderLineBox(poseStack, buffer, x0, y0, z0, x1, y1, z1, P_R, P_G, P_B, ALPHA);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static net.minecraft.world.phys.Vec3 rayFirstHit(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 dir,
                                                             List<MagnetExclusionZone> zones, String dim) {
        net.minecraft.world.phys.Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (MagnetExclusionZone z : zones) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    z.minX(), z.minY(), z.minZ(), z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1);
            var hit = box.clip(from, from.add(dir.scale(200))).orElse(null);
            if (hit != null) {
                double d = from.distanceToSqr(hit);
                if (d < bestDist) {
                    bestDist = d;
                    best = hit;
                }
            }
        }
        return best;
    }

    private static boolean hitZoneEquals(net.minecraft.world.phys.Vec3 hit, MagnetExclusionZone z) {
        return hit.x >= z.minX() && hit.x <= z.maxX() + 1
                && hit.y >= z.minY() && hit.y <= z.maxY() + 1
                && hit.z >= z.minZ() && hit.z <= z.maxZ() + 1;
    }

    /**
     * 预览框第二角（2026-09-08）：按住 Alt → 眼前 1 格（Alt+右键落点，预览即时跟随）；
     * 未按 Alt → 左键取角规则（pickCorner）。保证"按住 Alt 时预览框立刻切换过去"。
     */
    private static net.minecraft.core.BlockPos previewSecondCorner(Minecraft mc) {
        return MagnetExclusionInputHandler.isAltDown()
                ? MagnetExclusionInputHandler.cornerInFront(mc)
                : MagnetExclusionInputHandler.pickCorner();
    }

    /**
     * 六面半透明盒（面片顶点：外表面刚好贴合盒子边界；调用方已 disableCull，绕序无关）。
     * ⚠️ 2026-09-08 修复：1.21.1 裸 addVertex(x,y,z) 不带矩阵 → 顶点画错位置不显示；
     *    必须用 addVertex(matrix, ...)（matrix = poseStack.last().pose()）。
     */
    private static void addBoxFaces(BufferBuilder buf, org.joml.Matrix4f matrix,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    float r, float g, float b, float a) {
        // 底面 -Y
        buf.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z0).setColor(r, g, b, a);
        // 顶面 +Y
        buf.addVertex(matrix, x0, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z1).setColor(r, g, b, a);
        // 北面 -Z
        buf.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z0).setColor(r, g, b, a);
        // 南面 +Z
        buf.addVertex(matrix, x0, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z1).setColor(r, g, b, a);
        // 西面 -X
        buf.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x0, y0, z1).setColor(r, g, b, a);
        // 东面 +X
        buf.addVertex(matrix, x1, y0, z0).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x1, y1, z0).setColor(r, g, b, a);
    }
}
