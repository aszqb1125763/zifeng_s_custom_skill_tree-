package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.zifeng.skilltree.client.AuraSwordClientState;
import org.zifeng.skilltree.client.ModKeyBindingEvents;

import java.util.List;

/**
 * 杀戮光环·剑阵 / 飞剑 / 爆炸光环（2026-10-21，A 方案 + 多目标）。
 *
 * <h2>分工</h2>
 * <ul>
 *   <li><b>判定</b>：服务端不可见实体 {@code AuraSwordEntity}（每把飞剑锁定一个目标，最多 8 把）；</li>
 *   <li><b>画面</b>：本类负责剑阵、多把飞剑（贝塞尔 + 拖尾）、多个爆炸红环。</li>
 * </ul>
 */
public final class AuraSwordRenderer {

    /** 剑数量 */
    private static final int SWORD_COUNT = 8;
    /** 待机环绕半径（格） */
    private static final float ORBIT_RADIUS = 1.30f;
    /** 环绕角速度（弧度/秒） */
    private static final float SPIN_PER_SEC = 1.6f;
    /** 剑阵缩放 */
    private static final float SWORD_SCALE = 1.15f;
    /** 飞行中的剑缩放倍数（用户：飞出去的剑要更大） */
    private static final float FLYING_SCALE_FACTOR = 2.2f;
    /** 红环最大半径（格） */
    private static final float BLAST_MAX_RADIUS = 10.0f;
    /** 冲击环分段数 */
    private static final int RING_SEGMENTS = 48;
    /** 拖尾分段数 */
    private static final int TRAIL_STEPS = 12;

    private static final ItemStack SWORD = new ItemStack(Items.DIAMOND_SWORD);

    /** 1.20.1：RenderStateShard 的常量是 protected，需在子类里复制暴露 */
    private static final class ShardAccess extends RenderStateShard {
        private ShardAccess() {
            super("zifeng_aura_sword_shard", () -> {
            }, () -> {
            });
        }

        static final ShaderStateShard POSITION_COLOR_SHADER_S = RenderStateShard.POSITION_COLOR_SHADER;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final CullStateShard NO_CULL_S = NO_CULL;
        static final WriteMaskStateShard COLOR_DEPTH_WRITE_S = COLOR_DEPTH_WRITE;
        static final DepthTestStateShard LEQUAL_DEPTH_S = LEQUAL_DEPTH_TEST;
        static final ShaderStateShard POSITION_TEX_SHADER_S = POSITION_TEX_SHADER;
        static final TransparencyStateShard ADDITIVE = ADDITIVE_TRANSPARENCY;
        static final WriteMaskStateShard COLOR_WRITE_S = COLOR_WRITE;
    }

    /** 光环通道（POSITION_COLOR，半透明，无剔除，带深度测试） */
    private static final RenderType BURST_RING = RenderType.create(
            "zifeng_aura_burst_ring",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_COLOR_SHADER_S)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setWriteMaskState(ShardAccess.COLOR_DEPTH_WRITE_S)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** TextureStateShard 的 protected 构造，用子类暴露 */
    private static final class HaloTextureState extends RenderStateShard.TextureStateShard {
        private HaloTextureState(net.minecraft.resources.ResourceLocation texture) {
            super(texture, false, false);
        }
    }

    /** 脑后光环贴图（2026-10-22 用户建议：用好看的贴图代替几何环） */
    private static final net.minecraft.resources.ResourceLocation HALO_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(org.zifeng.skilltree.SkillTreeMod.MOD_ID,
                    "textures/misc/aura_halo.png");

    private static final RenderType HALO_TYPE = RenderType.create(
            "zifeng_aura_halo",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_TEX_SHADER_S)
                    .setTextureState(new HaloTextureState(HALO_TEX))
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** 剑体光晕通道：叠加混合（ADDITIVE），比普通半透明更亮 */
    private static final RenderType GLOW_TYPE = RenderType.create(
            "zifeng_aura_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_COLOR_SHADER_S)
                    .setTransparencyState(ShardAccess.ADDITIVE)
                    .setWriteMaskState(ShardAccess.COLOR_WRITE_S)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** 剑身光晕贴图（2026-10-22：用户要求用贴图，不要几何色条） */
    private static final net.minecraft.resources.ResourceLocation SWORD_GLOW_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    org.zifeng.skilltree.SkillTreeMod.MOD_ID, "textures/misc/aura_sword_glow.png");

