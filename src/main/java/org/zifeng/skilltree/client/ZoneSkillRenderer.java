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
    // ⚠️ 2026-09-12 对比度增强：用户反馈"边缘分不清内外"→ 边框提亮 + 填充更实
    private static final float R1 = 0.78F, G1 = 0.6F, B1 = 0.1F;      // 外金
    private static final float R2 = 1.0F, G2 = 0.92F, B2 = 0.45F;     // 内亮金
    private static final float FILL_R = 0.95F, FILL_G = 0.78F, FILL_B = 0.2F, FILL_A = 0.35F; // 半透明金
    // 选区预览：青
    private static final float P_R = 0.3F, P_G = 1.0F, P_B = 1.0F;
    private static final float PRE_R = 0.15F, PRE_G = 0.95F, PRE_B = 1.0F, PRE_A = 0.35F;
    /**
     * 滤镜皮相对边界的偏移量（★★ 2026-09-12 v10，内外通用双层皮方案）。
     *
     * <p><b>为何"双层"</b>：选区边界面（如 +Z 侧的 {@code maxZ+1}）<b>同时</b>是外侧方块的远面与
     * 内侧方块的近面。只用一层壳时：往外偏 → 站外面看得见/站里面看不见；往内偏 → 反之。
     * <b>物理上外侧皮朝外、内侧皮朝内是两张皮</b>，所以本方案把同一个盒子画两次：
     * <ul>
     *   <li>{@code ∂V + ε}（外侧皮）→ 站在选区<b>外面</b>时可见</li>
     *   <li>{@code ∂V − ε}（内侧皮）→ 站在选区<b>里面</b>时可见</li>
     * </ul>
     * 两张皮用<b>同一套渲染参数</b>，只是偏移方向相反 → 内外表现一致。
     *
     * <p><b>为何是 0.002（而非旧的 0.12 / 0.03）</b>：旧线框外扩 0.12 格 = 12cm，肉眼可见地
     * 飘在选区外面，制造出"选区外有颜色"的假象。2mm 即脱离方块表面（避免共面景深冲突），
     * 又基本不可察觉。
     */
    private static final float FILL_OFFSET = 0.002F;

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
        /**
         * ★ 2026-09-12 v10：<b>不再使用 {@code VIEW_OFFSET_Z_LAYERING}</b>。
         * <p>它把几何体往相机拉 0.024%（远处可达 7cm）—— BG 只用在 1×1×1 单方块上（避自共面），
         * 我们用在 1677 万格大盒子上会<b>抢赢方块深度 → 把外侧方块染上色</b>。
         * 现改用 {@link #FILL_OFFSET} 的 0.002 微小偏移。
         */
        static final LayeringStateShard NO_LAYERING_S = NO_LAYERING;
        static final OutputStateShard ITEM_TARGET = ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE;
        static final CullStateShard NO_CULL_S = NO_CULL;
        /**
         * ★ 2026-09-12 v12：<b>线框全透视</b>（{@code NO_DEPTH_TEST} = GL_ALWAYS）。
         * <p>用户要求：线框不受地形遮挡，隔着山也能看到边界轮廓（亮度不变）。
         * <p>滤镜仍用 {@link #LEQUAL_DEPTH}（保持"被方块挡住就看不见"的立体感）。
         */
        static final DepthTestStateShard NO_DEPTH = NO_DEPTH_TEST;
        /**
         * ★ 2026-09-12 v11：滤镜启用<b>背面剔除</b>。
         * <p>配合正确的「从盒外看 CCW」绕序，外侧视角只画 3 个朝外面（不再叠 6 层）。
         * <p>站在盒内时额外补一次<b>反向绕序</b>的内侧皮（见调用点），
         * 否则朝外绕序的面从内部看全是背面 → 被剔除 → 只能看到线框。
         */
        static final CullStateShard CULL_S = CULL;
        static final LineStateShard LINE_WIDTH = new LineStateShard(java.util.OptionalDouble.empty());
    }

    /**
     * 线框 RenderType（★★ 2026-09-12 v12：<b>全透视</b>，不受地形遮挡）。
     * <p>与滤镜的唯一区别就是 {@code NO_DEPTH_TEST}（GL_ALWAYS）—— 隔着山也能看到边界轮廓。
     * <p>其余保持原样：不写深度（不遮挡后续）、双面、不施加 z 偏移。
     */
    private static final net.minecraft.client.renderer.RenderType ZONE_LINES = net.minecraft.client.renderer.RenderType.create(
            "zifeng_zone_lines",
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
     *
     * <p><b>四项关键设置</b>：
     * <ol>
     *   <li>{@code NO_CULL} —— 双面渲染：外侧皮与内侧皮都要能看见（内外通用方案的前提）；</li>
     *   <li>{@code LEQUAL_DEPTH_TEST} —— <b>真实深度测试：被方块/山体挡住就看不见（不穿墙）</b>；</li>
     *   <li>{@code NO_LAYERING} —— 不再用 {@code VIEW_OFFSET}（它会把大盒子的面抢到方块前面）；</li>
     *   <li>{@code COLOR_WRITE}（不写深度）—— 滤镜不遮挡后续要画的线框。</li>
     * </ol>
     * <p>几何上的 0.002 偏移由 {@link #addBoxFaces} 施加（{@code outer} 参数控制内外层）。
     */
    private static final net.minecraft.client.renderer.RenderType ZONE_FILL = net.minecraft.client.renderer.RenderType.create(
            "zifeng_zone_fill",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            4096, false, true,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POS_COLOR_SHADER)
                    .setLayeringState(ShardAccess.NO_LAYERING_S)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_ONLY)
                    .setCullState(ShardAccess.CULL_S)
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
            // 朝外绕序的双皮 → 服务【外侧】视角（外层 + 内层叠加出滤镜浓度）
            float bx0 = zone.minX(), by0 = zone.minY(), bz0 = zone.minZ();
            float bx1 = zone.maxX() + 1, by1 = zone.maxY() + 1, bz1 = zone.maxZ() + 1;
            addBoxFaces(poseMat, fillVertex, bx0, by0, bz0, bx1, by1, bz1,
                    FILL_R, FILL_G, FILL_B, FILL_A, true, false);
            addBoxFaces(poseMat, fillVertex, bx0, by0, bz0, bx1, by1, bz1,
                    FILL_R, FILL_G, FILL_B, FILL_A, false, false);
            // ★ 相机在选区内 → 补一次【朝内绕序】的内侧皮（否则内部只能看到线框）
            if (camX >= bx0 && camX <= bx1 && camY >= by0 && camY <= by1 && camZ >= bz0 && camZ <= bz1) {
                addBoxFaces(poseMat, fillVertex, bx0, by0, bz0, bx1, by1, bz1,
                        FILL_R, FILL_G, FILL_B, FILL_A, false, true);
            }
        }
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            float px0 = Math.min(first.getX(), second.getX()), py0 = Math.min(first.getY(), second.getY());
            float pz0 = Math.min(first.getZ(), second.getZ());
            float px1 = Math.max(first.getX(), second.getX()) + 1, py1 = Math.max(first.getY(), second.getY()) + 1;
            float pz1 = Math.max(first.getZ(), second.getZ()) + 1;
            addBoxFaces(poseMat, fillVertex, px0, py0, pz0, px1, py1, pz1, PRE_R, PRE_G, PRE_B, PRE_A, true, false);
            addBoxFaces(poseMat, fillVertex, px0, py0, pz0, px1, py1, pz1, PRE_R, PRE_G, PRE_B, PRE_A, false, false);
            if (camX >= px0 && camX <= px1 && camY >= py0 && camY <= py1 && camZ >= pz0 && camZ <= pz1) {
                addBoxFaces(poseMat, fillVertex, px0, py0, pz0, px1, py1, pz1, PRE_R, PRE_G, PRE_B, PRE_A, false, true);
            }
        }
        bufferSource.endBatch(ZONE_FILL);

        // ===== 线框（0.002 贴边界；旧版外扩 0.12/0.03 会飘在选区外面制造假象） =====
        var vertex = bufferSource.getBuffer(ZONE_LINES);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            float x0 = zone.minX(), y0 = zone.minY(), z0 = zone.minZ();
            float x1 = zone.maxX() + 1, y1 = zone.maxY() + 1, z1 = zone.maxZ() + 1;
            LevelRenderer.renderLineBox(poseStack, vertex, x0 - FILL_OFFSET, y0 - FILL_OFFSET, z0 - FILL_OFFSET,
                    x1 + FILL_OFFSET, y1 + FILL_OFFSET, z1 + FILL_OFFSET, R1, G1, B1, 1.0F);
            LevelRenderer.renderLineBox(poseStack, vertex, x0, y0, z0, x1, y1, z1, R2, G2, B2, 1.0F);
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

    /**
     * 滤镜盒六个面（★★ 2026-09-12 v11：内外视角各用一套绕序）。
     *
     * @param outer   true = 外侧皮（{@code ∂V + ε}）／false = 内侧皮（{@code ∂V − ε}）
     * @param reverse true = 反向绕序（法线朝<b>内</b>，供站在选区内部时可见）
     *                ／false = 正向绕序（法线朝<b>外</b>，供站在选区外部时可见）
     *
     * <p><b>为何需要 reverse</b>：渲染类型是 {@code CULL}（背面剔除）。
     * 正向绕序的面从盒子内部看全是背面 → 被剔除 → 站在选区内只能看到线框。
     * 所以相机在选区内时额外画一次反向绕序的内侧皮。
     *
     * <p><b>绕序基准</b>：学自 Building Gadgets {@code BaseRenderer#renderBoxSolid}
     * ——「从盒子外部看逆时针(CCW)」；本文件已逐面叉积验证法线朝外
     * （下=-Y、上=+Y、北=-Z、南=+Z、西=-X、东=+X）。
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

    /**
     * 单个四边形。
     * <p>{@code reverse=true} 时顶点顺序<b>整体倒过来</b>（v3→v0）→ 法线翻转。
     * <p>顶点绕序基准：正向 = 「从盒外看逆时针(CCW)」= 法线朝外。
     */
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
