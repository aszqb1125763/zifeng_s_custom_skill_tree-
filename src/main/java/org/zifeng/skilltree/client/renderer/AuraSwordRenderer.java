package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.zifeng.skilltree.client.AuraSwordClientState;
import org.zifeng.skilltree.client.ModKeyBindingEvents;
import org.zifeng.skilltree.client.render.WorldRender;

import java.util.List;

/**
 * 杀戮光环·剑阵 / 飞剑 / 冲击波（26.1.2 移植版，2026-10-21）。
 *
 * <h2>与 1.20.1/1.21.1 的差异</h2>
 * 26.1.2 删除了即时模式渲染（RenderStateShard / BufferUploader / enableBlend 等），
 * item 模型无法像旧版那样用 {@code ItemRenderer.renderStatic} 塞进任意 buffer，
 * 因此这里改用<b>几何四边形拼剑</b>（剑身青色长条 + 剑柄棕色短条 + 护手）。
 * 优点：零 item 渲染开销、批量提交；观感与 1.20.1 的平铺飞剑一致。
 *
 * <p>判定仍由不可见实体 {@code AuraSwordEntity} 负责（服务端），本类只画。
 */
public final class AuraSwordRenderer {

    private static final int SWORD_COUNT = 8;
    private static final float ORBIT_RADIUS = 1.30f;
    private static final float SPIN_PER_SEC = 1.6f;
    private static final float SWORD_SCALE = 1.15f;
    private static final float FLYING_SCALE_FACTOR = 2.2f;
    private static final float BLAST_MAX_RADIUS = 10.0f;
    private static final int RING_SEGMENTS = 48;
    private static final int TRAIL_STEPS = 12;

    private static final ItemStack SWORD = new ItemStack(Items.DIAMOND_SWORD);

    /** 脑后光环贴图（2026-10-22） */
    private static final Identifier HALO_TEX =
            Identifier.fromNamespaceAndPath(org.zifeng.skilltree.SkillTreeMod.MOD_ID,
                    "textures/misc/aura_halo.png");

