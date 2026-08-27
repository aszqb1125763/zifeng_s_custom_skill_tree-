package org.zifeng.skilltree.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * 寻宝大师（TREASURE_HUNTER，2026-08-27）：64 格内战利品容器与考古刷扫点白色发光轮廓。
 * <p>客户端渲染：不创建实体、不碰任何模组数据 → 兼容性极好（所有实现 RandomizableContainer
 * 的容器：原版箱子/桶/潜影盒/漏斗 + 任意模组战利品容器；以及考古刷扫方块）。
 * <p>实现：
 * <ul>
 *   <li>每 1 秒扫描一次玩家 64 格内的目标方块实体，缓存 BlockPos（避免每帧遍历）</li>
 *   <li>{@link RenderLevelStageEvent.AFTER_BLOCK_ENTITIES} 画白色半透明线框（类似原版发光轮廓）</li>
 *   <li>技能状态用客户端本地缓存（服务端 S2CPacket 校准），多人下仅自己可见</li>
 * </ul>
 */
public class ClientTreasureEvents {

    private static final int SCAN_RADIUS_SQ = 64 * 64;        // 水平半径 64 格
    private static final int SCAN_Y_RADIUS_SQ = 128 * 128;    // 垂直半径 128 格（y 轴范围翻倍，2026-08-27）
    // 金色高亮（2026-08-27）：白线太细不显眼 → 金色双线框（外暗金粗线 + 内亮金），远看是粗金框
    private static final float OUTER_R = 1.0F, OUTER_G = 0.78F, OUTER_B = 0.15F;   // 暗金（外层）
    private static final float INNER_R = 1.0F, INNER_G = 0.92F, INNER_B = 0.35F;   // 亮金（内层）
    private static final float OUTLINE_ALPHA = 1.0F; // 纯色不透明（alpha=1 → 混合时 dst 贡献 0 → 颜色不受背后方块影响，2026-08-27）
    // 刷怪笼高亮（2026-08-27）：品红色双线框（与金色容器区分，刷怪笼火焰粒子同色系）
    private static final float SPAWNER_OUTER_R = 0.75F, SPAWNER_OUTER_G = 0.1F, SPAWNER_OUTER_B = 0.9F;  // 深品红（外层）
    private static final float SPAWNER_INNER_R = 1.0F, SPAWNER_INNER_G = 0.5F, SPAWNER_INNER_B = 1.0F;  // 亮品红（内层）
    // 考古方块高亮（2026-08-27）：青色双线框（与金色容器/品红刷怪笼区分，考古主题：可疑沙砾/沙子）
    private static final float ARCH_OUTER_R = 0.1F, ARCH_OUTER_G = 0.85F, ARCH_OUTER_B = 0.85F;   // 深青（外层）
    private static final float ARCH_INNER_R = 0.6F, ARCH_INNER_G = 1.0F, ARCH_INNER_B = 1.0F;     // 亮青（内层）
    private static final List<BlockPos> CACHED_TREASURES = new ArrayList<>();  // 容器（金色）
    private static final List<BlockPos> CACHED_ARCHAEOLOGY = new ArrayList<>(); // 考古方块（青色，2026-08-27）
    private static final List<BlockPos> CACHED_SPAWNERS = new ArrayList<>();   // 刷怪笼（品红）
    private static int scanTimer = 0;

    // ============ 透视线框 RenderType（无深度测试 → 穿墙透视） ============
    /** 1.20.1：RenderStateShard 的 shard 常量是 protected，嵌套子类继承以访问 */
    private static final class ShardAccess extends net.minecraft.client.renderer.RenderStateShard {
        private ShardAccess() {
            super("zifeng_shard", () -> {
            }, () -> {
            });
        }

        static final DepthTestStateShard NO_DEPTH = NO_DEPTH_TEST;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final ShaderStateShard LINES_SHADER = RENDERTYPE_LINES_SHADER;
        static final LayeringStateShard VIEW_OFFSET = VIEW_OFFSET_Z_LAYERING;
        static final OutputStateShard ITEM_TARGET = ITEM_ENTITY_TARGET;
        static final WriteMaskStateShard COLOR_DEPTH_WRITE_S = COLOR_DEPTH_WRITE;
        static final CullStateShard NO_CULL_S = NO_CULL;
        // LineStateShard 构造器也是 protected：在子类内实例化（lines() 同款默认线宽）
        static final LineStateShard LINE_WIDTH = new LineStateShard(java.util.OptionalDouble.empty());
    }

