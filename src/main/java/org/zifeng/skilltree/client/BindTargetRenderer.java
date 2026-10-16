package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.zifeng.skilltree.client.render.WorldRender;
import org.zifeng.skilltree.skill.Skills;

/**
 * 容器绑定瞄准提示（2026-09-08，木棍工具层 BIND 模块）：
 * 条件：手持木棍 + 工具开 + 模式=BIND + 已学任一容器绑定技能（挪移/搬运）。
 * 潜行右键绑定前，视线对准可绑定的容器方块时显示【选框】——
 * 普通容器=黄绿色；AE2 无线访问点=紫色外壳+青色边线（2026-09-12 1.4.1）——
 * 与磁铁屏蔽区（RANGE 红框/红面）样式区分，提示"这是将要绑定的目标"。
 * <p>★ 26.1.2：即时模式渲染（手工开关全局混合/剔除状态 + 手动缓冲区上传）整体被删除 →
 * 改为在 {@link SubmitCustomGeometryEvent} 里向 {@code SubmitNodeCollector} <b>提交几何体</b>，
 * 状态由 {@link WorldRender} 里声明好的 RenderType 负责。
 * ⚠️ {@link SubmitCustomGeometryEvent} <b>只在主 pass 触发</b>（不经过 Iris 阴影 pass），
 * 所以旧的 {@code IrisCompat.isShadowPass()} 规避判断已不再需要。
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
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        if (IrisCompat.isShadowPass()) {
            return; // ★ 26.1.2：Iris 阴影 pass 会重入 renderLevel 导致本事件再触发，跳过以免污染阴影贴图
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // ★ 2026-10-09：主手为木棍本体 或 已链接物品类型（与输入层保持一致）
        if (!ModKeyBindingEvents.matchesStickGestureClient(mc.player.getMainHandItem())) {
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
        String curDim = mc.level.dimension().identifier().toString();
        if (!bindDim.equals(curDim)) {
            return;
        }
        BlockPos pos = new BlockPos(ModKeyBindingEvents.getBindXClient(),
                ModKeyBindingEvents.getBindYClient(), ModKeyBindingEvents.getBindZClient());
        // ★ 26.1.2：事件给的 pose 是【世界原点】，需自行减相机平移
        var cam = event.getLevelRenderState().cameraRenderState.pos;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        var collector = event.getSubmitNodeCollector();

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

        // 半透明面（浅浅一层）
        //    ★ WorldRender.boxFaces 自带【正反两套绕序】→ 不需要再手工 disableCull。
        final float fx0 = x0, fy0 = y0, fz0 = z0, fx1 = x1, fy1 = y1, fz1 = z1;
        final float fR = fillR, fG = fillG, fB = fillB, fA = ae ? AE_FILL_A : FILL_A;
        collector.submitCustomGeometry(poseStack, WorldRender.translucentFill(), (pose, buf) -> {
            WorldRender.boxFaces(buf, pose, fx0, fy0, fz0, fx1, fy1, fz1, WorldRender.argb(fR, fG, fB, fA));
        });

        // 双层线框：外框=外壳（普通金绿 / AE 紫），内框=主线（普通鲜绿 / AE 青）
        //    普通深度（真实遮挡）——与旧实现的语义一致（旧版此处未关深度测试）
        final int outerColor = WorldRender.argb(outR, outG, outB, ALPHA);
        final int innerColor = WorldRender.argb(inR, inG, inB, ALPHA);
        collector.submitCustomGeometry(poseStack, WorldRender.normalLines(), (pose, buf) -> {
            WorldRender.lineBox(buf, pose, fx0 - 0.08F, fy0 - 0.08F, fz0 - 0.08F,
                    fx1 + 0.08F, fy1 + 0.08F, fz1 + 0.08F, outerColor, 1.0F);
            WorldRender.lineBox(buf, pose, fx0 - 0.02F, fy0 - 0.02F, fz0 - 0.02F,
                    fx1 + 0.02F, fy1 + 0.02F, fz1 + 0.02F, innerColor, 1.0F);
        });

        poseStack.popPose();
    }
}
