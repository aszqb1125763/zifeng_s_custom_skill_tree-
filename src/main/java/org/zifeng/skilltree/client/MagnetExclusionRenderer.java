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
 * ⚠️ 2026-09-12 v12 更新：<b>线框改为全透视</b>（{@code NO_DEPTH_TEST}），不再被地形遮挡；
 *    <b>滤镜仍保持真实深度</b>（被方块/山体挡住即不可见）。
 * ⚠️ Iris/Oculus 兼容：阴影 pass 跳过（与 ClientTreasureEvents 相同策略）。
 */
public final class MagnetExclusionRenderer {
    private MagnetExclusionRenderer() {
    }

    // 红（已存区）双层：外红 + 内亮红
    // ⚠️ 2026-09-12 对比度增强（与 ZoneSkillRenderer 同批修复）
    private static final float R1 = 0.78F, G1 = 0.1F, B1 = 0.1F;
    private static final float R2 = 1.0F, G2 = 0.38F, B2 = 0.38F;
    // 青（选区预览）
    private static final float P_R = 0.3F, P_G = 1.0F, P_B = 1.0F;
    private static final float ALPHA = 1.0F;
    // 半透明六面填充（2026-09-08：屏蔽区以"半透明方块"呈现，线框保留做边缘强调）
    private static final float FILL_R = 0.9F, FILL_G = 0.15F, FILL_B = 0.15F, FILL_A = 0.40F;       // 已存区：红色
    private static final float FILL_AIM_R = 1.0F, FILL_AIM_G = 1.0F, FILL_AIM_B = 1.0F, FILL_AIM_A = 0.45F; // 潜行瞄准删除目标：白亮提亮
    private static final float FILL_PRE_R = 0.15F, FILL_PRE_G = 0.95F, FILL_PRE_B = 1.0F, FILL_PRE_A = 0.35F; // 选区预览：青色
    /**
     * 滤镜皮相对边界的偏移量（★★ 2026-09-12 v10，内外通用双层皮方案）。
     * <p>同一个盒子画两次：{@code ∂V + ε}（外侧皮）与 {@code ∂V − ε}（内侧皮），
     * 两皮渲染参数相同、偏移方向相反 → 站在区外/区内都能看到滤镜。
     * <p>旧线框外扩 0.12 格 = 12cm，肉眼可见地飘在区域外面 → 现改为 2mm。
     */
    private static final float FILL_OFFSET = 0.002F;

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
        /** ★ v10：不再使用 VIEW_OFFSET_Z_LAYERING（会把大盒子的面抢到方块前面） */
        static final LayeringStateShard NO_LAYERING_S = NO_LAYERING;
        static final OutputStateShard ITEM_TARGET = ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE; // 不写 depth：线/面不遮挡后续实体
        static final CullStateShard NO_CULL_S = NO_CULL;
        /** ★ v12：<b>线框全透视</b>（GL_ALWAYS）—— 用户要求线框不受地形遮挡 */
        static final DepthTestStateShard NO_DEPTH = NO_DEPTH_TEST;
        /** ★ v11：滤镜启用背面剔除（配合朝外 CCW 绕序；站内部时另画反向皮） */
        static final CullStateShard CULL_S = CULL;
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
                    .setLayeringState(ShardAccess.NO_LAYERING_S)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.NO_DEPTH)
                    .createCompositeState(false));

    /**
     * 滤镜（半透明六面盒）RenderType（★★ 2026-09-12 v10）。
     * <p><b>四项关键设置</b>：{@code NO_CULL}（双面，内外皮都要画）+ {@code LEQUAL_DEPTH_TEST}
     * （<b>被方块挡住就看不见，不穿墙</b>）+ {@code NO_LAYERING}（不再用 VIEW_OFFSET）
     * + {@code COLOR_WRITE}（不写深度，不遮挡后续线框）。
     */
    private static final net.minecraft.client.renderer.RenderType EXCLUSION_FILL = net.minecraft.client.renderer.RenderType.create(
            "zifeng_exclusion_fill",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            4096, false, true, // translucency=true（允许与其它半透明混合排序）
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POS_COLOR_SHADER)
                    .setLayeringState(ShardAccess.NO_LAYERING_S)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.CULL_S)
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
            float fr = aimed ? FILL_AIM_R : FILL_R, fg = aimed ? FILL_AIM_G : FILL_G;
            float fb = aimed ? FILL_AIM_B : FILL_B, fa = aimed ? FILL_AIM_A : FILL_A;
            float bx0 = z.minX(), by0 = z.minY(), bz0 = z.minZ();
            float bx1 = z.maxX() + 1, by1 = z.maxY() + 1, bz1 = z.maxZ() + 1;
            // 朝外绕序双皮 → 服务【外侧】视角
            addBoxFaces(poseMat, fillVertex, bx0, by0, bz0, bx1, by1, bz1, fr, fg, fb, fa, true, false);
            addBoxFaces(poseMat, fillVertex, bx0, by0, bz0, bx1, by1, bz1, fr, fg, fb, fa, false, false);
            // ★ 相机在区内 → 补一次【朝内绕序】的内侧皮
            if (camX >= bx0 && camX <= bx1 && camY >= by0 && camY <= by1 && camZ >= bz0 && camZ <= bz1) {
                addBoxFaces(poseMat, fillVertex, bx0, by0, bz0, bx1, by1, bz1, fr, fg, fb, fa, false, true);
            }
        }
        if (first != null) {
            // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则，所见即所得）
            BlockPos fillSecond = previewSecondCorner(mc);
            float qx0 = Math.min(first.getX(), fillSecond.getX()), qy0 = Math.min(first.getY(), fillSecond.getY());
            float qz0 = Math.min(first.getZ(), fillSecond.getZ());
            float qx1 = Math.max(first.getX(), fillSecond.getX()) + 1, qy1 = Math.max(first.getY(), fillSecond.getY()) + 1;
            float qz1 = Math.max(first.getZ(), fillSecond.getZ()) + 1;
            addBoxFaces(poseMat, fillVertex, qx0, qy0, qz0, qx1, qy1, qz1,
                    FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A, true, false);
            addBoxFaces(poseMat, fillVertex, qx0, qy0, qz0, qx1, qy1, qz1,
                    FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A, false, false);
            if (camX >= qx0 && camX <= qx1 && camY >= qy0 && camY <= qy1 && camZ >= qz0 && camZ <= qz1) {
                addBoxFaces(poseMat, fillVertex, qx0, qy0, qz0, qx1, qy1, qz1,
                        FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A, false, true);
            }
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
            // 线框 0.002 贴边界（旧版外扩 0.12/0.03 会飘在区域外面）
            float ir = aimed ? 1.0F : R2, ig = aimed ? 1.0F : G2, ib = aimed ? 1.0F : B2;
            LevelRenderer.renderLineBox(poseStack, vertex, x0 - FILL_OFFSET, y0 - FILL_OFFSET, z0 - FILL_OFFSET,
                    x1 + FILL_OFFSET, y1 + FILL_OFFSET, z1 + FILL_OFFSET, R1, G1, B1, ALPHA);
            LevelRenderer.renderLineBox(poseStack, vertex, x0, y0, z0, x1, y1, z1, ir, ig, ib, ALPHA);
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

    /**
     * 滤镜盒六个面（★★ 2026-09-12 v11）。
     * @param outer   true = 外侧皮（{@code ∂V + ε}）／false = 内侧皮（{@code ∂V − ε}）
     * @param reverse true = 反向绕序（法线朝内，站区内可见）／false = 正向（法线朝外，站区外可见）
     * <p>渲染类型是 {@code CULL}，正向绕序从内部看全是背面会被剔除 → 故相机在区内时补画反向皮。
     */
    private static void addBoxFaces(org.joml.Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer buf,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    float r, float g, float b, float a, boolean outer, boolean reverse) {
        float e = outer ? FILL_OFFSET : -FILL_OFFSET;
        x0 -= e;
        y0 -= e;
        z0 -= e;
        x1 += e;
        y1 += e;
        z1 += e;
        // 下 -Y
        quad(m, buf, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a, reverse);
        // 上 +Y
        quad(m, buf, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a, reverse);
        // 北 -Z
        quad(m, buf, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a, reverse);
        // 南 +Z
        quad(m, buf, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a, reverse);
        // 西 -X
        quad(m, buf, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a, reverse);
        // 东 +X
        quad(m, buf, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a, reverse);
    }

    /** 单个四边形；{@code reverse=true} 时顶点顺序整体倒过来 → 法线翻转 */
    private static void quad(org.joml.Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer buf,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a, boolean reverse) {
        if (reverse) {
            buf.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
            buf.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
            buf.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
            buf.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
            return;
        }
        buf.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        buf.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        buf.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        buf.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
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
