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
        // 滤镜启用背面剔除（朝外绕序 → 外侧视角只画 3 面）
        RenderSystem.enableCull();
        // ★★ v10 关键修复：**显式开启真实深度测试**。
        //    旧版从未设置过深度测试 —— 依赖本帧前面代码的遗留状态；
        //    若之前有 disableDepthTest() 未还原，线框/滤镜就会**穿透山体**显示。
        //    现在明确开启：被方块/山体挡住的部分一律不画（"滤镜不透山"）。
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        // ===== 滤镜（半透明六面盒）：外侧视角用朝外绕序双皮 =====
        //    ★ 相机在盒内时另补一次【反向绕序】的内侧皮 ——
        //    否则朝外绕序的面从内部看全是背面 → 被剔除 → 只能看到线框。
        RenderSystem.depthMask(false);
        BufferBuilder fill = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            float bx0 = zone.minX(), by0 = zone.minY(), bz0 = zone.minZ();
            float bx1 = zone.maxX() + 1, by1 = zone.maxY() + 1, bz1 = zone.maxZ() + 1;
            // 朝外绕序双皮 → 服务【外侧】视角
            addBoxFaces(fill, poseMat, bx0, by0, bz0, bx1, by1, bz1,
                    FILL_R, FILL_G, FILL_B, FILL_A, true, false);
            addBoxFaces(fill, poseMat, bx0, by0, bz0, bx1, by1, bz1,
                    FILL_R, FILL_G, FILL_B, FILL_A, false, false);
            if (camX >= bx0 && camX <= bx1 && camY >= by0 && camY <= by1 && camZ >= bz0 && camZ <= bz1) {
                addBoxFaces(fill, poseMat, bx0, by0, bz0, bx1, by1, bz1,
                        FILL_R, FILL_G, FILL_B, FILL_A, false, true);
            }
        }
        if (first != null) {
            BlockPos second = previewSecondCorner(mc);
            float px0 = Math.min(first.getX(), second.getX()), py0 = Math.min(first.getY(), second.getY());
            float pz0 = Math.min(first.getZ(), second.getZ());
            float px1 = Math.max(first.getX(), second.getX()) + 1, py1 = Math.max(first.getY(), second.getY()) + 1;
            float pz1 = Math.max(first.getZ(), second.getZ()) + 1;
            addBoxFaces(fill, poseMat, px0, py0, pz0, px1, py1, pz1, PRE_R, PRE_G, PRE_B, PRE_A, true, false);
            addBoxFaces(fill, poseMat, px0, py0, pz0, px1, py1, pz1, PRE_R, PRE_G, PRE_B, PRE_A, false, false);
            if (camX >= px0 && camX <= px1 && camY >= py0 && camY <= py1 && camZ >= pz0 && camZ <= pz1) {
                addBoxFaces(fill, poseMat, px0, py0, pz0, px1, py1, pz1, PRE_R, PRE_G, PRE_B, PRE_A, false, true);
            }
        }
        drawIfAny(fill);
        RenderSystem.depthMask(true);

        // ===== 线框（★★ v12：全透视——不受地形遮挡，隔着山也能看到边界） =====
        //    ⚠️ 滤镜仍用真实深度（上面那段），这里单独关掉 → 只影响线框。
        RenderSystem.disableDepthTest();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (OperZone zone : zones) {
            if (!zone.dim().equals(dim)) {
                continue;
            }
            float x0 = zone.minX(), y0 = zone.minY(), z0 = zone.minZ();
            float x1 = zone.maxX() + 1, y1 = zone.maxY() + 1, z1 = zone.maxZ() + 1;
            // 线框 0.002 贴边界（旧版外扩 0.12/0.03 会飘在选区外面）
            LevelRenderer.renderLineBox(poseStack, buffer, x0 - FILL_OFFSET, y0 - FILL_OFFSET, z0 - FILL_OFFSET,
                    x1 + FILL_OFFSET, y1 + FILL_OFFSET, z1 + FILL_OFFSET, R1, G1, B1, 1.0F);
            LevelRenderer.renderLineBox(poseStack, buffer, x0, y0, z0, x1, y1, z1, R2, G2, B2, 1.0F);
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
     * <p><b>为何需要 reverse</b>：管线开启 {@code enableCull()}（背面剔除）。
     * 正向绕序的面从盒子内部看全是背面 → 被剔除 → 站在选区内只能看到线框。
     * 所以相机在选区内时额外画一次反向绕序的内侧皮。
     *
     * <p><b>绕序基准</b>：学自 Building Gadgets {@code BaseRenderer#renderBoxSolid}
     * ——「从盒子外部看逆时针(CCW)」；本文件已逐面叉积验证法线朝外
     * （下=-Y、上=+Y、北=-Z、南=+Z、西=-X、东=+X）。
     *
     * <p>⚠️ 必须用 {@link #drawIfAny}（不能用 buildOrThrow：可能一帧一个面都没有）。
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
