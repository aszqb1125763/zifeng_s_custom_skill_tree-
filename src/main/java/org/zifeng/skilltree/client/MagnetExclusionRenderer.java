package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.zifeng.skilltree.client.render.WorldRender;
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
 * ⚠️ 渲染管线与宝藏大师(ClientTreasureEvents)同族：只画「半透明六面盒 + 双层线框」，深度测试正常开启（不透视原则延续）。
 * <p>★ 26.1.2：即时模式渲染（手工开关全局混合/深度状态 + 手动缓冲区上传）整体被删除 →
 * 改为向 {@code SubmitNodeCollector} <b>提交几何体</b>，状态由 {@link WorldRender} 里声明好的 RenderType 负责。
 * <p>⚠️ {@link SubmitCustomGeometryEvent} <b>只在主 pass 触发</b>（不经过 Iris 阴影 pass），
 * 所以旧的 {@code IrisCompat.isShadowPass()} 规避判断对本类几何体已不再需要。
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
        String dim = mc.level.dimension().identifier().toString();
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
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        if (IrisCompat.isShadowPass()) {
            return; // ★ 26.1.2：Iris 阴影 pass 会重入 renderLevel 导致本事件再触发，跳过以免污染阴影贴图
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 条件：主手或副手任一为【木棍手势载体】+ 工具开 + RANGE 模块（磁铁已学；仅已学判定，磁铁开关不影响查看/配置）
        // ⚠️ 2026-09-08：副手木棍仅【显示】，框选/删除等交互仍要求主手（输入层未放宽）
        // ★ 2026-10-09：判定改为「木棍本体 或 已链接物品类型」（与输入层保持一致）
        if (!ModKeyBindingEvents.matchesStickGestureClient(mc.player.getMainHandItem())
                && !ModKeyBindingEvents.matchesStickGestureClient(mc.player.getOffhandItem())) {
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
        String dim = mc.level.dimension().identifier().toString();

        // 潜行删除瞄准检测（射线命中高亮）
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        isAiming = mc.player.isShiftKeyDown() && isRayHittingZone(eye, look);

        // ★ 26.1.2：事件给的 pose 是【世界原点】，需自行减相机平移
        var cam = event.getLevelRenderState().cameraRenderState.pos;
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        var collector = event.getSubmitNodeCollector();

        // ===== 滤镜（半透明六面盒）：∂V ± ε 双层皮 =====
        //    ★ 旧版「相机在盒内时另补一次反向绕序」已不需要：WorldRender.boxFaces
        //      每次都会把 6 个面按【正反两套绕序】全部提交，站在区外 / 区内都能看到面。
        final net.minecraft.world.phys.Vec3 fillAimHit = isAiming ? rayFirstHit(eye, look, zones, dim) : null;
        final BlockPos fillFirst = first;
        collector.submitCustomGeometry(poseStack, WorldRender.translucentFill(), (pose, buf) -> {
            for (MagnetExclusionZone z : zones) {
                if (!z.dim().equals(dim)) {
                    continue;
                }
                boolean aimed = fillAimHit != null && hitZoneEquals(fillAimHit, z);
                float fr = aimed ? FILL_AIM_R : FILL_R, fg = aimed ? FILL_AIM_G : FILL_G;
                float fb = aimed ? FILL_AIM_B : FILL_B, fa = aimed ? FILL_AIM_A : FILL_A;
                float bx0 = z.minX(), by0 = z.minY(), bz0 = z.minZ();
                float bx1 = z.maxX() + 1, by1 = z.maxY() + 1, bz1 = z.maxZ() + 1;
                final int color = WorldRender.argb(fr, fg, fb, fa);
                // 外侧皮（∂V+ε）→ 站在区外可见
                WorldRender.boxFaces(buf, pose, bx0 - FILL_OFFSET, by0 - FILL_OFFSET, bz0 - FILL_OFFSET,
                        bx1 + FILL_OFFSET, by1 + FILL_OFFSET, bz1 + FILL_OFFSET, color);
                // 内侧皮（∂V−ε）→ 站在区内可见
                WorldRender.boxFaces(buf, pose, bx0 + FILL_OFFSET, by0 + FILL_OFFSET, bz0 + FILL_OFFSET,
                        bx1 - FILL_OFFSET, by1 - FILL_OFFSET, bz1 - FILL_OFFSET, color);
            }
            if (fillFirst != null) {
                // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则，所见即所得）
                net.minecraft.core.BlockPos fillSecond = previewSecondCorner(mc);
                float qx0 = Math.min(fillFirst.getX(), fillSecond.getX()), qy0 = Math.min(fillFirst.getY(), fillSecond.getY());
                float qz0 = Math.min(fillFirst.getZ(), fillSecond.getZ());
                float qx1 = Math.max(fillFirst.getX(), fillSecond.getX()) + 1, qy1 = Math.max(fillFirst.getY(), fillSecond.getY()) + 1;
                float qz1 = Math.max(fillFirst.getZ(), fillSecond.getZ()) + 1;
                final int previewColor = WorldRender.argb(FILL_PRE_R, FILL_PRE_G, FILL_PRE_B, FILL_PRE_A);
                WorldRender.boxFaces(buf, pose, qx0 - FILL_OFFSET, qy0 - FILL_OFFSET, qz0 - FILL_OFFSET,
                        qx1 + FILL_OFFSET, qy1 + FILL_OFFSET, qz1 + FILL_OFFSET, previewColor);
                WorldRender.boxFaces(buf, pose, qx0 + FILL_OFFSET, qy0 + FILL_OFFSET, qz0 + FILL_OFFSET,
                        qx1 - FILL_OFFSET, qy1 - FILL_OFFSET, qz1 - FILL_OFFSET, previewColor);
            }
        });

        // ===== 线框：已存红框 + 选区青框 =====
        //    ★★ v12：全透视——不受地形遮挡（滤镜仍用真实深度，见上一次提交）
        final net.minecraft.world.phys.Vec3 aimHit = isAiming ? rayFirstHit(eye, look, zones, dim) : null;
        final BlockPos first2 = first;
        collector.submitCustomGeometry(poseStack, WorldRender.seeThroughLines(), (pose, buf) -> {
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
                WorldRender.lineBox(buf, pose, x0 - FILL_OFFSET, y0 - FILL_OFFSET, z0 - FILL_OFFSET,
                        x1 + FILL_OFFSET, y1 + FILL_OFFSET, z1 + FILL_OFFSET,
                        WorldRender.argb(R1, G1, B1, ALPHA), 1.0F);
                WorldRender.lineBox(buf, pose, x0, y0, z0, x1, y1, z1,
                        WorldRender.argb(ir, ig, ib, ALPHA), 1.0F);
            }
            if (first2 != null) {
                // 选区预览：第一角 → 当前角点（按住 Alt = 眼前 1 格；否则 = 左键取角规则）
                net.minecraft.core.BlockPos second = previewSecondCorner(mc);
                float x0 = Math.min(first2.getX(), second.getX());
                float y0 = Math.min(first2.getY(), second.getY());
                float z0 = Math.min(first2.getZ(), second.getZ());
                float x1 = Math.max(first2.getX(), second.getX()) + 1;
                float y1 = Math.max(first2.getY(), second.getY()) + 1;
                float z1 = Math.max(first2.getZ(), second.getZ()) + 1;
                WorldRender.lineBox(buf, pose, x0, y0, z0, x1, y1, z1,
                        WorldRender.argb(P_R, P_G, P_B, ALPHA), 1.0F);
            }
        });

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
}