    /**
     * 透视线框 RenderType：RenderType.lines() 的完整克隆——格式必须完全一致
     * （POSITION_COLOR_NORMAL + Mode.LINES，2026-08-27 教训：用 POSITION_COLOR + DEBUG_LINES
     * 会与 rendertype_lines shader 格式不匹配 → 线框残缺/只剩顶点）。
     * 仅 depthTest=NO_DEPTH_TEST → 被方块挡住也显示（穿墙透视），其余 shard 与 lines() 完全相同。
     * 透明混合保留（alpha=1.0 顶点色 → 不透明纯色，不受方块颜色影响）。
     */
    private static final net.minecraft.client.renderer.RenderType TREASURE_LINES = net.minecraft.client.renderer.RenderType.create(
            "zifeng_treasure_lines",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES,
            1536, false, false,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(ShardAccess.LINES_SHADER)
                    .setLineState(ShardAccess.LINE_WIDTH)
                    .setLayeringState(ShardAccess.VIEW_OFFSET)
                    .setTransparencyState(ShardAccess.TRANSLUCENT)
                    .setOutputState(ShardAccess.ITEM_TARGET)
                    .setWriteMaskState(ShardAccess.COLOR_DEPTH_WRITE_S)
                    .setCullState(ShardAccess.NO_CULL_S)
                    .setDepthTestState(ShardAccess.NO_DEPTH)
                    .createCompositeState(false));

    /**
     * 复用的 BufferSource（懒加载单例）：endBatch 后内部 buffer 全部清空，下次 getBuffer 重新 begin，
     * 复用安全。避免每帧 new BufferBuilder（2026-08-27 OOM：Failed to resize buffer 9216→2106368）。
     */
    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource TREASURE_BUFFERS;

    private static net.minecraft.client.renderer.MultiBufferSource.BufferSource treasureBuffers() {
        if (TREASURE_BUFFERS == null) {
            TREASURE_BUFFERS = net.minecraft.client.renderer.MultiBufferSource.immediateWithBuffers(
                    java.util.Map.of(TREASURE_LINES,
                            new com.mojang.blaze3d.vertex.BufferBuilder(256)),
                    new com.mojang.blaze3d.vertex.BufferBuilder(256));
        }
        return TREASURE_BUFFERS;
    }

    /** 水平 64 格 + 垂直 128 格的椭球范围判定（y 轴翻倍） */
    private static boolean withinRange(BlockPos center, double x, double y, double z) {
        double dx = x - center.getX();
        double dy = y - center.getY();
        double dz = z - center.getZ();
        return dx * dx + dz * dz <= SCAN_RADIUS_SQ && dy * dy <= SCAN_Y_RADIUS_SQ;
    }

