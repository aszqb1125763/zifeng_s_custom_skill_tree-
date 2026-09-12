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
        RenderSystem.enableCull();
        // ★★ v10 关键修复：**显式开启真实深度测试**（旧版从未设置过 → 依赖遗留状态，可能穿墙）
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        // ===== 滤镜（半透明六面盒）：外侧视角用朝外绕序双皮 =====
        //    ★ 相机在盒内时另补一次【反向绕序】的内侧皮（否则内部只能看到线框）
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
            float fr = aimed ? FILL_AIM_R : FILL_R, fg = aimed ? FILL_AIM_G : FILL_G;
            float fb = aimed ? FILL_AIM_B : FILL_B, fa = aimed ? FILL_AIM_A : FILL_A;
            float bx0 = z.minX(), by0 = z.minY(), bz0 = z.minZ();
            float bx1 = z.maxX() + 1, by1 = z.maxY() + 1, bz1 = z.maxZ() + 1;
            // 朝外绕序双皮 → 服务【外侧】视角
            addBoxFaces(fill, poseMat, bx0, by0, bz0, bx1, by1, bz1, fr, fg, fb, fa, true, false);
            addBoxFaces(fill, poseMat, bx0, by0, bz0, bx1, by1, bz1, fr, fg, fb, fa, false, false);
            if (camX >= bx0 && camX <= bx1 && camY >= by0 && camY <= by1 && camZ >= bz0 && camZ <= bz1) {
                addBoxFaces(fill, poseMat, bx0, by0, bz0, bx1, by1, bz1, fr, fg, fb, fa, false, true);
            }
        }
        if (first != null) {
            // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则，所见即所得）
            net.minecraft.core.BlockPos fillSecond = previewSecondCorner(mc);
            float qx0 = Math.min(first.getX(), fillSecond.getX()), qy0 = Math.min(first.getY(), fillSecond.getY());
            float qz0 = Math.min(first.getZ(), fillSecond.getZ());
            float qx1 = Math.max(first.getX(), fillSecond.getX()) + 1, qy1 = Math.max(first.getY(), fillSecond.getY()) + 1;
            float qz1 = Math.max(first.getZ(), fillSecond.getZ()) + 1;
            addBoxFaces(fill, poseMat, qx0, qy0, qz0, qx1, qy1, qz1,
                    FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A, true, false);
            addBoxFaces(fill, poseMat, qx0, qy0, qz0, qx1, qy1, qz1,
                    FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A, false, false);
            if (camX >= qx0 && camX <= qx1 && camY >= qy0 && camY <= qy1 && camZ >= qz0 && camZ <= qz1) {
                addBoxFaces(fill, poseMat, qx0, qy0, qz0, qx1, qy1, qz1,
                        FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A, false, true);
            }
        }
        drawIfAny(fill);
        RenderSystem.depthMask(true);

        // ===== 线框（DEBUG_LINES）：已存红框 + 选区青框 =====
        //    ★★ v12：全透视——不受地形遮挡（滤镜仍用真实深度，见上一段）
        RenderSystem.disableDepthTest();
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
            // 线框 0.002 贴边界（旧版外扩 0.12/0.03 会飘在区域外面）
            float ir = aimed ? 1.0F : R2, ig = aimed ? 1.0F : G2, ib = aimed ? 1.0F : B2;
            LevelRenderer.renderLineBox(poseStack, buffer, x0 - FILL_OFFSET, y0 - FILL_OFFSET, z0 - FILL_OFFSET,
                    x1 + FILL_OFFSET, y1 + FILL_OFFSET, z1 + FILL_OFFSET, R1, G1, B1, ALPHA);
            LevelRenderer.renderLineBox(poseStack, buffer, x0, y0, z0, x1, y1, z1, ir, ig, ib, ALPHA);
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
        drawIfAny(buffer);
        RenderSystem.enableDepthTest(); // 恢复真实深度（线框透视结束）

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /**
     * 绘制已构建的缓冲（★★ 2026-09-12 崩溃修复）。
     *
     * <p><b>为何不能直接用 {@code buildOrThrow()} </b>：它在本帧<b>缓冲区为空</b>时抛
     * {@code IllegalStateException: BufferBuilder was empty}。
     * 而「只提交朝向相机的面」在<b>相机位于盒子内部</b>时恰好一个面都不提交（选区扩到包含自己时）
     * → 空缓冲 → 直接崩溃。
     * <p>{@code build()} 在空时返回 {@code null}，且无论是否为空都会正确复位 BufferBuilder 状态
     * （{@code building=false} / {@code vertexPointer=-1}）→ 空时跳过绘制即可。
     */
    private static void drawIfAny(BufferBuilder buf) {
        com.mojang.blaze3d.vertex.MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
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
     * <p>管线开启 {@code enableCull()}，正向绕序从内部看全是背面会被剔除 → 故相机在区内时补画反向皮。
     * <p>⚠️ 必须用 {@link #drawIfAny}；顶点必须带矩阵。
     */
    private static void addBoxFaces(BufferBuilder buf, org.joml.Matrix4f m,
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
        quad(buf, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a, reverse);
        // 上 +Y
        quad(buf, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a, reverse);
        // 北 -Z
        quad(buf, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a, reverse);
        // 南 +Z
        quad(buf, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a, reverse);
        // 西 -X
        quad(buf, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a, reverse);
        // 东 +X
        quad(buf, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a, reverse);
    }

    /**
     * 单个四边形。
     * <p>{@code reverse=true} 时顶点顺序整体倒过来（v3→v0）→ 法线翻转。
     * <p>顶点必须带矩阵：1.21.1 裸 {@code addVertex(x,y,z)} 不带矩阵会画错位置。
     */
    private static void quad(BufferBuilder buf, org.joml.Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a, boolean reverse) {
        if (reverse) {
            buf.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
            buf.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
            buf.addVertex(m, bx, by, bz).setColor(r, g, b, a);
            buf.addVertex(m, ax, ay, az).setColor(r, g, b, a);
            return;
        }
        buf.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        buf.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        buf.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        buf.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }
}
