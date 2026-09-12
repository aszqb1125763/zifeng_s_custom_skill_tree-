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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.zifeng.skilltree.skill.Skills;

/**
 * 容器绑定瞄准提示（2026-09-08，木棍工具层 BIND 模块）：
 * 条件：手持木棍 + 工具开 + 模式=BIND + 已学任一容器绑定技能（挪移/搬运）。
 * 潜行右键绑定前，视线对准可绑定的容器方块时显示【选框】——
 * 普通容器=黄绿色；AE2 无线访问点=紫色外壳+青色边线（2026-09-12 1.4.1）——
 * 与磁铁屏蔽区（RANGE 红框/红面）样式区分，提示"这是将要绑定的目标"。
 * ⚠️ 复用宝藏大师同款渲染管线（AFTER_BLOCK_ENTITIES 单阶段，Oculus 下也显示）。
 */
public final class BindTargetRenderer {
    private BindTargetRenderer() {
    }

    // 普通容器：黄绿色系（绑定主题：与屏蔽区红/青区分）
    private static final float CONTAINER_OUT_R = 0.95F, CONTAINER_OUT_G = 0.85F, CONTAINER_OUT_B = 0.15F; // 外框：亮金绿
    private static final float CONTAINER_IN_R = 0.5F, CONTAINER_IN_G = 1.0F, CONTAINER_IN_B = 0.3F;        // 内框：鲜绿
    private static final float CONTAINER_FILL_R = 0.4F, CONTAINER_FILL_G = 0.9F, CONTAINER_FILL_B = 0.2F;  // 半透明面

    // AE2 无线访问点：紫外壳 + 青边线（2026-09-12 1.4.1）——与普通容器黄绿一眼区分
    private static final float AE_OUT_R = 0.70F, AE_OUT_G = 0.38F, AE_OUT_B = 1.0F;  // 外框（外壳）：紫
    private static final float AE_IN_R = 0.20F, AE_IN_G = 0.82F, AE_IN_B = 1.0F;     // 内框：青
    private static final float AE_FILL_R = 0.55F, AE_FILL_G = 0.35F, AE_FILL_B = 1.0F;

    private static final float FILL_A = 0.18F;
    private static final float AE_FILL_A = 0.22F;
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

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> GameRenderer.getPositionColorShader());

        float x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
        float x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;

        // 绑定目标类型（2026-09-12 1.4.1）：0=普通容器（黄绿）1=AE 无线访问点（紫外壳 + 青边线）
        boolean ae = ModKeyBindingEvents.getBindTypeClient()
                == org.zifeng.skilltree.compat.Ae2StorageCompat.TYPE_AE;
        float outR = ae ? AE_OUT_R : CONTAINER_OUT_R;
        float outG = ae ? AE_OUT_G : CONTAINER_OUT_G;
        float outB = ae ? AE_OUT_B : CONTAINER_OUT_B;
        float inR = ae ? AE_IN_R : CONTAINER_IN_R;
        float inG = ae ? AE_IN_G : CONTAINER_IN_G;
        float inB = ae ? AE_IN_B : CONTAINER_IN_B;
        float fillR = ae ? AE_FILL_R : CONTAINER_FILL_R;
        float fillG = ae ? AE_FILL_G : CONTAINER_FILL_G;
        float fillB = ae ? AE_FILL_B : CONTAINER_FILL_B;

        // 半透明面（浅浅一层；带 pose 矩阵避免画错位置）
        BufferBuilder fill = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RenderSystem.depthMask(false);
        org.joml.Matrix4f poseMat = poseStack.last().pose();
        addBoxFaceVerts(fill, poseMat, x0, y0, z0, x1, y1, z1, fillR, fillG, fillB, ae ? AE_FILL_A : FILL_A);
        BufferUploader.drawWithShader(fill.buildOrThrow());
        RenderSystem.depthMask(true);

        // 双层线框：外框=外壳（普通金绿 / AE 紫），内框=主线（普通鲜绿 / AE 青）
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        LevelRenderer.renderLineBox(poseStack, buffer, x0 - 0.08F, y0 - 0.08F, z0 - 0.08F,
                x1 + 0.08F, y1 + 0.08F, z1 + 0.08F, outR, outG, outB, ALPHA);
        LevelRenderer.renderLineBox(poseStack, buffer, x0 - 0.02F, y0 - 0.02F, z0 - 0.02F,
                x1 + 0.02F, y1 + 0.02F, z1 + 0.02F, inR, inG, inB, ALPHA);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** 六面半透明盒（1.21.1 需带矩阵 addVertex(matrix,...)，否则顶点画错位置） */
    private static void addBoxFaceVerts(BufferBuilder buf, org.joml.Matrix4f matrix,
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

    /**
     * Iris 光影软检测（无 Iris 时返回 false）：阴影 pass → 跳过渲染。
     * <p>⚠️ 2026-09-11：改用共享 {@link IrisCompat}（修复「未装 Iris 时每帧抛
     * ClassNotFoundException」的问题）。
     */
    private static boolean isIrisShadowPass() {
        return IrisCompat.isShadowPass();
    }
}
