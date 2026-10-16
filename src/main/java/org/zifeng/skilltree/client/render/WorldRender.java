package org.zifeng.skilltree.client.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * 世界空间自绘渲染的<b>统一入口</b>（★ 26.1.2 移植新增）。
 *
 * <h2>为什么需要这个类</h2>
 * 26.1.2 把 1.21.1 那套<b>即时模式渲染整体删掉了</b>：
 * <ul>
 *   <li>{@code RenderStateShard} —— 类已删除</li>
 *   <li>{@code RenderSystem.enableBlend / enableCull / enableDepthTest / depthMask / …} —— 全部删除</li>
 *   <li>{@code BufferUploader.drawWithShader(…)} —— 已删除</li>
 *   <li>{@code LevelRenderer.renderLineBox(PoseStack, BufferBuilder, …)} —— 全库 0 引用</li>
 * </ul>
 * 取而代之的是「<b>提交几何体</b>」模型：把顶点写进 {@code SubmitNodeCollector}，
 * 由渲染器统一按 RenderType 声明好的状态（混合 / 剔除 / 深度）批量绘制。
 *
 * <h2>为什么这不是「降级」而是升级</h2>
 * 旧代码每次都要手工 enable/disable 一堆全局状态，还得自己保证成对还原 ——
 * 本模组 1.21.1 的选区渲染里就留着一条注释，专门在担心「前面有 {@code disableDepthTest()} 没还原，
 * 线框会穿透山体」。新模型下这些状态由 RenderType 一次性声明，<b>不存在漏还原的可能</b>，
 * 而且自动获得「未加载区块裁剪」与「不透明 / 半透明分层排序」。
 *
 * <h2>怎么用</h2>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onSubmit(SubmitCustomGeometryEvent event) {
 *     PoseStack ps = event.getPoseStack();
 *     Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
 *     ps.pushPose();
 *     ps.translate(-cam.x, -cam.y, -cam.z);   // 事件给的 pose 是世界原点，需自行减相机
 *     event.getSubmitNodeCollector().submitCustomGeometry(ps, WorldRender.normalLines(), (pose, buf) -> {
 *         WorldRender.lineBox(buf, pose, x0, y0, z0, x1, y1, z1, 0xFFFF0000, 1.0F);
 *     });
 *     ps.popPose();
 * }
 * }</pre>
 *
 * <p>⚠️ <b>触发时机</b>：本事件在 {@code LevelRenderer.renderLevel()} 内<b>无条件</b>触发
 * （见 26.1.2 反编译源码 {@code LevelRenderer.java:707}）。而 Iris 的阴影 pass 会重入
 * {@code renderLevel}，因此本事件在开光影时<b>每帧会触发多次</b>。若不加判断，选区线框/轮廓会
 * 被画进阴影贴图，表现为地形上出现规律性黑边闪烁。
 * <p>所以旧代码里那套「Iris 阴影 pass 规避」<b>仍然需要保留</b>，调用方在事件回调开头执行：
 * <pre>{@code
 * if (IrisCompat.isShadowPass()) {
 *     return;   // 无 Iris 时该调用首次解析失败后永久零开销短路
 * }
 * }</pre>
 */
public final class WorldRender {

    private WorldRender() {
    }

    /** 自定义「穿墙可见」线框管线的位置键。 */
    public static final Identifier SEE_THROUGH_LINES_ID =
            Identifier.fromNamespaceAndPath("zifeng_s_custom_skill_tree", "pipeline/see_through_lines");

    /** 自定义管线的实例（注册时赋值）。 */
    private static RenderPipeline seeThroughPipeline;

    /** 懒装载的 RenderType（必须等管线注册完才能建）。 */
    private static RenderType seeThroughLines;

    /**
     * 注册自定义管线（在 {@code modEventBus} 的 {@link RegisterRenderPipelinesEvent} 里调用）。
     *
     * <p>拿 {@code LINES_SNIPPET} 当底子（与 {@code RenderTypes.lines()} 同款顶点格式
     * {@code POSITION_COLOR_NORMAL_LINE_WIDTH} / {@code Mode.LINES}），只把深度测试换成
     * {@link CompareOp#ALWAYS_PASS} 且不写深度 —— 这正是 1.21.1 里
     * {@code RenderSystem.disableDepthTest()} 想表达的语义（选区边框隔着山也看得见）。
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        seeThroughPipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(SEE_THROUGH_LINES_ID)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();
        event.registerPipeline(seeThroughPipeline);
    }

    /**
     * 深度测试照常的线框（会被地形遮挡）。
     *
     * <p>直接用原版 {@code RenderTypes.lines()} —— NeoForge 自己的
     * {@code BlockEntityRenderBoundsDebugRenderer} 就是这么画的，是已验证的配置。
     */
    public static RenderType normalLines() {
        return RenderTypes.lines();
    }