    /** 每秒扫描一次 64 格内的战利品容器/考古刷扫点 */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof LocalPlayer)) {
            return;
        }
        scanTimer++;
        if (scanTimer < 40) {
            return; // 每 2 秒扫描一次（2026-08-27 性能优化：原 1 秒；扫描 81 区块开销大）
        }
        scanTimer = 0;
        CACHED_TREASURES.clear();
        CACHED_ARCHAEOLOGY.clear();
        CACHED_SPAWNERS.clear();
        if (!ModKeyBindingEvents.isSkillEnabledClient(Skills.TREASURE_HUNTER)) {
            return; // 技能未学/未开启
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos center = mc.player.blockPosition();
        // ===== 1. 方块实体：按区块扫描（Level 无公开 blockEntityList；LevelChunk.getBlockEntities 提供全部方块实体） =====
        int chunkMinX = (center.getX() - 64) >> 4;
        int chunkMaxX = (center.getX() + 64) >> 4;
        int chunkMinZ = (center.getZ() - 64) >> 4;
        int chunkMaxZ = (center.getZ() + 64) >> 4;
        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                // ⚠️ 性能优化（2026-08-27）：getChunk(x,z,status,false) 未加载时返回 null——
                //    单参 getChunk 会强制加载未加载区块（客户端站在加载边缘时每秒触发 chunk 加载 → 周期性卡顿）
                net.minecraft.world.level.chunk.ChunkAccess chunk = mc.level.getChunk(cx, cz,
                        net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk lc)) {
                    continue; // 未加载 → 跳过（下轮扫描再查）
                }
                for (BlockEntity be : lc.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!withinRange(center, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                        continue; // 超出范围（水平 64 格 / 垂直 128 格）
                    }
                    // 标记目标：
                    //  金色 = 容器（含模组容器，Container 接口）
                    //  青色 = 考古刷扫点（可疑沙砾/沙子）
                    //  品红 = 刷怪笼/试炼刷怪笼
                    // ⚠️ 2026-08-27：只标 RandomizableContainer 会漏掉无战利品表的普通箱子。
                    if (be instanceof net.minecraft.world.Container) {
                        CACHED_TREASURES.add(pos);
                    } else if (be instanceof BrushableBlockEntity) {
                        CACHED_ARCHAEOLOGY.add(pos); // 考古刷扫点：青色（样式区分，2026-08-27）
                    } else if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity) {
                        CACHED_SPAWNERS.add(pos); // 刷怪笼（品红色区分；1.20.1 无试炼刷怪笼）
                    }
                }
            }
        }
        // ===== 2. 容器实体（2026-08-27 测试反馈：宝箱矿车等漏标）：ContainerEntity 实体（宝箱矿车/漏斗矿车） =====
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.vehicle.ContainerEntity)) {
                continue;
            }
            net.minecraft.world.phys.Vec3 pos = entity.position();
            if (!withinRange(center, pos.x(), pos.y(), pos.z())) {
                continue; // 超出范围（水平 64 格 / 垂直 128 格）
            }
            CACHED_TREASURES.add(net.minecraft.core.BlockPos.containing(pos));
        }
    }

    /**
     * 方块实体渲染后画发光轮廓（金色/青色/品红双线框，类似原版发光）。
     * ⚠️ 1.20.1 最终方案（2026-08-27）：单阶段 AFTER_BLOCK_ENTITIES——Oculus/Embeddium 下也触发
     *    （日志证实），且事件 poseStack 带完整相机变换 → 位置正确；单阶段不双画 → 不频闪。
     *    渲染用 MultiBufferSource + RenderType.lines()（标准管线，Oculus 兼容），不碰 RenderSystem 手动状态。
     * ⚠️ Iris/Oculus 兼容：阴影 pass 时跳过（主 pass 才画轮廓，避免阴影里闪烁）。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // ⚠️ 单阶段 AFTER_BLOCK_ENTITIES：Oculus/Embeddium 下也触发（已日志证实），
        //    且该阶段事件 poseStack 带完整相机变换 → 位置正确；
        //    单阶段不双画 → 不频闪。勿用 AFTER_LEVEL（poseStack 无相机变换 → 偏移）。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // Iris 光影软检测：光影激活且处于阴影 pass → 跳过（主 pass 才画轮廓）
        if (isIrisShadowPass()) {
            return;
        }
        if (CACHED_TREASURES.isEmpty() && CACHED_ARCHAEOLOGY.isEmpty() && CACHED_SPAWNERS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 相机偏移（渲染坐标 = 世界坐标 - 相机位置）；事件 poseStack 带相机变换（AFTER_BLOCK_ENTITIES）
        var cam = event.getCamera();
        double camX = cam.getPosition().x;
        double camY = cam.getPosition().y;
        double camZ = cam.getPosition().z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        // ⚠️ 穿墙方案（2026-08-27，用户确认有效）：自定义 TREASURE_LINES（lines() 完整克隆，仅
        //    depthTest=NO_DEPTH_TEST → depthFunc=GL_ALWAYS）+ 全局 RenderSystem.disableDepthTest()
        //    双保险；BufferSource 静态复用（零每帧分配 → 不 OOM）；标准 endBatch 管线（Oculus/Embeddium 兼容）。
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = treasureBuffers();
        VertexConsumer vertex = bufferSource.getBuffer(TREASURE_LINES);
        // ===== 容器/考古点：金色双线框 =====
        for (BlockPos pos : CACHED_TREASURES) {
            // 双线框加粗：外框（扩 0.12 格，暗金）+ 内框（扩 0.03 格，亮金）→ 视觉粗线
            float x0 = pos.getX();
            float y0 = pos.getY();
            float z0 = pos.getZ();
            // 外层暗金（粗）：向外扩 0.12
            LevelRenderer.renderLineBox(poseStack, vertex,
                    x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x0 + 1.12F, y0 + 1.12F, z0 + 1.12F,
                    OUTER_R, OUTER_G, OUTER_B, OUTLINE_ALPHA);
            // 内层亮金（细高亮）：向外扩 0.03
            LevelRenderer.renderLineBox(poseStack, vertex,
                    x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x0 + 1.03F, y0 + 1.03F, z0 + 1.03F,
                    INNER_R, INNER_G, INNER_B, OUTLINE_ALPHA);
        }
        // ===== 考古方块：青色双线框（样式区分，2026-08-27）=====
        for (BlockPos pos : CACHED_ARCHAEOLOGY) {
            float x0 = pos.getX();
            float y0 = pos.getY();
            float z0 = pos.getZ();
            // 外层深青（粗）：向外扩 0.12
            LevelRenderer.renderLineBox(poseStack, vertex,
                    x0 - 0.12F, y0 - 0.12F, z0 - 0.12F,
                    x0 + 1.12F, y0 + 1.12F, z0 + 1.12F,
                    ARCH_OUTER_R, ARCH_OUTER_G, ARCH_OUTER_B, OUTLINE_ALPHA);
            // 内层亮青（细高亮）：向外扩 0.03
            LevelRenderer.renderLineBox(poseStack, vertex,
                    x0 - 0.03F, y0 - 0.03F, z0 - 0.03F,
                    x0 + 1.03F, y0 + 1.03F, z0 + 1.03F,
                    ARCH_INNER_R, ARCH_INNER_G, ARCH_INNER_B, OUTLINE_ALPHA);
        }
        // ===== 刷怪笼：品红色双线框（样式区分，2026-08-27）=====
        for (BlockPos pos : CACHED_SPAWNERS) {
            float x0 = pos.getX();
            float y0 = pos.getY();
            float z0 = pos.getZ();
            // 外层深品红（粗）：向外扩 0.14
            LevelRenderer.renderLineBox(poseStack, vertex,
                    x0 - 0.14F, y0 - 0.14F, z0 - 0.14F,
                    x0 + 1.14F, y0 + 1.14F, z0 + 1.14F,
                    SPAWNER_OUTER_R, SPAWNER_OUTER_G, SPAWNER_OUTER_B, OUTLINE_ALPHA);
            // 内层亮品红（细高亮）：向外扩 0.04
            LevelRenderer.renderLineBox(poseStack, vertex,
                    x0 - 0.04F, y0 - 0.04F, z0 - 0.04F,
                    x0 + 1.04F, y0 + 1.04F, z0 + 1.04F,
                    SPAWNER_INNER_R, SPAWNER_INNER_G, SPAWNER_INNER_B, OUTLINE_ALPHA);
        }
        // ===== 上传绘制：穿墙双保险（disableDepthTest 全局开关 + TREASURE_LINES 自带 NO_DEPTH_TEST shard）=====
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        bufferSource.endBatch(TREASURE_LINES);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();

        poseStack.popPose();
    }

    /**
     * Iris 光影软检测（无 Iris 时返回 false）：光影激活且处于阴影 pass → 跳过渲染。
     * 反射调用 IrisApi（避免编译期依赖 iris jar）。Method 对象首次成功后缓存（避免每帧 getMethod）。
     */
    private static java.lang.reflect.Method IRIS_GET_INSTANCE, IRIS_IS_SHADER_IN_USE, IRIS_IS_SHADOW_PASS;

    private static boolean isIrisShadowPass() {
        try {
            if (IRIS_GET_INSTANCE == null) {
                Class<?> irisApiCls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                IRIS_GET_INSTANCE = irisApiCls.getMethod("getInstance");
                IRIS_IS_SHADER_IN_USE = irisApiCls.getMethod("isShaderPackInUse");
                IRIS_IS_SHADOW_PASS = irisApiCls.getMethod("isRenderingShadowPass");
            }
            Object instance = IRIS_GET_INSTANCE.invoke(null);
            if (!(Boolean) IRIS_IS_SHADER_IN_USE.invoke(instance)) {
                return false; // 无光影激活 → 正常渲染
            }
            return (Boolean) IRIS_IS_SHADOW_PASS.invoke(instance);
        } catch (Throwable ignored) {
            return false; // 无 Iris 或 API 变动 → 正常渲染
        }
    }
}