    private AuraSwordRenderer() {
    }

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ModKeyBindingEvents.isAuraAttackEnabled()) {
            return;
        }
        if (org.zifeng.skilltree.client.IrisCompat.isShadowPass()) {
            return; // 光影阴影 pass 跳过
        }
        final PoseStack pose = event.getPoseStack();
        final Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        final var player = mc.player;
        final float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        final double px = Mth.lerp((double) partial, player.xo, player.getX());
        final double py = Mth.lerp((double) partial, player.yo, player.getY());
        final double pz = Mth.lerp((double) partial, player.zo, player.getZ());
        final float seconds = (System.currentTimeMillis() % 600_000L) / 1000.0f;
        final List<AuraSwordClientState.Launch> flying = AuraSwordClientState.launches();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        // ===== 剑阵：脑后竖直圆环（2026-10-22） =====
        final float yawRad = (float) Math.toRadians(player.getYRot());
        final Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        final Vec3 rightDir = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
        final Vec3 arrayCenter = new Vec3(px, py + player.getEyeHeight() + 0.05, pz)
                .add(forward.scale(-0.55));
        for (int i = 0; i < SWORD_COUNT; i++) {
            if (i < flying.size()) {
                continue;
            }
            final float angle = (float) (i * (Math.PI * 2.0 / SWORD_COUNT) + seconds * SPIN_PER_SEC);
            final Vec3 radial = rightDir.scale(Math.cos(angle)).add(0.0, Math.sin(angle), 0.0).normalize();
            final Vec3 pos = arrayCenter.add(radial.scale(ORBIT_RADIUS));
            drawSwordOriented(mc, pose, event.getSubmitNodeCollector(),
                    (float) pos.x, (float) pos.y, (float) pos.z, radial, forward, SWORD_SCALE);
        }

        // ===== 飞剑 + 拖尾 =====
        for (final AuraSwordClientState.Launch launch : flying) {
            final float prog = AuraSwordClientState.launchProgress(launch);
            if (prog < 0.0f) {
                continue;
            }
            final Vec3 s0 = launch.start();
            final Vec3 s1 = launch.target();
            final Vec3 ctrl = controlPoint(s0, s1);
            // ★ 起步慢、越飞越快
            // 明显的速度增长曲线
            final float e = (float) Math.pow(prog, 2.4);
            final Vec3 pos = bezier(s0, ctrl, s1, e);
            final Vec3 tan = bezierTangent(s0, ctrl, s1, e);
            // ★ 飞行中渐渐变大
            final float scale = SWORD_SCALE * (1.15f + 1.85f * prog);
            final float trailUpTo = e;
            event.getSubmitNodeCollector().submitCustomGeometry(pose, WorldRender.translucentFill(),
                    (p, buf) -> drawTrail(buf, p, s0, ctrl, s1, trailUpTo, seconds));
            drawSword(mc, pose, event.getSubmitNodeCollector(), (float) pos.x, (float) pos.y, (float) pos.z, tan, scale);
        }

        // ===== 冲击波 + 脚下常驻环 =====
        event.getSubmitNodeCollector().submitCustomGeometry(pose, WorldRender.translucentFill(),
                (p, buf) -> {
                    for (final AuraSwordClientState.Blast blast : AuraSwordClientState.blasts()) {
                        final float pb = AuraSwordClientState.blastProgress(blast);
                        if (pb < 0.0f) {
                            continue;
                        }
                        final float wave = 1.0f - (float) Math.pow(1.0f - pb, 3.0);
                        final float inner = 0.4f + wave * (BLAST_MAX_RADIUS - 0.4f);
                        final float thickness = 0.25f + wave * 1.95f;
                        final int alpha = (int) (220 * (1.0f - wave * 0.85f));
                        if (alpha > 0) {
                            // ★ 与 1.20.1/1.21.1 同步：白青核心 + 青色主环 + 紫色外晕
                            final float bx = (float) blast.center().x;
                            final float by = (float) (blast.center().y + 0.18);
                            final float bz = (float) blast.center().z;
                            drawRing(buf, p, bx, by, bz, inner, inner + thickness * 2.1f,
                                    WorldRender.argb(0.59f, 0.35f, 1.0f, alpha / 255.0f * 0.30f));
                            drawRing(buf, p, bx, by, bz, inner, inner + thickness,
                                    WorldRender.argb(0.35f, 0.88f, 1.0f, alpha / 255.0f));
                            drawRing(buf, p, bx, by, bz, inner, inner + thickness * 0.22f,
                                    WorldRender.argb(0.92f, 1.0f, 1.0f, alpha / 255.0f));
                        }
                    }
                    // ★ 2026-10-22 用户要求：删掉脚下光环
                    // ★ 2026-10-22：脑后紫色竖直光环（剑阵底座）
                    final float haloYaw = (float) Math.toRadians(mc.player.getYRot());
                    final Vec3 haloRight = new Vec3(Math.cos(haloYaw), 0.0, Math.sin(haloYaw));
                    final Vec3 haloFwd = new Vec3(-Math.sin(haloYaw), 0.0, Math.cos(haloYaw));
                    final Vec3 haloCenter = new Vec3(px, py + mc.player.getEyeHeight() + 0.05, pz)
                            .add(haloFwd.scale(-0.55));
                });

        // ★ 2026-10-22：脑后贴图光环（旋转，替代几何环）
        final float haloHalf = ORBIT_RADIUS / 0.6875f;
        final Vec3 haloCenter = new Vec3(px, py + player.getEyeHeight() + 0.05, pz)
                .add(forward.scale(-0.55));
        event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(HALO_TEX),
                (haloPose, buf) -> drawHaloQuad(buf, haloPose, haloCenter, rightDir, haloHalf, seconds));

        pose.popPose();
    }

    /**
     * 用「物品模型」画一把钻石剑（26.1.2 的正规做法）。
     *
     * <p>26.1.2 删除了即时模式渲染，但保留了 {@code ItemStackRenderState.submit(...)} 这条提交路径：
     * 先用 {@code ItemModelResolver.updateForTopItem} 解析模型，再按正交基矩阵摆姿态，
     * 最后交给 {@code SubmitNodeCollector} 统一绘制 —— 效果与 1.21.1 的 {@code renderStatic} 一致。
     */
    private static void drawSword(Minecraft mc, PoseStack pose, SubmitNodeCollector collector,
                                  float x, float y, float z, Vec3 dir, float scale) {
        drawSwordOriented(mc, pose, collector, x, y, z, dir, new Vec3(0.0, 1.0, 0.0), scale);
    }

    /** 剑尖朝 dir、剑面尽量朝 normalHint（脑后剑阵用法线=玩家前方） */
    private static void drawSwordOriented(Minecraft mc, PoseStack pose, SubmitNodeCollector collector,
                                          float x, float y, float z, Vec3 dir, Vec3 normalHint, float scale) {
        final ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, SWORD, ItemDisplayContext.FIXED,
                mc.level, mc.player, 0);
        if (state.isEmpty()) {
            return;
        }
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
        Vec3 normal = normalHint.subtract(d.scale(normalHint.dot(d)));
        if (normal.lengthSqr() < 1.0E-6) {
            normal = new Vec3(1.0, 0.0, 0.0).subtract(d.scale(d.x));
        }
        normal = normal.normalize();
        Vec3 side = d.cross(normal).normalize();

        pose.pushPose();
        pose.translate(x, y, z);
        // 正交基：col0=side，col1=刀尖方向，col2=面法线（列主序！）
        final org.joml.Matrix4f basis = new org.joml.Matrix4f(
                (float) side.x, (float) side.y, (float) side.z, 0.0f,
                (float) d.x, (float) d.y, (float) d.z, 0.0f,
                (float) normal.x, (float) normal.y, (float) normal.z, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f);
        basis.mul(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(-45.0f)));
        pose.last().pose().mul(basis);
        pose.scale(scale, scale, scale);
        state.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** 绕圈渐变环带：紫 ↔ 青（26.1.2 版，逐段换色） */
    private static void drawRingGradient(VertexConsumer buf, PoseStack.Pose pose,
                                         float cx, float cy, float cz, float inner, float outer,
                                         float alpha, float phase) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            final double a0 = i * (Math.PI * 2.0 / RING_SEGMENTS);
            final double a1 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);
            final float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            final float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            final float t = (float) (i / (double) RING_SEGMENTS);
            final float mix = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0 - phase * 1.4));
            final float r = 0.59f + (0.35f - 0.59f) * mix;
            final float g = 0.35f + (0.80f - 0.35f) * mix;
            final int argb = WorldRender.argb(r, g, 1.0f, alpha);
            WorldRender.quad(buf, pose, argb,
                    cx + c0 * inner, cy, cz + s0 * inner,
                    cx + c0 * outer, cy, cz + s0 * outer,
                    cx + c1 * outer, cy, cz + s1 * outer,
                    cx + c1 * inner, cy, cz + s1 * inner);
            WorldRender.quad(buf, pose, argb,
                    cx + c1 * inner, cy, cz + s1 * inner,
                    cx + c1 * outer, cy, cz + s1 * outer,
                    cx + c0 * outer, cy, cz + s0 * outer,
                    cx + c0 * inner, cy, cz + s0 * inner);
        }
    }

    /** 脑后光环（26.1.2）：多色循环渐变 + 径向 3 层虚化（双面提交） */
    private static void drawVerticalRing(VertexConsumer buf, PoseStack.Pose pose,
                                         Vec3 center, Vec3 right, float radius, float thickness,
                                         int baseAlpha, float time) {
        final int layers = 3;
        for (int layer = 0; layer < layers; layer++) {
            final float rIn = radius - thickness * 0.5f + layer * thickness / layers;
            final float rOut = rIn + thickness / layers;
            final float a0f = baseAlpha / 255.0f * (1.0f - layer * 0.32f);
            for (int i = 0; i < RING_SEGMENTS; i++) {
                final double a0 = i * (Math.PI * 2.0 / RING_SEGMENTS);
                final double a1 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);
                final float t = (float) (i / (double) RING_SEGMENTS + time * 0.03);
                final float r = 0.59f + 0.41f * (float) Math.sin(t * Math.PI * 2.0);
                final float g = 0.59f + 0.41f * (float) Math.sin(t * Math.PI * 2.0 + 2.09);
                final float b = 0.59f + 0.41f * (float) Math.sin(t * Math.PI * 2.0 + 4.19);
                final int argb = WorldRender.argb(r, g, b, a0f);
                final int argbOut = WorldRender.argb(r, g, b, Math.max(0.0f, a0f - 0.18f));
                final float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                final float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                WorldRender.quad(buf, pose, argb,
                        (float) (center.x + right.x * c0 * rIn), (float) (center.y + s0 * rIn),
                        (float) (center.z + right.z * c0 * rIn),
                        (float) (center.x + right.x * c0 * rOut), (float) (center.y + s0 * rOut),
                        (float) (center.z + right.z * c0 * rOut),
                        (float) (center.x + right.x * c1 * rOut), (float) (center.y + s1 * rOut),
                        (float) (center.z + right.z * c1 * rOut),
                        (float) (center.x + right.x * c1 * rIn), (float) (center.y + s1 * rIn),
                        (float) (center.z + right.z * c1 * rIn));
                WorldRender.quad(buf, pose, argb,
                        (float) (center.x + right.x * c1 * rIn), (float) (center.y + s1 * rIn),
                        (float) (center.z + right.z * c1 * rIn),
                        (float) (center.x + right.x * c1 * rOut), (float) (center.y + s1 * rOut),
                        (float) (center.z + right.z * c1 * rOut),
                        (float) (center.x + right.x * c0 * rOut), (float) (center.y + s0 * rOut),
                        (float) (center.z + right.z * c0 * rOut),
                        (float) (center.x + right.x * c0 * rIn), (float) (center.y + s0 * rIn),
                        (float) (center.z + right.z * c0 * rIn));
            }
        }
    }

    /** 脑后贴图光环（竖直面，绕法线旋转） */
    private static void drawHaloQuad(VertexConsumer buf, PoseStack.Pose pose,
                                     Vec3 center, Vec3 right, float half, float time) {
        final Vec3 up = new Vec3(0.0, 1.0, 0.0);
        final float ang = time * 0.8f;
        final float ca = (float) Math.cos(ang);
        final float sa = (float) Math.sin(ang);
        final Vec3 r = right.scale(ca).add(up.scale(sa)).scale(half);
        final Vec3 u = up.scale(ca).subtract(right.scale(sa)).scale(half);
        texVertex(buf, pose, center.subtract(r).subtract(u), 0.0f, 1.0f);
        texVertex(buf, pose, center.add(r).subtract(u), 1.0f, 1.0f);
        texVertex(buf, pose, center.add(r).add(u), 1.0f, 0.0f);
        texVertex(buf, pose, center.subtract(r).add(u), 0.0f, 0.0f);
    }

    /** 写一个贴图顶点（ENTITY 格式） */
    private static void texVertex(VertexConsumer buf, PoseStack.Pose pose, Vec3 p, float u, float v) {
        buf.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setUv(u, v)
                .setColor(0xFFFFFFFF)
                .setLight(0xF000F0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    /** 冲击波环带 */
    private static void drawRing(VertexConsumer buf, PoseStack.Pose pose,
                                 float cx, float cy, float cz, float inner, float outer, int argb) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            final double a0 = i * (Math.PI * 2.0 / RING_SEGMENTS);
            final double a1 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);
            final float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            final float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // ⚠️ 26.1.2 的 debugFilledBox 管线有背面剔除：正反两套绕序各提交一次，否则从上方看会被剔掉
            WorldRender.quad(buf, pose, argb,
                    cx + c0 * inner, cy, cz + s0 * inner,
                    cx + c0 * outer, cy, cz + s0 * outer,
                    cx + c1 * outer, cy, cz + s1 * outer,
                    cx + c1 * inner, cy, cz + s1 * inner);
            WorldRender.quad(buf, pose, argb,
                    cx + c1 * inner, cy, cz + s1 * inner,
                    cx + c1 * outer, cy, cz + s1 * outer,
                    cx + c0 * outer, cy, cz + s0 * outer,
                    cx + c0 * inner, cy, cz + s0 * inner);
        }
    }

    /** 拖尾·流光：尾部暗细 → 剑附近亮宽 + 流动条纹（26.1.2 版，逐段 argb + 双面提交） */
    private static void drawTrail(VertexConsumer buf, PoseStack.Pose pose,
                                  Vec3 s0, Vec3 c, Vec3 s1, float upTo, float time) {
        if (upTo <= 0.02f) {
            return;
        }
        Vec3 prevA = null;
        Vec3 prevB = null;
        int prevArgb = 0;
        for (int k = 0; k <= TRAIL_STEPS; k++) {
            final float t = upTo * k / TRAIL_STEPS;
            final Vec3 p = bezier(s0, c, s1, t);
            final Vec3 d = bezierTangent(s0, c, s1, t);
            Vec3 perp = new Vec3(-d.z, 0.0, d.x);
            if (perp.lengthSqr() < 1.0E-6) {
                perp = new Vec3(1.0, 0.0, 0.0);
            }
            final float kt = (float) k / TRAIL_STEPS;
            perp = perp.normalize().scale(0.015 + 0.12 * (float) Math.sin(Math.PI * kt));
            final Vec3 a = p.subtract(perp);
            final Vec3 b = p.add(perp);

            final float bright = 0.30f + 0.70f * kt;
            final float flow = 0.62f + 0.38f * (float) Math.sin(t * 16.0 - time * 10.0);
            final int argb = WorldRender.argb((40 + 175 * bright) / 255f, (150 + 105 * bright) / 255f,
                    1.0f, Math.min(1.0f, bright * flow));

            if (prevA != null) {
                WorldRender.quad(buf, pose, prevArgb,
                        (float) prevA.x, (float) prevA.y, (float) prevA.z,
                        (float) a.x, (float) a.y, (float) a.z,
                        (float) b.x, (float) b.y, (float) b.z,
                        (float) prevB.x, (float) prevB.y, (float) prevB.z);
                WorldRender.quad(buf, pose, prevArgb,
                        (float) prevB.x, (float) prevB.y, (float) prevB.z,
                        (float) b.x, (float) b.y, (float) b.z,
                        (float) a.x, (float) a.y, (float) a.z,
                        (float) prevA.x, (float) prevA.y, (float) prevA.z);
            }
            prevA = a;
            prevB = b;
            prevArgb = argb;
        }
    }

    private static Vec3 controlPoint(Vec3 start, Vec3 end) {
        final Vec3 d = end.subtract(start);
        final double len = Math.max(0.8, d.length());
        Vec3 dir = d.normalize();
        Vec3 perp = new Vec3(-dir.z, 0.0, dir.x);
        if (perp.lengthSqr() < 1.0E-6) {
            perp = new Vec3(1.0, 0.0, 0.0);
        }
        perp = perp.normalize();
        return start.add(end).scale(0.5).add(perp.scale(Math.min(2.4, len * 0.38))).add(0.0, 0.4, 0.0);
    }

    private static Vec3 bezier(Vec3 s0, Vec3 c, Vec3 s1, float t) {
        final float u = 1.0f - t;
        return s0.scale(u * u).add(c.scale(2.0f * u * t)).add(s1.scale(t * t));
    }

    private static Vec3 bezierTangent(Vec3 s0, Vec3 c, Vec3 s1, float t) {
        return c.subtract(s0).scale(2.0f * (1.0f - t)).add(s1.subtract(c).scale(2.0f * t));
    }

    private static float groundHeightAt(net.minecraft.world.level.Level level,
                                        double x, double playerFeetY, double z) {
        // ★ 2026-10-22 修「洞穴里剑阵消失」：getHeight(MOTION_BLOCKING) 返回地表高度，
        //   洞穴里会把剑阵画到地表上方。直接以玩家脚底为基准。
        return (float) playerFeetY + 0.18f;
    }
}