    /**
     * 穿墙可见的线框（不受地形遮挡）。
     *
     * <p>配置与 {@link RenderTypes#lines()} 保持一致（同样的分层变换与输出目标），
     * 只换自定义管线 —— 这样两者的绘制时机与坐标约定完全相同，可以安全地叠着画。
     */
    public static RenderType seeThroughLines() {
        if (seeThroughLines == null) {
            seeThroughLines = RenderType.create("zifeng_see_through_lines",
                    RenderSetup.builder(seeThroughPipeline)
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup());
        }
        return seeThroughLines;
    }

    /**
     * 半透明填充（六面盒用）。
     *
     * <p>原版 {@code DEBUG_FILLED_SNIPPET} 就是给这类「半透明盒子」准备的：
     * 顶点格式 {@code POSITION_COLOR} + {@code Mode.QUADS} + 半透明混合 + 不写深度。
     */
    public static RenderType translucentFill() {
        return RenderTypes.debugFilledBox();
    }

    // ────────────────────────────────────────────────────────────────
    // 顶点写入工具（都要求调用方已经把「减相机」平移压进了 pose）
    // ────────────────────────────────────────────────────────────────

    /**
     * 画一个长方体的 12 条棱（线框）。
     *
     * <p>取代 1.21.1 的 {@code LevelRenderer.renderLineBox(poseStack, buffer, …)} ——
     * 那个方法在 26.1.2 已无任何调用方，且颜色参数从 float 换成了打包 int。
     *
     * <p>⚠️ 线框管线必须 {@code setNormal} + {@code setLineWidth}：
     * 顶点格式是 {@code POSITION_COLOR_NORMAL_LINE_WIDTH}，缺了会画不出来。
     *
     * @param argb  打包颜色（0xAARRGGBB）
     * @param width 线宽（一般取 1.0F）
     */
    public static void lineBox(VertexConsumer buffer, PoseStack.Pose pose,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               int argb, float width) {
        // 12 条棱：4 条沿 X、4 条沿 Y、4 条沿 Z
        edge(buffer, pose, x0, y0, z0, x1, y0, z0, argb, width);
        edge(buffer, pose, x0, y0, z1, x1, y0, z1, argb, width);
        edge(buffer, pose, x0, y1, z0, x1, y1, z0, argb, width);
        edge(buffer, pose, x0, y1, z1, x1, y1, z1, argb, width);

        edge(buffer, pose, x0, y0, z0, x0, y1, z0, argb, width);
        edge(buffer, pose, x0, y0, z1, x0, y1, z1, argb, width);
        edge(buffer, pose, x1, y0, z0, x1, y1, z0, argb, width);
        edge(buffer, pose, x1, y0, z1, x1, y1, z1, argb, width);

        edge(buffer, pose, x0, y0, z0, x0, y0, z1, argb, width);
        edge(buffer, pose, x0, y1, z0, x0, y1, z1, argb, width);
        edge(buffer, pose, x1, y0, z0, x1, y0, z1, argb, width);
        edge(buffer, pose, x1, y1, z0, x1, y1, z1, argb, width);
    }

    /** 单条线段（两个顶点，法线 = 线段方向）。 */
    private static void edge(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             int argb, float width) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-5F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        buffer.addVertex(pose, x0, y0, z0).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    /**
     * 画一个长方体的 6 个面（半透明填充）。
     *
     * <p>正反两套绕序都提交 —— 旧实现也是这么做的（两次 {@code addBoxFaces}），
     * 这样无论相机在盒子里面还是外面都能看到面，且不依赖管线是否开启背面剔除。
     * 每个盒子多 6 个四边形，代价可忽略。
     */
    public static void boxFaces(VertexConsumer buffer, PoseStack.Pose pose,
                                float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        // 朝外绕序：下、上、北(-Z)、南(+Z)、西(-X)、东(+X)
        quad(buffer, pose, argb, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
        quad(buffer, pose, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(buffer, pose, argb, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        quad(buffer, pose, argb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(buffer, pose, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);

        // 反向绕序（供相机在盒内时可见）
        quad(buffer, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(buffer, pose, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, pose, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(buffer, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(buffer, pose, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** 提交一个四边形（4 个顶点，按给定顺序）。 */
    public static void quad(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.addVertex(pose, ax, ay, az).setColor(argb);
        buffer.addVertex(pose, bx, by, bz).setColor(argb);
        buffer.addVertex(pose, cx, cy, cz).setColor(argb);
        buffer.addVertex(pose, dx, dy, dz).setColor(argb);
    }

    /** 把 float 分量颜色打包成顶点用的 int（0xAARRGGBB）。 */
    public static int argb(float r, float g, float b, float a) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    private static int clamp8(float v) {
        int i = Math.round(v * 255.0F);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