    private static final RenderType SWORD_GLOW_TYPE = RenderType.create(
            "zifeng_sword_glow",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_TEX_SHADER_S)
                    .setTextureState(new HaloTextureState(SWORD_GLOW_TEX))
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** 剑光叠加层：比普通半透明更亮（不受 alpha 排序影响，视觉上更“发光”） */
    private static final RenderType SWORD_GLOW_ADD_TYPE = RenderType.create(
            "zifeng_sword_glow_add",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_TEX_SHADER_S)
                    .setTextureState(new HaloTextureState(SWORD_GLOW_TEX))
                    .setTransparencyState(ShardAccess.ADDITIVE)
                    .setWriteMaskState(ShardAccess.COLOR_WRITE_S)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** 飞剑贴图（2026-10-22 用户要求：像光环那样用贴图做剑，不再用 item 模型） */
    private static final net.minecraft.resources.ResourceLocation SWORD_BODY_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    org.zifeng.skilltree.SkillTreeMod.MOD_ID, "textures/misc/aura_sword.png");

    private static final RenderType SWORD_BODY_TYPE = RenderType.create(
            "zifeng_aura_sword_body",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_TEX_SHADER_S)
                    .setTextureState(new HaloTextureState(SWORD_BODY_TEX))
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** 彩色光柱贴图（2026-10-22 用户要求：飞剑改成激光光柱） */
    private static final net.minecraft.resources.ResourceLocation BEAM_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    org.zifeng.skilltree.SkillTreeMod.MOD_ID, "textures/misc/aura_beam.png");

    private static final RenderType BEAM_TYPE = RenderType.create(
            "zifeng_aura_beam",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.POSITION_TEX_SHADER_S)
                    .setTextureState(new HaloTextureState(BEAM_TEX))
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.LEQUAL_DEPTH_S)
                    .createCompositeState(false));

    /** 复用缓冲（懒加载单例；endBatch 后内部 buffer 清空，下次 getBuffer 重新 begin） */
    private static MultiBufferSource.BufferSource SWORD_BUFFERS;

    private static MultiBufferSource.BufferSource swordBuffers() {
        if (SWORD_BUFFERS == null) {
            final java.util.LinkedHashMap<RenderType, com.mojang.blaze3d.vertex.ByteBufferBuilder> map =
                    new java.util.LinkedHashMap<>();
            map.put(BURST_RING, new com.mojang.blaze3d.vertex.ByteBufferBuilder(8192));
            SWORD_BUFFERS = MultiBufferSource.immediateWithBuffers(map,
                    new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096));
        }
        return SWORD_BUFFERS;
    }

    private AuraSwordRenderer() {
    }

    /** 实体阶段：剑阵 + 所有在飞的飞剑 */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ModKeyBindingEvents.isAuraAttackEnabled()) {
            return;
        }
        final var player = mc.player;
        final var camPos = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        final double px = Mth.lerp((double) partial, player.xo, player.getX());
        final double py = Mth.lerp((double) partial, player.yo, player.getY());
        final double pz = Mth.lerp((double) partial, player.zo, player.getZ());

        final float seconds = (System.currentTimeMillis() % 600_000L) / 1000.0f;
        final List<AuraSwordClientState.Launch> flying = AuraSwordClientState.launches();
        final MultiBufferSource.BufferSource buffers = swordBuffers();

        // ★ 2026-10-22 修「黑暗/洞穴里飞剑消失」：实体阶段的浓雾会把整把剑吃掉。
        //   画剑期间临时把雾推到无限远，画完恢复原值（不影响后续阶段）。
        final float savedFogStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
        final float savedFogEnd = com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        // ===== 剑阵：脑后竖直圆环（2026-10-22 用户要求：从脚下改到脑后，不碍事） =====
        final float yawRad = (float) Math.toRadians(player.getYRot());
        final Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        final Vec3 rightDir = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
        final double eyeY = py + player.getEyeHeight();
        // 圆心：眼睛高度 + 往后 0.55 格（脑后）
        final Vec3 arrayCenter = new Vec3(px, eyeY + 0.05, pz).add(forward.scale(-0.55));
        // ===== 剑阵：脑后竖直圆环，8 把剑径向排列 =====
        for (int i = 0; i < SWORD_COUNT; i++) {
            if (i < flying.size()) {
                continue;
            }
            final float angle = (float) (i * (Math.PI * 2.0 / SWORD_COUNT) + seconds * SPIN_PER_SEC);
            final Vec3 radial = rightDir.scale(Math.cos(angle)).add(0.0, Math.sin(angle), 0.0).normalize();
            final Vec3 pos = arrayCenter.add(radial.scale(ORBIT_RADIUS));
            drawSwordOriented(mc, pose, buffers, pos, radial, forward, SWORD_SCALE);
        }


        // ===== 多把飞剑：各自沿贝塞尔弧线飞向自己的目标 =====
        for (final AuraSwordClientState.Launch launch : flying) {
            final float p = AuraSwordClientState.launchProgress(launch);
            if (p < 0.0f) {
                continue;
            }
            final Vec3 s0 = launch.start();
            final Vec3 s1 = launch.target();
            final Vec3 ctrl = controlPoint(s0, s1);
            // ★ 2026-10-22 用户要求：明显的速度增长曲线 —— pow(p, 2.4)
            //   前 25% 只走约 4% 路程，中后段猛加速，肉眼能清楚看到“越飞越快”
            final float e = (float) Math.pow(p, 2.4);
            final Vec3 pos = bezier(s0, ctrl, s1, e);
            final Vec3 tan = bezierTangent(s0, ctrl, s1, e);
            // ★ 2026-10-22 去掉飞行翻滚：短促飞行里 sin 摆角会让剑身每帧左右晃，看着“不稳”
            final float roll = 0.0f;
            drawTrail(buffers.getBuffer(BURST_RING), pose.last().pose(), s0, ctrl, s1, e, seconds);
            // 朝向平滑：刚出手时沿用剑阵朝向，18% 内平滑转到曲线切向，避免起步瞬間“掰头”
            Vec3 aim = tan;
            if (p < 0.18f) {
                final Vec3 launchDir = s1.subtract(s0);
                if (launchDir.lengthSqr() > 1.0E-6) {
                    final float k = p / 0.18f;
                    aim = launchDir.normalize().scale(1.0f - k).add(tan.normalize().scale(k));
                    if (aim.lengthSqr() < 1.0E-6) {
                        aim = tan;
                    }
                }
            }
            // 飞行中渐渐变大
            final float flyScale = SWORD_SCALE * (1.15f + 1.85f * p);
            drawSwordAlong(mc, pose, buffers, (float) pos.x, (float) pos.y, (float) pos.z,
                    aim, roll, flyScale);
        }

        pose.popPose();
        buffers.endBatch();

        // 恢复雾（否则本帧后续的粒子/天气会失去雾效）
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(savedFogStart);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(savedFogEnd);
    }

    /**
     * 半透明方块之后：命中红环 + 玩家脚下常驻环。
     *
     * <p>放到 {@code AFTER_TRANSLUCENT_BLOCKS} 的原因：冰/水是半透明方块，比实体阶段更晚绘制；
     * 在实体阶段画环会和冰面叠成两层，看起来发灰、像透视。
     */
    @SubscribeEvent
    public static void onRenderRing(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ModKeyBindingEvents.isAuraAttackEnabled()) {
            return;
        }
        final float seconds = (System.currentTimeMillis() % 600_000L) / 1000.0f;
        final PoseStack pose = event.getPoseStack();
        final Vec3 camPos = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = swordBuffers();
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        // ① 命中冲击波（可能同时多个）：半径 10 格，easeOutCubic 先快后慢
        for (final AuraSwordClientState.Blast blast : AuraSwordClientState.blasts()) {
            final float pb = AuraSwordClientState.blastProgress(blast);
            if (pb < 0.0f) {
                continue;
            }
            final float wave = 1.0f - (float) Math.pow(1.0f - pb, 3.0);
            final float inner = 0.4f + wave * (BLAST_MAX_RADIUS - 0.4f);
            final int alpha = (int) (220 * (1.0f - wave * 0.85f));
            if (alpha > 0) {
                // ★ 渐渐变粗：冲击波越往外，环带厚度越大（0.25 → 约 2.2 格）
                final float thickness = 0.25f + wave * 1.95f;
                // ★ 2026-10-22 用户要求：冲击波重做 —— 白青核心 + 青色主环 + 紫色外晕
                final org.joml.Matrix4f bm = pose.last().pose();
                final VertexConsumer bv = buffers.getBuffer(BURST_RING);
                final float bx = (float) blast.center().x;
                final float by = (float) blast.center().y + 0.18f;
                final float bz = (float) blast.center().z;
                drawRing(bm, bv, bx, by, bz, inner, inner + thickness * 2.1f,
                        150, 90, 255, (int) (alpha * 0.30f));
                drawRing(bm, bv, bx, by, bz, inner, inner + thickness,
                        90, 225, 255, alpha);
                drawRing(bm, bv, bx, by, bz, inner, inner + thickness * 0.22f,
                        235, 255, 255, alpha);
            }
        }

        // ★ 2026-10-22 用户要求：删掉脚下光环（剑阵已移到脑后，脚下不需要了）
        final var player = mc.player;
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final double px = Mth.lerp((double) partial, player.xo, player.getX());
        final double py = Mth.lerp((double) partial, player.yo, player.getY());
        final double pz = Mth.lerp((double) partial, player.zo, player.getZ());

        // ★ 2026-10-22：脑后紫色竖直光环（剑阵的底座）
        final float haloYaw = (float) Math.toRadians(player.getYRot());
        final Vec3 haloFwd = new Vec3(-Math.sin(haloYaw), 0.0, Math.cos(haloYaw));
        final Vec3 haloRight = new Vec3(Math.cos(haloYaw), 0.0, Math.sin(haloYaw));
        // 光环与剑阵同平面（剑阵已改回 item 模型，不再和贴图互相干涉）
        final Vec3 haloCenter = new Vec3(px, py + player.getEyeHeight() + 0.05, pz)
                .add(haloFwd.scale(-0.55));
        // 贴图光环：贴图里环半径占比 176/256 ≈ 0.6875，quad 半宽 = ORBIT_RADIUS / 0.6875
        final float haloHalf = ORBIT_RADIUS / 0.6875f;
        drawHaloQuad(buffers, pose.last().pose(), haloCenter, haloRight, haloHalf, seconds);

        pose.popPose();
        buffers.endBatch();
    }

    /** 模型里刀尖相对 +Y 的偏角（度） */
    private static final float BLADE_MODEL_OFFSET_DEG = 45.0f;

    /**
     * 画一把「平铺」的剑，剑尖严格朝向 dir（三维，含俯仰），剑面尽量朝上。
     * <p>用<b>正交基矩阵</b>保证姿态：模型刀尖(+Y)→dir，模型面法线(+Z)→世界上方在垂直 dir 平面的投影。
     * <p>⚠️ JOML Matrix4f 构造是【列主序】：每 4 个数 = 一整列。
     */
    private static void drawSwordAlong(Minecraft mc, PoseStack pose, MultiBufferSource buffers,
                                       float x, float y, float z, Vec3 dir, float roll, float scale) {
        drawSwordOriented(mc, pose, buffers, new Vec3(x, y, z), dir, new Vec3(0.0, 1.0, 0.0), scale);
    }

    /** 画一把剑：剑尖朝 dir，剑面尽量朝向 normalHint（用于脑后竖直剑阵：法线=玩家前方） */
    private static void drawSwordOriented(Minecraft mc, PoseStack pose, MultiBufferSource buffers,
                                          Vec3 position, Vec3 dir, Vec3 normalHint, float scale) {
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);

        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
        Vec3 normal = normalHint.subtract(d.scale(normalHint.dot(d)));
        if (normal.lengthSqr() < 1.0E-6) {
            normal = new Vec3(0.0, 1.0, 0.0).subtract(d.scale(d.y));
        }
        normal = normal.normalize();
        Vec3 side = d.cross(normal).normalize();

        final org.joml.Matrix4f basis = new org.joml.Matrix4f(
                (float) side.x, (float) side.y, (float) side.z, 0.0f,
                (float) d.x, (float) d.y, (float) d.z, 0.0f,
                (float) normal.x, (float) normal.y, (float) normal.z, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f);
        basis.mul(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(-BLADE_MODEL_OFFSET_DEG)));
        pose.last().pose().mul(basis);

        pose.scale(scale, scale, scale);
        mc.getItemRenderer().renderStatic(SWORD, ItemDisplayContext.FIXED,
                0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
    }

    /** 贝塞尔控制点：中点 + 侧向偏移 + 轻微抬高 */
    private static Vec3 controlPoint(Vec3 start, Vec3 end) {
        final Vec3 d = end.subtract(start);
        final double len = Math.max(0.8, d.length());
        Vec3 dir = d.normalize();
        Vec3 perp = new Vec3(-dir.z, 0.0, dir.x);
        if (perp.lengthSqr() < 1.0E-6) {
            perp = new Vec3(1.0, 0.0, 0.0);
        }
        perp = perp.normalize();
        final double arc = Math.min(2.4, len * 0.38);
        return start.add(end).scale(0.5)
                .add(perp.scale(arc))
                .add(0.0, 0.4, 0.0);
    }

    private static Vec3 bezier(Vec3 s0, Vec3 c, Vec3 s1, float t) {
        final float u = 1.0f - t;
        return s0.scale(u * u).add(c.scale(2.0f * u * t)).add(s1.scale(t * t));
    }

    private static Vec3 bezierTangent(Vec3 s0, Vec3 c, Vec3 s1, float t) {
        return c.subtract(s0).scale(2.0f * (1.0f - t))
                .add(s1.subtract(c).scale(2.0f * t));
    }

    /** 拖尾·流光：尾部暗细 → 剑附近亮宽，叠加沿路径流动的明暗条纹 */
    /**
     * 彩色光柱（2026-10-22 用户要求：真柱状物体）。
     * <p>8 棱柱侧面 + 内芯，沿轴向紫→青渐变，双面半透明。
     */
    private static void drawBeam(MultiBufferSource buffers, org.joml.Matrix4f m,
                                 Vec3 cam, Vec3 head, Vec3 tail, float halfW) {
        Vec3 axis = head.subtract(tail);
        if (axis.lengthSqr() < 1.0E-6) {
            return;
        }
        final Vec3 dir = axis.normalize();
        // 构造垂直于轴的基
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        if (Math.abs(dir.dot(up)) > 0.95) {
            up = new Vec3(1.0, 0.0, 0.0);
        }
        final Vec3 u = up.subtract(dir.scale(up.dot(dir))).normalize();
        final Vec3 v = dir.cross(u).normalize();
        final VertexConsumer vc = buffers.getBuffer(BEAM_TYPE);
        final int seg = 10;
        final int steps = 14;
        final double twoPi = Math.PI * 2.0;
        for (int st = 0; st < steps; st++) {
            final float t0 = st / (float) steps;
            final float t1 = (st + 1) / (float) steps;
            // 头粗尾细（像一道冲击光）
            final float r0 = halfW * (0.55f + 0.45f * t0);
            final float r1 = halfW * (0.55f + 0.45f * t1);
            final Vec3 c0 = tail.add(axis.scale(t0));
            final Vec3 c1 = tail.add(axis.scale(t1));
            // 沿轴颜色：紫 → 青
            final int ar = (int) (210 - 60 * t0);
            final int ag = (int) (95 + 140 * t0);
            final int ab = 255;
            final int aa = (int) (150 + 70 * t0);
            for (int i = 0; i < seg; i++) {
                final double a0 = i * twoPi / seg;
                final double a1 = (i + 1) * twoPi / seg;
                final Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
                final Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
                final Vec3 p00 = c0.add(d0.scale(r0));
                final Vec3 p01 = c0.add(d1.scale(r0));
                final Vec3 p11 = c1.add(d1.scale(r1));
                final Vec3 p10 = c1.add(d0.scale(r1));
                gradVertex(vc, m, p00, ar, ag, ab, aa);
                gradVertex(vc, m, p01, ar, ag, ab, aa);
                gradVertex(vc, m, p11, ar, ag, ab, aa);
                gradVertex(vc, m, p10, ar, ag, ab, aa);
            }
        }
        // 内芯：更细的白光（4 棱）
        for (int st = 0; st < steps; st++) {
            final float t0 = st / (float) steps;
            final float t1 = (st + 1) / (float) steps;
            final float r0 = halfW * 0.38f;
            final float r1 = halfW * 0.30f;
            final Vec3 c0 = tail.add(axis.scale(t0));
            final Vec3 c1 = tail.add(axis.scale(t1));
            final int aa = (int) (200 + 40 * t0);
            for (int i = 0; i < 4; i++) {
                final double a0 = i * twoPi / 4.0;
                final double a1 = (i + 1) * twoPi / 4.0;
                final Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
                final Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
                final Vec3 p00 = c0.add(d0.scale(r0));
                final Vec3 p01 = c0.add(d1.scale(r0));
                final Vec3 p11 = c1.add(d1.scale(r1));
                final Vec3 p10 = c1.add(d0.scale(r1));
                gradVertex(vc, m, p00, 240, 250, 255, aa);
                gradVertex(vc, m, p01, 240, 250, 255, aa);
                gradVertex(vc, m, p11, 240, 250, 255, aa);
                gradVertex(vc, m, p10, 240, 250, 255, aa);
            }
        }
    }

    /** 写一个带颜色顶点 */
    private static void gradVertex(VertexConsumer vc, org.joml.Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    private static void drawTrail(VertexConsumer vc, org.joml.Matrix4f m,
                                  Vec3 s0, Vec3 c, Vec3 s1, float upTo, float time) {
        if (vc == null || upTo <= 0.02f) {
            return;
        }
        Vec3 prevA = null;
        Vec3 prevB = null;
        int prevR = 0, prevG = 0, prevAlpha = 0;
        for (int k = 0; k <= TRAIL_STEPS; k++) {
            final float t = upTo * k / TRAIL_STEPS;
            final Vec3 p = bezier(s0, c, s1, t);
            final Vec3 d = bezierTangent(s0, c, s1, t);
            Vec3 perp = new Vec3(-d.z, 0.0, d.x);
            if (perp.lengthSqr() < 1.0E-6) {
                perp = new Vec3(1.0, 0.0, 0.0);
            }
            final float kt = (float) k / TRAIL_STEPS;
            perp = perp.normalize().scale(0.015f + 0.12f * (float) Math.sin(Math.PI * kt));
            final Vec3 a = p.subtract(perp);
            final Vec3 b = p.add(perp);

            final float bright = 0.30f + 0.70f * kt;
            final float flow = 0.62f + 0.38f * (float) Math.sin(t * 16.0 - time * 10.0);
            final int r = (int) (40 + 175 * bright);
            final int g = (int) (150 + 105 * bright);
            final int alpha = Math.min(255, (int) (255 * bright * flow));

            if (prevA != null) {
                vc.addVertex(m, (float) prevA.x, (float) prevA.y, (float) prevA.z).setColor(prevR, prevG, 255, prevAlpha);
                vc.addVertex(m, (float) prevB.x, (float) prevB.y, (float) prevB.z).setColor(prevR, prevG, 255, prevAlpha);
                vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, 255, alpha);
                vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, 255, alpha);
            }
            prevA = a;
            prevB = b;
            prevR = r;
            prevG = g;
            prevAlpha = alpha;
        }
    }

    /** 取 (x,z) 处的地面高度（贴地画剑用） */
    private static float groundHeightAt(net.minecraft.world.level.Level level,
                                        double x, double playerFeetY, double z) {
        // ★ 2026-10-22 修「洞穴里剑阵消失」：不能用 getHeight(MOTION_BLOCKING)——
        //   它返回的是该列【地表】高度（从世界顶往下数），玩家在洞穴 y=30 时会返回地表 y=70，
        //   剑阵就被画到几十格高的地表上了。直接以玩家脚底为基准，任何高度/洞穴都正确。
        return (float) playerFeetY + 0.18f;
    }

    /**
     * 绕圈渐变环带：颜色沿圆周在「紫 → 青 → 紫」之间平滑过渡（2026-10-22 美化）。
     * <p>每个小四边形按角度取色，形成流动的彩虹感，比纯紫色更有层次。
     */
    private static void drawRingGradient(org.joml.Matrix4f matrix, VertexConsumer vc,
                                         float cx, float cy, float cz, float inner, float outer,
                                         int alpha, float phase) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            final double a0 = i * (Math.PI * 2.0 / RING_SEGMENTS);
            final double a1 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);
            final float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            final float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);
            // t ∈ [0,1) 沿圆周，phase 让它缓慢流动
            final float t = (float) (i / (double) RING_SEGMENTS);
            final float mix = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0 - phase * 1.4));
            final int r = (int) (150 + (90 - 150) * mix);
            final int g = (int) (90 + (205 - 90) * mix);
            final int b = 255;
            vc.addVertex(matrix, cx + cos0 * inner, cy, cz + sin0 * inner).setColor(r, g, b, alpha);
            vc.addVertex(matrix, cx + cos0 * outer, cy, cz + sin0 * outer).setColor(r, g, b, Math.max(0, alpha - 25));
            vc.addVertex(matrix, cx + cos1 * outer, cy, cz + sin1 * outer).setColor(r, g, b, Math.max(0, alpha - 25));
            vc.addVertex(matrix, cx + cos1 * inner, cy, cz + sin1 * inner).setColor(r, g, b, alpha);
        }
    }

    /** 脑后光环：多色循环渐变 + 径向 3 层虚化（1.21.1 顶点 API） */
    private static void quadColorA(VertexConsumer vc, org.joml.Matrix4f m,
                                   Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4,
                                   int r, int g, int b, int a1, int a2, int a3, int a4) {
        vc.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a1);
        vc.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a2);
        vc.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, a3);
        vc.addVertex(m, (float) p4.x, (float) p4.y, (float) p4.z).setColor(r, g, b, a4);
    }

    private static void quadColor(VertexConsumer vc, org.joml.Matrix4f m,
                                  Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a);
        vc.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a);
        vc.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, a);
        vc.addVertex(m, (float) p4.x, (float) p4.y, (float) p4.z).setColor(r, g, b, a);
    }

    /** 写一个贴图顶点 */
    private static void quadTex(VertexConsumer vc, org.joml.Matrix4f m, Vec3 p, float u, float v) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v);
    }

    /** 剑体光晕：贴图 quad（普通半透明层 + 叠加加亮层） */
    private static void drawSwordGlow(MultiBufferSource buffers, org.joml.Matrix4f m,
                                      Vec3 center, Vec3 dir, Vec3 normal, float scale) {
        final Vec3 d = dir.normalize();
        final Vec3 side = d.cross(normal).normalize();
        // 与光环相同的半透明通道（柔和、有颜色，不会加成白块）
        glowQuad(buffers.getBuffer(SWORD_GLOW_TYPE), m, center, d, side, scale, 1.0f);
    }

    /** 画一层剑光贴图（沿剑身方向，正反两面） */
    private static void glowQuad(VertexConsumer vc, org.joml.Matrix4f m,
                                 Vec3 center, Vec3 d, Vec3 side, float scale, float widthFactor) {
        final float len = 1.05f * scale;
        final float halfW = 0.17f * scale * widthFactor;
        final Vec3 back = center.subtract(d.scale(len * 0.45f));
        final Vec3 front = center.add(d.scale(len * 0.55f));
        final Vec3 b1 = back.subtract(side.scale(halfW));
        final Vec3 b2 = back.add(side.scale(halfW));
        final Vec3 f1 = front.add(side.scale(halfW));
        final Vec3 f2 = front.subtract(side.scale(halfW));
        quadTex(vc, m, b1, 0.0f, 1.0f);
        quadTex(vc, m, b2, 1.0f, 1.0f);
        quadTex(vc, m, f1, 1.0f, 0.0f);
        quadTex(vc, m, f2, 0.0f, 0.0f);
        quadTex(vc, m, f2, 0.0f, 0.0f);
        quadTex(vc, m, f1, 1.0f, 0.0f);
        quadTex(vc, m, b2, 1.0f, 1.0f);
        quadTex(vc, m, b1, 0.0f, 1.0f);
    }

    /** 一条沿剑身方向的光带（两端透明、中段最亮，ADDITIVE，正反两面） */
    private static void glowBar(VertexConsumer vc, org.joml.Matrix4f m, Vec3 center,
                                Vec3 d, Vec3 side, float halfW, float len, float intensity, float pulse) {
        final int peak = Math.min(255, (int) (255 * intensity * (0.55f + 0.45f * pulse)));
        if (peak <= 0) {
            return;
        }
        final Vec3 back = center.add(d.scale(-0.20f * len));
        final Vec3 mid = center.add(d.scale(0.30f * len));
        final Vec3 front = center.add(d.scale(0.80f * len));
        // 段一：剑柄端（透明）→ 中段（最亮）
        quadColorA(vc, m,
                back.subtract(side.scale(halfW * 0.30f)), back.add(side.scale(halfW * 0.30f)),
                mid.add(side.scale(halfW)), mid.subtract(side.scale(halfW)),
                170, 235, 255, 0, 0, peak, peak);
        quadColorA(vc, m,
                mid.subtract(side.scale(halfW)), mid.add(side.scale(halfW)),
                front.add(side.scale(halfW * 0.25f)), front.subtract(side.scale(halfW * 0.25f)),
                170, 235, 255, peak, peak, 0, 0);
    }

    /** 画贴图光环（竖直面，正反两面） */
    private static void drawHaloQuad(MultiBufferSource buffers, org.joml.Matrix4f m,
                                     Vec3 center, Vec3 right, float half, float time) {
        final Vec3 up = new Vec3(0.0, 1.0, 0.0);
        // ★ 2026-10-22：贴图环缓慢自转（绕法线，即玩家前方轴）
        final float ang = time * 0.8f;
        final float ca = (float) Math.cos(ang);
        final float sa = (float) Math.sin(ang);
        final Vec3 r = right.scale(ca).add(up.scale(sa)).scale(half);
        final Vec3 u = up.scale(ca).subtract(right.scale(sa)).scale(half);
        final VertexConsumer vc = buffers.getBuffer(HALO_TYPE);
        quadTex(vc, m, center.subtract(r).subtract(u), 0.0f, 1.0f);
        quadTex(vc, m, center.add(r).subtract(u), 1.0f, 1.0f);
        quadTex(vc, m, center.add(r).add(u), 1.0f, 0.0f);
        quadTex(vc, m, center.subtract(r).add(u), 0.0f, 0.0f);
    }

    private static void drawVerticalRing(org.joml.Matrix4f m, VertexConsumer vc,
                                         Vec3 center, Vec3 right, float radius, float thickness,
                                         int baseAlpha, float time) {
        final int layers = 3;
        for (int layer = 0; layer < layers; layer++) {
            final float rIn = radius - thickness * 0.5f + layer * thickness / layers;
            final float rOut = rIn + thickness / layers;
            final int alpha = (int) (baseAlpha * (1.0f - layer * 0.32f));
            for (int i = 0; i < RING_SEGMENTS; i++) {
                final double a0 = i * (Math.PI * 2.0 / RING_SEGMENTS);
                final double a1 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);
                final float t = (float) (i / (double) RING_SEGMENTS + time * 0.03);
                final int r = (int) (150 + 105 * Math.sin(t * Math.PI * 2.0));
                final int g = (int) (150 + 105 * Math.sin(t * Math.PI * 2.0 + 2.09));
                final int b = (int) (150 + 105 * Math.sin(t * Math.PI * 2.0 + 4.19));
                final float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                final float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                vc.addVertex(m, (float) (center.x + right.x * c0 * rIn), (float) (center.y + s0 * rIn),
                        (float) (center.z + right.z * c0 * rIn)).setColor(r, g, b, alpha);
                vc.addVertex(m, (float) (center.x + right.x * c0 * rOut), (float) (center.y + s0 * rOut),
                        (float) (center.z + right.z * c0 * rOut)).setColor(r, g, b, Math.max(0, alpha - 45));
                vc.addVertex(m, (float) (center.x + right.x * c1 * rOut), (float) (center.y + s1 * rOut),
                        (float) (center.z + right.z * c1 * rOut)).setColor(r, g, b, Math.max(0, alpha - 45));
                vc.addVertex(m, (float) (center.x + right.x * c1 * rIn), (float) (center.y + s1 * rIn),
                        (float) (center.z + right.z * c1 * rIn)).setColor(r, g, b, alpha);
            }
        }
    }

    /** 画一圈圆环带（世界坐标，可指定 RGB） */
    private static void drawRing(org.joml.Matrix4f matrix, VertexConsumer vc,
                                 float cx, float cy, float cz, float inner, float outer,
                                 int r, int g, int b, int alpha) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            final double a0 = i * (Math.PI * 2.0 / RING_SEGMENTS);
            final double a1 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);
            final float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            final float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);
            vc.addVertex(matrix, cx + cos0 * inner, cy, cz + sin0 * inner).setColor(r, g, b, alpha);
            vc.addVertex(matrix, cx + cos0 * outer, cy, cz + sin0 * outer).setColor(r, g, b, Math.max(0, alpha - 30));
            vc.addVertex(matrix, cx + cos1 * outer, cy, cz + sin1 * outer).setColor(r, g, b, Math.max(0, alpha - 30));
            vc.addVertex(matrix, cx + cos1 * inner, cy, cz + sin1 * inner).setColor(r, g, b, alpha);
        }
    }
}
