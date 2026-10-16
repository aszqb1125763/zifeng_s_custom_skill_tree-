package org.zifeng.skilltree.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.zifeng.skilltree.client.ModKeyBindingEvents;

/**
 * 玩家脚下的光环星空环渲染层（Avaritia 式 RenderLayer 绑定玩家模型）：
 * <ul>
 *   <li>零实体零存档，随玩家模型渲染，第三人称/他人可见（无闪烁、存档不受影响）</li>
 *   <li>星空贴图：原版末地传送门 shader（动态星空），自定义通道加 NO_CULL（底部也可见）+ 半透明</li>
 *   <li>内径 1.0 / 外径 2.0；内圈紫色描边；外径渐变虚化（多层递减透明度）</li>
 *   <li>位置：玩家脚底为 0 坐标基点，降低到脚底下方 2 像素</li>
 *   <li>渲染条件：只绑定杀戮光环（总开关开启 + 伤害/速度任一开启），与磁力光环无关</li>
 * </ul>
 *
 * <h2>★ 26.1.2 移植说明（为什么这么写）</h2>
 * <ol>
 *   <li><b>绑定方式</b>：1.21.1 用 Mixin 往 {@code PlayerRenderer} 构造里塞 layer；26.1.2 里
 *       {@code PlayerRenderer} 连类都不存在了（玩家渲染器已统一为
 *       {@link net.minecraft.client.renderer.entity.player.AvatarRenderer}）。改为官方事件
 *       {@code EntityRenderersEvent.AddLayers} + {@code event.getPlayerRenderer(skin).addLayer(...)}
 *       （见 {@code ClientRegistrar}），不再需要 mixin。</li>
 *   <li><b>泛型变了</b>：1.21.1 的 {@code RenderLayer<E extends Entity, M extends EntityModel<E>>}
 *       现在换成 {@code RenderLayer<S extends EntityRenderState, M extends EntityModel<? super S>>}
 *       —— 第一个参数是<b>渲染状态</b>而不是实体。{@code AvatarRenderer} 的状态类型是
 *       {@link AvatarRenderState}，所以本类声明为 {@code RenderLayer<AvatarRenderState, PlayerModel>}。</li>
 *   <li><b>绘制方式</b>：旧写法是「从 {@code MultiBufferSource} 取缓冲区 + 手工拼一个复合状态的
 *       通道描述（含着色器状态 / 贴图状态 / 透明状态 / 剔除状态 / 写掩码）」—— 这套 API 在 26.1.2
 *       <b>全部删除</b>。
 *       现在改为 {@code SubmitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> …)}
 *       （与官方 {@code FishingHookRenderer} / {@code AbstractEndPortalRenderer} 同款），
 *       RenderType 用 {@code RenderType.create(name, RenderSetup.builder(管线)…createRenderSetup())} 构造。</li>
 *   <li><b>星空管线</b>：直接用原版现成的 {@link RenderPipelines#END_PORTAL}
 *       （顶点格式 {@code POSITION} + {@code QUADS} + 双贴图 Sampler0/Sampler1），贴图常量取
 *       {@link AbstractEndPortalRenderer#END_SKY_LOCATION} / {@link AbstractEndPortalRenderer#END_PORTAL_LOCATION}。
 *       <b>无需自己注册管线</b>，星空效果与原版末地传送门一致（不是降级实现）。</li>
 *   <li><b>两处与原实现的有意差异</b>（原 API 已删除，无法一比一还原，但观感一致）：
 *       <ul>
 *         <li>{@code END_PORTAL} 管线自带背面剔除（原实现用的是「不剔除」配置）→
 *             星环四边形<b>按正反两套绕序各提交一次</b>，俯视/仰视都能看到环。</li>
 *         <li>描边/渐变环原用「位置+颜色着色器 + 半透明 + 不剔除 + 写颜色与深度」，
 *             现改用现成的 {@link RenderPipelines#DEBUG_QUADS}（位置+颜色着色器 + 半透明混合 +
 *             {@code withCull(false)}）；唯一差别是它不写深度 —— 对纯装饰性的半透明光环更安全。</li>
 *       </ul></li>
 * </ol>
 */
