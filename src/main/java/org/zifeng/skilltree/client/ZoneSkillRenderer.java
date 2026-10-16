package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.zifeng.skilltree.client.render.WorldRender;
import org.zifeng.skilltree.data.OperZone;
import org.zifeng.skilltree.skill.Skills;

/**
 * 机械共鸣·操作区 渲染（2026-09-08，木棍工具模式 2-5）：
 * 手持木棍 + 当前模式对应技能已学 时显示：
 * <ul>
 *   <li>该技能已框选的操作区：金色双线框 + 半透明填充（仅当前维度）</li>
 *   <li>选区进行中（已选第一角）：第一角 → 当前角点 的青色预览框</li>
 * </ul>
 * ⚠️ 渲染管线与 MagnetExclusionRenderer 完全同款。本文件为独立复制（磁铁渲染器内部逻辑高度内聚，不宜耦合）。
 * <p>★ 26.1.2：旧的即时模式渲染（手工开关全局混合/深度状态 + 手动缓冲区上传、
 * 原版的线框辅助方法）整套被删除，改为向 {@code SubmitNodeCollector} <b>提交几何体</b>，
 * 状态（混合 / 剔除 / 深度）由 {@link WorldRender} 里声明好的 RenderType 负责。
 * <p>⚠️ {@link SubmitCustomGeometryEvent} <b>只在主 pass 触发</b>（不经过 Iris 阴影 pass），
 * 所以旧的 {@code IrisCompat.isShadowPass()} 规避判断对本类几何体已不再需要。
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
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        if (IrisCompat.isShadowPass()) {
            return; // ★ 26.1.2：Iris 阴影 pass 会重入 renderLevel 导致本事件再触发，跳过以免污染阴影贴图
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 条件：主手或副手任一为【木棍手势载体】+ 当前模式为机械共鸣区（2-5）且对应技能已学
        // ⚠️ 2026-09-08：副手木棍仅【显示】，框选/删除等交互仍要求主手（输入层未放宽）
        // ★ 2026-10-09：判定改为「木棍本体 或 已链接物品类型」（与输入层保持一致）
        if (!ModKeyBindingEvents.matchesStickGestureClient(mc.player.getMainHandItem())
                && !ModKeyBindingEvents.matchesStickGestureClient(mc.player.getOffhandItem())) {
            return;
        }
        String skill = ZoneSelectionInputHandler.zoneSkillOfCurrentMode();
        if (skill == null || !ZoneSelectionInputHandler.isZoneModuleActiveClient()) {
            return;
        }
        // 当前模式展示区：防护/方块保护 = 多块全画（不限数量）；其余技能 = 单块
        java.util.List<OperZone> zones = new java.util.ArrayList<>();
        if (Skills.MACHINE_ZONE_PROTECT.equals(skill)) {
            zones.addAll(ModKeyBindingEvents.getProtectZonesClient());
        } else if (Skills.MINING_PROTECT.equals(skill)) {
            // ★ 2026-10-11 方块保护：多块，不限数量
            zones.addAll(ModKeyBindingEvents.getMiningProtectZonesClient());
        } else {
            OperZone single = ModKeyBindingEvents.getOperZoneClient(skill);
            if (single != null) {
                zones.add(single);
            }
        }
        BlockPos first = ZoneExclusionClientState.getFirstCorner();
        boolean any = false;
        String dim = mc.level.dimension().identifier().toString();
        for (OperZone z : zones) {
            if (z.dim().equals(dim)) {
                any = true;
                break;
            }
        }
        if (!any && first == null) {
            return;
        }

        // ★ 26.1.2：事件给的 pose 是【世界原点】，需自行减相机平移
        var cam = event.getLevelRenderState().cameraRenderState.pos;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        var collector = event.getSubmitNodeCollector();

        // ===== 滤镜（半透明六面盒）：∂V ± ε 双层皮 =====
        //    ★ 旧版「相机在盒内时另补一次反向绕序」已不需要：WorldRender.boxFaces
        //      每次都会把 6 个面按【正反两套绕序】全部提交，站在选区外 / 区内都能看到面。
        final BlockPos previewFirst = first;
        collector.submitCustomGeometry(poseStack, WorldRender.translucentFill(), (pose, buf) -> {
            final int fillColor = WorldRender.argb(FILL_R, FILL_G, FILL_B, FILL_A);
            for (OperZone zone : zones) {
                if (!zone.dim().equals(dim)) {
                    continue;
                }
                float bx0 = zone.minX(), by0 = zone.minY(), bz0 = zone.minZ();
                float bx1 = zone.maxX() + 1, by1 = zone.maxY() + 1, bz1 = zone.maxZ() + 1;
                // 外侧皮（∂V+ε）→ 站在选区外面可见
                WorldRender.boxFaces(buf, pose, bx0 - FILL_OFFSET, by0 - FILL_OFFSET, bz0 - FILL_OFFSET,
                        bx1 + FILL_OFFSET, by1 + FILL_OFFSET, bz1 + FILL_OFFSET, fillColor);
                // 内侧皮（∂V−ε）→ 站在选区里面可见
                WorldRender.boxFaces(buf, pose, bx0 + FILL_OFFSET, by0 + FILL_OFFSET, bz0 + FILL_OFFSET,
                        bx1 - FILL_OFFSET, by1 - FILL_OFFSET, bz1 - FILL_OFFSET, fillColor);
            }
            if (previewFirst != null) {
                BlockPos second = previewSecondCorner(mc);
                float px0 = Math.min(previewFirst.getX(), second.getX()), py0 = Math.min(previewFirst.getY(), second.getY());
                float pz0 = Math.min(previewFirst.getZ(), second.getZ());
                float px1 = Math.max(previewFirst.getX(), second.getX()) + 1, py1 = Math.max(previewFirst.getY(), second.getY()) + 1;
                float pz1 = Math.max(previewFirst.getZ(), second.getZ()) + 1;
                final int previewColor = WorldRender.argb(PRE_R, PRE_G, PRE_B, PRE_A);
                WorldRender.boxFaces(buf, pose, px0 - FILL_OFFSET, py0 - FILL_OFFSET, pz0 - FILL_OFFSET,
                        px1 + FILL_OFFSET, py1 + FILL_OFFSET, pz1 + FILL_OFFSET, previewColor);
                WorldRender.boxFaces(buf, pose, px0 + FILL_OFFSET, py0 + FILL_OFFSET, pz0 + FILL_OFFSET,
                        px1 - FILL_OFFSET, py1 - FILL_OFFSET, pz1 - FILL_OFFSET, previewColor);
            }
        });

        // ===== 线框（★★ v12：全透视——不受地形遮挡，隔着山也能看到边界） =====
        //    ⚠️ 滤镜用真实深度（上一次提交），只有线框换成「深度恒通过」的 seeThroughLines 管线。
        collector.submitCustomGeometry(poseStack, WorldRender.seeThroughLines(), (pose, buf) -> {
            final int outerColor = WorldRender.argb(R1, G1, B1, 1.0F);
            final int innerColor = WorldRender.argb(R2, G2, B2, 1.0F);
            for (OperZone zone : zones) {
                if (!zone.dim().equals(dim)) {
                    continue;
                }
                float x0 = zone.minX(), y0 = zone.minY(), z0 = zone.minZ();
                float x1 = zone.maxX() + 1, y1 = zone.maxY() + 1, z1 = zone.maxZ() + 1;
                // 线框 0.002 贴边界（旧版外扩 0.12/0.03 会飘在选区外面）
                WorldRender.lineBox(buf, pose, x0 - FILL_OFFSET, y0 - FILL_OFFSET, z0 - FILL_OFFSET,
                        x1 + FILL_OFFSET, y1 + FILL_OFFSET, z1 + FILL_OFFSET, outerColor, 1.0F);
                WorldRender.lineBox(buf, pose, x0, y0, z0, x1, y1, z1, innerColor, 1.0F);
            }
            if (previewFirst != null) {
                BlockPos second = previewSecondCorner(mc);
                WorldRender.lineBox(buf, pose,
                        Math.min(previewFirst.getX(), second.getX()), Math.min(previewFirst.getY(), second.getY()),
                        Math.min(previewFirst.getZ(), second.getZ()),
                        Math.max(previewFirst.getX(), second.getX()) + 1, Math.max(previewFirst.getY(), second.getY()) + 1,
                        Math.max(previewFirst.getZ(), second.getZ()) + 1,
                        WorldRender.argb(P_R, P_G, P_B, 1.0F), 1.0F);
            }
        });

        poseStack.popPose();
    }

    /** 预览框第二角：按住 Alt → 眼前 1 格；未按 Alt → 左键取角规则（所见即所得） */
    private static BlockPos previewSecondCorner(Minecraft mc) {
        return MagnetExclusionInputHandler.isAltDown()
                ? MagnetExclusionInputHandler.cornerInFront(mc)
                : MagnetExclusionInputHandler.pickCorner();
    }
}

