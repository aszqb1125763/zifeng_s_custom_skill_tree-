package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.zifeng.skilltree.data.MagnetExclusionZone;

import java.util.List;

/**
 * 磁铁屏蔽区渲染（2026-09-07，1.20.1）：
 * 主手或副手持木棍 + 磁铁已学且开启 时显示：
 * <ul>
 *   <li>所有已存屏蔽区：红色双线框（本维度）</li>
 *   <li>选区进行中（已选第一角）：第一角 → 当前视线命中点 的青色预览框</li>
 *   <li>潜行删除瞄准：命中的框内框亮白高亮提示</li>
 * </ul>
 * ⚠️ 不透视：depth 测试正常开启（LEQUAL），被方块遮挡的线不显示（用户要求）。
 * 渲染管线沿用 ClientTreasureEvents 同款（POSITION_COLOR_NORMAL + LINES + 标准 endBatch），
 * 但用普通 lines 渲染类型（depth 正常），不做 disableDepthTest。
 * ⚠️ Iris/Oculus 兼容：阴影 pass 跳过（与 ClientTreasureEvents 相同策略）。
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

    // ============ 不透视 lines RenderType（depth 正常：被挡住不显示） ============
    /** 1.20.1：RenderStateShard 的 shard 常量是 protected，嵌套子类继承以访问 */
    private static final class ShardAccess extends net.minecraft.client.renderer.RenderStateShard {
        private ShardAccess() {
            super("zifeng_shard", () -> {
            }, () -> {
            });
        }

        static final DepthTestStateShard LEQUAL_DEPTH = LEQUAL_DEPTH_TEST;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final ShaderStateShard LINES_SHADER = RENDERTYPE_LINES_SHADER;
        static final ShaderStateShard POS_COLOR_SHADER = POSITION_COLOR_SHADER; // 半透明面（POSITION_COLOR + QUADS）
        static final LayeringStateShard VIEW_OFFSET = VIEW_OFFSET_Z_LAYERING;
        static final OutputStateShard ITEM_TARGET = ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE; // 不写 depth：线/面不遮挡后续实体
        static final CullStateShard NO_CULL_S = NO_CULL;
        static final LineStateShard LINE_WIDTH = new LineStateShard(java.util.OptionalDouble.empty());
    }

    /** 与 RenderType.lines() 同构，仅 depthTest=LEQUAL（正常遮挡）+ 不写 depth */
    private static final net.minecraft.client.renderer.RenderType EXCLUSION_LINES = net.minecraft.client.renderer.RenderType.create(
            "zifeng_exclusion_lines",
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

    /** 半透明六面 RenderType（QUADS + POSITION_COLOR）：面不写 depth、双面渲染、深度正常遮挡 */
    private static final net.minecraft.client.renderer.RenderType EXCLUSION_FILL = net.minecraft.client.renderer.RenderType.create(
            "zifeng_exclusion_fill",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            4096, false, true, // translucency=true（允许与其它半透明混合排序）
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POS_COLOR_SHADER)
                    .setLayeringState(ShardAccess.VIEW_OFFSET)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH)
                    .createCompositeState(false));

    private static MultiBufferSource.BufferSource EXCLUSION_BUFFERS;

    private static MultiBufferSource.BufferSource exclusionBuffers() {
        if (EXCLUSION_BUFFERS == null) {
            EXCLUSION_BUFFERS = MultiBufferSource.immediateWithBuffers(
                    java.util.Map.of(
                            EXCLUSION_LINES, new com.mojang.blaze3d.vertex.BufferBuilder(256),
                            EXCLUSION_FILL, new com.mojang.blaze3d.vertex.BufferBuilder(2048)),
                    new com.mojang.blaze3d.vertex.BufferBuilder(256));
        }
        return EXCLUSION_BUFFERS;
    }

    /** 射线是否命中某屏蔽区（供潜行删除预览高亮） */
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
        // Iris 光影软检测：光影激活且处于阴影 pass → 跳过（与 ClientTreasureEvents 相同策略）
        if (isIrisShadowPass()) {
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
        boolean isAiming = mc.player.isShiftKeyDown() && isRayHittingZone(eye, look);

        var cam = event.getCamera();
        double camX = cam.getPosition().x, camY = cam.getPosition().y, camZ = cam.getPosition().z;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        MultiBufferSource.BufferSource bufferSource = exclusionBuffers();

        // ===== 半透明六面填充（2026-09-08）：屏蔽区以半透明方块呈现（先面后线） =====
        // 面不写 depth（COLOR_WRITE）→ 只做染色不遮挡后续半透明元素；深度正常 → 被地形挡的不显示。
        net.minecraft.world.phys.Vec3 fillAimHit = isAiming ? rayFirstHit(eye, look, zones, dim) : null;
        var poseMat = poseStack.last().pose();
        var fillVertex = bufferSource.getBuffer(EXCLUSION_FILL);
        for (MagnetExclusionZone z : zones) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            boolean aimed = fillAimHit != null && hitZoneEquals(fillAimHit, z);
            addBoxFaces(poseMat, fillVertex, z.minX(), z.minY(), z.minZ(), z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1,
                    aimed ? FILL_AIM_R : FILL_R, aimed ? FILL_AIM_G : FILL_G, aimed ? FILL_AIM_B : FILL_B,
                    aimed ? FILL_AIM_A : FILL_A);
        }
        if (first != null) {
            // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则，所见即所得）
            BlockPos fillSecond = previewSecondCorner(mc);
            addBoxFaces(poseMat, fillVertex,
                    Math.min(first.getX(), fillSecond.getX()), Math.min(first.getY(), fillSecond.getY()),
                    Math.min(first.getZ(), fillSecond.getZ()),
                    Math.max(first.getX(), fillSecond.getX()) + 1, Math.max(first.getY(), fillSecond.getY()) + 1,
                    Math.max(first.getZ(), fillSecond.getZ()) + 1,
                    FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A);
        }
        bufferSource.endBatch(EXCLUSION_FILL);

        var vertex = bufferSource.getBuffer(EXCLUSION_LINES);

        // ===== 已存区：红框（本维度；潜行瞄准的区内框亮白提示删除目标） =====
        net.minecraft.world.phys.Vec3 aimHit = isAiming ? rayFirstHit(eye, look, zones, dim) : null;
        for (MagnetExclusionZone z : zones) {
            if (!z.dim().equals(dim)) {
                continue;
            }
            float x0 = z.minX(), y0 = z.minY(), z0 = z.minZ();
            float x1 = z.maxX() + 1, y1 = z.maxY() + 1, z1 = z.maxZ() + 1;
            boolean aimed = aimHit != null && hitZoneEquals(aimHit, z);
            float ir = aimed ? 1.0F : R2, ig = aimed ? 1.0F : G2, ib = aimed ? 1.0F : B2;
            LevelRenderer.renderLineBox(poseStack, vertex, x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x1 + 0.12F, y1 + 0.12F, z1 + 0.12F, R1, G1, B1, ALPHA);
            LevelRenderer.renderLineBox(poseStack, vertex, x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x1 + 0.03F, y1 + 0.03F, z1 + 0.03F, ir, ig, ib, ALPHA);
        }
        // ===== 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则） =====
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            float x0 = Math.min(first.getX(), second.getX());
            float y0 = Math.min(first.getY(), second.getY());
            float z0 = Math.min(first.getZ(), second.getZ());
            float x1 = Math.max(first.getX(), second.getX()) + 1;
            float y1 = Math.max(first.getY(), second.getY()) + 1;
            float z1 = Math.max(first.getZ(), second.getZ()) + 1;
            LevelRenderer.renderLineBox(poseStack, vertex, x0, y0, z0, x1, y1, z1, P_R, P_G, P_B, ALPHA);
        }
        bufferSource.endBatch(EXCLUSION_LINES);

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

    /** 六面半透明盒（1.20.1 VertexConsumer：vertex(matrix,x,y,z).color().endVertex()） */
    private static void addBoxFaces(org.joml.Matrix4f matrix, com.mojang.blaze3d.vertex.VertexConsumer buf,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    float r, float g, float b, float a) {
        // 底面 -Y
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        // 顶面 +Y
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        // 北面 -Z
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        // 南面 +Z
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y0, z1).color(r, g, b, a).endVertex();
        // 西面 -X
        buf.vertex(matrix, x0, y0, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x0, y0, z1).color(r, g, b, a).endVertex();
        // 东面 +X
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