public class PlayerRingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** 环内半径（格） */
    private static final float RING_INNER = 1.0f;
    /** 环外半径（格） */
    private static final float RING_RADIUS = 2.0f;
    /** 外圈虚化范围（格）：2.0 → 2.2 渐变淡出 */
    private static final float FADE_RADIUS = 2.2f;
    /** 位置：RenderLayer 坐标 Y 轴被 LivingEntityRenderer 的 scale(-1,-1,1) 翻转（py 越大越低，每 +1 = 下降 1 格）。
     *  实测校准：py=0.1 胸口、py=-1.4 头顶、py=2.3 脚底下方1格 → 脚底在 py=1.3；
     *  目标脚底上方 2 像素 → 1.2；用户要求再往下调 2 像素（0.1 格）→ 1.2（当前=降低到脚底上方0像素，再低2像素=贴近地面）
     *  最新：降低到脚底下方 2 像素 → 1.3 */
    private static final float RING_HEIGHT = 1.3f;
    /** 环带分段数 */
    private static final int SEGMENTS = 48;

    /**
     * 末地传送门星空通道（★ 26.1.2：原版现成管线 + 双贴图；背面剔除由「双绕序提交」兜住）。
     */
    private static final RenderType RING_STAR = RenderType.create(
            "zifeng_ring_star",
            RenderSetup.builder(RenderPipelines.END_PORTAL)
                    .withTexture("Sampler0", AbstractEndPortalRenderer.END_SKY_LOCATION)
                    .withTexture("Sampler1", AbstractEndPortalRenderer.END_PORTAL_LOCATION)
                    .createRenderSetup());

    /**
     * 描边/渐变环通道（★ 26.1.2：{@code POSITION_COLOR} + 半透明混合 + 无剔除）。
     */
    private static final RenderType RING_OVERLAY = RenderType.create(
            "zifeng_ring_overlay",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS)
                    .createRenderSetup());

    public PlayerRingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       AvatarRenderState state, float yRot, float xRot) {
        // 渲染条件：只绑定杀戮光环（伤害/速度任一开启 = 杀戮光环单独开关），与磁力光环无关
        boolean auraOn = ModKeyBindingEvents.isAuraAttackEnabled();
        if (!auraOn) {
            return;
        }

        // ① 星空环（内径 1.0 ~ 外径 2.0）：末地传送门动态星空
        //    ⚠️ 管线自带背面剔除 → 同一圈环带按正、反两套绕序各提交一次，俯视/仰视都可见
        submitNodeCollector.submitCustomGeometry(poseStack, RING_STAR, (pose, buffer) -> {
            drawRingBand(buffer, pose, RING_HEIGHT, RING_INNER, RING_RADIUS, false);
            drawRingBand(buffer, pose, RING_HEIGHT, RING_INNER, RING_RADIUS, true);
        });

        // ②③ 内圈描边 + 外圈渐变虚化（半透明纯色，管线无剔除 → 单绕序即可）
        submitNodeCollector.submitCustomGeometry(poseStack, RING_OVERLAY, (pose, buffer) -> {
            // ② 内圈描边（1.0 处亮紫细环）
            drawRingBandColor(buffer, pose, RING_HEIGHT, RING_INNER - 0.04f, RING_INNER + 0.04f,
                    0x9B, 0x5F, 0xFF, 230);
            // ③ 外圈渐变虚化（2.0 → 2.6 多层递减透明度）
            for (int i = 1; i <= 6; i++) {
                float r0 = RING_RADIUS + (i - 1) * (FADE_RADIUS - RING_RADIUS) / 6.0f;
                float r1 = RING_RADIUS + i * (FADE_RADIUS - RING_RADIUS) / 6.0f;
                int alpha = (int) (90 * (1.0f - i / 7.0f));
                if (alpha <= 0) break;
                drawRingBandColor(buffer, pose, RING_HEIGHT, r0, r1,
                        0x5F, 0x3F, 0x9B, alpha);
            }
        });
    }

    /**
     * 预计算的分段角度 sin/cos（2026-09-11 性能优化）。
     * <p>环带顶点的角度<b>只与段索引 i 有关、与帧无关</b>，原实现每帧对每个被渲染的
     * 玩家重复计算 8 个环带 × 48 段 × 2 组 sin/cos = <b>768 次三角函数</b>。
     * 改为类加载时一次性算好（用 double 保留与原实现完全相同的精度）。
     */
    private static final double[] SEG_SIN = new double[SEGMENTS + 1];
    private static final double[] SEG_COS = new double[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS;
            SEG_SIN[i] = Math.sin(a);
            SEG_COS[i] = Math.cos(a);
        }
    }

    /** 星空环带（只有位置，末地传送门 shader 上色） */
    private static void drawRingBand(VertexConsumer consumer, PoseStack.Pose pose, float py,
                                     float inner, float outer, boolean reverse) {
        for (int i = 0; i < SEGMENTS; i++) {
            double sin0 = SEG_SIN[i], cos0 = SEG_COS[i];
            double sin1 = SEG_SIN[i + 1], cos1 = SEG_COS[i + 1];
            float a0x = (float) (cos0 * inner), a0z = (float) (sin0 * inner);
            float b0x = (float) (cos0 * outer), b0z = (float) (sin0 * outer);
            float b1x = (float) (cos1 * outer), b1z = (float) (sin1 * outer);
            float a1x = (float) (cos1 * inner), a1z = (float) (sin1 * inner);
            if (reverse) {
                consumer.addVertex(pose, a1x, py, a1z);
                consumer.addVertex(pose, b1x, py, b1z);
                consumer.addVertex(pose, b0x, py, b0z);
                consumer.addVertex(pose, a0x, py, a0z);
            } else {
                consumer.addVertex(pose, a0x, py, a0z);
                consumer.addVertex(pose, b0x, py, b0z);
                consumer.addVertex(pose, b1x, py, b1z);
                consumer.addVertex(pose, a1x, py, a1z);
            }
        }
    }

    /** 带颜色的环带（描边/渐变） */
    private static void drawRingBandColor(VertexConsumer consumer, PoseStack.Pose pose, float py,
                                          float inner, float outer, int r, int g, int b, int a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double sin0 = SEG_SIN[i], cos0 = SEG_COS[i];
            double sin1 = SEG_SIN[i + 1], cos1 = SEG_COS[i + 1];
            consumer.addVertex(pose, (float) (cos0 * inner), py, (float) (sin0 * inner)).setColor(r, g, b, a);
            consumer.addVertex(pose, (float) (cos0 * outer), py, (float) (sin0 * outer)).setColor(r, g, b, a);
            consumer.addVertex(pose, (float) (cos1 * outer), py, (float) (sin1 * outer)).setColor(r, g, b, a);
            consumer.addVertex(pose, (float) (cos1 * inner), py, (float) (sin1 * inner)).setColor(r, g, b, a);
        }
    }
}
